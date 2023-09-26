import dk.alexandra.fresco.framework.builder.numeric.field.BigIntegerFieldDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CRTFieldParamTest {
    @Test
    void testParamSelect() {
        CRTFieldParams params;
        params = new CRTFieldParams(32, 60, 2);
        assertEquals(64, params.getP().getModulus().bitLength());
        assertEquals(128, params.getQ().getModulus().bitLength());
        assertEquals(192, params.getMaxAllowedValue().bitLength());
        assertEquals(BigInteger.ZERO, params.getQ().getModulus().subtract(BigInteger.ONE).shiftRight(1).mod(params.getP().getModulus()));
        assertTrue(params.getP().getModulus().isProbablePrime(40));
        assertTrue(params.getQ().getModulus().isProbablePrime(40));
        assertEquals(BigInteger.ZERO, params.getQ().getModulus().subtract(BigInteger.ONE).divide(BigInteger.TWO).mod(params.getP().getModulus()));
        assertEquals(BigInteger.ONE, params.getQ().getModulus().mod(params.getP().getModulus()));

        params = new CRTFieldParams(32, 40, 3);
        assertEquals(40, params.getP().getModulus().bitLength());
        assertEquals(88, params.getQ().getModulus().bitLength());
        assertEquals(128, params.getMaxAllowedValue().bitLength());
        assertEquals(BigInteger.ZERO, params.getQ().getModulus().subtract(BigInteger.ONE).shiftRight(1).mod(params.getP().getModulus()));
        assertTrue(params.getP().getModulus().isProbablePrime(40));
        assertTrue(params.getQ().getModulus().isProbablePrime(40));
        assertEquals(BigInteger.ZERO, params.getQ().getModulus().subtract(BigInteger.ONE).divide(BigInteger.TWO).mod(params.getP().getModulus()));
        assertEquals(BigInteger.ONE, params.getQ().getModulus().mod(params.getP().getModulus()));

        params = new CRTFieldParams(255, 40, 3);
        assertEquals(104, params.getP().getModulus().bitLength());
        // Notice the rounding of parameters mean we can get something a bit larger
        assertEquals(152, params.getQ().getModulus().bitLength());
        assertEquals(256, params.getMaxAllowedValue().bitLength());
        assertEquals(BigInteger.ZERO, params.getQ().getModulus().subtract(BigInteger.ONE).shiftRight(1).mod(params.getP().getModulus()));
        assertTrue(params.getP().getModulus().isProbablePrime(40));
        assertTrue(params.getQ().getModulus().isProbablePrime(40));
        assertEquals(BigInteger.ZERO, params.getQ().getModulus().subtract(BigInteger.ONE).divide(BigInteger.TWO).mod(params.getP().getModulus()));
        assertEquals(BigInteger.ONE, params.getQ().getModulus().mod(params.getP().getModulus()));

        params = new CRTFieldParams(512, 40, 3);
        assertEquals(232, params.getP().getModulus().bitLength());
        assertEquals(288, params.getQ().getModulus().bitLength());
        // Notice the rounding of parameters mean we can get something a bit larger
        assertEquals(520, params.getMaxAllowedValue().bitLength());
        assertEquals(BigInteger.ZERO, params.getQ().getModulus().subtract(BigInteger.ONE).shiftRight(1).mod(params.getP().getModulus()));
        assertTrue(params.getP().getModulus().isProbablePrime(40));
        assertTrue(params.getQ().getModulus().isProbablePrime(40));
        assertEquals(BigInteger.ZERO, params.getQ().getModulus().subtract(BigInteger.ONE).divide(BigInteger.TWO).mod(params.getP().getModulus()));
        assertEquals(BigInteger.ONE, params.getQ().getModulus().mod(params.getP().getModulus()));

        params = new CRTFieldParams(511, 60, 3);
        assertEquals(216, params.getP().getModulus().bitLength());
        assertEquals(296, params.getQ().getModulus().bitLength());
        assertEquals(512, params.getMaxAllowedValue().bitLength());
        assertEquals(BigInteger.ZERO, params.getQ().getModulus().subtract(BigInteger.ONE).shiftRight(1).mod(params.getP().getModulus()));
        assertTrue(params.getP().getModulus().isProbablePrime(40));
        assertTrue(params.getQ().getModulus().isProbablePrime(40));
        assertEquals(BigInteger.ZERO, params.getQ().getModulus().subtract(BigInteger.ONE).divide(BigInteger.TWO).mod(params.getP().getModulus()));
        assertEquals(BigInteger.ONE, params.getQ().getModulus().mod(params.getP().getModulus()));

    }

}
