# Pi TypeScript 到 Pi Java 完整改造与演进白皮书 (Transformation Roadmap)

本文档是 `pi` (TypeScript 版本的 AI 编程智能体生态) 向 `pi-java` (生产级 Java 21 + Maven Monorepo) 全面改造迁移的权威技术白皮书与路线图。本文档详细梳理了 TS 版本的全部核心功能矩阵、截至目前的 Java 改造进度与验证证据，并剖析了剩余差距与下一步的开发指南，旨在为后续的迭代演进提供清晰、可执行的导航图。

---

## 一、 改造背景与架构原则

### 1.1 迁移愿景
TypeScript 版本的 `pi` 提供了极其灵活、响应式的 AI 编程辅助与多智能体协调生态。为适应企业级级联部署、更高并发与强类型 JVM 环境的整合需求，我们开启了 `pi-java` 的重构工作。目标是在 **Java 21 虚线程 (Virtual Threads)** 与强类型契约下，实现与 TS 版本 **100% 的功能对齐与行为互通**。

### 1.2 核心架构原则
1. **语言纯洁性与强类型契约**：坚持使用原生 Java 21 (Record, Sealed Classes, Pattern Matching, CompletableFuture) 与纯 JVM 依赖，避免外部不稳定的 Native 包装。
2. **零配置本地安全隔离**：所有配置、密钥及会话记录严格持久化在本地目录 (`~/.pi/agent/` 或项目根目录 `.pi/`)，严禁将私密凭证或示例配置提交至远程仓库。
3. **高保真流式响应**：保持与 TS 架构完全一致的 SSE 流式事件解析、ToolCall 增量拼接与分块传输，确保 CLI 交互控制台的极致丝滑输出。

---

## 二、 TS vs Java 功能全景矩阵对照表

| 模块 / 子包 (`packages/*`) | 核心功能特性 (TS 功能描述) | Java 当前状态 | 对齐程度 | 关键实现与落地点 |
| :--- | :--- | :--- | :---: | :--- |
| **`common`** | 基础工具库、字符串截断、JSON 编码/解析、并发辅助 | ✅ 完整实现 | **100%** | `JsonCodec`, `Truncation`, `AnsiUtils` 完整对齐 |
| **`ai`** | 多模型提供商统一接口、SSE 流式解析、Token 统计、Message 模型 | 🟡 核心实现 | **96%** | 实现了 `Message`/`Content` 体系与 `OpenAiProvider`, `AnthropicProvider`, `GeminiProvider`, `GroqProvider`, `MistralProvider`, `OllamaProvider`, `XaiProvider`，接入共享 HTTP retry / rate-limit 传输治理、全局与 provider-id 级设置透传、Ollama 本地动态发现与 CLI/RPC 刷新入口；待扩展更细粒度多模态回放 |
| **`agent`** | 智能体循环 (AgentLoop)、工具契约、JSONL v3 会话存储、UUIDv7 | ✅ 完整实现 | **100%** | `AgentLoop`, `JsonlSessionStorage`, `AgentTool` 支持生命周期拦截与回传 |
| **`tui`** | 终端 UI 渲染、ANSI 格式化、文本排版、交互式组件 | 🟡 基础实现 | **86%** | 实现了基础控制台 REPL I/O、ANSI 截断、带 Token/Timings 统计及宽度自适应的交互状态行，新增 Markdown 代码块与 Diff split-view 渲染模型，并接入 JAnsi 主题化组件输出、交互消息路径、工具调用阶段 edit/write diff 预览、折叠预览、orchestrator live 面板与 skill trigger diagnostic 面板输出；全屏高级窗口待扩展 |
| **`coding-agent`**| 编程工具集、沙箱执行、安全护栏、上下文压缩、配置管理、CLI 启动器 | ✅ 核心自治完成 | **97%** | 7大核心工具、沙箱 Bash、Harness 鉴权、Token 压缩、RPC 模式完全就绪，交互模式使用 Markdown/Diff/Panel 组件渲染 assistant、tool 与 orchestrator live 输出，edit 工具支持 TS 风格多处替换，edit/write 支持调用前 diff preview 与结果 diff details，并新增可从 session 恢复的状态化 `/grill-me` 面谈流程、可真实派生 sub-agent 的 `/teamwork-preview run`、项目 `.agents/skills` 发现、技能 prompt 变量渲染、trigger hints、运行时命中诊断、交互式诊断面板、`/skill-diagnostics` 回看命令与显式 `/skill:name` 技能调用 |
| **`orchestrator`**| 多智能体管理、进程树监控、IPC 通信协议、实例持久化 | 🟢 架构完成 | **97%** | 实现了 `OrchestratorStorage`, `OrchestratorSupervisor`, IPC 消息模型，打通可注入的 coding-agent RPC stdio 子进程管理，新增 sub-agent 任务协调器，并补齐 heartbeat、stderr 日志归档、日志索引、日志轮转、stale restart、restart backoff/max attempts、运行策略配置、RPC event stream 订阅、coding-agent 交互命令触发入口与 `/orchestrator-status` 可观测性、stderr log snapshot/follow、live RPC events 入口、结构化面板输出和 dashboard snapshot |

---

## 三、 当前已改造功能进度详解 (Completed Status & Evidence)

截至本阶段，`pi-java` 已经成功实现了底层通信、智能体主循环到上层编程工具链的完整闭环，执行 `mvn test` 全部 217 项核心自动化单元测试 100% 通过（`BUILD SUCCESS`）。

### 3.1 核心编程工具链 (Tool Calling & Execution)
在 `works.earendil.pi.codingagent.tools` 下完成了 TypeScript 版本标准的 7 大核心编程工具：
* **`read` (`ReadTool`)**：支持按行/按字节截断读取，自动附加超长截断提示语。
* **`write` (`WriteTool`)**：支持安全覆盖写入、目录自动创建、调用前 diff preview 与结果 diff details 输出。
* **`edit` (`EditDiff`)**：实现了精准字符串替换、多处 `edits[]` 替换、重复命中/重叠/no-op 校验与 diff details 输出。
* **`ls` (`LsTool`)**：目录层级遍历与排版输出。
* **`grep` (`GrepTool`)**：基于正则的高效文件内容匹配检索。
* **`find` (`FindTool`)**：基于 Glob 模式的文件路径搜索。
* **`bash` (`BashTool` + `BashExecutor`)**：实现了 **沙箱命令执行**。支持指定工作目录 (CWD)、执行超时控制 (Timeout)、stdout/stderr 实时流式捕获与输出长度防爆屏截断。

### 3.2 Harness 权限管理与安全护栏 (Security Guardrails)
在 `works.earendil.pi.codingagent.core.TrustManager` 与 `AgentSession` 中实现了完整的项目信任体系：
* **安全拦截机制**：每次主循环执行前动态校验当前 CWD 在 `~/.pi/agent/trust.json` 中的授权状态。
* **分级管控**：当项目未被明确信任 (Untrusted) 时，高危修改类工具（`bash`, `write`, `edit`）会被安全护栏动态拦截并返回明确说明，而读取类工具（`read`, `ls`, `grep`, `find`）仍可正常运行以完成代码库调研。

### 3.3 智能上下文窗口压缩 (Context Window Compaction)
在 `works.earendil.pi.codingagent.core.CompactionSupport` 中无缝对接了 LLM 上下文管理：
* **Token 预估与阈值触发**：每次进入 `prompt` 前自动预估当前对话 Token 占用，当接近模型最大窗口限制 (如 128k/200k) 且满足安全边际时自动触发压缩。
* **大模型摘要重构**：通过注入系统专业总结 Prompt，调用大模型生成精简对话摘要，同时自动提取保留历史对话中的文件读取与修改记录（`<read-files>` / `<modified-files>`）。
* **JSONL V3 存储重塑**：压缩完成后通过 `SessionManager.appendCompaction` 记录分界点，并重构内存消息链，防止长时间开发会话导致显存溢出或请求失败。

