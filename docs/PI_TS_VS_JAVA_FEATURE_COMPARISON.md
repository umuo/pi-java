# Pi TypeScript vs Pi Java 功能与细节差异全景对比白皮书

本文档深度剖析并对比了原始 TypeScript 版本的 AI 编程智能体生态 **`pi`** (`/Volumes/Data 1/github/pi`) 与当前 Java 重构版本 **`pi-java`** (`/Volumes/Data 1/github/pi-java`) 在架构设计、核心功能矩阵、实现细节以及使用体验上的差异。

尽管 `pi-java` 在核心主循环（Agent Loop）、7 大基础工具链（read/write/edit/ls/grep/find/bash）、多模型基础对话及高级命令（如 `/grill-me`、`/teamwork-preview`、`/orchestrator-status`）方面已完成 **100% 核心功能对齐**，但两个仓库在底层生态机制、交互实现层级以及特定边缘功能上仍存在显著差异。

---

## 一、 总体差异全景摘要矩阵

| 比较维度 | TypeScript 版本 (`pi`) | Java 版本 (`pi-java`) | 总结建议与现存差异 |
| :--- | :--- | :--- | :--- |
| **底层运行时与并发** | Node.js / Bun 异步单线程事件循环 | **Java 21** 虚线程 (Virtual Threads) + CompletableFuture | Java 版本在高并发工具执行与多子进程调度上更具稳定性与低开销。 |
| **包结构与代码组织** | npm Monorepo (`ai`, `agent`, `tui`, `coding-agent`, `orchestrator`) | Maven Monorepo (新增 **`common`** 独立模块，其余分包对齐) | Java 通过 `common` 强化了跨包的基础工具与强类型契约。 |
| **插件与扩展系统** | **支持强大的扩展机制 (`extensions/`)**，允许运行时加载自定义 JS/TS 插件、生命周期钩子与自定义工具 | **暂无动态扩展插件机制** | TS 版生态开放度更高；Java 版聚焦于企业级内置安全闭环。 |
| **资源与包管理 (`pkg`)** | 内置 `pi install/update/remove` 与 `package-manager.ts`，支持 Git 插件下载和 `pi update self` | **暂无内置包管理器**，依赖标准的 Maven 构建与 Git 手动更新 | TS 版具备独立的 CLI 资源下载能力。 |
| **AI 驱动层 (`packages/ai`)**| 支持 OAuth 浏览器认证流、AWS Bedrock 以及 **图像生成 API (`image-models.ts`)** | 支持通用 OpenAI-Compatible 与 Google/Anthropic 等，**新增大量新晋内置 Provider** (DeepSeek/Zai/Cerebras 等)，**暂不支持生图与 Bedrock** | Java 版在文本编程大模型支持更全面，但缺少图像生成与 Bedrock 适配。 |
| **TUI 渲染与控制台UI** | **自研 57KB 响应式终端渲染引擎**，包含全屏富文本编辑器 (`editor.ts`)、箭头键交互选择菜单和 Sixel/Kitty 原生生图渲染 | 基于 **JLine + JAnsi** 实现缓冲交互，内置 Alternate Screen 全屏面板、Patch 分栏对比，图像降级为 iTerm2 / 文本回退 | TS 版 CLI 交互更像 TUI 图形应用；Java 版执行与输出排版更稳健聚焦。 |
| **会话导出能力** | 支持 `pi --export <file.html>` 生成带有 CSS/JS 的单文件可视化 HTML 交互报告 | **暂无 HTML 导出功能**，会话仅通过 JSONL v3 存储与 RPC 消费 | TS 版分享会话记录可视化能力更强。 |
| **多智能体可观测性** | 基础进程监控与 IPC 事件机制 | **极其强大的可观测控制面**：支持 Stdio JSON-RPC 进程池、心跳监测、退避重启、日志轮转及全屏交互式 Dashboard 面板 | Java 版在长期后台运行、多智能体调试和故障恢复上大幅超越 TS 版。 |

---

## 二、 各模块功能与细节差异深度剖析

### 2.1 底层架构与技术底座差异
* **TypeScript 版 (`pi`)**：
  * 依托 Node.js/Bun 的单线程异步事件驱动机制。代码结构极为灵活，利用 TS 的 Duck Typing 与运行时动态注入处理组件通信。
  * 依赖较多的 npm 工具链（如 `glob`, `minimatch`, `semver` 等）。
* **Java 版 (`pi-java`)**：
  * 严格基于 **Java 21 原生虚线程 (Virtual Threads)** 构建，为每个子智能体任务、远程 RPC 订阅者、输入流监听分配独立的虚线程，彻底告别回调地狱与线程池阻塞。
  * 采用强类型契约 (Records, Sealed Classes, Pattern Matching)，全面剥离外部原生依赖（如不依赖 Native Lib 包装），安全性与跨 JVM 平台可移植性极佳。

