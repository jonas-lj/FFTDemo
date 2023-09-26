import dk.alexandra.fresco.framework.builder.numeric.field.BigIntegerFieldDefinition;
import dk.alexandra.fresco.framework.util.ModulusFinder;

import java.math.BigInteger;
import java.util.Random;

// TODO should be refactored into the actual CRT code and integrated with the CRTResourcePool
public class CRTFieldParams {
    private final BigIntegerFieldDefinition p;
    private final BigIntegerFieldDefinition q;
    private final BigInteger maxAllowedValue;

    /**
     * Generate field moduli for CRT based on the given parameters.
     * @param computationSpaceInBits The number of bits needed to represent the largest value that will be used in the computation.
     * @param statisticalSecurity The statistical security parameter.
     * @param parties The number of parties that will be involved in the computation.
     */
    public CRTFieldParams(int computationSpaceInBits, int statisticalSecurity, int parties) {
        p = getP(computationSpaceInBits, statisticalSecurity);
        q = getQ(p, computationSpaceInBits, statisticalSecurity, parties);
        BigInteger freeSpaceNeeded = amountOfFreeSpaceNeeded(p.getModulus(), statisticalSecurity, parties);
        maxAllowedValue = p.getModulus().multiply(q.getModulus()).subtract(freeSpaceNeeded);
    }

    /** Get the modulus of the smaller of the two fields */
    public BigIntegerFieldDefinition getP() {
        return p;
    }

    /** Get the modulus of the larger of the two fields */
    public BigIntegerFieldDefinition getQ() {
        return q;
    }

    /** Get an upper bound for the largest value that can be used for computation in the CRT field */
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
        // Ensure that q = 1 mod 2p and is of the correct size
        BigInteger candidate = BigInteger.TWO.multiply(p);

        // Ensure candidate is of correct size
        candidate = candidate.multiply(BigInteger.TWO.pow(minBitsNeeded - candidate.bitLength()));

        candidate = candidate.add(BigInteger.ONE);
        // Subtract p until we reach a prime
        while (!candidate.isProbablePrime(40)) {
            candidate = candidate.subtract(p);
        }
        return candidate;
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
