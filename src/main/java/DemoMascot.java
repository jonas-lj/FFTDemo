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
    private static final int TRIPLES = 1024;
    private static List<Mascot> mascots;
    private static Closeable toClose;
    private static MascotSecurityParameters parameters = new MascotSecurityParameters();

    public static void main(String[] arguments) {
        if (arguments.length < 5) {
            throw new IllegalArgumentException("Usage: java -jar mascot.jar [n] [statSec] [SPDZ/CRT] [myId] [otherIP1] ([otherIP2] ...)");
        }

        int n = Integer.parseInt(arguments[0]);
        int statsec = Integer.parseInt(arguments[1]);
        DemoOnline.Scheme strategy = DemoOnline.Scheme.valueOf(arguments[2]);
        int myId = Integer.parseInt(arguments[3]);
        List<String> otherIPs = new ArrayList<>();
        for (int i = 4; i < arguments.length; i++) {
            otherIPs.add(arguments[i]);
        }
        run(myId, otherIPs, n, statsec, strategy);
    }

    public static void run(int myId, List<String> otherIPs, int n, int statsec, DemoOnline.Scheme preprocessingStrategy) {
        Map<Integer, Party> parties = setupParties(myId, otherIPs);
        NetworkConfiguration networkConfiguration = new NetworkConfigurationImpl(myId, parties);
        Network network =  new NetworkLoggingDecorator(new SocketNetwork(networkConfiguration));

        if (preprocessingStrategy == DemoOnline.Scheme.SPDZ) {
            FieldDefinition fieldDefinition =
                    new BigIntegerFieldDefinition(ModulusFinder.findSuitableModulus(n));
            MascotResourcePool resourcePool = defaultResourcePool(myId, parties.size(), fieldDefinition,
                    network);
            FieldElement macKeyShare = resourcePool.getLocalSampler().getNext();
            toClose = (Closeable) network;
            mascots = Arrays.asList(new Mascot(resourcePool, network, macKeyShare));
            System.out.println("Bits available for computation: " +  n);
        } else {
            CRTFieldParams crtParams = new CRTFieldParams(n, statsec, parties.size());
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
        run(1, TRIPLES);
        System.out.println("================== Metrics ==================");
        System.out.println("Network: " + ((NetworkLoggingDecorator) network).getLoggedValues());
        System.out.println("=============================================");
    }

    public static Map<Integer, Party> setupParties(int myId, List<String> otherIPs) {
        Party me = new Party(myId, "localhost", 9000 + myId);
        Map<Integer, Party> parties = new HashMap<>();
        parties.put(myId, me);

        List<Integer> otherIds = IntStream.range(1, otherIPs.size()+2).boxed().collect(Collectors.toList());
        otherIds.remove(Integer.valueOf(myId));
        for (int i = 0; i <otherIds.size(); i++) {
            int id = otherIds.get(i);
            parties.put(id, new Party(id, otherIPs.get(i), 9000 + id));
        }
        System.out.println("Parties: " + parties.size());
        System.out.println();
        return parties;
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
