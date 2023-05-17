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

    public enum Strategy {
        Covert, SemiHonest,
    }

    public static void main(String[] arguments) {
        if (arguments.length != 5) {
            throw new IllegalArgumentException("Usage: java Demo [myId] [otherIP1] [otherIP2] [n] [Covert/SemiHonest]");
        }

        int myId = Integer.parseInt(arguments[0]);
        String otherIP1 = arguments[1];
        String otherIP2 = arguments[2];
        int n = Integer.parseInt(arguments[3]);

        Strategy strategy = Strategy.valueOf(arguments[4]);

        run(myId, otherIP1, otherIP2, n, strategy);
    }

    public static void run(int myId, String otherIP1, String otherIP2, int n, Strategy preprocessingStrategy) {

        Party me = new Party(myId, "localhost", 9000 + myId);
        Map<Integer, Party> parties = new HashMap<>();
        parties.put(myId, me);

        List<String> otherIPs = List.of(otherIP1, otherIP2);
        final List<Integer> otherIds = new ArrayList<>(List.of(1, 2, 3));
        otherIds.remove(Integer.valueOf(myId));
        for (int i = 0; i < 2; i++) {
            int id = otherIds.get(i);
            parties.put(id, new Party(id, otherIPs.get(i), 9000 + id));
        }
        int noParties = 3;

        System.out.println("Parties: " + parties);
        System.out.println();

        NetworkConfiguration networkConfiguration = new NetworkConfigurationImpl(myId, parties);
        Network network = new SocketNetwork(networkConfiguration);

        BatchEvaluationStrategy<CRTResourcePool<SpdzResourcePool, SpdzResourcePool>> strategy =
                new CRTSequentialStrategy<>();

        SpdzDataSupplier supplierLeft = new SpdzDummyDataSupplier(myId, noParties, DemoOnline.DEFAULT_FIELD_LEFT,
                DemoOnline.SECRET_SHARED_KEY);
        SpdzResourcePool rpLeft = new SpdzResourcePoolImpl(myId, noParties,
                new SpdzOpenedValueStoreImpl(), supplierLeft,
                AesCtrDrbg::new);
        SpdzDataSupplier supplierRight = new SpdzDummyDataSupplier(myId, noParties, DemoOnline.DEFAULT_FIELD_RIGHT,
                DemoOnline.SECRET_SHARED_KEY);
        SpdzResourcePool rpRight = new SpdzResourcePoolImpl(myId, noParties,
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
                new CRTResourcePoolImpl<>(myId, noParties, dataSupplier, rpLeft, rpRight);

        ProtocolSuiteNumeric<CRTResourcePool<SpdzResourcePool, SpdzResourcePool>> ps =
                new CRTProtocolSuite<>(
                        new SpdzBuilder(new BasicNumericContext(DemoOnline.DEFAULT_FIELD_LEFT.getBitLength(),
                                myId, noParties, DemoOnline.DEFAULT_FIELD_LEFT, 16, DEFAULT_STATSECURITY)),
                        new SpdzBuilder(new BasicNumericContext(DemoOnline.DEFAULT_FIELD_RIGHT.getBitLength(),
                                myId, noParties, DemoOnline.DEFAULT_FIELD_RIGHT, 16, DEFAULT_STATSECURITY)));
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
                .runApplication(new PreprocessingApplication(n),
                        rp, network, Duration.ofMinutes(30));

        System.out.println("================== Metrics ==================");
        System.out.println("Evaluation: " + ((BatchEvaluationLoggingDecorator<?>) strategy).getLoggedValues());
        System.out.println("Network: " + ((NetworkLoggingDecorator) network).getLoggedValues());
        System.out.println("Arithmetic: " + ((NumericSuiteLogging<?>) ps).getLoggedValues());
        System.out.println("=============================================");

        System.out.println("Took " + Duration.between(start, Instant.now()));

    }

    public static class PreprocessingApplication implements Application<List<CRTCombinedPad>, ProtocolBuilderNumeric> {

        private final int n;

        public PreprocessingApplication(int n) {
            this.n = n;
        }

        @Override
        public DRes<List<CRTCombinedPad>> buildComputation(ProtocolBuilderNumeric protocolBuilder) {
            return protocolBuilder.seq(new SemiHonestNoiseGenerator<>(n, DEFAULT_STATSECURITY));
        }
    }

}
