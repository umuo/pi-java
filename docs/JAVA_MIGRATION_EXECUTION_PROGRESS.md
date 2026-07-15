# Pi Java 迁移优化执行进度

更新时间：2026-07-15

依据文档：`docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

## 当前总进度

| 优先级 | 当前状态 | 说明 |
| --- | --- | --- |
| P0：声明但未接通的用户入口 | 进行中，已完成 151 项 | 已完成启动会话参数接通、交互 `/settings`、交互 `/login`、交互 `/login` OAuth/env help、交互 `/logout`、交互 `/logout` stored/runtime/env 来源列表、交互 `/logout [provider]` 可选参数 usage、交互 `/export`、交互 `/export` HTML/JSONL help 回归、CLI `--export` JSONL、CLI `--export` HTML/JSONL help、交互 `/share`、交互 `/copy`、交互 `/import`、交互 `/name`、交互 `/session`、交互 `/new`、交互 `/compact`、`/compact` 公共执行路径和扩展事件、行式 `/tree`、行式 `/fork`、行式 `/clone`、行式 `/resume`、`/resume` 重命名/删除、`/resume` 全局搜索/过滤、交互 `/reload`、交互 `!` / `!!` bash 命令、bash `shellCommandPrefix` / `shellPath` 设置接入、扩展工具基础加载、扩展工具执行器 API、扩展基础事件 hook、扩展 slash command 注册/执行、扩展命令 session facade、扩展 custom entry/label facade、扩展 `sendUserMessage` 同步版、扩展 `sendUserMessage` steer/followUp 队列语义、扩展 `sendUserMessage` running-state delivery guard、扩展 `sendUserMessage` text/image content blocks、扩展 `sendUserMessage` 队列 content block 保真、扩展 `sendUserMessage` source 标记、行式扩展命令 UserMessageSender source 透传、扩展 custom message LLM context source 保留与 session 显式持久化、扩展队列事件 image metadata、扩展队列图片 decoded byte length、扩展队列 URL 图片 metadata、扩展队列图片 source 归一化、行式 QueueUpdate 附件摘要渲染、扩展 `sendMessage` custom message 和 nextTurn delivery、扩展结构化命令参数、扩展 `user_bash` 事件、扩展 `input` 事件、扩展 `tool_call` 改参/阻断、扩展 `tool_result` 结果修改、扩展 `before_agent_start` 上下文/系统提示注入、扩展 `session_before_switch` / `session_before_fork` 取消拦截、扩展上下文 abort signal、扩展命令基础 UI context、扩展上下文 `ctx.mode` / `hasUI` 基础语义、基础 provider 请求/响应 hook、Bedrock provider 去除模拟成功响应、Bedrock Converse request payload 基础构建、Bedrock requestMetadata/region 基础解析、Bedrock AWS 认证来源基础检测、Bedrock Claude thinking request fields、Bedrock Claude thinking signature fallback、Bedrock system cachePoint 基础构建、Bedrock last user message cachePoint 基础构建、Bedrock 连续 toolResult 合并、扩展 `resources_discover` skill/prompt/theme 路径发现、theme resource 主链路、行式主题应用、行式 `/theme` 入口、行式主题 truecolor / 256-color 精度、行式 `/prompt` 模板入口、HTML export skill/custom/XSS 回归、custom_message source badge、HTML export user message source badge、行式 session tree custom_message source、行式 session tree user message source、行式 `/session` source 统计、RPC `session_info` source 统计、RPC `session_info` external session/branch、RPC `session_info` selector index/sessionId 定位、RPC `session_rename` external session、RPC `session_rename` selector index/sessionId 定位、RPC `session_delete` external session、RPC `session_delete` selector index/sessionId 定位、RPC `session_switch` external session、RPC `session_switch` selector index/sessionId 定位、RPC `session_fork` / `session_clone`、RPC `session_tree` 基础结构化输出、RPC `session_tree` external session/branch、RPC `session_tree` flat selector 数据、RPC `session_tree` flat query/filter、RPC `session_tree` flat pagination metadata、RPC `session_tree` flat collapse metadata、RPC `session_tree` collapsedIds array input、RPC `session_list` 基础结构化列表/搜索、RPC `session_list` 分页元数据、RPC `session_list` sort metadata、RPC `session_user_messages` selector 数据、RPC `session_user_messages` selector index/sessionId 定位、RPC `session_fork` selector index 定位、HTML export 图片内容安全渲染、OpenRouter 图像生成 API 基础链路、read tool 图片附件 / blockImages 过滤、read tool 图片 autoResize / BMP 转 PNG 基础处理、CLI 初始 `@file` / `@image` 图片附件、行式 `/paste-image` 剪贴板图片入口、行式 `/image` 图像生成入口、package resource discovery 基础链路、`pi install/remove` settings packages 持久化、settings package filters 加载联动、基础 `pi config list|enable|disable`、git package source/pinned ref 基础链路、npm package source/npmCommand 基础链路、settings 驱动的 package update 基础链路、package update trusted project scopes、package CLI approve trust flag、package CLI list settings/trust 输出、package CLI list installed path / filtered marker、package CLI help / invalid args 基础语义、package CLI duplicate `--extension` 冲突校验、git package dependencies 基础安装链路、`pi update --self|--extensions|--all|--extension` 基础目标选择语义、`pi update --force` self-update 重装语义、package resource identity dedupe / project 优先基础链路、package identity git/local 归一化增量、local package settings path 归一化基础链路、`pi config` local source 匹配、top-level resource filters、npm registry semver/range update 查询、settings-driven npm self-update 基础安装链路、self-update current version registry skip、self-update range exact target install、self-update fallback command、self-update cleanup failure 安装状态提示、package update summary output、package update failed summary、package update progress output、git remote HEAD unchanged skip 基础链路、package update offline mode 短路、`pi config` JSON/resolved 快照、resolved resource 来源元数据、selector toggle action metadata、disabled candidates、shadowed resource 覆盖原因/覆盖方元数据和 top-level resolved resource item metadata；其他交互命令和完整扩展平台仍待补。 |
| P1：TS 生态优势核心闭环 | 进行中 | Java JAR 扩展 SPI 已接入基础加载、事件 hook、工具执行器、compact 事件、`user_bash` / `input` 事件、`tool_call` 改参/阻断、`tool_result` 结果修改、`before_agent_start` 上下文/系统提示注入、`session_before_switch` / `session_before_fork` 取消拦截、扩展上下文 abort signal、provider 请求/响应 hook、`resources_discover` skill/prompt/theme 路径发现、theme resource 加载、行式主题应用、行式 `/theme` 选择/预览、行式主题 truecolor / 256-color 输出、行式 `/prompt` list/preview/run 和直接模板 slash 展开、行式 slash command、命令上下文、基础 UI context、`ctx.mode` / `hasUI` 基础语义、session metadata、custom entry、label facade、同步 user message 触发、`sendUserMessage` steer/followUp 队列语义、运行中缺少 delivery 的 TS 式 guard、text/image content blocks（含 steer/followUp 队列保真）、extension source 标记、行式扩展命令即时发送 source 透传和 extension custom message LLM context source 保留与 session 显式持久化，以及 QueueUpdate image metadata（含 decoded byte length / URL source / data-first source / 行式附件摘要渲染）、`sendMessage` custom message / nextTurn delivery 和结构化命令参数；package resource discovery 已能从全局/可信项目安装目录加载 `package.json#pi` 与 conventional dirs 下的 skills/prompts/themes，`pi install/remove` 会同步维护 global/project settings `packages` 数组，本地 package source 会按 global agentDir 或 project `.pi` 写入相对路径并按解析后的绝对路径匹配，对象形式 package filters 已能按 global/project scope 影响资源加载，`pi config list|enable|disable` 已能 scope-aware 匹配 local package source 并修改 package resource filters，`pi config --top-level` 已能写入顶层 resource filters 且 ResourceLoader 会应用 `+` / `-` / `!` 顶层过滤，`pi config list --json` 已能输出 package/top-level resource 结构化快照，`--resolved` 已能追加 package resource discovery 后的实际资源路径、逐项来源元数据、相对路径、enable/disable action args、被 package filter 排除的 disabled candidates、同 identity shadowed 资源覆盖原因及覆盖方 package 元数据，并为顶层 resource filters 输出 `resolvedTopLevelResources` / `resolvedTopLevelResourceItems`、enabled/disabled 状态、过滤原因和 top-level enable/disable action args，可信项目 package roots 会优先于 global roots，并按 package identity 去重避免同一包重复暴露资源，git package identity 已按 host/path 归一化、local package resource identity 已按 scope base 解析 source path，git package source 已支持 `git:` shorthand、protocol URL、pinned ref checkout/reconcile、`git/` 安装根资源发现、package root `package.json` dependencies 基础安装，以及 unpinned remote HEAD / pinned ref commit unchanged skip，npm package source 已支持 `npm:<name>[@version]`、`npmCommand` wrapper、global/project npm 安装根和 `node_modules` 资源发现，package update 已能按 settings packages 更新 npm/git package，Package CLI 已支持 `--approve` / `--no-approve` 基础项目 settings 信任覆盖，`pi list` 已改为 settings-aware 输出并遵守 `--approve` / `--no-approve` project trust，且能为已安装 configured package 输出安装路径和 `(filtered)` 标记，Package CLI 已支持 `--help` 基础帮助、未知 option / 多余参数报错和重复 `--extension` 冲突报错，在可信项目下默认聚合 global + project package sources 并按各自 scope 更新，跳过 pinned npm exact version、通过 `npm view ... version --json` 查询 registry 并按 semver/range 选择目标版本、已最新时跳过 npm reinstall、reconcile pinned git ref、对每个 source 输出 update start 状态、对 updated/skipped/failed 输出汇总并在单个 package 失败后继续后续 source，并在 `PI_OFFLINE` / `--offline` 下短路 self/package update，package CLI 已支持 `pi update` 默认 self-only 以及 `--extensions` / `--all` / `--extension <source>` 基础目标选择，配置了 `selfUpdatePackage` 时可执行 npm 全局 self-update 安装并清理旧包名，self-update 安装失败会输出可手动执行的 fallback command，清理旧包失败时会明确提示目标包已安装成功并给出 uninstall fallback command，配置 `selfUpdateCurrentVersion` 时会先查 registry 目标版本并在已最新时跳过安装，未配置 current version 但 `selfUpdatePackage` 为 range/unpinned 时也会查 registry 并把 install 目标固定到 exact version，`pi update --force` 会在 registry 目标版本等于当前版本时仍执行 self-update 安装；shell prefix/path 设置已接入交互 bash 和 bash tool；完整 `pi config` TUI selector、全屏 TUI 组件上下文、OAuth 登录、完整 self-update 安装方式识别/权限与说明、依赖治理细节和 update 并发/进度语义仍待规划实施。 |
| P2：高级协议与体验细节 | 进行中 | HTML export 已补 skill wrapper、custom entry、user/custom_message source badge、图片内容渲染和 XSS 回归；图像生成已补 OpenRouter 基础 API / provider / registry 和行式 `/image list|generate` 入口；read tool 已能返回图片附件，`images.blockImages` 会过滤 LLM 上下文图片，`images.autoResize` 会在 read tool 和 CLI 初始 `@image` 附件中对可解码 PNG/JPEG/BMP 执行基础缩放并将 BMP 转 PNG；行式 `/paste-image` 可把剪贴板图片保存为临时/指定图片文件并输出 `@path`；QueueUpdate 已能在行式交互中展示 queued user message 的图片附件摘要；Provider 高级协议、全屏剪贴板图片 UX、完整图片处理/terminal graphics、分享导出高保真 viewer、SDK 文档等仍待补。 |

## 执行记录

### 优化 001：接通启动会话参数

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：`--continue`、`--resume`、`--fork`、`--session-id`、`--no-session`、`--session-dir` 的启动路径。

完成内容：

- `CliArgs` 新增 `--session-dir` 参数。
- `Main` 启动流程不再固定调用 `SessionManager.create(cwd, cwd.resolve(".pi/sessions"))`。
- 新增启动会话解析逻辑：
  - `--no-session` 创建内存 session，并支持 `--session-id`。
  - `--fork <path|id>` 从指定 session 文件或本项目 session id 前缀分叉。
  - `--session <path|id>` 打开指定 session 文件或本项目 session id 前缀。
  - `--continue` 继续当前项目最近 session，没有则创建新 session。
  - `--resume` 当前先复用最近 session 逻辑；还不是 TS 版交互选择器。
  - `--session-id <id>` 优先打开同 id 已有 session，没有则创建指定 id 的新 session。
  - `--session-dir <dir>`、`PI_CODING_AGENT_SESSION_DIR`、settings `sessionDir` 参与 session 目录解析。
- `--export` 未显式传 `--session` 时改为导出当前项目最近 session，不再硬编码 `.pi/sessions/latest.jsonl`。
- 增加单测覆盖 path/id 打开、partial id、continue/resume、fork、session-id 复用/创建、no-session、session-dir 解析和缺失 session 报错。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/CliArgs.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/Main.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 共 11 个测试，0 failures，0 errors。

当前限制：

- `--resume` 仍只是非交互地继续最近 session，还没有 TS 版 session picker。
- `--session <id>` 和 `--fork <id>` 当前只查当前项目 session 列表，不查所有项目 session。
- session 目录默认仍保持 Java 当前行为 `.pi/sessions`，未切换到 TS 文档中的全局项目编码目录。

### 优化 002：接通交互 `/export`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：命令 palette/交互 slash commands 中 `/export` 声明存在，但 Java 行式交互未执行。

完成内容：

- `InteractiveModeRunner` 新增 `/export [path]` 执行分支。
- 支持不传路径时导出到当前工作目录下 `pi-session-<sessionId>.html`。
- 支持显式 `.html` / `.htm` 路径导出 HTML。
- 支持显式 `.jsonl` 路径复制原始 session 文件。
- 对无扩展名路径自动补 `.html`。
- 对目录路径自动使用默认导出文件名。
- 对内存 session 返回明确错误，不尝试写不存在的 session 文件。
- `/help` 新增 `/export [path]` 文案。
- `HtmlExporter` 兼容 Java session 中无显式 `type` 的文本 content、`toolCall` content 和 ISO timestamp，避免导出正文为空或时间显示为 1970。
- 交互模式单测覆盖 `/export` 命令输出、HTML 文件生成和 session 内容写入。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/export/HtmlExporter.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 共 11 个测试，0 failures，0 errors。

当前限制：

- `/export` 当前只复用现有 HTML exporter，还未补 TS 版更高保真分享/发布 viewer。

### 优化 003：接通交互 `/import`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：命令 palette/交互 slash commands 中 `/import` 声明存在，但 Java 行式交互未执行。

完成内容：

- `InteractiveModeRunner` 新增 `/import <path>` 执行分支。
- 使用当前工作目录解析相对路径，并支持 CLI 路径规范化逻辑。
- 复用 `AgentSessionRuntime.importFromJsonl` 将外部 JSONL session 复制进当前 session 目录并切换 runtime。
- import 成功后重新绑定交互循环中的当前 `session`、`/grill-me` 状态和 skill diagnostics 状态，保证后续 prompt 写入导入后的会话。
- 对缺失路径和内存 session 给出明确错误。
- `/help` 新增 `/import <path>` 文案。
- 交互模式单测覆盖 `/import` 命令输出、runtime session 切换为导入 session，以及导入后继续 prompt 写入新 session。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 共 11 个测试，0 failures，0 errors。

当前限制：

- `/import` 当前是行式版本，不提供 TS 版全屏 picker 或导入确认界面。
- `/import` 在当前项目 cwd 下恢复导入 session；还未实现缺失原 cwd 时的交互选择。

### 优化 004：接通行式 `/tree`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：交互 `/tree` 在 Java 中只声明未接通；TS 版可用于查看和导航 session 分支。

完成内容：

- `InteractiveModeRunner` 新增 `/tree` 执行分支。
- 输出当前 session id、当前 leaf id、entry 总数。
- 使用 ASCII 树渲染 `SessionManager.tree()`，保留完整 entry id，便于后续 `/fork` 等命令复用。
- message 节点显示 role 和文本预览；其他 entry 类型显示对应摘要。
- 当前 leaf 节点用 `*` 标记。
- `/help` 新增 `/tree` 文案。
- 交互模式单测覆盖 `/tree` 帮助文案、树输出、user message 预览和 assistant message 预览。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 共 11 个测试，0 failures，0 errors。

当前限制：

- `/tree` 当前是只读行式树视图，还没有 TS 版全屏树选择器。
- `/tree` 当前不执行分支切换；后续可接 `/fork`、`/clone` 或 `/tree <entryId>` 导航。

### 优化 005：接通行式 `/fork`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：交互 `/fork` 在 Java 中只声明未接通；TS 版可从历史 user message 分叉新会话。

完成内容：

- `InteractiveModeRunner` 新增 `/fork [before|at] <entryId>` 执行分支。
- 默认 `/fork <entryId>` 等价于 `/fork before <entryId>`，用于从历史 user message 之前分叉，并回显 selected prompt 文本。
- 支持 `/fork at <entryId>` 保留到指定 entry 分叉。
- 复用 `AgentSessionRuntime.fork` 创建/切换分叉 session。
- fork 成功后重新绑定交互循环中的当前 `session`、`/grill-me` 状态和 skill diagnostics 状态，保证后续 prompt 写入分叉后的会话。
- 对缺失 entry id、非法 entry id 和非法分叉位置返回明确错误与 usage。
- `/help` 新增 `/fork [before|at] <entryId>` 文案。
- 交互模式单测覆盖 `/fork` 命令输出、selected prompt 回显、runtime session 切换，以及新 session 记录原 session 为 parent。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 共 12 个测试，0 failures，0 errors。

当前限制：

- `/fork` 当前是行式版本，不提供 TS 版历史消息选择器。
- `/fork before <entryId>` 无法预填输入框，只能在结果里回显 selected prompt。
- `/fork at <entryId>` 需要用户先通过 `/tree` 获取 entry id。

### 优化 006：接通行式 `/clone`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：交互 `/clone` 在 Java 中只声明未接通；TS 版可克隆当前 active branch。

完成内容：

- `InteractiveModeRunner` 新增 `/clone` 执行分支。
- 对有当前 leaf 的 session，复用 `AgentSessionRuntime.fork(leafId, AT)` 克隆当前 active branch 到新 session。
- 对空 session，降级为 `AgentSessionRuntime.newSession(currentSessionFile)`，创建带 parent metadata 的新 session。
- clone 成功后重新绑定交互循环中的当前 `session`、`/grill-me` 状态和 skill diagnostics 状态，保证后续 prompt 写入 clone 后的新会话。
- `/help` 新增 `/clone` 文案。
- 交互模式单测覆盖 `/clone` 命令输出、runtime session 切换、克隆后保留 user/assistant 消息统计，以及新 session 记录原 session 为 parent。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 共 13 个测试，0 failures，0 errors。

当前限制：

- `/clone` 当前是行式版本，不提供 TS 版全屏确认/导航体验。
- `/clone` 克隆 active branch，不包含非当前分支的旁支节点。
- 空 session clone 只创建带 parent metadata 的新 session，不复制额外内容。

### 优化 007：接通行式 `/resume`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：交互 `/resume` 在 Java 中只声明未接通；TS 版支持交互搜索、排序、过滤、重命名和删除。

完成内容：

- `InteractiveModeRunner` 新增 `/resume [index|id|path]` 执行分支。
- 不传参数时列出当前项目 session 候选，包含序号、当前 session 标记、session id、名称、消息数、修改时间和首条用户消息预览。
- 支持 `/resume <index>` 按列表序号恢复 session。
- 支持 `/resume <id-prefix>` 按 session id 或唯一前缀恢复 session，并对歧义前缀报错。
- 支持 `/resume <path>` 按 JSONL 文件路径恢复 session。
- 复用 `AgentSessionRuntime.switchSession` 切换 runtime。
- resume 成功后重新绑定交互循环中的当前 `session`、`/grill-me` 状态和 skill diagnostics 状态，保证后续 prompt 写入恢复后的会话。
- 对内存 session、无匹配、序号越界等情况返回明确错误与 usage。
- `/help` 新增 `/resume [index|id|path]` 文案。
- 交互模式单测覆盖 `/resume` 列表输出、按 session id 恢复、runtime session 切换，以及恢复后 session 文件一致性。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 12 个测试、`CliEntryTest` 14 个测试，共 26 个测试，0 failures，0 errors。

当前限制：

- `/resume` 当前是行式列表，不提供 TS 版全屏搜索和选择器。
- `/resume` 当前只列当前项目 cwd 下的 session，不查全局所有项目 session。
- 后续优化 021 已补行式重命名和删除；但仍没有 TS 版 picker 内快捷键式 rename/delete 体验。

### 优化 008：接通交互 `/copy`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：交互 `/copy` 在 Java 中只声明未接通；TS 版可复制最近一次 assistant 输出。

完成内容：

- `InteractiveModeRunner` 新增 `/copy` 执行分支。
- 从当前 `AgentSession.messages()` 反向查找最近一次 assistant message。
- 复用 `InteractiveOutputRenderer.textFromContent` 提取 assistant 文本，保持与终端渲染一致的 Markdown 文本内容。
- 生产环境通过系统剪贴板写入最近一次 assistant 输出。
- 在无 assistant 消息或 headless 环境剪贴板不可用时返回明确错误，不影响交互会话继续运行。
- `/help` 新增 `/copy` 文案。
- 交互模式单测通过注入内存剪贴板覆盖 `/copy` 命令输出和实际复制内容。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 共 14 个测试，0 failures，0 errors。

当前限制：

- `/copy` 当前复制最近一次 assistant 文本，不提供 TS 版 TUI 中对代码块、选区或指定历史消息的选择复制。
- 在 headless 或无图形剪贴板环境中，`/copy` 会返回剪贴板不可用错误。

### 优化 009：接通扩展工具基础加载

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0/P1 项：`--extension` / `--no-extensions` 在 Java 中已有参数和 SPI 骨架，但未接入主运行链路；扩展工具不会进入 agent tool list。

完成内容：

- `Main` 启动流程新增扩展加载：
  - 从 settings `extensions` 读取扩展路径。
  - 从 CLI `--extension` / `-e` 读取显式扩展路径。
  - `--no-extensions` 禁用默认 classpath、用户目录和项目目录发现；仍允许显式 `--extension` 路径加载。
- `ExtensionLoader` 支持显式加载单个 `.jar` 文件，不再只支持目录扫描。
- `ExtensionLoader` 对相对扩展路径使用当前 cwd 解析。
- `ExtensionRunner` 新增 `collectAgentTools()`，将扩展 `registerTools()` 返回的 `Tool` 定义包装成 `AgentTool`，并传入 `AgentSessionServices.createAgentSessionFromServices` 的 `customTools`。
- 扩展工具现在会进入 `AgentSession.tools()`、provider context tools 和 system prompt 的 available tools / prompt guidelines。
- 后续优化 020 已补 `ExtensionPlugin.executeTool(...)` 执行器 API；旧扩展未实现执行器时仍会返回明确兼容错误。
- 单测覆盖 CLI `--extension` / `--no-extensions` 解析、扩展工具进入 session/system prompt、可执行扩展工具，以及旧扩展执行器缺失时的明确降级错误。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/Main.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionLoader.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 11 个测试、`CliEntryTest` 14 个测试，共 25 个测试，0 failures，0 errors。

当前限制：

- 这只是 Java JAR SPI 的基础接入，不是 TS 版动态 TS/JS 扩展运行时。
- 扩展工具已支持 Java SPI 执行器 API；但仍不是 TS 版动态 TS/JS 扩展运行时。
- 尚未实现扩展命令、快捷键、消息渲染器、自定义 provider、CLI flag 动态扩展等 TS 版能力。

### 优化 010：接入扩展基础事件 hook

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0/P1 项：Java `ExtensionPlugin` 虽声明 `onBeforeTurn`、`onAfterTurn`、`onBeforeToolCall`、`onAfterToolCall`，但此前没有接入 agent lifecycle。

完成内容：

- `AgentSessionServices.CreateSessionOptions` 新增可选 `ExtensionRunner`，并保留旧构造参数形式，避免现有调用点大面积改动。
- `AgentSession.Config` 新增可选 `ExtensionRunner`。
- `Main` 启动时只加载一次扩展 runner，并同时用于收集扩展工具和传入 session lifecycle。
- `AgentSession.prompt` 在 skill command 展开/trigger 处理后、user message 持久化前调用 `emitBeforeTurn(prompt)`。
- `AgentSession.prompt` 在 agent loop 成功结束并持久化新消息后调用 `emitAfterTurn(response)`，response 取本轮最后一条 assistant 文本。
- `AgentSession` 在传给 `AgentLoop` 前包装 active tools：
  - tool 执行前调用 `emitBeforeToolCall(toolName, inputJson)`。
  - tool 正常返回后调用 `emitAfterToolCall(toolName, outputText)`。
  - tool 抛错时调用 `emitAfterToolCall(toolName, errorMessage)` 后继续抛给原有错误处理路径。
- 保留 TrustManager guard 行为；扩展 hook 包装发生在 trust guard 之后，因此扩展能观察到被 trust guard 阻断后的工具结果。
- 单测覆盖包含 tool call 的完整 prompt：扩展收到 before/after turn、before/after tool call，并能看到工具输入和 read 工具输出。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/Main.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSessionServices.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 12 个测试、`CliEntryTest` 14 个测试，共 26 个测试，0 failures，0 errors。

当前限制：

- 只接入 Java 当时已有四个基础 hook；后续优化已继续补 compact、input transform、session switch/fork 取消拦截、基础 provider 请求/响应 hook、`resources_discover` skill/prompt/theme 路径发现、theme resource 加载、行式主题应用和行式 `/theme` 入口等能力，但完整 UI context / 全屏主题选择 UI 等事件面仍待补。
- `onBeforeTurn` 当前只能观察 prompt，不能修改 prompt 或阻断执行。
- `onBeforeToolCall` 当前只能观察工具输入，不能修改参数或阻断工具调用。
- `onAfterTurn` 只在 agent loop 成功结束后触发；异常结束路径还未建模为独立扩展事件。

### 优化 011：接通交互 `/reload`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：Java `SlashCommands` 声明 `/reload`，但此前行式交互未处理，会落入普通 prompt；TS 版可重载 keybindings、extensions、skills、prompts、themes 等资源。

完成内容：

- `InteractiveModeRunner` 新增 `/reload` 执行分支。
- `/help` 新增 `/reload` 文案。
- `AgentSessionRuntime` 新增 `reloadCurrent(reason)`，在保持当前 `SessionManager` 和当前 session 文件的前提下重建 `AgentSession`，并触发已有 rebind 回调。
- `AgentSession` 新增只读 `skills()` getter，用于 reload 输出和状态确认。
- `/reload` 当前会执行：
  - `SettingsManager.reload()` 重新读取 settings。
  - `AuthStorage.reload()` 重新读取认证存储。
  - `ResourceLoader.reload()` 重新加载 skills、prompts、context files 和 system prompt。
  - `ModelRegistry.refresh()` 重新刷新模型列表。
  - `AgentSessionRuntime.reloadCurrent("reload")` 重建当前 session，使 reload 后的 resources/settings 进入后续 prompt。
- `Main` 的 runtime factory 调整为每次 session 创建/重建时重新加载 extension runner 和 extension tools，保证 `/reload` 能让扩展路径变化生效。
- `/reload` 成功后重新绑定交互循环中的当前 `session`、`/grill-me` 状态和 skill diagnostics 状态，并刷新 footer provider count。
- 输出包含 session id、前后 session 文件、skills/tools/models 数量，便于确认重载结果。
- 单测覆盖 runtime reload rebind，以及交互 `/reload` help 文案和成功输出。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/Main.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSessionRuntime.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 12 个测试、`CliEntryTest` 14 个测试，共 26 个测试，0 failures，0 errors。

当前限制：

- `/reload` 当前是行式版本，没有 TS 版全屏提示或分项 reload UI。
- Java 当前没有真正接入 keybindings/theme 全屏 TUI，因此 `/reload` 目前不重载这些未组装进行式 REPL 的能力。
- 模型刷新复用现有 `ModelRegistry.refresh()` 行为，没有单独提供离线/跳过网络选项。

### 优化 012：接通交互 `/settings`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：Java `SlashCommands` 声明 `/settings`，但此前行式交互未处理，会落入普通 prompt；TS 版提供 settings selector。

完成内容：

- `InteractiveModeRunner` 新增 `/settings` 执行分支。
- `/help` 新增 `/settings [json|get|set|unset]` 文案。
- `/settings` 默认显示当前 project trusted 状态、merged/global/project settings JSON，以及行式 usage。
- `/settings json` 输出 merged settings。
- `/settings global` / `/settings project` 分别输出对应 scope 的原始 settings。
- `/settings get <path>` 支持点分路径读取 merged settings，例如 `enableSkillCommands`、`retry.provider.timeoutMs`。
- `/settings set [global|project] <path> <json|text>` 支持写入 global 或 project scope；未指定 scope 时默认写 global。
- `/settings set [global|project] <path>=<json|text>` 支持等号形式。
- `/settings unset [global|project] <path>` 支持删除指定 scope 下的点分路径。
- `SettingsManager` 新增 `unset(scope, path)`，复用现有文件锁写入逻辑，并保留 project trust 限制。
- 配置修改后输出提示：需要 `/reload` 才能让当前 session 使用 settings 相关的新 resources/session 配置。
- 单测覆盖 `/settings` help 文案、默认查看、get、set、unset、json 输出，并继续验证后续 skill command 和 session 流程不受影响。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/config/SettingsManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 共 14 个测试，0 failures，0 errors。

当前限制：

- `/settings` 当前是行式基础版本，不提供 TS 版全屏 selector、搜索、分组说明或类型化控件。
- `/settings set` 对任意路径写入 JSON/text，不做完整 schema 校验；具体配置合法性仍由读取方处理。
- 修改会立即写入 settings 文件并更新 `SettingsManager`，但当前 session 中依赖 settings 的资源/扩展/系统提示需要用户运行 `/reload` 后重建。

### 优化 013：接通交互 `/logout`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：Java `SlashCommands` 声明 `/logout`，但此前行式交互未处理，会落入普通 prompt；TS 版提供更完整的认证管理体验。

完成内容：

- `InteractiveModeRunner` 新增 `/logout` 执行分支。
- `/help` 新增 `/logout <provider>` 文案。
- `/logout` 不传 provider 时列出当前 stored auth provider；没有 stored provider 时输出明确状态和 usage。
- `/logout <provider>` 支持删除 stored auth。
- `/logout <provider>` 支持删除 runtime `--api-key` 形式的临时认证。
- 对 environment-only auth 返回明确说明：环境变量不能由 CLI 删除，需要用户移除对应环境变量。
- 对 provider 未配置、参数过多等情况返回清晰错误或状态。
- 认证变更后输出提示：需要 `/reload` 才能让当前 session/provider 状态刷新到依赖认证的对象。
- 单测覆盖交互入口 help、空 provider 列表、未配置 provider，以及 focused helper 覆盖 stored/runtime/environment-only/参数错误。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 12 个测试、`CliEntryTest` 15 个测试，共 27 个测试，0 failures，0 errors。

当前限制：

- `/logout` 当前是行式基础版本，不提供 TS 版全屏 provider/account selector。
- 尚未实现完整 OAuth 浏览器/device-code 登录流程。
- CLI 不能删除当前进程继承的环境变量认证，只能提示用户移除环境变量。
- 已创建的 session/provider 相关对象可能仍持有旧状态，认证变更后建议运行 `/reload`。

### 优化 014：接通交互 `/login`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0/P1 项：Java `SlashCommands` 声明 `/login`，但此前行式交互未处理，会落入普通 prompt；TS 版有 provider 选择和 OAuth 登录 UX。

完成内容：

- `InteractiveModeRunner` 新增 `/login` 执行分支。
- `/help` 新增 `/login <provider> <api-key>` 文案。
- `/login` 不传参数时列出当前模型注册表可见 provider，并显示每个 provider 的认证来源状态。
- `/login <provider> <api-key>` 支持将 API key 写入 `AuthStorage`，输出不回显密钥。
- `/login <provider> env <ENV_VAR>` 支持写入环境变量引用形式，便于不把真实 key 直接写入 auth storage。
- 对缺失 provider、缺失 API key、非法环境变量名返回明确 usage。
- 如果未来有 Java OAuth provider 通过 `AuthStorage.registerOAuthProvider` 注册，`/login <provider>` 会调用现有 `AuthStorage.login` 入口。
- `/login` 或 `/logout` 后刷新交互 footer 的可用 provider 数量。
- 单测覆盖交互 help、provider 列表、API key 登录、登录后 `/logout` 列表和删除闭环、密钥不出现在输出里，以及 focused helper 覆盖 env 引用和参数错误。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 12 个测试、`CliEntryTest` 16 个测试，共 28 个测试，0 failures，0 errors。

当前限制：

- `/login` 当前是行式基础版本，不提供 TS 版全屏 provider selector、账号 selector 或登录状态面板。
- OAuth 浏览器/device-code 登录仍未真正实现；目前只是把已有 Java OAuth 抽象暴露到命令入口。
- API key 通过交互行输入时仍会出现在终端输入历史里；更安全的隐藏输入提示需要后续单独实现。
- `env <ENV_VAR>` 只保存引用，不保存环境变量值；如果当前进程没有该变量，会在输出中给出 warning。

### 优化 015：接通交互 `/share`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0/P2 项：Java `SlashCommands` 声明 `/share`，但此前行式交互未处理，会落入普通 prompt；TS 版可通过 GitHub gist 分享会话。

完成内容：

- `InteractiveModeRunner` 新增 `/share` 执行分支。
- `/help` 新增 `/share [public|secret]` 文案。
- `/share` 默认以 secret gist 分享当前 session HTML。
- `/share public` / `/share --public` 支持创建 public gist。
- `/share secret` / `/share --secret` / `/share private` / `/share --private` 显式选择 secret gist。
- 复用现有 `HtmlExporter` 将当前 session 导出成临时 HTML，再调用 `gh gist create --public=<bool> <file>`。
- 分享成功后输出 visibility、gist URL 和 gist 文件名。
- 对内存 session、非法参数、`gh` 失败或未返回 URL 返回明确错误和 usage。
- 新增可注入 `GistSharer`，单测用 fake sharer 验证导出的 HTML 内容、secret visibility、URL 输出和 help 文案，不依赖真实 GitHub CLI 或网络。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 12 个测试、`CliEntryTest` 16 个测试，共 28 个测试，0 failures，0 errors。

当前限制：

- `/share` 当前依赖本机已安装并已登录的 GitHub CLI `gh`。
- 当前上传的是 Java 现有 HTML exporter 的基础会话视图，还不是 TS 版更高保真 viewer。
- 没有实现 gist 更新、撤销、删除、浏览器打开或复制 URL 到剪贴板。
- 默认 visibility 为 secret；public gist 需要用户显式传 `/share public`。

### 优化 016：接通交互 `/name`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：Java `SlashCommands` 声明 `/name`，但此前行式交互未处理，会落入普通 prompt；TS 版可管理 session 显示名。

完成内容：

- `InteractiveModeRunner` 新增 `/name` 执行分支。
- `/help` 新增 `/name [text|clear]` 文案。
- `/name` 不传参数时显示当前 session id、当前 session name 和 usage。
- `/name <text>` 复用 `SessionManager.appendSessionInfo` 写入 session display name。
- `/name clear`、`/name reset`、`/name --clear` 支持清空当前 session display name。
- 复用 `SessionManager.sessionName()` 读取最新 session info entry，因此设置/清空会进入 session JSONL，并能被后续 session list/resume 使用。
- 单测覆盖交互 help、当前名称查看、设置名称、清空名称，以及最终 `SessionManager.sessionName()` 状态。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 12 个测试、`CliEntryTest` 16 个测试，共 28 个测试，0 failures，0 errors。

当前限制：

- `/name` 当前是行式基础版本，不提供 TS 版全屏 session 管理 UI。
- 清空名称通过追加空 `session_info` entry 表达，不重写或删除历史 name entry。
- 没有实现 rename 后的跨项目 session index 刷新提示；当前依赖已有 `SessionManager.buildSessionInfo/list` 从 JSONL 最新信息读取。

### 优化 017：接通交互 `/session`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：Java `SlashCommands` 声明 `/session`，但此前行式交互未处理，会落入普通 prompt；TS 版可查看当前 session 信息和状态。

完成内容：

- `InteractiveModeRunner` 新增 `/session` 执行分支。
- `/help` 新增 `/session` 文案。
- `/session` 输出当前 session id、name、session file、cwd、persisted 状态。
- 输出当前 leaf、总 entries 数和当前 branch entries 数，便于配合 `/tree`、`/fork` 使用。
- 输出当前模型、thinking level、消息统计、token 统计、skills 数和 tools 数。
- 对多余参数返回明确错误和 usage，避免误解为 session 切换入口。
- 单测覆盖 help 文案、session id/name/file/cwd、persisted、entries/branch、model/thinking、messages/tokens、skills/tools 等输出。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 12 个测试、`CliEntryTest` 16 个测试，共 28 个测试，0 failures，0 errors。

当前限制：

- `/session` 当前是只读行式状态面板，不提供 TS 版全屏 session inspector。
- `/session` 不做 session 切换、重命名、删除或 fork；这些仍由 `/resume`、`/name`、`/fork` 等入口承担。
- 输出字段偏工程调试视角，还没有 TS 版更细的会话来源、压缩状态、远端分享状态等 UI 分组。

### 优化 018：接通交互 `/new`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：Java `SlashCommands` 声明 `/new`，但此前行式交互未处理，会落入普通 prompt；TS 版可从交互界面启动新 session。

