# Pi Java 迁移优化执行进度

更新时间：2026-07-03

依据文档：`docs/PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md`

## 当前总进度

| 优先级 | 当前状态 | 说明 |
| --- | --- | --- |
| P0：声明但未接通的用户入口 | 进行中，已完成 9 项 | 已完成启动会话参数接通、交互 `/export`、交互 `/copy`、交互 `/import`、行式 `/tree`、行式 `/fork`、行式 `/clone`、行式 `/resume` 和扩展工具基础加载；其他交互命令和完整扩展平台仍待补。 |
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

- `/export` 当前只复用现有 HTML exporter，还未补 TS 版分享/发布能力。

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
mvn -pl packages/coding-agent -am -Dtest=CliEntryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。`CliEntryTest` 共 14 个测试，0 failures，0 errors。

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
- 尚未接入扩展事件 `onBeforeTurn` / `onAfterTurn` / `onBeforeToolCall` / `onAfterToolCall` 到 agent lifecycle。
- 尚未实现扩展命令、快捷键、消息渲染器、自定义 provider、CLI flag 动态扩展等 TS 版能力。

## 下一步建议

1. 继续 P0：实现交互 `/settings`、`/reload`、`/login`、`/logout` 的基础行式版本。
2. 继续 P0：把扩展事件 hook 接入 prompt turn 和 tool call lifecycle。
3. 继续 P0：补齐 `/resume` 的重命名、删除、全局 session 搜索等 TS 版 session 管理能力。
