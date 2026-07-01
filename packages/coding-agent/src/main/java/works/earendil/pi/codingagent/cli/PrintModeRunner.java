package works.earendil.pi.codingagent.cli;

import works.earendil.pi.agent.core.AgentEvent;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;

import java.util.List;

public final class PrintModeRunner {

    public static int run(AgentSessionRuntime runtime, CliArgs args) {
        AgentSession session = runtime.session();
        boolean jsonMode = "json".equalsIgnoreCase(args.mode);

        if (args.messages.isEmpty()) {
            System.err.println("Error: No prompt message specified for print mode.");
            return 1;
        }

        try {
            if (jsonMode) {
                session.subscribe(event -> {
                    if (event instanceof AgentSession.AgentSessionEvent.AgentEventEnvelope env) {
                        if (env.event() instanceof AgentEvent.MessageUpdate mu &&
                                mu.assistantMessageEvent() instanceof AssistantMessageEvent.ContentDelta cd &&
                                cd.content() instanceof Content.Text t) {
                            System.out.println("{\"type\":\"content_delta\",\"text\":\"" + escapeJson(t.text()) + "\"}");
                        }
                    } else if (event instanceof AgentSession.AgentSessionEvent.SkillCommand skillCommand) {
                        System.out.println("{\"type\":\"skill_command\",\"phase\":\"" + escapeJson(skillCommand.phase())
                                + "\",\"skill\":\"" + escapeJson(skillCommand.skillName()) + "\",\"path\":"
                                + jsonString(skillCommand.skillPath() == null ? null : skillCommand.skillPath().toString())
                                + ",\"message\":" + jsonString(skillCommand.message()) + "}");
                    }
                });
            }

            for (String prompt : args.messages) {
                session.prompt(prompt);
            }

            if (!jsonMode) {
                List<AgentMessage> msgs = session.messages();
                if (!msgs.isEmpty()) {
                    AgentMessage last = msgs.get(msgs.size() - 1);
                    if (last instanceof AgentMessage.Llm llm && llm.message() instanceof Message.Assistant asst) {
                        if (asst.errorMessage() != null) {
                            System.err.println(asst.errorMessage());
                            return 1;
                        }
                        for (Content c : asst.content()) {
                            if (c instanceof Content.Text t) {
                                System.out.println(t.text());
                            }
                        }
                    }
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Print mode execution failed: " + e.getMessage());
            return 1;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String jsonString(String value) {
        return value == null ? "null" : "\"" + escapeJson(value) + "\"";
    }
}