完成内容：

- `InteractiveModeRunner` 新增 `/new [name]` 执行分支。
- `/help` 新增 `/new [name]` 文案。
- `/new` 复用 `AgentSessionRuntime.newSession(currentSessionFile)` 创建空新会话，并将当前 session 文件记录为 parent metadata。
- `/new <name>` 在创建新会话后写入 session display name。
- 创建成功后重新绑定交互循环中的当前 `session`、`/grill-me` 状态和 skill diagnostics 状态，并刷新 footer provider count。
- 输出新 session id、name、previous session 文件和 current session 文件。
- 单测覆盖交互 help、创建新会话、可选 name、消息统计归零、runtime session 切换，以及新 session 记录原 session 为 parent。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 12 个测试、`CliEntryTest` 17 个测试，共 29 个测试，0 failures，0 errors。

当前限制：

- `/new` 当前是行式基础版本，不提供 TS 版全屏确认或 template/session picker。
- `/new` 默认记录当前 session 文件为 parent metadata，但不复制历史消息；如需复制当前分支应继续使用 `/clone`。
- `/new <name>` 只设置 display name，不支持同时设置 session id 或目标目录。

### 优化 019：接通交互 `/compact`

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：Java `SlashCommands` 声明 `/compact`，但此前行式交互未处理，会落入普通 prompt；TS 版可手动 compact session context。

完成内容：

- `AgentSession` 新增 `compactNow()`，复用 `CompactionSupport.prepareCompaction`、`serializeConversation`、`computeFileLists` 和 `formatFileOperations`。
- 手动压缩会调用当前模型生成摘要，写入 `SessionManager.appendCompaction`，并通过 `restoreMessagesFromSession()` 刷新内存消息。
- `InteractiveModeRunner` 新增 `/compact` 执行分支。
- `/help` 新增 `/compact` 文案。
- `/compact` 输出 compacted/skipped 状态、compaction entry、first kept entry、summarized messages、turn prefix messages、tokens before 和 summary chars。
- 对多余参数返回明确 usage。
- 单测覆盖交互 help、手动压缩、写入 `CompactionEntry`，以及 fake summary 内容落盘。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 12 个测试、`CliEntryTest` 18 个测试，共 30 个测试，0 failures，0 errors。

当前限制：

- `/compact` 当前是行式基础版本，不提供 TS 版全屏确认/进度 UI。
- 手动压缩使用当前模型和 `streamFunction`；真实环境中会消耗一次模型请求。
- 后续优化 023 已补自动/手动 compact 公共执行路径和 extension compact 前后事件。

### 优化 020：补齐扩展工具执行器 API

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0/P1 项：Java 扩展工具此前只能注册 `Tool` 声明进入 agent tool list，但没有执行器 API；模型调用扩展工具时只能得到降级错误。

完成内容：

- `ExtensionPlugin` 新增向后兼容的默认方法 `executeTool(String toolName, Object input)`。
- `ExtensionRunner.collectAgentTools()` 包装扩展工具时保留对应 `ExtensionPlugin` 实例，并在 `AgentTool.execute(...)` 中调用插件执行器。
- 扩展执行器返回 `AgentTool.AgentToolResult` 后会作为真实工具结果进入 agent loop。
- 旧扩展未实现 `executeTool(...)` 或返回 `null` 时，仍保留原有明确兼容错误，不破坏已有 JAR SPI。
- 单测覆盖可执行扩展工具返回正常文本/details、扩展工具经 `AgentLoop` 真实 tool call 路径执行，以及旧扩展未提供执行器时的兼容错误。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 14 个测试、`CliEntryTest` 18 个测试，共 32 个测试，0 failures，0 errors。

当前限制：

- 该能力仍限定在 Java JAR SPI；还不是 TS 版可直接加载 TS/JS 扩展包的运行时。
- `executeTool(...)` 目前只接收 tool name 和原始 input，不提供完整 session/context/cancellation/permission 对象。
- 扩展工具参数校验仍依赖模型声明和插件自身实现，Runner 不做 schema validation。

### 优化 021：补齐 `/resume` 行式重命名和删除

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：TS 版 `/resume` 支持 session 搜索、排序、过滤、重命名和删除；Java 此前只支持行式列表与恢复 session。

完成内容：

- `InteractiveModeRunner` 的 `/resume` 新增 `rename <target> <name>` 子命令。
- `rename` 支持使用现有 index、session id/id 前缀或 path 定位目标 session，并写入 `SessionInfoEntry`。
- 重命名当前 session 时复用 `AgentSession.setSessionName(...)`，保持运行中 session 的事件和内存状态一致。
- 重命名非当前 session 时通过 `SessionManager.open(...)` 直接更新对应 JSONL 文件，后续 `/resume` 列表和恢复后 session name 都能读取到新名称。
- `/resume` 新增 `delete <target>` 子命令，支持 index、id/id 前缀或 path 定位目标 session。
- 删除当前 session 会被拒绝，避免运行时指向已删除的会话文件；删除其他 session 后返回 session id 和文件路径。
- `/help` 和 `/resume` 列表 usage 更新为包含 resume/rename/delete。
- 交互单测覆盖 help 文案、列表 usage、重命名目标 session、删除非当前 session、恢复重命名后的 session，以及删除文件确实移除。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 14 个测试、`CliEntryTest` 18 个测试，共 32 个测试，0 failures，0 errors。

当前限制：

- `/resume rename/delete` 仍是行式命令，不提供 TS 版全屏 picker 和快捷键操作。
- `/resume delete` 当前是直接删除 session JSONL 文件，没有回收站/undo。
- 后续优化 022 已补全局 session 搜索和按名称/消息过滤；但仍不是 TS 版全屏 picker。

### 优化 022：补齐 `/resume` 全局搜索和过滤

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0 项：TS 版 `/resume` 支持跨项目 session 搜索、排序、按名称/内容过滤；Java 此前 `/resume` 只列当前项目 cwd 下的 session。

完成内容：

- `/resume` 新增 `--all` / `all` scope，能从当前 session dir 的父级 sessions root 扫描所有项目 session。
- 全局列表会包含 session cwd，便于区分不同项目来源。
- `/resume find <query>` 支持在当前项目 session 中按 session id、name、cwd、first message、all messages text 过滤。
- `/resume --all find <query>` 支持跨项目按名称和消息内容过滤。
- `/resume --all <target>` 支持从全局列表按 index、id/id 前缀或 path 恢复 session。
- 全局恢复时保留目标 session 自身 cwd；当前项目恢复仍沿用当前 cwd override 行为。
- `/resume --all rename <target> <name>` 和 `/resume --all delete <target>` 也复用全局目标解析。
- `/help` 和 `/resume` usage 更新为包含 `--all`、`find`、rename/delete。
- 交互单测覆盖默认项目列表、全局列表、按本项目消息过滤、按跨项目消息过滤、全局恢复其他 cwd session。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 14 个测试、`CliEntryTest` 19 个测试，共 33 个测试，0 failures，0 errors。

当前限制：

- `/resume --all/find` 仍是行式命令，不提供 TS 版全屏搜索 picker。
- `find` 过滤是简单大小写不敏感 substring，不提供模糊匹配、排序权重或高亮。
- 全局恢复目标 cwd 不存在时仍会走现有 missing cwd 错误路径，没有交互式 fallback 选择。

### 优化 023：抽公共 compaction 路径并补扩展事件

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0/P1 项：Java 版此前手动 `/compact` 和自动 compact 逻辑分叉，且扩展生命周期没有覆盖 session compact 前后事件。

完成内容：

- `AgentSession` 新增内部 `performCompaction(...)` 公共路径，自动 compact 和手动 `compactNow()` 都复用同一套摘要生成、file operations 附加、`appendCompaction`、`restoreMessagesFromSession` 逻辑。
- 自动 compact 保留原行为：只有存在 `messagesToSummarize` 时才执行压缩。
- 手动 `/compact` 保留原行为：允许 `turnPrefixMessages` 参与摘要，仍会返回 compacted/skipped 和统计信息。
- `ExtensionPlugin` 新增向后兼容 hook：
  - `onBeforeCompact(int tokensBefore, int summarizedMessages, int turnPrefixMessages)`
  - `onAfterCompact(String entryId, String summary)`
- `ExtensionRunner` 新增 `emitBeforeCompact(...)` / `emitAfterCompact(...)`，并沿用现有扩展事件策略：插件异常不打断主流程。
- compaction 执行前发 before hook，写入 `CompactionEntry` 并恢复内存上下文后发 after hook。
- 单测覆盖手动 compact 触发 before/after hook、hook 统计值与 `CompactionResult` 一致、after hook 能拿到 entry id 和 summary。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 15 个测试、`CliEntryTest` 19 个测试，共 34 个测试，0 failures，0 errors。

当前限制：

- compact hook 当前只能观察压缩输入统计和输出摘要，不能修改摘要、取消压缩或替换 compaction entry details。
- `onAfterCompact` 只在压缩成功写入后触发；压缩跳过和异常路径尚未建模成独立扩展事件。
- `/compact` 仍是行式基础版本，不提供 TS 版全屏确认/进度 UI。

### 优化 024：补齐扩展 slash command 注册和执行

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0/P1 项：TS 版扩展可通过 `registerCommand` 暴露 slash command，Java 扩展此前只能注册工具和事件 hook，不能提供交互命令入口。

完成内容：

- `ExtensionPlugin` 新增向后兼容 API：
  - `registerCommands()`
  - `executeCommand(String commandName, String arguments)`
- `ExtensionRunner` 新增 `collectCommands()`、`hasCommand(...)` 和 `executeCommand(...)`。
- 扩展命令按注册顺序收集，重复命令保留先注册者。
- 空命令名、内置 slash command 以及行式交互保留命令 `help` / `exit` / `quit` / `clear` 会被过滤，避免扩展覆盖核心入口。
- `InteractiveModeRunner` 在普通内置命令处理前分发扩展命令；命令执行不会追加 user message，也不会消费模型响应。
- `/help` 新增 `Loaded extension commands:` 分组，列出已加载扩展命令。
- `AgentSession` 新增只读 `extensionRunner()` 访问器，供当前交互 session 使用同一 runner 实例。
- 单测覆盖扩展命令收集、内置/保留命令过滤、命令执行和 CLI 集成分发。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 16 个测试、`CliEntryTest` 20 个测试，共 36 个测试，0 failures，0 errors。

当前限制：

- 扩展命令当前是行式同步处理，尚未提供 TS 版扩展 UI request/response、选择器或进度流。
- 后续优化 025 已补命令上下文和 session facade 的第一步，优化 028 已补结构化参数解析，优化 039 已补基础 abort signal；但暂未提供权限上下文或 UI context。
- 命令执行结果当前只回显到终端，不会作为可持久化事件写入 session transcript。

### 优化 025：补齐扩展命令上下文和基础 session facade

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 版扩展命令可通过 extension API 访问 session metadata 并调用 `setSessionName/getSessionName`；Java 版优化 024 只给命令 handler 传入命令名和原始参数字符串。

完成内容：

- 新增 `ExtensionCommandContext`，作为扩展命令执行时的受控上下文。
- `ExtensionCommandContext` 当前提供：
  - `cwd()`
  - `sessionId()`
  - `sessionFile()`
  - `sessionName()`
  - `stats()`
  - `setSessionName(String name)`
  - `clearSessionName()`
- `ExtensionPlugin` 新增向后兼容重载：
  - `executeCommand(String commandName, String arguments, ExtensionCommandContext context)`
- 旧扩展仍可只实现 `executeCommand(String commandName, String arguments)`，新重载默认委托旧方法。
- `ExtensionRunner.executeCommand(...)` 新增 context 参数版本，交互模式执行扩展命令时会传入当前 session 的上下文。
- CLI 集成测试覆盖扩展命令读取当前 `sessionId`、`cwd`、`stats`，并通过 context 修改 session name。
- 测试同时确认扩展命令修改 session metadata 后仍不会追加 user/assistant 对话消息，也不会触发模型调用。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 16 个测试、`CliEntryTest` 20 个测试，共 36 个测试，0 failures，0 errors。

当前限制：

- 后续优化 026 已补 `appendEntry`、`setLabel`、`clearLabel` 和 label 查询；但尚未提供 TS 版 `sendUserMessage`、active tools、model/thinking 修改等能力。
- 后续优化 028 已补结构化参数对象，优化 039 已补基础 abort signal；命令上下文暂未包含权限结果或 UI context。
- `setSessionName` 当前直接复用 `AgentSession.setSessionName`，会持久化 `SessionInfoEntry` 并触发 session info changed 事件，但不会把命令执行结果写入 transcript。

### 优化 026：补齐扩展 custom entry 和 label facade

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 版扩展 API 支持 `appendEntry` 和 `setLabel`，用于扩展写入自定义 session 状态、标注 entry；Java 版优化 025 只提供基础 session metadata 和 session name。

完成内容：

- `ExtensionCommandContext` 新增 custom entry 能力：
  - `appendEntry(String customType, JsonNode data)`
  - `appendEntry(String customType, Map<String, ?> data)`
- `ExtensionCommandContext` 新增 label 能力：
  - `setLabel(String entryId, String label)`
  - `clearLabel(String entryId)`
  - `label(String entryId)`
- `appendEntry` 直接复用 `SessionManager.appendCustomEntry`，返回新 entry id，便于扩展后续打 label 或引用。
- `setLabel` / `clearLabel` 复用 `SessionManager.appendLabelChange`，保留当前 Java session 的 label 变更语义。
- CLI 集成测试覆盖扩展命令写入 custom entry、给该 entry 打 label、读回 custom type/data/label，并确认仍不会触发模型调用或追加普通 user/assistant 对话消息。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 16 个测试、`CliEntryTest` 20 个测试，共 36 个测试，0 failures，0 errors。

当前限制：

- custom entry 目前只通过命令上下文写入，不提供独立扩展事件或消息 renderer 注册能力。
- label API 当前要求扩展提供已存在的 entry id；不存在时沿用 `SessionManager.appendLabelChange` 抛错行为。
- 后续优化 027 已补同步版 `sendUserMessage`，优化 036 已补文本消息 steer/followUp 队列语义，优化 039 已补基础 abort signal；但仍没有权限和 UI context。

### 优化 027：补齐扩展 `sendUserMessage` 同步版

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 版扩展 API 支持 `sendUserMessage` 从扩展触发一轮用户消息；Java 版扩展命令此前只能写 metadata/custom entry/label，不能让扩展命令驱动 agent 回合。

完成内容：

- `AgentSession` 新增 `promptRaw(String text)`，复用现有 prompt 执行链路，但跳过 skill slash command 展开和 skill trigger diagnostic。
- `prompt(String text)` 保持原行为，仍会处理 `/skill:*` 和 trigger diagnostics。
- `ExtensionCommandContext` 新增：
  - `sendUserMessage(String content)`
- `sendUserMessage` 当前调用 `AgentSession.promptRaw(...)`，确保扩展发送的 `/skill:*` 等文本会作为普通 user message 进入模型，而不会被解释为 slash command。
- CLI 集成测试新增扩展命令 `/sendmsg`，验证扩展命令可触发一轮 user/assistant 消息、只调用一次模型，并保留已有 custom entry/label/session metadata 行为。
- core 单测覆盖 `promptRaw` 不展开 `/skill:*`，防止后续回归。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 17 个测试、`CliEntryTest` 20 个测试，共 37 个测试，0 failures，0 errors。

当前限制：

- 后续优化 036 已补文本消息 `deliverAs=steer|followUp` 队列语义；本节的同步版记录保留为历史执行记录。
- 后续优化 029 已补行式交互中的扩展 `sendUserMessage` assistant 输出渲染；但仍不是 TS 版异步队列/流式中断体验。
- 后续优化 098 已补 text/image content blocks，优化 105 已补运行中扩展 source 标记；本节的同步版记录保留为历史执行记录。

### 优化 028：补齐扩展结构化命令参数

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：Java 扩展命令此前只能拿到原始参数字符串，扩展作者需要自行处理引号、flag 和 option；TS 生态扩展命令通常依赖更完整的命令上下文能力。

完成内容：

- `ExtensionCommandContext` 构造时记录当前 `commandName` 和原始 `arguments`。
- `ExtensionCommandContext` 新增结构化参数 API：
  - `commandName()`
  - `arguments()`
  - `argv()`
  - `options()`
  - `option(String name)`
  - `flags()`
  - `hasFlag(String name)`
  - `positionals()`
- 交互模式执行扩展命令时，会把当前命令名和原始参数传入 `ExtensionCommandContext`。
- 参数解析支持常见 shell-like 用法：
  - 双引号和单引号包裹参数
  - 反斜杠转义
  - `--key value`
  - `--key=value`
  - `--flag`
  - positional 参数
- CLI 集成测试新增 `/args --name "Ada Lovelace" --count=2 bare --verbose`，覆盖 argv、option、flag 和 positional 解析。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 17 个测试、`CliEntryTest` 20 个测试，共 37 个测试，0 failures，0 errors。

当前限制：

- 参数解析是轻量 shell-like 版本，不支持完整 shell 语法、短参数聚合、重复 option 多值或 schema 校验。
- 当前 structured 参数只进入 Java 扩展命令 context，不影响内置 slash command 参数解析。
- 后续优化 036 已补文本消息 `sendUserMessage` steer/followUp 队列语义，优化 039 已补基础 abort signal；仍未实现权限和 UI context。

### 优化 029：接通交互 `!` / `!!` bash 命令

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P0/P1 项：TS 交互模式支持 `!cmd` 运行 bash 并写入上下文、`!!cmd` 运行 bash 但排除上下文；Java 此前已有 `BashExecutor` 和 bash tool，但行式交互未处理 `!` / `!!` 用户命令。

完成内容：

- `InteractiveModeRunner` 新增 `!<cmd>` / `!!<cmd>` 执行入口。
- `!<cmd>` 会在当前 session cwd 执行 bash，并把结果记录为 `bashExecution` session message，后续模型上下文可见。
- `!!<cmd>` 会同样执行并持久化结果，但标记 `excludeFromContext=true`，后续模型上下文转换会跳过该输出。
- `/help` 新增 `!<cmd>` 和 `!!<cmd>` 文案。
- `AgentSession` 新增 `executeBash(...)` 和 `recordBashResult(...)`，复用现有 `BashExecutor` / `LocalBashOperations`。
- `SessionManager` 新增 `appendCustomMessage(...)`，用于持久化 `bashExecution` 等展示型 custom message。
- `CompactionSupport.buildSessionContext(...)` 能从 JSONL `custom_message` 恢复 `bashExecution` 专用消息，保证重启后 included/excluded 上下文语义不丢失。
- 单测覆盖交互 help/output、`!`/`!!` 行式执行、session tree 展示 bash custom message，以及 session 恢复后 included bash 进入上下文、excluded bash 不进入上下文。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/CompactionSupport.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/session/SessionManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 18 个测试、`CliEntryTest` 20 个测试，共 38 个测试，0 failures，0 errors。

当前限制：

- 当前是行式输出，不提供 TS 版 `BashExecutionComponent` 的全屏实时组件、折叠/展开 UI 或 Esc 取消体验。
- 后续优化 030 已补 `user_bash` 扩展事件和扩展替换 bash operations；但优化 029 本身仍只是行式 shell 输出体验。
- 后续优化 031 已补 `shellCommandPrefix` / `shellPath` 设置接入；优化 029 本身仍不提供全屏 bash execution component。

### 优化 030：补齐扩展 `user_bash` 事件

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 扩展支持 `user_bash` 事件，可在用户输入 `!` / `!!` 时拦截，提供自定义 bash operations（例如 SSH、profile wrapper）或直接返回完整结果；Java 优化 029 只接通了行式 `!` / `!!` 本地执行，尚未给扩展拦截点。

完成内容：

- `ExtensionPlugin` 新增向后兼容 API：
  - `onUserBash(String command, boolean excludeFromContext, Path cwd)`
  - `UserBashResult.operations(BashOperations operations)`
  - `UserBashResult.result(BashExecutor.Result result)`
- `ExtensionRunner` 新增 `emitUserBash(...)`，按扩展加载顺序寻找第一个有效拦截结果。
- `AgentSession.executeBash(...)` 接入扩展拦截：
  - 扩展返回完整 `BashExecutor.Result` 时，直接持久化并返回该结果，不执行本地 shell。
  - 扩展返回 `BashOperations` 时，使用该 operations 执行命令，并保留原有 streaming chunk、持久化和 `excludeFromContext` 语义。
  - 无扩展拦截时继续使用 `LocalBashOperations`。
- 行式 `InteractiveModeRunner` 的 `!` / `!!` 自动复用同一 `AgentSession.executeBash(...)` 路径，因此交互入口也支持扩展拦截。
- 单测覆盖扩展直接返回 bash 结果、扩展提供自定义 operations、stream chunk 透传、session 中 `bashExecution` 持久化，以及交互 `!extension-bash` 通过扩展结果完成。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 19 个测试、`CliEntryTest` 20 个测试，共 39 个测试，0 failures，0 errors。

当前限制：

- `user_bash` 当前是 Java JAR SPI 的同步接口，不是 TS 版动态 TS/JS 扩展运行时。
- 当前采用第一个有效拦截结果，不做多扩展 operations middleware 合成。
- 后续优化 039 已补基础 abort signal；暂未提供 TS 版 `ctx.mode`、UI context 或 TUI 自定义组件能力。
- 后续优化 031 已补 shell prefix/custom shell path 设置接入。

### 优化 031：接入 bash `shellCommandPrefix` / `shellPath` 设置

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 Shell 运行时便利功能项：TS 版 `AgentSession.executeBash` 和 bash tool 都会读取 `shellCommandPrefix` 与 `shellPath`；Java 此前虽然有 `LocalBashOperations(shellPath)` 和 `SettingsManager.getShellCommandPrefix()`，但交互 `!` / `!!` 与模型调用 bash tool 都没有实际使用这些 settings。

完成内容：

- `SettingsManager` 新增 `getShellPath()`。
- `AgentSession.Config` 新增 `shellCommandPrefix` / `shellPath` 字段，并保持旧构造器兼容。
- `AgentSession.executeBash(...)` 执行时会把 `shellCommandPrefix` 拼到实际 shell 命令前，同时 session 中仍记录用户原始命令。
- `AgentSession.executeBash(...)` 无扩展自定义 operations 时会用 `LocalBashOperations(shellPath)`，使交互 `!` / `!!` 支持自定义 shell。
- `CodingToolFactory` 新增 `BashConfig(commandPrefix, shellPath)`，bash tool 执行时同样应用 prefix 和 shell path。
- `AgentSessionServices.resolveTools(...)` 从 settings 注入 `BashConfig`，保证模型调用内置 bash tool 与交互 `!` / `!!` 一致。
- 单测覆盖 settings 读取 `shellPath`、交互/user bash prefix 生效且落盘记录原始命令、bash tool prefix 生效并把 shell path 传入本地 shell 执行。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/config/SettingsManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSessionServices.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/tools/CodingToolFactory.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/config/SettingsManagerTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/tools/CodingToolFactoryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 20 个测试、`CliEntryTest` 20 个测试、`CodingToolFactoryTest` 5 个测试、`SettingsManagerTest` 8 个测试，共 53 个测试，0 failures，0 errors。

当前限制：

- 仍没有 TS 版 bash execution component 的全屏实时 UI、折叠/展开和 Esc 取消体验。
- Java `LocalBashOperations` 仍是同步基础实现，尚未补齐 TS 版 bash 底层 abort signal、late output cleanup 和更多 Windows/WSL/Cygwin 回归资产。

### 优化 032：补齐扩展 `input` 事件

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 扩展支持 `input` 事件，在扩展 slash command 检查之后、skill/template 展开之前拦截用户输入，可 transform 或 handled；Java 此前交互输入只能进入内置 slash command、`!` / `!!` 或普通 prompt，没有扩展输入拦截点。

完成内容：

- `ExtensionPlugin` 新增 `InputResult` 和 `onInput(String text, ExtensionCommandContext context)` 默认方法，保持旧扩展兼容。
- `ExtensionRunner` 新增 `emitInput(...)`：
  - 多个扩展按加载顺序执行；
  - transform 结果会继续传给后续扩展，实现 TS 版 transform chaining 的同步 Java SPI 版本；
  - handled 结果会短路后续处理并跳过 agent prompt；
  - 扩展异常沿用既有策略，不打断主交互流程。
- `InteractiveModeRunner` 调整交互处理顺序：
  - 扩展 slash command 仍优先执行，命中后跳过 `input` 事件；
  - 其余输入先触发 `input` 事件；
  - transform 后重新进入 Java 现有 skill command、内置命令、`!` / `!!` 和普通 prompt 路径。
- 单测覆盖 Runner 级 transform chaining / handled 短路，以及 CLI 级 handled 输入不调用模型、transform 后持久化改写后的 user message。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest#extensionInputEventsChainTransformsAndHandleInput,CliEntryTest#interactiveExtensionInputCanTransformOrHandleText -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。新增 `AgentSessionRuntimeTest` / `CliEntryTest` 各 1 个用例，共 2 个测试，0 failures，0 errors。

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 21 个测试、`CliEntryTest` 21 个测试、`CodingToolFactoryTest` 5 个测试、`SettingsManagerTest` 8 个测试，共 55 个测试，0 failures，0 errors。

当前限制：

- `input` 事件当前是 Java JAR SPI 的同步接口，不是 TS 版动态 TS/JS 扩展运行时。
- 后续优化 039 已补基础 abort signal；尚未提供 TS 版 `images`、`source`、`streamingBehavior`、`ctx.mode`、UI context 或 TUI 自定义组件能力。
- 当前 context 的 structured args 仍基于原始输入构建；transform 链中后续扩展能看到改写后的 text，但 context 不会随 transform 重算。

### 优化 033：补齐扩展 `tool_call` 改参/阻断

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 扩展支持 `tool_call` 事件，handler 可以修改 `event.input` 后继续执行，也可以返回 block/reason 阻断危险工具调用；Java 此前只有 `onBeforeToolCall(String, String)` / `onAfterToolCall(String, String)` 观察型 hook，不能影响工具执行。

完成内容：

- `ExtensionPlugin` 新增向后兼容 API：
  - `ToolCallResult.transform(Object input)` 用于替换工具输入；
  - `ToolCallResult.block(String reason)` 用于阻断工具执行；
  - `onToolCall(String toolName, Object input, ExtensionCommandContext context)` 作为同步 Java SPI handler。
- `ExtensionRunner` 新增 `emitToolCall(...)`：
  - 每个扩展仍会先收到 legacy `onBeforeToolCall(...)` 字符串 payload；
  - transform 后的 input 会继续传给后续扩展，实现 TS 版“后续 handler 看到前序修改”的核心语义；
  - 第一个 block 结果会短路后续扩展和真实工具执行。
- `AgentSession.wrapToolsForExtensions(...)` 接入 `emitToolCall(...)`：
  - transform 时用改写后的 input 执行真实工具；
  - block 时返回 error tool result，details 标记 `extensionBlocked=true`，并继续触发 after hook 观察最终输出。
- 单测覆盖内置 `read` 工具：扩展把 `note.txt` 改读 `safe.txt`，并阻断 `blocked.txt`，验证模型后续上下文中的 tool result 确实来自改写/阻断结果。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest#extensionToolCallCanTransformInputAndBlockExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。新增 `AgentSessionRuntimeTest` 1 个用例，0 failures，0 errors。

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 22 个测试、`CliEntryTest` 21 个测试、`CodingToolFactoryTest` 5 个测试、`SettingsManagerTest` 8 个测试，共 56 个测试，0 failures，0 errors。

当前限制：

- `tool_call` 当前是 Java JAR SPI 的同步接口，不是 TS 版动态 TS/JS 扩展运行时。
- 后续优化 039 已补基础 abort signal；Java 版暂未提供 TS `ToolCallEvent` 的强类型 built-in union、`toolCallId`、完整 UI context 或并行工具执行下的 session 同步语义。
- transform 后不重新执行 JSON schema validation，沿用 TS 文档中“mutation 后不 re-validation”的行为。

### 优化 034：补齐扩展 `tool_result` 结果修改

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 扩展支持 `tool_result` 事件，在工具执行后、`tool_execution_end` 和最终 tool result message 发出前修改 `content` / `details` / `isError`；Java 此前只有 `onAfterToolCall(String, String)` 观察型 hook，不能影响进入模型上下文的工具结果。

完成内容：

- `ExtensionPlugin` 新增 `ToolResultPatch` 和 `onToolResult(String toolName, Object input, AgentToolResult result, ExtensionCommandContext context)`：
  - `ToolResultPatch.content(...)` 修改结果内容；
  - `ToolResultPatch.details(...)` 修改 details；
  - `ToolResultPatch.error(...)` 修改错误状态；
  - `ToolResultPatch.of(...)` 一次性 patch 多个字段。
- `ExtensionRunner` 新增 `emitToolResult(...)`：
  - 多个扩展按加载顺序运行；
  - 每个 handler 都看到前一个 handler patch 后的当前结果；
  - omitted 字段保留当前值；
  - 所有 patch 完成后再触发 legacy `onAfterToolCall(...)`，使旧扩展看到最终输出。
- `AgentSession.wrapToolsForExtensions(...)` 在正常工具结果、扩展 block 结果和工具异常转成的错误结果上都调用 `emitToolResult(...)`，保证最终进入 `Message.ToolResult` / 模型上下文的是扩展修改后的结果。
- 单测覆盖两个扩展链式修改同一个 `read` tool result：第一个 patch content/details/error，第二个读取第一个 patch 后的结果继续修改，并验证最终 tool result 和 after hook 输出。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest#extensionToolResultCanPatchContentDetailsAndErrorInOrder -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。新增 `AgentSessionRuntimeTest` 1 个用例，0 failures，0 errors。

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 23 个测试、`CliEntryTest` 21 个测试、`CodingToolFactoryTest` 5 个测试、`SettingsManagerTest` 8 个测试，共 57 个测试，0 failures，0 errors。

当前限制：

- `tool_result` 当前是 Java JAR SPI 的同步接口，不是 TS 版动态 TS/JS 扩展运行时。
- 后续优化 039 已补基础 abort signal；Java 版暂未提供 TS `ToolResultEvent` 的强类型 built-in union、`toolCallId`、完整 UI context 或并行工具完成顺序语义。
- 当前 patch API 中 `null` 表示字段未修改，因此暂不支持显式把 `details` 清空为 null。

### 优化 035：补齐扩展 `before_agent_start` 上下文/系统提示注入

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 扩展支持 `before_agent_start` 事件，可在用户提交 prompt 后、agent loop 启动前注入持久 custom message，并修改本轮系统提示；Java 此前只有 `onBeforeTurn(String prompt)` 观察型 hook，不能给本轮 agent 注入上下文或调整系统提示。

完成内容：

- `ExtensionPlugin` 新增 `CustomMessage`、`BeforeAgentStartResult` 和 `onBeforeAgentStart(String prompt, String systemPrompt, ExtensionCommandContext context)`：
  - 扩展可返回新的 `systemPrompt`，只影响当前 agent loop；
  - 扩展可返回一个或多个 `CustomMessage`，写入 session 并进入本轮模型上下文；
  - 默认方法保持旧扩展兼容。
- `ExtensionRunner` 新增 `emitBeforeAgentStart(...)`：
  - 多个扩展按加载顺序运行；
  - 后续扩展会看到前序扩展修改后的 system prompt；
  - 多个扩展注入的 custom message 会按顺序累积；
  - 扩展异常沿用既有策略，不打断主 prompt 流程。
- `AgentSession.prompt(...)` 在追加当前 user message 和启动 `AgentLoop` 前执行 `before_agent_start`：
  - 注入的 custom message 会持久化为 session custom message；
  - 注入的 custom message 会出现在本轮 provider context 中；
  - 修改后的 system prompt 会传入本轮 `AgentContext`。
- 单测覆盖两个扩展链式修改 system prompt、注入 string/object custom message、provider context 能看到注入内容和当前用户消息、session 持久化 custom message，且普通 user/assistant 统计不受影响。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest#extensionBeforeAgentStartCanInjectContextAndModifySystemPrompt -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。新增 `AgentSessionRuntimeTest` 1 个用例，0 failures，0 errors。

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 24 个测试、`CliEntryTest` 21 个测试、`CodingToolFactoryTest` 5 个测试、`SettingsManagerTest` 8 个测试，共 58 个测试，0 failures，0 errors。

当前限制：

- `before_agent_start` 当前是 Java JAR SPI 的同步接口，不是 TS 版动态 TS/JS 扩展运行时。
- 后续优化 039 已补基础 abort signal；Java 版暂未提供 TS 事件中的 `ctx.getSystemPrompt()`、完整 UI context 或异步队列语义；本轮 system prompt 通过方法参数传入，并由返回值链式更新。
- 注入的 custom message 当前会在 session 中排在当前 user message 之前，以保证本轮 provider context 能在用户 prompt 之前看到扩展上下文。

### 优化 036：补齐扩展 `sendUserMessage` steer/followUp 队列语义

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 扩展 `sendUserMessage(content, { deliverAs })` 在 agent 正在 streaming 时要求声明 delivery mode，并可把消息作为 `steer` 插入当前工具链之后、或作为 `followUp` 等 agent 工具链结束后再触发下一轮；Java 优化 027 只有空闲命令里的同步 `promptRaw(...)`。

完成内容：

- `AgentSession` 新增 `UserMessageDelivery.STEER` / `FOLLOW_UP` 和 `sendUserMessage(String, UserMessageDelivery)`：
  - agent 空闲时保持旧行为，立即触发 `promptRaw(...)`；
  - agent 运行中未指定 delivery mode 会抛出明确错误；
  - `STEER` 写入 steering 队列，接入 `AgentLoop` 的 `steeringMessages` supplier；
  - `FOLLOW_UP` 写入 follow-up 队列，接入 `AgentLoop` 的 `followUpMessages` supplier；
  - 队列变更复用既有 `QueueUpdate` session event；
  - 异常退出时清理未消费队列，避免泄漏到下一轮 prompt。
- `ExtensionCommandContext` 新增 `UserMessageDelivery` enum 和 `sendUserMessage(String, UserMessageDelivery)`：
  - 旧的 `sendUserMessage(String)` 仍兼容空闲命令触发；
  - agent 运行中调用旧方法会得到“需要 deliverAs”的错误，与 TS 行为对齐。
- 单测覆盖扩展在 `tool_result` 阶段同时发送 steer 和 followUp：
  - steer 消息在当前 tool result 后、下一次 LLM 调用前进入上下文；
  - followUp 消息等工具链完成后再进入后续 LLM 调用；
  - queued user message 会持久化到 session，并纳入 user/assistant/tool 统计；
  - 运行中不带 delivery mode 的调用会被拒绝。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest#extensionSendUserMessageQueuesSteerAndFollowUpDuringAgentTurn -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。新增 `AgentSessionRuntimeTest` 1 个用例，0 failures，0 errors。

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 25 个测试、`CliEntryTest` 21 个测试、`CodingToolFactoryTest` 5 个测试、`SettingsManagerTest` 8 个测试，共 59 个测试，0 failures，0 errors。

当前限制：

- 当前只支持文本 user message；尚未迁移 TS `sendUserMessage` 的多 content block / 图片输入。
- 当前是同步 Java JAR SPI 队列入口，不是 TS 版动态 TS/JS 运行时。
- 后续优化 037 已补 TS `sendMessage(custom message)` 文本/对象内容、steer/followUp/nextTurn delivery 和空闲持久化，优化 039 已补基础 abort signal；Java 版仍未提供完整 UI context。

### 优化 037：补齐扩展 `sendMessage` custom message 和 `nextTurn` delivery

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 扩展 `sendMessage(message, options)` 可注入 custom message，支持空闲持久化、空闲 `triggerTurn`、streaming 时 `steer` / `followUp` delivery，以及 `nextTurn` 在下一次用户 prompt 时作为上下文注入；Java 此前只有 `appendEntry` 状态写入和 `before_agent_start` 特定事件的 custom message 注入。

完成内容：

- `AgentSession` 新增 `CustomMessageDelivery.STEER` / `FOLLOW_UP` / `NEXT_TURN` 和 `sendMessage(CustomMessage, delivery, triggerTurn)`：
  - 空闲且无 `triggerTurn` 时持久化 custom message 并发出 message start/end 事件，不触发模型；
  - 空闲且 `triggerTurn=true` 时以 custom message 作为本轮 prompt 触发 LLM；
  - agent 运行中 `STEER` / `FOLLOW_UP` 会持久化 custom message，并接入当前 AgentLoop 的 steering/followUp 队列；
  - `NEXT_TURN` 会暂存到下一次用户 prompt 前，持久化并进入该轮 provider context，但不会立即打断当前回合；
  - `before_agent_start` 注入的 custom message 复用同一套构造/持久化 helper。
- `ExtensionCommandContext` 新增 `MessageDelivery` enum 和 `sendMessage(CustomMessage, MessageDelivery, boolean)`，让扩展命令、工具事件和生命周期事件共享同一入口。
- 单测覆盖：
  - 空闲 `sendMessage` 只持久化 custom message，不触发 LLM；
  - `tool_result` 阶段发送 custom `STEER`，会在下一次 LLM 调用前进入上下文；
  - custom `FOLLOW_UP` 等当前工具链结束后进入后续 LLM 调用；
  - custom `NEXT_TURN` 等下一次用户 prompt 前注入上下文；
  - custom message 持久化为 session custom message，但不计入普通 user message stats。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest#extensionSendMessageQueuesCustomMessagesAndNextTurnContext -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。新增 `AgentSessionRuntimeTest` 1 个用例，0 failures，0 errors。

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 26 个测试、`CliEntryTest` 21 个测试、`CodingToolFactoryTest` 5 个测试、`SettingsManagerTest` 8 个测试，共 60 个测试，0 failures，0 errors。

