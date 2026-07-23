# Pi TS 优秀功能未完整迁移到 Java 的对比清单

生成时间：2026-07-03

对比范围：

- TypeScript 仓库：`/Volumes/Data 1/github/pi`
- Java 仓库：`/Volumes/Data 1/github/pi-java`

本文只记录 TS 版本中较成熟、对用户体验或生态能力有明显价值，但 Java 版本尚未迁移、只迁移了骨架、或交互入口未接通的能力。Java 版已经明显增强的能力（例如 server 状态面板、skill diagnostics、`/grill-me`、Provider HTTP 重试治理）不列为缺口。

## 核对方法

- 按 monorepo 模块逐项对齐：`ai`、`agent`、`coding-agent`、`tui`、`server`。
- 交叉检查 TS 文档、示例、测试与 Java 源码入口，避免只看文件名导致误判。
- 对“Java 已有同名文件/参数但未实际接入”的项目单独标为“部分迁移”。

## 总览

| 领域 | TS 版本优秀能力 | Java 当前状态 | 迁移状态 |
| --- | --- | --- | --- |
| 扩展平台 | TS/JS 运行时扩展、事件、命令、快捷键、UI、Provider、消息渲染器 | JAR SPI 已接入工具、命令、事件、基础 UI context、`ctx.mode` / `hasUI`、`sendUserMessage` text/image content blocks（含 steer/followUp 队列保真、extension source 标记、行式扩展命令即时发送 source 透传和 extension custom message LLM context source 保留与 session 显式持久化）和 QueueUpdate image metadata（含 data decoded byte length / URL source / data-first source / 行式附件摘要渲染）；缺动态 TS/JS 运行时、完整 TUI、快捷键、Provider 与消息渲染器 | 部分迁移 |
| 包生态 | npm/git/local 包、`pi` manifest、依赖安装、资源过滤、`pi config`、self-update | clone/copy 目录级包管理；已安装目录可发现 `package.json#pi` 与 conventional dirs 下的 skills/prompts/themes；可信项目 package resources 已 project-over-global，并按 npm name、git host/path、local scoped source path 的基础 identity 去重；`pi install/remove` 会维护 settings `packages` source，local source 会按 scope base 保存相对路径并按解析后路径匹配；对象形式 package filters 已接入资源加载；基础 `pi config list|enable|disable` 可 scope-aware 匹配 local package source 并修改 package resource filters，`pi config --top-level` 可写入顶层 resource filters，`pi config list --json` 可输出 package/top-level resource 结构化快照，`--resolved` 可追加 package resource discovery 后的实际资源路径、来源元数据、相对路径、enable/disable action args、被 package filter 排除的 disabled candidates、同 identity shadowed 覆盖原因/覆盖方 package 元数据，以及顶层 resource filters 的 resolved item metadata / top-level action args，ResourceLoader 会应用顶层 `+` / `-` / `!` filters；git package source 已有 shorthand/protocol URL、pinned ref checkout/reconcile、`git/` 资源发现、package root dependencies 基础安装链路和远端目标未变化 skip；npm package source 已有 `npmCommand`、install/remove 和 `node_modules` 资源发现基础链路；Package CLI 已支持 `--approve` / `--no-approve` 基础项目 settings 信任覆盖，`pi list` 已按 settings packages 输出并遵守 project trust，可显示已安装路径和 `(filtered)` 标记，并补齐 `--help` / unknown option / extra arg / duplicate `--extension` 基础校验；package update 已有 settings packages 驱动的 npm/git 更新，可信项目下会聚合 global + project package sources 并按各自 scope 更新，pinned npm exact version 跳过、npm registry semver/range 目标版本查询、pinned git ref reconcile、逐 source update start 文本输出、updated/skipped/failed 汇总输出、单包失败隔离、离线模式短路和 `pi update --self|--extensions|--all|--extension` 基础目标选择语义；配置 `selfUpdatePackage` 时可执行 settings-driven npm self-update 全局安装并清理旧包名，安装失败会输出可手动执行的 install fallback command，清理旧包失败时会明确提示目标包已安装成功并给出 uninstall fallback command，配置 `selfUpdateCurrentVersion` 时可查询 registry 并在已最新时跳过安装，`pi update --force` 可在已最新时强制重装，range/unpinned self-update spec 会收敛为 registry exact target | 部分迁移 |
| 交互 TUI | 全屏组件系统、overlay、选择器、会话树、主题、富编辑器 | 行式 REPL + 少量结构化输出 | 未完整迁移 |
| 会话 UX | `/resume`、`/tree`、`/fork`、`/clone`、分支标签、分支摘要、删除/重命名选择器 | 启动会话参数和行式 `/resume`/`/tree`/`/fork`/`/clone`/`/import` 等入口已接通，`/tree` 可展示 user/custom_message source，`/session` 和 RPC `session_info` 可统计 source，RPC `session_info` / `session_tree` 均支持外部 session/branch 只读查看，RPC `session_tree flat=true` 可输出 selector 扁平 items/actions，并支持 query 过滤、offset/limit 分页、collapsed ids 字符串/数组入参和 itemTotal/itemReturned/itemHasMore 统计，RPC `session_list` 可输出基础结构化列表/搜索、排序和分页元数据，RPC `session_user_messages` 可输出历史 user message selector 数据，并可用 session list sessionQuery/index/sessionId 定位目标会话，RPC `session_info` / `session_switch` / `session_rename` / `session_delete` 可消费 session list query/index/sessionId 定位结果，RPC `session_fork` 可消费 selector action/index/branchIndex 定位结果，RPC `session_switch` / `session_rename` / `session_delete` / `session_fork` / `session_clone` 可执行基础结构化会话变更；缺 TS 全屏 selector/tree 导航体验 | 部分迁移 |
| 分享与导出 | `/share` gist、交互 `/export`、HTML 高保真会话视图 | CLI `--export` 和交互 `/export` 可按扩展名导出 HTML/JSONL，help 文案已同步；交互 `/share` 已有基础入口；HTML export 已补 skill/custom/user/custom_message source badge/图片/XSS 基础回归 | 部分迁移 |
| OAuth/登录 | `/login` UI、OAuth provider、device code、Copilot/OpenAI Codex 订阅登录 | AuthStorage 有 OAuth 接口；行式 `/login` / `/logout` 已支持 API key、env 引用、已注册 OAuth provider 可发现/登录入口，以及 stored/runtime/environment 认证来源列表；内置 provider、device code/browser callback 和全屏 UI 未接 | 部分迁移 |
| AI Provider 高级协议 | OpenAI Codex Responses/WebSocket/cache affinity、GitHub Copilot、Azure、Vertex、Cloudflare、OpenRouter images、动态 API provider | 内置 provider 少一批；OpenRouter image generation 已有基础链路；Bedrock 是 stub 响应 | 部分迁移 |
| 图片能力 | 粘贴/缩放/转换图片、terminal graphics、图像生成 API | 有图片 content/model 骨架、MIME 判断、HTML export 图片渲染、OpenRouter 图像生成基础 API、行式 `/image` 图像生成入口、read tool 图片附件、CLI 初始 `@image` 附件、扩展 `sendUserMessage` text/image content blocks（含 steer/followUp 队列保真、extension source 标记、行式扩展命令即时发送 source 透传和 extension custom message LLM context source 保留与 session 显式持久化）、QueueUpdate image metadata（含 data decoded byte length / URL source / data-first source / 行式附件摘要渲染）、行式 `/paste-image` 剪贴板图片入口、blockImages 过滤、autoResize 基础缩放和 BMP 转 PNG，缺少完整全屏剪贴板/终端图像/处理流程 | 部分迁移 |
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

