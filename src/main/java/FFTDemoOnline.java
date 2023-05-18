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
        new DemoOnline<List<OpenComplex>>().run(myId, otherIPs, domainInBits, statsec, batchSize, strategy, new FFTApplication(batchSize));
    }

    /**
     * Compute the fast fourier transform of <i>(0,...,0) &isin; &#8450;<sup>n</sup></i>.
     */
    public static class FFTApplication implements
            Application<List<OpenComplex>, ProtocolBuilderNumeric> {

        private final int batchSize;

        public FFTApplication(int batchSize) {
            this.batchSize = batchSize;
        }

        @Override
        public DRes<List<OpenComplex>> buildComputation(ProtocolBuilderNumeric builder) {
            return builder.par(par -> {
                ArrayList<DRes<SecretComplex>> inputs = new ArrayList<>();
                for (int i = 0; i < batchSize; i++) {
                    inputs.add(new SecretComplex(FixedNumeric.using(par).known(i), FixedNumeric.using(par).known(i)));
                }
                return DRes.of(inputs);
            }).seq((seq, inputs) -> new FFT(inputs).buildComputation(seq)).par((par, result) ->
                    DRes.of(result.stream().map(z -> new Open(z.out()).buildComputation(par)).collect(Collectors.toList()))
            ).par((par, result) -> DRes.of(result.stream().map(DRes::out).collect(Collectors.toList())));
        }
    }
}