### 2.2 扩展插件与资源配置管理 (`packages/coding-agent`)
这是目前 TS 版本与 Java 版本在面向使用者层面的**最大功能差异点**：

#### 1. 动态插件与扩展机制 (Extensions Ecosystem)
* **TS 版**：具有完整的 `core/extensions/` 架构（包含 `loader.ts`, `runner.ts`, `types.ts` 约 110KB 代码）。允许用户在配置目录通过 JS/TS 编写自定义 Extension，注册自定义命令行参数 (`unknownFlags`)、拦截 Agent 生命周期钩子、挂载外部工具类和扩充 TUI 界面视图。
* **Java 版**：坚持开箱即用的强类型闭环，**没有动态插件加载系统**。用户自定义能力主要通过标准的 `SKILL.md` (技能 Prompt 模板) 与 `.pi/settings.json` 配置体现，无法在运行时动态加载第三方 Java 类库或外部脚本拦截器。

#### 2. 包资源管理与自更新 (Package Manager CLI)
* **TS 版**：内置了 `package-manager.ts` 与命令行子指令 (`pi install <source>`, `pi update`, `pi remove`, `pi list`)。能够自动从 GitHub 仓库下载自定义提示词模板、主题、扩展插件，并支持 `pi update self` 实现 CLI 工具自身的版本升级。
* **Java 版**：没有内置的 `pi install/update` 命令。软件本身的更新依赖 `git pull && mvn clean install` 或环境二进制替代；项目技能 (`SKILL.md`) 采用自动向上扫描当前目录及祖先目录（`.agents/skills` 与 `.pi/skills`）的零配置发现机制。

#### 3. CLI 启动命令行参数矩阵 (CLI Flags)
* **TS 版**：支持接近 30 个丰富的命令行选项，例如 `--continue`/`-c` (自动延续上一会话)、`--resume`/`-r`、`--name`/`-n`、`--no-session`、`--fork`、`--export <html-path>`、`--offline` (离线模式)、`--verbose` 以及传递 `@file` 注入上下文等。
* **Java 版**：CLI 选项精简实用，专注于四大核心运行模式。支持 `--provider`, `--model`, `--api-key`, `--thinking`, `-p/--print`, `--mode` (`text`/`json`/`rpc`), `--session`, `--tools`, `--no-tools`, `--list-models`。会话恢复与会话切换更多在交互控制台内或 RPC 调用方完成。

---

### 2.3 AI 模型驱动层与协议差异 (`packages/ai`)

| 功能细分 | TypeScript 版 (`pi`) | Java 版 (`pi-java`) | 详细差异说明 |
| :--- | :--- | :--- | :--- |
| **OAuth 认证与浏览器授权** | ✅ 支持 (`auth/`, `oauth.ts`) | ❌ 不支持 | TS 版内置了 OAuth 授权跳转流与 Refresh Token 持久化；Java 版直接从 `~/.pi/agent/settings.json` 或系统环境变量读取 `API_KEY`。 |
| **AWS Bedrock 协议** | ✅ 支持 (`bedrock-provider.ts`) | ❌ 不支持 | TS 版独立实现了 AWS Bedrock 接口映射；Java 版目前支持通过 OpenAI-Compatible 代理或标准 HTTP 接入。 |
| **图像生成模型 (Image Gen)** | ✅ 支持 (`image-models.ts`, `images.ts`) | ❌ 不支持 | TS 版对接了 DALL-E 3、Imagen 等生图模型接口；Java 版专注于编程与文本推理大模型架构。 |
| **内置模型厂商广度** | 覆盖主流厂商 | **大幅增强覆盖** | Java 版新增了专用强类型驱动如 **DeepSeekProvider**, **CerebrasProvider**, **FireworksProvider**, **MoonshotProvider**, **ZaiProvider**, **TogetherProvider** 等，并对 Ollama 实现了零配置自动发现与 `/api/tags` 刷新。 |
| **HTTP 传输治理与重试** | 依赖底层 fetch / 简单重试 | **生产级退避治理 (`ProviderHttpSupport`)** | Java 版实现了标准指数退避、429/50x 状态捕获、`Retry-After` 头秒级解析及 Provider 维度的并发请求信号量封顶。 |

---

### 2.4 终端控制台与交互组件差异 (`packages/tui`)

#### 1. 终端渲染引擎底层
* **TS 版**：自研了长达 57KB 的响应式终端引擎 (`tui.ts`)，能够像 React / Vue 一样处理 DOM 树、Flex 弹性布局、焦点切换、键盘原始 ANSI Escape 解码 (`keys.ts`) 与鼠标事件。
* **Java 版**：底层基于成熟稳定的 **JLine + JAnsi**。实现了备用屏幕缓冲 (`FullScreenPanelManager`) 防止闪屏，重点聚焦在结构化日志输出、Markdown 定宽着色与 Diff 分屏排版。

