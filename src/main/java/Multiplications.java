import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.value.SInt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class Multiplications {

    public static void main(String[] arguments) {
        if (arguments.length < 6) {
            throw new IllegalArgumentException("Usage: java -jar Demo [domainInBits] [statSec] [batchSize] [CRT/SPDZ] [myId] [otherIP1] ([otherIP2] ...)");
        }

        int domainInBits = Integer.parseInt(arguments[0]);
        int statsec = Integer.parseInt(arguments[1]);
        int batchSize = Integer.parseInt(arguments[2]);
        DemoOnline.Scheme scheme = DemoOnline.Scheme.valueOf(arguments[3]);
        int myId = Integer.parseInt(arguments[4]);
        List<String> otherIPs = new ArrayList<>();
        for (int i = 5; i < arguments.length; i++) {
            otherIPs.add(arguments[i]);
        }
        new DemoOnline<List<BigInteger>>().run(myId, otherIPs, domainInBits, statsec, batchSize, scheme, DemoOnline.Strategy.Dummy, new MultiplicationApplication(batchSize));
    }

    /**
     * Compute n multiplications in parallel
     */
    public static class MultiplicationApplication implements
            Application<List<BigInteger>, ProtocolBuilderNumeric> {

        private final int batchSize;
        private final Random random;

        public MultiplicationApplication(int batchSize) {
            this.batchSize = batchSize;
            this.random = new Random(1234);
        }

        @Override
        public DRes<List<BigInteger>> buildComputation(ProtocolBuilderNumeric builder) {
            return builder.par(par -> {
                ArrayList<DRes<SInt>> inputs = new ArrayList<>();
                for (int i = 0; i < 2 * batchSize; i++) {
                    inputs.add(par.numeric().known(random.nextInt()));
                }
                return DRes.of(inputs);
            }).par((par, inputs) -> {
                ArrayList<DRes<SInt>> outputs = new ArrayList<>();
                for (int i = 0; i < batchSize; i++) {
                    outputs.add(par.numeric().mult(inputs.get(i), inputs.get(i + batchSize)));
                }
                return DRes.of(outputs);
            }).par((par, result) ->
                    DRes.of(result.stream().map(z -> par.numeric().open(z)).collect(Collectors.toList()))
            ).par((par, result) -> DRes.of(result.stream().map(DRes::out).collect(Collectors.toList())));
        }
    }


}
