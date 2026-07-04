# Pi TS 优秀功能未完整迁移到 Java 的对比清单

生成时间：2026-07-03

对比范围：

- TypeScript 仓库：`/Volumes/Data 1/github/pi`
- Java 仓库：`/Volumes/Data 1/github/pi-java`

本文只记录 TS 版本中较成熟、对用户体验或生态能力有明显价值，但 Java 版本尚未迁移、只迁移了骨架、或交互入口未接通的能力。Java 版已经明显增强的能力（例如 orchestrator 状态面板、skill diagnostics、`/grill-me`、Provider HTTP 重试治理）不列为缺口。

## 核对方法

- 按 monorepo 模块逐项对齐：`ai`、`agent`、`coding-agent`、`tui`、`orchestrator`。
- 交叉检查 TS 文档、示例、测试与 Java 源码入口，避免只看文件名导致误判。
- 对“Java 已有同名文件/参数但未实际接入”的项目单独标为“部分迁移”。

## 总览

| 领域 | TS 版本优秀能力 | Java 当前状态 | 迁移状态 |
| --- | --- | --- | --- |
| 扩展平台 | TS/JS 运行时扩展、事件、命令、快捷键、UI、Provider、消息渲染器 | 只有 JAR SPI 骨架，未接入主运行链路 | 部分迁移 |
| 包生态 | npm/git/local 包、`pi` manifest、依赖安装、资源过滤、`pi config`、self-update | clone/copy 目录级包管理 | 部分迁移 |
| 交互 TUI | 全屏组件系统、overlay、选择器、会话树、主题、富编辑器 | 行式 REPL + 少量结构化输出 | 未完整迁移 |
| 会话 UX | `/resume`、`/tree`、`/fork`、`/clone`、分支标签、分支摘要、删除/重命名选择器 | 底层 SessionManager 有一部分，交互命令基本未接 | 部分迁移 |
| 分享与导出 | `/share` gist、交互 `/export`、HTML 高保真会话视图 | CLI `--export` 基础 HTML；交互 `/share` 未实现 | 部分迁移 |
| OAuth/登录 | `/login` UI、OAuth provider、device code、Copilot/OpenAI Codex 订阅登录 | AuthStorage 有 OAuth 接口；具体 provider/UI 未接 | 部分迁移 |
| AI Provider 高级协议 | OpenAI Codex Responses/WebSocket/cache affinity、GitHub Copilot、Azure、Vertex、Cloudflare、OpenRouter images、动态 API provider | 内置 provider 少一批；Bedrock 是 stub 响应 | 部分迁移 |
| 图片能力 | 粘贴/缩放/转换图片、terminal graphics、图像生成 API | 有图片 content/model 骨架和 MIME 判断，缺少完整生成与剪贴板流程 | 部分迁移 |
| SDK | npm SDK 文档与 13 个示例，运行时 API 面向嵌入 | Java 有内部类，无公开文档/示例/稳定 API 面 | 未迁移 |
| 测试与回归资产 | 大量交互、扩展、provider、session、regression 测试 | Java 测试集中在核心工具与少量 runtime | 未完整迁移 |

## 1. 扩展平台生态

### 1.1 动态 TS/JS 扩展运行时

TS 版本的扩展系统是一个完整平台：使用 `jiti` 加载 `.ts/.js`，支持虚拟模块、缓存、热重载、异步 factory、Node 内置模块和 npm 依赖。扩展可以直接 import `@earendil-works/pi-coding-agent`、`@earendil-works/pi-ai`、`@earendil-works/pi-tui` 等。

Java 版有 `ExtensionPlugin`、`ExtensionLoader`、`ExtensionRunner`，但仅支持 JAR + `ServiceLoader` 风格接口，且主流程中没有实际调用 `ExtensionLoader.loadExtensions()`、`collectTools()` 或事件 emit。也就是说目前更像预留接口，不是可用生态。

证据：

- TS：`packages/coding-agent/src/core/extensions/loader.ts`
- TS：`packages/coding-agent/src/core/extensions/runner.ts`
- TS：`packages/coding-agent/docs/extensions.md`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionLoader.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- Java 未接入搜索：`rg "ExtensionLoader|ExtensionRunner|loadExtensions|collectTools" packages/coding-agent/src/main/java`

### 1.2 扩展事件与拦截能力

TS 扩展事件覆盖资源、会话、模型、输入、工具、provider 请求、agent 生命周期等阶段。典型能力包括：