### 3.4 CLI 多模式启动器与 RPC 交互 (CLI & RPC)
在 `works.earendil.pi.codingagent.cli` 下完成了四重运行模式：
* **`--list-models`**：刷新并列出本地配置与默认注册的所有可用 LLM 模型。
* **`--print` (`PrintModeRunner`)**：单次指令问答，处理完毕后自动退出，适合脚本管道集成。
* **交互式控制台 REPL (`InteractiveModeRunner`)**：实现了多轮对话、模型动态切换指令 (`/model`)、模型刷新指令 (`/models refresh [provider]`)、会话状态保存与恢复。
* **标准输入输出 RPC 模式 (`RpcModeRunner`)**：实现基于 JSON-RPC 的进程间通信，支持 `list_models` 与带可选 `params.provider` 的 `refresh_models` 模型查询/刷新动作为 VSCode 插件、Web 前端及多智能体管理程序提供底层通信管道。

### 3.5 Google Gemini 官方 Provider
在 `works.earendil.pi.ai.provider.GeminiProvider` 中新增了 Google Gemini 原生 REST/SSE 驱动：
* **模型目录**：默认注册 `google` provider，覆盖 `gemini-2.0-flash`, `gemini-2.5-flash`, `gemini-2.5-pro`, `gemini-3.1-pro-preview` 等 TS 侧核心 Gemini 模型。
* **原生请求格式**：将 Java `Context` 转换为 Gemini `contents`, `systemInstruction`, `tools.functionDeclarations`, `generationConfig` 与 `thinkingConfig`。
* **函数调用解析**：解析 Gemini `functionCall` part 为 `Content.ToolCall`，并将工具结果回放为 `functionResponse`。
* **流式事件映射**：支持 SSE `data:` chunk 解析，输出 `ContentDelta`, `UsageDelta`, `End`，并映射 `STOP`, `MAX_TOKENS` 与工具调用停止原因。
* **离线验证**：`BuiltinProvidersTest` 覆盖默认注册、请求体构造与 Gemini chunk 解析，避免单元测试依赖真实 API。

### 3.6 OpenAI-Compatible Provider 扩展
在 `works.earendil.pi.ai.provider.OpenAiCompatibleProvider` 中抽象了 OpenAI Chat Completions 兼容传输层，并新增 Groq / Mistral / xAI 内置 provider：
* **`GroqProvider`**：默认注册 `groq` provider，覆盖 `llama-3.1-8b-instant`, `llama-3.3-70b-versatile`, `openai/gpt-oss-120b`, `qwen/qwen3-32b` 等 TS 侧核心 Groq 模型。
* **`MistralProvider`**：默认注册 `mistral` provider，覆盖 `devstral-medium-latest`, `mistral-large-latest`, `mistral-small-latest`, `pixtral-large-latest` 等 TS 侧核心 Mistral 模型。
* **`OllamaProvider`**：默认注册 `ollama` provider，内置 `llama3.1:8b`, `qwen2.5-coder:7b`, `gpt-oss:20b` 等本地模型入口，复用 OpenAI Chat Completions 兼容层，并通过 `/api/tags` 支持本地已下载模型刷新。
* **`XaiProvider`**：默认注册 `xai` provider，覆盖 `grok-3`, `grok-4.3`, `grok-code-fast-1`, `grok-build-0.1` 等 TS 侧核心 xAI 模型，并保留 compat 元数据。
* **Ollama 零配置鉴权**：在 `ModelRegistry` 中将 `ollama` 标记为本地零配置 provider，无需 API key 即可进入可用模型列表，并向请求层提供与 TS 文档一致的 `ollama` 占位 key。
* **共享协议层**：复用 Chat Completions `messages`, `tools`, `tool_calls`, `usage` 与 SSE chunk 解析，避免后续 OpenAI-compatible provider 重复实现。
* **离线验证**：`BuiltinProvidersTest` 覆盖 Groq/Mistral/Ollama/xAI 默认注册、模型元数据、请求体构造与 OpenAI-compatible chunk 解析；`ModelRegistryTest` 覆盖 Ollama 无密钥可用与占位 key 解析。

### 3.7 Provider HTTP 传输治理
在 `works.earendil.pi.ai.provider.ProviderHttpSupport` 中抽象了 provider 共享 HTTP 发送层：
* **指数退避重试**：根据 `StreamOptions.maxRetries` 对 429 / 500 / 502 / 503 / 504 与 IO 异常进行可配置重试，默认最多重试 2 次。
* **Retry-After 支持**：解析服务端 `Retry-After` 秒级响应头，并受最大重试延迟上限约束，避免无限等待。
* **Provider 级并发限制**：通过 provider 维度的信号量限制并发 HTTP 请求，降低 burst 流量导致的速率限制风险。
* **统一接入点**：`OpenAiProvider`、`GeminiProvider` 与 `OpenAiCompatibleProvider` 均已改为通过 `ProviderHttpSupport` 发送请求，避免新旧 provider 传输行为分裂。
* **离线验证**：`BuiltinProvidersTest` 使用 fake `HttpResponse` 覆盖 retryable status 重试、非重试状态识别与 `Retry-After` 延迟封顶。

### 3.8 Provider Retry/Rate-Limit 设置透传与覆盖
在 `SettingsManager`、`AgentSessionServices` 与 `AgentSession` 中打通了用户设置到 provider 请求层的链路：
* **设置读取**：支持读取 `retry.enabled`, `retry.maxRetries`, `retry.baseDelayMs`, `retry.provider.timeoutMs`, `retry.provider.maxRetries`, `retry.provider.maxRetryDelayMs`, `retry.provider.maxConcurrentRequests`，以及 `retry.providers.<providerId>` 下的同名 provider-id 覆盖项。
* **StreamOptions 构造**：`AgentSessionServices.buildStreamOptions` 将上述设置转换为 `StreamOptions.timeout`, `StreamOptions.maxRetries` 与 provider metadata，供 `ProviderHttpSupport` 按当前 provider id 合并消费。
* **Provider 级覆盖**：`OpenAiProvider`、`GeminiProvider` 与 `OpenAiCompatibleProvider` 均会按 provider id 应用 `timeoutMs`, `maxRetries`, `baseDelayMs`, `maxRetryDelayMs`, `maxConcurrentRequests`；`maxRetries: 0` 可用于禁用特定 provider 的 HTTP 重试。
* **会话级复用**：普通 prompt 与上下文压缩摘要调用共用同一份 settings-derived `StreamOptions`，避免压缩请求绕过 provider 传输治理。
* **用户可见说明**：根目录 `README.md` 已补充 `~/.pi/agent/settings.json` 与 `.pi/settings.json` 中 retry/rate-limit 配置示例。
* **离线验证**：`SettingsManagerTest` 覆盖 retry 设置迁移、getter 与 provider-id 覆盖解析；`AgentSessionRuntimeTest` 捕获真实会话 prompt 传入 provider 的 `StreamOptions`；`BuiltinProvidersTest` 验证 `ProviderHttpSupport` 会按 provider id 应用 retry/timeout/concurrency 覆盖。

### 3.9 动态模型刷新与 Ollama 本地发现
在 `Provider`、`ProviderRegistry`、`ModelRegistry` 与 `OllamaProvider` 中补齐了 TS 侧 `refreshModels()` 风格的动态模型刷新能力：
* **Provider refresh 钩子**：`Provider` 新增默认 `refreshModels()`，静态 provider 无需额外实现即可保持兼容。
* **注册表刷新链路**：`ProviderRegistry.refreshModels()` 聚合调用各 provider 刷新逻辑，`ModelRegistry.refresh()` 会先刷新内置 provider，再重新合并 built-in/custom 模型；`ModelRegistry.refresh(providerId)` 支持按 provider id 刷新，与 TS 侧 `Models.refresh(provider?)` 对齐。
* **Ollama `/api/tags` 发现**：`OllamaProvider.refreshModels()` 以短超时读取本地 Ollama `/api/tags`，将响应中的 `name`/`model` 转为 OpenAI-compatible 模型，并保留默认静态模型作为兜底。
* **推断元数据**：动态发现模型会标记 `options.discovered = true`，并对 `gpt-oss`、`deepseek-r1`、`qwq` 等本地 reasoning 模型自动设置 reasoning 元数据。
* **用户入口**：`--list-models` 会在打印前刷新模型列表；交互式 REPL 支持 `/models refresh [provider]`；RPC 模式支持 `refresh_models` 并返回刷新后的模型数组。
* **离线验证**：`BuiltinProvidersTest` 使用 fake `/api/tags` 响应验证本地模型解析、endpoint 派生与 reasoning 推断；`ModelRegistryTest` 验证 `refresh()` 会重新加载动态 provider 模型。

