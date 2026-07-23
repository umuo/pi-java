# 核心功能 (Core Features)

**pi-java** 作为面向代码和工程开发人员的智能工具箱，整合了多种高级功能：

## 1. 交互式多智能体团队 (Interactive Teamwork)

在 `coding-agent` 的交互式控制台中，您不再是单打独斗，而是作为一个团队在解决问题：

- `/teamwork-preview [compact]`：可以随时预览当前的规划中涉及哪些子智能体 (sub-agent) 角色。
- 当执行复杂任务（例如 `/teamwork-preview run implement the settings import flow`）时，后台 `server` 会启动多个特定的角色，协同推进。
- 通过 `/server-status` 及附加的 `dashboard`、`tail` 命令，您可以随时追踪整个微型团队的心跳情况、执行事件流和标准错误日志，在主控制台上就能鸟瞰一切。

## 2. 基于配置文件的资源管理

利用本地 `.pi/settings.json` 与全局的 `~/.pi/agent/settings.json`：
- **无缝覆盖机制**：针对可信项目，项目级的配置项可以无缝覆盖全局配置，实现定制化。
- **自定义 Retry 与限流 (Rate Limiting)**：您可以针对不同的提供商（例如 `openai`, `ollama`）设置各自的超时时间、重试次数、重试间隔和并发限制，以最优化使用成本与体验。

## 3. 极其强大的 Skills (技能) 机制

**Skill** 是教给智能体的可复用知识库或特定任务的解决手段。
- 通过配置文件和依赖包管理（npm/git），`pi-java` 可以在不同作用域中自动发现这些技能（`SKILL.md`）。
- **参数化提示（Templated Prompts）**：支持丰富的环境变量注入如 `{{cwd}}`, `{{skill_name}}`。
- **动态发现与激活**：模型可以通过包含特定触发关键词自动激活后台诊断（Skill Diagnostics），记录匹配日志并在会话重载时恢复。或者，用户也可以在交互界面中手动使用 `/skill:name` 展开指令。
- **诊断大盘 (`/skill-diagnostics`)**：您可以查询历史上的技能触发信息，还可以输出 JSON 或按特定的分支 (branch)、模型 (model) 进行过滤，掌握整个模型是如何“学习”与利用您的仓库的。

## 4. `pi config` 终端包管理器 

通过类似 npm/git 的依赖源引入外部的 Prompts、Themes 或 Skills。
- 用户可以直接通过 `pi update` 命令更新远端包配置，如果更新失败，还会获得友好的 fallback 手动指令。
- 交互式 `pi config` 命令提供 TUI 面板，按空格键一键开启/禁用某个资源，无需手写复杂的 JSON 配置，极大地降低了外部资源的使用门槛。

## 5. 设计访谈 (`/grill-me`)

当在实现困难或存在歧义的功能时，您可能希望先在设计阶段和 AI 理清思路：
- `/grill-me <topic>` 会启动一个结构化设计的访谈流。
- AI 充当提问者，梳理不确定的设计边界；当收集完足够的回答后，自动生成并继续下一个阶段的对话。
- 所有会话被持久化写入 JSONL，您可以放心退出并重新载入 `/grill-me status`。