- `tool_call` 阶段阻断危险操作或修改工具参数。
- `before_agent_start` 注入上下文或系统提示。
- `before_provider_request` / `after_provider_response` 修改 provider payload。
- `input` 转换用户输入。
- `session_before_switch` / `session_before_fork` / `session_before_compact` 拦截会话操作。
- `resources_discover` 动态暴露技能、prompt、theme 等资源。

Java 版插件接口已接通基础 lifecycle/tool hook、compact 事件、`user_bash` 事件、同步 `input` transform/handled 事件、同步 `tool_call` 改参/阻断、同步 `tool_result` 结果修改、同步 `before_agent_start` 上下文/系统提示注入、`sendUserMessage` 文本消息的 steer/followUp 队列语义、`sendMessage` custom message / nextTurn delivery、`session_before_switch` / `session_before_fork` 取消拦截、扩展上下文基础 abort signal，以及基础 provider 请求/响应 hook；但仍缺 TS 版动态 TS/JS 运行时，以及 resources discover、完整 UI context、底层 provider 流取消、图像输入和 streamingBehavior 等完整事件面。

证据：

- TS：`packages/coding-agent/src/core/extensions/types.ts`
- TS 示例：`packages/coding-agent/examples/extensions/permission-gate.ts`
- TS 示例：`packages/coding-agent/examples/extensions/provider-payload.ts`
- TS 示例：`packages/coding-agent/examples/extensions/dynamic-resources/index.ts`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`

### 1.3 扩展命令、快捷键、消息渲染器、动态 Provider

TS 支持扩展注册：

- `/my-command` 命令。
- 自定义快捷键。
- CLI flag。
- 自定义消息渲染器。
- 自定义工具或覆盖内置工具。
- 自定义 provider / 覆盖 provider baseUrl、headers、OAuth、streamSimple。

Java 版 `ExtensionPlugin` 只有工具和 flag 的简化返回值，没有命令、快捷键、消息渲染、Provider 注册的完整 API，也没有将扩展工具合并进 `AgentSession` 工具列表。

证据：

- TS：`packages/coding-agent/docs/custom-provider.md`
- TS 示例：`packages/coding-agent/examples/extensions/message-renderer.ts`
- TS 示例：`packages/coding-agent/examples/extensions/custom-provider-anthropic/index.ts`
- TS 示例：`packages/coding-agent/examples/extensions/tool-override.ts`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`

## 2. 包管理与资源生态

TS 版 `pi install/remove/update/list/config` 是围绕“Pi package”的完整资源系统，而不是简单文件复制。

TS 版优秀能力：

- 支持 `npm:`、`git:`、HTTP(S)/SSH git URL、本地路径。
- npm 包安装到 `~/.pi/agent/npm` 或 `.pi/npm`，并处理依赖。
- git 包按 repo/ref 管理，支持 pinned tag/commit、更新时 reconcile。
- 支持 `package.json` 中的 `pi` manifest。
- 支持约定目录：`extensions/`、`skills/`、`prompts/`、`themes/`。
- 支持资源过滤：glob、`!exclude`、`+force include`、`-force exclude`。
- `pi config` 可以交互启停资源。
- `pi update` 默认 self-update，`--extensions` 更新包，`--all` 同时更新。
- 支持 `npmCommand` 让用户指定 npm wrapper。

Java 版 `PackageManager` 当前做的是把 git clone 或本地文件/目录复制到 `~/.pi/agent/packages` / `.pi/packages`。它没有 npm 包解析、manifest、资源过滤、依赖安装、`pi config`、self-update 或 pinned ref reconcile。

证据：

- TS：`packages/coding-agent/docs/packages.md`
- TS：`packages/coding-agent/src/package-manager-cli.ts`
- TS：`packages/coding-agent/src/core/package-manager.ts`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`

## 3. 交互式 TUI 与用户体验

### 3.1 全屏 TUI 组件系统

TS 版 `@earendil-works/pi-tui` 提供自研终端组件系统：`Component.render(width)`、输入焦点、IME 光标定位、overlay、选择器、Markdown、Box、Input、Editor、SettingsList、Image 等。扩展也可以创建自定义组件。

Java 版 `tui` 模块有 Markdown、Diff、Footer、Autocomplete、FullScreenPanel 等组件，但当前 `InteractiveModeRunner` 仍是 `BufferedReader` 行式 REPL，未形成 TS 那种全屏组件应用。

证据：

- TS：`packages/tui/src/tui.ts`
- TS：`packages/tui/src/components/editor.ts`
- TS：`packages/coding-agent/docs/tui.md`
- Java：`packages/tui/src/main/java/works/earendil/pi/tui/component`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`

