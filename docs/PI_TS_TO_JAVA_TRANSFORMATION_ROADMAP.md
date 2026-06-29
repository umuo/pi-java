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
| **`tui`** | 终端 UI 渲染、ANSI 格式化、文本排版、交互式组件 | 🟡 基础实现 | **83%** | 实现了基础控制台 REPL I/O、ANSI 截断、带 Token/Timings 统计及宽度自适应的交互状态行，新增 Markdown 代码块与 Diff split-view 渲染模型，并接入 JAnsi 主题化组件输出、交互消息路径与折叠预览；全屏高级窗口待扩展 |
| **`coding-agent`**| 编程工具集、沙箱执行、安全护栏、上下文压缩、配置管理、CLI 启动器 | ✅ 核心自治完成 | **92%** | 7大核心工具、沙箱 Bash、Harness 鉴权、Token 压缩、RPC 模式完全就绪，交互模式使用 Markdown/Diff 组件渲染 assistant 与 tool 输出，edit 工具结果携带 diff details |
| **`orchestrator`**| 多智能体管理、进程树监控、IPC 通信协议、实例持久化 | 🟢 架构完成 | **85%** | 实现了 `OrchestratorStorage`, `OrchestratorSupervisor`, IPC 消息模型 |

---

## 三、 当前已改造功能进度详解 (Completed Status & Evidence)

截至本阶段，`pi-java` 已经成功实现了底层通信、智能体主循环到上层编程工具链的完整闭环，执行 `mvn test` 全部 152 项核心自动化单元测试 100% 通过（`BUILD SUCCESS`）。

### 3.1 核心编程工具链 (Tool Calling & Execution)
在 `works.earendil.pi.codingagent.tools` 下完成了 TypeScript 版本标准的 7 大核心编程工具：
* **`read` (`ReadTool`)**：支持按行/按字节截断读取，自动附加超长截断提示语。
* **`write` (`WriteTool`)**：支持安全覆盖写入与目录自动创建。
* **`edit` (`EditDiff`)**：实现了精准字符串替换与多处出现校验。
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
* **Edit 结果携带 Diff**：`CodingToolFactory.edit` 复用 `EditDiff.unifiedPatch`，在 `AgentToolResult.details` 中返回 `path`, `replacements`, `diff`，让 UI 层不再只能看到一句成功文案。
* **Details 优先渲染**：`InteractiveOutputRenderer` 会优先读取 `Message.ToolResult.details.diff` 并使用 `component.Diff` 输出 split-view；如果没有 details，则回退到 text content 的 unified diff 探测。
* **折叠预览**：普通长工具输出默认显示最后 20 行并提示隐藏行数；长 diff 默认显示前 20 行并提示隐藏 diff 行数，为后续交互式展开/折叠快捷键打基础。
* **离线验证**：`CodingToolFactoryTest` 覆盖 edit details 中的 diff/replacements；`InteractiveOutputRendererTest` 覆盖 details diff 优先渲染与长输出折叠预览。

---

## 四、 差距分析与待改造清单 (Gap Analysis & Roadmap)

为了达到真正的全功能超越，下次工作应当聚焦于以下四个方向进行延续改造：

### 4.1 AI 模型生态扩展 (`packages/ai`)
* **现状**：目前已原生对接并测试了 OpenAI (`OpenAiProvider`)、Anthropic (`AnthropicProvider`)、Google Gemini (`GeminiProvider`)、Groq (`GroqProvider`)、Mistral (`MistralProvider`)、Ollama (`OllamaProvider`) 与 xAI (`XaiProvider`) 协议格式（同时也支持通过 NewAPI/OpenRouter 等兼容接口接入 DeepSeek v4 等模型）。
* **下一步任务**：
  1. 进一步对齐 Gemini/Mistral/Ollama 多模态工具结果、responseId 与更细粒度 thinking signature 回放。

### 4.2 TUI 终端界面高级渲染 (`packages/tui`)
* **现状**：目前 CLI 交互处于基础控制台输出阶段，能够保证正确的文字与 Tool 执行状态打印，并已具备基于 `FooterDataProvider` 的结构化状态行，可展示 Git 分支、当前模型、消息计数、Token 使用量、provider 数量、单轮耗时与 `Timings` 明细，并支持终端宽度自适应；`packages/tui` 已具备 Markdown 代码块、Diff split-view 渲染模型，以及 JAnsi 主题化 Markdown/Diff 组件输出，且已接入 REPL 的 assistant 与 tool 输出路径；edit 工具结果已携带 diff details，长工具输出已有折叠预览。仍缺乏 TS 版本中基于全屏终端的炫酷渲染。
* **下一步任务**：
  1. 引入并深化基于 JLine3 / JAnsi 的全屏面板管理器。
  2. 将当前 `InteractiveOutputRenderer` 进一步接入文件编辑调用阶段预览、工具输出交互式展开/折叠与实时分屏 Diff 对比。
  3. 将当前交互状态行升级为实时底部状态栏 (Footer Display)：补齐刷新去闪烁与全屏渲染状态管理。

### 4.3 技能插件与高级 Slash 命令 (`packages/coding-agent`)
* **现状**：核心命令解析与 `SKILL.md` 加载器 (`SkillLoader`) 已完成。
* **下一步任务**：
  1. 扩展更多原生交互式 Slash 命令处理逻辑，例如 `/grill-me` (交互式设计面谈)、`/teamwork-preview` (多智能体团队配置预览)。
  2. 增强 `.agents/skills` 自定义技能的动态变量替换与条件触发逻辑。

### 4.4 多智能体分布式协同 (`packages/orchestrator`)
* **现状**：实例存储 (`OrchestratorStorage`) 与进程监管服务 (`OrchestratorSupervisor`) 已经建立。
* **下一步任务**：
  1. 打通 `RpcModeRunner` 与 `OrchestratorSupervisor` 的标准 IO 管道。
  2. 实现主智能体发起任务时，自动派生、监控与回收多个后台并行子智能体 (Sub-agents) 的会话管理链路。

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
* **任务**：在现有交互状态行、Markdown/Diff 主题化组件和 REPL 输出接入基础上，继续迁移到 JLine3 / JAnsi 全屏渲染，并补齐消息流、工具执行、文件编辑预览的实时刷新去闪烁。

**建议切入点 B：继续扩展模型驱动**
* **目标文件**：`packages/ai/src/main/java/works/earendil/pi/ai/provider/*Provider.java`
* **任务**：在已完成 Gemini、Groq、Mistral、Ollama、xAI 驱动、共享 HTTP retry / rate limit、Ollama `/api/tags` 动态发现与 CLI/RPC 刷新入口的基础上，继续补齐更细粒度多模态回放。

### 5.3 代码开发与提交规范
1. **新增模型/类时**：务必使用 Java 21 Record 编写数据载体，保证不可变性。
2. **测试驱动**：在任何核心工具或处理逻辑修改后，请在对应的 `src/test/java/...` 下补充单测。
3. **配置文件安全**：所有动态运行产生的会话、凭证或模型配置文件，一律使用 `AuthStorage` 或 `SettingsManager` 写入本地的用户目录，**绝不暴露或提交到 Git 追踪列表**。

---
*文档更新时间：2026-06-29 | 维护者：Pi-Java Engineering Team*
