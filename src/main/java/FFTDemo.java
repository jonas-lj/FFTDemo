import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.DRes;
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
import dk.alexandra.fresco.framework.util.AesCtrDrbg;
import dk.alexandra.fresco.lib.field.integer.BasicNumericContext;
import dk.alexandra.fresco.lib.fixed.AdvancedFixedNumeric;
import dk.alexandra.fresco.lib.fixed.FixedNumeric;
import dk.alexandra.fresco.stat.complex.Open;
import dk.alexandra.fresco.stat.complex.OpenComplex;
import dk.alexandra.fresco.stat.complex.SecretComplex;
import dk.alexandra.fresco.stat.linearalgebra.FFT;
import dk.alexandra.fresco.suite.crt.CRTProtocolSuite;
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

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class FFTDemo {

  protected static final FieldDefinition DEFAULT_FIELD_LEFT =
          MersennePrimeFieldDefinition.find(64);
  protected static final FieldDefinition DEFAULT_FIELD_RIGHT = new BigIntegerFieldDefinition(
          new BigInteger(152 + 40, new Random(1234)).nextProbablePrime());
  
  protected static final FieldDefinition DEFAULT_FIELD = new BigIntegerFieldDefinition(
          new BigInteger(256, new Random(1234)).nextProbablePrime());

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 2) {
      throw new IllegalArgumentException("Usage: java Demo [myId] [otherIP]");
    }

    // Configure fresco
    final int myId = Integer.parseInt(arguments[0]);
    final String otherIP = arguments[1];
    final int noParties = 2;
    final int otherId = 3 - myId;
    final int modBitLength = 256;
    final int maxBitLength = 180;
    final int maxBatchSize = 4096;

    Party me = new Party(myId, "localhost", 9000 + myId);
    Party other = new Party(otherId, otherIP, 9000 + otherId);
    NetworkConfiguration networkConfiguration = new NetworkConfigurationImpl(myId,
        Map.of(myId, me, otherId, other));
    Network network = new SocketNetwork(networkConfiguration);

//    SpdzProtocolSuite suite = new SpdzProtocolSuite(maxBitLength);
//
//    // Use "dummy" multiplication triples to simulate doing only the online phase
//    SpdzDataSupplier supplier = new SpdzDummyDataSupplier(myId, noParties, DEFAULT_FIELD,
//            BigInteger.valueOf(1234));
//
//    SpdzResourcePool rp = new SpdzResourcePoolImpl(myId, noParties,
//            new SpdzOpenedValueStoreImpl(), supplier,
//            AesCtrDrbg::new);
//
//    BatchedProtocolEvaluator<SpdzResourcePool> evaluator =
//            new BatchedProtocolEvaluator<>(EvaluationStrategy.SEQUENTIAL_BATCHED.getStrategy(), suite,
//                    maxBatchSize);
//    SecureComputationEngine<SpdzResourcePool, ProtocolBuilderNumeric> sce = new SecureComputationEngineImpl<>(
//            suite, evaluator);

    BatchEvaluationStrategy<CRTResourcePool<SpdzResourcePool, SpdzResourcePool>> batchEvaluationStrategy =
            new CRTSequentialStrategy<>();

        SpdzDataSupplier supplierLeft = new SpdzDummyDataSupplier(myId, noParties, DEFAULT_FIELD_LEFT,
            BigInteger.valueOf(1234));
    SpdzResourcePool rpLeft = new SpdzResourcePoolImpl(myId, noParties,
            new SpdzOpenedValueStoreImpl(), supplierLeft,
            AesCtrDrbg::new);
    SpdzDataSupplier supplierRight = new SpdzDummyDataSupplier(myId, noParties, DEFAULT_FIELD_RIGHT,
            BigInteger.valueOf(1234));
    SpdzResourcePool rpRight = new SpdzResourcePoolImpl(myId, noParties,
            new SpdzOpenedValueStoreImpl(), supplierRight,
            AesCtrDrbg::new);
    CRTDataSupplier<SpdzResourcePool, SpdzResourcePool> dataSupplier = new CRTCovertDataSupplier<>(null);

    CRTResourcePool<SpdzResourcePool, SpdzResourcePool> rp =
            new CRTResourcePoolImpl<>(myId, noParties, dataSupplier, rpLeft, rpRight);

    CRTProtocolSuite<SpdzResourcePool, SpdzResourcePool> ps =
            new CRTProtocolSuite<>(
                    new SpdzBuilder(new BasicNumericContext(DEFAULT_FIELD_LEFT.getBitLength(),
                            myId, noParties, DEFAULT_FIELD_LEFT, 16, 40)),
                    new SpdzBuilder(new BasicNumericContext(DEFAULT_FIELD_RIGHT.getBitLength(),
                            myId, noParties, DEFAULT_FIELD_RIGHT, 16, 40)));
    ProtocolEvaluator<CRTResourcePool<SpdzResourcePool,
            SpdzResourcePool>> evaluator =
            new BatchedProtocolEvaluator<>(batchEvaluationStrategy, ps);

    SecureComputationEngine<CRTResourcePool<SpdzResourcePool,
            SpdzResourcePool>, ProtocolBuilderNumeric> sce =
            new SecureComputationEngineImpl<>(ps, evaluator);

    System.out.println("Loading CRT fixed numeric...");
    FixedNumeric.load(CRTFixedNumeric::new);
    AdvancedFixedNumeric.load(CRTAdvancedFixedNumeric::new);

    Instant start = Instant.now();

    List<OpenComplex> out = sce
        .runApplication(new FFTApp(2048),
            rp, network, Duration.ofMinutes(30));

    System.out.println(out);
    System.out.println("Took " + Duration.between(start, Instant.now()));
  }

  public static class FFTApp implements
      Application<List<OpenComplex>, ProtocolBuilderNumeric> {

    private final int n;

    public FFTApp(int n) {
      this.n = n;
    }

    @Override
    public DRes<List<OpenComplex>> buildComputation(ProtocolBuilderNumeric builder) {
      return builder.par(par -> {
        ArrayList<DRes<SecretComplex>> inputs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
          inputs.add(new SecretComplex(FixedNumeric.using(par).known(i), FixedNumeric.using(par).known(0)));
        }
        return DRes.of(inputs);
      }).seq((seq, inputs) -> new FFT(inputs).buildComputation(seq)).
      par((par, result) ->
        DRes.of(result.stream().map(z -> new Open(z.out()).buildComputation(par)).collect(Collectors.toList()))
      ).par((par, result) -> DRes.of(result.stream().map(DRes::out).collect(Collectors.toList()))
    );
    }
  }
}