### 3.2 交互选择器与设置界面

TS 版有大量可视选择器和交互面板：

- 模型选择器、scoped models 选择器。
- settings selector。
- session selector/search。
- tree selector。
- OAuth selector / login dialog。
- theme selector。
- trust selector。
- extension selector/editor。
- show images selector。
- user message selector。

Java 版 `/help` 只展示少量行式命令；`SlashCommands` 虽声明了 `/settings`、`/resume`、`/tree`、`/login` 等命令，但 `InteractiveModeRunner` 实际没有处理这些命令，会落入普通 prompt。

证据：

- TS：`packages/coding-agent/src/modes/interactive/components`
- TS：`packages/coding-agent/src/modes/interactive/interactive-mode.ts`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/SlashCommands.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`

### 3.3 富输入编辑器、快捷键、外部编辑器

TS 版交互输入有多行编辑、Undo、Kill Ring、词级导航、跳转、外部编辑器、快捷键配置、快捷键迁移、`Ctrl+P` 模型轮转、`Alt+Enter` follow-up 等完整体验。

Java 版底层已经有 `UndoStack`、`KillRing`、`WordNavigation`、`KeybindingsManager`，但行式 REPL 没有把这些能力组装成 TS 版的富输入体验。

证据：

- TS：`packages/coding-agent/docs/keybindings.md`
- TS：`packages/tui/src/components/editor.ts`
- Java：`packages/tui/src/main/java/works/earendil/pi/tui/text`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/KeybindingsManager.java`

## 4. 会话操作与分支 UX

TS 版会话能力不仅是 JSONL 存储，还包括完整交互操作：

- `pi -c` / `pi -r` / `--session` / `--fork` / `--session-id` / `--session-dir`。
- `/resume` 交互搜索、排序、按名称过滤、重命名、删除。
- `/tree` 在同一 session 文件内导航树分支。
- branch label、filter mode、branch summary。
- `/fork` 从历史 user message 新建 session。
- `/clone` 克隆当前 active branch。
- `/import` 从 JSONL 导入。

Java 版 `SessionManager` 有 `continueRecent`、`forkFrom`、`list`、`open` 等底层能力，`CliArgs` 也声明了 `--continue`、`--resume`、`--fork` 等参数；但 `Main` 当前总是 `SessionManager.create(cwd, cwd.resolve(".pi/sessions"))`，没有按这些参数选择/恢复/分叉 session。交互命令 `/resume`、`/tree`、`/fork`、`/clone`、`/import` 也没有 handler。

证据：

- TS：`packages/coding-agent/docs/sessions.md`
- TS：`packages/coding-agent/src/main.ts`
- TS：`packages/coding-agent/src/modes/interactive/components/tree-selector.ts`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/session/SessionManager.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/CliArgs.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/Main.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`

## 5. 分享、导出、剪贴板

TS 版优秀能力：

- `/share` 调用 `gh gist create --public=false`，生成可分享 viewer URL。
- `/copy` 复制最后一条 assistant 消息到系统剪贴板。
- `/export` 交互导出 HTML 或 JSONL。
- HTML export 包含更完整的会话呈现、工具块、skill block、XSS/空白处理回归测试。

Java 版有 `HtmlExporter` 和 CLI `--export`，但 HTML 是基础消息列表渲染。交互 `/share`、`/copy`、`/export` 虽在 `SlashCommands` 中声明，但没有交互 handler。

证据：

- TS：`packages/coding-agent/src/modes/interactive/interactive-mode.ts`
- TS：`packages/coding-agent/test/export-html-xss.test.ts`
- TS：`packages/coding-agent/test/export-html-skill-block.test.ts`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/export/HtmlExporter.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/Main.java`

## 6. OAuth、登录与认证 UX

TS 版提供面向用户的登录体验和 OAuth provider：

- `/login` / `/logout` 交互选择 provider。
- API key 与 OAuth 共存。
- OAuth token 刷新、文件锁、device code/browser callback。
- GitHub Copilot、OpenAI Codex、Anthropic OAuth 等订阅/企业场景。
- 扩展可以注册 OAuth provider。

Java 版 `AuthStorage` 已有 OAuth credential、refresh 和 `registerOAuthProvider` 接口，但没有内置 provider 注册点，也没有 `/login` / `/logout` handler。`OAuthTokenManager` 存在于 `ai` 包，但只是 token 文件读写/缓存工具，不等价于 TS 的完整登录流。

