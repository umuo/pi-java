# Pi Java 迁移优化执行进度

更新时间：2026-07-04

依据文档：`docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

## 当前总进度

| 优先级 | 当前状态 | 说明 |
| --- | --- | --- |
| P0：声明但未接通的用户入口 | 进行中，已完成 53 项 | 已完成启动会话参数接通、交互 `/settings`、交互 `/login`、交互 `/logout`、交互 `/export`、交互 `/share`、交互 `/copy`、交互 `/import`、交互 `/name`、交互 `/session`、交互 `/new`、交互 `/compact`、`/compact` 公共执行路径和扩展事件、行式 `/tree`、行式 `/fork`、行式 `/clone`、行式 `/resume`、`/resume` 重命名/删除、`/resume` 全局搜索/过滤、交互 `/reload`、交互 `!` / `!!` bash 命令、bash `shellCommandPrefix` / `shellPath` 设置接入、扩展工具基础加载、扩展工具执行器 API、扩展基础事件 hook、扩展 slash command 注册/执行、扩展命令 session facade、扩展 custom entry/label facade、扩展 `sendUserMessage` 同步版、扩展 `sendUserMessage` steer/followUp 队列语义、扩展 `sendMessage` custom message 和 nextTurn delivery、扩展结构化命令参数、扩展 `user_bash` 事件、扩展 `input` 事件、扩展 `tool_call` 改参/阻断、扩展 `tool_result` 结果修改、扩展 `before_agent_start` 上下文/系统提示注入、扩展 `session_before_switch` / `session_before_fork` 取消拦截、扩展上下文 abort signal、基础 provider 请求/响应 hook、扩展 `resources_discover` skill/prompt/theme 路径发现、theme resource 主链路、行式主题应用、行式 `/theme` 入口、行式主题 truecolor / 256-color 精度、行式 `/prompt` 模板入口、HTML export skill/custom/XSS 回归、HTML export 图片内容安全渲染、OpenRouter 图像生成 API 基础链路、read tool 图片附件 / blockImages 过滤、read tool 图片 autoResize / BMP 转 PNG 基础处理、CLI 初始 `@file` / `@image` 图片附件和行式 `/paste-image` 剪贴板图片入口；其他交互命令和完整扩展平台仍待补。 |
| P1：TS 生态优势核心闭环 | 进行中 | Java JAR 扩展 SPI 已接入基础加载、事件 hook、工具执行器、compact 事件、`user_bash` / `input` 事件、`tool_call` 改参/阻断、`tool_result` 结果修改、`before_agent_start` 上下文/系统提示注入、`session_before_switch` / `session_before_fork` 取消拦截、扩展上下文 abort signal、provider 请求/响应 hook、`resources_discover` skill/prompt/theme 路径发现、theme resource 加载、行式主题应用、行式 `/theme` 选择/预览、行式主题 truecolor / 256-color 输出、行式 `/prompt` list/preview/run 和直接模板 slash 展开、行式 slash command、命令上下文、session metadata、custom entry、label facade、同步 user message 触发、`sendUserMessage` steer/followUp 队列语义、`sendMessage` custom message / nextTurn delivery 和结构化命令参数；shell prefix/path 设置已接入交互 bash 和 bash tool；包生态、全屏 TUI、OAuth 登录仍待规划实施。 |
| P2：高级协议与体验细节 | 进行中 | HTML export 已补 skill wrapper、custom entry、图片内容渲染和 XSS 回归；图像生成已补 OpenRouter 基础 API / provider / registry；read tool 已能返回图片附件，`images.blockImages` 会过滤 LLM 上下文图片，`images.autoResize` 会在 read tool 和 CLI 初始 `@image` 附件中对可解码 PNG/JPEG/BMP 执行基础缩放并将 BMP 转 PNG；行式 `/paste-image` 可把剪贴板图片保存为临时/指定图片文件并输出 `@path`；Provider 高级协议、全屏剪贴板图片 UX、完整图片处理/terminal graphics、分享导出高保真 viewer、SDK 文档等仍待补。 |

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
- Java 行式交互目前只在普通 prompt 执行期间安装渲染订阅；扩展命令内部触发的 `sendUserMessage` 会持久化消息并更新 stats，但 assistant 输出不会走完整的行式渲染体验。
- 暂未支持图像内容、结构化 content array 或 extension source 标记。

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

## 下一步建议

1. 继续 P1：扩展 SPI 继续补 UI context，并补 TS 版全屏主题/模板选择器、自动主题探测和更完整的全屏 TUI 主题应用。
2. 继续 P1：规划 TS 版全屏 TUI picker/search 体验在 Java 中的对应实现。
3. 继续 P2：补齐 Provider 高级协议、剪贴板图片/完整图片处理和分享导出体验。
