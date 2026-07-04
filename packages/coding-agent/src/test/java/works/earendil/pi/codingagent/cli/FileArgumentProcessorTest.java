package works.earendil.pi.codingagent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;
import works.earendil.pi.codingagent.core.AgentSessionServices;
import works.earendil.pi.codingagent.core.AuthStorage;
import works.earendil.pi.codingagent.session.SessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FileArgumentProcessorTest {
    private static final String TINY_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

    @TempDir
    Path tempDir;

    @Test
    void combinesTextFilesImageAttachmentsAndFirstPromptIntoInitialMessage() throws Exception {
        Path cwd = tempDir.resolve("project");
        Files.createDirectories(cwd);
        Files.writeString(cwd.resolve("note.txt"), "hello from file");
        Files.write(cwd.resolve("tiny.png"), Base64.getDecoder().decode(TINY_PNG_BASE64));

        CliArgs args = new CliArgs();
        args.messages.add("@note.txt");
        args.messages.add("Explain this");
        args.messages.add("@tiny.png");
        args.messages.add("Second prompt");

        FileArgumentProcessor.process(args, cwd, true);

        assertThat(args.messages).hasSize(2);
        assertThat(args.messages.getFirst())
                .contains("<file name=\"" + cwd.resolve("note.txt"))
                .contains("hello from file")
                .contains("<file name=\"" + cwd.resolve("tiny.png") + "\"></file>\nExplain this");
        assertThat(args.messages.get(1)).isEqualTo("Second prompt");
        assertThat(args.initialImages).containsExactly(new Content.Image("image/png", TINY_PNG_BASE64, null));
    }

    @Test
    void printModeSendsInitialImagesWithTheFirstPromptOnly() throws Exception {
        Path cwd = tempDir.resolve("print-project");
        Path agentDir = tempDir.resolve("print-agent");
        Files.createDirectories(cwd);
        AuthStorage authStorage = AuthStorage.inMemory();
        authStorage.set("openai", new AuthStorage.ApiKeyCredential("stored-key", null));
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));
        SessionManager sessionManager = SessionManager.create(cwd, tempDir.resolve("print-sessions"));
        Model model = services.modelRegistry().getAll().getFirst();
        AtomicReference<List<Content>> firstUserContent = new AtomicReference<>();
        AtomicReference<List<Content>> secondUserContent = new AtomicReference<>();
        AtomicReference<Integer> calls = new AtomicReference<>(0);

        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, List.of(),
                            (m, context, opts) -> {
                                captureUserContent(context, calls, firstUserContent, secondUserContent);
                                return new Message.Assistant(List.of(new Content.Text("ok")),
                                        m.provider(), m.modelId(), StopReason.STOP,
                                        new Usage(1, 1, 0, 0, 0), null, Instant.now());
                            }
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, "print-image"));

        CliArgs args = new CliArgs();
        args.print = true;
        args.messages.add("Describe image");
        args.messages.add("Follow-up");
        args.initialImages.add(new Content.Image("image/png", "abc", null));

        int exitCode = PrintModeRunner.run(runtime, args);

        assertThat(exitCode).isEqualTo(0);
        assertThat(firstUserContent.get()).containsExactly(
                new Content.Text("Describe image"),
                new Content.Image("image/png", "abc", null));
        assertThat(secondUserContent.get()).containsExactly(new Content.Text("Follow-up"));
    }

    private static void captureUserContent(Context context, AtomicReference<Integer> calls,
                                           AtomicReference<List<Content>> firstUserContent,
                                           AtomicReference<List<Content>> secondUserContent) {
        int call = calls.get();
        calls.set(call + 1);
        Message.User user = (Message.User) context.messages().getLast();
        if (call == 0) {
            firstUserContent.set(user.content());
        } else {
            secondUserContent.set(user.content());
        }
    }
}
