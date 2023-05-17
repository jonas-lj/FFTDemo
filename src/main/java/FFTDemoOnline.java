import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.lib.fixed.FixedNumeric;
import dk.alexandra.fresco.stat.complex.Open;
import dk.alexandra.fresco.stat.complex.OpenComplex;
import dk.alexandra.fresco.stat.complex.SecretComplex;
import dk.alexandra.fresco.stat.linearalgebra.FFT;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FFTDemoOnline {

    public static void main(String[] arguments) {
        if (arguments.length != 5) {
            throw new IllegalArgumentException("Usage: java Demo [myId] [otherIP1] [otherIP2] [n] [CRT/SPDZ]");
        }

        int myId = Integer.parseInt(arguments[0]);
        String otherIP1 = arguments[1];
        String otherIP2 = arguments[2];
        int n = Integer.parseInt(arguments[3]);
        DemoOnline.Scheme scheme = DemoOnline.Scheme.valueOf(arguments[4]);

        new DemoOnline<List<OpenComplex>>().run(myId, otherIP1, otherIP2, scheme, new FFTApplication(n));
    }

    /**
     * Compute the fast fourier transform of <i>(0,...,0) &isin; &#8450;<sup>n</sup></i>.
     */
    public static class FFTApplication implements
            Application<List<OpenComplex>, ProtocolBuilderNumeric> {

        private final int n;

        public FFTApplication(int n) {
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
            }).seq((seq, inputs) -> new FFT(inputs).buildComputation(seq)).par((par, result) ->
                    DRes.of(result.stream().map(z -> new Open(z.out()).buildComputation(par)).collect(Collectors.toList()))
            ).par((par, result) -> DRes.of(result.stream().map(DRes::out).collect(Collectors.toList())));
        }
    }
}