### 3.10 交互式状态行雏形
在 `InteractiveModeRunner` 中接入 `FooterDataProvider`，为基础 REPL 增加 TS 侧 Footer Display 的 Java 雏形：
* **Prompt 前状态行**：每次进入 `pi> ` 提示符前输出当前 Git 分支、当前 provider/model、会话消息计数、累计 Token 消耗量与可用 provider 数量。
* **模型刷新联动**：`/models refresh [provider]` 完成后会重新统计可用 provider 数量，确保状态行与最新模型目录一致。
* **Token 汇总与恢复**：`AgentSession.stats()` 汇总 assistant `Usage` 的 input/output/cache/reasoning/total token；`CompactionSupport` 从 JSONL 恢复 assistant usage，避免 resume 后状态行与上下文压缩估算丢失 token 依据。
* **Turn 延时与 Timings 明细**：普通用户 prompt 完成后输出本轮耗时、更新后的消息计数、累计 token，并通过 `Timings` 记录 agent 执行阶段耗时与 total 汇总，为后续全屏 footer 打下基础。
* **终端宽度自适应**：状态行与 turn 行按真实 console 的 `COLUMNS` 列宽进行 East Asian Width 感知裁剪，避免窄终端中 footer 挤压提示符或换行错位；测试/管道模式使用稳定默认宽度。
* **离线验证**：`CliEntryTest` 覆盖交互式状态行、普通 prompt 后 turn elapsed/timings 输出、宽度自适应裁剪与 `/models refresh` 命令路径；`AgentSessionRuntimeTest` 覆盖 token stats 汇总；`CompactionSupportTest` 覆盖 JSONL usage 恢复。

### 3.11 Markdown 与 Diff 渲染基础层
在 `packages/tui` 中新增面向后续全屏组件的文本渲染模型：
* **Markdown 行模型**：`MarkdownRenderer` 可识别标题、列表、引用、水平线、代码 fence 与代码内容；代码块会保留语言信息，并对关键字、字符串、注释、数字、标点等生成样式 span。
* **流式 fence 稳定性**：未闭合或部分闭合的代码 fence 会继续按代码内容处理，避免流式输出阶段代码块反复收缩/展开。
* **Diff split-view 模型**：`DiffSplitRenderer` 可解析 unified diff，将删除/新增行配对为左右栏 `SplitLine`，并按 East Asian Width 对左右列做宽度裁剪，为后续分屏 Diff 组件提供数据层。
* **模块测试启用**：`pi-tui` 增加 JUnit/AssertJ 测试依赖；`MarkdownRendererTest` 与 `DiffSplitRendererTest` 覆盖代码块高亮、流式 fence、删除/新增配对和 CJK 宽度裁剪。

### 3.12 Markdown / Diff 主题化终端组件
在 `packages/tui` 的组件层继续向 TS 侧 `Markdown` 与 `renderDiff` 对齐：
* **JAnsi 主题层**：新增 `TerminalTheme`，为 Markdown 标题、引用、代码 fence、代码关键字、字符串、注释、Diff hunk、上下文、新增与删除行提供统一 ANSI 样式映射。
* **Markdown 组件输出**：新增 `component.Markdown`，复用 `MarkdownRenderer` 的行模型与 span 信息，输出定宽、带 padding、带 ANSI 样式的终端行，并保留 `Component.render(RenderContext)` 的纯文本 Surface 回退路径。
* **Diff 组件输出**：新增 `component.Diff`，复用 `DiffSplitRenderer` 生成左右栏，按新增/删除/变更类型分别着色，并输出定宽 split-view 行。
* **宽字符 Surface 修正**：修复 `Surface.write` 对 CJK 宽字符的列宽处理，避免纯文本回退渲染中中文字符被额外空格拆开。
* **离线验证**：`MarkdownTest` 与 `DiffTest` 覆盖 ANSI 输出、定宽行、Surface 纯文本回退与 CJK 宽字符场景；`mvn -pl packages/tui -am test` 当前通过 14 项相关测试。

### 3.13 交互模式接入 Markdown / Diff 输出
在 `packages/coding-agent` 的 REPL 输出路径中接入 TUI 组件：
* **Assistant Markdown 输出**：`InteractiveModeRunner` 不再直接打印 `ContentDelta` 原始文本，而是汇总当前 assistant 消息后交给 `InteractiveOutputRenderer`，再由 `component.Markdown` 输出标题、代码 fence 与代码高亮。
* **Tool 执行可见状态**：交互模式现在消费 `ToolExecutionStart` 与 `ToolExecutionEnd` 事件，输出工具开始/完成状态，并将工具结果内容统一送入 Markdown 渲染。
* **Diff 工具结果分栏**：`InteractiveOutputRenderer` 会识别带 hunk 与增删行的 unified diff，并使用 `component.Diff` 输出左右分栏视图，为后续 edit/write 预览与全屏 diff 面板打基础。
* **测试覆盖**：`InteractiveOutputRendererTest` 覆盖 assistant Markdown ANSI 输出与 unified diff split-view；`CliEntryTest` 覆盖交互模式真实输出中出现 Markdown ANSI 渲染。

### 3.14 Edit Diff Details 与工具输出折叠预览
在 `packages/coding-agent` 的工具链与交互渲染之间补齐更接近 TS `ToolExecutionComponent` / `edit` renderer 的数据流：
* **Edit 结果携带 Diff**：`CodingToolFactory.edit` 复用 `EditDiff.unifiedPatch`，在 `AgentToolResult.details` 中返回 `path`, `replacements`, `diff`, `patch`，让 UI 层不再只能看到一句成功文案。
* **Details 优先渲染**：`InteractiveOutputRenderer` 会优先读取 `Message.ToolResult.details.diff` 并使用 `component.Diff` 输出 split-view；如果没有 details，则回退到 text content 的 unified diff 探测。
* **折叠预览**：普通长工具输出默认显示最后 20 行并提示隐藏行数；长 diff 默认显示前 20 行并提示隐藏 diff 行数，为后续交互式展开/折叠快捷键打基础。
* **离线验证**：`CodingToolFactoryTest` 覆盖 edit details 中的 diff/replacements；`InteractiveOutputRendererTest` 覆盖 details diff 优先渲染与长输出折叠预览。

### 3.15 Edit 多处替换语义对齐
在 `packages/coding-agent` 的 `edit` 工具中继续对齐 TS 侧 `edit.ts` 的多处替换输入与校验语义：
* **输入兼容**：`CodingToolFactory.edit` 支持 TS 风格 `edits[]`，同时保留旧版 `oldText` / `newText` 参数；针对模型偶发传入字符串化 JSON 的情况，也支持解析 `edits` JSON string。
* **原文匹配语义**：`EditDiff.apply` 会先在原始文件内容中匹配所有 replacement，再按原始 offset 倒序应用，避免前一个替换改变后续 replacement 的定位。
* **失败校验**：空 `edits`、空 `oldText`、未命中、重复命中、replacement 区间重叠以及替换后无变化都会返回明确错误，降低模型误改文件的风险。
* **工具结果元数据**：成功结果会返回替换块数，并在 details 中携带 `diff` / `patch` / `replacements`，供交互 UI 继续做 split-view 和折叠展示。
* **离线验证**：新增 `EditDiffTest` 覆盖多处替换、重复命中、重叠与 no-op；`CodingToolFactoryTest` 覆盖 `edits[]` 数组输入、字符串化 JSON 输入与 details replacement 计数。

