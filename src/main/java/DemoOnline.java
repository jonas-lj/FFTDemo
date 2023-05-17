import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.Party;
import dk.alexandra.fresco.framework.ProtocolEvaluator;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.builder.numeric.field.BigIntegerFieldDefinition;
import dk.alexandra.fresco.framework.builder.numeric.field.FieldDefinition;
import dk.alexandra.fresco.framework.builder.numeric.field.MersennePrimeFieldDefinition;
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
import dk.alexandra.fresco.lib.field.integer.BasicNumericContext;
import dk.alexandra.fresco.lib.fixed.AdvancedFixedNumeric;
import dk.alexandra.fresco.lib.fixed.FixedNumeric;
import dk.alexandra.fresco.logging.BatchEvaluationLoggingDecorator;
import dk.alexandra.fresco.logging.NetworkLoggingDecorator;
import dk.alexandra.fresco.logging.NumericSuiteLogging;
import dk.alexandra.fresco.suite.ProtocolSuiteNumeric;
import dk.alexandra.fresco.suite.crt.CRTProtocolSuite;
import dk.alexandra.fresco.suite.crt.datatypes.resource.CRTDataSupplier;
import dk.alexandra.fresco.suite.crt.datatypes.resource.CRTDummyDataSupplier;
import dk.alexandra.fresco.suite.crt.datatypes.resource.CRTResourcePool;
import dk.alexandra.fresco.suite.crt.datatypes.resource.CRTResourcePoolImpl;
import dk.alexandra.fresco.suite.crt.fixed.CRTAdvancedFixedNumeric;
import dk.alexandra.fresco.suite.crt.fixed.CRTFixedNumeric;
import dk.alexandra.fresco.suite.crt.protocols.framework.CRTSequentialStrategy;
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

    public static final BigInteger SECRET_SHARED_KEY = BigInteger.valueOf(1234);
    protected static final FieldDefinition DEFAULT_FIELD_LEFT =
            MersennePrimeFieldDefinition.find(64);
    protected static final FieldDefinition DEFAULT_FIELD_RIGHT = new BigIntegerFieldDefinition(
            new BigInteger(192, new Random(1234)).nextProbablePrime());
    protected static final FieldDefinition DEFAULT_FIELD = new BigIntegerFieldDefinition(
            new BigInteger(256, new Random(1234)).nextProbablePrime());

    public void run(int myId, String otherIP1, String otherIP2, Scheme scheme, Application<OutputT, ProtocolBuilderNumeric> application) {

        final int modBitLength = 256;
        final int maxBitLength = 180;
        final int maxBatchSize = 4096;

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

        Instant start;
        OutputT results;

        switch (scheme) {
            case CRT: {
                BatchEvaluationStrategy<CRTResourcePool<SpdzResourcePool, SpdzResourcePool>> strategy =
                        new CRTSequentialStrategy<>();

                SpdzDataSupplier supplierLeft = new SpdzDummyDataSupplier(myId, noParties, DEFAULT_FIELD_LEFT,
                        SECRET_SHARED_KEY);
                SpdzResourcePool rpLeft = new SpdzResourcePoolImpl(myId, noParties,
                        new SpdzOpenedValueStoreImpl(), supplierLeft,
                        AesCtrDrbg::new);
                SpdzDataSupplier supplierRight = new SpdzDummyDataSupplier(myId, noParties, DEFAULT_FIELD_RIGHT,
                        SECRET_SHARED_KEY);
                SpdzResourcePool rpRight = new SpdzResourcePoolImpl(myId, noParties,
                        new SpdzOpenedValueStoreImpl(), supplierRight,
                        AesCtrDrbg::new);

                CRTDataSupplier<SpdzResourcePool, SpdzResourcePool> dataSupplier = new CRTDummyDataSupplier<>(myId, noParties, CRTDataSupplier.DEFAULT_STATSECURITY,
                        DEFAULT_FIELD_LEFT, DEFAULT_FIELD_RIGHT,
                        x -> Utils.fromBigInteger(DEFAULT_FIELD_LEFT, SECRET_SHARED_KEY, myId, x),
                        x -> Utils.fromBigInteger(DEFAULT_FIELD_RIGHT, SECRET_SHARED_KEY, myId, x));

                CRTResourcePool<SpdzResourcePool, SpdzResourcePool> rp =
                        new CRTResourcePoolImpl<>(myId, noParties, dataSupplier, rpLeft, rpRight);

                ProtocolSuiteNumeric<CRTResourcePool<SpdzResourcePool, SpdzResourcePool>> ps =
                        new CRTProtocolSuite<>(
                                new SpdzBuilder(new BasicNumericContext(DEFAULT_FIELD_LEFT.getBitLength(),
                                        myId, noParties, DEFAULT_FIELD_LEFT, 16, 40)),
                                new SpdzBuilder(new BasicNumericContext(DEFAULT_FIELD_RIGHT.getBitLength(),
                                        myId, noParties, DEFAULT_FIELD_RIGHT, 16, 40)));

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

                System.out.println("================== Metrics ==================");
                System.out.println("Evaluation: " + ((BatchEvaluationLoggingDecorator<?>) strategy).getLoggedValues());
                System.out.println("Network: " + ((NetworkLoggingDecorator) network).getLoggedValues());
                System.out.println("Arithmetic: " + ((NumericSuiteLogging<?>) ps).getLoggedValues());
                System.out.println("=============================================");
                break;
            }

            case SPDZ: {
                ProtocolSuiteNumeric<SpdzResourcePool> suite = new SpdzProtocolSuite(maxBitLength);

                // Use "dummy" multiplication triples to simulate doing only the online phase
                SpdzDataSupplier supplier = new SpdzDummyDataSupplier(myId, noParties, DEFAULT_FIELD,
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
                                maxBatchSize);

                SecureComputationEngine<SpdzResourcePool, ProtocolBuilderNumeric> sce = new SecureComputationEngineImpl<>(
                        suite, evaluator);

                start = Instant.now();
                results = sce
                        .runApplication(application,
                                rp, network, Duration.ofMinutes(30));

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

}