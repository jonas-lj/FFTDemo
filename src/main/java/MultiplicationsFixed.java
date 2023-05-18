import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.lib.fixed.FixedNumeric;
import dk.alexandra.fresco.lib.fixed.SFixed;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class MultiplicationsFixed {

    public static void main(String[] arguments) {
        if (arguments.length < 6) {
            throw new IllegalArgumentException("Usage: java -jar Demo [domainInBits] [statSec] [batchSize] [CRT/SPDZ] [myId] [otherIP1] ([otherIP2] ...)");
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
        new DemoOnline<List<BigDecimal>>().run(myId, otherIPs, domainInBits, statsec, batchSize, strategy, new FixedMultiplicationApplication(batchSize));
    }

    /**
     * Compute n multiplications in parallel
     */
    public static class FixedMultiplicationApplication implements
            Application<List<BigDecimal>, ProtocolBuilderNumeric> {

        private final int batchSize;
        private final Random random;

        public FixedMultiplicationApplication(int batchSize) {
            this.batchSize = batchSize;
            this.random = new Random(1234);
        }

        @Override
        public DRes<List<BigDecimal>> buildComputation(ProtocolBuilderNumeric builder) {
            return builder.par(par -> {
                ArrayList<DRes<SFixed>> inputs = new ArrayList<>();
                for (int i = 0; i < 2 * batchSize; i++) {
                    inputs.add(FixedNumeric.using(par).known(random.nextDouble()));
                }
                return DRes.of(inputs);
            }).par((par, inputs) -> {
                ArrayList<DRes<SFixed>> outputs = new ArrayList<>();
                for (int i = 0; i < batchSize; i++) {
                    outputs.add(FixedNumeric.using(par).mult(inputs.get(i), inputs.get(i + batchSize)));
                }
                return DRes.of(outputs);
            }).par((par, result) ->
                    DRes.of(result.stream().map(z -> FixedNumeric.using(par).open(z)).collect(Collectors.toList()))
            ).par((par, result) -> DRes.of(result.stream().map(DRes::out).collect(Collectors.toList())));
        }
    }


}