Java 版插件接口已接通基础 lifecycle/tool hook、compact 事件、`user_bash` 事件、同步 `input` transform/handled 事件、同步 `tool_call` 改参/阻断、同步 `tool_result` 结果修改、同步 `before_agent_start` 上下文/系统提示注入、`sendUserMessage` 文本消息的 steer/followUp 队列语义、运行中缺少 delivery 的 TS 式 guard、text/image content blocks（含 steer/followUp 队列保真、extension source 标记、行式扩展命令即时发送 source 透传和 extension custom message LLM context source 保留与 session 显式持久化）和 QueueUpdate image metadata（含 data decoded byte length / URL source / data-first source / 行式附件摘要渲染）、`sendMessage` custom message / nextTurn delivery、`session_before_switch` / `session_before_fork` 取消拦截、扩展上下文基础 abort signal、交互命令和 input hook 的基础 UI context（`ctx.mode`、`hasUI`、terminal columns/rows）、基础 provider 请求/响应 hook，以及 `resources_discover` skill/prompt/theme 路径发现、theme resource 加载主链路、行式 Markdown/Diff 主题应用、行式 `/theme` 选择/预览入口和行式主题 truecolor / 256-color 输出；但仍缺 TS 版动态 TS/JS 运行时，以及完整 TUI component/context、全屏主题选择 UI、底层 provider 流取消、图像输入和 streamingBehavior 等完整事件面。

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

