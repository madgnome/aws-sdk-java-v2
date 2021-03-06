package software.amazon.awssdk.services.jsonprotocoltests.model;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Generated;

@Generated("software.amazon.awssdk:codegen")
final class ListOfEnumsCopier {
    static List<String> copy(Collection<String> listOfEnumsParam) {
        if (listOfEnumsParam == null) {
            return null;
        }
        List<String> listOfEnumsParamCopy = new ArrayList<>(listOfEnumsParam);
        return Collections.unmodifiableList(listOfEnumsParamCopy);
    }
}