### 3.16 Tool Call 阶段预览
在 `packages/coding-agent` 的交互事件渲染路径中补齐更接近 TS `renderCall()` 的调用阶段可见性：
* **Tool Start 专用渲染**：`InteractiveModeRunner` 在 `ToolExecutionStart` 时不再只输出工具名，而是调用 `InteractiveOutputRenderer.renderToolStart`，集中渲染工具名、关键参数摘要与后续 preview。
* **Edit / Write 调用前 Diff Preview**：当 `edit` 调用参数完整且目标文件可读取时，交互模式会在真正执行工具前读取原始文件、按 `edits[]` / `oldText` / `newText` 计算预期内容；当 `write` 调用参数完整时，会按目标文件现有内容或空文件内容生成预期写入 diff，并使用现有 `component.Diff` split-view 输出预览。
* **非阻断错误预览**：如果预览阶段遇到缺失文件、非法参数、重复命中、no-op 或 overwrite=false 冲突等问题，只显示 `edit preview unavailable` / `write preview unavailable`，不会阻断 AgentLoop 后续工具执行与正式错误返回。
* **参数摘要**：`write`, `read`, `ls`, `grep`, `find`, `bash` 等工具调用开始时会显示 path、pattern、command、content chars 等关键字段，避免长 JSON 参数直接刷屏。
* **Write 结果 Diff Details**：`WriteTool` 会在同一文件 mutation queue 内返回写入前后内容，`CodingToolFactory.write` 在成功结果 details 中返回 `path`, `created`, `bytes`, `diff`, `patch`，让 UI 与日志路径均可复用写入 diff。
* **离线验证**：`InteractiveOutputRendererTest` 覆盖 edit/write start preview diff、预览失败提示、畸形参数非阻断渲染与原有 result diff/折叠渲染路径；`CodingToolFactoryTest` 覆盖 write create/overwrite details diff；`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 129 项测试通过。

### 3.17 Orchestrator RPC Stdio 子进程管道
在 `packages/orchestrator` 中将原先只登记内存实例的 supervisor 推进到可管理 coding-agent RPC 子进程：
* **进程抽象层**：新增 `AgentProcess` 与 `AgentProcessLauncher`，将 stdin/stdout 读写、存活检测与停止操作从 supervisor 中解耦，便于真实进程与测试 fake 进程共用同一套生命周期逻辑。
* **默认 RPC Launcher**：新增 `RpcAgentProcessLauncher.currentJava()`，默认使用当前 Java 可执行文件、当前 classpath 与 `works.earendil.pi.codingagent.cli.Main --mode rpc` 启动子进程，并用后台 reader thread 收集 stdout JSONL 响应、drain stderr。
* **Supervisor 生命周期接入**：`OrchestratorSupervisor.spawnInstance` 会通过 launcher 启动子进程，成功后将实例置为 `ONLINE`；失败时置为 `ERROR` 并清理进程；`stopInstance` 会发送 JSON-RPC `exit` 消息并停止进程。
* **RPC 发送入口**：新增 `sendRpc(instanceId, message, timeout)`，向 live instance 的 stdin 写入 JSON-RPC line，并从 stdout 读取一行响应，同时刷新 `lastSeenAt`。
* **离线验证**：`OrchestratorSupervisorTest` 使用 fake launcher 覆盖 spawn/stop 进程生命周期、stdio RPC 发送/读取、缺失或已停止进程返回空响应；`mvn -pl packages/orchestrator -am test` 当前 `pi-orchestrator` 6 项测试通过。

### 3.18 原生 Slash 命令：Grill Me 与 Teamwork Preview
在 `packages/coding-agent` 的交互命令层补齐 TS 侧高级交互命令的 Java 雏形：
* **`/grill-me [topic]`**：新增 `GrillMePrompt`，将命令转换为结构化设计面谈 prompt，要求 assistant 在提出方案前逐步澄清需求、约束、用户、权衡、成功标准与失败模式，并复用现有交互模式 Markdown / Tool 输出渲染路径。
* **`/teamwork-preview [compact]`**：新增 `TeamworkPreview`，基于当前 cwd、主模型、可用 provider 数、已加载 skill 数与 steering/follow-up 设置，渲染 researcher / implementer / reviewer 等 sub-agent 角色、工具权限与 handoff 输出。
* **命令表与帮助输出**：`SlashCommands.BUILTIN_SLASH_COMMANDS` 与 `InteractiveModeRunner.printHelp()` 均已登记两个命令，交互模式可直接执行。
* **离线验证**：`GrillMePromptTest`, `TeamworkPreviewTest`, `SlashCommandsTest` 与 `CliEntryTest` 覆盖 prompt 构造、团队预览渲染、命令表顺序和真实 REPL 输入路径；`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 133 项测试通过。

### 3.19 Sub-agent 任务协调器与 RPC 事件复用
在 `packages/orchestrator` 中继续推进 TS 侧多智能体任务链路：
* **JSON-RPC id 关联读取**：`OrchestratorSupervisor.sendRpc` 不再盲读第一行 stdout，而是按请求 `id` 读取最终 response；中间 `event` / 非匹配 response 会被收集到 `RpcExchange.events`，避免 `prompt` 调用被流式 `content_delta` 事件截断。
* **进程级发送锁**：supervisor 为 live process 维护 per-instance lock，允许不同子进程并行 RPC，同时避免同一子进程内请求/响应交错。
* **Sub-agent 协调器**：新增 `SubAgentTaskCoordinator`，接受 cwd、objective 与 role 列表，使用 Java 21 virtual threads 并行 spawn 多个 coding-agent RPC 子进程，向每个子进程发送 role-specific `prompt`，收集中间事件与最终响应，并按配置自动 stop/recycle instance。
* **失败隔离**：单个 role 的 spawn / RPC timeout / RPC exception 会落到该 role 的 `SubAgentResult.error`，避免直接吞掉其它 sub-agent 的结果。
* **离线验证**：`OrchestratorSupervisorTest` 覆盖 event-before-response 的 id 匹配读取；`SubAgentTaskCoordinatorTest` 覆盖多 role spawn、prompt 下发、event 收集、response 收集、stop 回收与 storage 清理；`mvn -pl packages/orchestrator -am test` 当前 `pi-orchestrator` 8 项测试通过。

### 3.20 Orchestrator Heartbeat 与 stderr 日志归档
在 `packages/orchestrator` 的进程监管层补齐长期运行所需的最小可观测性：
* **日志目录约定**：`OrchestratorConfig` 新增 `logs/` 目录和 `getInstanceStderrLogPath(instanceId)`，按 instance id 生成安全文件名，避免子进程 stderr 混入主进程输出。
* **stderr 归档**：`RpcAgentProcessLauncher` 支持传入 stderr log dir，默认 supervisor 会使用 orchestrator storage 的 `logs/` 目录；真实子进程 stderr 会追加写入 `<instanceId>.stderr.log`，`AgentProcess.stderrLogPath()` 可暴露日志位置。
* **Heartbeat Sweep**：`OrchestratorSupervisor.heartbeat()` 会遍历 live instances：活进程刷新 `lastSeenAt`，已退出或缺失的进程移出 live process map、清理 per-instance lock，并将 instance 标记为 `ERROR` 后持久化。
* **默认接入**：`new OrchestratorSupervisor(storage)` 已自动创建带日志目录的 `RpcAgentProcessLauncher.currentJava(storage.config().getLogsDir())`。
* **离线验证**：`OrchestratorStorageTest` 覆盖日志路径清洗；`RpcAgentProcessLauncherTest` 使用当前 JDK `java -version` 验证 stderr 实际落盘；`OrchestratorSupervisorTest` 覆盖 heartbeat 刷新在线实例与标记 dead process 为 ERROR；`mvn -pl packages/orchestrator -am test` 当前 `pi-orchestrator` 11 项测试通过。

### 3.21 Stale Restart 与日志索引
在 `packages/orchestrator` 中继续补齐多智能体长期运行的恢复与可观测性：
* **日志索引**：`OrchestratorStorage.listInstanceLogs()` 会扫描 `logs/*.stderr.log`，返回 instance id、绝对日志路径、文件大小与最后修改时间，便于后续 CLI / UI 展示子智能体 stderr 历史。
* **Stale Restart 策略**：`OrchestratorSupervisor.restartStaleInstances(Duration staleAfter)` 会按 live instance 的 `lastSeenAt` 判断是否超时；超时后先向旧子进程发送 `orchestrator-restart/exit`，停止旧进程，再使用同一个 instance id、cwd、label 重新启动 RPC 子进程。
* **状态持久化**：restart 过程中 instance 会进入 `STARTING`，成功后回到 `ONLINE` 并刷新 `lastSeenAt`；失败则标记 `ERROR` 并保留错误信息在 `RestartResult` 中。
* **离线验证**：`OrchestratorStorageTest` 覆盖日志索引排序、大小与修改时间；`OrchestratorSupervisorTest` 覆盖 stale instance 使用同一 id 重启、旧进程退出、新进程在线与 storage 状态更新；`mvn -pl packages/orchestrator -am test` 当前 `pi-orchestrator` 13 项测试通过。

