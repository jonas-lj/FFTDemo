import dk.alexandra.fresco.framework.Application;
import dk.alexandra.fresco.framework.DRes;
import dk.alexandra.fresco.framework.builder.numeric.ProtocolBuilderNumeric;
import dk.alexandra.fresco.framework.util.*;
import dk.alexandra.fresco.framework.value.SInt;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

public class SPDZBitGen {
    public static void main(String[] arguments) {
        if (arguments.length <5) {
            throw new IllegalArgumentException("Usage: java -jar SPDZBitGen [domainInBits] [statSec] [batchSize] [myId] [otherIP1] ([otherIP2] ...)");
        }

        int domainInBits = Integer.parseInt(arguments[0]);
        int statsec = Integer.parseInt(arguments[1]);
        int batchSize = Integer.parseInt(arguments[2]);
        int myId = Integer.parseInt(arguments[3]);
        List<String> otherIPs = new ArrayList<>();
        for (int i = 4; i < arguments.length; i++) {
            otherIPs.add(arguments[i]);
        }
        new DemoOnline<List<SInt>>().run(myId, otherIPs, domainInBits, statsec, batchSize, DemoOnline.Scheme.SPDZ, DemoOnline.Strategy.Dummy, new BitGen(batchSize, statsec));
    }

    public static class BitGen implements
            Application<List<SInt>, ProtocolBuilderNumeric> {

        private final int batchSize;
        private final int statSec;
        private final Random random;

        public BitGen(int batchSize, int statsec) {
            this.batchSize = batchSize;
            this.statSec = statsec;
            this.random = new Random(1234);
        }

        @Override
        public DRes<List<SInt>> buildComputation(ProtocolBuilderNumeric builder) {
            int amount = batchSize+statSec;
            return builder.par(par -> {
                List<DRes<SInt>> rands = new ArrayList();
                for (int i = 0; i < amount; i++) {
                    rands.add(par.numeric().randomElement());
                }
                return DRes.of(rands);
            }).par((par, rands) -> {
                List<DRes<SInt>> squares = new ArrayList();
                for (int i = 0; i < amount; i++) {
                    squares.add(par.numeric().mult(rands.get(i), rands.get(i)));
                }
                return DRes.of(new Pair<>(rands, squares));
            }).par((par, pair) -> {
                List<DRes<BigInteger>> opened = new ArrayList();
                for (int i = 0; i < amount; i++) {
                    opened.add(par.numeric().open(pair.getSecond().get(i)));
                }
                return DRes.of(new Pair<>(pair.getFirst(), opened));
            }).par((par, pair) -> {
                List<DRes<SInt>> bits = new ArrayList();
                for (int i = 0; i < amount; i++) {
                    // Assume p \equiv 3 mod 4
                    BigInteger curSqrt = pair.getSecond().get(i).out().modPow(
                            builder.getBasicNumericContext().getModulus().add(BigInteger.ONE).shiftRight(2),
                            builder.getBasicNumericContext().getModulus());
                    BigInteger curSqrtInv = curSqrt.modPow(curSqrt.subtract(BigInteger.valueOf(2)), builder.getBasicNumericContext().getModulus());
                    DRes<SInt> vr = par.numeric().mult(curSqrtInv, pair.getFirst().get(i));
                    DRes<SInt> vrPlusOne = par.numeric().add(BigInteger.ONE, vr);
                    BigInteger twoInv = BigInteger.valueOf(2).modPow(builder.getBasicNumericContext().getModulus().subtract(BigInteger.valueOf(2)), builder.getBasicNumericContext().getModulus());
                    DRes<SInt> bit = par.numeric().mult(twoInv, vrPlusOne);
                    bits.add(bit);
                }
                return DRes.of(bits);
            }).par((par, result) -> DRes.of(result.stream().map(DRes::out).collect(Collectors.toList())));
        }
    }
}