Java 版 `PackageManager` 当前做的是把本地文件/目录复制到 `~/.pi/agent/packages` / `.pi/packages`，git package 安装到 `~/.pi/agent/git/<host>/<path>` / `.pi/git/<host>/<path>`，npm package 安装到 `~/.pi/agent/npm/node_modules` / `.pi/npm/node_modules`。`ResourceLoader` 已能从全局 `agentDir/packages/*`、`agentDir/git/**`、`agentDir/npm/node_modules` 和可信项目 `.pi/packages/*`、`.pi/git/**`、`.pi/npm/node_modules` 中读取 `package.json#pi`，或在缺少 manifest 时按 conventional dirs 发现 skills/prompts/themes；可信项目 package roots 会先于 global roots 解析，并按 configured source、`package.json` name 或安装路径的基础 identity 去重，使同一 package 的 project 资源覆盖 global 资源；configured source identity 已对 npm 使用 package name、对 git 使用小写 host/path 并忽略 ref、对 local 使用按 global/project scope base 解析后的绝对路径，避免不同 host 同名 repo 或不同路径同名 local package 被误去重；manifest 数组支持精确路径、glob、`!` 排除、`+` 强制包含和 `-` 精确排除。`pi install/remove` 已会把 source 加入或移出 global/project settings 的 `packages` 数组，并支持字符串项和对象项的基础去重/移除；local source 写入 settings 时会按 global agentDir 或 project `.pi` 保存相对路径，后续 remove/update 匹配会按 scope base 与 cwd 解析后比较。对象形式 package filters（`skills`、`prompts`、`themes`、预留 `extensions`）已按 global/project scope 接入资源加载，可禁用某类资源或按路径/glob 收窄 manifest/conventional 允许结果。基础 `pi config list|enable|disable` 已能对已配置 package source 写入 `+path` / `-path` filters，并可 scope-aware 匹配 settings 中相对保存的 local source；`pi config --top-level` 已能对 settings 顶层 `extensions` / `skills` / `prompts` / `themes` 写入 `+path` / `-path` filters，`pi config list --json` 与 `pi config list --top-level --json` 已能输出 package/top-level resource 结构化快照，`--resolved` 可在 JSON 中追加 package resource discovery 后的实际资源路径、逐项来源元数据、package 内相对路径、enable/disable action args、被 package filter 排除的 disabled candidates 和同 identity shadowed 覆盖原因/覆盖方 package 元数据，并在 top-level JSON 中输出 `resolvedTopLevelResources` / `resolvedTopLevelResourceItems`、enabled/disabled 状态、过滤原因和 top-level enable/disable action args，ResourceLoader 会应用顶层 `+` / `-` / `!` filters 影响 skills/prompts/themes 加载。git package source 已支持 `git:` shorthand、protocol URL、`@ref` pinned checkout/reconcile，以及同一 `host/path` 更新 settings source；git package root 存在 `package.json` 时会使用 settings `npmCommand` 或默认 npm 执行 dependencies 基础安装；已安装 git package 更新时会比较 unpinned upstream/remote HEAD 或 pinned `FETCH_HEAD^{commit}`，目标 commit 未变化时跳过 `pull` / `reset` / `clean` 和依赖安装。npm package source 已支持 `npm:<name>[@version]`、scoped package、`npmCommand` wrapper、install/remove 和 `node_modules` 资源发现。Package CLI 已支持 `-a` / `--approve` 与 `-na` / `--no-approve` 基础信任覆盖，可在不改变 global/local scope 的情况下控制是否读取项目 `.pi/settings.json`；`pi list` 已改为按 settings `packages` 输出 configured packages，默认只读 global，`--approve` 时合并 project，`--no-approve` 显式忽略 project，对已安装 package 会输出安装路径，带 filters 的 package 会标记 `(filtered)`；Package CLI 已支持 `-h` / `--help` 基础帮助，并对未知 option、多余 positional argument 和重复 `--extension` 返回明确错误；package update 已能从 settings `packages` 读取 npm/git source，在可信项目下默认聚合 global + project configured packages 并按各自 scope 更新，按 package identity 更新单个 configured source，跳过 pinned npm exact version，通过 `npm view ... version --json` 查询 registry 并按 semver/range 选择 npm 目标版本，已安装版本等于目标版本时跳过 reinstall，并对 pinned git ref 执行 checkout reconcile；每次 settings-driven package update 会先输出逐 source 的 `Updating package ...` 文本状态，再输出 updated/skipped/failed 汇总，git unchanged skip 也会正确计入 skipped，单个 source 失败会输出 `Failed package ...` 并继续后续 source；`PI_OFFLINE=1|true|yes` 或 `--offline` 会短路 self/package update，避免 npm registry、npm install 或 git remote 操作；`pi update` CLI 已支持默认 self-only，以及 `--extensions`、`--all`、`--extension <source>` 的基础目标选择；配置 `selfUpdatePackage` 时可使用 settings `npmCommand` 执行 npm 全局 self-update 安装，并在 `selfUpdatePackageName` 与目标包名不同时清理旧包名，安装失败会输出可手动执行的 install fallback command，清理旧包失败时会明确提示目标包已安装成功并给出 uninstall fallback command；配置 `selfUpdateCurrentVersion` 时会先查 registry 目标版本，已最新则跳过安装；`pi update --force` 会在已最新时仍执行 self-update 安装；未配置 current version 但 self-update spec 为 range/unpinned 时也会查 registry，并将 install 目标固定为 exact semver。它仍没有完整依赖安装治理、`pi config` 交互式 selector、完整 self-update 安装方式识别/权限与说明、git update 并发/ProgressEvent 事件；`extensions` 路径解析和顶层 filter 写入已预留，Java 当前扩展运行时仍只支持 JAR extension。

