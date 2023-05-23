import dk.alexandra.fresco.framework.Party;
import dk.alexandra.fresco.framework.ProtocolEvaluator;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.builder.numeric.field.BigIntegerFieldDefinition;
import dk.alexandra.fresco.framework.builder.numeric.field.FieldDefinition;
import dk.alexandra.fresco.framework.builder.numeric.field.FieldElement;
import dk.alexandra.fresco.framework.configuration.NetworkConfiguration;
import dk.alexandra.fresco.framework.configuration.NetworkConfigurationImpl;
import dk.alexandra.fresco.framework.network.Network;
import dk.alexandra.fresco.framework.network.socket.SocketNetwork;
import dk.alexandra.fresco.framework.util.*;
import dk.alexandra.fresco.logging.NetworkLoggingDecorator;
import dk.alexandra.fresco.tools.mascot.Mascot;
import dk.alexandra.fresco.tools.mascot.MascotResourcePool;
import dk.alexandra.fresco.tools.mascot.MascotResourcePoolImpl;
import dk.alexandra.fresco.tools.mascot.MascotSecurityParameters;
import dk.alexandra.fresco.tools.mascot.field.MultiplicationTriple;
import dk.alexandra.fresco.tools.ot.base.BigIntNaorPinkas;
import dk.alexandra.fresco.tools.ot.base.DummyOt;
import dk.alexandra.fresco.tools.ot.base.Ot;
import dk.alexandra.fresco.tools.ot.otextension.RotList;

import java.io.Closeable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalField;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static dk.alexandra.fresco.suite.crt.datatypes.resource.CRTDataSupplier.DEFAULT_STATSECURITY;

public class DemoMascot {
    private static List<Mascot> mascots;
    private static Closeable toClose;
    private static MascotSecurityParameters parameters = new MascotSecurityParameters();

    public static void main(String[] arguments) {
        if (arguments.length < 6) {
            throw new IllegalArgumentException("Usage: java -jar mascot.jar [domainInBits] [statSec] [batchSize] [SPDZ/CRT] [myId] [otherIP1] ([otherIP2] ...)");
        }

        int domainInBits = Integer.parseInt(arguments[0]);
        int statsec = Integer.parseInt(arguments[1]);
        int batchSize = Integer.parseInt(arguments[2]);
        DemoOnline.Scheme strategy = DemoOnline.Scheme.valueOf(arguments[3]);
        int myId = Integer.parseInt(arguments[4]);
        List<String> otherIPs = new ArrayList<>();
        for (int i = 5; i < arguments.length; i++) {
            otherIPs.add(arguments[i]);
        }
        run(myId, otherIPs, domainInBits, statsec, batchSize, strategy);
    }

    public static void run(int myId, List<String> otherIPs, int domainInBits, int statsec, int batchSize, DemoOnline.Scheme preprocessingStrategy) {
        Map<Integer, Party> parties = Utils.setupParties(myId, otherIPs);
        NetworkConfiguration networkConfiguration = new NetworkConfigurationImpl(myId, parties);
        Network network =  new NetworkLoggingDecorator(new SocketNetwork(networkConfiguration));

        if (preprocessingStrategy == DemoOnline.Scheme.SPDZ) {
            FieldDefinition fieldDefinition =
                    new BigIntegerFieldDefinition(ModulusFinder.findSuitableModulus(domainInBits));
            MascotResourcePool resourcePool = defaultResourcePool(myId, parties.size(), fieldDefinition,
                    network);
            FieldElement macKeyShare = resourcePool.getLocalSampler().getNext();
            toClose = (Closeable) network;
            mascots = Arrays.asList(new Mascot(resourcePool, network, macKeyShare));
            System.out.println("Bits available for computation: " +  domainInBits);
        } else {
            CRTFieldParams crtParams = new CRTFieldParams(domainInBits, statsec, parties.size());
            MascotResourcePool resourcePoolLeft = defaultResourcePool(myId, parties.size(), crtParams.getP(),
                    network);
            MascotResourcePool resourcePoolRight = defaultResourcePool(myId, parties.size(), crtParams.getQ(),
                    network);
            FieldElement macKeyShareLeft = resourcePoolLeft.getLocalSampler().getNext();
            FieldElement macKeyShareRight = resourcePoolRight.getLocalSampler().getNext();
            toClose = (Closeable) network;
            mascots = Arrays.asList(new Mascot(resourcePoolLeft, network, macKeyShareLeft),
                    new Mascot(resourcePoolRight, network, macKeyShareRight));
            System.out.println("Bits in P: " + crtParams.getP().getModulus().bitLength());
            System.out.println("Bits in Q: " + crtParams.getQ().getModulus().bitLength());
            System.out.println("Bits available for computation: " +  crtParams.getMaxAllowedValue().bitLength());
        }
        run(1, batchSize);
        System.out.println("================== Metrics ==================");
        System.out.println("Network: " + ((NetworkLoggingDecorator) network).getLoggedValues());
        System.out.println("=============================================");
    }

    private static void run(int numIts, int numTriples) {
        for (int i = 0; i < numIts; i++) {
            System.out.println("Generating another triple batch.");
            Instant startTime = Instant.now();
            List<MultiplicationTriple> triples = null;
            for (Mascot cur: mascots) {
                triples = cur.getTriples(numTriples);
            }
            Instant endTime = Instant.now();
            long total = endTime.toEpochMilli() - startTime.toEpochMilli();
            System.out.println("Generated " + triples.size() + " triples in " + total + " ms");
        }
        Callable<Void> closeTask = () -> {
            toClose.close();
            return null;
        };
        ExceptionConverter.safe(closeTask, "Failed closing network");
    }


    private static MascotResourcePool defaultResourcePool(int myId, int noOfParties, FieldDefinition field,
                                                   Network network) {
        // generate random seed for local DRBG
        byte[] drbgSeed = new byte[parameters.getPrgSeedLength() / 8];
        new SecureRandom().nextBytes(drbgSeed);
        Drbg drbg = AesCtrDrbgFactory.fromDerivedSeed(drbgSeed);
        Map<Integer, RotList> seedOts = new HashMap<>();
        for (int otherId = 1; otherId <= noOfParties; otherId++) {
            if (myId != otherId) {
                Ot ot = new DummyOt(otherId, network);
                RotList currentSeedOts = new RotList(drbg, parameters.getPrgSeedLength());
                if (myId < otherId) {
                    currentSeedOts.send(ot);
                    currentSeedOts.receive(ot);
                } else {
                    currentSeedOts.receive(ot);
                    currentSeedOts.send(ot);
                }
                seedOts.put(otherId, currentSeedOts);
            }
        }
        int instanceId = 1;
        return new MascotResourcePoolImpl(
                myId, noOfParties, instanceId, drbg, seedOts, parameters, field);
    }
}
