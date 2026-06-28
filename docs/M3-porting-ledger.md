# M3: Porting Ledger

This ledger tracks the TypeScript files from `/Users/gitsilence/github/pi` and their Java destination status.

## Current status

| Area | TS files | Java status |
| --- | ---: | --- |
| `packages/ai/src` | 147 | Core domain types, stream events, stream options, provider registry started |
| `packages/agent/src` | 25 | Agent/tool contracts, low-level agent loop with injectable LLM message transforms, session model/storage, JSONL v3 storage, UUIDv7, compaction heuristics started |
| `packages/tui/src` | 28 | Text width, ANSI, component rendering, fuzzy matching, terminal image fallback started |
| `packages/coding-agent/src` | 160 | Read/write/ls/grep/find/edit/bash tools, output accumulator/truncation, file mutation queue, tool factory, bash/exec backend, system prompt/messages, keybindings, compaction support, slash commands, HTTP dispatcher, footer data provider, diagnostics/source metadata, skills/prompts resources, path handling, rich settings model/manager |
| `packages/orchestrator/src` | 13 | IPC protocol message model, JSON codec, instance lifecycle records, storage persistence, directory configuration, and supervisor process coordination completed |

## Completed this iteration

| Milestone | Result |
| --- | --- |
| M1 | Added `docs/M1-dependency-mapping.md` and `docs/M1-module-design.md`. |
| M2 | Added Maven reactor, six module POMs, dependency management, and fallback utility implementations. |
| M3 | Started bottom-up code conversion across common, ai, agent, tui, coding-agent, and orchestrator modules. |

## Latest continuation progress

