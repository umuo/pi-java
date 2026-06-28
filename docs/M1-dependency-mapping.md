# M1: TS 到 Java 依赖映射表

源项目：`/Users/gitsilence/github/pi`

目标项目：`/Users/gitsilence/github/pi-java`

## 源项目包结构

| TS workspace | NPM 包名 | 源文件数 | Java 模块 | 职责 |
| --- | --- | ---: | --- | --- |
| `packages/ai` | `@earendil-works/pi-ai` | 147 | `packages/ai` / `pi-ai` | 统一 LLM provider、模型注册、流式协议、OAuth、图像生成 |
| `packages/agent` | `@earendil-works/pi-agent-core` | 25 | `packages/agent` / `pi-agent-core` | agent loop、工具调用、session tree、harness、compaction |
| `packages/tui` | `@earendil-works/pi-tui` | 28 | `packages/tui` / `pi-tui` | 终端 UI、键盘解析、文本宽度、组件渲染 |
| `packages/coding-agent` | `@earendil-works/pi-coding-agent` | 160 | `packages/coding-agent` / `pi-coding-agent` | CLI、读写编辑工具、配置、技能、扩展、交互/RPC 模式 |
| `packages/orchestrator` | `@earendil-works/pi-orchestrator` | 13 | `packages/orchestrator` / `pi-orchestrator` | IPC、进程监管、实例存储、RPC 桥 |

## 运行依赖映射

| TS 依赖 | 使用位置 | Java 对等组件 | 处理策略 |
| --- | --- | --- | --- |
| `@earendil-works/pi-ai` | `agent`, `coding-agent` | `pi-ai` Maven 模块 | 1:1 内部模块依赖 |
| `@earendil-works/pi-agent-core` | `coding-agent` | `pi-agent-core` Maven 模块 | 1:1 内部模块依赖 |
| `@earendil-works/pi-tui` | `coding-agent` | `pi-tui` Maven 模块 | 1:1 内部模块依赖 |
| `@earendil-works/pi-coding-agent` | `orchestrator` | `pi-coding-agent` Maven 模块 | 1:1 内部模块依赖 |
| `typebox` | schema/type validation | Jackson `JsonNode` + Java records + validation helpers | 手动兜底实现 TS schema 合约 |
| `yaml` | config/frontmatter/skills | SnakeYAML | 成熟替代 |
| `ignore` | gitignore 风格匹配 | `IgnoreRules` 手动实现 + glob matcher | Java 无完全等价内置，保留语义兜底 |
| `glob` | 文件搜索 | Java NIO `FileSystem.getPathMatcher` + walk | 手动封装 |
| `minimatch` | 路径模式匹配 | Java NIO glob + `GlobMatcher` | 手动封装 |
| `proper-lockfile` | session/config 文件锁 | `FileChannel.lock()` | 手动封装 |
| `diff` | edit/patch | `java-diff-utils` | 成熟替代 |
| `semver` | 版本判断 | `semver4j` | 成熟替代 |
| `chalk` | ANSI 样式 | Jansi + `Ansi` 工具 | 成熟替代加薄封装 |
| `highlight.js` | 语法高亮/export html | 后续映射到 Java 高亮库或手动主题 token renderer | 当前登记为待移植 |
| `hosted-git-info` | package/git URL 解析 | 手动 `GitUrl` parser | Java 无稳定等价，兜底实现 |
| `undici` | HTTP/fetch | Apache HttpClient 5 | 成熟替代 |
| `http-proxy-agent`, `https-proxy-agent` | 代理 | Apache HttpClient route/proxy config | 成熟替代 |
| `partial-json` | 流式 JSON 片段 | `PartialJson` 手动实现 | 兜底实现 |
| `get-east-asian-width` | TUI 宽度 | `EastAsianWidth` 手动实现 | 兜底实现 Unicode 宽度 |
| `marked` | Markdown 渲染 | flexmark-java | 成熟替代 |
| `cross-spawn` | 子进程 | `ProcessBuilder` + `Exec` 封装 | JDK 替代 |
| `jiti` | 动态扩展加载 | Java `ServiceLoader` + isolated classloader | 语义重建 |
| `@silvia-odwyer/photon-node` | 图像处理 | Java ImageIO + TwelveMonkeys/后续 native adapter | 当前先 JDK 兜底 |
| `@mariozechner/clipboard` | 剪贴板 | AWT Clipboard + 平台命令兜底 | 手动实现 |
| `openai` | OpenAI API | Apache HttpClient + provider adapter | 手动 provider，避免 SDK 锁定 |
| `@anthropic-ai/sdk` | Anthropic API | Apache HttpClient + provider adapter | 手动 provider，保持统一流协议 |
| `@google/genai` | Gemini API | Apache HttpClient + provider adapter | 手动 provider |
| `@mistralai/mistralai` | Mistral API | Apache HttpClient + provider adapter | 手动 provider |
| `@aws-sdk/client-bedrock-runtime`, `@smithy/node-http-handler` | Bedrock | AWS SDK for Java v2 后续加入 | 成熟替代 |
| `@opentelemetry/api` | tracing | OpenTelemetry Java API | 成熟替代 |

## 构建/测试依赖映射

| TS 依赖 | Java 对等组件 | 处理策略 |
| --- | --- | --- |
| `typescript`, `@typescript/native-preview`, `tsx`, `esbuild` | Maven Compiler Plugin | Java 编译替代 |
| `vitest`, `node --test` | JUnit Jupiter + AssertJ | 测试框架替代 |
| `biome` | Checkstyle/Spotless 后续加入 | 代码风格替代 |
| `shx`, `husky` | Maven lifecycle + shell scripts | 构建阶段替代 |

## 兜底实现清单

首批已规划或已实现的兜底组件：

| Java 类 | 对应 TS/依赖 | 目标语义 |
| --- | --- | --- |
| `works.earendil.pi.common.glob.GlobMatcher` | `glob`, `minimatch` | 路径 glob 编译与匹配 |
| `works.earendil.pi.common.glob.IgnoreRules` | `ignore` | gitignore 风格 include/exclude |
| `works.earendil.pi.common.text.EastAsianWidth` | `get-east-asian-width` | CJK/emoji 终端列宽 |
| `works.earendil.pi.common.text.Truncation` | `core/tools/truncate.ts` | 按行/字节 head/tail 截断 |
| `works.earendil.pi.common.json.PartialJson` | `partial-json` | 流式 JSON 片段闭合/解析 |
| `works.earendil.pi.agent.session.*` | `harness/session/*` | session tree、leaf、label、JSONL 存储 |
| `works.earendil.pi.codingagent.tools.EditDiff` | `core/tools/edit-diff.ts` | fuzzy edit、line-ending 保持、patch 生成 |

