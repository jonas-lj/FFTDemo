import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.stat.complex.OpenComplex;
import dk.alexandra.fresco.suite.crt.datatypes.CRTCombinedPad;
import dk.alexandra.fresco.suite.crt.datatypes.resource.*;

import java.util.ArrayList;
import java.util.List;

public class CRTPreprocessing {
    public static void main(String[] arguments) {
        if (arguments.length < 7) {
            throw new IllegalArgumentException("Usage: java -jar CRTPreprocessing [domainInBits] [statSec] [deterrence] [batchSize] [Covert/SemiHonest] [myId] [otherIP1] ([otherIP2] ...)");
        }

        int domainInBits = Integer.parseInt(arguments[0]);
        int statsec = Integer.parseInt(arguments[1]);
        int deterrence = Integer.parseInt(arguments[2]);
        int batchSize = Integer.parseInt(arguments[3]);
        DemoOnline.Strategy strategy = DemoOnline.Strategy.valueOf(arguments[4]);
        int myId = Integer.parseInt(arguments[5]);
        List<String> otherIPs = new ArrayList<>();
        for (int i = 6; i < arguments.length; i++) {
            otherIPs.add(arguments[i]);
        }
        new DemoOnline<List<CRTCombinedPad>>().run(myId, otherIPs, domainInBits, statsec, deterrence, batchSize, DemoOnline.Scheme.CRT, strategy,
                new PreprocessingApplication(strategy, batchSize, statsec, deterrence));
    }


    public static class PreprocessingApplication implements Application<List<CRTCombinedPad>, ProtocolBuilderNumeric> {
        private final DemoOnline.Strategy strategy;
        private final int batchSize;
        private final int statSec;
        private final int deterrence;

        public PreprocessingApplication(DemoOnline.Strategy strategy, int batchSize, int statSec, int deterrence) {
            this.strategy = strategy;
            this.batchSize = batchSize;
            this.statSec = statSec;
            this.deterrence = deterrence;
        }

        @Override
        public DRes<List<CRTCombinedPad>> buildComputation(ProtocolBuilderNumeric protocolBuilder) {
            if (strategy == DemoOnline.Strategy.SemiHonest) {
                return protocolBuilder.seq(new SemiHonestNoiseGenerator<>(batchSize, statSec));
            } else if (strategy == DemoOnline.Strategy.Covert) {
                return protocolBuilder.seq(new CovertNoiseGenerator<>(batchSize, deterrence, statSec));
            } else {
                throw new IllegalArgumentException("Unsupported strategy: " + strategy);
            }
        }
    }

}
