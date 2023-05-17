import dk.alexandra.fresco.framework.builder.numeric.field.FieldDefinition;
import dk.alexandra.fresco.suite.spdz.datatypes.SpdzSInt;

import java.math.BigInteger;

public class Utils {

    public static SpdzSInt fromBigInteger(FieldDefinition field, BigInteger secretKey, int myId, BigInteger x) {
        if (myId == 1) {
            return new SpdzSInt(field.createElement(x), field.createElement(x).multiply(field.createElement(secretKey)));
        } else {
            return new SpdzSInt(field.createElement(0), field.createElement(0));
        }
    }

}