### 3.22 Restart Policy 退避与最大重试
在 `packages/orchestrator` 的 stale restart 基础上补齐生产运行所需的失败节流策略：
* **可配置重启策略**：`OrchestratorSupervisor.RestartPolicy(maxAttempts, baseBackoff, maxBackoff)` 支持设置最大失败次数、基础退避时间与最大退避上限；默认策略为 3 次失败、30 秒基础退避、5 分钟上限。
* **失败追踪与跳过**：restart 失败后 supervisor 会记录该 instance 的失败次数与 `nextAllowedAt`；退避窗口内再次扫描会返回 `restart backoff active`，达到最大失败次数后返回 `max restart attempts reached`，不会继续拉起子进程。
* **恢复后清理**：restart 成功会清除失败追踪，后续失败重新按第一次失败计数；显式 stop instance 与 supervisor recover 也会清理对应重启追踪状态，避免旧状态污染新生命周期。
* **确定性测试时钟**：supervisor 增加包内可见的 `Clock` 注入构造器，测试可精确推进时间并验证 backoff 与 max attempts 行为。
* **离线验证**：`OrchestratorSupervisorTest` 覆盖 restart 失败后的退避窗口、最大重试耗尽、成功重启后清除失败计数；`mvn -pl packages/orchestrator -am test` 当前 `pi-orchestrator` 15 项测试通过。

### 3.23 stderr 日志轮转与历史索引
在 `packages/orchestrator` 的子进程 stderr 归档基础上补齐长期运行日志容量控制：
* **默认轮转策略**：`RpcAgentProcessLauncher.LogRotationPolicy` 默认按 1MiB 当前日志与 3 个备份文件进行 stderr 日志轮转；测试与后续配置入口可通过构造器覆盖 `maxBytes` 与 `maxBackups`，也可显式禁用轮转。
* **按行写入前轮转**：stderr drainer 在 append 每一行前检查当前日志大小，超过阈值时将 `<instanceId>.stderr.log` 轮转为 `.stderr.log.1`，旧备份顺延到 `.2/.3`，超过保留数量的历史文件会被删除。
* **多进程写入保护**：日志写入按规范化路径加锁，降低同一 instance 重启交接期间旧进程 stderr drain 与新进程 stderr 写入互相覆盖的风险。
* **历史日志索引**：`OrchestratorStorage.listInstanceLogs()` 现在会同时索引当前日志与 `.stderr.log.N` 备份文件，并在 `InstanceLogRecord.rotation` 中暴露当前文件 `0`、历史备份 `1..N` 的代数，便于后续 CLI / UI 展示。
* **离线验证**：`RpcAgentProcessLauncherTest` 使用测试 JVM 连续写 stderr 触发真实轮转；`OrchestratorStorageTest` 覆盖当前日志、轮转日志、非法后缀过滤与排序；`mvn -pl packages/orchestrator -am test` 当前 `pi-orchestrator` 16 项测试通过。

### 3.24 Orchestrator 运行策略配置
在 `packages/orchestrator` 中补齐 restart policy 与 log rotation 的持久化配置入口：
* **配置文件约定**：`OrchestratorConfig.getRuntimeSettingsPath()` 指向 `~/.pi/orchestrator/orchestrator.json`，或 `PI_ORCHESTRATOR_DIR/orchestrator.json`；新增 `OrchestratorRuntimeSettings` 作为强类型配置模型。
* **Restart 配置**：`restart.maxAttempts`, `restart.baseBackoffMs`, `restart.maxBackoffMs` 会被解析为默认 stale restart 策略；无效或缺失字段保留默认值，并确保 `maxBackoff >= baseBackoff`。
* **Log Rotation 配置**：`logRotation.enabled`, `logRotation.maxBytes`, `logRotation.maxBackups` 会被解析为默认 stderr 轮转策略；`enabled: false` 会禁用轮转。
* **默认构造接入**：`new OrchestratorSupervisor(storage)` 现在会读取 runtime settings，并将 restart policy 注入 supervisor，将 log rotation policy 注入默认 `RpcAgentProcessLauncher.currentJava(...)`。
* **用户可见说明**：根目录 `README.md` 已补充 `orchestrator.json` 配置示例与字段说明。
* **离线验证**：`OrchestratorConfigTest` 覆盖默认值、restart/logRotation 解析、禁用轮转与无效字段回退；`OrchestratorSupervisorTest` 覆盖默认构造路径确实使用 runtime settings；`mvn -pl packages/orchestrator -am test` 当前 `pi-orchestrator` 29 项测试通过。

### 3.25 RPC Event Stream 外部订阅
在 `packages/orchestrator` 的 JSON-RPC id 匹配读取基础上补齐外部事件流消费入口：
* **订阅 API**：`OrchestratorSupervisor.subscribeRpcEvents(listener)` 支持订阅全部 RPC 中间事件；`subscribeRpcEvents(instanceId, listener)` 支持按 instance id 过滤事件。
* **事件模型**：新增 `RpcEvent(sequence, instanceId, requestId, rawJson, receivedAt)`，对外暴露事件顺序、来源 instance、请求 id、原始 JSON 行与接收时间。
* **流式发布与返回值兼容**：`sendRpcExchange` 在跳过非匹配 response 的中间 `event` 行时会即时发布 `RpcEvent`，同时继续把原始 event 行保留在 `RpcExchange.events()`，不破坏已有 sub-agent 结果收集路径。
* **订阅生命周期**：`RpcEventSubscription.close()` 可取消订阅；监听器异常会被隔离，不会阻断其它订阅者或 RPC response 读取。
* **进程内共享 Runtime**：新增 `OrchestratorRuntime.shared()`，让 coding-agent REPL 中的 `/teamwork-preview run`、状态报告、log tail 与 live event 订阅复用同一个 `OrchestratorSupervisor`，避免状态命令订阅到孤立 supervisor。
* **离线验证**：`OrchestratorSupervisorTest` 覆盖全局订阅、按 instance 订阅、取消订阅、序号递增、request id 解析、监听器异常隔离以及 `RpcExchange.events()` 兼容；`OrchestratorRuntimeTest` 覆盖共享 storage/supervisor/status reporter 包装；`mvn -pl packages/orchestrator -am test` 当前 `pi-orchestrator` 29 项测试通过。