#### 2. 全屏富文本编辑器组件 (Editor vs REPL)
* **TS 版**：内置了一个 77KB 的全屏终端多行代码编辑器 (`components/editor.ts`)，支持终端内的语法高亮、自动折行、光标自由移动、Undo/Redo 栈与 Kill-ring 剪贴板。
* **Java 版**：采用标准的 REPL 提示符风格 (`pi> `)。虽然在底层补齐了 `UndoStack`、`KillRing` 和 `WordNavigation` 的输入缓冲能力，并提供了 `FuzzyMatcher` 模糊联想组件，但多行代码与修改方案更推荐通过系统核心工具 (`edit`/`write`) 预览完成。

#### 3. 交互式选择器菜单 (Interactive UI Selectors)
* **TS 版**：拥有 38 个全屏可视化选择组件（如模型切换弹窗、树形对话分支导航树 `tree-selector.ts`、登录对话框、配置编辑器等），用户可以通过上下箭头和回车键在可视列表间穿梭。
* **Java 版**：交互控制台采用专业的高效指令操作体系：如 `/model <id>` 切换模型、`/models refresh` 刷新模型、`/skill-diagnostics` 查看技能命中解释、`/grill-me` 开启设计面谈、`/orchestrator-status dashboard` 调出分栏面板监控。

#### 4. 终端图片渲染支持 (Terminal Graphics)
* **TS 版**：原生支持在支持 Kitty Graphics Protocol、Sixel、iTerm2 协议的终端（如 Warp, Kitty, Ghostty, iTerm2）直接绘制输出高清大图。
* **Java 版**：仅实现了 iTerm2 内联 Base64 协议（`TerminalImage.encodeITerm2`），在非 iTerm2 终端下统一优雅降级为文字标记 fallback (`[image dimensions filename]`)。

---

### 2.5 多智能体编排与可观测性差异 (`packages/orchestrator`)

在这个关键的企业级模块上，**Java 版本超越并优化了 TS 版本的工业级可靠性**：

* **进程间通信与启动 (IPC vs Stdio RPC Launcher)**：
  * **TS 版**：使用 Node 子进程与 IPC 事件管道。
  * **Java 版**：构建了标准的 `RpcAgentProcessLauncher`，按标准输入输出流 (Stdio JSON-RPC) 拉起独立的 `coding-agent` 进程池，配合 JVM 虚线程并发发送并准确关联 JSON-RPC Request ID。
* **心跳监管与自动修复 (Heartbeat & Stale Restart Policy)**：
  * **Java 版原生内置**：实现了定期心跳扫盲 (`heartbeat()`)。对于失联、超时的智能体实例，配合可配置指数退避的 `RestartPolicy` 自动优雅终止旧子进程并携同一 CWD 与上下文重启拉起。
* **Stderr 日志轮转与监控 (Log Rotation & Tail Follow)**：
  * **Java 版原生内置**：为每个子智能体进程提供了严格的按行截断与大小上限日志轮转 (`LogRotationPolicy`)，防止多进程后台运行写爆磁盘。支持 `/orchestrator-status tail --follow` 实时输出美化的分栏日志面板。
* **实时全局控制面板 (Interactive Dashboard View)**：
  * **Java 版原生内置**：提供了动态双栏排版 (`DashboardView`)，以 `Event Stream | Stderr` 左右对照呈现子进程活动，并支持通过热键实时过滤和诊断各个 Sub-agent 的运行健康度与 Skill 命中归因。

---

### 2.6 技能诊断与面谈机制差异
* **设计面谈 (`/grill-me`)**：
  * **Java 版**：不仅实现了与 TS 版对齐的引导澄清流程，更将其进阶为会话快照持久化的状态机 (`GrillMeInterview`)，跨会话重启后能够从 JSONL 快照精准恢复面谈所处阶段与回答历史。
* **技能匹配调试 (`/skill-diagnostics`)**：
  * **Java 版**：首创并完善了技能触发的运行时诊断与层级下钻功能 (`SkillDiagnosticHistory.inspect`)，对每个候选技能计算准确的命中原因（`trigger-terms`, `trigger-patterns`, `trigger-globs`），并通过标准 JSON-RPC 暴露给 VSCode 或 Web 前端进行多级视图呈现。

---

## 三、 差异对照总结与选择指导

1. **若追求极佳的交互式 CLI UI 体验、第三方插件自定义 (Extensions) 以及从 GitHub 安装第三方主题与脚本**：
   * 当前 **TypeScript 版本 (`pi`)** 的前端交互表现力和开源社区插件体系支持更为丰富。
2. **若追求企业级生产级高并发、稳定的长耗时后台多智能体编排、严格的沙箱护栏、零配置安全隔离以及更强大的服务器端监控与日志治理**：
   * 当前 **Java 版本 (`pi-java`)** 在核心功能完全对齐的基础上，具备了卓越的虚线程架构、完备的传输重试治理与工业级的多智能体控制面板，更加适合作为研发协同基座和底层 RPC 服务核心。
