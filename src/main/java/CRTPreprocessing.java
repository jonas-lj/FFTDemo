import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.Party;
import dk.alexandra.fresco.framework.ProtocolEvaluator;
import dk.alexandra.fresco.framework.builder.ProtocolBuilder;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.configuration.NetworkConfiguration;
import dk.alexandra.fresco.framework.configuration.NetworkConfigurationImpl;
import dk.alexandra.fresco.framework.network.Network;
import dk.alexandra.fresco.framework.network.socket.SocketNetwork;
import dk.alexandra.fresco.framework.sce.SecureComputationEngine;
import dk.alexandra.fresco.framework.sce.SecureComputationEngineImpl;
import dk.alexandra.fresco.framework.sce.evaluator.BatchEvaluationStrategy;
import dk.alexandra.fresco.framework.sce.evaluator.BatchedProtocolEvaluator;
import dk.alexandra.fresco.framework.util.AesCtrDrbg;
import dk.alexandra.fresco.lib.field.integer.BasicNumericContext;
import dk.alexandra.fresco.lib.fixed.AdvancedFixedNumeric;
import dk.alexandra.fresco.lib.fixed.FixedNumeric;
import dk.alexandra.fresco.logging.BatchEvaluationLoggingDecorator;
import dk.alexandra.fresco.logging.NetworkLoggingDecorator;
import dk.alexandra.fresco.logging.NumericSuiteLogging;
import dk.alexandra.fresco.stat.complex.OpenComplex;
import dk.alexandra.fresco.suite.ProtocolSuiteNumeric;
import dk.alexandra.fresco.suite.crt.CRTProtocolSuite;
import dk.alexandra.fresco.suite.crt.datatypes.CRTCombinedPad;
import dk.alexandra.fresco.suite.crt.datatypes.resource.*;
import dk.alexandra.fresco.suite.crt.fixed.CRTAdvancedFixedNumeric;
import dk.alexandra.fresco.suite.crt.fixed.CRTFixedNumeric;
import dk.alexandra.fresco.suite.crt.protocols.framework.CRTSequentialStrategy;
import dk.alexandra.fresco.suite.spdz.SpdzBuilder;
import dk.alexandra.fresco.suite.spdz.SpdzResourcePool;
import dk.alexandra.fresco.suite.spdz.SpdzResourcePoolImpl;
import dk.alexandra.fresco.suite.spdz.storage.SpdzDataSupplier;
import dk.alexandra.fresco.suite.spdz.storage.SpdzDummyDataSupplier;
import dk.alexandra.fresco.suite.spdz.storage.SpdzOpenedValueStoreImpl;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dk.alexandra.fresco.suite.crt.datatypes.resource.CRTDataSupplier.DEFAULT_STATSECURITY;

public class CRTPreprocessing {
    private static final int BATCH_SIZE = 1024;

    public enum Strategy {
        Covert, SemiHonest,
    }

    public static void main(String[] arguments) {
        if (arguments.length < 6) {
            throw new IllegalArgumentException("Usage: java -jar CRTPreprocessing [domainInBits] [statSec] [batchSize] [Covert/SemiHonest] [myId] [otherIP1] ([otherIP2] ...)");
        }

        int domainInBits = Integer.parseInt(arguments[0]);
        int statsec = Integer.parseInt(arguments[1]);
        int batchSize = Integer.parseInt(arguments[2]);
        Strategy strategy = Strategy.valueOf(arguments[3]);
        int myId = Integer.parseInt(arguments[4]);
        List<String> otherIPs = new ArrayList<>();
        for (int i = 5; i < arguments.length; i++) {
            otherIPs.add(arguments[i]);
        }
        run(myId, otherIPs, domainInBits, statsec, batchSize, strategy);
    }

