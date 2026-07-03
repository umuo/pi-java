# Pi Java 迁移优化执行进度

更新时间：2026-07-03

依据文档：`docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

## 当前总进度

| 优先级 | 当前状态 | 说明 |
| --- | --- | --- |
| P0：声明但未接通的用户入口 | 进行中，已完成 19 项 | 已完成启动会话参数接通、交互 `/settings`、交互 `/login`、交互 `/logout`、交互 `/export`、交互 `/share`、交互 `/copy`、交互 `/import`、交互 `/name`、交互 `/session`、交互 `/new`、交互 `/compact`、行式 `/tree`、行式 `/fork`、行式 `/clone`、行式 `/resume`、交互 `/reload`、扩展工具基础加载和扩展基础事件 hook；其他交互命令和完整扩展平台仍待补。 |
| P1：TS 生态优势核心闭环 | 未开始 | 扩展平台、包生态、全屏 TUI、OAuth 登录仍待规划实施。 |
| P2：高级协议与体验细节 | 未开始 | Provider 高级协议、图像生成、分享导出、SDK 文档等仍待补。 |

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
- `/resume` 尚未支持重命名、删除等 TS 版 session 管理操作。

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
- 由于现有 Java SPI 只提供 `Tool` 定义、不提供执行函数，包装后的扩展工具在被调用时会返回明确错误，说明当前 Java SPI 尚无 tool executor。
- 单测覆盖 CLI `--extension` / `--no-extensions` 解析、扩展工具进入 session/system prompt，以及执行时的明确降级错误。

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
- 扩展工具当前只完成“声明进入模型上下文”，未完成真实执行器 API；模型调用扩展工具会得到明确错误。
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

- 只接入 Java 现有四个基础 hook，尚未覆盖 TS 版完整事件面，如资源发现、provider 请求/响应、session switch/fork/compact、input transform 等。
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
- 未接入 extension `session_before_compact` / `session_after_compact` 拦截事件。
- 自动压缩逻辑仍保留一份内联实现，后续可与 `compactNow()` 抽成公共路径。

## 下一步建议

1. 继续 P0：为扩展 SPI 补执行器 API，让扩展注册的工具不只可见，也能真实执行。
2. 继续 P0：补齐 `/resume` 的重命名、删除、全局 session 搜索等 TS 版 session 管理能力。
3. 继续 P0/P1：为 `/compact` 抽公共 compaction 路径，并补 extension compact 前后事件。