证据：

- TS：`packages/coding-agent/docs/packages.md`
- TS：`packages/coding-agent/src/package-manager-cli.ts`
- TS：`packages/coding-agent/src/core/package-manager.ts`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/config/SettingsManager.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/PackageResourceResolver.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/ResourceLoader.java`

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

Java 版已接通多项行式入口，包括 `/settings`、`/resume`、`/tree`、`/login`、`/theme` 和 `/prompt` 等；但这些仍是文本命令输出，还没有 TS 版全屏 selector、overlay、palette 和富交互面板体验。

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

Java 版 `SessionManager` 有 `continueRecent`、`forkFrom`、`list`、`open` 等底层能力，启动参数已接通 `--continue`、`--resume`、`--fork`、`--session-id`、`--session-dir` 等会话选择路径；交互模式也已有行式 `/resume`、`/tree`、`/fork`、`/clone`、`/import`、`/new`、`/reload`、`/name`、`/session` 等入口。`/tree` 已能显示 user/custom_message source，便于识别扩展投递消息和扩展注入上下文；`/session` 也会在当前 branch 存在 source 时输出 messages/custom_messages source 统计；RPC `session_info` 可返回结构化 session stats、branch message/token summary 和 `sources.messages` / `sources.customMessages`，并支持读取外部 session 文件与指定 branch，也可按 `sessionId` 或与 `session_list` 对齐的 `all/query/sort/index` 定位并返回 `resolvedSessionFile` / `resolvedSessionId` / `resolvedIndex` / `selector`；RPC `session_tree` 可返回 roots/children/current/label/summary/source 等基础结构化树，并支持读取外部 session 文件和指定 branch current 标记，`flat=true` 时还会输出 depth-first `items`、depth/index/child count 和 selector actions，并支持 query 过滤 items、offset/limit 分页、collapsed ids 字符串/数组入参与返回 `itemTotal` / `itemReturned` / `itemHasMore` / `collapsedIds`；RPC `session_list` 可按当前项目或 all scope 输出结构化列表，支持 query/sort/offset/limit 搜索排序和 `returned` / `hasMore` 分页元数据；RPC `session_user_messages` 可按 current/external session 与 branch 输出历史 user message、source 和 fork actions，除显式 `session` 路径外也可按 `sessionId` 或与 `session_list` 对齐的 `all/sessionQuery/sort/index` 定位目标会话，并返回同样的 resolved selector 元数据，其中 `query` 保留为 user message 过滤条件；RPC `session_switch` / `session_resume` 可切换当前 runtime session 并让后续 prompt 写入目标 session，除显式 `session` 路径外也可按 `sessionId` 或与 `session_list` 对齐的 `all/query/sort/index` 定位，并返回同样的 resolved selector 元数据；RPC `session_rename` 可对当前或外部 session 追加 `session_info` entry 完成结构化改名/清名，除显式 `session` 路径外也可按 `sessionId` 或与 `session_list` 对齐的 `all/query/sort/index` 定位，并返回同样的 resolved selector 元数据；RPC `session_delete` 可按 `/resume delete` 安全边界删除非当前外部 session，除显式 `session` 路径外也可按 `sessionId` 或与 `session_list` 对齐的 `all/query/sort/index` 定位，并返回同样的 resolved selector 元数据；RPC `session_fork` 可消费 `forkBeforeEntryId` / `forkAtEntryId` action 字段，也可按 `session_tree flat=true` 的 `index` 或 branch path `branchIndex` 定位 entry，并返回 `resolvedEntryId` / `selector`；RPC `session_fork` / `session_clone` 可对当前 runtime session 执行基础结构化分支变更。但这些仍是行式文本或基础 JSON 接口，还没有 TS 版全屏 session selector、tree selector、排序、富导航和完整分支操作体验。

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

Java 版已有 `HtmlExporter`、CLI `--export`，以及交互 `/share`、`/copy`、`/export` handler。CLI `--export` 和交互 `/export` 均可按输出路径扩展名导出 HTML 或复制原始 JSONL，CLI help 和交互 `/help` 也已标明 HTML/JSONL 双格式并有回归覆盖。HTML export 已补 skill wrapper 拆分、custom/custom_message 结构块、user/custom_message source badge、图片 content 安全渲染和基础 XSS 回归；但整体仍是基础会话视图，还没有 TS 版完整高保真 viewer、侧边栏树、主题映射、markdown 渲染和完整 tool renderer。

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

Java 版 `AuthStorage` 已有 OAuth credential、refresh 和 `registerOAuthProvider` 接口，行式 `/login` / `/logout` handler 已接通 API key、`env <ENV_VAR>` 引用和已注册 OAuth provider 的可发现/登录入口，`/help` 与 provider 列表会提示这些路径；`/logout [provider]` 空参数也能列出 stored、runtime `--api-key` 和 environment-only 认证来源，包含 Bedrock/Vertex 这类环境认证标签。但 Java 仍没有内置 OAuth provider 注册点、device code/browser callback、全屏 OAuth selector，也没有 Copilot/OpenAI Codex/Anthropic OAuth 等订阅登录流。`OAuthTokenManager` 存在于 `ai` 包，但只是 token 文件读写/缓存工具，不等价于 TS 的完整登录流。

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

Java 版内置 provider 包括 OpenAI、Anthropic、Gemini、Groq、Mistral、Ollama、XAI、DeepSeek、Together、OpenRouter、Cerebras、Fireworks、Moonshot、Zai、Bedrock 等，且 `pi-ai` 已补 OpenRouter image generation 的基础 provider / registry / HTTP 链路；但仍缺少上述一批高级 provider 和动态 API provider。尤其要注意：Java 的 `BedrockProvider` 仍没有实际调用 Bedrock Converse API；当前已补基础 Converse request payload 构建、`requestMetadata` 透传、Claude thinking `additionalModelRequestFields`、Claude thinking 缺 signature 回放降级、system prompt cache point、最后一条 user message cache point、连续 toolResult 合并、ARN / 显式 region / env / 标准 endpoint 的区域解析，以及 AWS profile、access/secret pair、bearer token、ECS/IRSA 和 skip-auth 的认证来源检测，并在检测到 AWS 凭证后返回明确未实现错误，不再返回 `"Hello from AWS Bedrock ..."` 模拟内容。

证据：

- TS：`packages/ai/src/providers/all.ts`
- TS：`packages/ai/src/api/openai-codex-responses.ts`
- TS：`packages/ai/src/api/bedrock-converse-stream.ts`
- TS：`packages/ai/src/compat.ts`
- Java：`packages/ai/src/main/java/works/earendil/pi/ai/provider/ProviderRegistry.java`
- Java：`packages/ai/src/main/java/works/earendil/pi/ai/provider/ImageGenerationRegistry.java`
- Java：`packages/ai/src/main/java/works/earendil/pi/ai/provider/OpenRouterImagesProvider.java`
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

Java 版已有 `Content.Image`、`ImageGenModel`、`MimeUtils`、部分 settings 字段和若干支持 image input 的模型定义；HTML export 已能安全渲染已持久化的图片 content；`pi-ai` 已补 `ImageGenerationProvider`、`ImageGenerationRegistry` 和 OpenRouter image generation 基础 HTTP provider；coding-agent 行式交互已补 `/image list|generate`，可列出图像模型、调用 provider、落盘 base64 图片并列出远端 URL；read tool 已能读取受支持图片并返回 `Content.Image`，CLI 启动参数已能把 `@image` 处理为首轮 prompt 的图片附件，扩展 `sendUserMessage` 已支持 text/image content blocks，且运行中 steer/followUp 队列会保留图片块并在 QueueUpdate 输出轻量 image metadata（data 图片使用 decoded byte length，URL 图片保留 source/url，data+URL 图片按 data source 归一），并为扩展投递的 queued user message 标记 `source=extension`；扩展 custom message 转为 LLM context 时也会保留 `source=extension`，新写入 session JSONL 的 `custom_message` 也会显式持久化 source；行式交互也会把 queued user message 的图片附件摘要渲染到 `Queued user messages` 面板；行式交互已补 `/paste-image` 将剪贴板图片保存为文件并输出可提交的 `@path`，`images.blockImages` 已接入 LLM 上下文过滤，`images.autoResize` 已接到 read tool 和 CLI 初始图片附件的 PNG/JPEG/BMP 基础缩放链路，BMP 会转为 PNG 并输出转换/尺寸提示。但还没有 TS 全屏 image selector、生成结果预览、编辑器级别的剪贴板图片快捷键、附件管理、多后端剪贴板读取或 TS Photon/WASM 级别的完整图片处理流程。终端图片能力也未达到 TS 版 Kitty/Sixel/iTerm2 多协议体验。

证据：

- TS：`packages/ai/src/images.ts`
- TS：`packages/ai/src/images-api-registry.ts`
- TS：`packages/ai/src/providers/openrouter-images.ts`
- TS：`packages/coding-agent/test/clipboard-image.test.ts`
- TS：`packages/coding-agent/test/image-processing.test.ts`
- Java：`packages/ai/src/main/java/works/earendil/pi/ai/model/Content.java`
- Java：`packages/ai/src/main/java/works/earendil/pi/ai/model/ImageGenModel.java`
- Java：`packages/ai/src/main/java/works/earendil/pi/ai/provider/ImageGenerationRegistry.java`
- Java：`packages/ai/src/main/java/works/earendil/pi/ai/provider/OpenRouterImagesProvider.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/tools/ReadTool.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/CodingAgentMessages.java`
- Java：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/util/MimeUtils.java`