### 3.26 `/teamwork-preview` 真实 Sub-agent 执行入口
在 `packages/coding-agent` 与 `packages/orchestrator` 之间补齐从交互命令直接发起多智能体任务的执行链路：
* **依赖方向修正**：`pi-orchestrator` 不再编译期依赖 `pi-coding-agent`；默认 RPC launcher 改用 coding-agent main class 字符串启动子进程，`pi-coding-agent` 显式依赖 `pi-orchestrator`，避免 Maven 循环依赖。
* **交互命令执行**：`/teamwork-preview run <objective>` 会复用预览生成的 role 列表，构造 `SubAgentTaskCoordinator.TaskRequest`，按 role 并行启动 coding-agent RPC 子进程并发送 role-specific prompt。
* **Compact 执行模式**：`/teamwork-preview compact` 仍保持预览；`/teamwork-preview compact run <objective>` 会执行 compact role set，适合快速派生 implementer/reviewer 两类子任务。
* **结果报告**：交互输出会展示 objective、cwd、role 数、模式、每个 role 的 instance id、中间 event 数、是否自动 stop、response 摘要或 error 信息。
* **运行配置复用**：默认执行路径使用 `OrchestratorRuntime.shared()` 中的 `OrchestratorConfig` / `OrchestratorStorage` / `OrchestratorSupervisor`，因此自动继承 `orchestrator.json` 中的 restart policy 与 stderr log rotation 设置，并能被当前 REPL 的 live event 订阅观察。
* **离线验证**：`TeamworkPreviewTest` 覆盖执行参数解析、未提供 objective 的错误提示、可注入 executor 的执行请求映射与结果渲染；`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 158 项测试通过，且相关 reactor 中 `pi-orchestrator` 29 项测试通过。

### 3.27 Orchestrator 可观测性状态入口
在 `packages/orchestrator` 与 `packages/coding-agent` 之间补齐面向本地调试的只读状态入口：
* **状态报告模型**：新增 `OrchestratorStatusReporter`，从 `OrchestratorStorage` 读取持久化 instance 列表、stderr log 索引和 `orchestrator.json` runtime settings，生成可复用的 `StatusReport`。
* **Heartbeat 可见性**：报告会展示每个 instance 的 `lastSeenAt` 与相对 heartbeat age，便于判断后台 sub-agent 是否已经长时间无响应；无 instance 时也会明确输出 `heartbeat: no instances`。
* **日志与配置可见性**：报告会展示 orchestrator dir、settings path、restart policy、log rotation 设置、每个 instance 的日志数量与最新 stderr 日志路径，并列出前 5 条日志索引。
* **Event Stream 可见性**：报告显式展示 `OrchestratorSupervisor.subscribeRpcEvents(instanceId, listener)` RPC event stream 能力，为后续实时 tail / UI 订阅入口打基础。
* **stderr Log Tail**：`OrchestratorStatusReporter.tailLatestLog` 可从日志索引中按 instance 选择当前轮转日志并输出末尾 N 行；交互命令支持 `/orchestrator-status tail [instanceId] [lines]`，无 instance 时会从所有日志中选择当前日志。
* **stderr 增量 Follow**：新增 `OrchestratorLogTailer`，可按 instance 或全部 current stderr log 从订阅时刻的文件末尾开始轮询新增行，遇到 current log 截断/轮转后从新文件开头继续；交互命令支持 `/orchestrator-status tail --follow [instanceId]` 与 `/orchestrator-status tail --stop`。
* **Live RPC Events**：交互命令支持 `/orchestrator-status events [instanceId]` 启动后台 RPC event 订阅，并通过 `/orchestrator-status events stop` 取消；后续同一 REPL 中的 `/teamwork-preview run` 会经共享 supervisor 发布 live event，输出 sequence、instance、request id、接收时间与原始 JSON。
* **交互命令接入**：`/orchestrator-status` 已登记为内置 slash command，并在 coding-agent REPL 中输出上述状态报告、stderr tail 或 live RPC events；根目录 `README.md` 已补充使用说明。
* **离线验证**：`OrchestratorStatusReporterTest` 覆盖 runtime settings、instance、heartbeat age、stderr log index、event stream 状态渲染、最新日志 tail 与无日志提示；`OrchestratorLogTailerTest` 覆盖订阅启动后只输出新增行、current log 截断/轮转后继续输出；`CliEntryTest` 覆盖 `/orchestrator-status tail` / `tail --follow` / `events` 帮助文案、参数错误渲染、log follow 输出新增 stderr 行，以及 event tailer 接收并打印 live RPC event；`mvn -pl packages/orchestrator -am test` 当前 `pi-orchestrator` 29 项测试通过，`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 158 项测试通过。

### 3.28 项目 `.agents/skills` 发现与技能变量渲染
在 `packages/coding-agent` 的 skill loader 与 system prompt builder 中继续对齐 TS 侧项目技能发现能力，并补齐 Java 侧 prompt 变量渲染：
* **项目 `.agents/skills` 发现**：trusted project 会从当前 cwd 开始，逐级向上扫描 `.agents/skills`，直到 git repo root；包含 `SKILL.md` 的技能目录会按现有规则加载，且 `.agents/skills` 根目录下的普通 `.md` 文件不会被当作技能，保持与 TS 文档一致。
* **信任边界**：未信任项目不会加载项目 `.agents/skills`，避免本地仓库中的自定义技能在用户授权前进入系统 prompt；显式 `--skill` / settings skill path 仍走原有路径加载。
* **技能变量渲染**：`SystemPromptBuilder` 会为技能描述提供 `{{cwd}}` / `${cwd}`、`{{agent_dir}}`、`{{date}}`、`{{skill_name}}`、`{{skill_dir}}` 与 `{{skill_path}}` 等变量，替换发生在 XML escape 之前，只影响系统 prompt 中的技能元数据，不改写磁盘上的 `SKILL.md`。
* **向后兼容**：旧的 `SkillLoader.formatSkillsForPrompt(skills)` 保持可用；新增 `SkillPromptContext` 由系统 prompt 构建路径使用，避免破坏已有调用者。
* **用户可见说明**：根目录 `README.md` 已补充 trusted project `.agents/skills` 发现路径和支持的占位符列表。
* **离线验证**：`ResourceLoadingTest` 覆盖 trusted/untrusted `.agents/skills` 加载与 ancestor discovery；`SystemPromptBuilderTest` 覆盖技能描述变量渲染；`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 158 项测试通过。

### 3.29 显式 `/skill:name` 技能命令展开
在 `packages/coding-agent` 的会话入口继续对齐 TS 侧 skill command 语义，让已加载技能可以被用户显式注入到下一轮 prompt：
* **命令展开语义**：`SkillLoader.expandSkillCommand` 识别 `/skill:name optional instructions`，读取对应 `SKILL.md`，剥离 YAML frontmatter 后生成 `<skill name="..." location="...">` 块，并附带 `References are relative to ...` 路径说明。
* **参数注入**：命令后的剩余文本会作为 additional instructions 追加到技能块后方，保持与 TS 侧 `/skill:name args` 行为一致。
* **统一入口**：`AgentSession.prompt` 在持久化用户消息前执行技能命令展开，因此交互模式、`--print` 与 RPC prompt 路径共享同一语义。
* **设置开关**：`SettingsManager.getEnableSkillCommands()` 继续作为用户开关；设置 `"enableSkillCommands": false` 时 `/skill:*` 会按普通用户文本保留。
* **命令列表展示**：`SlashCommands.skillCommands` 会把已加载技能转换为 `/skill:<name>` 外部命令；交互模式 `/help` 会列出 `Loaded skills`，未知 `/skill:*` 会给出本地 `Skill not found` 反馈，避免明显拼错的技能命令静默进入模型。
* **事件上报**：`AgentSession` 会在技能命令处理时发出 `skill_command` 事件，包含 `start` / `end` / `error` phase、skill name、skill path 与错误信息；JSON print 与 RPC event stream 会透出该事件，便于外部 UI 和 orchestrator 观察技能展开状态。
* **条件触发控制面**：`disable-model-invocation: true` 技能会从模型可见的 `<available_skills>` 列表中隐藏，避免模型按描述自动触发；同时仍保留显式 `/skill:name` 调用能力，并在命令描述中标记 `(manual only)`。
* **离线验证**：`AgentSessionRuntimeTest` 覆盖技能命令展开后的用户消息持久化、start/end 事件、技能文件丢失时的 error 事件、manual-only 技能显式调用，以及关闭 `enableSkillCommands` 后保留原始输入；`SlashCommandsTest` 覆盖技能命令元数据与 manual-only 标记；`SystemPromptBuilderTest` 覆盖 manual-only 技能不进入模型可见 prompt；`CliEntryTest` 覆盖交互 `/help` 列出已加载技能、未知技能提示与 orchestrator status 参数路径；`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 158 项测试通过。

### 3.30 Orchestrator Live 输出面板化
在 `packages/coding-agent` 的交互渲染路径中继续推进 `/orchestrator-status` 的实时可观测性：
* **结构化事件面板**：`InteractiveOutputRenderer.renderOrchestratorEvent` 将 live RPC event 渲染为固定宽度 ASCII panel，展示 sequence、instance、request id、接收时间与原始 JSON payload，并按终端宽度截断，避免长 JSON 破坏 REPL 布局。
* **结构化 stderr 面板**：`InteractiveOutputRenderer.renderOrchestratorLogLine` 将 stderr follow 新增行渲染为固定宽度 panel，展示 instance、接收时间、文件名优先的日志路径和新增 stderr 行；长绝对路径会保留文件名可见性。
* **交互订阅接入**：`OrchestratorEventTailer` 与 `OrchestratorLogFollowTailer` 的后台回调已改为调用统一渲染器，`/orchestrator-status events [instanceId]` 与 `/orchestrator-status tail --follow [instanceId]` 在同一 REPL 中输出一致的 orchestrator live 面板。
* **离线验证**：`InteractiveOutputRendererTest` 覆盖 event/log panel 的关键字段与终端宽度约束；`CliEntryTest` 覆盖 live event tailer 与 stderr follow tailer 通过面板打印新增事件/日志；`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 158 项测试通过。

### 3.31 Orchestrator Dashboard Snapshot
在 `packages/orchestrator` 与 `packages/coding-agent` 之间继续推进 4.4 的 dashboard 数据面：
* **Recent RPC Event Buffer**：`OrchestratorSupervisor` 新增有界 recent RPC event history，即使当前没有 live subscription，也会在 `sendRpcExchange` 读取中间 event 时记录 sequence、instance、request id、原始 JSON 与接收时间；`recentRpcEvents(instanceId, maxEvents)` 支持按 instance 过滤和数量截断。
* **Dashboard 数据视图**：`OrchestratorStatusReporter.dashboard(...)` 会组合当前 instance snapshot、stderr log index、current stderr tail 和 recent RPC events，生成可渲染的 `DashboardView`。
* **事件 / stderr 分栏**：`DashboardView.render()` 输出面向后续全屏 UI 的 dashboard snapshot，顶部展示 scope、instance/log/event 计数，下方展示 instance 列表，并以 `event stream | stderr` 双列并排呈现最近 RPC event 与当前 stderr 行。
* **CLI 入口**：交互命令新增 `/orchestrator-status dashboard [instanceId] [events]`（别名 `dash`），可按 instance 聚合查看 dashboard，并通过第二个参数控制最近事件数量。
* **离线验证**：`OrchestratorSupervisorTest` 覆盖无订阅时仍保留 recent event、按 instance 过滤与数量截断；`OrchestratorStatusReporterTest` 覆盖 dashboard 的 instance 过滤、event/stderr 双列与 stderr tail 展示；`CliEntryTest` 覆盖 dashboard 帮助文案与参数错误；`mvn -pl packages/orchestrator -am test` 当前 `pi-orchestrator` 29 项测试通过，`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 158 项测试通过。

