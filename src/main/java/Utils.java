import dk.alexandra.fresco.framework.Party;
import dk.alexandra.fresco.framework.builder.numeric.field.FieldDefinition;
import dk.alexandra.fresco.suite.spdz.datatypes.SpdzSInt;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Utils {

    public static SpdzSInt fromBigInteger(FieldDefinition field, BigInteger secretKey, int myId, BigInteger x) {
        if (myId == 1) {
            return new SpdzSInt(field.createElement(x), field.createElement(x).multiply(field.createElement(secretKey)));
        } else {
            return new SpdzSInt(field.createElement(0), field.createElement(0));
        }
    }


    public static Map<Integer, Party> setupParties(int myId, List<String> otherIPs) {
        Party me = new Party(myId, "localhost", 9000 + myId);
        Map<Integer, Party> parties = new HashMap<>();
        parties.put(myId, me);

        List<Integer> otherIds = IntStream.range(1, otherIPs.size()+2).boxed().collect(Collectors.toList());
        otherIds.remove(Integer.valueOf(myId));
        for (int i = 0; i <otherIds.size(); i++) {
            int id = otherIds.get(i);
            parties.put(id, new Party(id, otherIPs.get(i), 9000 + id));
        }
        System.out.println("Parties: " + parties.size());
        System.out.println();
        return parties;
    }


}
