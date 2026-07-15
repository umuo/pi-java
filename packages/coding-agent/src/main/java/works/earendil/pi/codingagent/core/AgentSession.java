package works.earendil.pi.codingagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.agent.core.AgentContext;
import works.earendil.pi.agent.core.AgentEvent;
import works.earendil.pi.agent.core.AgentLoop;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.ai.provider.Provider;
import works.earendil.pi.ai.provider.StreamOptions;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;
import works.earendil.pi.codingagent.core.extensions.ExtensionCommandContext;
import works.earendil.pi.codingagent.core.extensions.ExtensionPlugin;
import works.earendil.pi.codingagent.core.extensions.ExtensionRunner;
import works.earendil.pi.codingagent.resources.Skill;
import works.earendil.pi.codingagent.resources.SkillLoader;
import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class AgentSession {
    private final SessionManager sessionManager;
    private final ModelRegistry modelRegistry;
    private final List<ModelResolver.ScopedModel> scopedModels;
    private final List<AgentTool> tools;
    private final AgentLoop.StreamFunction streamFunction;
    private final StreamOptions defaultStreamOptions;
    private final List<Skill> skills;
    private final boolean enableSkillCommands;
    private final ExtensionRunner extensionRunner;
    private final String shellCommandPrefix;
    private final String shellPath;
    private final boolean blockImages;
    private final List<AgentSessionEventListener> listeners = new CopyOnWriteArrayList<>();
    private final List<AgentMessage> messages = new ArrayList<>();
    private final List<AgentMessage> steeringQueue = new ArrayList<>();
    private final List<AgentMessage> followUpQueue = new ArrayList<>();
    private final List<AgentMessage> nextTurnQueue = new ArrayList<>();
    private final String systemPrompt;
    private final java.nio.file.Path agentDir;
    private Model model;
    private ThinkingLevel thinkingLevel;
    private boolean disposed;
    private boolean agentTurnActive;
    private boolean abortRequested;
    private CompletableFuture<Void> activeAbortSignal;

    public AgentSession(Config config) {
        this.sessionManager = Objects.requireNonNull(config.sessionManager());
        this.modelRegistry = Objects.requireNonNull(config.modelRegistry());
        this.model = config.model();
        this.thinkingLevel = config.thinkingLevel() == null ? Defaults.DEFAULT_THINKING_LEVEL : config.thinkingLevel();
        this.scopedModels = List.copyOf(config.scopedModels() == null ? List.of() : config.scopedModels());
        this.tools = List.copyOf(config.tools() == null ? List.of() : config.tools());
        this.defaultStreamOptions = config.defaultStreamOptions() == null ? StreamOptions.defaults() : config.defaultStreamOptions();
        this.skills = List.copyOf(config.skills() == null ? List.of() : config.skills());
        this.enableSkillCommands = config.enableSkillCommands();
        this.extensionRunner = config.extensionRunner();
        this.shellCommandPrefix = config.shellCommandPrefix();
        this.shellPath = config.shellPath();
        this.blockImages = config.blockImages();
        this.agentDir = config.agentDir() != null ? config.agentDir() : sessionManager.cwd().resolve(".pi/agent");
        if (config.streamFunction() != null) {
            this.streamFunction = config.streamFunction();
        } else {
            this.streamFunction = (modelParam, contextParam, streamOptionsParam) -> {
                String providerId = modelParam.provider();
                String apiType = modelParam.api();
                Provider provider = modelRegistry.builtInRegistry().provider(providerId)
                        .or(() -> modelRegistry.builtInRegistry().provider(apiType != null ? apiType : "openai"))
                        .orElseThrow(() -> new IllegalStateException("No provider found for provider '" + providerId + "'"));

                String resolvedKey = streamOptionsParam.apiKey() != null ? streamOptionsParam.apiKey() : modelRegistry.getApiKeyForProvider(providerId).orElse(null);
                StreamOptions effectiveOptions = new StreamOptions(
                        streamOptionsParam.temperature(),
                        streamOptionsParam.maxTokens(),
                        resolvedKey,
                        streamOptionsParam.transport(),
                        streamOptionsParam.cacheRetention(),
                        streamOptionsParam.sessionId(),
                        streamOptionsParam.headers(),
                        streamOptionsParam.timeout(),
                        streamOptionsParam.maxRetries(),
                        streamOptionsParam.env(),
                        streamOptionsParam.metadata(),
                        providerHooks(streamOptionsParam)
                );

                AssistantMessageEventStream stream = provider.stream(modelParam, contextParam, effectiveOptions);
                CountDownLatch latch = new CountDownLatch(1);
                AtomicReference<Message.Assistant> resultRef = new AtomicReference<>();
                AtomicReference<Throwable> errorRef = new AtomicReference<>();

                stream.publisher().subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(AssistantMessageEvent item) {
                        if (item instanceof AssistantMessageEvent.ContentDelta cd) {
                            handleAgentEvent(new AgentEvent.MessageUpdate(null, cd));
                        } else if (item instanceof AssistantMessageEvent.End end) {
                            resultRef.set(end.message());
                        } else if (item instanceof AssistantMessageEvent.Error err) {
                            errorRef.set(err.cause() != null ? err.cause() : new RuntimeException(err.message()));
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        errorRef.set(throwable);
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });
                latch.await(120, TimeUnit.SECONDS);
                if (errorRef.get() != null) {
                    if (errorRef.get() instanceof Exception ex) throw ex;
                    throw new RuntimeException(errorRef.get());
                }
                if (resultRef.get() != null) {
                    return resultRef.get();
                }
                return new Message.Assistant(
                        List.of(), modelParam.provider(), modelParam.modelId(),
                        works.earendil.pi.ai.model.StopReason.STOP,
                        new works.earendil.pi.ai.model.Usage(0, 0, 0, 0, 0), null, Instant.now());
            };
        }
        this.systemPrompt = config.systemPrompt() == null ? "" : config.systemPrompt();
        restoreMessagesFromSession();
    }

    public record Config(
            SessionManager sessionManager,
            ModelRegistry modelRegistry,
            Model model,
            ThinkingLevel thinkingLevel,
            List<ModelResolver.ScopedModel> scopedModels,
            List<AgentTool> tools,
            String systemPrompt,
            AgentLoop.StreamFunction streamFunction,
            StreamOptions defaultStreamOptions,
            java.nio.file.Path agentDir,
            List<Skill> skills,
            boolean enableSkillCommands,
            ExtensionRunner extensionRunner,
            String shellCommandPrefix,
            String shellPath,
            boolean blockImages) {
        public Config(SessionManager sessionManager, ModelRegistry modelRegistry, Model model,
                      ThinkingLevel thinkingLevel, List<ModelResolver.ScopedModel> scopedModels,
                      List<AgentTool> tools, String systemPrompt, AgentLoop.StreamFunction streamFunction) {
            this(sessionManager, modelRegistry, model, thinkingLevel, scopedModels, tools, systemPrompt, streamFunction,
                    null, null, null, false, null, null, null, false);
        }

        public Config(SessionManager sessionManager, ModelRegistry modelRegistry, Model model,
                      ThinkingLevel thinkingLevel, List<ModelResolver.ScopedModel> scopedModels,
                      List<AgentTool> tools, String systemPrompt, AgentLoop.StreamFunction streamFunction,
                      StreamOptions defaultStreamOptions) {
            this(sessionManager, modelRegistry, model, thinkingLevel, scopedModels, tools, systemPrompt, streamFunction,
                    defaultStreamOptions, null, null, false, null, null, null, false);
        }
    }

    public sealed interface AgentSessionEvent permits
            AgentSessionEvent.QueueUpdate,
            AgentSessionEvent.ThinkingLevelChanged,
            AgentSessionEvent.SessionInfoChanged,
            AgentSessionEvent.SkillCommand,
            AgentSessionEvent.SkillTriggerDiagnostic,
            AgentSessionEvent.AgentEventEnvelope,
            AgentSessionEvent.Disposed {
        String type();

        record QueueUpdate(List<String> steering, List<String> followUp,
                           List<QueueItem> steeringItems, List<QueueItem> followUpItems)
                implements AgentSessionEvent {
            public QueueUpdate(List<String> steering, List<String> followUp) {
                this(steering, followUp, List.of(), List.of());
            }

            public QueueUpdate {
                steering = steering == null ? List.of() : List.copyOf(steering);
                followUp = followUp == null ? List.of() : List.copyOf(followUp);
                steeringItems = steeringItems == null ? List.of() : List.copyOf(steeringItems);
                followUpItems = followUpItems == null ? List.of() : List.copyOf(followUpItems);
            }

            @Override
            public String type() {
                return "queue_update";
            }

            public record QueueItem(String text, String source, List<QueueImage> images) {
                public QueueItem(String text, List<QueueImage> images) {
                    this(text, null, images);
                }

                public QueueItem {
                    text = text == null ? "" : text;
                    source = source == null || source.isBlank() ? "" : source.trim();
                    images = images == null ? List.of() : List.copyOf(images);
                }
            }

            public record QueueImage(String mimeType, String source, int dataLength, String url) {
                public QueueImage {
                    mimeType = mimeType == null ? "" : mimeType;
                    source = source == null || source.isBlank() ? "unknown" : source;
                    dataLength = Math.max(dataLength, 0);
                    url = url == null || url.isBlank() ? null : url;
                }
            }
        }

        record ThinkingLevelChanged(ThinkingLevel level) implements AgentSessionEvent {
            @Override
            public String type() {
                return "thinking_level_changed";
            }
        }

        record SessionInfoChanged(String name) implements AgentSessionEvent {
            @Override
            public String type() {
                return "session_info_changed";
            }
        }

        record SkillCommand(String phase, String skillName, java.nio.file.Path skillPath, String message)
                implements AgentSessionEvent {
            @Override
            public String type() {
                return "skill_command";
            }
        }

        record SkillTriggerDiagnostic(List<SkillLoader.SkillTriggerMatch> matches) implements AgentSessionEvent {
            public SkillTriggerDiagnostic {
                matches = matches == null ? List.of() : List.copyOf(matches);
            }

            @Override
            public String type() {
                return "skill_trigger_diagnostic";
            }
        }

        record AgentEventEnvelope(AgentEvent event) implements AgentSessionEvent {
            @Override
            public String type() {
                return event.type();
            }
        }

        record Disposed() implements AgentSessionEvent {
            @Override
            public String type() {
                return "disposed";
            }
        }
    }

    @FunctionalInterface
    public interface AgentSessionEventListener {
        void onEvent(AgentSessionEvent event);
    }

    public AutoCloseable subscribe(AgentSessionEventListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public enum UserMessageDelivery {
        STEER,
        FOLLOW_UP
    }

    public enum CustomMessageDelivery {
        STEER,
        FOLLOW_UP,
        NEXT_TURN
    }

    public List<AgentMessage> prompt(String text) throws Exception {
        return prompt(text, List.of(), true, true);
    }

    public List<AgentMessage> prompt(String text, List<Content.Image> images) throws Exception {
        return prompt(text, images, true, true);
    }

    public List<AgentMessage> promptRaw(String text) throws Exception {
        return prompt(text, List.of(), false, false);
    }

    public List<AgentMessage> promptRaw(String text, String source) throws Exception {
        return promptContent(List.of(new Content.Text(text == null ? "" : text)),
                text == null ? "" : text,
                source);
    }

    public List<AgentMessage> promptRaw(List<Content> content) throws Exception {
        return promptRaw(content, null);
    }

    public List<AgentMessage> promptRaw(List<Content> content, String source) throws Exception {
        return promptContent(content, contentText(content), source);
    }

    public List<AgentMessage> sendUserMessage(String text, UserMessageDelivery delivery) throws Exception {
        return sendUserMessage(List.of(new Content.Text(text == null ? "" : text)), delivery);
    }

    public List<AgentMessage> sendUserMessage(String text, UserMessageDelivery delivery, String source) throws Exception {
        return sendUserMessage(List.of(new Content.Text(text == null ? "" : text)), delivery, source);
    }

    public List<AgentMessage> sendUserMessage(List<Content> content, UserMessageDelivery delivery) throws Exception {
        return sendUserMessage(content, delivery, null);
    }

    public List<AgentMessage> sendUserMessage(List<Content> content, UserMessageDelivery delivery, String source)
            throws Exception {
        ensureActive();
        List<Content> userContent = normalizeUserContent(content);
        if (!agentTurnActive) {
            return promptRaw(userContent, source);
        }
        if (delivery == null) {
            throw new IllegalStateException("deliverAs is required while the agent is running");
        }
        AgentMessage message = new AgentMessage.Llm(new Message.User(userContent, Instant.now(), source));
        if (delivery == UserMessageDelivery.STEER) {
            steeringQueue.add(message);
        } else {
            followUpQueue.add(message);
        }
        emitQueueUpdate();
        return List.of();
    }

    public List<AgentMessage> sendMessage(ExtensionPlugin.CustomMessage message,
                                          CustomMessageDelivery delivery,
                                          boolean triggerTurn) throws Exception {
        ensureActive();
        AgentMessage.Custom custom = buildExtensionCustomMessage(message);
        CustomMessageDelivery mode = delivery == null ? CustomMessageDelivery.STEER : delivery;
        if (mode == CustomMessageDelivery.NEXT_TURN) {
            nextTurnQueue.add(custom);
            return List.of();
        }
        if (agentTurnActive) {
            persistCustomMessage(custom);
            if (mode == CustomMessageDelivery.FOLLOW_UP) {
                followUpQueue.add(custom);
            } else {
                steeringQueue.add(custom);
            }
            emitQueueUpdate();
            return List.of();
        }
        persistAndAppendCustomMessage(custom);
        if (triggerTurn) {
            messages.remove(messages.size() - 1);
            beginAgentTurn();
            try {
                return runAgentMessages(List.of(custom), systemPrompt);
            } finally {
                endAgentTurn();
                clearUserMessageQueues();
            }
        }
        handleAgentEvent(new AgentEvent.MessageStart(custom));
        handleAgentEvent(new AgentEvent.MessageEnd(custom));
        return List.of(custom);
    }

    public BashExecutor.Result executeBash(String command, java.util.function.Consumer<String> onChunk,
                                           boolean excludeFromContext) throws Exception {
        ensureActive();
        ExtensionPlugin.UserBashResult extensionResult = extensionRunner == null
                ? null
                : extensionRunner.emitUserBash(command, excludeFromContext, sessionManager.cwd()).orElse(null);
        if (extensionResult != null && extensionResult.result() != null) {
            recordBashResult(command, extensionResult.result(), excludeFromContext);
            return extensionResult.result();
        }
        BashOperations operations = extensionResult != null && extensionResult.operations() != null
                ? extensionResult.operations()
                : new LocalBashOperations(shellPath);
        BashExecutor.Result result = BashExecutor.execute(withShellCommandPrefix(command), sessionManager.cwd(), operations,
                new BashExecutor.Options(onChunk, null));
        recordBashResult(command, result, excludeFromContext);
        return result;
    }

    public void recordBashResult(String command, BashExecutor.Result result, boolean excludeFromContext) throws IOException {
        ensureActive();
        CodingAgentMessages.BashExecutionMessage bashMessage = new CodingAgentMessages.BashExecutionMessage(
                command,
                result == null ? "" : result.output(),
                result == null ? null : result.exitCode(),
                result != null && result.cancelled(),
                result != null && result.truncated(),
                result == null || result.fullOutputPath() == null ? null : result.fullOutputPath().toString(),
                Instant.now(),
                excludeFromContext);
        JsonNode content = JsonCodec.mapper().valueToTree(bashMessage);
        sessionManager.appendCustomMessage("bashExecution", content, true, null);
        messages.add(new AgentMessage.Custom("bashExecution", bashMessage, true, null));
    }

    private List<AgentMessage> prompt(String text, List<Content.Image> images, boolean expandSkillCommands,
                                      boolean emitSkillTriggerDiagnostics) throws Exception {
        ensureActive();
        if (agentTurnActive) {
            throw new IllegalStateException("Cannot start a new prompt while the agent is running; use queued delivery");
        }
        if (model == null) {
            throw new IllegalStateException(AuthGuidance.formatNoModelSelectedMessage(java.nio.file.Path.of("docs")));
        }
        beginAgentTurn();
        try {
            boolean skillCommandHandled = false;
            if (expandSkillCommands && enableSkillCommands) {
                SkillLoader.SkillCommandResolution skillCommand = SkillLoader.resolveSkillCommand(text, skills);
                if (skillCommand.command()) {
                    skillCommandHandled = true;
                    java.nio.file.Path skillPath = skillCommand.skill() == null ? null : skillCommand.skill().filePath();
                    if (skillCommand.skill() != null) {
                        emit(new AgentSessionEvent.SkillCommand("start", skillCommand.skillName(), skillPath, null));
                    }
                    if (skillCommand.expanded()) {
                        text = skillCommand.expandedText();
                        emit(new AgentSessionEvent.SkillCommand("end", skillCommand.skillName(), skillPath, null));
                    } else {
                        emit(new AgentSessionEvent.SkillCommand("error", skillCommand.skillName(), skillPath,
                                skillCommand.errorMessage()));
                    }
                }
            }
            if (emitSkillTriggerDiagnostics && !skillCommandHandled) {
                List<SkillLoader.SkillTriggerMatch> triggerMatches = SkillLoader.matchTriggerHints(text, skills);
                if (!triggerMatches.isEmpty()) {
                    emit(new AgentSessionEvent.SkillTriggerDiagnostic(triggerMatches));
                }
            }
            List<Content> userContent = new ArrayList<>();
            userContent.add(new Content.Text(text));
            if (images != null) {
                userContent.addAll(images);
            }
            return runUserPrompt(userContent, text, null);
        } finally {
            endAgentTurn();
            clearUserMessageQueues();
        }
    }

    private List<AgentMessage> promptContent(List<Content> content, String textForExtensions) throws Exception {
        ensureActive();
        if (agentTurnActive) {
            throw new IllegalStateException("Cannot start a new prompt while the agent is running; use queued delivery");
        }
        if (model == null) {
            throw new IllegalStateException(AuthGuidance.formatNoModelSelectedMessage(java.nio.file.Path.of("docs")));
        }
        beginAgentTurn();
        try {
            return runUserPrompt(content, textForExtensions, null);
        } finally {
            endAgentTurn();
            clearUserMessageQueues();
        }
    }

    private List<AgentMessage> promptContent(List<Content> content, String textForExtensions, String source)
            throws Exception {
        ensureActive();
        if (agentTurnActive) {
            throw new IllegalStateException("Cannot start a new prompt while the agent is running; use queued delivery");
        }
        if (model == null) {
            throw new IllegalStateException(AuthGuidance.formatNoModelSelectedMessage(java.nio.file.Path.of("docs")));
        }
        beginAgentTurn();
        try {
            return runUserPrompt(content, textForExtensions, source);
        } finally {
            endAgentTurn();
            clearUserMessageQueues();
        }
    }

    private List<AgentMessage> runUserPrompt(List<Content> content, String textForExtensions, String source)
            throws Exception {
        List<Content> userContent = normalizeUserContent(content);
        String promptText = textForExtensions == null ? "" : textForExtensions;
        Message.User user = new Message.User(userContent, Instant.now(), source);
        AgentMessage.Llm prompt = new AgentMessage.Llm(user);
        emitExtensionBeforeTurn(promptText);
        String turnSystemPrompt = systemPrompt;
        appendNextTurnMessages();
        ExtensionPlugin.BeforeAgentStartResult beforeAgentStart = emitExtensionBeforeAgentStart(promptText,
                turnSystemPrompt);
        if (beforeAgentStart != null) {
            if (beforeAgentStart.messages() != null) {
                for (ExtensionPlugin.CustomMessage message : beforeAgentStart.messages()) {
                    appendExtensionCustomMessage(message);
                }
            }
            if (beforeAgentStart.systemPrompt() != null) {
                turnSystemPrompt = beforeAgentStart.systemPrompt();
            }
        }
        sessionManager.appendMessage(messageNode(user));
        messages.add(prompt);

        int contextWindow = model.contextWindow() > 0 ? model.contextWindow() : 128000;
        CompactionSupport.Settings compactionSettings = new CompactionSupport.Settings(true, 16384, 10);
        CompactionSupport.ContextUsageEstimate estimate = CompactionSupport.estimateContextTokens(messages);
        if (CompactionSupport.shouldCompact(estimate.tokens(), contextWindow, compactionSettings)) {
            CompactionSupport.CompactionPreparation prep =
                    CompactionSupport.prepareCompaction(sessionManager.branch(), compactionSettings);
            performCompaction(prep, true);
        }

        AgentMessage lastUserMessage = messages.remove(messages.size() - 1);
        return runAgentMessages(List.of(lastUserMessage), turnSystemPrompt);
    }

    private static List<Content> normalizeUserContent(List<Content> content) {
        if (content == null || content.isEmpty()) {
            return List.of(new Content.Text(""));
        }
        List<Content> normalized = new ArrayList<>();
        for (Content block : content) {
            if (block == null) {
                continue;
            }
            if (block instanceof Content.Text || block instanceof Content.Image) {
                normalized.add(block);
            } else {
                normalized.add(new Content.Text(JsonCodec.stringify(block)));
            }
        }
        return normalized.isEmpty() ? List.of(new Content.Text("")) : List.copyOf(normalized);
    }

    private static String contentText(List<Content> content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        List<String> texts = new ArrayList<>();
        for (Content block : content) {
            if (block instanceof Content.Text text) {
                texts.add(text.text() == null ? "" : text.text());
            }
        }
        return String.join("\n", texts);
    }

    private List<AgentMessage> runAgentMessages(List<AgentMessage> prompts, String turnSystemPrompt) throws Exception {
        List<AgentTool> guardedTools = new ArrayList<>();
        TrustManager.ProjectTrustStore trustStore = new TrustManager.ProjectTrustStore(agentDir);
        boolean requiresTrust = TrustManager.hasTrustRequiringProjectResources(sessionManager.cwd(), agentDir);
        Boolean trustDecision = trustStore.get(sessionManager.cwd());
        boolean isUntrusted = requiresTrust && Boolean.FALSE.equals(trustDecision);

        for (AgentTool t : tools) {
            if (isUntrusted && (t.name().equals("bash") || t.name().equals("write") || t.name().equals("edit"))) {
                guardedTools.add(new AgentTool() {
                    @Override
                    public Tool definition() { return t.definition(); }
                    @Override
                    public AgentToolResult execute(Object input) {
                        return new AgentToolResult(List.of(new Content.Text("Execution blocked by TrustManager: Project at " + sessionManager.cwd() + " is untrusted.")), Map.of("error", "untrusted"), true, false);
                    }
                });
            } else {
                guardedTools.add(t);
            }
        }
        List<AgentTool> activeTools = wrapToolsForExtensions(guardedTools);

        List<AgentMessage> newMessages = AgentLoop.run(prompts,
                new AgentContext(List.copyOf(messages), turnSystemPrompt, activeTools),
                new AgentLoop.Config(model, defaultStreamOptions,
                        () -> drainUserMessageQueue(UserMessageDelivery.STEER),
                        () -> drainUserMessageQueue(UserMessageDelivery.FOLLOW_UP),
                        AgentLoop.ToolExecutionMode.SEQUENTIAL,
                        agentMessages -> CodingAgentMessages.convertToLlm(agentMessages, blockImages),
                        this::abortRequested),
                streamFunction,
                this::handleAgentEvent);
        messages.addAll(newMessages);
        persistNewLlmMessages(newMessages.subList(prompts.size(), newMessages.size()));
        emitExtensionAfterTurn(latestAssistantText(newMessages));
        return newMessages;
    }

    public void setModel(Model model) throws IOException {
        ensureActive();
        this.model = model;
        sessionManager.appendModelChange(model.provider(), model.modelId());
    }

    public Optional<ModelCycleResult> cycleModel() throws IOException {
        ensureActive();
        List<ModelResolver.ScopedModel> cycle = scopedModels.isEmpty()
                ? modelRegistry.getAvailable().stream().map(m -> new ModelResolver.ScopedModel(m, null)).toList()
                : scopedModels;
        if (cycle.isEmpty()) {
            return Optional.empty();
        }
        int currentIndex = -1;
        for (int i = 0; i < cycle.size(); i++) {
            if (model != null && modelsAreEqual(cycle.get(i).model(), model)) {
                currentIndex = i;
                break;
            }
        }
        ModelResolver.ScopedModel next = cycle.get((currentIndex + 1) % cycle.size());
        model = next.model();
        if (next.thinkingLevel() != null) {
            setThinkingLevel(next.thinkingLevel());
        }
        sessionManager.appendModelChange(model.provider(), model.modelId());
        return Optional.of(new ModelCycleResult(model, thinkingLevel, !scopedModels.isEmpty()));
    }

    public void setThinkingLevel(ThinkingLevel thinkingLevel) throws IOException {
        ensureActive();
        this.thinkingLevel = thinkingLevel;
        sessionManager.appendThinkingLevelChange(thinkingLevel.wireName());
        emit(new AgentSessionEvent.ThinkingLevelChanged(thinkingLevel));
    }

    public String setSessionName(String name) throws IOException {
        ensureActive();
        String id = sessionManager.appendSessionInfo(name);
        emit(new AgentSessionEvent.SessionInfoChanged(sessionManager.sessionName().orElse(null)));
        return id;
    }

    public CompactionResult compactNow() throws Exception {
        ensureActive();
        CompactionSupport.Settings compactionSettings = new CompactionSupport.Settings(true, 0, 0);
        CompactionSupport.CompactionPreparation prep =
                CompactionSupport.prepareCompaction(sessionManager.branch(), compactionSettings);
        return performCompaction(prep, false).orElseGet(() ->
                new CompactionResult(false, null, null, 0, 0, 0, ""));
    }

    private Optional<CompactionResult> performCompaction(CompactionSupport.CompactionPreparation prep,
                                                        boolean requireSummarizedMessages) throws Exception {
        if (prep == null || (requireSummarizedMessages && prep.messagesToSummarize().isEmpty())
                || (prep.messagesToSummarize().isEmpty() && prep.turnPrefixMessages().isEmpty())) {
            return Optional.empty();
        }
        emitExtensionBeforeCompact(prep);
        String summaryText = summarizeCompaction(prep);
        CompactionSupport.FileLists fileLists = CompactionSupport.computeFileLists(prep.fileOperations());
        summaryText += CompactionSupport.formatFileOperations(fileLists.readFiles(), fileLists.modifiedFiles());
        String entryId = sessionManager.appendCompaction(summaryText, prep.firstKeptEntryId(), prep.tokensBefore(),
                null, false);
        restoreMessagesFromSession();
        emitExtensionAfterCompact(entryId, summaryText);
        return Optional.of(new CompactionResult(true, entryId, prep.firstKeptEntryId(), prep.messagesToSummarize().size(),
                prep.turnPrefixMessages().size(), prep.tokensBefore(), summaryText));
    }

    private String summarizeCompaction(CompactionSupport.CompactionPreparation prep) throws Exception {
        String serialized = CompactionSupport.serializeConversation(
                prep.messagesToSummarize().stream()
                        .map(m -> m instanceof AgentMessage.Llm llm ? llm.message() : null)
                        .filter(Objects::nonNull)
                        .toList()
        );
        if (prep.previousSummary() != null && !prep.previousSummary().isBlank()) {
            serialized = "Previous compaction summary:\n" + prep.previousSummary()
                    + "\n\nConversation since that summary:\n" + serialized;
        }
        if (!prep.turnPrefixMessages().isEmpty()) {
            String turnPrefix = CompactionSupport.serializeConversation(
                    prep.turnPrefixMessages().stream()
                            .map(m -> m instanceof AgentMessage.Llm llm ? llm.message() : null)
                            .filter(Objects::nonNull)
                            .toList()
            );
            serialized += "\n\nPartial turn prefix to retain in summary:\n" + turnPrefix;
        }
        Message.User summaryPromptUser = new Message.User(List.of(new Content.Text(
                "Summarize the following conversation history concisely while retaining key decisions, file modifications, and current context:\n\n" + serialized
        )), Instant.now());
        List<AgentMessage> summaryRes = AgentLoop.run(List.of(new AgentMessage.Llm(summaryPromptUser)),
                new AgentContext(List.of(), "You are a concise expert technical summarizer.", List.of()),
                new AgentLoop.Config(model, defaultStreamOptions, List::of, List::of,
                        AgentLoop.ToolExecutionMode.SEQUENTIAL, CodingAgentMessages::convertToLlm),
                streamFunction,
                this::handleAgentEvent);
        StringBuilder sb = new StringBuilder();
        if (!summaryRes.isEmpty() && summaryRes.getLast() instanceof AgentMessage.Llm llmRes
                && llmRes.message() instanceof Message.Assistant assistant) {
            for (Content block : assistant.content()) {
                if (block instanceof Content.Text textBlock) {
                    sb.append(textBlock.text());
                }
            }
        }
        return sb.isEmpty() ? "Compacted conversation history." : sb.toString();
    }

    private List<AgentMessage> drainUserMessageQueue(UserMessageDelivery delivery) {
        List<AgentMessage> queue = delivery == UserMessageDelivery.STEER ? steeringQueue : followUpQueue;
        if (queue.isEmpty()) {
            return List.of();
        }
        List<AgentMessage> drained = List.copyOf(queue);
        queue.clear();
        emitQueueUpdate();
        return drained;
    }

    private void emitQueueUpdate() {
        emit(new AgentSessionEvent.QueueUpdate(
                queueText(steeringQueue),
                queueText(followUpQueue),
                queueItems(steeringQueue),
                queueItems(followUpQueue)));
    }

    private void appendNextTurnMessages() throws IOException {
        if (nextTurnQueue.isEmpty()) {
            return;
        }
        List<AgentMessage> pending = List.copyOf(nextTurnQueue);
        nextTurnQueue.clear();
        for (AgentMessage message : pending) {
            if (message instanceof AgentMessage.Custom custom) {
                persistAndAppendCustomMessage(custom);
            } else {
                messages.add(message);
            }
        }
    }

    private void clearUserMessageQueues() {
        if (steeringQueue.isEmpty() && followUpQueue.isEmpty()) {
            return;
        }
        steeringQueue.clear();
        followUpQueue.clear();
        emitQueueUpdate();
    }

    private static List<String> queueText(List<AgentMessage> queue) {
        if (queue.isEmpty()) {
            return List.of();
        }
        List<String> text = new ArrayList<>();
        for (AgentMessage message : queue) {
            if (message instanceof AgentMessage.Llm llm && llm.message() instanceof Message.User user) {
                text.add(textFromContent(user.content()));
            }
        }
        return List.copyOf(text);
    }

    private static List<AgentSessionEvent.QueueUpdate.QueueItem> queueItems(List<AgentMessage> queue) {
        if (queue.isEmpty()) {
            return List.of();
        }
        List<AgentSessionEvent.QueueUpdate.QueueItem> items = new ArrayList<>();
        for (AgentMessage message : queue) {
            if (message instanceof AgentMessage.Llm llm && llm.message() instanceof Message.User user) {
                items.add(new AgentSessionEvent.QueueUpdate.QueueItem(
                        textFromContent(user.content()),
                        user.source(),
                        queueImages(user.content())));
            }
        }
        return List.copyOf(items);
    }

    private static List<AgentSessionEvent.QueueUpdate.QueueImage> queueImages(List<Content> content) {
        List<AgentSessionEvent.QueueUpdate.QueueImage> images = new ArrayList<>();
        for (Content block : content) {
            if (block instanceof Content.Image image) {
                String source = image.data() != null && !image.data().isBlank() ? "data"
                        : image.url() != null && !image.url().isBlank() ? "url"
                        : "unknown";
                images.add(new AgentSessionEvent.QueueUpdate.QueueImage(
                        image.mimeType(),
                        source,
                        imageDataLength(image.data()),
                        image.url()));
            }
        }
        return List.copyOf(images);
    }

    private static int imageDataLength(String data) {
        if (data == null || data.isBlank()) {
            return 0;
        }
        try {
            return Base64.getDecoder().decode(data).length;
        } catch (IllegalArgumentException e) {
            return data.length();
        }
    }

    private AgentMessage.Custom buildExtensionCustomMessage(ExtensionPlugin.CustomMessage message) {
        Objects.requireNonNull(message, "message");
        return new AgentMessage.Custom(message.customType(),
                CodingAgentMessages.createCustomMessage(message.customType(), message.content(), message.display(),
                        message.details(), "extension", Instant.now()),
                message.display(), message.details());
    }

    private void persistAndAppendCustomMessage(AgentMessage.Custom message) throws IOException {
        persistCustomMessage(message);
        messages.add(message);
    }

    private void persistCustomMessage(AgentMessage.Custom message) throws IOException {
        if (message.content() instanceof CodingAgentMessages.CustomMessage customMessage) {
            JsonNode content = JsonCodec.mapper().valueToTree(customMessage.content());
            JsonNode details = customMessage.details() == null ? null : JsonCodec.mapper().valueToTree(customMessage.details());
            sessionManager.appendCustomMessage(customMessage.customType(), content, customMessage.display(), details,
                    customMessage.source());
            return;
        }
        JsonNode content = JsonCodec.mapper().valueToTree(message.content());
        JsonNode details = message.details() == null ? null : JsonCodec.mapper().valueToTree(message.details());
        sessionManager.appendCustomMessage(message.customType(), content, message.display(), details);
    }

    public SessionStats stats() {
        int userMessages = 0;
        int assistantMessages = 0;
        int toolResults = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        long cacheCreationInputTokens = 0;
        long cacheReadInputTokens = 0;
        long reasoningTokens = 0;
        for (AgentMessage message : messages) {
            if (message instanceof AgentMessage.Llm llm) {
                if (llm.message() instanceof Message.User) {
                    userMessages++;
                } else if (llm.message() instanceof Message.Assistant assistant) {
                    assistantMessages++;
                    Usage usage = assistant.usage();
                    if (usage != null) {
                        inputTokens += usage.inputTokens();
                        outputTokens += usage.outputTokens();
                        cacheCreationInputTokens += usage.cacheCreationInputTokens();
                        cacheReadInputTokens += usage.cacheReadInputTokens();
                        reasoningTokens += usage.reasoningTokens();
                    }
                } else if (llm.message() instanceof Message.ToolResult) {
                    toolResults++;
                }
            }
        }
        return new SessionStats(sessionManager.sessionFile().orElse(null), sessionManager.sessionId(),
                userMessages, assistantMessages, toolResults, messages.size(),
                inputTokens, outputTokens, cacheCreationInputTokens, cacheReadInputTokens, reasoningTokens,
                inputTokens + outputTokens + cacheCreationInputTokens + cacheReadInputTokens + reasoningTokens);
    }

    public void dispose() {
        if (!disposed) {
            disposed = true;
            emit(new AgentSessionEvent.Disposed());
        }
    }

    public boolean disposed() {
        return disposed;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public Optional<java.nio.file.Path> sessionFile() {
        return sessionManager.sessionFile();
    }

    public Model model() {
        return model;
    }

    public ThinkingLevel thinkingLevel() {
        return thinkingLevel;
    }

    public List<AgentTool> tools() {
        return tools;
    }

    public ExtensionRunner extensionRunner() {
        return extensionRunner;
    }

    public boolean isIdle() {
        return !agentTurnActive;
    }

    public boolean hasPendingMessages() {
        return !steeringQueue.isEmpty() || !followUpQueue.isEmpty() || !nextTurnQueue.isEmpty();
    }

    public Optional<CompletionStage<Void>> abortSignal() {
        return Optional.ofNullable(activeAbortSignal);
    }

    public boolean abortRequested() {
        return abortRequested;
    }

    public void abort() {
        if (!agentTurnActive) {
            return;
        }
        abortRequested = true;
        if (activeAbortSignal != null) {
            activeAbortSignal.complete(null);
        }
    }

    public List<Skill> skills() {
        return skills;
    }

    public List<AgentMessage> messages() {
        return List.copyOf(messages);
    }

    public record ModelCycleResult(Model model, ThinkingLevel thinkingLevel, boolean scoped) {
    }

    public record SessionStats(java.nio.file.Path sessionFile, String sessionId, int userMessages,
                               int assistantMessages, int toolResults, int totalMessages,
                               long inputTokens, long outputTokens, long cacheCreationInputTokens,
                               long cacheReadInputTokens, long reasoningTokens, long totalTokens) {
        public long cacheInputTokens() {
            return cacheCreationInputTokens + cacheReadInputTokens;
        }
    }

    public record CompactionResult(boolean compacted, String entryId, String firstKeptEntryId,
                                   int summarizedMessages, int turnPrefixMessages, int tokensBefore,
                                   String summary) {
    }

    private void emitExtensionBeforeTurn(String prompt) {
        if (extensionRunner != null) {
            extensionRunner.emitBeforeTurn(prompt);
        }
    }

    private ExtensionPlugin.BeforeAgentStartResult emitExtensionBeforeAgentStart(String prompt, String turnSystemPrompt) {
        if (extensionRunner == null) {
            return null;
        }
        return extensionRunner.emitBeforeAgentStart(prompt, turnSystemPrompt, new ExtensionCommandContext(this))
                .orElse(null);
    }

    private void emitExtensionAfterTurn(String response) {
        if (extensionRunner != null) {
            extensionRunner.emitAfterTurn(response == null ? "" : response);
        }
    }

    private void appendExtensionCustomMessage(ExtensionPlugin.CustomMessage message) throws IOException {
        persistAndAppendCustomMessage(buildExtensionCustomMessage(message));
    }

    private void emitExtensionBeforeCompact(CompactionSupport.CompactionPreparation prep) {
        if (extensionRunner != null) {
            extensionRunner.emitBeforeCompact(prep.tokensBefore(), prep.messagesToSummarize().size(),
                    prep.turnPrefixMessages().size());
        }
    }

    private void emitExtensionAfterCompact(String entryId, String summary) {
        if (extensionRunner != null) {
            extensionRunner.emitAfterCompact(entryId, summary == null ? "" : summary);
        }
    }

    private StreamOptions.ProviderHooks providerHooks(StreamOptions options) {
        StreamOptions.ProviderHooks existing = options == null ? null : options.providerHooks();
        if (existing == null && extensionRunner == null) {
            return null;
        }
        return new StreamOptions.ProviderHooks(
                (payload, hookModel) -> {
                    Object currentPayload = payload;
                    if (existing != null && existing.beforeRequest() != null) {
                        Object result = existing.beforeRequest().beforeRequest(currentPayload, hookModel);
                        if (result != null) {
                            currentPayload = result;
                        }
                    }
                    if (extensionRunner != null) {
                        currentPayload = extensionRunner
                                .emitBeforeProviderRequest(currentPayload, new ExtensionCommandContext(this))
                                .orElse(currentPayload);
                    }
                    return currentPayload;
                },
                (status, headers, hookModel) -> {
                    if (existing != null && existing.afterResponse() != null) {
                        existing.afterResponse().afterResponse(status, headers, hookModel);
                    }
                    if (extensionRunner != null) {
                        extensionRunner.emitAfterProviderResponse(status, headers, new ExtensionCommandContext(this));
                    }
                });
    }

    private void beginAgentTurn() {
        agentTurnActive = true;
        abortRequested = false;
        activeAbortSignal = new CompletableFuture<>();
    }

    private void endAgentTurn() {
        agentTurnActive = false;
        abortRequested = false;
        activeAbortSignal = null;
    }

    private List<AgentTool> wrapToolsForExtensions(List<AgentTool> sourceTools) {
        if (extensionRunner == null || sourceTools == null || sourceTools.isEmpty()) {
            return sourceTools == null ? List.of() : sourceTools;
        }
        List<AgentTool> wrapped = new ArrayList<>();
        for (AgentTool tool : sourceTools) {
            wrapped.add(new AgentTool() {
                @Override
                public Tool definition() {
                    return tool.definition();
                }

                @Override
                public AgentToolResult execute(Object input) throws Exception {
                    ExtensionCommandContext extensionContext = new ExtensionCommandContext(AgentSession.this);
                    ExtensionPlugin.ToolCallResult toolCallResult = extensionRunner
                            .emitToolCall(tool.name(), input, extensionContext)
                            .orElse(null);
                    if (toolCallResult != null && toolCallResult.block()) {
                        String reason = toolCallResult.reason() == null || toolCallResult.reason().isBlank()
                                ? "Tool execution blocked by extension."
                                : toolCallResult.reason();
                        AgentToolResult blocked = new AgentToolResult(List.of(new Content.Text(reason)),
                                Map.of("extensionBlocked", true, "reason", reason), true, false);
                        return extensionRunner.emitToolResult(tool.name(), input, blocked, extensionContext);
                    }
                    Object effectiveInput = toolCallResult != null && toolCallResult.input() != null
                            ? toolCallResult.input()
                            : input;
                    try {
                        AgentToolResult result = tool.execute(effectiveInput);
                        return extensionRunner.emitToolResult(tool.name(), effectiveInput, result, extensionContext);
                    } catch (Exception e) {
                        String message = e.getMessage() == null ? "" : e.getMessage();
                        AgentToolResult error = new AgentToolResult(List.of(new Content.Text(message)), null, true, false);
                        return extensionRunner.emitToolResult(tool.name(), effectiveInput, error, extensionContext);
                    }
                }

                @Override
                public Map<String, Object> prepareArguments(Map<String, Object> input) {
                    return tool.prepareArguments(input);
                }

                @Override
                public ExecutionMode executionMode() {
                    return tool.executionMode();
                }
            });
        }
        return List.copyOf(wrapped);
    }

    private static String latestAssistantText(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (message instanceof AgentMessage.Llm llm && llm.message() instanceof Message.Assistant assistant) {
                return textFromContent(assistant.content());
            }
        }
        return "";
    }

    private static String textFromContent(List<Content> content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Content block : content) {
            if (block instanceof Content.Text textBlock) {
                text.append(textBlock.text());
            }
        }
        return text.toString();
    }

    private String withShellCommandPrefix(String command) {
        return shellCommandPrefix == null || shellCommandPrefix.isBlank()
                ? command
                : shellCommandPrefix + "\n" + command;
    }

    private void handleAgentEvent(AgentEvent event) {
        emit(new AgentSessionEvent.AgentEventEnvelope(event));
    }

    private void persistNewLlmMessages(List<AgentMessage> newMessages) throws IOException {
        for (AgentMessage message : newMessages) {
            if (message instanceof AgentMessage.Llm llm) {
                sessionManager.appendMessage(messageNode(llm.message()));
            }
        }
    }

    private void restoreMessagesFromSession() {
        messages.clear();
        messages.addAll(CompactionSupport.buildSessionContext(sessionManager.branch()));
    }

    private Optional<Message> messageFromJson(JsonNode node) {
        String role = node.path("role").asText();
        if ("user".equals(role)) {
            return Optional.of(new Message.User(readContent(node), readTimestamp(node), node.path("source").asText(null)));
        }
        if ("assistant".equals(role)) {
            return Optional.of(new Message.Assistant(readContent(node),
                    node.path("provider").asText(null),
                    node.path("model").asText(null),
                    works.earendil.pi.ai.model.StopReason.STOP,
                    readUsage(node),
                    null,
                    readTimestamp(node),
                    node.path("responseId").asText(null)));
        }
        return Optional.empty();
    }

    private Usage readUsage(JsonNode node) {
        JsonNode usage = node.get("usage");
        if (usage == null || !usage.isObject()) {
            return null;
        }
        return new Usage(
                usage.path("inputTokens").asInt(0),
                usage.path("outputTokens").asInt(0),
                usage.path("cacheCreationInputTokens").asInt(0),
                usage.path("cacheReadInputTokens").asInt(0),
                usage.path("reasoningTokens").asInt(0));
    }

    private JsonNode messageNode(Message message) {
        ObjectNode node = JsonCodec.mapper().createObjectNode();
        node.put("timestamp", Instant.now().toString());
        if (message instanceof Message.User user) {
            node.put("role", "user");
            node.set("content", JsonCodec.mapper().valueToTree(user.content()));
            if (user.source() != null && !user.source().isBlank()) {
                node.put("source", user.source());
            }
        } else if (message instanceof Message.Assistant assistant) {
            node.put("role", "assistant");
            node.set("content", JsonCodec.mapper().valueToTree(assistant.content()));
            if (assistant.provider() != null) {
                node.put("provider", assistant.provider());
            }
            if (assistant.model() != null) {
                node.put("model", assistant.model());
            }
            if (assistant.stopReason() != null) {
                node.put("stopReason", assistant.stopReason().name().toLowerCase(java.util.Locale.ROOT));
            }
            if (assistant.usage() != null) {
                node.set("usage", JsonCodec.mapper().valueToTree(assistant.usage()));
            }
            if (assistant.responseId() != null && !assistant.responseId().isBlank()) {
                node.put("responseId", assistant.responseId());
            }
        } else if (message instanceof Message.ToolResult toolResult) {
            node.put("role", "toolResult");
            node.put("toolCallId", toolResult.toolCallId());
            node.put("toolName", toolResult.toolName());
            node.set("content", JsonCodec.mapper().valueToTree(toolResult.content()));
            node.put("error", toolResult.error());
            if (toolResult.details() != null) {
                node.set("details", JsonCodec.mapper().valueToTree(toolResult.details()));
            }
        } else {
            node.put("role", "custom");
            node.set("content", JsonCodec.mapper().valueToTree(message));
        }
        return node;
    }

    private List<Content> readContent(JsonNode node) {
        JsonNode content = node.get("content");
        if (content == null || content.isNull()) {
            return List.of();
        }
        if (content.isTextual()) {
            return List.of(new Content.Text(content.asText()));
        }
        if (content.isArray()) {
            List<Content> values = new ArrayList<>();
            for (JsonNode item : content) {
                readContentBlock(item).ifPresent(values::add);
            }
            return List.copyOf(values);
        }
        return List.of(new Content.Text(content.toString()));
    }

    private Optional<Content> readContentBlock(JsonNode item) {
        if (item.isTextual()) {
            return Optional.of(new Content.Text(item.asText()));
        }
        return switch (item.path("type").asText()) {
            case "text" -> Optional.of(new Content.Text(item.path("text").asText()));
            case "thinking" -> Optional.of(new Content.Thinking(item.path("text").asText(),
                    item.path("signature").asText(null)));
            case "image" -> Optional.of(new Content.Image(item.path("mimeType").asText("image/png"),
                    blankToNull(item.path("data").asText(null)),
                    blankToNull(item.path("url").asText(null))));
            case "toolCall" -> Optional.of(new Content.ToolCall(item.path("id").asText(null),
                    item.path("name").asText(""),
                    item.has("input") ? item.get("input") : JsonCodec.mapper().createObjectNode(),
                    readContentList(item.get("displayContent"))));
            default -> Optional.empty();
        };
    }

    private List<Content> readContentList(JsonNode content) {
        if (content == null || !content.isArray()) {
            return List.of();
        }
        List<Content> values = new ArrayList<>();
        for (JsonNode item : content) {
            readContentBlock(item).ifPresent(values::add);
        }
        return List.copyOf(values);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Instant readTimestamp(JsonNode node) {
        try {
            return node.hasNonNull("timestamp") ? Instant.parse(node.path("timestamp").asText()) : Instant.now();
        } catch (RuntimeException e) {
            return Instant.now();
        }
    }

    private void emit(AgentSessionEvent event) {
        for (AgentSessionEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    private void ensureActive() {
        if (disposed) {
            throw new IllegalStateException("AgentSession has been disposed");
        }
    }

    private static boolean modelsAreEqual(Model a, Model b) {
        return a.provider().equals(b.provider()) && a.modelId().equals(b.modelId());
    }
}