证据：

- TS：`packages/coding-agent/src/core/auth-storage.ts`
- TS：`packages/ai/src/utils/oauth`
- TS：`packages/coding-agent/src/modes/interactive/components/oauth-selector.ts`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AuthStorage.java`
- Java：`packages/ai/src/main/java/works/earendil/pi/ai/provider/OAuthTokenManager.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`

## 7. AI Provider 与高级协议

TS 版 `packages/ai` 覆盖的 provider 和协议更广，且很多有专项测试：

- GitHub Copilot provider。
- OpenAI Codex Responses provider。
- OpenAI Codex WebSocket / WebSocket cached / SSE fallback / cache affinity。
- Azure OpenAI Responses。
- Google Vertex。
- Cloudflare Workers AI / Cloudflare AI Gateway。
- HuggingFace、NVIDIA、OpenCode、Kimi Coding、Minimax、Ant Ling、Xiaomi Token Plan 等 provider。
- OpenRouter image generation provider。
- 动态 API provider registry，扩展可注册 `streamSimple`。

Java 版内置 provider 包括 OpenAI、Anthropic、Gemini、Groq、Mistral、Ollama、XAI、DeepSeek、Together、OpenRouter、Cerebras、Fireworks、Moonshot、Zai、Bedrock 等，但缺少上述一批 provider。尤其要注意：Java 的 `BedrockProvider` 当前只检查 AWS env 后返回 `"Hello from AWS Bedrock ..."` 的模拟内容，没有实际调用 Bedrock Converse API。

证据：

- TS：`packages/ai/src/providers/all.ts`
- TS：`packages/ai/src/api/openai-codex-responses.ts`
- TS：`packages/ai/src/api/bedrock-converse-stream.ts`
- TS：`packages/ai/src/compat.ts`
- Java：`packages/ai/src/main/java/works/earendil/pi/ai/provider/ProviderRegistry.java`
- Java：`packages/ai/src/main/java/works/earendil/pi/ai/provider/BedrockProvider.java`

## 8. 图片输入、图片处理与图像生成

TS 版图片能力包括：

- `ImageContent` 输入。
- 剪贴板图片粘贴。
- 图片 MIME 检测、转换、缩放。
- terminal image 渲染。
- `generateImages()` 图像生成 API。
- OpenRouter image generation provider。
- `images.autoResize` / `images.blockImages` 等设置和相关测试。

Java 版已有 `Content.Image`、`ImageGenModel`、`MimeUtils`、部分 settings 字段和若干支持 image input 的模型定义，但没有完整图像生成 API，也没有交互剪贴板图片粘贴流程。终端图片能力也未达到 TS 版 Kitty/Sixel/iTerm2 多协议体验。

证据：

- TS：`packages/ai/src/images.ts`
- TS：`packages/ai/src/images-api-registry.ts`
- TS：`packages/ai/src/providers/openrouter-images.ts`
- TS：`packages/coding-agent/test/clipboard-image.test.ts`
- TS：`packages/coding-agent/test/image-processing.test.ts`
- Java：`packages/ai/src/main/java/works/earendil/pi/ai/model/Content.java`
- Java：`packages/ai/src/main/java/works/earendil/pi/ai/model/ImageGenModel.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/util/MimeUtils.java`

## 9. Prompt 模板、主题、文档资源

TS 版资源系统同时支持：

- prompt templates。
- themes。
- project context files。
- package 资源发现。
- `/reload` 热重载 keybindings、extensions、skills、prompts、themes。
- 主题选择器、自动主题、主题导出测试。

Java 版已有 `ResourceLoader`、`PromptTemplateLoader`、`SkillLoader`、`ProjectContextLoader` 和 settings 字段，但交互层没有 `/settings`、`/reload`、主题选择器、prompt template 选择/展开的完整 TS UX。扩展的 `resources_discover` 也缺失。

证据：

- TS：`packages/coding-agent/docs/prompt-templates.md`
- TS：`packages/coding-agent/docs/themes.md`
- TS：`packages/coding-agent/src/core/resource-loader.ts`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`

## 10. Shell、命令与运行时便利功能

TS 交互模式支持：

- `!cmd` 运行 bash 并写入上下文。
- `!!cmd` 运行 bash 但排除上下文。
- bash execution component 展示实时输出。
- `shellPath`、`shellCommandPrefix`、Windows/WSL/Cygwin 兼容处理。
- stdout cleanliness、bash hang、WSL bash 等回归测试。

