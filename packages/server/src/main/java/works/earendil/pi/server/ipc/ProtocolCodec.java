package works.earendil.pi.server.ipc;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.common.json.JsonCodec;

public final class ProtocolCodec {
    private ProtocolCodec() {
    }

    public static String encode(ProtocolMessage message) {
        return JsonCodec.stringify(message);
    }

    public static JsonNode parseLine(String line) {
        return JsonCodec.parse(line);
    }
}