### 3.32 `/grill-me` 状态化面谈流程
在 `packages/coding-agent` 的高级 slash command 层继续推进 4.3：
* **本地面谈状态**：新增 `GrillMeInterview`，在交互 REPL 生命周期内保存当前 topic、阶段和用户回答历史；阶段会从 `discovery` 进入 `constraints`，再进入 `synthesis`，为后续更完整的面谈状态机留出数据结构。
* **回答历史注入**：`GrillMePrompt.build(topic, phase, answers, controls)` 会把当前阶段、已记录回答数和 answer history 注入下一轮 prompt，让 assistant 能基于已回答内容继续追问，而不是每次从空白面谈开始。
* **交互控制命令**：`/grill-me <topic>` 启动或重启面谈；`/grill-me answer <text>` 记录用户回答并继续生成下一轮面谈 prompt；`/grill-me status` 只读展示当前 topic、phase 与 answers；`/grill-me reset` / `stop` 清除当前面谈。
* **离线验证**：`GrillMePromptTest` 覆盖状态化 prompt、phase 迁移、answer history、status 与 reset；`CliEntryTest` 覆盖 REPL 中 `/grill-me status`、`answer` 和 `reset` 的真实输入路径；`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 158 项测试通过。

### 3.33 `/grill-me` 会话恢复与 assistant question 摘要
在 3.32 的 REPL-local 面谈状态基础上，继续补齐跨进程恢复能力：
* **Session JSONL 快照**：`GrillMeInterview` 新增 `CUSTOM_TYPE=grill_me_interview` 状态快照，交互模式在 `/grill-me` start / answer / reset 后通过 `SessionManager.appendCustomEntry` 写入当前 active、topic、answers 与 assistant question summaries。
* **跨进程恢复**：`InteractiveModeRunner` 启动时调用 `GrillMeInterview.fromSession(session.sessionManager())`，从当前 session branch 最新的 `grill_me_interview` custom entry 恢复面谈状态；reset 也会落盘为 inactive 快照，避免 reopen 后误恢复旧面谈。
* **assistant question 摘要提取**：每轮 `/grill-me` prompt 执行后，`GrillMeInterview.captureLatestAssistantQuestion` 会从最新 assistant message 的文本块提取问题行摘要；后续 prompt 会携带 `Previous assistant question summaries`，`/grill-me status` 也会展示已记录的问题摘要。
* **离线验证**：`GrillMePromptTest` 覆盖 custom entry 持久化、恢复、reset 后 inactive 恢复，以及 assistant question summary 注入；`CliEntryTest` 覆盖交互模式真实 start/status/answer/reset 路径中的问题摘要展示；`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 158 项测试通过。

### 3.34 Skill Trigger Hints 与细粒度模型触发策略
在 `packages/coding-agent` 的技能元数据与系统 prompt 渲染层继续推进 4.3：
* **兼容式触发控制**：`Skill` 保留 `disable-model-invocation: true` 的 manual-only 语义，同时新增 `model-invocation: manual|auto` 前置元数据；`manual` 会让技能从模型可见 `<available_skills>` 中隐藏，但仍可通过显式 `/skill:name` 调用。
* **Trigger Hints 元数据**：`SkillLoader` 解析 `trigger-terms`、`trigger-patterns` 与 `trigger-globs` frontmatter 列表，用于表达关键词、正则式任务线索和文件 glob 线索，避免只靠一段 description 承担全部触发语义。
* **系统 Prompt 注入**：`SkillLoader.formatSkillsForPrompt` 会在模型可见技能上输出 `<activation>` 块，包含 `trigger_terms`、`trigger_patterns` 与 `trigger_globs`；prompt 说明也会提醒模型把 trigger hints 作为额外匹配依据，并继续尊重用户显式请求。
* **向后兼容**：旧的 6 参数 `Skill` 构造器、旧 `disable-model-invocation` frontmatter、旧 `/skill:name` 展开路径和 manual-only help 标记均保持可用。
* **离线验证**：`ResourceLoadingTest` 覆盖 trigger hints frontmatter 解析、`model-invocation: manual` 隐藏语义和 prompt activation 块输出；`SystemPromptBuilderTest` 覆盖最终系统 prompt 中 trigger hints 的渲染；`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 158 项测试通过。

### 3.35 Skill Trigger Diagnostics 运行时可观测性
在 3.34 的 trigger hints 元数据基础上，继续补齐运行时诊断事件，让外部 UI 能解释技能建议来源：
* **命中诊断模型**：`SkillLoader.matchTriggerHints` 会对普通用户 prompt 进行关键词、正则式与路径 glob 匹配，返回 `SkillTriggerMatch`，包含技能名、技能路径、是否模型可见以及 `term:*` / `pattern:*` / `glob:*` 命中原因。
* **Session 事件接入**：`AgentSession.prompt` 在非显式 `/skill:name` 输入上发布 `skill_trigger_diagnostic` 事件；显式技能命令仍只发布 `skill_command` 生命周期事件，避免同一次调用出现重复解释。
* **Print/RPC 输出**：JSON print 与 JSON-RPC event stream 会输出 `skill_trigger_diagnostic`，每个 match 包含 `skill`、`path`、`modelVisible` 和 `reasons` 字段，为后续 TUI 面板、编辑器插件或 orchestrator dashboard 展示“为什么建议/隐藏某技能”提供稳定契约。
* **离线验证**：`ResourceLoadingTest` 覆盖 trigger hints 命中原因；`AgentSessionRuntimeTest` 覆盖普通 prompt 的诊断事件和显式 `/skill:name` 的去重行为；`CliEntryTest` 覆盖 print JSON 与 RPC event stream 中的 `skill_trigger_diagnostic` 输出；`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 158 项测试通过。