## 9. Prompt 模板、主题、文档资源

TS 版资源系统同时支持：

- prompt templates。
- themes。
- project context files。
- package 资源发现。
- `/reload` 热重载 keybindings、extensions、skills、prompts、themes。
- 主题选择器、自动主题、主题导出测试。

Java 版已有 `ResourceLoader`、`PromptTemplateLoader`、`SkillLoader`、`ProjectContextLoader` 和 settings 字段，且扩展 `resources_discover` 已能追加 skill/prompt/theme 路径；theme JSON 已能从用户、项目、settings 与扩展路径加载、去重并暴露诊断，settings 选中的主题已能应用到行式 Markdown/Diff 输出，保留行式 truecolor / 256-color 精度，并可通过行式 `/theme` 列表、预览和切换；prompt templates 已能通过行式 `/prompt` 列表、预览、执行，并支持直接模板 slash 展开。但交互层还没有 TS 版全屏主题/模板选择器、自动主题、主题热重载 watcher 和全屏 TUI 主题体验。

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

Java 测试已覆盖工具、设置、部分 runtime、skill diagnostics、server 等，但缺少上述交互生态和 provider 协议级回归。

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

- 可运行的扩展平台：命令、工具、事件、UI、动态 provider、resources discover。（Java 已接通 JAR SPI 的工具、命令、基础 lifecycle/tool/compact、`user_bash`、同步 `input`、`tool_call` 改参/阻断、`tool_result` 结果修改、`before_agent_start` 上下文/系统提示注入事件、`sendUserMessage` 文本消息 steer/followUp 队列、running-state delivery guard、text/image content blocks（含 steer/followUp 队列保真、extension source 标记、行式扩展命令即时发送 source 透传和 extension custom message LLM context source 保留与 session 显式持久化）和 QueueUpdate image metadata（含 data decoded byte length / URL source / data-first source / 行式附件摘要渲染）、`sendMessage` custom message / nextTurn delivery、`session_before_switch` / `session_before_fork` 取消拦截、扩展上下文基础 abort signal、交互命令/input hook 基础 UI context 和 `ctx.mode` / `hasUI` 语义、基础 provider 请求/响应 hook，以及 `resources_discover` skill/prompt/theme 路径发现、theme resource 加载、行式主题应用、行式 `/theme` 入口、行式主题 truecolor / 256-color 输出和行式 `/prompt` 模板入口；动态 TS/JS 运行时和完整 TUI/provider/resources 仍待补。）
- 完整 Pi package：npm/git/local source 解析、依赖安装、`pi config` 交互式启停。（Java 已补已安装目录的 `package.json#pi` / conventional resource discovery 基础链路、resource resolution 中的 project-over-global 和 package identity dedupe、git host/path 归一化、local scoped source path identity、local package settings path 归一化、`pi install/remove` 对 settings `packages` source 的基础持久化、settings object package filters 与资源加载联动、基础 `pi config list|enable|disable` package filters 命令及 local source scope-aware 匹配、`pi config --top-level` 顶层 resource filters 写入和 skills/prompts/themes 加载过滤、`pi config list --json` / `--top-level --json` 结构化快照、`--resolved` 实际 package resource 路径、来源元数据、相对路径、toggle action args、disabled candidates、shadowed 覆盖原因/覆盖方 package 元数据快照和 top-level resolved resource item metadata、git package source/pinned ref 基础链路、git package dependencies 基础安装、git remote HEAD unchanged skip、npm source/npmCommand/install/remove 基础链路、Package CLI `--approve` / `--no-approve` 基础信任覆盖、`pi list` settings-aware 输出与 project trust 语义、Package CLI `--help` 和非法参数基础校验、settings 驱动的 package update 基础链路、可信项目 global + project package scopes 聚合更新、npm registry semver/range 目标版本查询、逐 source update start 文本输出、updated/skipped/failed 汇总输出和单包失败隔离，`pi update --self|--extensions|--all|--extension` 基础目标选择语义，settings-driven npm self-update 基础安装链路、self-update current version registry skip、self-update range exact target install、self-update failure fallback command，以及 package update 离线模式短路；完整 selector、完整 self-update 安装方式识别/权限与说明、依赖治理细节和 git update 并发/ProgressEvent 事件语义仍待补。）
- 全屏 TUI：富输入编辑器、selector、overlay、会话树。
- 内置 OAuth provider、device code/browser callback 与全屏登录 UI。

### P2：补高级协议与体验细节

- OpenAI Codex WebSocket/cache affinity、GitHub Copilot、Azure、Vertex、Cloudflare 等 provider。
- 全屏剪贴板图片 UX、完整图片处理、terminal graphics 和图像生成结果预览。
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
