package works.earendil.pi.orchestrator.ipc;

import java.util.List;

public sealed interface ProtocolMessage permits ProtocolMessage.SpawnRequest, ProtocolMessage.ListRequest,
        ProtocolMessage.StopRequest, ProtocolMessage.StatusRequest, ProtocolMessage.RpcRequest,
        ProtocolMessage.Response {
    String type();

    record SpawnRequest(String cwd, List<String> args) implements ProtocolMessage {
        @Override
        public String type() {
            return "spawn";
        }
    }

    record ListRequest() implements ProtocolMessage {
        @Override
        public String type() {
            return "list";
        }
    }

    record StopRequest(String id) implements ProtocolMessage {
        @Override
        public String type() {
            return "stop";
        }
    }

    record StatusRequest(String id) implements ProtocolMessage {
        @Override
        public String type() {
            return "status";
        }
    }

    record RpcRequest(String id, String line) implements ProtocolMessage {
        @Override
        public String type() {
            return "rpc";
        }
    }

    record Response(boolean ok, String requestType, Object payload, String error) implements ProtocolMessage {
        @Override
        public String type() {
            return ok ? requestType + "_response" : "error";
        }
    }
}