### 3.36 Skill Trigger Diagnostics 交互式面板
在 3.35 的事件契约基础上，继续把 trigger diagnostics 接入交互式终端输出：
* **TUI 面板渲染**：`InteractiveOutputRenderer.renderSkillTriggerDiagnostic` 将每次命中的 skill、model 可见性、reasons 与 `SKILL.md` 路径渲染为固定宽度 ASCII panel，并复用现有 EastAsianWidth 截断逻辑，避免长路径或复杂 glob 破坏 REPL 布局。
* **交互订阅接线**：`InteractiveModeRunner.executePrompt` 现在会在普通 prompt 触发 `skill_trigger_diagnostic` 时立即打印 `Skill trigger diagnostic` 面板；显式 `/skill:name` 仍保持原有 `skill_command` 生命周期事件路径。
* **离线验证**：`InteractiveOutputRendererTest` 覆盖 trigger diagnostic 面板字段与终端宽度约束；`CliEntryTest` 覆盖真实交互输入中 trigger term 命中后打印 skill diagnostic 面板；`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 158 项测试通过。

### 3.37 `/skill-diagnostics` 最近一次技能建议回看
在 3.36 的即时面板基础上，继续补齐交互式调试命令，让用户能在 assistant 输出后回看最近一次技能建议来源：
* **REPL 内存快照**：`InteractiveModeRunner.SkillDiagnosticHistory` 在本次交互会话内保存最近一次 `SkillTriggerDiagnostic`，不会改写 session JSONL，也不会影响 print/RPC 的无状态输出。
* **只读调试命令**：新增 `/skill-diagnostics`（别名 `/skill-diagnostic`）展示最近一次诊断；无命中时输出 `status: no recent matches`，`/skill-diagnostics clear` / `reset` 可清除当前快照。
* **Help 与命令元数据**：`SlashCommands` 将 `/skill-diagnostics` 注册为内置命令，交互 `/help` 展示回看/清除用法，保持与其它高级 slash command 的发现方式一致。
* **离线验证**：`SlashCommandsTest` 覆盖内置命令顺序；`CliEntryTest` 覆盖无最近诊断、普通 prompt 命中后回看、清除后再次回看的完整交互路径；`mvn -pl packages/coding-agent -am test` 当前 `pi-coding-agent` 158 项测试通过。

---

## 四、 差距分析与待改造清单 (Gap Analysis & Roadmap)

为了达到真正的全功能超越，下次工作应当聚焦于以下四个方向进行延续改造：

### 4.1 AI 模型生态扩展 (`packages/ai`)
* **现状**：目前已原生对接并测试了 OpenAI (`OpenAiProvider`)、Anthropic (`AnthropicProvider`)、Google Gemini (`GeminiProvider`)、Groq (`GroqProvider`)、Mistral (`MistralProvider`)、Ollama (`OllamaProvider`) 与 xAI (`XaiProvider`) 协议格式（同时也支持通过 NewAPI/OpenRouter 等兼容接口接入 DeepSeek v4 等模型）。
* **下一步任务**：
  1. 进一步对齐 Gemini/Mistral/Ollama 多模态工具结果、responseId 与更细粒度 thinking signature 回放。

### 4.2 TUI 终端界面高级渲染 (`packages/tui`)
* **现状**：目前 CLI 交互处于基础控制台输出阶段，能够保证正确的文字与 Tool 执行状态打印，并已具备基于 `FooterDataProvider` 的结构化状态行，可展示 Git 分支、当前模型、消息计数、Token 使用量、provider 数量、单轮耗时与 `Timings` 明细，并支持终端宽度自适应；`packages/tui` 已具备 Markdown 代码块、Diff split-view 渲染模型，以及 JAnsi 主题化 Markdown/Diff 组件输出，且已接入 REPL 的 assistant、tool start preview、tool result 与 orchestrator live panel 输出路径；edit/write 工具调用前已有 diff preview，结果已携带 diff/patch details，长工具输出已有折叠预览。仍缺乏 TS 版本中基于全屏终端的炫酷渲染。
* **下一步任务**：
  1. 引入并深化基于 JLine3 / JAnsi 的全屏面板管理器。
  2. 将当前 `InteractiveOutputRenderer` 进一步扩展为 patch 类工具调用阶段预览，并补齐工具输出交互式展开/折叠与实时分屏 Diff 对比。
  3. 将当前交互状态行升级为实时底部状态栏 (Footer Display)：补齐刷新去闪烁与全屏渲染状态管理。

### 4.3 技能插件与高级 Slash 命令 (`packages/coding-agent`)
* **现状**：核心命令解析与 `SKILL.md` 加载器 (`SkillLoader`) 已完成，并新增带 topic、phase、answer history、status/reset 控制面、session JSONL 恢复和 assistant question summary 注入的状态化 `/grill-me` 设计面谈流程、`/teamwork-preview` 多智能体团队配置预览命令，以及 `/teamwork-preview run <objective>` 真实 sub-agent 派生入口；trusted project 的 `.agents/skills` 会从 cwd 到 git root 被发现，技能描述支持 scoped prompt 变量渲染，技能 frontmatter 支持 `disable-model-invocation`、`model-invocation` 与 trigger hints，用户也可通过 `/skill:name args` 显式展开技能正文，交互 `/help` 会列出当前已加载技能命令，技能展开过程已具备 `skill_command` start/end/error 事件上报，trigger hints 已具备 `skill_trigger_diagnostic` 运行时命中诊断事件、交互式 TUI 面板和 `/skill-diagnostics` 最近一次回看命令，manual-only 技能已从模型条件触发列表中隐藏但保留显式调用。
* **下一步任务**：
  1. 将 skill diagnostics 从 REPL 内存快照升级为可选 session JSONL 历史，支持跨进程恢复与最近多次匹配列表。

### 4.4 多智能体分布式协同 (`packages/orchestrator`)
* **现状**：实例存储 (`OrchestratorStorage`) 与进程监管服务 (`OrchestratorSupervisor`) 已经建立，并已具备可注入的 coding-agent RPC stdio 子进程启动、按 JSON-RPC id 读取 response、收集中间 event、RPC event stream 外部订阅、stderr 日志归档、日志轮转与历史索引、运行策略配置、heartbeat 状态刷新、stale restart、restart backoff/max attempts 与停止管道；`SubAgentTaskCoordinator` 已能并行派生多个后台 sub-agent，发送 role-specific prompt，收集结果并自动回收实例，且已接入 coding-agent 交互命令 `/teamwork-preview run <objective>`；`OrchestratorRuntime.shared()` 让 teamwork 执行、状态查询和事件订阅复用同一个 supervisor；`/orchestrator-status` 已能展示 instance、runtime settings、heartbeat age、stderr log index、event stream 能力，可通过 `/orchestrator-status tail [instanceId] [lines]` 只读查看最新 stderr 日志末尾内容，通过 `/orchestrator-status tail --follow [instanceId]` 订阅新增 stderr 行并以面板输出，通过 `/orchestrator-status events [instanceId]` 订阅同一 REPL 后续 orchestrator 工作的 live RPC events 并以面板输出，也可通过 `/orchestrator-status dashboard [instanceId] [events]` 查看聚合 instance、recent RPC events 与 current stderr tail 的 dashboard snapshot。
* **下一步任务**：
  1. 将 dashboard snapshot 继续升级为全屏动态 orchestrator dashboard，补齐实时刷新、事件/stderr 分栏滚动、实例过滤快捷键与最近事件缓冲可视化。

---

## 五、 下次继续改造的行动指南 (Next Session Guide)

当新的接力会话启动时，开发者可以按照以下标准规范与步骤直接继续开发：

### 5.1 环境快速自检
工作区根目录为：`/Volumes/Data 1/github/pi-java`。
启动工作前，请先运行测试确保基线稳固：
```bash
# 编译整个模块树并执行全量单元测试 (跳过需联网的 Live API 测试)
mvn test -Dtest=!BuiltinProvidersTest
```
预期输出：`BUILD SUCCESS`，且 `pi-coding-agent` 等核心模块测试全数通过。

### 5.2 下一步切入点推荐 (优先级推荐)
**建议切入点 A：TUI 富文本控制台增强**
* **目标文件**：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java` 与 `packages/tui/src/...`
* **任务**：在现有交互状态行、Markdown/Diff 主题化组件、REPL 输出接入和 edit/write 调用前 preview 基础上，继续迁移到 JLine3 / JAnsi 全屏渲染，并补齐消息流、工具执行、patch 类预览的实时刷新去闪烁。

**建议切入点 B：继续扩展模型驱动**
* **目标文件**：`packages/ai/src/main/java/works/earendil/pi/ai/provider/*Provider.java`
* **任务**：在已完成 Gemini、Groq、Mistral、Ollama、xAI 驱动、共享 HTTP retry / rate limit、Ollama `/api/tags` 动态发现与 CLI/RPC 刷新入口的基础上，继续补齐更细粒度多模态回放。

### 5.3 代码开发与提交规范
1. **新增模型/类时**：务必使用 Java 21 Record 编写数据载体，保证不可变性。
2. **测试驱动**：在任何核心工具或处理逻辑修改后，请在对应的 `src/test/java/...` 下补充单测。
3. **配置文件安全**：所有动态运行产生的会话、凭证或模型配置文件，一律使用 `AuthStorage` 或 `SettingsManager` 写入本地的用户目录，**绝不暴露或提交到 Git 追踪列表**。

---
*文档更新时间：2026-07-01 | 维护者：Pi-Java Engineering Team*