| Source area | Java files | Notes |
| --- | --- | --- |
| `packages/agent/src/agent-loop.ts` | `AgentLoop`, `AgentEvent` | Ported lifecycle events, prompt injection, assistant turns, tool execution, follow-up turn continuation. |
| `packages/agent/src/harness/session/jsonl-storage.ts` | `JsonlSessionStorage`, `SessionEntryCodec`, `JsonlSessionMetadata` | Ported session header v3, typed entry parsing, leaf entries, label cache, path-to-root traversal. |
| `packages/agent/src/types.ts` | `AgentTool`, `AgentContext` | Expanded tool contract with names, argument preparation hook, and execution mode. |
| Tests | `AgentLoopTest`, `JsonlSessionStorageTest` | Added coverage for tool-call continuation and JSONL reopen/leaf/label behavior. |
| `packages/coding-agent/src/core/exec.ts` | `ExecCommand` | Ported process execution with stdout/stderr capture, exit code, timeout, and process-tree termination. |
| `packages/coding-agent/src/core/bash-executor.ts` | `BashExecutor`, `BashOperations`, `LocalBashOperations`, `ShellSupport` | Ported local shell execution, chunk streaming, ANSI/binary sanitization, tail truncation, and full-output temp log behavior. |
| Tests | `BashExecutorTest` | Added coverage for sanitized streaming output and stdout/stderr/code capture. |
| `packages/coding-agent/src/core/tools/truncate.ts` | `Truncation` | Completed TS-aligned head/tail truncation defaults, line/byte limit metadata, first-line byte-limit detection, UTF-8-safe partial tail truncation, size formatting, and grep-line truncation suffixes. |
| `packages/coding-agent/src/core/tools/output-accumulator.ts` | `OutputAccumulator` | Ported streaming UTF-8 decoding with split-character buffering, bounded rolling tail snapshots, line/byte truncation metadata, last-line byte tracking, temp-file persistence for full output, and append-after-finish protection; wired `BashExecutor` to use it for full-output capture. |
| `packages/coding-agent/src/core/tools/file-mutation-queue.ts` | `FileMutationQueue` | Ported per-canonical-file mutation serialization with realpath keys for existing paths, normalized absolute keys for missing paths, independent parallelism for different files, and integration into write/edit file mutations. |
| Tests | `TruncationTest`, `OutputAccumulatorTest`, `FileMutationQueueTest`, `BashExecutorTest` | Added coverage for byte/line truncation boundaries, first-line overflow, partial tail truncation, line suffix truncation, split UTF-8 streaming, truncated full-output persistence, last-line byte tracking, same-file mutation serialization, different-file mutation parallelism, and bash full-output log creation. |
| `packages/coding-agent/src/utils/frontmatter.ts` | `Frontmatter` | Ported YAML frontmatter extraction and body stripping. |
| `packages/coding-agent/src/core/skills.ts` | `Skill`, `SkillLoader`, `ResourceDiagnostic`, `SourceInfo` | Ported skill discovery, validation, collision handling, prompt XML formatting, and default/explicit source resolution. |
| `packages/coding-agent/src/core/prompt-templates.ts` | `PromptTemplate`, `PromptTemplateLoader` | Ported markdown prompt discovery, quoted argument parsing, positional/default/sliced arg substitution, and slash-template expansion. |
| Tests | `ResourceLoadingTest` | Added coverage for frontmatter, skill discovery/collision formatting, and prompt expansion. |
| `packages/coding-agent/src/core/resource-loader.ts` | `ProjectContextLoader`, `ResourceLoader` | Ported AGENTS/CLAUDE context discovery, trusted project/global SYSTEM.md and APPEND_SYSTEM.md resolution, and baseline skills/prompts/context aggregation. |
| `packages/coding-agent/src/core/tools/index.ts` | `CodingToolFactory` | Ported tool-set creation for coding, read-only, and all tools, backed by Java `AgentTool`. |
| `packages/coding-agent/src/core/tools/find.ts` | `FindTool` | Ported local glob file discovery with default limit, node_modules/.git skips, root basename matching, truncation metadata. |
| `packages/coding-agent/src/core/tools/path-utils.ts` | `PathUtils` | Aligned path resolution with TS behavior: relative paths resolve from cwd, absolute paths remain absolute. |
| Tests | `CodingToolFactoryTest` | Added coverage for real coding tools executed through `AgentLoop`, plus write/edit/bash tool chain. |
| `packages/coding-agent/src/core/session-manager.ts` | `SessionManager`, `SessionPaths`, `SessionFileInfo` | Ported session id validation, v1/v2 to v3 migration, default session directory encoding, JSONL header scanning, recent/list/all indexing, create/open/continue/fork flows, branch traversal, labels, session names, and branched-session extraction on top of Java session storage. |
| `packages/coding-agent/src/utils/paths.ts` | `PathUtils`, `SessionPaths` | Ported canonicalization fallback, local-vs-remote path detection, CLI path normalization for `~`/`file://`/leading `@`/Unicode spaces, cwd-relative display formatting, cloud-sync ignore markers, default `~/.pi/agent/sessions/<encoded-cwd>` layout, and cwd matching. |
| Tests | `PathUtilsTest` | Added coverage for CLI normalization, file URL resolution, local path classification, cwd-relative formatting, and missing-path canonicalization fallback. |
| Tests | `SessionManagerTest` | Added coverage for default session paths, id validation, create/reopen/list/name/label behavior, malformed JSONL tolerance, legacy session migration, continue-recent, and forked session history. |
| `packages/coding-agent/src/utils/json.ts` | `JsonUtils` | Ported JSON comment stripping and trailing-comma removal while preserving string literals. |
| `packages/coding-agent/src/utils/html.ts` | `HtmlUtils` | Ported named and numeric HTML entity decoding plus bounded entity scanning at an index. |
| `packages/coding-agent/src/utils/mime.ts` | `MimeUtils` | Ported image MIME sniffing for JPEG, PNG with APNG rejection, GIF, WebP, and BMP structural validation. |
| `packages/coding-agent/src/utils/sleep.ts` | `Sleep` | Ported abortable async sleep using Java `CompletableFuture` and a daemon scheduler. |
| Tests | `SmallUtilsTest` | Added coverage for JSONC cleanup, HTML entity decoding, image MIME sniffing from bytes/files, and abortable sleep. |
| `packages/coding-agent/src/core/event-bus.ts` | `EventBus` | Ported channel-based event emission, unsubscribe handles, handler exception isolation, and listener clearing with thread-safe Java collections. |
| Tests | `EventBusTest` | Added coverage for emit/unsubscribe/clear behavior and exception isolation across handlers. |
| `packages/coding-agent/src/core/defaults.ts` | `Defaults` | Ported default thinking-level constant to the Java AI domain enum. |
| `packages/coding-agent/src/core/experimental.ts` | `Experimental` | Ported exact `PI_EXPERIMENTAL=1` feature-flag detection with injectable environment map for tests. |
| `packages/coding-agent/src/core/provider-display-names.ts` | `ProviderDisplayNames` | Ported built-in provider display-name registry. |
| `packages/coding-agent/src/core/telemetry.ts` | `Telemetry` | Ported install telemetry flag resolution with environment override semantics. |
| `packages/coding-agent/src/core/provider-attribution.ts` | `ProviderAttribution` | Ported OpenRouter, NVIDIA NIM, Cloudflare, Vercel Gateway, and OpenCode session attribution header merging with user header precedence. |
| `packages/coding-agent/src/core/timings.ts` | `Timings` | Ported opt-in startup timing namespaces, elapsed interval capture, and formatted timing output. |
| `packages/coding-agent/src/core/session-cwd.ts` | `SessionCwd` | Ported missing session cwd detection, prompt/error formatting, and typed exception. |
| `packages/coding-agent/src/core/output-guard.ts` | `OutputGuard` | Ported stdout takeover, raw stdout write path, flush/backpressure wait, and restoration semantics for Java streams. |
| Tests | `CoreUtilitiesTest`, `OutputGuardTest` | Added coverage for defaults, provider names, attribution headers, telemetry override, timings, session cwd errors, and stdout guarding. |
| `packages/coding-agent/src/core/trust-manager.ts` | `TrustManager`, `ProjectTrustStore` | Ported canonical trust paths, nearest ancestor trust lookup, trust option generation, trust-requiring project resource detection, locked `trust.json` reads/writes, sorted persistence, and null-decision removal. |
| `packages/coding-agent/src/core/auth-guidance.ts` | `AuthGuidance` | Ported provider login help and no-model/no-api-key guidance formatting. |
| Tests | `TrustManagerTest` | Added coverage for trust options, inherited decisions, deletion semantics, invalid trust store validation, resource detection, user `.agents/skills` exclusion, and auth guidance messages. |
| `packages/coding-agent/src/core/resolve-config-value.ts` | `ConfigValueResolver` | Ported API-key/header config resolution for literals, env templates, escapes, shell commands with process-lifetime cache, missing-env diagnostics, and header resolution. |
| `packages/ai/src/env-api-keys.ts` | `EnvApiKeys` | Ported known provider API-key environment variable mapping, Anthropic OAuth-token precedence, Bedrock ambient credential detection, and Google Vertex ADC fallback. |
| `packages/coding-agent/src/core/auth-storage.ts` | `AuthStorage`, `FileAuthStorageBackend`, `InMemoryAuthStorageBackend` | Ported auth credential model, locked `auth.json` persistence with private permissions, runtime API-key overrides, provider env values, auth status reporting, environment fallback, error draining, login/logout hooks, and OAuth refresh-with-lock flow with reload fallback. |
| Tests | `AuthStorageTest` | Added coverage for config value parsing/commands/headers, env API key mapping, memory/file auth backends, runtime override precedence, environment fallback status, OAuth refresh persistence, failed-refresh reload fallback, and malformed storage errors. |
| `packages/coding-agent/src/core/model-registry.ts` | `ModelRegistry` | Ported built-in/custom model registry composition, JSONC `models.json` loading, provider overrides, model overrides, custom model replacement, request API-key/header resolution, auth status mapping, provider display names, dynamic provider registration, and OAuth-auth detection over Java model options. |
| `packages/coding-agent/src/core/model-resolver.ts` | `ModelResolver` | Ported default provider model table, exact/canonical/fuzzy model matching, thinking-level suffix parsing, glob model scopes, CLI provider/model resolution, custom model-id fallback, initial model selection, and session model restore fallback. |
| Tests | `ModelRegistryTest` | Added coverage for JSONC model loading, provider/model overrides, request auth/header resolution, dynamic provider registration/unregistration, resolver matching, glob scopes, fallback custom IDs, initial selection, restore fallback, and validation errors. |
| `packages/coding-agent/src/core/system-prompt.ts` | `SystemPromptBuilder` | Ported default/custom system prompt assembly, selected-tool snippets, prompt guideline de-duplication, append-system-prompt insertion, project context XML, skill prompt gating by read-tool availability, current date, and cwd footer. |
| `packages/coding-agent/src/core/messages.ts` | `CodingAgentMessages` | Ported bash execution message formatting, custom extension messages, branch summaries, compaction summaries, context exclusion for `!!` bash messages, and conversion to LLM user messages. |
| `packages/agent/src/types.ts`, `packages/agent/src/agent-loop.ts` | `AgentContext`, `AgentLoop.Config` | Added injectable `transformToLlm` support while preserving the default LLM-only transform, then wired coding-agent sessions to use the coding-agent message transformer. |
| `packages/coding-agent/src/core/keybindings.ts` | `KeybindingsManager` | Ported TUI/app keybinding definitions, default/user binding resolution, legacy key name migrations, ordered effective config generation, conflict detection, file loading/reload, and common terminal input matching. |
| `packages/coding-agent/src/core/compaction/*.ts` | `CompactionSupport` | Ported compaction file-operation tracking, file list formatting, summarization conversation serialization, context token estimation from assistant usage plus trailing messages, compaction threshold checks, cut point/turn-start detection, latest-compaction context rebuild, and compaction preparation with previous summary and split-turn prefixes. |
| `packages/coding-agent/src/core/slash-commands.ts` | `SlashCommands` | Ported builtin slash command definitions in TS order, command source typing, builtin lookup, external command merging, and slash invocation name/argument parsing. |
| `packages/coding-agent/src/core/http-dispatcher.ts` | `HttpDispatcher` | Ported idle-timeout choices, timeout parsing/formatting, proxy setting application, invalid-timeout validation, and Java `HttpClient` creation with HTTP/1.1 plus optional proxy selector. |
| `packages/coding-agent/src/core/footer-data-provider.ts` | `FooterDataProvider` | Ported git metadata discovery from cwd ancestors, regular repo/worktree `.git` handling, HEAD branch/detached resolution, `.invalid` reftable fallback through git, extension status storage, provider counts, cwd switching, debounced WatchService branch refresh, and branch-change callbacks. |
| `packages/coding-agent/src/core/diagnostics.ts`, `packages/coding-agent/src/core/source-info.ts` | `ResourceDiagnostic`, `SourceInfo` | Completed warning/error/collision diagnostic payloads, collision source labels, source scope defaults, source origin defaults, and package/top-level source metadata. |
| `packages/coding-agent/src/core/settings-manager.ts` | `SettingsManager`, `Settings` | Expanded settings loading/writing to TS-aligned global/project JSONC merge, trust gating, legacy settings migrations, error draining, external-change-preserving writes, typed getters for model/theme/transport/compaction/retry/http/editor/resources/terminal/image/session options, in-memory overrides, and project package writes. |
| `packages/coding-agent/src/core/agent-session.ts` | `AgentSession` | Started Java session abstraction tying together `SessionManager`, `ModelRegistry`, `AgentLoop`, thinking-level/model changes, tool selection, prompt execution, TS-compatible session message JSON persistence, event subscription, model cycling, session names, and basic stats. |
| `packages/coding-agent/src/core/agent-session-services.ts` | `AgentSessionServices` | Ported cwd-bound service composition for auth storage, settings manager, model registry, resource loader, diagnostics, and service-backed AgentSession creation with tool allow/deny/no-tools resolution plus real system-prompt construction from loaded resources and selected tools. |
| `packages/coding-agent/src/core/agent-session-runtime.ts` | `AgentSessionRuntime` | Ported runtime replacement shell for initial creation, session switch/resume, new session, fork before/at entry, JSONL import, missing import error, cwd validation, teardown, rebind callback, and model fallback propagation. |
| `packages/orchestrator/src/types.ts` | `InstanceStatus`, `MachineRecord`, `RadiusRegistration`, `InstanceRecord` | Ported instance lifecycle enum and records with immutable update helpers. |
| `packages/orchestrator/src/config.ts` | `OrchestratorConfig` | Ported directory resolution supporting `PI_ORCHESTRATOR_DIR`, `PI_CONFIG_DIR`, and default `~/.pi/orchestrator` paths. |
| `packages/orchestrator/src/storage.ts` | `OrchestratorStorage` | Ported JSON-backed machine and instances storage persistence with thread-safe upserts and removals. |
| `packages/ai/src/main/java/works/earendil/pi/ai/provider/OpenAiProvider.java`, `AnthropicProvider.java` | `OpenAiProvider`, `AnthropicProvider` | Ported OpenAI and Anthropic built-in provider implementations with model catalog registries and SSE streaming support. |
| `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/*.java` | `Main`, `CliArgs`, `PrintModeRunner`, `InteractiveModeRunner`, `RpcModeRunner` | Completed standalone CLI entry point with Picocli argument parsing, model listing `--list-models`, single-shot `--print` execution, rich interactive console REPL mode, and JSON-RPC over stdio mode for external integrations. |
| Tests | `SystemPromptBuilderTest`, `CodingAgentMessagesTest`, `KeybindingsManagerTest`, `CompactionSupportTest`, `SlashCommandsTest`, `HttpDispatcherTest`, `FooterDataProviderTest`, `ExperimentalAndDiagnosticsTest`, `SettingsManagerTest`, `AgentSessionRuntimeTest`, `OrchestratorStorageTest`, `OrchestratorSupervisorTest`, `BuiltinProvidersTest`, `CliEntryTest` | Added coverage for TS-aligned prompt formatting, coding-agent custom message conversion, keybinding migration/loading/conflicts/matching, compaction serialization/file tracking/token estimation/context rebuild/preparation, slash command order/lookup/parsing, HTTP timeout/proxy/client configuration, footer git branch/status/provider-count behavior, experimental flag semantics, diagnostic/source metadata, settings merge/migration/trust/error/timeouts/resource getters/external write preservation, service-created system prompts reaching model context, prompt persistence through AgentLoop, tool selection, model cycling, thinking-level persistence, runtime new/switch/import/fork flows, rebind callbacks, missing import errors, orchestrator machine/instance storage lifecycle, supervisor instance spawn/stop/recovery transitions, built-in provider stream emission, and CLI print/interactive/rpc entry point execution. |

## Current Java inventory

| Metric | Count |
| --- | ---: |
| Main Java files | 122 |
| Main Java lines | 12422 |
| Test Java files | 31 |

## Verification

```bash
mvn test -q
```

Status: passing.

## Source inventory

Generated inventory source: `/tmp/pi-ts-sources.txt`.

Full conversion status will be expanded file-by-file as each module is ported.