当前限制：

- 当前 Java `sendMessage` 仍是同步 JAR SPI API，不是 TS/JS 动态运行时。
- 当前 custom message content 通过既有 `CodingAgentMessages` 转换进入 LLM，上下文支持文本和可 JSON 化对象；尚未补齐 TS image content array 和自定义 renderer。
- `nextTurn` 当前只覆盖 custom message；`sendUserMessage` 仍只支持 `STEER` / `FOLLOW_UP`，与 TS 行为一致。

### 优化 038：补齐扩展 `session_before_switch` / `session_before_fork` 取消拦截

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 扩展支持 `session_before_switch` 和 `session_before_fork`，可在 `/new`、`/resume`、`/fork`、`/clone` 等会话替换动作发生前取消。Java 此前已有 runtime replacement 路径，但扩展不能阻止 session teardown/rebind。

完成内容：

- `ExtensionPlugin` 新增 `SessionBeforeResult`、`onSessionBeforeSwitch(...)` 和 `onSessionBeforeFork(...)`：
  - `SessionBeforeResult.cancel(reason)` 可取消会话替换；
  - switch 事件包含 `reason`（`new` / `resume`）和目标 session file；
  - fork 事件包含 entry id 和 position（`before` / `at`）。
- `ExtensionRunner` 新增 `emitSessionBeforeSwitch(...)` / `emitSessionBeforeFork(...)`：
  - 多扩展按加载顺序执行；
  - 第一个 cancel 结果短路后续扩展和真实 session 替换；
  - 扩展异常沿用既有策略，不打断主流程。
- `AgentSessionRuntime` 在统一 replacement 路径接入：
  - `newSession(...)` 在创建新 session 文件前触发 `session_before_switch(reason=new)`；
  - `switchSession(...)` / `importFromJsonl(...)` 在 teardown 前触发 `session_before_switch(reason=resume)`；
  - `fork(...)` 在创建 branched session 前触发 `session_before_fork`；
  - 取消时返回 `ReplacementResult.cancelled=true` 和 `cancelReason`，不会 teardown 当前 session、不会 rebind、不会创建新 runtime。
- 单测覆盖 `/new`、`/resume`、`/fork before` 三类 runtime replacement 被扩展取消，并验证当前 session 未 dispose、未 rebind、未创建新 runtime，同时 fork 仍返回被选中的 user message 文本。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSessionRuntime.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest#runtimeSessionBeforeHooksCanCancelSwitchAndFork -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。新增 `AgentSessionRuntimeTest` 1 个用例，0 failures，0 errors。

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 27 个测试、`CliEntryTest` 21 个测试、`CodingToolFactoryTest` 5 个测试、`SettingsManagerTest` 8 个测试，共 61 个测试，0 failures，0 errors。

当前限制：

- 当前只实现取消拦截；TS `session_before_fork` 的 `skipConversationRestore` 预留语义尚未迁移。
- 当前是 Java JAR SPI 同步事件，不是 TS/JS 动态运行时，也尚未提供 UI confirm context。
- CLI 各命令当前会看到 `ReplacementResult.cancelled`，但取消提示仍是行式基础信息，不是 TS 全屏 UI 体验。

### 优化 039：补齐扩展上下文基础 abort signal

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS `ExtensionContext` 提供 `isIdle()`、`signal`、`abort()`、`hasPendingMessages()`，扩展可在事件处理期间观察当前 agent 状态并请求中止当前 turn。Java 此前只在 `AgentSession` 内部维护 `agentTurnActive`，扩展无法发出可被 agent loop 识别的 abort 请求。

完成内容：

- `AgentLoop.Config` 新增 `abortRequested` supplier：
  - 每次模型 stream 前检查 abort 状态；
  - 已请求 abort 时生成 `StopReason.ABORTED` assistant message 并结束 agent loop；
  - 保留既有构造器兼容性，未提供 supplier 时默认不取消。
- `AgentSession` 新增当前 turn abort 状态：
  - `beginAgentTurn()` / `endAgentTurn()` 管理 `abortRequested` 和 `activeAbortSignal` 生命周期；
  - `abort()` 在 agent active 时标记 abort 并完成 signal；
  - `isIdle()`、`hasPendingMessages()`、`abortSignal()`、`abortRequested()` 暴露可查询状态。
- `ExtensionCommandContext` 新增基础上下文能力：
  - `isIdle()`；
  - `hasPendingMessages()`；
  - `abortSignal()`；
  - `abortRequested()`；
  - `abort()`。
- 单测覆盖扩展在 `before_agent_start` 调用 `context.abort()`：
  - 扩展可观察到 agent 非 idle 和 signal 存在；
  - abort 后 signal completed；
  - streamFunction 不会被调用；
  - session 记录 `StopReason.ABORTED` assistant，并在 turn 结束后恢复 idle。

涉及文件：

- `packages/agent/src/main/java/works/earendil/pi/agent/core/AgentLoop.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest#extensionContextAbortStopsAgentBeforeStreaming -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。新增 `AgentSessionRuntimeTest` 1 个用例，0 failures，0 errors。

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 28 个测试、`CliEntryTest` 21 个测试、`CodingToolFactoryTest` 5 个测试、`SettingsManagerTest` 8 个测试，共 62 个测试，0 failures，0 errors。

```bash
mvn -pl packages/agent -am -Dtest=AgentLoopTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentLoopTest` 1 个测试，0 failures，0 errors。

当前限制：

- 当前 abort 在 agent loop 的模型调用边界生效；尚未把正在进行中的 provider streaming subscription / HTTP 请求底层取消打通。
- Java 暴露的是 `Optional<CompletionStage<Void>> abortSignal()`，不是 TS `AbortSignal` 对象。
- 当前未新增 UI interrupt 入口，只补齐扩展事件上下文的基础取消能力。

### 优化 040：补齐扩展 `before_provider_request` / `after_provider_response` 基础 hook

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 扩展支持 `before_provider_request` 修改 provider payload，并通过 `after_provider_response` 观察 HTTP status / headers。Java 此前 provider payload 在 `pi-ai` provider 内部构造，扩展层无法介入 provider 请求/响应。

完成内容：

- `StreamOptions` 新增 `ProviderHooks`：
  - `ProviderPayloadHook beforeRequest(Object payload, Model model)` 可返回替换后的 payload；
  - `ProviderResponseHook afterResponse(int status, Map<String, String> headers, Model model)` 可观察响应；
  - 保留原 11 参数构造器，避免既有调用点大面积改动。
- `ProviderHttpSupport` 新增 provider hook 工具：
  - `applyBeforeProviderRequest(...)` 在发送前调用 hook，并将返回值归一为 `JsonNode`；
  - `emitAfterProviderResponse(...)` 将 Java `HttpHeaders` 扁平化为 `Map<String, String>` 后发给 hook。
- 主要 HTTP provider 接入 hook：
  - `OpenAiProvider`；
  - `OpenAiCompatibleProvider`（覆盖 Groq、Mistral、OpenRouter、Together、DeepSeek 等兼容 provider）；
  - `GeminiProvider`。
- `ExtensionPlugin` / `ExtensionRunner` 新增扩展事件：
  - `onBeforeProviderRequest(...)`；
  - `onAfterProviderResponse(...)`；
  - before provider request 按扩展加载顺序链式改写 payload；
  - after provider response 按扩展加载顺序广播 status / headers。
- `AgentSession` 在构造 effective `StreamOptions` 时把扩展 runner 桥接为 provider hooks，并保留外部已有 hooks 的串联执行。
- 单测覆盖自定义 provider 通过 `StreamOptions.providerHooks()` 调用扩展：
  - 扩展将 payload marker 从 `original` 改为 `mutated`；
  - provider 最终 assistant 内容证明 payload 改写生效；
  - 扩展收到 response status 和 header；
  - hook 执行期间上下文仍处于 agent active 状态。

涉及文件：

- `packages/ai/src/main/java/works/earendil/pi/ai/provider/StreamOptions.java`
- `packages/ai/src/main/java/works/earendil/pi/ai/provider/ProviderHttpSupport.java`
- `packages/ai/src/main/java/works/earendil/pi/ai/provider/OpenAiProvider.java`
- `packages/ai/src/main/java/works/earendil/pi/ai/provider/OpenAiCompatibleProvider.java`
- `packages/ai/src/main/java/works/earendil/pi/ai/provider/GeminiProvider.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest#extensionProviderHooksCanTransformPayloadAndObserveResponse -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。新增 `AgentSessionRuntimeTest` 1 个用例，0 failures，0 errors。

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 29 个测试、`CliEntryTest` 21 个测试、`CodingToolFactoryTest` 5 个测试、`SettingsManagerTest` 8 个测试，共 63 个测试，0 failures，0 errors。

```bash
mvn -pl packages/ai -am -Dtest=BuiltinProvidersTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`BuiltinProvidersTest` 15 个测试，0 failures，0 errors。

当前限制：

- 当前接入的是 JSON body provider；Anthropic / Bedrock 仍是 stub provider，尚无真实 HTTP payload 可 hook。
- `after_provider_response` 目前在拿到 HTTP response 后、消费 stream body 前触发；不暴露 response body。
- 当前仍是 Java JAR SPI 同步 hook，不是 TS/JS 动态扩展运行时，也未补动态 provider 注册的完整生态。

### 优化 041：补齐扩展 `resources_discover` skill/prompt/theme 路径发现

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 扩展可通过 `resources_discover` 在 startup/reload 后动态暴露 skill、prompt、theme 路径。Java 此前资源加载只来自固定 `ResourceLoader` 路径和 settings，扩展无法追加资源路径。

完成内容：

- `ExtensionPlugin` 新增 `ResourcesDiscoverResult` 和 `onResourcesDiscover(...)`：
  - 支持返回 `skillPaths`；
  - 支持返回 `promptPaths`；
  - 支持返回 `themePaths`；后续优化已将 theme path 接入 theme resource 加载主链路。
- `ExtensionRunner` 新增 `emitResourcesDiscover(...)`：
  - 按扩展加载顺序聚合三类路径；
  - 传入 `cwd` 和 `reason`（`startup` / `reload` 等）；
  - 沿用既有策略，扩展异常不会打断主流程。
- `ResourceLoader` 新增 `extendResources(...)`：
  - 追加扩展发现的 skill/prompt 路径；
  - 对路径做 cwd-relative 归一化和去重；
  - 有新增路径时自动 reload，使 skill/prompt 进入后续 system prompt 和 prompt template 链路。
- `AgentSessionServices.createAgentSessionFromServices(...)` 在构建 system prompt 前触发资源发现：
  - 使用轻量 `ExtensionCommandContext(cwd)`，适配 session 创建前的资源发现阶段；
  - startup 默认 reason 为 `startup`；
  - `Main` 和 `RuntimeHarness` 将 `CreateRuntimeOptions.reason()` 传入，用于 reload 场景。
- `ExtensionCommandContext` 支持无 session 的轻量上下文：
  - `cwd()` 可用；
  - `isIdle()` 返回 true；
  - `hasPendingMessages()` / `abortRequested()` 返回 false；
  - `abortSignal()` 为空；
  - `abort()` 在无 session 阶段为 no-op。
- 单测覆盖扩展发现资源：
  - 扩展返回临时 `SKILL.md`、prompt template 和 theme json 路径；
  - skill 被加载进 `ResourceLoader.skills()`；
  - prompt 被加载进 `ResourceLoader.prompts()` 并可通过 `PromptTemplateLoader.expandPromptTemplate(...)` 展开；
  - startup 和 reload reason 均被扩展观察到；
  - 多次触发不会重复加入同一个 prompt。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSessionServices.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/Main.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionPlugin.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/ResourceLoader.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest#extensionResourcesDiscoverAddsSkillAndPromptPaths -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。新增 `AgentSessionRuntimeTest` 1 个用例，0 failures，0 errors。

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 30 个测试、`CliEntryTest` 21 个测试、`CodingToolFactoryTest` 5 个测试、`SettingsManagerTest` 8 个测试，共 64 个测试，0 failures，0 errors。

当前限制：

- theme resource 主链路和行式主题应用已在后续优化接入；Java TUI 仍未提供 TS 版主题选择器、自动主题和全屏运行时主题能力。
- 资源发现运行在 session 创建前，轻量 context 不提供 session mutation、message queue 或 UI 能力。
- 当前仍是 Java JAR SPI 同步 hook，不是 TS/JS 动态扩展运行时。

### 优化 042：补齐 theme resource 加载主链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS ResourceLoader 会统一加载 skills、prompts、themes，并让扩展 `resources_discover` 返回的 `themePaths` 进入主题资源链路。Java 上一轮只聚合了 `themePaths`，没有加载和暴露 theme 资源。

完成内容：

- 新增 `ThemeResource` 和 `ThemeResourceLoader`：
  - 支持从用户目录 `agentDir/themes`、项目目录 `.pi/themes` 和额外路径加载 `.json` 主题；
  - 支持目录或单个 JSON 文件；
  - 校验主题必须是 JSON object 且包含非空 `name`；
  - 同名主题按加载顺序保留首个，并产生 `ResourceDiagnostic.Collision`；
  - 缺失路径、非 JSON 文件、解析失败和缺少名称会产生 warning 诊断。
- `ResourceLoader` 增加 theme 路径状态与 `themes()` 结果：
  - reload 时同步加载 theme resources；
  - `extendResources(...)` 支持追加 skill/prompt/theme 三类路径；
  - 保留旧的双参数 `extendResources(...)` 兼容已有调用。
- `AgentSessionServices.create(...)` 将 settings 中的 `skills`、`prompts`、`themes` 一并传入 `ResourceLoader`。
- `AgentSessionServices.createAgentSessionFromServices(...)` 对扩展 `resources_discover` 返回的 `themePaths` 进行追加和 reload。
- 单测覆盖：
  - 默认/额外 theme 目录加载、同名 collision、缺少 name warning；
  - `ResourceLoader` 同时聚合 skill、prompt、theme、context 和 prompt sources；
  - 扩展返回的 theme JSON 会进入 `services.resourceLoader().themes()`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/ThemeResource.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/ThemeResourceLoader.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/ResourceLoader.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSessionServices.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/resources/ResourceLoadingTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=ResourceLoadingTest#loadsThemesFromDefaultsAndAdditionalPathsWithDiagnostics,ResourceLoadingTest#resourceLoaderAggregatesSkillsPromptsContextAndPromptSources,AgentSessionRuntimeTest#extensionResourcesDiscoverAddsSkillAndPromptPaths -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。新增/更新 3 个目标用例，0 failures，0 errors。

当前限制：

- 本轮完成的是 theme resource 加载和暴露；后续优化已将 settings 选中的 JSON theme 应用到行式 Markdown/Diff 输出。
- 仍未补 TS 版主题选择器、自动明暗主题、主题热重载 watcher 和 HTML export 主题映射。

### 优化 043：将已加载 theme 应用到行式 Markdown/Diff 输出

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 theme JSON 会影响 Markdown、diff、代码高亮等交互渲染。Java 上一轮已经加载 theme resource，但 `TerminalTheme` 仍是固定样式，行式交互输出没有使用 settings 选中的主题。

完成内容：

- `TerminalTheme` 新增 token color 构造入口：
  - 可从 `mdHeading`、`mdQuote`、`mdHr`、`mdCodeBlock` 等 Markdown token 定制行式 Markdown 样式；
  - 可从 `syntaxKeyword`、`syntaxString`、`syntaxComment`、`syntaxNumber`、`syntaxPunctuation` 等 token 定制代码块高亮；
  - 可从 `toolDiffAdded`、`toolDiffRemoved`、`toolDiffContext`、`borderAccent`、`borderMuted` 等 token 定制 split diff 和分隔符样式；
  - 缺失 token 保持默认样式，避免主题 JSON 不完整时破坏输出。
- 新增 `TerminalThemeResolver`：
  - 从 `SettingsManager.getThemeSetting()` 解析当前主题名；
  - 对 `light/dark` 自动主题设置当前采用 dark 侧作为行式 fallback；
  - 从 `ResourceLoader.themes()` 查找同名 `ThemeResource`；
  - 支持 theme JSON `vars` 引用；
  - 将 hex / 256-color token 映射为 Java TUI 当前可用的 ANSI 基础色。
- `InteractiveOutputRenderer` 增加带 `TerminalTheme` 的渲染重载：
  - assistant Markdown；
  - tool start 的参数摘要和 edit/write 预览；
  - tool result 的 split diff 或折叠文本。
- `InteractiveModeRunner.executePrompt(...)` 每轮从 runtime services 解析当前主题，并传入 assistant/tool 输出渲染。
- 单测覆盖：
  - 自定义 theme JSON 的 `vars` 和 token color 会改变 Markdown heading 与 diff added 输出色；
  - settings 指定 `theme: custom` 且 `ResourceLoader` 已加载该 theme 时，行式 Markdown 输出使用该主题。

涉及文件：

- `packages/tui/src/main/java/works/earendil/pi/tui/style/TerminalTheme.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/TerminalThemeResolver.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveOutputRenderer.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/InteractiveOutputRendererTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=InteractiveOutputRendererTest#rendersAssistantMarkdownWithConfiguredTheme,InteractiveOutputRendererTest#resolvesConfiguredThemeFromLoadedResources -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。新增/更新 2 个目标用例，0 failures，0 errors。

当前限制：

- 本轮当时只支持 ANSI 基础色映射；后续优化 045 已补齐行式主题 truecolor / 256-color 输出精度。
- 主题已接入行式 Markdown/Diff 输出；后续优化已补行式 `/theme` 列表、预览和切换入口。但尚未覆盖全屏 TUI 组件、全屏主题选择器、自动明暗检测、主题 watcher 和 HTML export 映射。

### 优化 044：补齐行式 `/theme` 主题选择与预览入口

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版提供主题选择器和主题切换体验。Java 已加载并应用 theme JSON，但用户只能通过通用 `/settings set theme ...` 修改，缺少面向主题资源的交互入口。

完成内容：

- `SlashCommands` 新增内置 `/theme` 命令，并在 `/help` 中展示。
- `InteractiveModeRunner` 新增 `handleTheme(...)`：
  - `/theme` 或 `/theme list` 列出 `ResourceLoader.themes()` 中的已加载主题，并包含 Java 默认 `standard`；
  - `/theme current` 显示当前 settings theme、行式有效主题和来源；
  - `/theme <name>` / `/theme set <name>` 写入 `SettingsManager.setTheme(...)`，后续 assistant/tool 输出立即使用新主题；
  - `/theme preview <name>` 使用目标主题渲染 Markdown heading、代码块和 split diff 样例；
  - 对 `light/dark` 自动主题设置做基本校验，当前行式 fallback 使用 dark 侧；
  - 未知主题会返回可用主题列表。
- 交互端到端测试覆盖：
  - `/help` 展示 `/theme`；
  - `/theme current`、`/theme list`、`/theme preview ruby`、`/theme ruby`、`/theme missing`；
  - settings 中最终写入 `theme: ruby`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/SlashCommands.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/SlashCommandsTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=SlashCommandsTest,CliEntryTest#testInteractiveModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`SlashCommandsTest` 5 个测试、`CliEntryTest#testInteractiveModeRunnerExecution` 1 个测试，0 failures，0 errors。

当前限制：

- `/theme` 是行式选择/预览入口，不是 TS 版全屏 selector。
- 自动明暗主题仅保存并校验 `light/dark` 字符串；Java 行式渲染当前仍使用 dark 侧 fallback，不做终端背景探测。

### 优化 045：补齐行式主题 truecolor / 256-color 输出精度

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 theme JSON 的 hex 与 256-color token 会以较高色彩精度影响 Markdown、diff、代码高亮等交互渲染。Java 上一轮已经应用主题，但仍把颜色折算为 ANSI 基础色。

完成内容：

- `TerminalTheme` 内部样式从固定 `Ansi.Color` 扩展为 raw foreground SGR：
  - 保留 `fromTokenColors(Map<String, Ansi.Color>)` 兼容默认样式和已有调用；
  - 新增 `fromTokenSgr(Map<String, String>)`，允许主题资源直接传入 truecolor / 256-color 前景色序列；
  - Markdown line、代码 span、split diff 左右栏和分隔符样式都复用同一套 SGR 输出路径。
- `TerminalThemeResolver` 不再把 theme JSON 颜色折算到 8 色：
  - `#rrggbb` 输出 `38;2;r;g;b` truecolor SGR；
  - `0..255` 数字输出 `38;5;n` 256-color SGR；
  - 继续支持 `vars` 递归引用，并忽略空值、非法 hex、越界色号和循环引用。
- 单测覆盖：
  - 自定义 theme 的 `mdHeading` hex token 输出 truecolor；
  - `toolDiffAdded` 通过 `vars` 引用 hex 并输出 truecolor；
  - `syntaxKeyword` 数字 token 输出 256-color；
  - settings 指定已加载主题时，行式 Markdown 输出保留 truecolor。

涉及文件：

- `packages/tui/src/main/java/works/earendil/pi/tui/style/TerminalTheme.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/TerminalThemeResolver.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/InteractiveOutputRendererTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=InteractiveOutputRendererTest#rendersAssistantMarkdownWithConfiguredTheme,InteractiveOutputRendererTest#resolvesConfiguredThemeFromLoadedResources -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`InteractiveOutputRendererTest` 2 个目标测试，0 failures，0 errors。

当前限制：

- truecolor / 256-color 精度已覆盖行式 Markdown/Diff/代码 span 输出；全屏 TUI 组件、全屏主题选择器、自动明暗检测、主题 watcher 和 HTML export 映射仍未补齐。

### 优化 046：补齐行式 `/prompt` 模板发现、预览与执行入口

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 prompt templates 可作为资源被发现，并通过交互命令选择、展开和发送。Java 此前已有 `PromptTemplateLoader` 和展开函数，但行式交互层没有面向用户的模板入口，也没有把已加载模板暴露为 prompt slash command。

完成内容：

- `SlashCommands` 新增 prompt template 命令元数据：
  - `promptCommands(List<PromptTemplate>)` 将已加载模板暴露为 `SlashCommandSource.PROMPT`；
  - 内置命令表新增 `/prompt`。
- `InteractiveModeRunner` 新增行式 `/prompt`：
  - `/prompt` 或 `/prompt list` 列出已加载模板、参数 hint、描述和来源 scope；
  - `/prompt preview <name> [args]` 展示模板参数替换后的 prompt；
  - `/prompt run <name> [args]` 展开模板并发送给 agent；
  - `/prompt <name> [args]` 作为 `run` 的快捷形式。
- 未被内置命令、扩展命令和 skill command 捕获的普通 slash 输入，在发送给 agent 前会经过 `PromptTemplateLoader.expandPromptTemplate(...)`；因此 `/fix file bug` 这类模板 slash 能直接展开为真实用户 prompt。
- `/help` 会展示 `/prompt`，并列出已加载 prompt template slash 入口。
- 交互端到端测试覆盖：
  - `/help` 展示 `/prompt` 和已加载 `/fix` 模板；
  - `/prompt list`、`/prompt preview fix ...`、`/prompt run fix ...`；
  - 直接 `/fix direct.java direct-bug` 写入 session 的是展开后的 prompt，而不是原始 slash。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/SlashCommands.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/SlashCommandsTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=SlashCommandsTest,CliEntryTest#testInteractiveModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`SlashCommandsTest` 6 个测试、`CliEntryTest#testInteractiveModeRunnerExecution` 1 个测试，0 failures，0 errors。

当前限制：

- `/prompt` 是行式 list/preview/run 入口，不是 TS 版全屏 prompt template selector。
- 模板参数继续沿用现有 `PromptTemplateLoader` 的基础 shell-like 引号解析；尚未补更复杂的全屏表单输入或模板变量 UI。

### 优化 047：补齐 HTML export skill/custom block 与 XSS 回归

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2/P5/P10 项：TS 版 HTML export 对 skill block、工具块、XSS 和空白处理有专项回归。Java 此前 HTML exporter 主要是基础消息列表，非 message 结构会被忽略，skill command 产生的 `<skill>...</skill>` wrapper 会作为普通文本显示。

完成内容：

- `HtmlExporter` 增强动态字段安全：
  - role badge、tool 名称、tool input/result、custom type 和 custom payload 均经过 HTML escape；
  - 对未知 content block 输出安全 details，避免静默丢失结构化内容。
- user message 中的 skill wrapper 会被拆成两个块：
  - `skill-invocation` details 展示 skill 名称、位置和 skill 内容；
  - 用户原始 prompt 作为独立 `user-message` 文本块展示，不再把 `<skill ...>` XML wrapper 当作用户正文。
- 非 message session entry 支持基础导出：
  - `custom` / `custom_message` 被渲染为 `custom-entry` details；
  - skill diagnostics 等自定义结构不再在 HTML export 中完全消失。
- 新增 `HtmlExporterTest`：
  - 覆盖恶意 role、tool name、tool result、custom type 和 custom payload 的 XSS escape；
  - 覆盖 skill wrapper 拆分和 wrapper 标签隐藏；
  - 覆盖 custom `skill_trigger_diagnostics` 安全呈现。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/export/HtmlExporter.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/export/HtmlExporterTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=HtmlExporterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`HtmlExporterTest` 1 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 Java 基础 exporter 的结构化块和安全回归；仍不是 TS 版完整高保真 viewer，不含 markdown 渲染器、侧边栏树、主题导出映射和完整 tool renderer。

### 优化 048：补齐 HTML export 图片内容安全渲染

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2/P5/P8/P10 项：TS 版导出链路覆盖图片内容与 XSS 回归。Java 已有 `Content.Image` 等模型骨架，但此前 HTML exporter 对图片 content block 只会落入未知 JSON details，无法在导出视图中直接呈现图片。

完成内容：

- `HtmlExporter` 新增图片 content block 渲染：
  - inline `data` 图片渲染为 `<figure class='content-image'>`，图片 `src` 使用 `data:<safe bitmap mime>;base64,...`；
  - URL 图片仅允许 `http://` / `https://`，并对属性值进行 HTML escape；
  - 支持 `image/png`、`image/jpeg`、`image/jpg`、`image/gif`、`image/webp`、`image/bmp` 等常见 bitmap MIME；
  - 不安全或不支持的 inline MIME、URL scheme、缺失 data/url 的图片块会渲染为 `Image omitted` placeholder details，而不是输出可执行或不可控 HTML。
- `HtmlExporterTest` 扩展图片安全回归：
  - 覆盖 inline png 正常导出；
  - 覆盖带引号和 `onerror` 片段的 https URL 被安全 escape；
  - 覆盖 `image/svg+xml` inline data 被拒绝；
  - 覆盖 `javascript:` URL 被拒绝。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/export/HtmlExporter.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/export/HtmlExporterTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=HtmlExporterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`HtmlExporterTest` 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 session 中已持久化图片 content 的 HTML export 呈现；仍未补 TS 版交互剪贴板图片粘贴、图片缩放/转换、图像生成 API、terminal graphics 和完整高保真 viewer。

### 优化 049：补齐 OpenRouter 图像生成 API 基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2/P8 项：TS 版 `packages/ai` 提供 `generateImages()`、image API registry 和 OpenRouter image generation provider。Java 此前只有 `ImageGenModel` 数据骨架，没有可注册、可调用、可测试的图像生成 provider 链路。

完成内容：

- `pi-ai` 新增图像生成 provider 抽象：
  - `ImageGenerationProvider` 定义 provider id、图片模型列表、刷新入口和 `generateImages(...)`；
  - `ImageGenerationRegistry` 支持注册默认 provider、查找 provider/model，并按 model provider 分发生成请求；
  - `ImageGenerationOptions` 提供 API key、headers、timeout、retry、env、metadata 和图片 provider hook 入口。
- 新增 `OpenRouterImagesProvider`：
  - 注册 `openrouter` 图像生成 provider 和基础 OpenRouter image models；
  - 构造 OpenRouter chat completions image 请求：`stream=false`、`modalities=["image","text"]`、用户文本 prompt content；
  - 支持 request 的 `n`、`aspectRatio`、`resolution` 和额外 options 合并进 payload；
  - 复用现有 `ProviderHttpSupport` 的 HTTP client、retry、timeout 和并发限制；
  - 解析 OpenRouter `message.images[].image_url`，支持 `data:<mime>;base64,...` 和远端 URL 两种输出为 `ImageGenModel.GeneratedImage`；
  - 支持 payload/response hook 便于后续接入扩展 provider 请求事件。
- 新增 `ImageGenerationProvidersTest`：
  - 覆盖默认 registry 包含 OpenRouter image provider 和模型；
  - 覆盖请求体构造、payload options 合并和 image response 解析；
  - 用本地 HTTP server 覆盖真实 `generateImages(...)` 请求、Authorization header 和 base64 图片结果，不依赖真实 OpenRouter 网络或 API key。

涉及文件：

- `packages/ai/src/main/java/works/earendil/pi/ai/provider/ImageGenerationProvider.java`
- `packages/ai/src/main/java/works/earendil/pi/ai/provider/ImageGenerationRegistry.java`
- `packages/ai/src/main/java/works/earendil/pi/ai/provider/ImageGenerationOptions.java`
- `packages/ai/src/main/java/works/earendil/pi/ai/provider/OpenRouterImagesProvider.java`
- `packages/ai/src/test/java/works/earendil/pi/ai/provider/ImageGenerationProvidersTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/ai -am -Dtest=ImageGenerationProvidersTest,BuiltinProvidersTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`ImageGenerationProvidersTest` 3 个测试、`BuiltinProvidersTest` 15 个测试，共 18 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 `pi-ai` 层 OpenRouter 图像生成基础链路；尚未接入 coding-agent 交互命令、工具、SDK 文档或扩展动态 provider 注册。
- 仍未补 TS 版剪贴板图片粘贴、图片缩放/转换、terminal graphics、多 provider image catalog 自动刷新和完整 `generateImages()` 兼容 facade。

### 优化 050：补齐 read tool 图片附件与图片 content 保真

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2/P8/P12 项：TS 版 read tool 支持读取 jpg/png/gif/webp/bmp 等图片并返回 `ImageContent`，同时 `images.blockImages` 可在 LLM 转换层阻断图片进入 provider。Java 此前已有 `Content.Image` 与 MIME 检测工具，但 read tool 只按 UTF-8 文本读取文件，session 恢复链路也会丢失 image/thinking/toolCall 等结构化 content block。

完成内容：

- `ReadTool` / `CodingToolFactory.read` 支持图片附件：
  - 使用 `MimeUtils.detectSupportedImageMimeTypeFromFile(...)` 识别受支持图片；
  - 图片文件返回 text note + `Content.Image(mimeType, base64Data, null)`；
  - details 中记录 path、mimeType、bytes 和 image 标记；
  - tool 描述补充图片支持范围。
- `CodingAgentMessages.convertToLlm(...)` 新增 `blockImages` 过滤入口：
  - `images.blockImages=true` 时移除 user/assistant/tool result 中的 `Content.Image`；
  - 在被过滤位置追加文本占位，避免上下文静默丢失图片存在的信息。
- `AgentSession` 将 `SettingsManager.getBlockImages()` 接入当前 turn 的 `convertToLlm`。
- session 恢复链路补齐结构化 content：
  - `AgentSession` 本地 parser 支持 text、thinking、image、toolCall；
  - `CompactionSupport.buildSessionContext(...)` 使用的 parser 同步支持 image 与 toolCall displayContent，避免恢复历史上下文时丢失结构块。
- 测试覆盖：
  - read tool 读取 PNG 时返回 `Content.Image`；
  - `convertToLlm(..., true)` 将图片替换为 blockImages 占位文本；
  - session JSON 恢复时保留 user image、assistant thinking 和 assistant toolCall/displayContent。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/tools/ReadTool.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/tools/CodingToolFactory.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/CodingAgentMessages.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSessionServices.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/CompactionSupport.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/tools/CodingToolFactoryTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/CodingAgentMessagesTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CodingToolFactoryTest,CodingAgentMessagesTest,AgentSessionRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CodingToolFactoryTest` 6 个测试、`CodingAgentMessagesTest` 4 个测试、`AgentSessionRuntimeTest` 31 个测试，共 41 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 read tool 图片附件、session content 保真和 blockImages 过滤；后续优化 051 已补 read tool 基础 resize/BMP 转换，但仍未补 TS 版完整图片处理、剪贴板图片粘贴、terminal graphics、print/interactive 初始图片参数或完整 provider tool-result 图片协议。

### 优化 051：补齐 read tool 图片 autoResize / BMP 转 PNG 基础处理

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2/P8 项：TS 版 `processImage(...)` 会对图片做 MIME 归一、转换和 resize，并受 `images.autoResize` 控制。Java 在优化 050 后已能把图片作为 `Content.Image` 返回，但仍只是原样 base64，没有把 `images.autoResize` 接到 read tool，也没有基础转换/尺寸提示。

完成内容：

- 新增 `ImageProcessor`：
  - 使用 JDK `ImageIO` 对 PNG/JPEG/BMP 做基础解码；
  - 默认最大边 2000px，超限时按比例缩放后输出 PNG；
  - BMP 即使关闭 autoResize 也会转换为 PNG，避免 provider inline 图片收到不兼容格式；
  - 输出 TS 风格的转换提示和尺寸映射提示，帮助模型把缩放图坐标映射回原图；
  - GIF/WebP 暂保持原样，不做破坏性重编码。
- `ReadTool` 接入图片处理结果：
  - 图片 note 会附带转换/缩放 hints；
  - details 记录 originalMimeType、原始/处理后 byte 数、autoResizeImages、原始尺寸和显示尺寸；
  - 处理失败时返回明确图片省略文本，不返回损坏图片 content。
