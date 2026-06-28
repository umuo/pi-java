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
| **`ai`** | 多模型提供商统一接口、SSE 流式解析、Token 统计、Message 模型 | 🟡 核心实现 | **75%** | 实现了 `Message`/`Content` 体系与 `OpenAiProvider`, `AnthropicProvider`；待扩展 Gemini/Ollama 等 |
| **`agent`** | 智能体循环 (AgentLoop)、工具契约、JSONL v3 会话存储、UUIDv7 | ✅ 完整实现 | **100%** | `AgentLoop`, `JsonlSessionStorage`, `AgentTool` 支持生命周期拦截与回传 |
| **`tui`** | 终端 UI 渲染、ANSI 格式化、文本排版、交互式组件 | 🟡 基础实现 | **60%** | 实现了基础控制台 REPL I/O 与 ANSI 截断；全屏高级窗口与 Diff 视图待扩展 |
| **`coding-agent`**| 编程工具集、沙箱执行、安全护栏、上下文压缩、配置管理、CLI 启动器 | ✅ 核心自治完成 | **90%** | 7大核心工具、沙箱 Bash、Harness 鉴权、Token 压缩、RPC 模式完全就绪 |
| **`orchestrator`**| 多智能体管理、进程树监控、IPC 通信协议、实例持久化 | 🟢 架构完成 | **85%** | 实现了 `OrchestratorStorage`, `OrchestratorSupervisor`, IPC 消息模型 |

---

## 三、 当前已改造功能进度详解 (Completed Status & Evidence)

截至本阶段，`pi-java` 已经成功实现了底层通信、智能体主循环到上层编程工具链的完整闭环，执行 `mvn test` 全部 108+ 项核心自动化单元测试 100% 通过（`BUILD SUCCESS`）。

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
* **`--list-models`**：列出本地配置与默认注册的所有可用 LLM 模型。
* **`--print` (`PrintModeRunner`)**：单次指令问答，处理完毕后自动退出，适合脚本管道集成。
* **交互式控制台 REPL (`InteractiveModeRunner`)**：实现了多轮对话、模型动态切换指令 (`/model`)、会话状态保存与恢复。
* **标准输入输出 RPC 模式 (`RpcModeRunner`)**：实现基于 JSON-RPC 的进程间通信，为 VSCode 插件、Web 前端及多智能体管理程序提供底层通信管道。

---

## 四、 差距分析与待改造清单 (Gap Analysis & Roadmap)

为了达到真正的全功能超越，下次工作应当聚焦于以下四个方向进行延续改造：

### 4.1 AI 模型生态扩展 (`packages/ai`)
* **现状**：目前已原生对接并测试了 OpenAI (`OpenAiProvider`) 与 Anthropic (`AnthropicProvider`) 协议格式（同时也支持通过 NewAPI/OpenRouter 等兼容接口接入 DeepSeek v4 等模型）。
* **下一步任务**：
  1. 补充 **Google Gemini 官方 API 协议** provider (支持原生结构化输出与函数调用解析)。
  2. 补充 **Ollama / Groq / xAI / Mistral** 内置驱动适配器。
  3. 完善 HTTP 传输层的重试机制 (Exponential Backoff Retry) 与速率限制器 (Rate Limiter)。

### 4.2 TUI 终端界面高级渲染 (`packages/tui`)
* **现状**：目前 CLI 交互处于基础控制台输出阶段，能够保证正确的文字与 Tool 执行状态打印，但缺乏 TS 版本中基于全屏终端的炫酷渲染。
* **下一步任务**：
  1. 引入并深化基于 JLine3 / JAnsi 的全屏面板管理器。
  2. 实现代码块语法高亮渲染 (Markdown Syntax Highlighting) 与实时分屏 Diff 对比组件。
  3. 增设底部状态栏 (Footer Display) 的实时渲染：动态展示当前 Git 分支、模型名称、Token 消耗量与延时统计 (`Timings`)。

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
工作区根目录为：`/Users/gitsilence/github/pi-java`。
启动工作前，请先运行测试确保基线稳固：
```bash
# 编译整个模块树并执行全量单元测试 (跳过需联网的 Live API 测试)
mvn test -Dtest=!BuiltinProvidersTest
```
预期输出：`BUILD SUCCESS`，且 `pi-coding-agent` 等核心模块测试全数通过。

### 5.2 下一步切入点推荐 (优先级推荐)
**建议切入点 A：TUI 富文本控制台增强**
* **目标文件**：`packages/coding-agent/src/main/java/works/earendil/pi/codingagent/cli/InteractiveModeRunner.java` 与 `packages/tui/src/...`
* **任务**：将 `FooterDataProvider` 获取到的 Git 分支与 Token 统计信息，在每次用户输入提示符 (`pi> `) 上方或右侧进行结构化格式渲染。

**建议切入点 B：新增 Google Gemini 模型驱动**
* **目标文件**：`packages/ai/src/main/java/works/earendil/pi/ai/provider/GeminiProvider.java`
* **任务**：参考 `OpenAiProvider.java` 的结构，实现 `BuiltinProvider` 接口，解析 Gemini 原生 JSON 响应与 Function Calling 结构，并在 `ModelRegistry` 中注册激活。

### 5.3 代码开发与提交规范
1. **新增模型/类时**：务必使用 Java 21 Record 编写数据载体，保证不可变性。
2. **测试驱动**：在任何核心工具或处理逻辑修改后，请在对应的 `src/test/java/...` 下补充单测。
3. **配置文件安全**：所有动态运行产生的会话、凭证或模型配置文件，一律使用 `AuthStorage` 或 `SettingsManager` 写入本地的用户目录，**绝不暴露或提交到 Git 追踪列表**。

---
*文档更新时间：2026-06-29 | 维护者：Pi-Java Engineering Team*
