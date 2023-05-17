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
        if (arguments.length != 5) {
            throw new IllegalArgumentException("Usage: java Demo [myId] [otherIP1] [otherIP2] [n] [CRT/SPDZ]");
        }

        int myId = Integer.parseInt(arguments[0]);
        String otherIP1 = arguments[1];
        String otherIP2 = arguments[2];
        int n = Integer.parseInt(arguments[3]);
        DemoOnline.Scheme scheme = DemoOnline.Scheme.valueOf(arguments[4]);

        new DemoOnline<List<BigDecimal>>().run(myId, otherIP1, otherIP2, scheme, new FixedMultiplicationApplication(n));
    }

    /**
     * Compute n multiplications in parallel
     */
    public static class FixedMultiplicationApplication implements
            Application<List<BigDecimal>, ProtocolBuilderNumeric> {

        private final int n;
        private final Random random;

        public FixedMultiplicationApplication(int n) {
            this.n = n;
            this.random = new Random(1234);
        }

        @Override
        public DRes<List<BigDecimal>> buildComputation(ProtocolBuilderNumeric builder) {
            return builder.par(par -> {
                ArrayList<DRes<SFixed>> inputs = new ArrayList<>();
                for (int i = 0; i < 2 * n; i++) {
                    FixedNumeric.using(par).known(new BigInteger(32, random).intValue());
                }
                return DRes.of(inputs);
            }).par((par, inputs) -> {
                ArrayList<DRes<SFixed>> outputs = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    outputs.add(FixedNumeric.using(par).mult(inputs.get(i), inputs.get(i + n)));
                }
                return DRes.of(outputs);
            }).par((par, result) ->
                    DRes.of(result.stream().map(z -> FixedNumeric.using(par).open(z)).collect(Collectors.toList()))
            ).par((par, result) -> DRes.of(result.stream().map(DRes::out).collect(Collectors.toList())));
        }
    }


}