- `CodingToolFactory` 新增 `read(Path, boolean)`、`createAllTools(..., boolean autoResizeImages)` 等重载，默认仍保持 autoResize 开启。
- `AgentSessionServices.resolveTools(...)` 将 `SettingsManager.getImageAutoResize()` 传入内置工具创建链路。
- 测试覆盖：
  - 默认 read tool 对 2101px 宽 PNG 缩放到 2000px 并输出尺寸提示；
  - `autoResize=false` 时大 PNG 原样返回；
  - BMP 被转换为 PNG，并保留转换 hint 和 details。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/util/ImageProcessor.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/tools/ReadTool.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/tools/CodingToolFactory.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSessionServices.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/tools/CodingToolFactoryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CodingToolFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CodingToolFactoryTest` 共 9 个测试，0 failures，0 errors。

当前限制：

- 本轮是 JDK ImageIO 可覆盖的保守子集；尚未迁移 TS Photon/WASM 的大小压缩策略、EXIF orientation、WebP/GIF resize、剪贴板图片粘贴、terminal graphics、print/interactive 初始图片参数或完整 provider tool-result 图片协议。

### 优化 052：补齐 CLI 初始 `@file` / `@image` 图片附件链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2/P8 项：TS 版启动参数支持 `@file` 注入文本上下文，`@image` 会处理为首轮 prompt 的图片附件，并复用 `images.autoResize`。Java 此前只有一个简单的 `@file` 文本替换逻辑，无法把图片作为初始 user message 的 `Content.Image` 传入 LLM。

完成内容：

- 新增 `FileArgumentProcessor`：
  - 扫描 CLI messages 中的 `@...` 参数；
  - 文本文件转换为 TS 风格 `<file name="...">...</file>` 上下文；
  - 图片文件复用 `MimeUtils` + `ImageProcessor`，生成首轮 `Content.Image` 附件；
  - 图片转换/缩放 hints 写入对应 `<file>` 标签；
  - 空文件跳过，缺失文件或目录给出明确错误；
  - 所有文件上下文和第一条普通 prompt 合并为首轮消息，剩余普通 prompt 继续作为后续消息。
- `Main` 关闭 picocli at-file expansion，避免 `@image.png` 被 picocli 当作参数文件展开。
- `CliArgs` 增加非 CLI 参数字段 `initialImages`，用于携带首轮图片附件。
- `AgentSession` 新增 `prompt(String, List<Content.Image>)`，user message 可同时包含文本和图片 content block。
- `PrintModeRunner` 首轮 prompt 会附带 `initialImages`，后续 prompt 保持纯文本。
- 测试覆盖：
  - `@note.txt`、`@tiny.png` 和普通 prompt 合并为首轮消息，图片进入 `initialImages`；
  - print mode 首轮 prompt 携带图片进入 LLM context，第二轮 prompt 不重复携带图片。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/FileArgumentProcessor.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/CliArgs.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/Main.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/PrintModeRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/FileArgumentProcessorTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=FileArgumentProcessorTest,CliEntryTest,CodingToolFactoryTest,AgentSessionRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`FileArgumentProcessorTest` 2 个测试、`CliEntryTest` 21 个测试、`CodingToolFactoryTest` 9 个测试、`AgentSessionRuntimeTest` 31 个测试，共 63 个测试，0 failures，0 errors。

当前限制：

- 本轮补齐的是 print/启动参数首轮图片附件链路；Java 行式 interactive 仍没有 TS 全屏模式里的剪贴板图片粘贴、编辑器内附件管理、terminal graphics inline 渲染或多协议图片展示。

### 优化 053：补齐行式 `/paste-image` 剪贴板图片入口

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2/P8 项：TS 全屏交互模式支持从剪贴板粘贴图片，保存成临时图片文件并插入到编辑器中，后续由 `@image` 文件参数进入 prompt。Java 已有 `app.clipboard.pasteImage` keybinding 声明和 `/copy` 文本剪贴板能力，但行式交互没有可用的剪贴板图片入口。

完成内容：

- `InteractiveModeRunner` 新增行式 `/paste-image [path]`：
  - 从系统 clipboard 读取图片；
  - 支持 AWT `imageFlavor`，读取后编码为 PNG；
  - 支持 clipboard 中的图片文件列表，读取第一个 `MimeUtils` 支持的图片文件；
  - 未传 path 时保存到临时目录；传目录时在目录下生成随机图片名；传无扩展路径时自动补图片扩展；
  - 输出保存路径和可直接复制提交的 `@path`，与优化 052 的 CLI `@image` 链路衔接；
  - headless 或无图片时返回明确错误。
- 新增 `ClipboardImageReader` 测试注入点，避免单测依赖真实系统剪贴板。
- `SlashCommands` 注册 `paste-image` 内置命令，help 输出同步补充 `/paste-image [path]`。
- 测试覆盖：
  - 交互大回归中注入假剪贴板图片，执行 `/paste-image pasted-clip` 后生成 `pasted-clip.png`；
  - 输出包含 `Clipboard image`、`status: saved`、`mimeType`、`submit: @...`；
  - help 文案和 slash command 固定顺序包含 `paste-image`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/SlashCommands.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/SlashCommandsTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest,SlashCommandsTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 21 个测试、`SlashCommandsTest` 6 个测试，共 27 个测试，0 failures，0 errors。

当前限制：

- 本轮是 Java 行式 REPL 的基础剪贴板图片入口；尚未迁移 TS 全屏编辑器里的快捷键触发、附件管理、Wayland/xclip/WSL PowerShell 多后端、terminal graphics inline 预览或多协议图片展示。

### 优化 054：补齐行式 `/image` 图像生成入口

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2/P8 项：TS 版 `packages/ai` 提供 `generateImages()` facade，并在 coding-agent 交互侧有图像生成相关入口。Java 在优化 049 已补 `pi-ai` 层 provider / registry / OpenRouter HTTP 链路，但 coding-agent 行式交互仍没有可用的图像生成命令。

完成内容：

- `InteractiveModeRunner` 新增行式 `/image` / `/images` 命令：
  - `/image` 或 `/image list` 列出当前 `ImageGenerationRegistry` 中可用 provider/model，并显示支持的 aspect ratio 和 size；
  - `/image generate --model <provider/model> [--out <path>] [--aspect <ratio>] [--size <size>] [--n <count>] <prompt>` 调用图像生成 provider；
  - 未指定模型时默认使用 registry 的第一个图像模型，裸 model id 在唯一匹配时可省略 provider；
  - 从 `AuthStorage` 读取目标 provider 的 API key，缺失时提示 `/login <provider> <api-key>`；
  - 对 base64 image 结果写入当前工作目录、指定目录或指定文件，远端 URL 结果在输出中列出；
  - 输出 status、model、prompt、files、urls、revisedPrompt 和图片数量，便于行式 REPL 中继续引用生成文件。
- `SlashCommands` 注册 `image` 内置命令，help 输出同步补充 `/image [list|generate]`。
- 新增 `ImageCommandTest`：
  - 覆盖模型列表输出；
  - 覆盖带 `--model`、`--out`、`--aspect`、`--size`、`--n` 的生成调用、API key 传递、base64 文件落盘和 URL 输出；
  - 覆盖 provider API key 缺失时的错误提示。
- `SlashCommandsTest` 固定内置 slash command 顺序，包含 `image`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/SlashCommands.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/ImageCommandTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/SlashCommandsTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=ImageCommandTest,CliEntryTest,SlashCommandsTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`ImageCommandTest` 3 个测试、`CliEntryTest` 21 个测试、`SlashCommandsTest` 6 个测试，共 30 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 Java 行式 REPL 的基础图像生成入口；尚未迁移 TS 全屏 image selector、生成结果预览、附件管理、terminal graphics inline 渲染、多 provider catalog 自动刷新或扩展动态 image provider 注册。

### 优化 055：补齐 package resource discovery 基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package manager 支持 `package.json` 中的 `pi` manifest、conventional resource directories 和 glob / `!` 过滤，把安装包里的 extensions、skills、prompts、themes 纳入资源生态。Java 此前 `PackageManager` 只把本地目录/文件复制到安装目录或 clone git repo，`ResourceLoader` 不会扫描已安装包内的资源。

完成内容：

- 新增 `PackageResourceResolver`：
  - 扫描全局 `agentDir/packages/*`；
  - 项目可信时扫描当前项目 `.pi/packages/*`，不可信项目不会加载项目包资源；
  - 读取 package root 下 `package.json` 的 `pi` manifest；
  - 支持 `skills`、`prompts`、`themes` 和预留的 `extensions` 四类 key；
  - manifest 缺失时按 conventional dirs 发现 `skills/`、`prompts/`、`themes/`、`extensions/`；
  - manifest 数组支持精确路径、glob、`!pattern` 排除、`+path` 强制包含和 `-path` 精确排除；
  - 防止 manifest 路径越出 package root。
- `ResourceLoader.reload()` 在默认资源加载时合并 package resources：
  - skills 接入现有 `SkillLoader`；
  - prompts 接入现有 `PromptTemplateLoader`；
  - themes 接入现有 `ThemeResourceLoader`；
  - `includeDefaults=false` 时不加载 package resources，保持显式加载模式语义。
- 测试覆盖：
  - package manifest 中 skills/prompts/themes 的加载；
  - `skills/*.md` + `!skills/private.md` 过滤；
  - conventional package dirs 自动发现；
  - 项目 `.pi/packages` 只在 trusted project 下加载。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/PackageResourceResolver.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/ResourceLoader.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/resources/ResourceLoadingTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`ResourceLoadingTest` 13 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是已安装目录中的 package resource discovery；尚未补 TS 版 npm source 解析、semver/range、依赖安装、git pinned ref reconcile、settings packages 持久化/过滤、`pi config` 资源启停或 TS/JS 动态扩展运行时。`extensions` 路径解析已预留，但 Java 当前扩展加载仍只执行 JAR extension。

### 优化 056：补齐 `pi install/remove` 的 settings packages 持久化

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package manager 的 `installAndPersist()` / `removeAndPersist()` 会把 package source 加入或移出 global/project settings 的 `packages` 数组。Java 此前 `pi install` 只复制/clone 到安装目录，`pi remove` 只删除目录，不会留下可复现的 settings package source。

完成内容：

- `SettingsManager` 新增 `setPackages(...)`，用于写入 global settings `packages` 数组，和已有 `setProjectPackages(...)` 对齐。
- `PackageManager` 新增可注入 `agentDir` 的 `install/remove/list/update` 重载，便于测试和后续非默认 agent dir 场景复用。
- `PackageManager` 新增 settings source 操作：
  - `installAndPersist(...)`：先安装，再把 source 写入 global/project settings；
  - `removeAndPersist(...)`：先移除目录，再从 global/project settings 移除 source；
  - `addSourceToSettings(...)`：字符串 source 精确去重，已存在时不重复写入；
  - `removeSourceFromSettings(...)`：同时支持移除字符串 package entry 和对象 package entry 的 `source`。
- `PackageManagerCli` 的 `install` / `remove` / `uninstall` 改走 persist 路径：
  - 默认写 global `~/.pi/agent/settings.json`；
  - `-l` / `--local` 写当前项目 `.pi/settings.json`。
- 新增 `PackageManagerTest`：
  - 覆盖 global install 写入 package dir 和 global settings，重复安装不重复追加；
  - 覆盖 global remove 删除 package dir 并移除 settings source；
  - 覆盖 local install/remove 写项目 `.pi/packages` 和 `.pi/settings.json`；
  - 覆盖对象形式 `{ "source": "..." }` 的 packages entry 可被去重/移除。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/config/SettingsManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,SettingsManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 3 个测试、`SettingsManagerTest` 8 个测试、`ResourceLoadingTest` 13 个测试，共 24 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 source 的 settings 持久化和基础去重/移除；尚未补 TS 版 npm source 解析、semver/range、依赖安装、git URL 规范化和 pinned ref reconcile，也尚未把 settings object package filters 接入 `PackageResourceResolver` 的加载决策。

### 优化 057：补齐 settings package filters 与资源加载联动

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 settings `packages` 支持对象形式 `{ source, skills, prompts, themes }`，用来启停或缩小已安装 package 暴露的资源。Java 在优化 055/056 后已经能发现安装目录、读取 manifest 并持久化 package source，但对象 filters 仍不会影响实际资源加载。

完成内容：

- `PackageResourceResolver` 新增 settings package filters 支持：
  - 接受 settings `packages` entries；
  - 对对象形式 package entry 读取 `source`、`extensions`、`skills`、`prompts`、`themes`；
  - 通过安装目录名、settings source 派生名或 package.json `name` 匹配 package root；
  - filter key 省略时加载该类型的所有 manifest/conventional 允许资源；
  - filter key 为 `[]` 时禁用该类型资源；
  - filter 数组支持精确路径、glob、`!pattern` 排除、`+path` 强制包含和 `-path` 精确排除；
  - filters 叠在 package manifest / conventional dirs 允许结果之上，不越权加载 manifest 未允许的资源。
- `ResourceLoader` 新增 package entries 字段和构造器：
  - 默认构造器保持兼容；
  - 默认 reload 时将 package entries 传给 `PackageResourceResolver`；
  - `includeDefaults=false` 继续不加载 package resources。
- `AgentSessionServices.create(...)` 将 `settingsManager.getPackages()` 传入 `ResourceLoader`，使 CLI/交互会话启动时 settings package filters 生效。
- `ResourceLoadingTest` 新增覆盖：
  - package manifest 暴露多项 skills/prompts/themes；
  - settings object filters 只允许指定 skill/theme；
  - `prompts: []` 禁用该 package 的 prompt templates。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/PackageResourceResolver.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/ResourceLoader.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSessionServices.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/resources/ResourceLoadingTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=ResourceLoadingTest,PackageManagerTest,AgentSessionRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`ResourceLoadingTest` 14 个测试、`PackageManagerTest` 3 个测试、`AgentSessionRuntimeTest` 31 个测试，共 48 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 settings object filters 与已安装 package resource discovery 的联动；仍未补 `pi config` 的交互式启停 UI/CLI、npm source 解析、semver/range、依赖安装、git URL 规范化和 pinned ref reconcile。

### 优化 058：补齐基础 `pi config` 资源启停命令

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi config` 可在交互选择器中启停 package resource，并把选择写回 settings package filters。Java 在优化 057 后已经能读取 filters，但还没有用户入口修改这些 filters。

完成内容：

- `Main` 将顶层 `pi config` 分发给 package manager CLI。
- `PackageManagerCli` 新增基础行式命令：
  - `pi config list [-l]`：列出 global/project settings 中配置的 package source 及 filters；
  - `pi config enable <source> <extensions|skills|prompts|themes> <path> [-l]`：把指定资源路径写成 `+path` filter；
  - `pi config disable <source> <extensions|skills|prompts|themes> <path> [-l]`：把指定资源路径写成 `-path` filter。
- `PackageManager` 新增 `configurePackageResource(...)` 和 `listConfiguredPackages(...)`：
  - 支持字符串形式 package entry 自动升级为 `{ "source": "..." }` 对象；
  - 写入前会移除同一路径已有的 `!` / `+` / `-` filter，避免 enable/disable 反复叠加冲突项；
  - 保持 `-l` / `--local` 与 `install/remove` 一致，默认写 global settings，local 写项目 `.pi/settings.json`；
  - 对未配置的 package source 返回明确的 `status: not found`，不隐式新增未知 source。
- `PackageManagerTest` 新增覆盖：
  - disable 后写入 `-skills/private.md`；
  - enable 同一路径会替换为 `+skills/private.md`；
  - 字符串 package entry 会转换为对象 entry；
  - list 输出包含 source 和 filters；
  - 未找到 source 时不会创建 packages 配置。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/Main.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 6 个测试、`ResourceLoadingTest` 14 个测试、`CliEntryTest` 21 个测试，共 41 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是基础行式 `pi config`，还不是 TS 版全屏 selector/search UI。
- 只能修改 settings 中已配置 package source 的 `extensions` / `skills` / `prompts` / `themes` filters，不会扫描资源后提供可视化选择列表。
- 尚未补 top-level 非 package resources 的启停入口。
- 仍未补 npm source 解析、semver/range、依赖安装、完整 git update 语义和 package identity/dedupe 细节。

### 优化 059：补齐 git package source 与 pinned ref 基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package manager 支持 `git:` shorthand、protocol git URL、`@ref` pinned checkout，以及安装后把 git package resources 纳入资源加载。Java 此前只把部分 URL 当作 git clone，且落在 `packages/` 扁平目录下，不支持 `git:github.com/user/repo@v1` 这类 TS 文档入口。

完成内容：

- `PackageManager` 新增 git source 解析：
  - 支持 `git:github.com/user/repo@ref`；
  - 支持 `git:git@github.com:user/repo@ref`；
  - 支持 `https://` / `http://` / `ssh://` / `git://` protocol URL 后缀 `@ref`；
  - 保留既有 `git@host:path` 兼容；
  - 额外支持 `file://` git URL，便于本地/测试仓库使用。
- git package 安装目录改为 TS 风格的 global/project git root：
  - global：`<agentDir>/git/<host>/<path>`；
  - project：`<cwd>/.pi/git/<host>/<path>`。
- 已存在 git clone 时：
  - 有 pinned ref：`git fetch origin <ref>` 后 `reset --hard FETCH_HEAD` 并 `clean -fdx`；
  - 无 pinned ref：继续执行 `git pull`。
- settings source 匹配支持 git identity：
  - `addSourceToSettings(...)` 对同一 `host/path` 忽略 ref 匹配；
  - 安装 `git:...@new-ref` 会替换旧 source，而不是追加重复 package entry；
  - `removeSourceFromSettings(...)` 和 `configurePackageResource(...)` 也按同一 git identity 匹配。
- `PackageResourceResolver` 会扫描 global/project `git/` 嵌套安装目录中的 git package root，使 git package 的 `package.json#pi` / conventional resources 能进入资源加载。
- `PackageManagerTest` 新增覆盖：
  - 从本地 file git URL 安装 pinned `v1`；
  - 校验 checkout 内容为 `v1`；
  - 校验 git package skills 被 `PackageResourceResolver` 发现；
  - 再安装同 repo 的 `v2`，校验已有 clone 被 fetch/reset 到新 ref；
  - 校验 settings `packages` source 从 `@v1` 替换为 `@v2`，不会重复追加。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/PackageResourceResolver.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 7 个测试、`ResourceLoadingTest` 14 个测试，共 21 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 git package source 的基础安装、ref reconcile 和资源发现；尚未补完整 update 命令语义、并发更新、remote HEAD 比较、进度事件和离线模式。
- git package 中的 `package.json` dependencies 尚未按 TS 策略执行安装；npm source 基础链路已在优化 060 补齐，但 semver/range/update 细节仍待补。
- 尚未补 TS 版完整 package identity/dedupe 在 resource resolution 中“project wins over global”的全部细节。

### 优化 060：补齐 npm package source 与 `npmCommand` 基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package manager 支持 `npm:<package>[@version]` source、global/project npm 安装根、`npmCommand` wrapper，以及从 `node_modules` 中加载 package resources。Java 此前已经补了 settings 持久化和 git package，但 `npm:` source 仍会被当作本地路径失败。

完成内容：

- `SettingsManager` 新增 `getNpmCommand()`，读取 merged settings 中的 `npmCommand` 字符串数组。
- `PackageManager` 新增 npm source 解析：
  - 支持 `npm:pkg`；
  - 支持 `npm:pkg@1.0.0`；
  - 支持 `npm:@scope/pkg`；
  - 支持 `npm:@scope/pkg@1.0.0`。
- npm package 安装目录采用 TS 风格 global/project npm root：
  - global：`<agentDir>/npm/node_modules/<name>`；
  - project：`<cwd>/.pi/npm/node_modules/<name>`。
- `installAndPersist(...)` 对 npm source 会：
  - 初始化 npm root 下的 private `package.json`；
  - 使用 `settings.npmCommand` 或默认 `npm` 执行 `install <spec> --prefix <root> --legacy-peer-deps`；
  - 按 npm package name 去重 settings source，安装同名新版本时替换旧 source。
- `removeAndPersist(...)` 对 npm source 会：
  - 使用 `settings.npmCommand` 或默认 `npm` 执行 `uninstall <name> --prefix <root>`；
  - 清理残留 package dir；
  - 按 npm package name 移除 settings source。
- `PackageResourceResolver` 会扫描 global/project `npm/node_modules`：
  - 支持普通包；
  - 支持 scoped 包；
  - npm package 的 `package.json#pi` / conventional resources 会进入资源加载。
- `PackageManagerTest` 新增 fake npm command 覆盖：
  - `npm:@scope/review-pack@1.0.0` 安装；
  - `settings.npmCommand` 被用于安装；
  - npm package skills 被 `PackageResourceResolver` 发现；
  - 安装同名 `@2.0.0` 会替换 settings source；
  - remove 会删除 node_modules 包目录并移除 settings source。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/config/SettingsManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/PackageResourceResolver.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 8 个测试、`ResourceLoadingTest` 14 个测试、`SettingsManagerTest` 8 个测试，共 30 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 npm source 的基础 install/remove 和资源发现；尚未补 npm registry 查询、semver/range 选择、update 时跳过 pinned exact version、批量更新、离线模式和进度事件。
- npm/git package 内部 dependencies 的安装策略仍是基础 CLI 行为；尚未完整迁移 TS 版 dependency/bundledDependencies 说明中的全部加载语义。
- 尚未补完整 package identity/dedupe 在 resource resolution 中“project wins over global”的全部细节。

### 优化 061：补齐 settings 驱动的 package update 基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package update 以 settings `packages` 为权威，能更新 npm/git package，并对 pinned git ref 做 reconcile。Java 此前 `PackageManager.update(...)` 仍按旧 installed-dir 逻辑扫 `packages/` 目录，既不会读取 settings source，也不会使用 `npmCommand` 更新 npm package。

完成内容：

- `PackageManager` 新增带 `SettingsManager` 的 `update(...)` 重载。
- `PackageManagerCli update` 改为复用同一个 `SettingsManager`，因此 update 可以读取 global/project settings packages 和 `npmCommand`。
- `update all` 现在会从当前 scope 的 settings `packages` 收集 configured sources：
  - npm source：调用既有 npm install 链路更新 unpinned package，并复用 `npmCommand`；
  - pinned npm exact version：跳过，不偷偷移动用户锁定版本；
  - git source：调用既有 git install/update 链路，对 pinned ref 执行 fetch/reset/clean reconcile；
  - local source：跳过，保持 TS 版 package update 不处理本地路径的语义。
- `update <source>` 会按 npm package name 或 git `host/path` identity 匹配 settings 中已有 source；找不到时输出当前 configured packages，方便用户确认 source 或 scope。
- `PackageManagerTest` 新增覆盖：
  - `update all` 更新 unpinned npm package，并跳过 pinned exact npm version；
  - `update <source>` 按 npm identity 匹配 configured source，只更新目标 package；
  - 未匹配 source 时输出 configured package 列表；
  - settings source 从 git `@v1` 改为 `@v2` 后，`update all` 会 reconcile 既有 clone 到 pinned `v2`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 11 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 package update 的 settings-driven 基础链路；尚未补 npm registry `view` 查询、semver/range 最新版本选择、批量 npm install、update 可用性检查、离线模式、进度事件和并发治理。
- `pi update` 的 self-update / `--extensions` / `--all` TS CLI flag 语义仍未完整迁移；Java 当前 package CLI 仍是基础子命令形态。
- git package update 仍缺 remote HEAD 比较和 package 内部 dependencies 安装策略；pinned ref reconcile 已接通。

### 优化 062：补齐 git package dependencies 基础安装链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 git package clone / update 后会在 package root 存在 `package.json` 时运行 npm install，使 git package 内声明的 runtime dependencies 可用。Java 此前只 clone/fetch/reset/clean，不安装 git package 自身依赖。

完成内容：

- `PackageManager.install(...)` 对 git source 现在会把 `SettingsManager` 传入 git 安装链路，使 git package dependency install 能复用 settings `npmCommand`。
- `installGit(...)` 在以下场景完成 git checkout 后检测 package root `package.json`：
  - 首次 clone；
  - pinned ref fetch/reset/clean；
  - unpinned `git pull`。
- 若存在 `package.json`，执行 dependency install：
  - settings 中配置了 `npmCommand`：运行 `<npmCommand...> install`；
  - 未配置 `npmCommand`：运行默认 `npm install --omit=dev`，对齐 TS 版默认跳过 dev dependencies 的策略。
- git install/update 输出在执行 dependency install 后会追加 `Installed git package dependencies in <path>`，便于 CLI 用户和测试识别实际发生的 dependency step。
- `PackageManagerTest` 的 fake npm command 增强为同时支持 managed npm package `--prefix` 安装和 git package root 内无 `--prefix` 的 dependency install。
- 既有 git package 测试改为注入 fake `npmCommand`，并断言：
  - 首次安装 pinned git package 后生成 dependency install marker；
  - 从 `@v1` reconcile 到 `@v2` 后 marker 会在 `git clean -fdx` 后重新生成；
  - settings-driven `update all` 对 pinned git ref reconcile 后同样执行 dependency install。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 11 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 git package dependencies 的基础安装；尚未补包管理器识别（pnpm/bun/yarn）下的完整参数差异、锁文件策略、离线模式、进度事件和安装失败恢复策略。
- git package 的 bundled dependencies / nested package resources 仍依赖 npm install 结果和现有 resource resolver；尚未补 TS 版完整 dependency governance。
- npm registry semver/range 查询、self-update、完整 `pi update --extensions/--all` CLI 语义仍待补。

### 优化 063：补齐 `pi update` TS 风格目标选择基础语义

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi update` 默认更新 self，`--extensions` 更新 package，`--all` 同时执行 self 与 package，`--extension <source>` 更新单个 package。Java 此前无参数 `update` 会直接更新 packages，和 TS 用户预期不一致。

完成内容：

- `PackageManagerCli` 新增 `update` 目标解析：
  - `pi update`：默认 self-only，输出 Java 当前 self-update 管理说明，并提示 packages 被跳过；
  - `pi update --self` / `pi update self` / `pi update pi`：self-only；
  - `pi update --extensions`：更新当前 scope settings 中的 packages；
  - `pi update --all`：先输出 self-update 管理说明，再更新当前 scope settings 中的 packages；
  - `pi update --extension <source>`：按既有 package identity 更新单个 configured package；
  - `pi update <source>`：继续作为单 package update 的短写；
  - `pi update self --extensions`：作为 self + package 的组合目标。
- `PackageManagerCli` 对明显冲突的 update flags 返回 usage：
  - `--all` 不能和 `--self` / `--extensions` / `--extension` 组合；
  - `--extension` 不能和其他 target flags 或 positional source 组合；
  - positional package source 不能再叠加 target flags。
- `PackageManagerTest` 新增覆盖：
  - 无参数 `update` 不安装 configured package，并提示 packages skipped；
  - `--extensions` 才安装 configured npm package；
  - `--extension <source>` 只更新目标 package；
  - `--all` 同时输出 self-update 管理说明并更新 packages；
  - 冲突 flags 返回错误。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 13 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是目标选择和 CLI 行为；真实 self-update 安装仍只是 Java 当前管理说明，尚未迁移 TS 版 npm/package-manager reinstall 流程。
- package update 仍按当前 Java scope 行为工作，未完整迁移 TS 版 trust-aware global+project package update 策略。
- npm registry semver/range 查询、update 进度/离线语义、包管理器差异治理仍待补。

### 优化 064：补齐 package resource identity dedupe / project 优先基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package resolver 会先处理 project settings packages，再处理 global settings packages，并按 package identity 去重，同一包由 project scope 覆盖 global scope。Java 此前扫描 global roots 在前，且 `ResourceLoader` 只拿到 merged settings packages，无法保留 scope 过滤语义。

完成内容：

- `ResourceLoader` 新增 global/project package entries 分离构造入口，并在 reload 时把两个 scope 分别传给 `PackageResourceResolver`。
- `AgentSessionServices` 创建默认 `ResourceLoader` 时改为读取 `SettingsManager.getGlobalSettings()` / `getProjectSettings()` 中原始 `packages` 数组，避免 merged settings 丢失 scope。
- `PackageResourceResolver` 新增 scoped package roots：
  - 可信项目先扫描 `.pi/packages` / `.pi/git` / `.pi/npm/node_modules`；
  - 再扫描 global `packages` / `git` / `npm/node_modules`；
  - 非可信项目不加载 project package roots。
- package filters 改为带 scope 匹配，避免 global package object filters 误应用到同名 project package。
- package resource resolution 增加基础 identity dedupe：
  - configured source 优先使用 source identity；
  - 未配置时用 `package.json` 的 `name`；
  - 无 package name 时回退安装路径 identity；
  - 同 identity 后续 roots 会跳过，因此 trusted project package 会覆盖 global package。
- `ResourceLoadingTest` 新增同 package identity 下 project package 覆盖 global package、untrusted 仅加载 global package 的回归覆盖，并更新 package resource 顺序断言为 project-first。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSessionServices.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/ResourceLoader.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/PackageResourceResolver.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/resources/ResourceLoadingTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`ResourceLoadingTest` 15 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 resource resolution 的 project-over-global 和基础 identity dedupe；git URL host/path 标准化、local source 绝对路径 identity、settings update 的 trust-aware global+project 策略仍未完整迁移 TS 细节。
- `extensions` resource path 已继续预留，但 Java 扩展运行时仍只支持 JAR extension，不会把 package `extensions` 作为 TS/JS runtime 加载。

### 优化 065：补齐 package identity git/local 归一化增量

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package identity 对 npm 使用 package name、对 git 使用规范化 host/path、对 local 使用按 scope base 解析后的路径。Java 优化 064 已有 project-over-global 去重，但 resource resolver 的 configured source identity 仍对 git/local 过度依赖末段名称，容易把不同 host 的同名 repo 或不同路径的同名本地包误判为同一 package。

完成内容：

- `PackageResourceResolver` 的 configured package identity 改为更接近 TS：
  - npm source 使用 `npm:<name>`，忽略版本；
  - git source 解析 `git:` shorthand、HTTP(S)/SSH/git/file protocol 和 scp-like URL，并使用小写 host + repo path，忽略 ref；
  - local source 根据 package scope 解析路径：global 相对 `agentDir`，project 相对 `cwd/.pi`，再转为绝对 normalize 路径；
  - `extractPackageName` 先走 npm/git parser，避免 `review-pack.git@v1` 这类 source 不能匹配已安装 root 的问题。
- `PackageManager` 的 git source parser 对 host 统一转小写，使 settings source 匹配能识别 `GitHub.com` 和 `github.com` 是同一 repo。
- `ResourceLoadingTest` 新增覆盖：
  - 不同 host/path 但 repo 末段同名的 git packages 不会被误去重；
  - 不同 local source path 但目录名/package name 同名的 packages 不会被误去重。
- `PackageManagerTest` 新增覆盖 mixed-case git shorthand 与 HTTPS URL 的 settings source identity 替换。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/PackageResourceResolver.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/resources/ResourceLoadingTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 14 个测试、`ResourceLoadingTest` 17 个测试，共 31 个测试，0 failures，0 errors。

当前限制：

- 本轮聚焦 package resource resolution 和 git settings source 匹配；settings 中 local package source 的相对路径规范化、跨输入形式匹配，以及 trust-aware global+project update 策略仍未完整迁移 TS 细节。
- 真实 self-update、update 进度/离线语义和依赖治理细节仍待补。

### 优化 066：补齐 local package settings path 归一化基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版本地 package source 写入 settings 时会先按当前 cwd 解析，再相对对应 scope base 保存；匹配已配置 package 时，settings source 按 scope base 解析，用户输入按 cwd 解析。Java 此前直接把绝对 source 字符串写进 settings，后续匹配也主要依赖字符串相等，和 TS 的可迁移 settings 语义不一致。

完成内容：

- `PackageManager.installAndPersist(...)` 写入 local package source 时改为 scope-aware 归一化：
  - global settings：相对 `agentDir` 保存 local source；
  - project settings：相对 `cwd/.pi` 保存 local source；
  - npm/git source 保持原样，沿用已有 identity 匹配。
- `removeAndPersist(...)` 和 settings-driven `update(...)` 的 configured source 匹配改为 scope-aware：
  - settings 中 local source 按 global/project scope base 解析；
  - 用户输入 local source 按 cwd 解析；
  - 因此 settings 中的相对 local source 可以被绝对输入正确匹配。
- `configuredPackageSources(...)` 的去重改为 settings-vs-settings 解析，避免同一 scope 下等价 local path 以不同相对形式重复参与 update。
- `PackageManagerTest` 更新 local install/remove 断言，覆盖 settings 中保存的是相对 scope base 的 source，并继续验证绝对 source remove 能清理该 entry。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 14 个测试，0 failures，0 errors。

当前限制：

- `pi config list|enable|disable` 的 public helper 当前仍是行式基础入口，local source filter 配置尚未获得 cwd/agentDir scope-aware 匹配能力；完整 TS 版 selector/top-level resource 启停仍待补。
- settings-driven update 仍跳过 local packages，保持当前 Java 基础策略；TS 更完整的 local package lifecycle、npm registry semver/range 查询、真实 self-update、update 进度/离线语义和依赖治理细节仍待补。

### 优化 067：补齐 `pi config` local source scope-aware 匹配

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package settings 中的 local source 会按 scope base 保存相对路径，`pi config enable/disable` 需要能用用户输入的绝对/相对 local source 找到对应 settings entry。Java 优化 066 已让 install/remove/update 具备 scope-aware local path 匹配，但 `configurePackageResource(...)` 和 CLI `pi config` 仍走旧的字符串/基础 identity 匹配。

完成内容：

- `PackageManager.configurePackageResource(...)` 新增带 `cwd` / `agentDir` 的 scope-aware overload。
- `PackageManagerCli config enable|disable` 改为传入当前 cwd 和 agentDir，使行式 `pi config` 能匹配 settings 中相对保存的 local package source。
- 配置 local package filters 时，字符串 package entry 自动升级为对象 entry 后仍保留 normalized settings source，不会把用户输入的绝对路径写回 settings。
- `PackageManagerTest` 新增覆盖：
  - global settings 中 local source 相对 `agentDir` 保存时，可用绝对 source 配置 filter；
  - project settings 中 local source 相对 `cwd/.pi` 保存时，可用绝对 source 配置 filter；
  - 配置后 source 字段继续保持相对形式，filters 正确写入。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl packages/coding-agent -am -Dtest=ImageGenerationProvidersTest,BuiltinProvidersTest,SlashCommandsTest,CliEntryTest,FileArgumentProcessorTest,ImageCommandTest,PackageManagerTest,InteractiveOutputRendererTest,HtmlExporterTest,MarkdownTest,DiffTest,AgentSessionRuntimeTest,CodingAgentMessagesTest,ResourceLoadingTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
```

结果：通过。`PackageManagerTest` 15 个测试，聚焦 package/resource/settings 40 个测试，迁移相关宽回归 133 个测试，均 0 failures，0 errors；`git diff --check` 无输出。

当前限制：

- `pi config` 仍是行式基础入口，不是 TS 版全屏 selector/search UI。
- settings-driven update 仍跳过 local packages，保持当前 Java 基础策略；TS 更完整的 local package lifecycle、npm registry semver/range 查询、真实 self-update、update 进度/离线语义和依赖治理细节仍待补。

### 优化 068：补齐 `pi config` top-level resource filters 基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi config` selector 不只修改 package object filters，也能启停 settings 顶层 `extensions` / `skills` / `prompts` / `themes` 中的资源。Java 优化 058/067 已能配置 package filters，但 top-level resource filters 还没有用户入口，也没有让 `-path` 实际影响默认资源加载。

完成内容：

- `SettingsManager` 新增 global/project 顶层 resource path setters：
  - `extensions`
  - `skills`
  - `prompts`
  - `themes`
- `PackageManager.configureTopLevelResource(...)` 可把顶层 resource path 写成 `+path` / `-path` filter，并替换同一路径已有 marker。
- `PackageManager.listConfiguredResources(...)` 可列出 global/project 顶层 resource filters。
- `PackageManagerCli config` 新增 `--top-level` / `--resource` 分支：
  - `pi config list --top-level [-l]`
  - `pi config enable --top-level <extensions|skills|prompts|themes> <path> [-l]`
  - `pi config disable --top-level <extensions|skills|prompts|themes> <path> [-l]`
- `ResourceLoader` 对顶层 settings paths 支持基础 filter 语义：
  - `+path` / plain `path` 作为显式启用路径加载；
  - `-path` / `!path` 会在加载结果中屏蔽匹配的 top-level resource；
  - 匹配时同时支持相对 cwd、global `agentDir` 和 project `.pi` 的路径形状，覆盖默认目录扫描。
- `PackageManagerTest` / `ResourceLoadingTest` 新增覆盖：
  - global/project 顶层 filters 写入；
  - CLI `pi config disable --top-level ...` 写入 settings；
  - `ResourceLoader` 会用 `-skills/...` 屏蔽默认 skill，并用 `+...` 加载显式 skill。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/config/SettingsManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/ResourceLoader.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/resources/ResourceLoadingTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl packages/coding-agent -am -Dtest=ImageGenerationProvidersTest,BuiltinProvidersTest,SlashCommandsTest,CliEntryTest,FileArgumentProcessorTest,ImageCommandTest,PackageManagerTest,InteractiveOutputRendererTest,HtmlExporterTest,MarkdownTest,DiffTest,AgentSessionRuntimeTest,CodingAgentMessagesTest,ResourceLoadingTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
```

结果：通过。聚焦 package/resource/settings 43 个测试，迁移相关宽回归 136 个测试，均 0 failures，0 errors；`git diff --check` 无输出。

当前限制：

- `pi config --top-level` 仍是行式基础入口，不是 TS 版全屏 selector/search UI。
- Java 当前仍未接完整 top-level `extensions` 运行时加载；`extensions` filter 写入与列表已预留，skills/prompts/themes 已接入 `ResourceLoader`。
- 真实 self-update、update 进度/离线语义和依赖治理细节仍待补。

### 优化 069：补齐 npm registry semver/range update 查询基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package update 会在 npm 更新前通过 configured `npmCommand` 执行 `npm view <spec> version --json`，读取 registry 版本，并按 semver/range 判断是否需要安装。Java 优化 061 已有 settings-driven npm update，但只要不是 pinned exact version 就直接 reinstall，缺少 registry 查询、range 目标版本选择和已最新跳过。

完成内容：

- npm package update 在非 pinned exact version 时会先读取已安装 package 的 `package.json#version`。
- 通过 settings `npmCommand` 执行 `npm view <name|spec> version --json` 查询 registry：
  - 无版本 spec 时查询 package name；
  - range/tag spec 时查询完整 npm spec。
- registry 返回版本数组时按基础 semver/range 规则选最高匹配版本；支持常见 exact、`^`、`~`、比较器、通配和 `||` 分组。
- 如果已安装版本等于 registry 目标版本，会输出 skip，不再 reinstall。
- 如果 registry 查询失败或返回无法识别，保持优化 061 的保守行为：继续按原 configured source 安装。
- range 更新安装时使用解析出的 exact target version，但 settings 中仍保留原 configured source，例如 `npm:@scope/review-pack@^1.0.0`。
- `PackageManagerTest` 新增覆盖：
  - `npm:@scope/review-pack@^1.0.0` 从 fake registry 版本数组中选中 `1.5.0`；
  - 第二次 update 识别已安装 `1.5.0`，输出 already-at skip；
  - `pi update --all` 在单包已更新后跳过已最新 npm package，同时继续安装其他 package。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl packages/coding-agent -am -Dtest=ImageGenerationProvidersTest,BuiltinProvidersTest,SlashCommandsTest,CliEntryTest,FileArgumentProcessorTest,ImageCommandTest,PackageManagerTest,InteractiveOutputRendererTest,HtmlExporterTest,MarkdownTest,DiffTest,AgentSessionRuntimeTest,CodingAgentMessagesTest,ResourceLoadingTest,CodingToolFactoryTest,SettingsManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
```

结果：通过。`PackageManagerTest` 18 个测试，聚焦 package/resource/settings 44 个测试，迁移相关宽回归 137 个测试，均 0 failures，0 errors；`git diff --check` 无输出。

当前限制：

- 这是基础 registry/range 链路，还没有 TS 版完整 batch npm install、并发 update check、离线模式和进度事件。
- 完整 self-update 最新版本探测/安装方式识别/权限与说明、依赖治理细节、git update 并发/进度/离线语义和全屏 `pi config` selector 仍待补。

### 优化 070：补齐 settings-driven npm self-update 基础安装链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi update` 默认 self-update，能根据安装方式执行 npm/package-manager reinstall。Java 优化 063 只补了目标选择语义，self 目标仍只输出 Java 当前管理说明。

完成内容：

- `SettingsManager` 新增 `selfUpdatePackage` / `selfUpdatePackageName` 读取入口。
- `PackageManager.update("self"|"pi"|默认 self)` 在 settings 配置 `selfUpdatePackage` 时，会使用 settings `npmCommand` 执行：
  - `install -g --ignore-scripts --min-release-age=0 <selfUpdatePackage>`
  - 当 `selfUpdatePackageName` 与安装目标包名不同步时，再执行 `uninstall -g <selfUpdatePackageName>` 清理旧包名。
- 未配置 `selfUpdatePackage` 时保持原有 Java 管理说明，避免对源码/Maven 安装用户做错误自更新。
- `PackageManagerTest` 新增 fake npm 覆盖 `pi update` 默认 self 目标会执行 configured npm self-update install，并验证旧包名清理命令。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/config/SettingsManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 19 个测试，0 failures，0 errors。

当前限制：

- 本轮是 settings-driven npm self-update 的显式配置链路；尚未迁移 TS 版自动判断当前安装方式、查询最新可更新版本、release notes、writable 检查、Windows 命令差异和完整进度/离线语义。
- 非 npm 分发方式仍保持 Java 当前管理说明。

### 优化 071：补齐 git remote HEAD unchanged skip 基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 git package update 会识别远端 HEAD / configured ref 是否真的变化，避免无变化时重复 reset/clean 和依赖安装。Java 此前已有 settings-driven git update 与 pinned ref reconcile，但每次更新都会执行 `pull` 或 `reset/clean` 并重复跑 package dependencies。

完成内容：

- `PackageManager.installGit(...)` 在已安装 unpinned git package 时：
  - 读取本地 `HEAD`；
  - 通过 upstream branch 的 `git ls-remote origin <branch>` 查询远端目标 commit；
  - upstream 不可用时回退查询 `origin HEAD`；
  - 本地与远端 commit 相同则输出 `Skipped git package ... already at <sha>`，不再执行 `git pull` 或 dependency install。
- pinned ref reconcile 在 `fetch origin <ref>` 后会比较本地 `HEAD` 与 `FETCH_HEAD^{commit}`：
  - commit 相同则 skip；
  - commit 不同才执行 `reset --hard FETCH_HEAD`、`clean -fdx` 和 package dependency install。
- 新增 `gitHead(...)`、`remoteGitHead(...)`、`parseLsRemoteHead(...)`、`shortCommit(...)` helper，保持失败时对 unpinned remote check 保守回退到既有更新行为。
- `PackageManagerTest` 新增覆盖：远端 HEAD 与本地一致时，`PackageManager.update("all", ...)` 输出 skip，并且不会重新生成 fake npm dependency marker。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 20 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 git package unchanged skip 的基础链路；尚未迁移 TS 版并发 update check、统一 progress events、离线模式短路和 batch update 可用性报告。
- unpinned git package 远端检查失败时会保守回退到既有 `git pull` 行为。

### 优化 072：补齐 package update offline mode 短路基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package manager 在 `PI_OFFLINE=1|true|yes` 或 `--offline` 下会避免 update/check 触网。Java 此前已有 `--offline` 参数声明，但 package update/self-update 不读取离线状态，仍可能执行 npm registry、npm install 或 git remote 操作。

完成内容：

- `PackageManager` 新增离线模式判断：
  - 识别环境变量 `PI_OFFLINE=1|true|yes`；
  - 识别 JVM system property `PI_OFFLINE=1|true|yes`，供 Java 主入口和测试使用。
- `PackageManager.update("self"|"pi")` 在离线模式下输出 `Offline mode enabled; skipped self-update.`，不再执行 configured npm self-update。
- settings-driven package update 在离线模式下输出 `Offline mode enabled; skipped package update.`，不再执行 npm registry 查询、npm install 或 git update。
- git remote HEAD 查询在离线模式下返回无远端结果，避免 `ls-remote` 触网；已有 update 短路会优先挡住 settings-driven git update。
- `Main` 在命令行包含 `--offline` 时设置 `PI_OFFLINE` / `PI_SKIP_VERSION_CHECK` system property，并支持 `pi --offline update` 与 `pi update --offline` 两种位置进入 package subcommand。
- `PackageManagerTest` 新增覆盖：
  - `pi update --all` 在离线 override 下同时跳过 self 和 packages，fake npm 不会被调用；
  - `PI_OFFLINE` system property 为 `yes` 时，直接调用 `PackageManager.update("all", ...)` 会短路 package update。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/Main.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 22 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 update/self-update 的离线短路；尚未迁移 TS 版 `checkForAvailableUpdates()` API、统一 progress events、并发 update check 和 batch update 可用性报告。
- `--offline` 对其他启动期网络操作仍依赖各模块逐步接入；本轮只覆盖 package update 相关路径。

### 优化 029：扩展 `sendUserMessage` 复用行式渲染路径

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 版扩展触发 user message 后会进入同一交互 UI 事件流；Java 版优化 027 的 `sendUserMessage` 会持久化消息并更新 stats，但在行式交互中不会显示 assistant 输出。

完成内容：

- `ExtensionCommandContext` 新增可注入的 `UserMessageSender`。
- 默认构造仍使用 `AgentSession.promptRaw(...)`，保持 core/测试环境兼容。
- `InteractiveModeRunner` 构造扩展命令 context 时传入带渲染的 raw prompt sender。
- `InteractiveModeRunner.executePrompt(...)` 抽出 raw prompt 变体，普通 prompt 和扩展 `sendUserMessage` 复用同一套订阅、assistant 文本渲染、tool start/end 渲染和 turn line 输出逻辑。
- CLI 集成测试确认 `/sendmsg` 扩展命令触发的 assistant 文本 `model response` 会出现在交互输出中。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 17 个测试、`CliEntryTest` 20 个测试，共 37 个测试，0 failures，0 errors。

当前限制：

- 渲染复用只覆盖行式 CLI；尚未实现 TS 版 TUI 的队列、steer/followUp、流式中断和 extension UI request/response。
- `sendUserMessage` 仍只支持字符串内容，不支持图片或多 part content array。
- 扩展命令 handler 仍是同步执行，长时间运行时没有取消信号。

### 优化 073：补齐 `pi config` JSON 快照基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi config` 具备面向选择器/TUI 的结构化配置数据基础；Java 此前只有文本 `pi config list|enable|disable`，后续完整 config selector 缺少稳定可消费的 package/resource 配置快照。

完成内容：

- `PackageManager` 新增 `listConfiguredPackagesJson(...)`：
  - 输出 `scope`；
  - 输出 `packages` 数组；
  - 每个 package 固定包含 `source`、`extensions`、`skills`、`prompts`、`themes` 字段，便于后续 selector 直接消费。
- `PackageManager` 新增 `listConfiguredResourcesJson(...)`：
  - 输出 `scope`；
  - 输出 `resources.extensions|skills|prompts|themes`；
  - 空资源类型保持空数组，避免调用方猜测字段是否存在。
- `PackageManagerCli` 新增 `pi config list --json` 与 `pi config list --top-level --json`：
  - 默认输出 package filters JSON；
  - 搭配 `--top-level` / `--resource` 输出顶层 resource filters JSON。
- 修复 `ExtensionCommandContext(Path cwd)` 构造器未初始化 `userMessageSender` 导致的当前编译阻塞；无 session 上下文调用 `sendUserMessage` 时现在抛出明确异常。
- `PackageManagerTest` 增加 JSON API 和 CLI 入口覆盖，验证 JSON 可解析、scope 正确、package filters 和顶层资源 filters 字段稳定。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 23 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 config 结构化快照基础链路；尚未迁移 TS 版完整 `pi config` TUI selector、键盘交互、多选状态切换和资源预览。
- JSON 输出只反映 settings 中的配置过滤规则；尚未把实际已安装 package resource discovery 结果、冲突来源、启用/禁用解释结果合并到同一个 selector 数据模型。

### 优化 074：补齐 `pi config --resolved` 资源候选快照基础链路

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi config` selector 需要展示实际可启停的已安装 package resources。Java 优化 073 只输出 settings 中的 package/top-level filters，还不能让后续 selector 直接拿到当前 discovery 后的资源候选。

完成内容：

- `PackageManager` 在 JSON config snapshot 上新增可选 `resolvedResources`：
  - 复用 `PackageResourceResolver.resolve(...)`；
  - 输出当前 effective scope 下 discovery 后的 `extensions`、`skills`、`prompts`、`themes` 绝对路径；
  - 保留 `resolvedScope`，区分 `global` 与可信项目下的 `effectiveProject`。
- `PackageManagerCli` 新增 `pi config list --json --resolved`：
  - 默认输出 package filters JSON；
  - 追加 package resource discovery 后的实际资源路径；
  - `pi config list --top-level --json --resolved` 同样可追加 resolved resources，供后续 selector 复用同一快照入口。
- `--resolved` 不改变未传 `--json` 时的文本输出，也不改变优化 073 已有 JSON 字段，降低对脚本调用方的破坏面。
- `PackageManagerTest` 增加 fake package 覆盖：manifest 暴露 public/private 两个 skill，settings package filter 只允许 public，`--resolved` JSON 只输出过滤后的 public 资源路径。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 24 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是实际 package resource 候选路径快照；仍未实现 TS 版完整 `pi config` TUI selector、键盘交互、多选切换和预览。
- `resolvedResources` 当前是按资源类型聚合后的 effective paths；尚未逐项输出来源 package、冲突/覆盖原因、当前 enabled/disabled 解释状态或可直接应用的 toggle action。

### 优化 075：补齐 resolved resource 来源元数据

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi config` selector 不只需要资源路径，还需要知道资源来自哪个 package、哪个 scope、哪个 identity，才能解释 project/global 覆盖、展示来源并进一步实现 toggle action。Java 优化 074 的 `resolvedResources` 还是按类型聚合的路径数组。

完成内容：

- `PackageResourceResolver` 新增 `PackageResourceInventory` / `PackageResourceItem`：
  - 继续返回既有 `PackageResourcePaths`，保持 `ResourceLoader` 行为不变；
  - 额外输出逐项资源元数据；
  - 每个 item 包含 `type`、`path`、`scope`、`packageRoot`、`packageName`、`source`、`identity`。
- resolver 内部把 package filter 逻辑抽成共享 `filteredPaths(...)`，确保聚合路径和逐项 metadata 使用同一套 manifest/filter 结果，不产生 selector 看到的 item 与实际加载路径不一致的问题。
- `PackageManager` 的 JSON resolved snapshot 新增 `resolvedResourceItems`：
  - `resolvedResources` 继续保留按类型聚合的路径数组；
  - `resolvedResourceItems` 提供后续 selector 所需的来源解释数据。
- `PackageManagerTest` 扩展 fake package 覆盖，验证 `resolvedResourceItems` 中的 `type`、`scope`、`packageRoot`、`packageName`、`source`、`identity`。
- 增加 `ResourceLoadingTest` 到本轮验证命令，覆盖 resolver refactor 未破坏 resource loading / package dedupe / filters。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/PackageResourceResolver.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 24 个测试、`ResourceLoadingTest` 18 个测试，共 42 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是来源元数据；尚未输出冲突/覆盖原因、当前 enabled/disabled 的解释状态或可直接应用的 toggle action。
- 仍未实现 TS 版完整 `pi config` TUI selector、键盘交互、多选切换和资源预览。

### 优化 076：补齐 selector toggle action metadata

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi config` selector 需要把资源项映射到可执行的 enable/disable 操作。Java 优化 075 已能解释资源来源，但 item 里还缺 package 内相对路径和可直接复用的 toggle action args。

完成内容：

- `PackageResourceResolver.PackageResourceItem` 新增 `relativePath`：
  - 基于 `packageRoot.relativize(path)` 生成 package 内路径；
  - 统一使用 `/` 分隔，匹配现有 `pi config enable|disable <source> <type> <path>` 的 filter path 语义。
- `PackageManager` 的 `resolvedResourceItems` 新增：
  - `relativePath`；
  - `enabled: true`；
  - `actions.disable`：`["config", "disable", source, type, relativePath]`；
  - `actions.enable`：`["config", "enable", source, type, relativePath]`。
- `PackageManagerTest` 扩展 JSON 断言，验证 `relativePath`、`enabled` 和 enable/disable action args。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/PackageResourceResolver.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 24 个测试、`ResourceLoadingTest` 18 个测试，共 42 个测试，0 failures，0 errors。

当前限制：

- 本轮只对当前 resolved/enabled 的 package resources 输出 toggle action metadata；尚未列出被 filter 排除的 disabled candidates。
- 尚未输出冲突/覆盖原因，也未实现完整 TS 版 `pi config` TUI selector 的键盘交互、多选切换和资源预览。

### 优化 077：补齐 package filter disabled candidates

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi config` selector 需要同时展示已启用和被过滤掉的候选资源，才能从 UI 中重新启用资源。Java 优化 076 只给当前 enabled/resolved 资源生成 toggle action metadata，无法列出已被 package filter 排除的候选项。

完成内容：

- `PackageResourceResolver.PackageResourceItem` 新增 `enabled` 字段。
- 当 package entry 存在某类 resource filter 时，resolver 会：
  - 继续把当前 filter 后的资源写入 `resolvedResources`；
  - 在 `resolvedResourceItems` 中追加 manifest/conventional 允许但被当前 filter 排除的文件项；
  - disabled item 标记 `enabled: false`，并保留 `relativePath`、`source`、`identity` 和 enable/disable action args。
- 未配置 package filter 的资源保持原有行为，不额外枚举 disabled candidates，避免改变默认 package discovery 的输出体量。
- `PackageManagerTest` 扩展 fake package 覆盖：`skills/public.md` 为 enabled item，`skills/private.md` 作为 disabled candidate 出现在 `resolvedResourceItems`，但不会进入 `resolvedResources.skills`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/PackageResourceResolver.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 24 个测试、`ResourceLoadingTest` 18 个测试，共 42 个测试，0 failures，0 errors。

当前限制：

- disabled candidates 当前只在 package entry 对应资源类型存在 filter 时枚举；未配置 filter 的 package 仍只输出当前 enabled/resolved 项。
- 尚未输出 project/global 冲突覆盖原因，也未实现完整 TS 版 `pi config` TUI selector 的键盘交互、多选切换和资源预览。

### 优化 078：补齐 shadowed resource 覆盖原因

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi config` selector 需要解释为什么某些资源没有进入当前 effective resources。Java 已有 project-over-global package identity dedupe，但 `--resolved` 只展示最终 enabled/disabled filter 状态，不能解释同 identity 的 global package 被 project package 覆盖。

完成内容：

- `PackageResourceResolver` 将 seen package identities 从集合升级为 identity -> package root 映射。
- 当后续 package root 命中已见 identity 时：
  - 不改变既有加载行为，仍跳过该 package，不写入 `resolvedResources`；
  - 额外解析该 shadowed package 的 manifest/conventional resources；
  - 在 `resolvedResourceItems` 中输出 `enabled: false` 的解释性 item。
- `PackageResourceItem` 新增：
  - `disabledReason`；
  - `overriddenByIdentity`。
- shadowed item 使用：
  - `disabledReason: "shadowed-by-prior-package"`；
  - `overriddenByIdentity: <identity>`。
- `PackageManager` 的 JSON resolved snapshot 输出上述字段。
- `PackageManagerTest` 增加 trusted project/global 同名 package 覆盖测试：
  - project package skill 进入 `resolvedResources.skills`；
  - global package skill 作为 shadowed disabled item 出现在 `resolvedResourceItems`；
  - 验证 `disabledReason` 和 `overriddenByIdentity`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/PackageResourceResolver.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 25 个测试、`ResourceLoadingTest` 18 个测试，共 43 个测试，0 failures，0 errors。

当前限制：

- shadowed item 当前记录的是同 identity 被先前 package 覆盖的原因；尚未输出更细的“由哪个具体 package root 覆盖”字段。
- 仍未实现完整 TS 版 `pi config` TUI selector 的键盘交互、多选切换和资源预览。

### 优化 079：补齐 shadowed resource 覆盖方元数据

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi config` selector 需要解释 disabled/shadowed 资源的具体来源关系。Java 优化 078 已能说明同 identity 资源被前序 package 覆盖，但只输出 identity，不能直接定位覆盖方 package。

完成内容：

- `PackageResourceResolver` 将 package identity 去重表升级为 identity -> resolved package metadata。
- shadowed item 在保留 `disabledReason: "shadowed-by-prior-package"` 和 `overriddenByIdentity` 的基础上，新增覆盖方字段：
  - `overriddenByScope`；
  - `overriddenByPackageRoot`；
  - `overriddenByPackageName`；
  - `overriddenBySource`。
- `PackageManager` 的 `resolvedResourceItems` JSON 快照输出上述字段，方便后续 TUI selector 展示“由哪个 scope/package 覆盖”。
- `PackageManagerTest` 扩展 trusted project/global 同名 package 覆盖测试，验证 global shadowed resource 指向 project package 的 scope、root 和 package name。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/resources/PackageResourceResolver.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 25 个测试、`ResourceLoadingTest` 18 个测试，共 43 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 `--resolved` 结构化快照中的覆盖方 metadata；尚未实现完整 TS 版 `pi config` TUI selector 的键盘交互、多选切换和资源预览。
- shadowed item 仍按 package identity 去重解释覆盖关系；更细粒度的单资源冲突排序/优先级 UI 仍需在 selector 层实现。

### 优化 080：补齐 top-level resolved resource selector metadata

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi config` selector 不只启停 package resource，也能启停 settings 顶层 `extensions` / `skills` / `prompts` / `themes` resource filters。Java 已能写入顶层 filters 并在 ResourceLoader 中应用，但 `pi config list --top-level --json --resolved` 缺少逐项 enabled/disabled selector 数据。

完成内容：

- `PackageManager.listConfiguredResourcesJson(..., includeResolvedResources=true)` 新增：
  - `resolvedTopLevelResources`：按 type 聚合当前顶层 filters 中 enabled 的绝对路径；
  - `resolvedTopLevelResourceItems`：逐项输出顶层 resource filter 候选。
- `resolvedTopLevelResourceItems` 每项包含：
  - `type`；
  - `path`；
  - `relativePath`；
  - `scope`；
  - `filter`；
  - `enabled`；
  - `disabledReason`。
- 顶层 item 新增可直接复用的 action metadata：
  - `actions.disable`: `["config", "disable", "--top-level", type, relativePath]`；
  - `actions.enable`: `["config", "enable", "--top-level", type, relativePath]`。
- 对手写 settings 中的空/无效 filter 做容错跳过，避免 list snapshot 因无效项整体失败。
- `PackageManagerTest` 新增测试覆盖 `+skills/public.md` 与 `-skills/private.md` 两类顶层 filters 的 resolved JSON 输出。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 26 个测试、`ResourceLoadingTest` 18 个测试，共 44 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 top-level resource filters 的结构化 selector 数据；仍未实现完整 TS 版全屏 `pi config` selector 的键盘交互、多选切换、搜索和资源预览。
- `resolvedTopLevelResources` 反映 settings 顶层 filters 的显式启用项；默认目录自动发现、package resources 和实际 loader 诊断仍分别由现有 ResourceLoader/package resolved snapshot 表达。

### 优化 081：补齐 self-update current version registry skip

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 self-update 会先探测最新版本并避免无意义 reinstall。Java 优化 070 已能在 settings 配置 `selfUpdatePackage` 时执行 npm 全局安装，但只要运行 `pi update` 就直接 install，缺少“已是最新版本则跳过”的基础判断。

完成内容：

- `SettingsManager` 新增 `getSelfUpdateCurrentVersion()`，读取 merged settings 中的 `selfUpdateCurrentVersion`。
- `PackageManager.update("self"|"pi")` 在同时配置：
  - `selfUpdatePackage`；
  - `selfUpdateCurrentVersion`
  时，会先使用 settings `npmCommand` 执行 `npm view <selfUpdatePackage> version --json`。
- 当 registry 返回版本等于 `selfUpdateCurrentVersion` 时：
  - 输出 `Skipped self-update <package> already at <version>`；
  - 不再执行 `npm install -g`；
  - `pi update` 默认 self-only 仍会提示 packages 被跳过，保持目标选择语义。
- 当 registry 返回更高 semver 时，self-update 会把安装目标收敛成 `<package>@<targetVersion>`，避免 range reinstall 的目标不透明。
- registry probe 失败时保持优化 070 的旧行为，继续按配置的 `selfUpdatePackage` 安装，避免网络/registry 异常阻断显式 self-update。
- `PackageManagerTest` 新增 fake npm 覆盖：
  - `view @earendil-works/pi-coding-agent@^9.0.0 version --json` 返回 `9.9.9`；
  - `selfUpdateCurrentVersion: "9.9.9"` 时跳过安装；
  - fake npm log 中没有 `install -g`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/config/SettingsManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 27 个测试、`ResourceLoadingTest` 18 个测试，共 45 个测试，0 failures，0 errors。

当前限制：

- 本轮只补显式配置版本的 registry skip；仍未自动识别当前二进制/包管理器安装方式，也未自动读取实际已安装 CLI 版本。
- 完整 release notes、权限检查、Windows 安装差异和 update 进度事件仍待补。

### 优化 082：补齐 self-update range exact target install

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 self-update 会探测最新版本并安装明确目标。Java 优化 081 已能在配置 `selfUpdateCurrentVersion` 时做 registry skip，但未配置 current version 时，`selfUpdatePackage` 若是 range/unpinned spec 仍会原样传给 `npm install -g`，安装目标不够明确。

完成内容：

- `PackageManager.planSelfUpdate(...)` 调整 self-update registry probe 触发条件：
  - 配置了 `selfUpdateCurrentVersion` 时继续 probe，用于已最新 skip；
  - 未配置 current version，但 `selfUpdatePackage` 为 unpinned 或 range spec 时也会 probe。
- 当 registry 返回 semver 目标版本且 self-update spec 非 exact pinned 时，安装目标改为：
  - `<package>@<targetVersion>`。
- exact pinned `selfUpdatePackage` 且未配置 current version 时保持旧行为，不额外触发 registry probe。
- registry probe 失败时继续保持显式配置安装路径，避免网络问题阻断用户明确发起的 self-update。
- `PackageManagerTest` 新增 fake npm 覆盖：
  - `selfUpdatePackage: "@earendil-works/pi-coding-agent@^9.0.0"`；
  - `npm view ... version --json` 返回 `9.9.9`；
  - 最终执行 `install -g ... @earendil-works/pi-coding-agent@9.9.9`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 28 个测试、`ResourceLoadingTest` 18 个测试，共 46 个测试，0 failures，0 errors。

当前限制：

- 本轮仍基于 settings 显式配置 self-update npm package；尚未自动识别当前 CLI 的安装来源、包管理器和真实当前版本。
- 完整 release notes、权限检查、Windows 安装差异和 update 进度事件仍待补。

### 优化 083：补齐 package update summary output

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package update 有更明确的进度/结果语义。Java 当前 package update 已能逐项输出安装或跳过原因，但缺少汇总；同时 git package 返回 `Skipped ... already at ...` 时仍被计入 updated，不利于后续进度 UI 或调用方判断结果。

完成内容：

- `PackageManager.updateConfiguredSources(...)` 在处理完 settings packages 后追加：
  - `Package update summary: updated <n>, skipped <n>.`
- npm update 路径在实际调用 install 后按返回文本识别 `Skipped ...`，正确计入 skipped。
- git update 路径同样按返回文本识别 skipped：
  - 远端 HEAD unchanged；
  - pinned ref commit unchanged；
  - 这些不再被计入 updated。
- 保留原有无配置、离线短路和逐项输出行为。
- `PackageManagerTest` 扩展断言覆盖：
  - `pi update --extensions` 更新 1、跳过 0；
  - `pi update --all` 中 npm 已最新 + 另一个 npm 更新，汇总 updated 1 / skipped 1；
  - pinned exact npm package 跳过，汇总 updated 1 / skipped 1；
  - npm range 已最新，汇总 updated 0 / skipped 1；
  - git remote HEAD unchanged，汇总 updated 0 / skipped 1；
  - git pinned ref 更新，汇总 updated 1 / skipped 0。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 28 个测试、`ResourceLoadingTest` 18 个测试，共 46 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 CLI 文本汇总，不是 TS 版完整 progress event stream、并发 update 队列或可订阅状态模型。
- package update 仍按当前 Java 的串行执行策略运行。

### 优化 084：补齐 package update failed summary

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package update 有更完整的批量执行结果语义。Java 优化 083 已补 updated/skipped 汇总，但 settings-driven 批量更新中单个 package 失败会中断后续 source，且没有 failed 计数，不利于批量更新和后续进度 UI 判断。

完成内容：

- `PackageManager.updateConfiguredSources(...)` 对每个 settings package source 做失败隔离。
- 单个 source 抛出 `IOException` 或 `RuntimeException` 时输出：
  - `Failed package <source>: <first error line>`
- 失败会计入 summary 的 failed 数，并继续处理后续 package source。
- 没有失败时保持原有汇总格式：
  - `Package update summary: updated <n>, skipped <n>.`
- 有失败时输出：
  - `Package update summary: updated <n>, skipped <n>, failed <n>.`
- `InterruptedException` 仍会恢复 interrupt 状态并向上抛出，不把中断误报成普通 package 失败。
- `PackageManagerTest` 新增 fake npm 失败场景，覆盖前一个 package 更新成功、中间 package 失败、后一个 pinned package 跳过，以及最终 updated/skipped/failed 汇总。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 29 个测试、`ResourceLoadingTest` 18 个测试，共 47 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是串行 package update 的失败隔离和文本汇总；仍不是 TS 版完整 progress event stream、并发 update 队列或可订阅状态模型。
- 单包失败消息当前只取异常第一行，便于 CLI 输出稳定；完整诊断日志和结构化错误对象仍待后续补齐。

### 优化 085：补齐 package update progress start output

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package manager 有 `ProgressEvent` 和 `withProgress("update", ...)`，能在 npm/git update 开始时通知调用方。Java 当前只在每个 package 完成后输出结果，批量更新时缺少“正在处理哪个 source”的阶段信息。

完成内容：

- `PackageManager.updateConfiguredSources(...)` 在处理每个 settings package source 前输出：
  - `Updating package <source>...`
- 该阶段行覆盖 npm 更新、git 更新、pinned npm skip、local skip 和失败隔离路径。
- 保留无配置、离线短路和原有 result/summary 输出格式。
- `PackageManagerTest` 扩展失败隔离场景断言，覆盖成功 source、失败 source 和 pinned skip source 都会输出 update start 状态。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 29 个测试、`ResourceLoadingTest` 18 个测试，共 47 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 CLI 文本阶段输出；仍未迁移 TS 版 `ProgressCallback` 事件 API、npm user/project batch progress、并发 update 队列或可订阅状态模型。
- Java 当前仍按串行 source 顺序执行，未实现 TS 版 npm/git 并发控制。

### 优化 086：补齐 package update trusted project scopes

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package update 会同时读取 user 和 project settings 中的 packages，并按 scope 更新到对应安装根。Java 此前 `PackageManager.update("all", false, ...)` 只读取 global settings；`-l` / `local=true` 只读 project settings，缺少可信项目下 global + project package 一起更新的默认语义。

完成内容：

- `PackageManager.update(...)` 的 settings-driven package update source 从字符串升级为 `source + local scope`。
- `local=true` 仍保持 project-only 更新，兼容原 `-l` 行为。
- `local=false` 且 `SettingsManager.isProjectTrusted()` 为 true 时，默认聚合 global settings packages 和 project settings packages。
- 每条 source 更新时使用自己的 scope：
  - global source 安装/查询到 `agentDir/npm|git|packages`；
  - project source 安装/查询到 `cwd/.pi/npm|git|packages`。
- explicit target 匹配时也按每条 source 的 scope 解析 local path identity，避免 global/project 本地相对路径混淆。
- missing target 提示在可信项目下会同时列出 global 和 project configured packages。
- `PackageManagerTest` 新增 trusted project update 场景，覆盖 global npm package 安装到 agentDir、project npm package 安装到 `cwd/.pi`，并验证 summary updated 2 / skipped 0。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 30 个测试、`ResourceLoadingTest` 18 个测试，共 48 个测试，0 failures，0 errors。

当前限制：

- 优化 088 已补 Package CLI `--approve` / `--no-approve` 基础信任覆盖；完整 TS 版交互式 trust prompt、持久化 trust store 与 `-a` / `-na` 之外的项目审批 UX 仍待补。
- 更新执行仍是串行文本输出，未迁移 TS 版 npm user/project batch 并发、git 并发和 `ProgressEvent` 事件 API。

### 优化 087：补齐 self-update failure fallback command

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 self-update 在安装失败时会提示用户可手动执行的更新命令。Java 当前 configured npm self-update 失败时只通过通用异常输出 npm 失败原因，缺少可操作的 fallback command。

完成内容：

- `PackageManager.updateSelf(...)` 在执行 configured npm self-update install 失败时，抛出包含 fallback command 的异常：
  - `If this keeps failing, run this command yourself: <npmCommand> install -g --ignore-scripts --min-release-age=0 <spec>`
- 旧包名清理 `npm uninstall -g <oldPackage>` 失败时同样输出 cleanup fallback command。
- 保留原始 npm 失败输出，便于排查具体错误。
- `PackageManagerTest` 新增 fake npm install 失败场景，覆盖 `pi update` 默认 self 目标失败时 CLI stderr 包含 fallback command。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 31 个测试、`ResourceLoadingTest` 18 个测试，共 49 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 configured npm self-update 的失败回退提示；仍未自动识别当前 CLI 的安装方式、包管理器和真实版本。
- 完整 release notes、权限检查、Windows 安装差异和结构化 progress events 仍待补。

### 优化 088：补齐 Package CLI approve/no-approve trust flags

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package CLI 支持 `--approve` / `--no-approve` 控制是否读取项目本地 `.pi/settings.json`。Java 此前把 `-l` / `--local` 同时当作 project scope 和 project trust，导致 `pi update --extensions --approve` 不能在保持 global scope 的同时读取可信项目 packages。

完成内容：

- `PackageManagerCli` 新增 `-a` / `--approve` 与 `-na` / `--no-approve` 参数解析。
- project trust 与 `local` scope 分离：
  - `-l` / `--local` 继续表示 project-local install/remove/config/list scope；
  - `--approve` 只控制读取项目 `.pi/settings.json`；
  - `--no-approve` 显式禁止读取项目 settings；
  - 未传 trust flag 时保留原兼容行为：`local=true` 默认 trusted，`local=false` 默认不读取 project settings。
- CLI cwd 改为显式读取 `System.getProperty("user.dir")`，便于嵌入/测试场景正确定位项目 `.pi/settings.json`。
- config/install/remove/update usage 文案补充 `--approve|--no-approve`。
- `PackageManagerTest` 新增 CLI 级用例，覆盖 `pi update --extensions --approve` 会同时读取 global 和 project packages，并分别安装到 `agentDir/npm` 与 `cwd/.pi/npm`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 32 个测试、`ResourceLoadingTest` 18 个测试，共 50 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是显式 flag 层面的基础信任覆盖；尚未迁移 TS 版交互式 trust prompt、项目 trust store 持久化和完整审批 UX。
- `--approve` 当前只影响本次 Package CLI 命令，不会持久化项目可信状态。

### 优化 089：补齐 self-update cleanup failure 安装状态提示

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 self-update 失败路径会给出更清晰的用户恢复指引。Java 此前在新包已经安装成功、旧包清理失败时只报 cleanup command failed，用户无法从错误文案判断安装是否已完成。

完成内容：

- `PackageManager.updateSelf` 的旧包清理失败路径改为明确输出 `Self-update installed <spec> but cleanup command failed`。
- 保留原有手动恢复命令输出，失败时继续给出可直接执行的 `npm uninstall -g <old-package>` fallback command。
- `PackageManagerTest` 新增 CLI 级回归，模拟 self-update install 成功但 uninstall 失败，验证退出码、安装成功提示和 cleanup fallback command。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 33 个测试、`ResourceLoadingTest` 18 个测试，共 51 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 cleanup failure 的可恢复提示；仍未自动识别当前 CLI 的安装方式、包管理器、真实版本、权限状态和平台差异。
- self-update 仍是 settings-driven npm 全局安装链路，还不是 TS 版完整 release/update UX。

### 优化 090：补齐 Package CLI list 的 settings/trust 语义

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi list [--approve|--no-approve]` 列出 settings 中配置的 user/project packages，并受项目 trust 控制。Java 此前的 `list` 只扫描安装目录，既不读取 settings `packages`，也不体现 `--approve` / `--no-approve`。

完成内容：

- `PackageManager` 新增 `listConfiguredPackagesForCommand(...)`，复用 package update 的 trust-aware scope 聚合规则：
  - 未信任项目时只列 global settings packages；
  - `--approve` / trusted 时列 global + project settings packages；
  - `-l` / local scope 仍可只列 project settings packages，保留 Java 既有兼容入口。
- `PackageManagerCli list` 改为输出 settings-aware configured packages，不再只扫描已安装目录。
- `PackageManagerTest` 新增 CLI 级回归，覆盖 `pi list` 默认不读项目、`pi list --approve` 读取 project settings、`pi list --no-approve` 显式忽略 project settings。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 34 个测试、`ResourceLoadingTest` 18 个测试，共 52 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 `pi list` 的 settings/trust 基础语义；优化 091 已继续补 installed path / filtered 标记。
- `-l` 在 Java `list` 中仍保留为 project-only 兼容入口；TS 版 `list` help 不暴露该参数。

### 优化 091：补齐 Package CLI list 的 installed path / filtered 标记

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 `pi list` 会显示 configured package 是否带 filters，并在能定位已安装 package 时输出 installed path。Java 优化 090 已切到 settings-aware list，但还只显示 source 和 filter 明细。

完成内容：

- `PackageManager.listConfiguredPackagesForCommand(...)` 新增 cwd/agentDir aware 输出：
  - npm source 按 `npm/node_modules/<name>` 解析已安装路径；
  - git source 按 Java 当前 git install layout 解析已安装路径；
  - local source 按 global/project packages 根目录和 `extractPackageName(...)` 解析已安装路径；
  - 只有路径实际存在时才显示，避免误导用户。
- settings package entry 存在 `extensions` / `skills` / `prompts` / `themes` filters 时，source 行追加 `(filtered)`，对齐 TS 版 list 的快速可见状态。
- `PackageManagerCli list` 传入 cwd/agentDir，使 CLI 输出能包含 installed path。
- `PackageManagerTest` 扩展 `pi list` CLI 回归，先通过 package update 准备 global/project npm 安装目录，再验证 `--approve` 输出 installed path、project installed path 和 `(filtered)` 标记。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 34 个测试、`ResourceLoadingTest` 18 个测试，共 52 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 list 的 installed path / filtered 基础显示；尚未迁移 TS 版 saved project trust store、交互式 trust prompt，以及更完整的 list formatter 样式。
- local source 的 installed path 仍按 Java 当前 install layout 推导，不覆盖 TS 动态 package resolver 的全部边界。

### 优化 092：补齐 `pi update --force` self-update 重装语义

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package CLI 支持 `pi update --force`，用于在当前版本已经是最新版本时仍强制重装 self-update package。Java 此前没有解析 `--force`，该参数会被静默忽略，导致用户无法触发 TS 版的重装语义。

完成内容：

- `PackageManager.update(...)` 新增带 `forceSelfUpdate` 的重载，旧调用保持默认非 force 行为。
- `PackageManager.updateSelf(...)` / `planSelfUpdate(...)` 支持 force：
  - 非 force 时保留 `selfUpdateCurrentVersion` 已最新则跳过；
  - force 时仍查询 registry 目标版本，并在目标版本等于当前版本时继续安装；
  - range/unpinned self-update spec 仍会收敛为 registry exact version 后安装。
- `PackageManagerCli` 新增 `--force` 参数解析，usage 文案包含 `--force`，并只将 force 传给 self-update；extensions update 语义不变。
- `PackageManagerTest` 新增 CLI 回归：`selfUpdateCurrentVersion` 已等于 registry 目标版本时，`pi update --force` 不输出 skipped，而是执行 npm install exact target。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 35 个测试、`ResourceLoadingTest` 18 个测试，共 53 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 settings-driven npm self-update 的 `--force` 重装语义；仍未迁移 TS 版完整安装方式识别、release notes、Windows native dependency quarantine 和权限说明。
- `--force` 当前只影响 self-update skip 判断，不改变 package extensions update 的 reinstall/skip 策略。

### 优化 093：补齐 Package CLI help 和非法参数基础校验

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package CLI 会处理 `-h` / `--help`，并对未知 option、多余 positional argument 返回明确错误。Java 此前会静默忽略未知 flag 或多余参数，容易让用户误以为参数已经生效。

完成内容：

- `PackageManagerCli` 新增 `-h` / `--help` 解析，支持 `install`、`remove` / `uninstall`、`update`、`list`、`config` 的基础帮助文本。
- 新增集中 `usage(...)` / `helpText(...)` helper，保持 CLI 错误输出和帮助文案一致。
- 未知 option 现在返回 exit 1，并输出 `Unknown option ...` 与对应 usage。
- 非 `config` 命令的多余 positional argument 现在返回 exit 1，并输出 `Unexpected argument ...` 与对应 usage。
- 保留 Java 现有 `list -l` project-only 兼容入口；未强行切到 TS help 的严格参数集合。
- `PackageManagerTest` 新增 CLI 级回归，覆盖 `update --help`、未知 `--bogus` option 和 `install` 多余参数。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 36 个测试、`ResourceLoadingTest` 18 个测试，共 54 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 package 子命令的基础 help/invalid args；还不是 TS 版完整 parser 形态，例如 duplicated `--extension` 的专门错误、所有命令的 invalid option parity 和彩色 help 输出。
- `config` 仍允许多个 positional argument 由子命令校验，以兼容现有 `config enable|disable` 参数结构。

### 优化 094：补齐 Package CLI duplicate `--extension` 冲突校验

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1/P9 项：TS 版 package CLI 对重复传入 `--extension` 返回专门错误 `--extension can only be provided once`。Java 此前会让后一个 `--extension` 静默覆盖前一个目标，容易更新错 package。

完成内容：

- `PackageManagerCli` 新增 `conflictingOption` 状态。
- `pi update --extension <a> --extension <b>` 现在返回 exit 1，并输出 `--extension can only be provided once` 与 update usage。
- 保留缺失 `--extension` value 的既有 usage 错误路径。
- `PackageManagerTest` 扩展 CLI 参数回归，覆盖重复 `--extension` 的冲突错误。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/pkg/PackageManagerCli.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/pkg/PackageManagerTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=PackageManagerTest,ResourceLoadingTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`PackageManagerTest` 36 个测试、`ResourceLoadingTest` 18 个测试，共 54 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是重复 `--extension` 的专门冲突错误；尚未迁移 TS 版完整 parser 的全部 invalid option matrix 和彩色帮助输出。
- `config` 的参数结构仍由子命令路径校验，后续完整 `pi config` selector/parser 迁移时需要重新梳理。

### 优化 095：补齐扩展命令基础 UI context 元数据

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P1 项：TS 版扩展上下文能感知 UI/运行环境。Java 此前扩展命令 context 只有命令参数、会话 facade 和 abort 状态，无法判断当前是否来自交互 UI，也拿不到终端尺寸。

完成内容：

- `ExtensionCommandContext` 新增 `UiContext` record，包含：
  - `interactive`：当前扩展上下文是否来自交互 UI。
  - `terminalColumns` / `terminalRows`：当前终端宽高，非 UI 上下文默认为 0。
- 保留所有旧构造器行为，旧调用点默认得到 `UiContext.none()`，不要求 lifecycle/provider/resources 等非交互路径改造。
- 交互 slash extension command 构造 context 时显式传入 `UiContext.interactive(...)`。
- `InteractiveModeRunner` 补充 `LINES` 行数解析，并沿用既有 `COLUMNS` 默认策略：无真实 console 时使用测试稳定默认值。
- `ExtensionCommandContext` 新增便捷方法：
  - `ui()`
  - `hasUi()`
  - `interactive()`
  - `terminalColumns()`
  - `terminalRows()`
- `CliEntryTest` 的扩展 `/args` 回归现在验证扩展命令可读取 interactive/UI 状态和终端宽高。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 共 21 个测试，0 failures，0 errors。

补充验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest,AgentSessionRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`CliEntryTest` 通过；`AgentSessionRuntimeTest` 有 1 个既有文案断言失败，期望 `deliverAs is required while the agent is running`，实际为 `Cannot start a new prompt while the agent is running; use queued delivery`。该失败点不在本轮改动文件中。

当前限制：

- 本轮只补基础交互状态和终端宽高元数据；还没有 TS 版完整 TUI component/context、overlay、焦点、快捷键或自定义组件 API。
- lifecycle/provider/resources 等非交互 context 仍默认 `hasUi=false`，后续如果要暴露更丰富运行模式，需要进一步区分 print/headless/test/interactive。

### 优化 096：补齐扩展上下文 `ctx.mode` / `hasUI` 基础语义

状态：已完成

对应缺口：

- TS 扩展文档明确要求 `ctx.mode` 返回 `"tui"`、`"rpc"`、`"json"` 或 `"print"`，并用 `ctx.hasUI` 区分可用 UI 能力。Java 优化 095 只有 `interactive` boolean 和终端尺寸，扩展无法用 TS 文档同款的 mode guard 判断是否能运行 TUI 专属逻辑。

完成内容：

- `ExtensionCommandContext.UiContext` 从单一 `interactive` boolean 扩展为：
  - `mode`：规范化为 `tui` / `rpc` / `json` / `print`。
  - `hasUi`：`tui` / `rpc` 为 true，`json` / `print` 为 false。
  - `terminalColumns` / `terminalRows`：保留基础终端尺寸。
- 保留兼容构造器 `new UiContext(boolean interactive, int columns, int rows)`，并让旧 `interactive(...)` 工厂继续可用。
- 新增 `UiContext.tui(...)`、`UiContext.rpc()`、`UiContext.json()`、`UiContext.print()` 工厂，为后续 RPC/JSON 模式接入预留明确入口。
- `ExtensionCommandContext` 新增 `mode()`，并让 `hasUi()` 改为读取 `UiContext.hasUi()`，不再等同于 `interactive()`。
- 交互 slash extension command 使用 `UiContext.tui(...)`。
- 交互普通输入 `onInput` 事件也使用 `UiContext.tui(...)`，避免扩展在 slash command 与 input hook 中看到不一致的上下文。
- `CliEntryTest` 同时覆盖：
  - slash extension command 输出 `mode: tui`、`hasUi=true`、终端宽高。
  - `onInput` hook 能看到 `tui:true:120:40`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 共 21 个测试，0 failures，0 errors。

当前限制：

- Java 目前主要运行路径仍是行式交互和 print/headless 风格；`rpc` / `json` 工厂只是为后续模式迁移预留语义，还没有完整 RPC extension UI sub-protocol。
- `ctx.mode` 已可作为基础 guard，但还没有 TS 版 `ctx.ui.custom()`、component factory、overlay、焦点和快捷键能力。

### 优化 097：修复扩展 `sendUserMessage` 运行中缺少 delivery 的 guard

状态：已完成

对应缺口：

- TS 扩展文档要求：`sendUserMessage` 在 agent streaming / running 时必须传 `deliverAs`，否则抛错；Java 优化 036 已在 `AgentSession.sendUserMessage(..., delivery)` 实现该语义，但 `ExtensionCommandContext.sendUserMessage(String)` 仍走注入的 raw prompt sender，运行中会落到普通 prompt 的 “Cannot start a new prompt...” 错误，和 TS guard 不一致。

完成内容：

- `ExtensionCommandContext.sendUserMessage(String)` 在 session 非 idle 时改为调用 `session.sendUserMessage(message, null)`：
  - 复用 `AgentSession` 已有 running-state 校验；
  - 缺少 delivery 时稳定返回 `deliverAs is required while the agent is running`；
  - 后续 `sendUserMessage(String, STEER/FOLLOW_UP)` 仍走原队列语义。
- 空闲状态继续使用 `userMessageSender`：
  - 保留交互扩展命令里复用行式渲染路径的行为；
  - `/sendmsg` 等空闲扩展命令仍能立即触发一轮 assistant 输出。
- 之前优化 095 中记录的补充验证失败已被本轮修复消除。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 31 个测试、`CliEntryTest` 21 个测试，共 52 个测试，0 failures，0 errors。

当前限制：

- 后续优化 098 已补 text/image content blocks，优化 105 已补运行中扩展 source 标记；任意 TS content array 兼容层和 URL 图片策略仍未迁移。
- 运行中 guard 已与 TS 文档对齐，但 Java 仍不是动态 TS/JS 扩展运行时。

### 优化 098：补齐扩展 `sendUserMessage` text/image content blocks

状态：已完成

对应缺口：

- TS 扩展 `pi.sendUserMessage(content, options?)` 支持字符串，也支持 content array，例如 text + image。Java 此前 `ExtensionCommandContext.sendUserMessage(...)` 只接受字符串，无法让扩展把图片 content block 作为真实 user message 发送给模型。

完成内容：

- `AgentSession` 新增 content block 用户消息入口：
  - `promptRaw(List<Content>)`
  - `sendUserMessage(List<Content>, UserMessageDelivery)`
- 字符串版 `sendUserMessage(String, delivery)` 改为复用 content block 路径，避免文本/图片两套队列逻辑分叉。
- 用户 content 归一化为 text/image：
  - `Content.Text` 与 `Content.Image` 原样保留；
  - 其他非 user message content block 降级为文本 JSON，避免把 thinking/toolCall 等非法用户块塞进 provider 上下文。
- 空闲 content block 消息会立即触发 agent turn，并复用 session 持久化、`before_turn`、`before_agent_start`、nextTurn custom message、compaction 和 agent loop 逻辑。
- 运行中 content block 消息仍要求 delivery mode，并进入 steer/followUp 队列。
- `ExtensionCommandContext` 新增：
  - `sendUserMessage(List<Content> content)`
  - `sendUserMessage(List<Content> content, UserMessageDelivery delivery)`
- `AgentSessionRuntimeTest` 新增回归，验证扩展发送 text + image 后：
  - provider context 中的 user message 保留 `Content.Image`；
  - session 持久化消息中也保留同一 image content block。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 共 32 个测试，0 failures，0 errors；组合回归共 53 个测试，0 failures，0 errors。

当前限制：

- 仅明确支持 Java `Content.Text` / `Content.Image` 作为 user message content；TS 版更完整的 typed content array 兼容层、URL 图片获取/校验策略和 image resize policy 尚未接到扩展 API。
- 后续优化 106 已补行式交互扩展命令 `UserMessageSender` 的 content/source 透传；完整 TUI 图片预览仍未迁移。

### 优化 099：补齐扩展 `sendUserMessage` 队列 content block 保真回归

状态：已完成

对应缺口：

- 优化 098 已补扩展 `sendUserMessage(List<Content>)` 的 idle 路径 text/image 保真；TS 版同时支持运行中通过 `deliverAs: "steer" | "followup"` 投递多 part content。Java 需要明确覆盖运行中队列路径，避免后续维护时把图片块降级成文本摘要。

完成内容：

- 扩展 `sendUserMessage` 运行中队列回归改为发送 text + image content blocks：
  - `STEER` 队列消息保留 `Content.Image` 并进入下一次 provider context；
  - `FOLLOW_UP` 队列消息保留 `Content.Image` 并进入随后的 provider context。
- 保留现有 QueueUpdate 行为：
  - 队列事件仍输出文本摘要，便于 UI 展示；
  - 真实 provider context 和 session message 继续保留完整 content blocks。
- 补充 `userMessageContaining(...)` 测试 helper，按文本定位 user message 后断言完整 content 顺序与图片块。

涉及文件：

- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 共 32 个测试，0 failures，0 errors；组合回归共 53 个测试，0 failures，0 errors。

当前限制：

- 本轮是运行中队列路径的保真回归补强；URL 图片拉取、图片大小策略和全屏/TUI 图片预览仍未迁移。
- 后续优化 100 已补 QueueUpdate 结构化 image metadata；完整 TUI 附件列表和图片预览仍未迁移。

### 优化 100：补齐 `QueueUpdate` image metadata

状态：已完成

对应缺口：

- 优化 099 已证明扩展 `sendUserMessage` 运行中队列会保留 text/image content blocks，但 `QueueUpdate` session event 仍只有 `steering()` / `followUp()` 文本摘要。后续全屏/TUI 队列面板无法知道某条 queued user message 是否带有图片附件，也无法展示基础附件信息。

完成内容：

- `AgentSessionEvent.QueueUpdate` 新增结构化队列项：
  - `steeringItems`
  - `followUpItems`
- 每个 `QueueItem` 包含：
  - `text`：沿用既有队列文本摘要；
  - `images`：图片附件摘要列表。
- 每个 `QueueImage` 只暴露轻量 metadata：
  - `mimeType`
  - `source`：`data` / `url` / `unknown`
  - `dataLength`：图片数据长度，不包含图片原始数据；
  - `url`：URL 图片来源，data 图片保持 `null`。
- 保留旧的 `QueueUpdate(List<String> steering, List<String> followUp)` 构造器和旧字段，避免破坏已有订阅方。
- `AgentSessionRuntimeTest` 在扩展 `sendUserMessage` steer/followUp 队列测试中新增断言：
  - 文本摘要仍保持原行为；
  - `steeringItems` / `followUpItems` 输出对应图片 metadata；
  - provider context 仍保留完整 `Content.Image`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 共 32 个测试，0 failures，0 errors；组合回归共 53 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 session event 的附件摘要；尚未实现全屏/TUI 中的队列附件列表、图片缩略图或终端图片预览。
- 后续优化 101 已把 `QueueImage.dataLength` 从 base64 字符串长度改为 decoded byte length；非法 base64 仍会回退为字符串长度，避免事件生成失败。

### 优化 101：补齐 `QueueImage.dataLength` decoded byte length

状态：已完成

对应缺口：

- 优化 100 为 `QueueUpdate` 新增了图片附件 metadata，但 `QueueImage.dataLength` 初始实现使用 base64 字符串长度。TS 侧 UI/附件信息更需要接近真实图片大小的字节数；Java 当前 metadata 语义需要收敛到 decoded byte length，避免 TUI 后续展示误差。

完成内容：

- `AgentSession.queueImages(...)` 生成 `QueueImage` 时改为计算 decoded byte length：
  - 对合法 base64 `Content.Image.data()` 使用 `Base64.getDecoder().decode(...).length`；
  - 对空 data 返回 `0`；
  - 对非法 base64 回退为原字符串长度，避免 queued event 因单个坏附件抛错。
- `AgentSessionRuntimeTest` 更新扩展 steer/followUp 队列图片 metadata 断言：
  - `c3RlZXI=` 对应 5 字节；
  - `Zm9sbG93LXVw` 对应 9 字节。
- 文档同步把 QueueUpdate image metadata 标注为包含 decoded byte length。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 共 32 个测试，0 failures，0 errors；组合回归共 53 个测试，0 failures，0 errors。

当前限制：

- 本轮只修正 data 图片的长度语义；后续优化 102 已补 URL 图片 metadata 回归与空白 URL 规范化，但仍不会联网探测远端大小。
- 非法 base64 的回退长度主要用于稳健性，不代表真实图片大小。

### 优化 102：补齐 `QueueImage` URL source metadata 回归

状态：已完成

对应缺口：

- 优化 100/101 已让 `QueueUpdate` 输出图片附件 metadata，并让 data 图片的 `dataLength` 使用 decoded byte length；但 URL 图片路径只有代码分支，缺少回归覆盖。后续 TUI 队列附件列表需要稳定识别 `source=url`、保留 URL，并避免空白 URL 被当成有效地址。

完成内容：

- `QueueImage` compact constructor 规范化 URL：
  - `null` 或空白 URL 统一为 `null`；
  - 非空 URL 原样保留，供 UI/附件列表展示。
- `AgentSessionRuntimeTest` 的扩展 steer/followUp 队列回归新增 URL image content block：
  - provider context 继续保留完整 URL `Content.Image`；
  - `QueueUpdate.followUpItems().images()` 同时包含 data 图片和 URL 图片；
  - URL 图片 metadata 稳定输出 `source=url`、`dataLength=0` 和原始 URL。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 共 32 个测试，0 failures，0 errors；组合回归共 53 个测试，0 failures，0 errors。

当前限制：

- URL 图片 metadata 只保留来源和 URL，不主动联网获取远端大小、MIME 校验结果或缩略图。
- 后续优化 103 已补 data+URL 同时存在时的 data-first source 归一化，避免 metadata 自相矛盾。
- 完整 TUI 队列附件列表和图片预览仍未迁移。

### 优化 103：补齐 `QueueImage` source data-first 归一化

状态：已完成

对应缺口：

- 优化 100-102 已让 `QueueUpdate` 能表达 data 图片和 URL 图片 metadata；但如果同一个 `Content.Image` 同时包含内嵌 data 和 URL，旧逻辑会优先标记 `source=url`，同时又输出非零 `dataLength`。这会让后续 TUI 队列附件列表看到互相矛盾的 metadata。

完成内容：

- `AgentSession.queueImages(...)` 的 source 判定改为 data-first：
  - `data` 非空时稳定输出 `source=data`；
  - 只有无 data 且 URL 非空时输出 `source=url`；
  - 两者都缺失时输出 `source=unknown`。
- `QueueImage` compact constructor 进一步规范化 source：
  - `null` / 空白 source 统一为 `unknown`；
  - URL 继续对 `null` / 空白值归一为 `null`。
- `AgentSessionRuntimeTest` 的扩展队列回归新增 data+URL 图片：
  - provider context 继续保留完整 `Content.Image`；
  - QueueUpdate metadata 对该图片输出 `source=data`、decoded byte length、并保留 URL 字段供 UI 展示。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 共 32 个测试，0 failures，0 errors。

当前限制：

- 后续优化 104 已把 QueueUpdate metadata 接入行式交互摘要；完整 TUI 队列附件列表、图片缩略图和终端图片预览还未迁移。
- 如果后续要区分 `data+url` 复合来源，可以在 `QueueImage.source` 上扩展枚举；本轮保持现有 `data` / `url` / `unknown` 简单语义。

### 优化 104：补齐行式 `QueueUpdate` 图片附件摘要渲染

状态：已完成

对应缺口：

- 优化 100-103 已让扩展 `sendUserMessage` 运行中队列事件携带 text/image content blocks 和结构化图片 metadata，但行式交互订阅方此前只渲染 assistant/tool/skill diagnostic 事件。用户在交互模式里看不到扩展排队的 steer/follow-up 消息，也看不到 queued message 是否带图片附件。

完成内容：

- `InteractiveOutputRenderer` 新增 `renderQueueUpdate(...)`：
  - 非空 QueueUpdate 会以 `Queued user messages` 面板渲染；
  - 按 `steer[n]` / `follow-up[n]` 展示 queued user message 文本；
  - 对结构化 `QueueItem.images()` 输出图片摘要，包含 MIME、source、decoded byte length 和 URL；
  - 旧构造器产生的纯文本 `steering()` / `followUp()` 仍作为 fallback 渲染；
  - 空队列更新保持静默，避免 drain/clear 事件刷屏。
- `InteractiveModeRunner.executePrompt(...)` 的 session event 订阅新增 `QueueUpdate` 分支，行式交互能直接显示扩展运行中追加的 queued message。
- `InteractiveOutputRendererTest` 新增回归：
  - 覆盖 data 图片、URL 图片和 follow-up/steer 两类队列摘要；
  - 覆盖空 QueueUpdate 不输出；
  - 继续断言输出不超过终端宽度。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveOutputRenderer.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/InteractiveOutputRendererTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=InteractiveOutputRendererTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CliEntryTest,InteractiveOutputRendererTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`InteractiveOutputRendererTest` 共 18 个测试，0 failures，0 errors；组合回归共 71 个测试，0 failures，0 errors。

当前限制：

- 本轮是行式 CLI 的轻量摘要渲染，不含图片缩略图、terminal graphics 或全屏 TUI 队列附件管理。
- URL 图片只展示已有 URL，不主动联网校验 MIME、尺寸或远端可访问性。

### 优化 105：补齐扩展 `sendUserMessage` source 标记

状态：已完成

对应缺口：

- TS 版扩展调用 `sendUserMessage(...)` 时会以 `source: "extension"` 触发 prompt，便于 UI、审计、队列面板和后续权限语义区分用户手输消息与扩展投递消息。Java 优化 098-104 已补 text/image content blocks、运行中队列保真、QueueUpdate 图片 metadata 和行式摘要渲染，但 queued item 仍缺来源字段。

完成内容：

- `Message.User` 新增兼容字段 `source`：
  - 保留原两参构造器，现有调用不需要改动；
  - `null` / 空白 source 归一为 `null`，避免普通用户消息输出空来源。
- `AgentSession.sendUserMessage(...)` 新增 source 传递路径：
  - 扩展运行中 `steer` / `followUp` 投递会创建 `source=extension` 的 user message；
  - 空闲态 `promptRaw(List<Content>, source)` 也能保留 source，供直接会话 API 使用。
- `QueueUpdate.QueueItem` 新增 `source` 字段并保留旧构造器：
  - 队列事件现在暴露 `item.source()`；
  - 行式 `Queued user messages` 面板会渲染 `source=extension`。
- session JSON 写入/恢复和 compaction 恢复会保留 user message `source`。
- `images.blockImages` 过滤 user message 图片时保留 `source`，避免附件过滤后丢失扩展来源。

涉及文件：

- `packages/ai/src/main/java/works/earendil/pi/ai/model/Message.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/CompactionSupport.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/CodingAgentMessages.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveOutputRenderer.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/CodingAgentMessagesTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/InteractiveOutputRendererTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,InteractiveOutputRendererTest,CodingAgentMessagesTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl packages/ai,packages/agent,packages/coding-agent -am -Dtest=BuiltinProvidersTest,AgentLoopTest,AgentSessionRuntimeTest,CliEntryTest,InteractiveOutputRendererTest,CodingAgentMessagesTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。首条目标回归共 54 个测试，0 failures，0 errors；组合回归中 `BuiltinProvidersTest` 15 个测试、`AgentLoopTest` 1 个测试、coding-agent 相关测试 75 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 Java SPI 中扩展投递 user message 的基础 source 标记；仍未迁移 TS/JS 动态扩展运行时。
- 后续优化 106 已补行式扩展命令 `UserMessageSender` 的 source 透传；完整 TUI/RPC source 传播和权限上下文仍待后续补齐。

### 优化 106：补齐行式扩展命令即时发送 source 透传

状态：已完成

对应缺口：

- 优化 105 已让扩展 `sendUserMessage` 在 session 队列和直接 `promptRaw(..., source)` 路径中保留 `source=extension`。但行式扩展命令为了复用即时 assistant/tool 渲染，会通过自定义 `UserMessageSender` 调回 `InteractiveModeRunner.executePrompt(...)`；该路径此前只传字符串 prompt，导致扩展命令空闲态 `context.sendUserMessage(...)` 仍会落成无 source 的普通 user message。

完成内容：

- `ExtensionCommandContext.UserMessageSender` 从字符串 sender 扩展为 `List<Content> + source` sender：
  - 字符串 `sendUserMessage(...)` 会包装成 `Content.Text` 并传 `source=extension`；
  - content block 版 `sendUserMessage(List<Content>)` 在空闲态也走 sender，不再绕过行式即时渲染；
  - 删除旧的文本降级 helper，避免行式扩展命令把 content blocks 降级为纯文本。
- `InteractiveModeRunner` 新增 content/source 版 `executePrompt(...)`：
  - 复用原有 assistant/tool/QueueUpdate 渲染订阅；
  - 实际执行 `session.promptRaw(content, source)`；
  - 行式扩展命令 `UserMessageSender` 现在保留 source，同时仍能即时输出 assistant response。
- `CliEntryTest` 的 `/sendmsg` 回归新增断言：
  - 扩展命令触发的 user message session entry 带 `source=extension`；
  - 原有模型响应即时渲染和 stats 行为保持不变。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/extensions/ExtensionCommandContext.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest,AgentSessionRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 21 个测试、`AgentSessionRuntimeTest` 32 个测试，共 53 个测试，0 failures，0 errors。

当前限制：

- 后续优化 107 已补 extension custom message 进入 LLM context 时的 source 保留，优化 108 已补 session 显式持久化；完整 TUI/RPC extension source 传播、权限上下文和动态 TS/JS 扩展运行时仍待补。
- 行式自定义 sender 已保留 content blocks，但仍没有全屏附件管理或图片预览。

### 优化 107：补齐扩展 custom message 的 LLM context source 保留

状态：已完成

对应缺口：

- 优化 105-106 已让扩展投递的 user message 保留 `source=extension`，但扩展 `before_agent_start` 和 `sendMessage(...)` 注入的 custom message 在转换为 LLM user context 时仍没有 source。后续 UI、审计和权限语义无法稳定区分“用户手输上下文”和“扩展注入上下文”。

完成内容：

- `CodingAgentMessages.CustomMessage` 新增兼容字段 `source`：
  - 旧构造器和旧 `createCustomMessage(...)` 工厂方法保持可用；
  - `null` / 空白 source 继续归一为空，避免普通 custom message 被误标记。
- `AgentSession.buildExtensionCustomMessage(...)` 现在为扩展 custom message 写入 `source=extension`：
  - 覆盖 `before_agent_start` 注入；
  - 覆盖空闲 `sendMessage(...)`；
  - 覆盖运行中 `STEER` / `FOLLOW_UP`；
  - 覆盖 `NEXT_TURN` 在下一轮进入 provider context 的路径。
- `CompactionSupport` 从 session custom message 恢复上下文时对非 bash custom message 使用 `source=extension`，避免 session 重载后扩展注入上下文丢失来源。
- `CodingAgentMessages.convertToLlm(...)` 转换 `CustomMessage` 时会把 source 写入 `Message.User.source()`。
- 回归测试新增断言：
  - 普通 custom message source 仍为空；
  - extension custom message 转换后 source 为 `extension`；
  - `before_agent_start` 注入的 string/object custom message 在 provider context 中带 `source=extension`；
  - `sendMessage` 的 idle、steer、followUp、nextTurn 四条路径进入 provider context 时都带 `source=extension`，普通用户 prompt 仍不带 source。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/CodingAgentMessages.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/CompactionSupport.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/CodingAgentMessagesTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=AgentSessionRuntimeTest,CodingAgentMessagesTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`AgentSessionRuntimeTest` 32 个测试、`CodingAgentMessagesTest` 4 个测试，共 36 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 Java JAR SPI 内部 custom message 到 LLM context 的来源保留；完整 TS/JS 动态扩展运行时、TUI/RPC source 展示和权限策略仍待迁移。
- 后续优化 108 已补 `SessionEntry.CustomMessageEntry.source` 显式持久化；旧 session 缺失 source 的非 bash custom_message 仍按 extension 兼容恢复。

### 优化 108：补齐扩展 custom_message source 显式持久化

状态：已完成

对应缺口：

- 优化 107 已让扩展 custom message 转换为 LLM context 时保留 `source=extension`，但 session JSONL 的 `custom_message` entry 本身没有独立 source 字段。新会话持久化后仍需要靠恢复逻辑推断来源，不利于后续 TUI/RPC 展示、审计和权限策略稳定读取。

完成内容：

- `SessionEntry.CustomMessageEntry` 新增兼容字段 `source`：
  - 保留旧构造器，旧调用不需要改动；
  - `null` / 空白 source 归一为空。
- `SessionEntryCodec` 对 `custom_message` 支持 source 编解码：
  - 编码时仅在 source 非空时写入 `"source"`；
  - 解码旧 JSONL 缺失 source 时保持 `null`。
- `SessionManager.appendCustomMessage(...)` 新增带 source 的 overload，并在 fork/clone 重写 parent 时保留 source。
- `AgentSession.persistCustomMessage(...)` 会把 `CodingAgentMessages.CustomMessage.source()` 写入 session entry：
  - 扩展 `before_agent_start` 注入；
  - 扩展 `sendMessage(...)` idle / steer / followUp / nextTurn；
  - 这些路径新写入的 `custom_message` 均显式带 `source=extension`。
- `CompactionSupport` 从 session 恢复 custom message 时优先读取 entry source；旧 session 缺字段时继续按 `extension` 兼容恢复，避免已有迁移记录丢来源。
- 回归测试新增：
  - `SessionManagerTest` 覆盖 custom_message source JSON 编解码和旧 JSON 兼容；
  - `AgentSessionRuntimeTest` 覆盖 before-agent-start 和 sendMessage 写入的 session custom message source。

涉及文件：

- `packages/agent/src/main/java/works/earendil/pi/agent/session/SessionEntry.java`
- `packages/agent/src/main/java/works/earendil/pi/agent/session/SessionEntryCodec.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/session/SessionManager.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AgentSession.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/CompactionSupport.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/session/SessionManagerTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AgentSessionRuntimeTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=SessionManagerTest,AgentSessionRuntimeTest,CodingAgentMessagesTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`SessionManagerTest` 7 个测试、`AgentSessionRuntimeTest` 32 个测试、`CodingAgentMessagesTest` 4 个测试，共 43 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 session JSONL 对 Java JAR SPI custom message source 的显式持久化；后续优化 109 已补 HTML export source badge，完整 TUI/RPC source 展示、权限策略和 TS/JS 动态扩展运行时仍待迁移。
- 旧 session 中缺少 source 的非 bash custom_message 会继续按 extension 兼容恢复；如果未来引入非扩展来源的 custom_message，应在写入时明确指定 source。

### 优化 109：补齐 HTML export custom_message source badge

状态：已完成

对应缺口：

- 优化 108 已把扩展 `custom_message.source` 显式写入 session JSONL，但 HTML export 仍只展示 custom type 和 payload。用户导出会话后无法看出 custom message 是扩展注入的上下文，也无法在审计导出里检查 source。

完成内容：

- `HtmlExporter.renderCustomEntry(...)` 新增 source badge：
  - `custom_message` 或 `custom` entry 带 `source` 字段时，在 header 中展示 `source=<value>`；
  - 缺失 source 的旧 entry 保持原样，不额外输出空 badge；
  - source 值复用现有 HTML escaping，避免导出 XSS。
- HTML export 样式新增 `.source-badge`，以次级元信息展示 source，不影响现有 payload details。
- `HtmlExporterTest` 增加带恶意 source 的 `custom_message`：
  - 断言 source 被转义后展示；
  - 断言不会生成可执行 `<script>`；
  - 继续覆盖 custom/custom_message、图片、tool、skill wrapper 的原有 XSS 回归。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/export/HtmlExporter.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/export/HtmlExporterTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=HtmlExporterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`HtmlExporterTest` 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 HTML export 的 source 可见性；后续优化 110 已补行式 session tree source 展示，TUI/RPC 面板和权限策略仍未消费 custom_message source。
- HTML export 仍是基础会话视图，不是 TS 版完整高保真 viewer、侧边栏树、主题映射或完整 tool renderer。

### 优化 110：补齐行式 session tree custom_message source 展示

状态：已完成

对应缺口：

- 优化 108 已把 `custom_message.source` 显式写入 session JSONL，优化 109 已让 HTML export 展示 source；但行式 `/tree` 仍只输出 `custom_message <type>`，交互查看 session 分支时无法识别扩展注入的 custom message。

完成内容：

- `InteractiveModeRunner.entrySummary(...)` 在 `SessionEntry.CustomMessageEntry` 带 source 时追加：
  - `source=<value>`
  - 缺失 source 的旧 entry 保持原输出。
- 新增 `treeSourceLabel(...)`：
  - source 为空时不输出；
  - source 非空时复用 `previewText(...)` 单行归一化，避免异常 JSONL source 破坏树形输出布局。
- `CliEntryTest` 在交互 session 中加入 `extension.context` custom_message，并断言 `/tree` 输出：
  - `custom_message extension.context source=extension`
  - 原有 bash custom_message 和普通 message tree 输出保持可见。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 21 个测试，0 failures，0 errors。

当前限制：

- 本轮只补行式 `/tree` 的 source 可见性；完整 TUI/RPC session tree、权限策略和动态 TS/JS 扩展运行时仍待迁移。
- `/tree` 仍是行式文本树，不是 TS 版全屏 tree selector、搜索、折叠或富交互会话图。

### 优化 111：补齐 HTML export user message source badge

状态：已完成

对应缺口：

- 优化 105/106/107 已为扩展投递的 user/custom message 建立 `source=extension` 语义，优化 109 已让 custom message 在 HTML export 中展示 source；但普通 `message.message.source` 仍不会出现在导出 header，导出的会话无法区分用户手输消息和扩展投递消息。

完成内容：

- `HtmlExporter` 在普通 message header 中读取 `message.source`：
  - source 非空时展示 `source=<value>`；
  - 缺失 source 的旧 session 保持原样；
  - 复用现有 `.source-badge` 样式和 HTML escaping。
- `sourceBadge(...)` 拆出字符串重载，让 custom/custom_message 和普通 message 共享同一转义路径。
- `HtmlExporterTest` 给 user message 加入恶意 source：
  - 断言 user message source badge 被安全转义；
  - 继续断言不会生成可执行 `<script>`；
  - 保持 custom_message source badge、skill wrapper、tool、图片内容的现有 XSS 回归。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/export/HtmlExporter.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/export/HtmlExporterTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=HtmlExporterTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`HtmlExporterTest` 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 HTML export 普通 message 的 source 可见性；完整 TUI/RPC 面板、权限策略和 TS/JS 动态扩展运行时仍待迁移。
- HTML export 仍是基础会话视图，不是 TS 版完整高保真 viewer、侧边栏树、主题映射或完整 tool renderer。

### 优化 112：补齐行式 session tree user message source 展示

状态：已完成

对应缺口：

- 优化 105/106 已让扩展投递的 user message 持久化 `source=extension`，优化 111 已让 HTML export 展示普通 message source；但行式 `/tree` 仍只给 `custom_message` 展示 source，无法在会话树里识别某个 user 节点是用户手输还是扩展命令投递。

完成内容：

- `InteractiveModeRunner.entrySummary(...)` 在 `SessionEntry.MessageEntry` 带 `message.source` 时追加：
  - `source=<value>`
  - 缺失 source 的旧消息保持 `message <role> <preview>` 原输出。
- 复用已有 `treeSourceLabel(...)`：
  - source 为空时不输出；
  - source 非空时用 `previewText(...)` 做单行归一化，避免异常 source 破坏树输出布局。
- `CliEntryTest.interactiveExecutesExtensionCommandsWithoutPromptingModel` 增加 `/tree` 调用，并断言扩展命令投递的 user message 输出：
  - `message user source=extension from extension`

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#interactiveExecutesExtensionCommandsWithoutPromptingModel -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补行式 `/tree` 对 user message source 的可见性；完整 TUI/RPC session tree、权限策略和动态 TS/JS 扩展运行时仍待迁移。
- `/tree` 仍是行式文本树，不是 TS 版全屏 tree selector、搜索、折叠或富交互会话图。

### 优化 113：补齐行式 `/session` source 统计

状态：已完成

对应缺口：

- 优化 110/112 已让行式 `/tree` 展示 user/custom_message source；但 `/session` 状态面板仍只显示消息数量、token、skills/tools，无法快速看出当前 active branch 中是否包含扩展投递的 user message 或扩展注入的 custom_message。

完成内容：

- `InteractiveModeRunner.handleSession(...)` 新增 source 统计行：
  - 只在 active branch 中存在 source 时输出 `sources:`；
  - 分别统计普通 message 和 custom_message；
  - 缺失 source 的旧 session 不输出该行，保持原有空会话和普通会话输出。
- 新增 `sessionSourceSummary(...)`、`incrementSource(...)`、`sourceCountsLabel(...)`：
  - 统计基于 `SessionManager.branch()`，与 `/session` 的 branch entries 视角一致；
  - source 使用 `trim()` 聚合；
  - 输出时复用 `previewText(...)` 单行归一化，避免异常 source 破坏状态面板布局。
- `CliEntryTest.interactiveExecutesExtensionCommandsWithoutPromptingModel` 在扩展 `/sendmsg` 后调用 `/session`，并断言：
  - `sources: messages[extension=1]`
  - 同一测试继续覆盖 `/tree` 的 `message user source=extension from extension`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#interactiveExecutesExtensionCommandsWithoutPromptingModel -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补行式 `/session` 的 source 统计；完整 TUI/RPC session inspector、权限策略和动态 TS/JS 扩展运行时仍待迁移。
- `/session` 仍是只读行式状态面板，不是 TS 版全屏 session inspector 或可交互分支/来源审计视图。

### 优化 114：补齐 RPC `session_info` source 统计

状态：已完成

对应缺口：

- 优化 113 已让行式 `/session` 输出当前 branch 的 source 统计；但 RPC 模式没有对应的会话信息接口，外部 UI、后续全屏/TUI 适配层或调试客户端无法通过结构化 JSON 获取 session stats 和 source 审计信息。

完成内容：

- `RpcModeRunner` 新增 JSON-RPC method：
  - `session_info`
- `session_info` 返回结构化 JSON：
  - `schemaVersion`
  - `sessionId`、`name`、`sessionFile`、`cwd`、`persisted`、`leaf`
  - `entries`、`branchEntries`
  - `thinking`、`skills`、`tools`
  - `model`
  - `messages`
  - `tokens`
  - `sources.messages`
  - `sources.customMessages`
- source 统计基于 `SessionManager.branch()`：
  - 普通 `message.message.source` 进入 `sources.messages`；
  - `custom_message.source` 进入 `sources.customMessages`；
  - 空 source 不输出；
  - source 名称和值使用 Jackson `ObjectNode` 生成 JSON，避免手写字符串转义风险。
- `CliEntryTest.rpcModeHandlesJsonRequestsAndDiagnostics` 预置带 `source=extension` 的 user message 和 custom_message，并断言 RPC 响应包含：
  - `sources.messages.extension=1`
  - `sources.customMessages.extension=1`

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 RPC 的基础 session info/source stats；还不是 TS 版完整 RPC session tree、全屏 session inspector 或可交互来源审计 UI。
- RPC 模式仍未提供 session 切换、tree navigation、rename/delete/fork 等完整结构化会话管理接口。

### 优化 115：补齐 RPC `session_tree` 基础结构化输出

状态：已完成

对应缺口：

- 优化 114 已补 RPC `session_info`，但 RPC 模式仍无法获取当前 session 的分支树。外部 UI 或后续全屏/TUI 适配层只能拿到统计，不能结构化呈现 entry 层级、当前 leaf、label、summary 和 source。

完成内容：

- `RpcModeRunner` 新增 JSON-RPC method：
  - `session_tree`
- `session_tree` 返回结构化 JSON：
  - `schemaVersion`
  - `sessionId`
  - `leaf`
  - `entries`
  - `roots`
- 每个 tree node 包含：
  - `id`
  - `parentId`
  - `type`
  - `timestamp`
  - `current`
  - `summary`
  - `label`
  - `labelTimestamp`
  - `children`
- 针对常见 entry 补充结构化字段：
  - message：`role`、`source`
  - custom/custom_message：`customType`、`source`
  - model/thinking/tools/summary/label/session_info/leaf 的基础字段。
- RPC tree summary 与行式 `/tree` 保持一致：
  - user message source 展示为 `message user source=extension ...`
  - custom_message source 展示为 `custom_message <type> source=extension`
  - 预览文本做单行归一化和长度截断。
- `CliEntryTest.testRpcModeRunnerExecution` 增加 `session_tree` 请求，并断言：
  - response 包含 `roots`；
  - source message summary 保留；
  - custom_message source summary 保留；
  - 节点里有 `source=extension`；
  - 输出包含 `children` 结构。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 RPC session tree 的只读结构化输出；还不是 TS 版完整全屏 tree selector、搜索、折叠、跳转或分支操作 UI。
- RPC 模式仍未提供 session 切换、rename/delete/fork 等完整结构化会话管理接口。

### 优化 116：补齐 RPC `session_tree` external session/branch 参数

状态：已完成

对应缺口：

- 优化 115 已补 RPC `session_tree` 基础结构化输出，但只能查看当前 runtime session 的当前 leaf。TS 版会话选择/树浏览需要跨 session 文件查看，并能针对指定 branch 标记当前节点；RPC 客户端此前仍无法结构化检查外部 session 或指定 branch 的树。

完成内容：

- `RpcModeRunner` 的 `session_tree` 支持参数：
  - `params.session` / 顶层 `session`：外部 session JSONL 路径；
  - `params.branch` / 顶层 `branch`：用于 `leaf` 和 node `current` 标记的 branch entry id。
- 外部 session 路径复用现有 `diagnosticsSession(...)` 解析：
  - 相对路径按当前 session cwd 解析；
  - 绝对路径直接打开；
  - 缺省时读取当前 session。
- branch 参数不会修改当前 runtime session leaf，只影响本次 JSON 输出。
- branch 非 `root` 且不存在时返回 JSON-RPC `-32602` 参数错误：
  - `Session tree branch not found: <id>`
- `session_tree` 响应新增 `sessionFile` 字段，便于外部 UI 区分当前/外部 session 来源。
- `CliEntryTest.testRpcModeRunnerExecution` 增加外部 session tree 请求：
  - `session=<externalSessionFile>`
  - `branch=<externalBranch>`
  - 断言响应为 external session id；
  - 断言 `leaf` 等于 external branch；
  - 断言对应 node 带 `current=true`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 RPC `session_tree` 的外部 session/branch 只读查看；还不是 TS 版完整 session selector/tree selector。
- RPC 模式仍未提供 session 切换、rename/delete/fork 等完整结构化会话管理接口。

### 优化 117：补齐 RPC `session_list` 基础结构化列表/搜索

状态：已完成

对应缺口：

- 行式 `/resume` 已支持当前项目和 `--all find` 的 session 列表/搜索，但 RPC 模式此前只有 `session_info` 和 `session_tree`。外部 UI 或后续全屏 session selector 缺少结构化 session 列表数据源，仍需要解析行式 `/resume` 文本或直接扫描文件。

完成内容：

- `RpcModeRunner` 新增 JSON-RPC method：
  - `session_list`
- `session_list` 支持参数：
  - `params.all` / 顶层 `all`：是否合并当前 session dir 与父级 sessions root 下的所有项目 session；
  - `params.query` / 顶层 `query`：按 session id、name、cwd、first message、all messages text 搜索；
  - `params.limit` / 顶层 `limit`：返回条数，默认 20，最小 1。
- 返回结构化 JSON：
  - `schemaVersion`
  - `scope`
  - `query`
  - `total`
  - `limit`
  - `currentSessionId`
  - `currentSessionFile`
  - `items`
- 每个 item 包含：
  - `index`
  - `sessionId`
  - `name`
  - `path`
  - `cwd`
  - `parentSessionPath`
  - `created`
  - `modified`
  - `messageCount`
  - `firstMessage`
  - `current`
- `all=true` 时复用行式 `/resume --all` 的合并策略：
  - 当前 session dir 的 session 先纳入；
  - 再扫描父级 sessions root；
  - 按 path 去重；
  - 按 modified 倒序输出。
- `CliEntryTest.testRpcModeRunnerExecution` 增加 `session_list` 请求：
  - `all=true`
  - `query=rpc extension`
  - `limit=5`
  - 断言返回当前 session、firstMessage 和 `current=true`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 RPC session list/search 的只读结构化数据源；还不是 TS 版完整全屏 session selector。
- RPC 模式仍未提供 session resume/switch、rename/delete/fork 等结构化变更接口。

### 优化 118：补齐 RPC `session_list` 分页元数据

状态：已完成

对应缺口：

- 优化 117 已补 RPC `session_list` 基础结构化列表/搜索，但响应只有 `total` 和 `limit`，没有 `offset`、`returned`、`hasMore`。后续全屏 session selector 或外部 UI 无法稳定判断当前页是否截断、是否需要继续加载，也无法请求后续页。

完成内容：

- `session_list` 新增参数：
  - `params.offset` / 顶层 `offset`：非负偏移，默认 0。
- `session_list` 响应新增字段：
  - `offset`
  - `returned`
  - `hasMore`
- 分页行为：
  - `offset` 超过结果数时返回空 `items`；
  - `limit` 仍默认 20，最小 1；
  - item `index` 继续使用全量列表中的 1-based 序号，便于 selector 显示和后续定位。
- `CliEntryTest.testRpcModeRunnerExecution` 增加 `session_list all=true limit=1` 请求，并断言：
  - `offset=0`
  - `limit=1`
  - `returned=1`
  - `hasMore=true`

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 RPC session list 的分页元数据；还不是 TS 版完整全屏 session selector。
- RPC 模式仍未提供 session resume/switch、rename/delete/fork 等结构化变更接口。

### 优化 119：补齐 RPC `session_info` external session/branch 参数

状态：已完成

对应缺口：

- 优化 114 已补 RPC `session_info` 当前 runtime 的结构化统计和 source 统计，但只能查看当前 session 当前 leaf。TS 版 session selector/tree selector 需要在不切换运行时的情况下读取外部 session 文件和指定 branch 的摘要；此前 RPC 客户端只能用 `session_tree` 看外部树，不能直接获取同一目标 branch 的 message/tokens/source summary。

完成内容：

- `session_info` 新增参数：
  - `params.session` / 顶层 `session`：外部 session JSONL 路径，支持相对当前 cwd 解析；
  - `params.branch` / 顶层 `branch`：指定 branch entry id，支持 `root`。
- 不传参数时保持原有当前 runtime 输出：
  - 继续使用 `AgentSession.stats()`；
  - 保留当前 model / thinking / skills / tools 信息。
- 传 `session` 或 `branch` 时改为只读打开目标 `SessionManager`，从指定 branch entries 计算：
  - `entries`
  - `branchEntries`
  - `messages.user`
  - `messages.assistant`
  - `messages.tool`
  - `messages.total`
  - `tokens.input`
  - `tokens.output`
  - `tokens.cache`
  - `tokens.reasoning`
  - `tokens.total`
  - `sources.messages`
  - `sources.customMessages`
- 指定 branch 不存在时返回 JSON-RPC `-32602`，避免客户端把错误 session/branch 当作空统计。
- 外部只读路径下 runtime 专属字段 `thinking` 为 `null`，`skills` / `tools` 为 `0`，避免误用当前 runtime 状态污染外部 session 摘要。
- `CliEntryTest.testRpcModeRunnerExecution` 增加外部 `session_info` 请求，并断言：
  - 返回 external session id / sessionFile；
  - 返回指定 external branch；
  - `entries=2` / `branchEntries=2`；
  - messages 统计来自外部 branch；
  - `sources.messages.external=1`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 RPC `session_info` 的外部 session/branch 只读摘要；还不是 TS 版完整 session selector/tree selector。
- 外部 session info 不创建完整 `AgentSession`，因此不会推断外部 runtime 的 model display name、active skills/tools 或 thinking level，只返回 JSONL 可证明的 branch 统计。

### 优化 120：补齐 RPC `session_rename` 外部会话改名

状态：已完成

对应缺口：

- 会话 UX 已有行式 `/name` 和 `/resume rename`，但 RPC 模式此前只有 `session_info` / `session_tree` / `session_list` 只读接口。后续全屏 session selector 或外部 UI 即使能列出、搜索和查看外部 session，也无法通过结构化 RPC 对 session 执行 rename，只能回退到解析行式命令或直接写 JSONL。

完成内容：

- `RpcModeRunner` 新增 JSON-RPC 方法：
  - `session_rename`
- `session_rename` 支持参数：
  - `params.session` / 顶层 `session`：目标 session JSONL 路径；缺省时改名当前 session；
  - `params.name` / 顶层 `name`：新 session 名称；
  - `params.clear` / 顶层 `clear`：为 true 时清空名称。
- 改名语义复用 `SessionManager.appendSessionInfo(...)`：
  - 不重写历史 entry；
  - 通过追加 `session_info` entry 记录最新名称；
  - 与行式 `/name` 和 `/resume rename` 保持一致。
- `session_rename` 响应返回结构化结果：
  - `schemaVersion`
  - `status`
  - `sessionId`
  - `sessionFile`
  - `entryId`
  - `name`
- 缺少 `name` 且没有 `clear=true` 时返回 JSON-RPC `-32602`。
- 外部 session 路径复用 `session_info` / `session_tree` 的路径解析逻辑，支持相对当前 cwd。
- `CliEntryTest.testRpcModeRunnerExecution` 增加外部 `session_rename` 请求，并随后用外部 `session_info` 重新读取同一 session，断言：
  - `session_rename` 返回 external session id / sessionFile / `status=renamed`；
  - 后续 `session_info` 返回 `name=Renamed External`；
  - external session entries 从 2 增加到 3；
  - 指定 branch 的 message/source 统计仍保持原 branch 结果。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 session rename；RPC 模式仍未提供 session switch/resume、delete、fork/clone 等结构化变更接口。
- 改名使用追加 `session_info` entry 的既有语义，不提供历史名称压缩或撤销。

### 优化 121：补齐 RPC `session_delete` 外部会话删除

状态：已完成

对应缺口：

- 优化 120 已补 RPC `session_rename`，但 RPC 模式仍缺少与行式 `/resume delete` 对齐的结构化删除能力。后续全屏 session selector 或外部 UI 可以列出、搜索、查看和改名 session，但仍无法通过 JSON-RPC 删除目标 session 文件。

完成内容：

- `RpcModeRunner` 新增 JSON-RPC 方法：
  - `session_delete`
- `session_delete` 支持参数：
  - `params.session` / 顶层 `session`：目标 session JSONL 路径，支持相对当前 cwd 解析。
- 删除语义复用行式 `/resume delete` 的安全边界：
  - 必须显式传入目标 `session`；
  - 拒绝删除当前 runtime 正在使用的 session；
  - 删除前通过 `SessionManager.buildSessionInfo(...)` 读取 session id，避免对不存在或不可解析的目标返回误导性成功。
- `session_delete` 响应返回结构化结果：
  - `schemaVersion`
  - `status=deleted`
  - `sessionId`
  - `sessionFile`
  - `deleted=true`
- `diagnosticsSession(...)` 与 `session_delete` 共用 `resolveSessionPath(...)`，使 RPC 中外部 session 路径解析保持一致。
- `CliEntryTest.testRpcModeRunnerExecution` 增加外部 `session_delete` 请求，并断言：
  - 返回 external session id / sessionFile / `deleted=true`；
  - 测试运行后 external session 文件确实不存在。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 session delete；RPC 模式仍未提供 session switch/resume、fork/clone 等结构化变更接口。
- 删除仍是直接删除目标 JSONL 文件，未实现 TS 全屏 selector 的确认 UI 或回收站/撤销体验。

### 优化 122：补齐 RPC `session_switch` 外部会话切换

状态：已完成

对应缺口：

- 优化 117-121 已补 RPC session list/info/tree/rename/delete，但 RPC 模式仍不能切换当前 runtime session。后续全屏 session selector 或外部 UI 即使能选择目标 session，也无法通过结构化 RPC 让后续 prompt 写入该 session，只能回退到行式 `/resume`。

完成内容：

- `RpcModeRunner` 新增 JSON-RPC 方法：
  - `session_switch`
  - `session_resume` alias
- `session_switch` 支持参数：
  - `params.session` / 顶层 `session`：目标 session JSONL 路径，支持相对当前 cwd 解析。
- 切换语义复用 `AgentSessionRuntime.switchSession(...)`：
  - 保留 `session_before_switch` extension hook；
  - 使用目标 session JSONL 中记录的 cwd；
  - 继续执行 runtime teardown/recreate/rebind 逻辑。
- RPC loop 新增 `RpcSessionState`：
  - 保存当前 `AgentSession`；
  - 保存当前 `SkillDiagnosticHistory`；
  - 在 session switch 后重新订阅新 session 事件；
  - 保证后续 `prompt`、`session_info`、skill diagnostics 等命令读取/写入新 session。
- `session_switch` 响应返回结构化结果：
  - `schemaVersion`
  - `status=switched|cancelled`
  - `cancelled`
  - `reason`
  - `sessionId`
  - `sessionFile`
  - `previousSessionFile`
  - `currentSessionFile`
  - `cwd`
- `CliEntryTest.testRpcModeRunnerExecution` 增加外部 `session_switch` 请求，并在切换后继续发送 `prompt` 和 `session_info`，断言：
  - `session_switch` 返回目标 session id / sessionFile；
  - 后续 `prompt` 返回 ok；
  - 后续当前 `session_info` 已是目标 session；
  - prompt 写入目标 session，messages 统计为 `user=1 assistant=1 total=2`；
  - `runtime.session()` 最终指向目标 session。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 session switch/resume；RPC 模式仍未提供 fork/clone 等结构化分支变更接口。
- `session_switch` 当前要求明确传入 session 文件路径，还没有按 index/id/query 解析 session list item。

### 优化 123：补齐 RPC `session_fork` / `session_clone` 分支变更

状态：已完成

对应缺口：

- 优化 122 已补 RPC `session_switch` / `session_resume`，但 RPC 模式仍缺少与行式 `/fork`、`/clone` 对齐的结构化分支变更能力。后续全屏 session selector/tree selector 或外部 UI 可以切换、改名、删除 session，但不能从指定 entry 分叉或克隆当前 active branch。

完成内容：

- `RpcModeRunner` 新增 JSON-RPC 方法：
  - `session_fork`
  - `session_clone`
- `session_fork` 支持参数：
  - `params.entryId` / 顶层 `entryId`：目标 entry id；
  - `params.position` / 顶层 `position`：`before` 或 `at`，默认 `before`。
- `session_clone` 无参数，克隆当前 active branch；当前 session 没有 leaf 时复用 `newSession(parentSession)` 的既有 clone 语义。
- 分支变更语义复用 `AgentSessionRuntime.fork(...)` / `newSession(...)`：
  - 保留 `session_before_fork` extension hook；
  - 保留 user message `before` 分叉的 selected prompt 语义；
  - 完成后通过 `RpcSessionState.rebind(...)` 重新绑定 RPC 当前 session 和事件订阅。
- `session_fork` 响应返回结构化结果：
  - `schemaVersion`
  - `status=forked|cancelled`
  - `cancelled`
  - `reason`
  - `sessionId`
  - `sessionFile`
  - `previousSessionFile`
  - `currentSessionFile`
  - `cwd`
  - `position`
  - `selected`
- `session_clone` 响应返回结构化结果：
  - `schemaVersion`
  - `status=cloned|cancelled`
  - `cancelled`
  - `reason`
  - `sessionId`
  - `sessionFile`
  - `previousSessionFile`
  - `currentSessionFile`
  - `cwd`
- `CliEntryTest.testRpcModeRunnerExecution` 增加：
  - 预置 switch 目标 session 的 user entry，并保存 entry id；
  - `session_switch` 后调用 `session_fork entryId=<id> position=at`；
  - 断言 fork 后当前 `session_info` 的 message 统计来自目标分支；
  - 调用 `session_clone` 并断言返回 `status=cloned`；
  - 最终 runtime 指向克隆后的新 session，且 branch 保留 1 条消息。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 RPC 层 fork/clone 基础接口；仍不是 TS 版全屏 tree selector、历史 user message selector 或富导航体验。
- `session_fork` 当前要求明确传入 entry id，还没有按树节点 index 或搜索结果定位。

### 优化 124：补齐 RPC `session_tree` flat selector 数据

状态：已完成

对应缺口：

- 优化 115/116 已补 RPC `session_tree` 的 nested `roots` 和 external session/branch 参数，但后续全屏 tree selector 或外部 UI 仍需要自己递归解析树、计算 depth/index/child count，并从节点类型推导可用操作。TS 版 tree selector 需要可直接渲染和执行动作的数据基础。

完成内容：

- `session_tree` 新增参数：
  - `params.flat` / 顶层 `flat`：为 true 时追加扁平 selector 数据。
- `flat=true` 时响应新增 `items` 数组，按 depth-first 顺序输出：
  - `index`
  - `depth`
  - `id`
  - `parentId`
  - `type`
  - `timestamp`
  - `current`
  - `summary`
  - `label`
  - `labelTimestamp`
  - `hasChildren`
  - `childCount`
  - entry details（如 `role`、`source`、`customType` 等）
  - `actions`
- `actions` 提供 selector 可直接使用的基础参数：
  - `branch`
  - `forkAtEntryId`
  - `forkBeforeEntryId`（仅 user message 节点非空）
- 默认不传 `flat` 时保持原有响应形状，只返回 nested `roots`，避免无谓增大普通调用 payload。
- `CliEntryTest.testRpcModeRunnerExecution` 将 external `session_tree` 请求改为 `flat=true`，并断言：
  - 响应包含 `items`；
  - 第一个 item `index=1 depth=0`；
  - external branch item `index=2 depth=1`；
  - item actions 包含 `branch` / `forkAtEntryId` / `forkBeforeEntryId`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 selector 数据基础；服务端 query/filter 已在优化 125 补齐，flat items 分页已在优化 126 补齐，仍未实现 TS 版全屏 tree selector UI、折叠或键盘导航。
- `items` collapse 状态入参已在优化 127 补齐。

### 优化 125：补齐 RPC `session_tree` flat query 过滤

状态：已完成

对应缺口：

- 优化 124 已补 `session_tree flat=true` 的 selector 扁平数据，但外部 UI 仍需要自己在完整 `items` 列表上做搜索过滤。TS 版 tree selector 的搜索体验需要服务端直接返回可渲染的命中列表，并保留总量信息。

完成内容：

- `session_tree` 新增参数：
  - `params.query` / 顶层 `query`：仅在 `flat=true` 时过滤扁平 `items`。
- `flat=true` 响应新增：
  - `itemQuery`：本次使用的过滤词，无过滤时为 `null`。
  - `itemTotal`：未过滤前的 depth-first item 总数。
  - `itemReturned`：过滤后返回的 item 数。
- query 会匹配 item 的 `id`、`parentId`、`type`、`summary`、`label`、`role`、`source`、`customType`、`name`、`targetId` 等渲染和动作相关字段。
- 过滤只影响 `items`；nested `roots` 仍完整返回，便于客户端同时保留完整树上下文。
- `index` 保持未过滤 depth-first 原始序号，客户端可以稳定定位原树节点。
- `CliEntryTest.testRpcModeRunnerExecution` 将 external `session_tree flat=true` 请求改为携带 `query`，并断言：
  - 响应包含 `itemQuery`；
  - filtered `items` 只返回命中的 root message；
  - `itemTotal=2` 且 `itemReturned=1`；
  - item actions 仍包含 `branch` / `forkAtEntryId` / `forkBeforeEntryId`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 `session_tree flat=true` 的服务端 query 过滤；仍未实现 TS 版全屏 tree selector UI、折叠、键盘导航或按过滤结果直接执行的完整交互体验。
- `session_tree` flat items 分页已在优化 126 补齐，collapse 状态入参已在优化 127 补齐。

### 优化 126：补齐 RPC `session_tree` flat 分页元数据

状态：已完成

对应缺口：

- 优化 124/125 已补 `session_tree flat=true` 的 selector 扁平数据与 query 过滤，但大 session tree 仍会一次性返回全部命中 item。后续全屏 tree selector 或外部 UI 无法按页加载，也无法稳定判断过滤结果是否还有后续内容。

完成内容：

- `session_tree` 新增参数：
  - `params.offset` / 顶层 `offset`：仅在 `flat=true` 时对过滤后的 `items` 做非负偏移，默认 0。
  - `params.limit` / 顶层 `limit`：仅在 `flat=true` 时限制当前页返回条数；未传时保持旧行为，返回过滤后的全部 items。
- `flat=true` 响应新增分页元数据：
  - `itemOffset`：本次请求使用的 offset。
  - `itemLimit`：本次请求使用的 limit，未传时为 `null`。
  - `itemPageReturned`：当前页实际返回条数。
  - `itemHasMore`：当前页后是否还有更多过滤命中 item。
- 分页发生在 query 过滤之后；`itemTotal` 仍表示未过滤树节点总数，`itemReturned` 表示过滤后的总命中数，`items` 只包含当前页。
- `index` 仍保持未过滤 depth-first 原始序号，分页后也能稳定定位原树节点。
- `CliEntryTest.testRpcModeRunnerExecution` 将 external `session_tree flat=true` 请求改为携带 `query`、`offset=1`、`limit=1`，并断言：
  - 当前页只返回第二个命中 item；
  - `itemTotal=2`、`itemReturned=2`；
  - `itemOffset=1`、`itemLimit=1`、`itemPageReturned=1`、`itemHasMore=false`；
  - 当前页 item actions 仍可直接用于 branch/fork。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 `session_tree flat=true` 的过滤后分页；仍未实现 TS 版全屏 tree selector UI、键盘导航或按过滤结果直接执行的完整交互体验。
- `session_tree` collapse 状态入参已在优化 127 补齐。

### 优化 127：补齐 RPC `session_tree` flat collapse 状态入参

状态：已完成

对应缺口：

- 优化 124/125/126 已补 `session_tree flat=true` 的 selector 扁平数据、query 过滤和分页，但外部 UI 仍无法把 tree selector 的折叠状态传给服务端。大树渲染时客户端只能拿完整 visible list 后自行隐藏 descendants，无法复用 RPC 输出的 depth/index/action 元数据。

完成内容：

- `session_tree` 新增参数：
  - `params.collapsed` / 顶层 `collapsed`：逗号或空白分隔的 collapsed entry id 列表。
  - `params.collapsedIds` / 顶层 `collapsedIds`：同上，优先级高于 `collapsed`。
- `flat=true` 响应新增 collapse 元数据：
  - `collapsedIds`：本次请求解析出的 collapsed id 列表。
  - `collapsedCount`：collapsed id 数量。
  - 每个 item 新增 `descendantCount`，便于 UI 显示可展开规模。
  - 每个 item 新增 `collapsed`，表示该 item 是否处于折叠状态。
  - `actions.toggleCollapseId`：有子节点时为当前 entry id；无子节点时为 `null`。
- collapsed 节点本身仍会返回；其 descendants 不进入 flat `items`。
- nested `roots` 保持完整返回，便于客户端保留全量树上下文和后续展开。
- `itemTotal` 仍表示完整树节点总数，`itemReturned` 表示应用 collapsed/query 后的当前可见命中总数，分页继续作用于最终可见命中列表。
- `CliEntryTest.testRpcModeRunnerExecution` 增加 external `session_tree flat=true collapsed=<rootId>` 请求，并断言：
  - 响应包含 `collapsedIds` / `collapsedCount`；
  - collapsed root item 保留 `childCount=1`、`descendantCount=1`、`collapsed=true`；
  - child 不进入 `items`，`itemReturned=1`；
  - `actions.toggleCollapseId` 可直接用于 UI 反向展开。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 `session_tree flat=true` 的 collapse 数据和可见列表裁剪；仍未实现 TS 版全屏 tree selector UI、键盘导航、排序或按过滤结果直接执行的完整交互体验。
- `collapsed` / `collapsedIds` 字符串和 JSON array 入参均已在优化 128 补齐。

### 优化 128：补齐 RPC `session_tree` `collapsedIds` JSON array 入参

状态：已完成

对应缺口：

- 优化 127 已补 `session_tree flat=true` 的 collapse 状态入参与输出，但 `collapsed` / `collapsedIds` 只能按字符串解析。外部 selector 通常天然持有 id 数组，此前必须额外拼接字符串，和 JSON-RPC 参数的结构化风格不一致。

完成内容：

- `session_tree` 的 `collapsed` / `collapsedIds` 现在同时支持：
  - 逗号或空白分隔的字符串；
  - JSON array，例如 `"collapsedIds":["entry-1","entry-2"]`。
- `collapsedIds` 仍优先于 `collapsed`。
- 新增数组字段提取逻辑：
  - 可读取顶层 array 参数；
  - 可读取 `params` 内的 nested array 参数；
  - nested `params` 对象提取改为匹配成对 `{}`，避免 array 或后续复杂值提前截断。
- `parseIdSet` 现在会识别 JSON array 并通过 `JsonCodec` 解析，过滤空 id，保持插入顺序和去重语义。
- 原有字符串参数保持兼容。
- `CliEntryTest.testRpcModeRunnerExecution` 将 external collapse 请求改为 `collapsedIds:["<rootId>"]`，继续断言 collapsed root 的可见列表裁剪、`collapsedIds` 输出、`descendantCount` 和 `toggleCollapseId`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 `session_tree flat=true` 的 JSON array collapse 入参；仍未实现 TS 版全屏 tree selector UI、键盘导航、排序或按过滤结果直接执行的完整交互体验。

### 优化 129：补齐 RPC `session_list` sort 元数据

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 明确记录 TS 版 `/resume` 支持 session selector/search 和排序。Java RPC `session_list` 已有 query/offset/limit，但排序固定为最新修改优先，外部 session selector 无法通过结构化参数切换排序，也无法从响应中确认当前排序状态。

完成内容：

- `session_list` 新增参数：
  - `params.sort` / 顶层 `sort`。
- 支持的排序值：
  - `newest`：默认值，按 modified 降序；
  - `oldest`：按 modified 升序；
  - `name`：按 session name 排序，无 name 时回退到 firstMessage/sessionId；
  - `messages`：按 messageCount 降序，再按 newest 稳定排序。
- 未知 sort 值会规范化为 `newest`，保持 RPC 调用容错。
- `session_list` 响应新增 `sort` 字段，便于 selector 保存和回显当前排序状态。
- query 过滤先执行，sort 再执行，offset/limit 最后分页。
- `CliEntryTest.testRpcModeRunnerExecution` 覆盖：
  - 默认 `session_list` 响应回显 `sort="newest"`；
  - `session_list all=true sort=messages limit=1` 响应回显 `sort="messages"`，分页元数据仍保持正确。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 RPC `session_list` 的排序数据源；仍不是 TS 版完整全屏 session selector、键盘导航、批量操作或富交互 UI。

### 优化 130：补齐 RPC `session_user_messages` 历史 user message selector 数据

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 明确记录 TS 版 `/fork` 支持从历史 user message 新建 session，并且交互 TUI 有 user message selector。Java 版虽已有行式 `/fork <entryId>` 和 RPC `session_fork`，但 RPC 客户端此前没有专门的历史 user message 数据源，只能解析完整 `session_tree flat=true` 或自行过滤 branch entries。

完成内容：

- `RpcModeRunner` 新增 JSON-RPC method：
  - `session_user_messages`
- 支持参数：
  - `params.session` / 顶层 `session`：读取外部 session 文件；未传时读取当前 runtime session。
  - `params.branch` / 顶层 `branch`：指定 branch/leaf；未传时读取当前 leaf。
  - `params.query` / 顶层 `query`：按 entry id、parent id、message text、source 做大小写不敏感过滤。
  - `params.offset` / 顶层 `offset`：非负分页偏移，默认 0。
  - `params.limit` / 顶层 `limit`：分页大小，默认 20。
- 响应字段包括：
  - `sessionId`
  - `sessionFile`
  - `leaf`
  - `query`
  - `total`
  - `offset`
  - `limit`
  - `returned`
  - `hasMore`
  - `items`
- 每个 item 输出：
  - `index`：过滤后列表序号。
  - `branchIndex`：原 branch path 中的 entry 序号。
  - `entryId`
  - `parentId`
  - `timestamp`
  - `role=user`
  - `source`
  - `text`
  - `actions.forkBeforeEntryId`
  - `actions.forkAtEntryId`
  - `actions.branch`
- `CliEntryTest.testRpcModeRunnerExecution` 增加 external `session_user_messages` 请求，并断言：
  - 可读取外部 session/branch；
  - query 命中 `external rpc prompt`；
  - 返回 `source=external`；
  - 返回可直接传给 `session_fork` 的 `forkBeforeEntryId` / `forkAtEntryId` actions。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补历史 user message selector 的 RPC 数据源；仍不是 TS 版完整全屏 user message selector、键盘导航或富交互 fork UI。

### 优化 131：补齐 RPC `session_fork` selector 定位参数

状态：已完成

对应缺口：

- 优化 124-130 已让 RPC `session_tree flat=true` 和 `session_user_messages` 输出 selector items/actions，但 `session_fork` 仍只接受显式 `entryId`。外部 UI 或后续全屏 selector 拿到 index/action 后，还需要自己转换 entry id 和 fork position，不能直接消费 selector 结果。

完成内容：

- `session_fork` 继续保留原有参数：
  - `params.entryId` / 顶层 `entryId`
  - `params.position` / 顶层 `position`
- 新增可直接消费 selector 的参数：
  - `params.forkBeforeEntryId` / 顶层 `forkBeforeEntryId`：来自 `session_tree` 或 `session_user_messages` action，自动使用 `position=before`。
  - `params.forkAtEntryId` / 顶层 `forkAtEntryId`：来自 selector action，自动使用 `position=at`。
  - `params.index` / 顶层 `index`：按当前 session tree 的 depth-first index 定位 entry，与 `session_tree flat=true` 的 `items[].index` 对齐。
  - `params.branchIndex` / 顶层 `branchIndex`：按指定/current branch path 的 1-based index 定位 entry，可配合 `params.branch` 使用。
  - `params.branch` / 顶层 `branch`：仅用于 `branchIndex` 的 branch path 上下文。
- `session_fork` 响应新增：
  - `resolvedEntryId`：实际传给 runtime fork 的 entry id。
  - `selector`：本次解析来源，可能为 `entryId`、`forkBeforeEntryId`、`forkAtEntryId`、`index` 或 `branchIndex`。
- 实际 fork 仍复用 `AgentSessionRuntime.fork(...)`：
  - 保留 `session_before_fork` 扩展 hook；
  - 保留 before user message 时的 selected prompt 语义；
  - fork 后继续通过 `RpcSessionState.rebind(...)` 更新当前 RPC session。
- `CliEntryTest.testRpcModeRunnerExecution` 将 `session_fork` 请求改为 `index=1 position=at`，并断言：
  - RPC 成功 fork；
  - `resolvedEntryId` 指向预置 user entry；
  - `selector=index`；
  - fork 后当前 session branch 统计仍正确。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 RPC selector 定位兼容层；仍不是 TS 版全屏 tree/user-message selector、键盘导航或富交互 fork UI。
- `index` 按完整 session tree depth-first 顺序解析，不携带 `query` / `collapsedIds` 的视图状态；过滤/折叠视图仍建议直接使用 selector action 字段里的 `forkBeforeEntryId` / `forkAtEntryId`。

### 优化 132：补齐 RPC `session_switch` session list selector 定位参数

状态：已完成

对应缺口：

- 优化 117-129 已让 RPC `session_list` 输出可搜索、排序、分页的 session selector 数据，优化 122 已补 `session_switch` 基础切换能力；但 `session_switch` 仍要求显式传入 session 文件路径，外部 UI 或后续全屏 session selector 不能直接用 list item 的 index/sessionId 执行切换。

完成内容：

- `session_switch` / `session_resume` 继续保留原有参数：
  - `params.session` / 顶层 `session`：显式 session JSONL 路径。
- 新增 selector 定位参数：
  - `params.sessionId` / 顶层 `sessionId`：按当前 scope 的 session id 精确匹配或唯一前缀匹配。
  - `params.index` / 顶层 `index`：按 `session_list` 的过滤后全量列表 1-based index 定位。
  - `params.all` / 顶层 `all`：与 `session_list` 一致，决定 project scope 或 all scope。
  - `params.query` / 顶层 `query`：用于 `index` 定位前的 session list 过滤。
  - `params.sort` / 顶层 `sort`：用于 `index` 定位前的 session list 排序，支持 `newest` / `oldest` / `name` / `messages`。
- `session_switch` 响应新增：
  - `resolvedSessionFile`
  - `resolvedSessionId`
  - `resolvedIndex`
  - `selector`
- 实际切换仍复用 `AgentSessionRuntime.switchSession(...)`：
  - 保留 `session_before_switch` 扩展 hook；
  - 保留目标 session cwd 校验；
  - 切换后继续通过 `RpcSessionState.rebind(...)` 更新当前 RPC session 和 skill diagnostic 订阅。
- `CliEntryTest.testRpcModeRunnerExecution` 将 `session_switch` 请求改为：
  - `all=true`
  - `query="switch seed prompt"`
  - `index=1`
- 测试断言：
  - 成功切换到目标 session；
  - 响应返回 `resolvedSessionFile`；
  - 响应返回目标 `resolvedSessionId`；
  - 响应返回 `resolvedIndex=1`；
  - 响应返回 `selector=index`；
  - 后续 prompt 和 `session_info` 仍写入/读取切换后的 session。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 RPC session selector 定位兼容层；仍不是 TS 版全屏 session selector、键盘导航、批量操作或富交互 UI。
- `index` 使用过滤/排序后的全量列表 index，不使用 `offset`；分页 UI 应传 item 自带的全量 `index`，或直接传 `sessionId`。

### 优化 133：补齐 RPC `session_rename` session list selector 定位参数

状态：已完成

对应缺口：

- 优化 117-129 已补 `session_list` 的结构化 selector 数据，优化 120 已补 `session_rename` 外部 session 改名，优化 132 已让 `session_switch` 可直接消费 session list selector；但 `session_rename` 仍要求显式传入 session 文件路径，外部 UI 或后续全屏 session selector 不能用 list item index/sessionId 直接执行改名。

完成内容：

- `session_rename` 继续保留原有参数：
  - `params.session` / 顶层 `session`：显式 session JSONL 路径。
  - `params.name` / 顶层 `name`：新 session 名称。
  - `params.clear` / 顶层 `clear`：为 true 时清空名称。
- 新增 selector 定位参数：
  - `params.sessionId` / 顶层 `sessionId`：按当前 scope 的 session id 精确匹配或唯一前缀匹配。
  - `params.index` / 顶层 `index`：按 `session_list` 的过滤后全量列表 1-based index 定位。
  - `params.all` / 顶层 `all`：与 `session_list` 一致，决定 project scope 或 all scope。
  - `params.query` / 顶层 `query`：用于 `index` 定位前的 session list 过滤。
  - `params.sort` / 顶层 `sort`：用于 `index` 定位前的 session list 排序，支持 `newest` / `oldest` / `name` / `messages`。
- `session_rename` 在 selector 定位时响应新增：
  - `resolvedSessionFile`
  - `resolvedSessionId`
  - `resolvedIndex`
  - `selector`
- `session_switch` 和 `session_rename` 复用同一套 session target resolver：
  - 显式 `session` 路径仍按原路径解析；
  - `sessionId` 支持唯一前缀；
  - `index` 与 `session_list` 的 `all/query/sort` 语义对齐。
- `CliEntryTest.testRpcModeRunnerExecution` 将 `session_rename` 请求改为：
  - `all=true`
  - `query="external rpc prompt"`
  - `index=1`
  - `name="Renamed External"`
- 测试断言：
  - external session 被成功改名；
  - 响应返回 `resolvedSessionFile`；
  - 响应返回目标 `resolvedSessionId`；
  - 响应返回 `resolvedIndex=1`；
  - 响应返回 `selector=index`；
  - 后续 external `session_info` 读取到新名称。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 RPC rename 的 selector 定位兼容层；仍不是 TS 版全屏 session selector、改名确认 UI、键盘导航或批量操作。
- `index` 使用过滤/排序后的全量列表 index，不使用 `offset`；分页 UI 应传 item 自带的全量 `index`，或直接传 `sessionId`。

### 优化 134：补齐 RPC `session_delete` session list selector 定位参数

状态：已完成

对应缺口：

- 优化 117-129 已补 `session_list` 的结构化 selector 数据，优化 121 已补 `session_delete` 外部 session 删除，优化 132/133 已让 `session_switch` / `session_rename` 可直接消费 session list selector；但 `session_delete` 仍要求显式传入 session 文件路径，外部 UI 或后续全屏 session selector 不能用 list item index/sessionId 直接执行删除。

完成内容：

- `session_delete` 继续保留原有参数：
  - `params.session` / 顶层 `session`：显式 session JSONL 路径。
- 新增 selector 定位参数：
  - `params.sessionId` / 顶层 `sessionId`：按当前 scope 的 session id 精确匹配或唯一前缀匹配。
  - `params.index` / 顶层 `index`：按 `session_list` 的过滤后全量列表 1-based index 定位。
  - `params.all` / 顶层 `all`：与 `session_list` 一致，决定 project scope 或 all scope。
  - `params.query` / 顶层 `query`：用于 `index` 定位前的 session list 过滤。
  - `params.sort` / 顶层 `sort`：用于 `index` 定位前的 session list 排序，支持 `newest` / `oldest` / `name` / `messages`。
- `session_delete` 响应新增：
  - `resolvedSessionFile`
  - `resolvedSessionId`
  - `resolvedIndex`
  - `selector`
- 删除仍保留既有安全边界：
  - 必须能解析到实际 session JSONL；
  - 拒绝删除当前 runtime 正在使用的 session；
  - 删除前通过 `SessionManager.buildSessionInfo(...)` 读取 session id。
- `session_switch` / `session_rename` / `session_delete` 复用同一套 session target resolver。
- `CliEntryTest.testRpcModeRunnerExecution` 将 `session_delete` 请求改为：
  - `all=true`
  - `query="Renamed External"`
  - `index=1`
- 测试断言：
  - external session 被成功删除；
  - 响应返回 `resolvedSessionFile`；
  - 响应返回目标 `resolvedSessionId`；
  - 响应返回 `resolvedIndex=1`；
  - 响应返回 `selector=index`；
  - 测试结束后 external session 文件不存在。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 RPC delete 的 selector 定位兼容层；仍不是 TS 版全屏 session selector、删除确认 UI、回收站/撤销或批量操作。
- `index` 使用过滤/排序后的全量列表 index，不使用 `offset`；分页 UI 应传 item 自带的全量 `index`，或直接传 `sessionId`。

### 优化 135：补齐 RPC `session_info` session list selector 定位参数

状态：已完成

对应缺口：

- 优化 114/119 已补 RPC `session_info` 当前 runtime 统计、source 统计和外部 session/branch 只读查看，优化 117-129 已补 `session_list` 的结构化 selector 数据，优化 132-134 已让 `session_switch` / `session_rename` / `session_delete` 可直接消费 session list selector；但 `session_info` 仍要求显式传入 session 文件路径。外部 UI 或后续全屏 session selector 能列出目标 item，却不能直接用 item 的 index/sessionId 读取详情面板。

完成内容：

- `session_info` 继续保留原有参数：
  - `params.session` / 顶层 `session`：显式 session JSONL 路径。
  - `params.branch` / 顶层 `branch`：指定 branch/entry id 作为统计 leaf。
- 新增 selector 定位参数：
  - `params.sessionId` / 顶层 `sessionId`：按当前 scope 的 session id 精确匹配或唯一前缀匹配。
  - `params.index` / 顶层 `index`：按 `session_list` 的过滤后全量列表 1-based index 定位。
  - `params.all` / 顶层 `all`：与 `session_list` 一致，决定 project scope 或 all scope。
  - `params.query` / 顶层 `query`：用于 `index` 定位前的 session list 过滤。
  - `params.sort` / 顶层 `sort`：用于 `index` 定位前的 session list 排序，支持 `newest` / `oldest` / `name` / `messages`。
- `session_info` 在 selector 定位时响应新增：
  - `resolvedSessionFile`
  - `resolvedSessionId`
  - `resolvedIndex`
  - `selector`
- 无 selector 参数时，`session_info` 仍默认读取当前 runtime session，并保留当前运行态的 thinking/skills/tools/model 等字段。
- 显式 `session`、`sessionId`、`index` 复用 `session_switch` / `session_rename` / `session_delete` 的 session target resolver，保持 list item 消费语义一致。
- `CliEntryTest.testRpcModeRunnerExecution` 将外部 `session_info` 请求改为：
  - `all=true`
  - `query="external rpc prompt"`
  - `index=1`
  - `branch=<externalBranch>`
- 测试断言：
  - external session 的 branch entries/messages/source 统计保持不变；
  - 响应返回 `resolvedSessionFile`；
  - 响应返回目标 `resolvedSessionId`；
  - 响应返回 `resolvedIndex=1`；
  - 响应返回 `selector=index`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 RPC `session_info` 的 selector 只读兼容层；仍不是 TS 版全屏 session selector、详情面板、键盘导航或富分支操作。
- `index` 使用过滤/排序后的全量列表 index，不使用 `offset`；分页 UI 应传 item 自带的全量 `index`，或直接传 `sessionId`。

### 优化 136：补齐 RPC `session_user_messages` session list selector 定位参数

状态：已完成

对应缺口：

- 优化 130 已补 RPC `session_user_messages` 的历史 user message selector 数据，优化 117-129 已补 `session_list` 的结构化 selector 数据，优化 135 已让 `session_info` 可直接消费 session list selector；但 `session_user_messages` 仍只能读取当前 session 或显式 `session` 路径。外部 UI 或后续全屏 session selector 要展示某个 list item 的历史 user message 时，仍需要自己保存/传递 session 文件路径。

完成内容：

- `session_user_messages` 继续保留原有参数：
  - `params.session` / 顶层 `session`：显式 session JSONL 路径。
  - `params.branch` / 顶层 `branch`：指定 branch/entry id 作为 user message 搜索范围。
  - `params.query` / 顶层 `query`：继续作为 user message 文本/source/entryId 过滤条件。
  - `params.offset` / 顶层 `offset`、`params.limit` / 顶层 `limit`：继续控制 user message selector 分页。
- 新增 session list selector 定位参数：
  - `params.sessionId` / 顶层 `sessionId`：按当前 scope 的 session id 精确匹配或唯一前缀匹配。
  - `params.index` / 顶层 `index`：按 `session_list` 的过滤后全量列表 1-based index 定位。
  - `params.all` / 顶层 `all`：与 `session_list` 一致，决定 project scope 或 all scope。
  - `params.sessionQuery` / 顶层 `sessionQuery`：用于 `index` 定位前的 session list 过滤。
  - `params.sort` / 顶层 `sort`：用于 `index` 定位前的 session list 排序，支持 `newest` / `oldest` / `name` / `messages`。
- 因为 `query` 已是 user message 过滤语义，本轮新增 `sessionQuery` 承载 session list 过滤，避免破坏既有 RPC 客户端。
- `session_user_messages` 在 selector 定位时响应新增：
  - `resolvedSessionFile`
  - `resolvedSessionId`
  - `resolvedIndex`
  - `selector`
- 无 selector 参数时，仍按旧逻辑读取当前 session 或显式 `session` 路径。
- `CliEntryTest.testRpcModeRunnerExecution` 将 external `session_user_messages` 请求改为：
  - `all=true`
  - `sessionQuery="external rpc prompt"`
  - `index=1`
  - `branch=<externalBranch>`
  - `query="external"`
- 测试断言：
  - 仍能筛出 external user message；
  - `query` 仍按 user message 维度过滤；
  - 响应返回 `resolvedSessionFile`；
  - 响应返回目标 `resolvedSessionId`；
  - 响应返回 `resolvedIndex=1`；
  - 响应返回 `selector=index`。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/RpcModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testRpcModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 RPC `session_user_messages` 的 selector 只读兼容层；仍不是 TS 版全屏 user message selector、会话详情面板或键盘导航体验。
- `index` 使用 `sessionQuery` / `sort` 过滤排序后的全量 session list index，不使用 `session_list` 的 `offset`；分页 UI 应传 item 自带的全量 `index`，或直接传 `sessionId`。

### 优化 137：补齐 CLI `--export` JSONL 原始会话导出

状态：已完成

对应缺口：

- TS 版支持导出 HTML 或 JSONL。Java 行式 `/export` 已可根据输出路径扩展名导出 HTML 或复制原始 JSONL，但 CLI 启动参数 `--export` 仍固定调用 `HtmlExporter`，即使目标路径为 `.jsonl` 也会写入 HTML 内容。脚本化/非交互导出无法获得原始 session JSONL。

完成内容：

- `Main.run()` 的 `--export` 路径改为调用统一 helper：
  - 输出路径以 `.jsonl` 结尾时，打开源 session 并复制原始 session JSONL；
  - 其他输出路径继续调用 `HtmlExporter.exportToFile(...)`；
  - 自动创建输出父目录；
  - 输出信息包含 `format: jsonl` 或 `format: html`。
- 新增 `Main.exportSessionFile(...)` 包内 helper，便于测试直接验证导出格式，不需要在测试中捕获 `System.exit(...)`。
- HTML 导出保持原行为；JSONL 导出使用 `SessionManager.copySessionFile(...)` 保留原始 session 文件内容。
- `CliEntryTest.startupSessionManagerHonorsSessionFlags` 增加导出断言：
  - `.jsonl` 输出返回 `jsonl`；
  - `.jsonl` 输出内容与源 session 文件完全一致；
  - `.html` 输出返回 `html`；
  - `.html` 输出仍包含 session name。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/Main.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#startupSessionManagerHonorsSessionFlags -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是 CLI 启动参数 `--export` 的 JSONL 格式选择；仍不是 TS 版完整高保真 HTML viewer、侧边栏树、主题映射或完整 tool renderer。
- JSONL 格式通过输出文件扩展名 `.jsonl` 选择；暂未新增显式 `--export-format` 参数。

### 优化 138：补齐 CLI `--export` HTML/JSONL help 文案

状态：已完成

对应缺口：

- 优化 137 已让 CLI `--export` 可按输出扩展名导出 HTML 或 JSONL，但 `CliArgs` 的用户可见 help 仍写着 `Export session file to HTML and exit`。这会让非交互用户误以为 `--export` 仍只支持 HTML，也容易让后续回归漏掉 JSONL 入口说明。

完成内容：

- `CliArgs --export` 描述改为 `Export session file to HTML or JSONL and exit`。
- `CliEntryTest.testCliArgsParsing` 增加 picocli usage 断言，确保 help 文案包含 HTML/JSONL 双格式说明。
- 不改变 `Main.exportSessionFile(...)` 的导出行为；本轮只补用户入口说明和回归。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/CliArgs.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testCliArgsParsing -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 CLI help 文案一致性；仍未新增显式 `--export-format` 参数，也未补 TS 版完整高保真 HTML viewer。

### 优化 139：补齐交互 `/export` HTML/JSONL help 回归

状态：已完成

对应缺口：

- 交互 `/export` 已支持按输出路径扩展名导出 HTML 或复制原始 JSONL，`/help` 文案也已写明 `.jsonl` 行为；但 `CliEntryTest.testInteractiveModeRunnerCommands` 只断言了 `Export session as HTML` 前缀，无法防止后续把 JSONL 说明从交互 help 中误删。

完成内容：

- `CliEntryTest.testInteractiveModeRunnerExecution` 将 `/export` help 断言收紧为完整文案：`Export session as HTML, or copy raw JSONL when path ends with .jsonl`。
- 对比文档同步标记 CLI `--export` 和交互 `/export` 的 HTML/JSONL help 均已覆盖，避免继续把交互入口误判为只说明 HTML。
- 不改变导出实现；本轮锁定的是用户可见入口说明和回归覆盖。

涉及文件：

- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testInteractiveModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮只补交互 `/help` 的 HTML/JSONL 文案回归；仍未补 TS 版完整高保真 HTML viewer、侧边栏树、主题映射、markdown 渲染或完整 tool renderer。

### 优化 140：补齐交互 `/login` OAuth/env 可发现性

状态：已完成

对应缺口：

- Java 行式 `/login` 已支持 API key、`env <ENV_VAR>` 引用，以及对已注册 `AuthStorage.OAuthProvider` 的无 API key OAuth 登录入口；但 `/help` 仍只写 API key 登录，`/login` provider 列表也不会提示哪些 provider 可走 OAuth。用户可见入口与 TS 版“API key 与 OAuth 共存”的登录体验不一致，对比文档中也仍保留了“没有 `/login` / `/logout` handler”的过期描述。

完成内容：

- `InteractiveModeRunner` 新增统一 `LOGIN_USAGE`：`/login [provider] [api-key|env <ENV_VAR>]`。
- 交互 `/help` 的 `/login` 文案改为 `Configure API key or registered OAuth authentication`，同时覆盖 API key、env 引用和已注册 OAuth provider。
- `/login` provider 列表在 provider 已注册 OAuth provider 时追加 `oauth: available`，使 OAuth 登录路径可发现。
- `loginUsage(...)` 与 `/login` 空参数输出统一使用新 usage，避免后续文案漂移。
- `CliEntryTest.interactiveLoginStoresApiKeyAndEnvReferences` 增加 fake OAuth provider，覆盖 provider 列表 OAuth 标记、`/login <oauth-provider>` 登录分支和 `getApiKey(...)` 取回 OAuth access token。
- 对比文档更新 OAuth 章节：Java 已有行式 `/login` / `/logout` API key、env 和已注册 OAuth provider 入口；仍缺内置 OAuth provider、device code/browser callback、全屏 selector 和订阅登录流。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#interactiveLoginStoresApiKeyAndEnvReferences,CliEntryTest#testInteractiveModeRunnerExecution -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 2 个测试，0 failures，0 errors。

当前限制：

- 本轮只是补齐已注册 OAuth provider 的可发现性和回归覆盖；仍未实现 TS 版内置 OAuth provider、device code/browser callback、Copilot/OpenAI Codex/Anthropic OAuth 订阅登录、全屏 selector 或浏览器登录 UI。

### 优化 141：补齐交互 `/logout` stored/runtime/env 来源列表

状态：已完成

对应缺口：

- Java `/logout <provider>` 已能处理 stored auth、runtime `--api-key` 和 environment-only auth，但 `/logout` 空参数只列出 stored provider。用户无法从选择列表发现 runtime 认证或环境变量认证，也无法看到 Bedrock/Vertex 这类非单一 API key 环境认证来源；这与 TS 版 provider/account selector 的“列出可管理认证来源”体验不一致。

完成内容：

- `EnvApiKeys` 新增 `findEnvAuthLabel(...)` 与 `findEnvAuthProviders(...)`，统一识别环境认证来源：
  - 常规 provider 返回具体 env var 名称，例如 `OPENAI_API_KEY`。
  - `google-vertex` ADC 环境返回 `Google ADC`。
  - `amazon-bedrock` AWS 环境返回 `AWS credentials`。
- `AuthStorage.getAuthStatus(...)` 改用统一 env label，修复 Bedrock/Vertex 环境认证 `hasAuth(...)` 为真但状态不可见的问题。
- `AuthStorage` 新增 `listAuthStatuses()`，按 stored、runtime、environment 顺序返回当前可见认证来源。
- `InteractiveModeRunner.renderLogoutProviders(...)` 改用 `listAuthStatuses()`，`/logout` 空参数现在会列出 stored、runtime `--api-key`、environment-only 来源；无认证时文案从 `no stored providers` 改为 `no configured providers`。
- `CliEntryTest.interactiveLogoutRemovesStoredAndRuntimeAuth` 覆盖 `/logout` 列表中的 runtime 和 environment-only provider。
- `AuthStorageTest.runtimeOverrideWinsAndEnvironmentFallbackIsReported` 覆盖 `listAuthStatuses()` 顺序、runtime 来源和 Bedrock `AWS credentials` 标签。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/EnvApiKeys.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/core/AuthStorage.java`
- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/core/AuthStorageTest.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#interactiveLogoutRemovesStoredAndRuntimeAuth,AuthStorageTest#runtimeOverrideWinsAndEnvironmentFallbackIsReported -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 1 个测试、`AuthStorageTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮补的是行式 `/logout` 来源可见性和状态层枚举；仍未实现 TS 版全屏 OAuth/account selector、确认 UI、浏览器/device-code 登录流或订阅账号管理。

### 优化 142：补齐交互 `/logout [provider]` 可选参数 usage

状态：已完成

对应缺口：

- 优化 141 已让 `/logout` 空参数可列出 stored、runtime 和 environment-only 认证来源，但 `/help`、错误提示和列表 usage 仍写成 `/logout <provider>`。这会让用户误以为 provider 参数必填，降低新来源列表的可发现性。

完成内容：

- `InteractiveModeRunner` 新增统一 `LOGOUT_USAGE`：`/logout [provider]`。
- 交互 `/help` 的 `/logout` 文案改为 `List auth sources or remove stored/runtime provider authentication`。
- `/logout` 参数错误、无配置状态和 provider 列表输出统一使用 `/logout [provider]`。
- `CliEntryTest.testInteractiveModeRunnerExecution` 与 `interactiveLogoutRemovesStoredAndRuntimeAuth` 更新断言，覆盖新 help 和 usage 文案。
- 对比文档同步标记 `/logout [provider]` 空参数来源列表是当前 Java 已接通能力。

涉及文件：

- `packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java`
- `packages/coding-agent/src/test/java/works/earendil/pi/codingagent/cli/CliEntryTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest#testInteractiveModeRunnerExecution,CliEntryTest#interactiveLogoutRemovesStoredAndRuntimeAuth -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 目标方法 2 个测试，0 failures，0 errors。

当前限制：

- 本轮只补行式 help/usage 一致性；仍未实现 TS 版全屏 OAuth/account selector、确认 UI、浏览器/device-code 登录流或订阅账号管理。

### 优化 143：移除 Bedrock provider 模拟成功响应

状态：已完成

对应缺口：

- 对比文档明确指出 Java `BedrockProvider` 只检查 AWS env 后返回 `"Hello from AWS Bedrock ..."` 的模拟内容，没有实际调用 Bedrock Converse API。这会造成 provider 看似可用、实际没有请求 AWS 的假成功，后续 provider hook、错误处理和用户诊断都会被误导。

完成内容：

- `BedrockProvider.stream(...)` 保留模型列表和 `Start` 事件。
- 缺少 AWS credential 时继续返回明确缺凭证错误。
- 检测到 credential 后不再生成 `ContentDelta`、`UsageDelta` 或 `End` 的模拟 assistant 消息，改为返回明确错误：`AWS Bedrock Converse API is not implemented in the Java provider yet`。
- 错误 cause 使用 `UnsupportedOperationException`，说明后续需要 AWS SigV4 signing 和 Bedrock Converse streaming 支持。
- `BuiltinProvidersTest.testBedrockProviderDoesNotReturnStubbedAssistantMessage` 覆盖有凭证时不会再返回 `"Hello from AWS Bedrock ..."` 模拟内容，也不会发出成功 `End`。
- 对比文档同步为“仍未实现 Converse，但已不再返回模拟内容”。

涉及文件：

- `packages/ai/src/main/java/works/earendil/pi/ai/provider/BedrockProvider.java`
- `packages/ai/src/test/java/works/earendil/pi/ai/provider/BuiltinProvidersTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/ai -am -Dtest=BuiltinProvidersTest#testBedrockProviderDoesNotReturnStubbedAssistantMessage -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`BuiltinProvidersTest` 目标方法 1 个测试，0 failures，0 errors。

当前限制：

- 本轮移除的是误导性的假成功响应；仍未实现 TS 版 Bedrock Converse API、SigV4 signing、streaming 解析、tool use 映射或 AWS 区域/凭证链完整治理。

### 优化 144：补齐 Bedrock Converse request payload 基础构建

状态：已完成

对应缺口：

- 优化 143 已移除 Bedrock provider 的模拟成功响应，但 Java 仍没有 Bedrock Converse 请求结构。TS 版会把 context、system prompt、tools、图片、tool call/result 和推理参数转换成 `ConverseStreamCommand` input；Java 如果直接从未实现错误跳到 HTTP/SigV4，缺少可回归的 payload 基础层。

完成内容：

- `BedrockProvider` 新增 `buildConverseRequestBody(...)`，构建基础 Bedrock Converse JSON payload：
  - `modelId`
  - `messages`
  - `system`
  - `inferenceConfig.maxTokens`
  - `inferenceConfig.temperature`
  - `toolConfig.tools`
  - `toolConfig.toolChoice.auto`
- message 转换覆盖：
  - user text/image
  - assistant text/toolUse
  - toolResult 合成为 Bedrock user message block
  - thinking block 到 `reasoningContent.reasoningText`
  - 空 user/tool result content 用 `<empty>` 占位
- image block 支持 `jpeg`、`png`、`gif`、`webp` format。
- tool call id 按 Bedrock 约束做基础归一化：非 `[a-zA-Z0-9_-]` 字符替换为 `_`，最长 64 字符。
- `BuiltinProvidersTest.testBedrockProviderBuildsConverseRequestBody` 覆盖 system、user text/image、assistant toolUse、toolResult、inferenceConfig 和 toolConfig 的 JSON shape。

涉及文件：

- `packages/ai/src/main/java/works/earendil/pi/ai/provider/BedrockProvider.java`
- `packages/ai/src/test/java/works/earendil/pi/ai/provider/BuiltinProvidersTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/ai -am -Dtest=BuiltinProvidersTest#testBedrockProviderBuildsConverseRequestBody,BuiltinProvidersTest#testBedrockProviderDoesNotReturnStubbedAssistantMessage -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`BuiltinProvidersTest` 目标方法 2 个测试，0 failures，0 errors。

当前限制：

- 本轮只补请求体构建基础层；仍未发起 Bedrock HTTP 请求，未实现 AWS SigV4 signing、AWS SDK client、Converse streaming 解析、retry/abort、cache point、provider-scoped AWS region/profile 和完整 Claude thinking/signature 兼容。

### 优化 145：补齐 Bedrock requestMetadata 与 region 基础解析

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2 Provider 高级协议项：TS 版 Bedrock Converse 支持 `requestMetadata` 成本分摊标签，并在创建 Bedrock client 前按 ARN、显式 region、环境变量和标准 endpoint 推断 AWS region。Java 优化 144 已补请求体结构，但没有这些后续真实请求层会依赖的基础字段/解析。

完成内容：

- `BedrockProvider.buildConverseRequestBody(...)` 支持从 `StreamOptions.metadata().get("requestMetadata")` 读取 map，并写入 Converse payload 的 `requestMetadata` 字段。
- 新增 `BedrockProvider.resolveBedrockRegion(...)`，基础解析优先级：
  - modelId 中的 Bedrock ARN region
  - `StreamOptions.metadata().get("region")`
  - `StreamOptions.env()` / 系统环境中的 `AWS_REGION`
  - `StreamOptions.env()` / 系统环境中的 `AWS_DEFAULT_REGION`
  - 标准 Bedrock runtime endpoint 中的 region
  - 默认 `us-east-1`
- 标准 endpoint 识别覆盖 `bedrock-runtime.<region>.amazonaws.com`、`bedrock-runtime-fips.<region>.amazonaws.com` 和 `.com.cn` 后缀。
- 显式传入的 `StreamOptions.env()` 键优先于系统环境，空值可用于测试或调用方明确屏蔽 ambient region。

涉及文件：

- `packages/ai/src/main/java/works/earendil/pi/ai/provider/BedrockProvider.java`
- `packages/ai/src/test/java/works/earendil/pi/ai/provider/BuiltinProvidersTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/ai -am -Dtest=BuiltinProvidersTest#testBedrockProviderBuildsConverseRequestBody,BuiltinProvidersTest#testBedrockProviderResolvesRegionForFutureConverseClient,BuiltinProvidersTest#testBedrockProviderDoesNotReturnStubbedAssistantMessage -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`BuiltinProvidersTest` 目标方法 3 个测试，0 failures，0 errors。

当前限制：

- 本轮仍未发起 Bedrock HTTP 请求，未实现 AWS SigV4 signing、AWS SDK client、Converse streaming 解析、retry/abort、cache point、bearer token、profile 凭证链或完整 Claude thinking/signature 兼容。

### 优化 146：补齐 Bedrock AWS 认证来源基础检测

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2 Provider 高级协议项：TS 版 Bedrock 支持多种 AWS 认证来源，包括 `AWS_PROFILE`、`AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY`、`AWS_BEARER_TOKEN_BEDROCK`、ECS credentials、IRSA web identity 和本地代理测试用的 skip-auth。Java 的 `/logout` 环境认证列表已能发现这些来源，但 `BedrockProvider.stream(...)` 此前只检查 `options.apiKey` / `AWS_ACCESS_KEY_ID`，导致运行时认证判断与账号发现不一致。

完成内容：

- `BedrockProvider` 新增 `hasBedrockCredentials(...)`，基础检测来源：
  - runtime `StreamOptions.apiKey()`
  - `StreamOptions.metadata().get("profile")` 或 `AWS_PROFILE`
  - `StreamOptions.metadata().get("bearerToken")` 或 `AWS_BEARER_TOKEN_BEDROCK`
  - `AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY`
  - `AWS_CONTAINER_CREDENTIALS_RELATIVE_URI`
  - `AWS_CONTAINER_CREDENTIALS_FULL_URI`
  - `AWS_WEB_IDENTITY_TOKEN_FILE`
  - `AWS_BEDROCK_SKIP_AUTH=1`
- `BedrockProvider.stream(...)` 改用统一认证来源检测；当 `AWS_PROFILE` 等非 access-key 来源存在时，会进入明确的 Converse 未实现错误路径，而不是误报缺少 `AWS_ACCESS_KEY_ID`。
- 缺凭证错误文案补充可用认证来源，便于用户按 TS 版路径配置 AWS profile、bearer token、ECS/IRSA 或本地代理 skip-auth。

涉及文件：

- `packages/ai/src/main/java/works/earendil/pi/ai/provider/BedrockProvider.java`
- `packages/ai/src/test/java/works/earendil/pi/ai/provider/BuiltinProvidersTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/ai -am -Dtest=BuiltinProvidersTest#testBedrockProviderDetectsCredentialSources,BuiltinProvidersTest#testBedrockProviderBuildsConverseRequestBody,BuiltinProvidersTest#testBedrockProviderResolvesRegionForFutureConverseClient,BuiltinProvidersTest#testBedrockProviderDoesNotReturnStubbedAssistantMessage -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`BuiltinProvidersTest` 目标方法 4 个测试，0 failures，0 errors。

当前限制：

- 本轮只统一认证来源检测；仍未真实创建 AWS SDK client，也未实现 SigV4、bearer token 发起请求、profile 凭证链加载、Converse streaming 解析、retry/abort、cache point 或完整 Claude thinking/signature 兼容。

### 优化 147：补齐 Bedrock Claude thinking request fields 基础构建

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2 Provider 高级协议项：TS 版 Bedrock Converse 会在启用 reasoning/thinking 时，通过 `additionalModelRequestFields` 给 Claude 模型传递 `thinking`、`output_config` 或 `anthropic_beta`。Java 优化 144-146 已补 payload、region 和认证来源基础层，但仍不会把 `Context.thinkingLevel()` 转成 Bedrock Claude 请求字段。

完成内容：

- Bedrock Claude 模型的 `Model.options()` 标记 `reasoning=true`，Titan 保持不声明 reasoning，避免非 Claude 模型收到 Anthropic thinking 字段。
- `BedrockProvider.buildConverseRequestBody(...)` 新增 `additionalModelRequestFields` 基础构建：
  - Claude non-adaptive thinking：`thinking.type=enabled`
  - thinking budget 映射：`minimal=1024`、`low=2048`、`medium=8192`、`high/xhigh=16384`
  - 默认 `thinking.display=summarized`，可通过 `StreamOptions.metadata().get("thinkingDisplay")` 覆盖为 `omitted`
  - 默认追加 `anthropic_beta=["interleaved-thinking-2025-05-14"]`
  - 为后续 Claude 4.6+ / Fable adaptive thinking 预留 `thinking.type=adaptive` 和 `output_config.effort` 映射
- `ThinkingLevel.OFF`、缺少 reasoning 标记或非 Claude 模型时不生成 `additionalModelRequestFields`。

涉及文件：

- `packages/ai/src/main/java/works/earendil/pi/ai/provider/BedrockProvider.java`
- `packages/ai/src/test/java/works/earendil/pi/ai/provider/BuiltinProvidersTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/ai -am -Dtest=BuiltinProvidersTest#testBedrockProviderBuildsThinkingRequestFieldsForClaudeModels,BuiltinProvidersTest#testBedrockProviderDetectsCredentialSources,BuiltinProvidersTest#testBedrockProviderBuildsConverseRequestBody,BuiltinProvidersTest#testBedrockProviderResolvesRegionForFutureConverseClient,BuiltinProvidersTest#testBedrockProviderDoesNotReturnStubbedAssistantMessage -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`BuiltinProvidersTest` 目标方法 5 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 request payload 中的 thinking 字段基础构建；仍未真实发送 Bedrock Converse 请求，也未实现 streaming thinking delta 解析、签名往返校验、GovCloud display 兼容、cache point、SigV4 / bearer token 请求或 AWS SDK 凭证链加载。

### 优化 148：补齐 Bedrock system cachePoint 基础构建

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2 Provider 高级协议项：TS 版 Bedrock Converse 会在支持 prompt caching 的 Claude 模型 system prompt 后追加 `cachePoint` block，并按 `cacheRetention` 或 `PI_CACHE_RETENTION` 控制缓存 TTL。Java 优化 144-147 已补 payload、认证、region 和 thinking 字段，但 system prompt 仍只有纯文本 block。

完成内容：

- `BedrockProvider.buildConverseRequestBody(...)` 的 system prompt 构建改为专用 helper，支持追加 cache point。
- 默认 cache retention 为 `SHORT`；`CacheRetention.NONE` 不追加 cache point。
- `CacheRetention.LONG` 追加 `cachePoint.ttl=1h`。
- 本地支持列表与 TS 基础规则对齐：
  - Claude 4.x
  - Claude 3.7 Sonnet
  - Claude 3.5 Haiku
- `AWS_BEDROCK_FORCE_CACHE=1` 可强制对应用 inference profile 或未在本地列表中的 Claude 模型追加 cache point。
- `PI_CACHE_RETENTION=long` 可在未显式传入 `StreamOptions.cacheRetention()` 时切换为 long retention。

涉及文件：

- `packages/ai/src/main/java/works/earendil/pi/ai/provider/BedrockProvider.java`
- `packages/ai/src/test/java/works/earendil/pi/ai/provider/BuiltinProvidersTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/ai -am -Dtest=BuiltinProvidersTest#testBedrockProviderBuildsSystemCachePointForSupportedClaudeModels,BuiltinProvidersTest#testBedrockProviderBuildsThinkingRequestFieldsForClaudeModels,BuiltinProvidersTest#testBedrockProviderDetectsCredentialSources,BuiltinProvidersTest#testBedrockProviderBuildsConverseRequestBody,BuiltinProvidersTest#testBedrockProviderResolvesRegionForFutureConverseClient,BuiltinProvidersTest#testBedrockProviderDoesNotReturnStubbedAssistantMessage -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`BuiltinProvidersTest` 目标方法 6 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 system prompt cache point；仍未补 message/tool content cache point、真实 Bedrock Converse 请求、AWS SDK cache enum 映射、streaming cache usage 解析、SigV4 / bearer token 请求或 AWS SDK 凭证链加载。

### 优化 149：补齐 Bedrock last user message cachePoint 基础构建

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2 Provider 高级协议项：TS 版 Bedrock Converse 在 `cacheRetention` 未禁用、模型支持 prompt caching 且最后一条消息是 user 时，会给最后一条 user message 追加 `cachePoint`。Java 优化 148 只补了 system prompt cache point，普通消息路径仍缺这层 payload 行为。

完成内容：

- `BedrockProvider.buildConverseRequestBody(...)` 在 messages 构建完成后调用 `appendCachePointToLastUserMessage(...)`。
- 复用优化 148 的 cache point helper 和 prompt caching 支持判断：
  - `CacheRetention.NONE` 不追加。
  - `CacheRetention.SHORT` 追加 `cachePoint.type=default`。
  - `CacheRetention.LONG` 追加 `cachePoint.type=default` 和 `cachePoint.ttl=1h`。
  - 仅当最后一条消息 role 为 `user` 时追加；最后一条是 assistant 时不追加。
  - 支持 `AWS_BEDROCK_FORCE_CACHE=1` 的强制缓存路径。
- `createCachePointBlock(...)` 统一供 system prompt 和 last user message 两条路径复用，减少后续接 AWS SDK enum 映射时的重复点。

涉及文件：

- `packages/ai/src/main/java/works/earendil/pi/ai/provider/BedrockProvider.java`
- `packages/ai/src/test/java/works/earendil/pi/ai/provider/BuiltinProvidersTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/ai -am -Dtest=BuiltinProvidersTest#testBedrockProviderAddsCachePointToLastUserMessage,BuiltinProvidersTest#testBedrockProviderBuildsSystemCachePointForSupportedClaudeModels,BuiltinProvidersTest#testBedrockProviderBuildsThinkingRequestFieldsForClaudeModels,BuiltinProvidersTest#testBedrockProviderDetectsCredentialSources,BuiltinProvidersTest#testBedrockProviderBuildsConverseRequestBody,BuiltinProvidersTest#testBedrockProviderResolvesRegionForFutureConverseClient,BuiltinProvidersTest#testBedrockProviderDoesNotReturnStubbedAssistantMessage -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`BuiltinProvidersTest` 目标方法 7 个测试，0 failures，0 errors。

当前限制：

- 本轮只补最后一条 user message 的 cache point；仍未实现真实 Bedrock Converse 请求、AWS SDK cache enum 映射、streaming cache usage 解析、SigV4 / bearer token 请求、AWS SDK 凭证链加载或更细粒度的 tool/content cache 统计。

### 优化 150：补齐 Bedrock 连续 toolResult 合并

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2 Provider 高级协议项：TS 版 Bedrock `convertMessages(...)` 会把连续的 `toolResult` 消息合并到同一个 Bedrock user message 的 `content` 数组中，因为 Bedrock 要求同一轮工具结果集中提交。Java 优化 144 的基础 payload 构建会把每个 `Message.ToolResult` 单独转换成一条 user message，与 TS 行为不一致，也会影响多工具并发结果回放。

完成内容：

- `BedrockProvider.buildConverseRequestBody(...)` 的 messages 构建改为索引循环，遇到 `Message.ToolResult` 时向后收集连续 tool result。
- 新增 `convertToolResults(...)` / `convertToolResultBlock(...)`，将连续 tool result 合并为单条 `role=user` message，内部每个结果仍保留独立 `toolResult.toolUseId`、`content` 和 `status`。
- 非连续 tool result 不合并；中间出现 assistant/user message 后会开始新的 user message。
- 既有 tool call id 归一化、成功/错误 status、空 content `<empty>` 占位和 last user message cache point 逻辑继续复用。

涉及文件：

- `packages/ai/src/main/java/works/earendil/pi/ai/provider/BedrockProvider.java`
- `packages/ai/src/test/java/works/earendil/pi/ai/provider/BuiltinProvidersTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/ai -am -Dtest=BuiltinProvidersTest#testBedrockProviderCombinesConsecutiveToolResults,BuiltinProvidersTest#testBedrockProviderAddsCachePointToLastUserMessage,BuiltinProvidersTest#testBedrockProviderBuildsSystemCachePointForSupportedClaudeModels,BuiltinProvidersTest#testBedrockProviderBuildsThinkingRequestFieldsForClaudeModels,BuiltinProvidersTest#testBedrockProviderDetectsCredentialSources,BuiltinProvidersTest#testBedrockProviderBuildsConverseRequestBody,BuiltinProvidersTest#testBedrockProviderResolvesRegionForFutureConverseClient,BuiltinProvidersTest#testBedrockProviderDoesNotReturnStubbedAssistantMessage -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`BuiltinProvidersTest` 目标方法 8 个测试，0 failures，0 errors。

当前限制：

- 本轮只补连续 toolResult 的 payload 合并；仍未实现真实 Bedrock Converse 请求、AWS SDK streaming event 解析、工具调用 delta 合并、SigV4 / bearer token 请求或 AWS SDK 凭证链加载。

### 优化 151：补齐 Bedrock Claude thinking signature fallback

状态：已完成

对应缺口：

- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md` 的 P2 Provider 高级协议项：TS 版 Bedrock 在回放 assistant thinking content 时会检查 Claude thinking signature。Claude 模型支持 `reasoningContent.reasoningText.signature`，但如果部分消息或外部持久化消息缺少 signature，Bedrock 会拒绝回放；TS 会把这类 Claude thinking 降级为普通 text block。Java 此前不看模型，缺 signature 的 thinking 仍会按 `reasoningContent` 回放。

完成内容：

- `BedrockProvider` 的 message/content 转换改为 model-aware：
  - `convertMessage(model, message)`
  - `convertContentBlocks(model, ...)`
  - `convertContentBlock(model, ...)`
- Claude Bedrock assistant thinking：
  - 有 signature 时继续输出 `reasoningContent.reasoningText.text/signature`。
  - 缺 signature 或 signature 为空时降级为普通 `text` block，避免后续真实 Converse 请求被 Bedrock 拒绝。
- 非 Claude 模型的 thinking block 继续输出无 signature 的 `reasoningContent.reasoningText.text`，保持 TS 版非 Claude 分支的基础 shape。
- tool result content 转换继续使用 model-independent 路径，避免无关 tool result 内容受 Claude fallback 影响。

涉及文件：

- `packages/ai/src/main/java/works/earendil/pi/ai/provider/BedrockProvider.java`
- `packages/ai/src/test/java/works/earendil/pi/ai/provider/BuiltinProvidersTest.java`
- `docs/JAVA_MIGRATION_EXECUTION_PROGRESS.md`
- `docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

验证：

```bash
mvn -pl packages/ai -am -Dtest=BuiltinProvidersTest#testBedrockProviderFallsBackMissingClaudeThinkingSignatureToText,BuiltinProvidersTest#testBedrockProviderCombinesConsecutiveToolResults,BuiltinProvidersTest#testBedrockProviderAddsCachePointToLastUserMessage,BuiltinProvidersTest#testBedrockProviderBuildsSystemCachePointForSupportedClaudeModels,BuiltinProvidersTest#testBedrockProviderBuildsThinkingRequestFieldsForClaudeModels,BuiltinProvidersTest#testBedrockProviderDetectsCredentialSources,BuiltinProvidersTest#testBedrockProviderBuildsConverseRequestBody,BuiltinProvidersTest#testBedrockProviderResolvesRegionForFutureConverseClient,BuiltinProvidersTest#testBedrockProviderDoesNotReturnStubbedAssistantMessage -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`BuiltinProvidersTest` 目标方法 9 个测试，0 failures，0 errors。

当前限制：

- 本轮只补 thinking replay 的 payload fallback；仍未实现真实 Bedrock Converse 请求、streaming thinking delta/signature 解析、签名跨轮校验、SigV4 / bearer token 请求或 AWS SDK 凭证链加载。

## 下一步建议

1. 继续 P1：扩展 SPI 继续补完整 TUI component/context，并补 TS 版全屏主题/模板选择器、自动主题探测和更完整的全屏 TUI 主题应用。
2. 继续 P1：补完整 `pi config` TUI selector、完整 self-update 最新版本探测/安装方式识别/权限与说明、依赖治理细节和 update 并发/进度语义。
3. 继续 P2：补齐 Provider 高级协议、完整图片处理/terminal graphics 和分享导出体验。
