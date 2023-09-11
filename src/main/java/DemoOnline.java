import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.Party;
import dk.alexandra.fresco.framework.ProtocolEvaluator;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.builder.numeric.field.BigIntegerFieldDefinition;
import dk.alexandra.fresco.framework.configuration.NetworkConfiguration;
import dk.alexandra.fresco.framework.configuration.NetworkConfigurationImpl;
import dk.alexandra.fresco.framework.network.Network;
import dk.alexandra.fresco.framework.network.socket.SocketNetwork;
import dk.alexandra.fresco.framework.sce.SecureComputationEngine;
import dk.alexandra.fresco.framework.sce.SecureComputationEngineImpl;
import dk.alexandra.fresco.framework.sce.evaluator.BatchEvaluationStrategy;
import dk.alexandra.fresco.framework.sce.evaluator.BatchedProtocolEvaluator;
import dk.alexandra.fresco.framework.sce.evaluator.EvaluationStrategy;
import dk.alexandra.fresco.framework.util.AesCtrDrbg;
import dk.alexandra.fresco.framework.util.ModulusFinder;
import dk.alexandra.fresco.lib.field.integer.BasicNumericContext;
import dk.alexandra.fresco.lib.fixed.AdvancedFixedNumeric;
import dk.alexandra.fresco.lib.fixed.FixedNumeric;
import dk.alexandra.fresco.logging.BatchEvaluationLoggingDecorator;
import dk.alexandra.fresco.logging.NetworkLoggingDecorator;
import dk.alexandra.fresco.logging.NumericSuiteLogging;
import dk.alexandra.fresco.suite.ProtocolSuiteNumeric;
import dk.alexandra.fresco.suite.crt.CRTProtocolSuite;
import dk.alexandra.fresco.suite.crt.datatypes.resource.*;
import dk.alexandra.fresco.suite.crt.fixed.CRTAdvancedFixedNumeric;
import dk.alexandra.fresco.suite.crt.fixed.CRTFixedNumeric;
import dk.alexandra.fresco.suite.crt.protocols.framework.CRTBatchedStrategy;
import dk.alexandra.fresco.suite.spdz.SpdzBuilder;
import dk.alexandra.fresco.suite.spdz.SpdzProtocolSuite;
import dk.alexandra.fresco.suite.spdz.SpdzResourcePool;
import dk.alexandra.fresco.suite.spdz.SpdzResourcePoolImpl;
import dk.alexandra.fresco.suite.spdz.storage.SpdzDataSupplier;
import dk.alexandra.fresco.suite.spdz.storage.SpdzDummyDataSupplier;
import dk.alexandra.fresco.suite.spdz.storage.SpdzOpenedValueStoreImpl;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class DemoOnline<OutputT> {
    public static final int DEFAULT_DETERRENCE = 2;
    public static final int PRECISION = 32;
    public static final BigInteger SECRET_SHARED_KEY = BigInteger.valueOf(1234);

    public void run(int myId, List<String> otherIPs, int domainInBits, int statsec, int batchSize, Scheme scheme, Strategy preprocessStrategy, Application<OutputT, ProtocolBuilderNumeric> application) {
        run(myId, otherIPs, domainInBits, statsec, DEFAULT_DETERRENCE, batchSize, scheme, preprocessStrategy, application);
    }

    public void run(int myId, List<String> otherIPs, int domainInBits, int statsec, int deterrence, int batchSize, Scheme scheme, Strategy preprocessStrategy, Application<OutputT, ProtocolBuilderNumeric> application) {

        final int maxBatchSize = 1024;
        Map<Integer, Party> parties = Utils.setupParties(myId, otherIPs);
        int noParties = parties.size();
        CRTFieldParams crtParams = new CRTFieldParams(domainInBits, statsec, noParties);

        NetworkConfiguration networkConfiguration = new NetworkConfigurationImpl(myId, parties);
        Network network = new SocketNetwork(networkConfiguration);

        Instant start;
        OutputT results;

        switch (scheme) {
            case CRT: {
                BatchEvaluationStrategy<CRTResourcePool<SpdzResourcePool, SpdzResourcePool>> strategy =
                        new CRTBatchedStrategy<>();

                SpdzDataSupplier supplierLeft = new SpdzDummyDataSupplier(myId, noParties, crtParams.getP(),
                        SECRET_SHARED_KEY);
                SpdzResourcePool rpLeft = new SpdzResourcePoolImpl(myId, noParties,
                        new SpdzOpenedValueStoreImpl(), supplierLeft,
                        AesCtrDrbg::new);
                SpdzDataSupplier supplierRight = new SpdzDummyDataSupplier(myId, noParties, crtParams.getQ(),
                        SECRET_SHARED_KEY);
                SpdzResourcePool rpRight = new SpdzResourcePoolImpl(myId, noParties,
                        new SpdzOpenedValueStoreImpl(), supplierRight,
                        AesCtrDrbg::new);

                CRTDataSupplier<SpdzResourcePool, SpdzResourcePool> dataSupplier;
                if (preprocessStrategy == Strategy.Covert) {
                    dataSupplier = new CRTSemiHonestDataSupplier<>(batchSize, statsec);
                } else if (preprocessStrategy == Strategy.SemiHonest) {
                    dataSupplier = new CRTCovertDataSupplier<>(batchSize, deterrence, statsec);
                } else if (preprocessStrategy == Strategy.Dummy) {
                    dataSupplier = new CRTDummyDataSupplier<>(myId, noParties, CRTDataSupplier.DEFAULT_STATSECURITY,
                            crtParams.getP(), crtParams.getQ(),
                            x -> Utils.fromBigInteger(crtParams.getP(), SECRET_SHARED_KEY, myId, x),
                            x -> Utils.fromBigInteger(crtParams.getQ(), SECRET_SHARED_KEY, myId, x));
                } else {
                    throw new IllegalArgumentException("Unknown strategy: " + strategy);
                }

                CRTResourcePool<SpdzResourcePool, SpdzResourcePool> rp =
                        new CRTResourcePoolImpl<>(myId, noParties, dataSupplier, rpLeft, rpRight);

                ProtocolSuiteNumeric<CRTResourcePool<SpdzResourcePool, SpdzResourcePool>> ps =
                        new CRTProtocolSuite<>(
                                new SpdzBuilder(new BasicNumericContext(crtParams.getP().getBitLength(),
                                        myId, noParties, crtParams.getP(), PRECISION, statsec)),
                                new SpdzBuilder(new BasicNumericContext(crtParams.getQ().getBitLength(),
                                        myId, noParties, crtParams.getQ(), PRECISION, statsec)));

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

                start = Instant.now();

                results = sce
                        .runApplication(application,
                                rp, network, Duration.ofMinutes(30));

                System.out.println("Bits in P: " + crtParams.getP().getModulus().bitLength());
                System.out.println("Bits in Q: " + crtParams.getQ().getModulus().bitLength());
                System.out.println("Bits available for computation: " +  crtParams.getMaxAllowedValue().bitLength());
                System.out.println("================== Metrics ==================");
                System.out.println("Evaluation: " + ((BatchEvaluationLoggingDecorator<?>) strategy).getLoggedValues());
                System.out.println("Network: " + ((NetworkLoggingDecorator) network).getLoggedValues());
                System.out.println("Arithmetic: " + ((NumericSuiteLogging<?>) ps).getLoggedValues());
                System.out.println("=============================================");
                break;
            }

            case SPDZ: {
                ProtocolSuiteNumeric<SpdzResourcePool> suite = new SpdzProtocolSuite(domainInBits, PRECISION);

                // Use "dummy" multiplication triples to simulate doing only the online phase
                SpdzDataSupplier supplier = new SpdzDummyDataSupplier(myId, noParties, new BigIntegerFieldDefinition(ModulusFinder.findSuitableModulus(domainInBits)),
                        BigInteger.valueOf(1234));

                SpdzResourcePool rp = new SpdzResourcePoolImpl(myId, noParties,
                        new SpdzOpenedValueStoreImpl(), supplier,
                        AesCtrDrbg::new);

                BatchEvaluationStrategy<SpdzResourcePool> strategy = EvaluationStrategy.SEQUENTIAL_BATCHED.getStrategy();

                // Logging
                strategy =
                        new BatchEvaluationLoggingDecorator<>(strategy);
                network = new NetworkLoggingDecorator(network);
                suite = new NumericSuiteLogging<>(suite);

                BatchedProtocolEvaluator<SpdzResourcePool> evaluator =
                        new BatchedProtocolEvaluator<>(strategy, suite,
                                maxBatchSize * PRECISION);

                SecureComputationEngine<SpdzResourcePool, ProtocolBuilderNumeric> sce = new SecureComputationEngineImpl<>(
                        suite, evaluator);

                start = Instant.now();
                results = sce
                        .runApplication(application,
                                rp, network, Duration.ofMinutes(30));
                System.out.println("Bits available for computation: " +  domainInBits);
                System.out.println("================== Metrics ==================");
                System.out.println("Evaluation: " + ((BatchEvaluationLoggingDecorator<SpdzResourcePool>) strategy).getLoggedValues());
                System.out.println("Network: " + ((NetworkLoggingDecorator) network).getLoggedValues());
                System.out.println("Arithmetic: " + ((NumericSuiteLogging<SpdzResourcePool>) suite).getLoggedValues());
                System.out.println("=============================================");
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown scheme: " + scheme);
        }

        System.out.println();
        System.out.println("================== Results ==================");
        System.out.println(results);
        System.out.println("=============================================");
        System.out.println("Took " + Duration.between(start, Instant.now()));
    }

    public enum Scheme {
        CRT, SPDZ
    }
    public enum Strategy {
        Covert, SemiHonest, Dummy,
    }
}