import dk.alexandra.fresco.framework.builder.numeric.field.BigIntegerFieldDefinition;
import dk.alexandra.fresco.framework.util.ModulusFinder;

import java.math.BigInteger;
import java.util.Random;

// TODO should be refactored into the actual CRT code and integrated with the CRTResourcePool
public class CRTFieldParams {
    private final BigIntegerFieldDefinition p;
    private final BigIntegerFieldDefinition q;
    private final BigInteger maxAllowedValue;

    public CRTFieldParams(int computationSpaceInBits, int statSec, int parties) {
        p = getP(computationSpaceInBits, statSec);
        q = getQ(p, computationSpaceInBits, statSec, parties);
        BigInteger freeSpaceNeeded = amountOfFreeSpaceNeeded(p.getModulus(), statSec, parties);
        maxAllowedValue = p.getModulus().multiply(q.getModulus()).subtract(freeSpaceNeeded);
    }

    public BigIntegerFieldDefinition getP() {
        return p;
    }

    public BigIntegerFieldDefinition getQ() {
        return q;
    }

    public BigInteger getMaxAllowedValue() {
        return maxAllowedValue;
    }

    protected static BigIntegerFieldDefinition getQ(BigIntegerFieldDefinition p, int computationSpaceInBits, int statSec, int parties) {
        BigInteger extraSpace = amountOfFreeSpaceNeeded(p.getModulus(), statSec, parties);
        BigInteger QCand =(BigInteger.TWO.pow(computationSpaceInBits).add(extraSpace)).divide(p.getModulus());
        int adjustedLength = adjustedUpBits(QCand.bitLength());
        return new BigIntegerFieldDefinition(findQ(p.getModulus(), adjustedLength));
    }

    protected static BigIntegerFieldDefinition getP(int computationSpaceInBits, int statSec) {
        int minBitsNeeded = minBitsForP(computationSpaceInBits, statSec);
        return new BigIntegerFieldDefinition(ModulusFinder.findSuitableModulus(minBitsNeeded));
    }

    protected static int minBitsForP(int computationSpaceInBits, int statSec) {
        int minBitsNeeded;
        // -7 to allow for buffer bits in relation to the amount of parties and adjustment
        if ((computationSpaceInBits-statSec-7)/2 <= statSec) {
            minBitsNeeded = adjustedUpBits(statSec);
        } else {
            minBitsNeeded = adjustedDownBits((computationSpaceInBits-statSec-7)/2);
        }
        return minBitsNeeded;
    }

    private static BigInteger findQ(BigInteger p, int minBitsNeeded) {
        BigInteger cand = BigInteger.TWO.multiply(p);
        int constSize = minBitsNeeded - cand.bitLength();
        // Ensure cand is of correct size
        cand = cand.multiply(BigInteger.TWO.pow(constSize));
        cand = cand.add(BigInteger.ONE);
        // Subtract p until we reach a prime
        while (!cand.isProbablePrime(40)) {
            cand = cand.add(p);
        }
        return cand;
    }

    private static int adjustedDownBits(int minBitsNeeded) {
        if (minBitsNeeded % 8 != 0) {
            // rounding to ensure divisibility with 8
            minBitsNeeded -= minBitsNeeded % 8;
        }
        return minBitsNeeded;
    }

    private static int adjustedUpBits(int minBitsNeeded) {
        if (minBitsNeeded % 8 != 0) {
            // rounding to ensure divisibility with 8
            minBitsNeeded += 8-(minBitsNeeded % 8);
        }
        return minBitsNeeded;
    }

    protected static BigInteger amountOfFreeSpaceNeeded(BigInteger p, int statSec, int parties) {
        BigInteger partyPart = BigInteger.valueOf(parties).add(BigInteger.ONE).multiply(BigInteger.valueOf(parties));
        BigInteger temp =  p.pow(2).multiply(BigInteger.TWO.pow(statSec).add(BigInteger.ONE));
        return temp.multiply(partyPart).add(BigInteger.valueOf(parties).multiply(p));
    }

    @Override
    public String toString() {
        return "P bits: " + p.getModulus().bitLength() + "\nQ Bits: " + q.getModulus().bitLength() + "\nBits in permissible domain: " + maxAllowedValue.bitLength();
    }
}