    public static void run(int myId,List<String> otherIPs, int domainInBits, int statsec, int batchSize, Strategy preprocessingStrategy) {
        Map<Integer, Party> parties = Utils.setupParties(myId, otherIPs);

        NetworkConfiguration networkConfiguration = new NetworkConfigurationImpl(myId, parties);
        Network network = new SocketNetwork(networkConfiguration);

        BatchEvaluationStrategy<CRTResourcePool<SpdzResourcePool, SpdzResourcePool>> strategy =
                new CRTSequentialStrategy<>();
        CRTFieldParams crtParams = new CRTFieldParams(domainInBits, statsec, parties.size());

        SpdzDataSupplier supplierLeft = new SpdzDummyDataSupplier(myId, parties.size(), crtParams.getP(),
                DemoOnline.SECRET_SHARED_KEY);
        SpdzResourcePool rpLeft = new SpdzResourcePoolImpl(myId, parties.size(),
                new SpdzOpenedValueStoreImpl(), supplierLeft,
                AesCtrDrbg::new);
        SpdzDataSupplier supplierRight = new SpdzDummyDataSupplier(myId, parties.size(), crtParams.getQ(),
                DemoOnline.SECRET_SHARED_KEY);
        SpdzResourcePool rpRight = new SpdzResourcePoolImpl(myId, parties.size(),
                new SpdzOpenedValueStoreImpl(), supplierRight,
                AesCtrDrbg::new);

        CRTDataSupplier<SpdzResourcePool, SpdzResourcePool> dataSupplier;
        if (preprocessingStrategy == Strategy.Covert) {
            dataSupplier = new CRTSemiHonestDataSupplier<>(null);
        } else if (preprocessingStrategy == Strategy.SemiHonest) {
            dataSupplier = new CRTCovertDataSupplier<>(null);
        } else {
            throw new IllegalArgumentException("Unknown strategy: " + strategy);
        }

        CRTResourcePool<SpdzResourcePool, SpdzResourcePool> rp =
                new CRTResourcePoolImpl<>(myId, parties.size(), dataSupplier, rpLeft, rpRight);

        ProtocolSuiteNumeric<CRTResourcePool<SpdzResourcePool, SpdzResourcePool>> ps =
                new CRTProtocolSuite<>(
                        new SpdzBuilder(new BasicNumericContext(crtParams.getP().getBitLength(),
                                myId, parties.size(), crtParams.getP(), 16, statsec)),
                        new SpdzBuilder(new BasicNumericContext(crtParams.getQ().getBitLength(),
                                myId, parties.size(), crtParams.getQ(), 16, statsec)));
        // Logging
        strategy =
                new BatchEvaluationLoggingDecorator<>(strategy);
        network = new NetworkLoggingDecorator(network);
        ps = new NumericSuiteLogging<>(ps);

        ProtocolEvaluator<CRTResourcePool<SpdzResourcePool,
                SpdzResourcePool>> evaluator =
                new BatchedProtocolEvaluator<>(strategy, ps);

        SecureComputationEngine<CRTResourcePool<SpdzResourcePool,
                SpdzResourcePool>, ProtocolBuilderNumeric> sce =
                new SecureComputationEngineImpl<>(ps, evaluator);

        System.out.println("Loading CRT fixed numeric...");
        FixedNumeric.load(CRTFixedNumeric::new);
        AdvancedFixedNumeric.load(CRTAdvancedFixedNumeric::new);

        Instant start = Instant.now();

        sce
                .runApplication(new PreprocessingApplication(batchSize, statsec),
                        rp, network, Duration.ofMinutes(30));

        System.out.println("================== Metrics ==================");
        System.out.println("Evaluation: " + ((BatchEvaluationLoggingDecorator<?>) strategy).getLoggedValues());
        System.out.println("Network: " + ((NetworkLoggingDecorator) network).getLoggedValues());
        System.out.println("Arithmetic: " + ((NumericSuiteLogging<?>) ps).getLoggedValues());
        System.out.println("=============================================");

        System.out.println("Took " + Duration.between(start, Instant.now()));

    }

    public static class PreprocessingApplication implements Application<List<CRTCombinedPad>, ProtocolBuilderNumeric> {

        private final int batchSize;
        private final int statSec;

        public PreprocessingApplication(int batchSize, int statSec) {
            this.batchSize = batchSize;
            this.statSec = statSec;
        }

        @Override
        public DRes<List<CRTCombinedPad>> buildComputation(ProtocolBuilderNumeric protocolBuilder) {
            return protocolBuilder.seq(new SemiHonestNoiseGenerator<>(batchSize, statSec));
        }
    }

}
