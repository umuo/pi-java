package works.earendil.pi.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.ai.provider.StreamOptions;
import works.earendil.pi.common.json.JsonCodec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopTest {
    @Test
    void runsToolCallAndContinuesUntilAssistantStops() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Model model = new Model("test", "model", "Test Model", "test", 1000, 1000, true, false, Map.of());
        AgentTool echo = new AgentTool() {
            @Override
            public Tool definition() {
                return new Tool("echo", "Echo text", JsonCodec.parse("{}"), null);
            }

            @Override
            public AgentToolResult execute(Object input) {
                return new AgentToolResult(List.of(new Content.Text("echoed")), Map.of("input", input), false, false);
            }
        };
        Message.User prompt = new Message.User(List.of(new Content.Text("hello")), Instant.now());
        AgentContext context = new AgentContext(List.of(), "system", List.of(echo));
        List<AgentEvent> events = new ArrayList<>();

        List<AgentMessage> messages = AgentLoop.run(
                List.of(new AgentMessage.Llm(prompt)),
                context,
                new AgentLoop.Config(model, StreamOptions.defaults(), List::of, List::of, AgentLoop.ToolExecutionMode.SEQUENTIAL),
                (m, c, o) -> {
                    if (calls.getAndIncrement() == 0) {
                        JsonNode input = JsonCodec.parse("{\"text\":\"hello\"}");
                        return new Message.Assistant(List.of(new Content.ToolCall("call-1", "echo", input, List.of())),
                                "test", "model", StopReason.TOOL_USE, new Usage(1, 1, 0, 0, 0), null, Instant.now());
                    }
                    return new Message.Assistant(List.of(new Content.Text("done")),
                            "test", "model", StopReason.STOP, new Usage(2, 2, 0, 0, 0), null, Instant.now());
                },
                events::add);

        assertThat(calls.get()).isEqualTo(2);
        assertThat(messages).hasSize(4);
        assertThat(events.stream().map(AgentEvent::type)).containsSequence(
                "agent_start",
                "turn_start",
                "message_start",
                "message_end",
                "message_start",
                "message_end",
                "tool_execution_start",
                "tool_execution_end",
                "message_start",
                "message_end",
                "turn_end",
                "turn_start",
                "message_start",
                "message_end",
                "turn_end",
                "agent_end",
                "agent_settled");
    }
}