Java 已接通行式 `!` / `!!` 用户命令、`user_bash` 扩展拦截，以及 `shellCommandPrefix` / `shellPath` 对交互 bash 和模型 bash tool 的配置路径；但仍缺 TS 版 bash execution component 的全屏实时输出、折叠/展开、Esc 取消体验，以及更完整的 Windows/WSL/Cygwin 回归资产。

证据：

- TS：`packages/coding-agent/src/modes/interactive/interactive-mode.ts`
- TS：`packages/coding-agent/src/core/bash-executor.ts`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/BashExecutor.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`

## 11. SDK 与嵌入式使用

TS 版把 coding-agent 作为 npm SDK 暴露，文档和示例覆盖：

- minimal session。
- custom model。
- custom prompt。
- skills。
- tools。
- extensions。
- context files。
- prompt templates。
- API keys and OAuth。
- settings。
- sessions。
- full control。
- session runtime。

Java 版内部有 `AgentSession`、`AgentSessionRuntime`、`AgentSessionServices` 等类，但没有等价的公开 SDK 文档、示例目录、兼容承诺或面向外部嵌入的 API 指南。

证据：

- TS：`packages/coding-agent/docs/sdk.md`
- TS：`packages/coding-agent/examples/sdk`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSessionRuntime.java`

## 12. 测试与回归资产

TS 版测试覆盖大量 Java 尚未覆盖的用户可见行为：

- extension runner、extension discovery、input event、plan-mode extension。
- package manager、SSH git URL、package command paths。
- session selector、tree navigation、branching、clone、import。
- clipboard、clipboard image、image resize。
- OAuth selector、first-time setup、theme picker/export。
- export HTML XSS / skill block。
- provider 高级协议和大量 e2e/smoke。
- regressions 目录覆盖真实 issue。

Java 测试已覆盖工具、设置、部分 runtime、skill diagnostics、orchestrator 等，但缺少上述交互生态和 provider 协议级回归。

证据：

- TS：`packages/coding-agent/test`
- TS：`packages/coding-agent/test/suite/regressions`
- TS：`packages/ai/test`
- Java：`packages/coding-agent/src/test/java`
- Java：`packages/ai/src/test/java`

## 优先级建议

### P0：先补“声明了但未接通”的用户入口

这些最容易造成用户误解，因为 Java 的 help/args/类名已经出现：

- `--continue`、`--resume`、`--fork`、`--session-id`、`--no-session`、`--session-dir` 的启动路径。
- 交互 `/settings`、`/resume`、`/tree`、`/fork`、`/clone`、`/export`、`/import`、`/share`、`/copy`、`/login`、`/logout`、`/reload`。
- `--extension` / `--no-extensions` 的加载与实际工具/钩子接入。

### P1：补 TS 生态优势的核心闭环

- 可运行的扩展平台：命令、工具、事件、UI、动态 provider、resources discover。（Java 已接通 JAR SPI 的工具、命令、基础 lifecycle/tool/compact、`user_bash`、同步 `input`、`tool_call` 改参/阻断、`tool_result` 结果修改、`before_agent_start` 上下文/系统提示注入事件、`sendUserMessage` 文本消息 steer/followUp 队列、`sendMessage` custom message / nextTurn delivery、`session_before_switch` / `session_before_fork` 取消拦截、扩展上下文基础 abort signal，以及基础 provider 请求/响应 hook；动态 TS/JS 运行时和完整 UI/provider/resources 仍待补。）
- 完整 Pi package：npm/git/local、manifest、资源过滤、依赖安装、`pi config`。
- 全屏 TUI：富输入编辑器、selector、overlay、会话树。
- OAuth provider 与登录 UI。

### P2：补高级协议与体验细节

- OpenAI Codex WebSocket/cache affinity、GitHub Copilot、Azure、Vertex、Cloudflare 等 provider。
- 图像生成 API 与剪贴板图片。
- `/share` gist 与更高保真 HTML 导出。
- SDK 文档与示例。
- TS 回归测试的 Java 等价迁移。

## 防遗漏检查表

- [x] 根 README / package manifest / pom 对齐。
- [x] CLI 参数和实际 Main/Interactive handler 对齐。
- [x] `packages/ai` provider、API、auth、image 能力对齐。
- [x] `packages/coding-agent` extensions、packages、settings、sessions、resources、export 对齐。
- [x] `packages/tui` 组件和交互应用层对齐。
- [x] TS docs 与 examples 对齐，确认“优秀功能”不是未使用死代码。
- [x] TS tests 与 Java tests 对齐，补充测试资产缺口。
