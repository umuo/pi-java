# pi-java 项目概览 (Overview)

**pi-java** 是原始 TypeScript `pi` 单体仓库 (monorepo) 的生产级 Java 移植版本。使用 Java 21 和 Maven 构建，旨在提供一个模块化、高性能的 AI Agent 框架与代码助手生态。

## 核心特性 (Key Features)

- **模块化架构**: 清晰的多模块 Maven 结构，从核心数据模型到复杂的智能体编排，解耦设计易于维护和扩展。
- **SPI 扩展机制**: 利用 Java 强大的 Service Provider Interface，打造媲美动态脚本语言的插件生态，实现工具、事件和 UI 的无缝接入。
- **设置驱动的包管理**: 基于 `.pi/settings.json` 的智能依赖解析与包管理，提供极高灵活性的技能 (Skills)、提示词 (Prompts) 与主题 (Themes) 配置。
- **丰富的终端用户体验 (TUI)**: 基于 JLine3 的全屏组件和交互系统，带来超越传统命令行的丝滑体验。
- **多智能体编排 (Multi-Agent Orchestrator)**: 支持团队协作 (`/teamwork-preview`) 与任务委派的健壮智能体编排网络。

## 环境要求 (Prerequisites)

* **JDK 21** 或更高版本
* **Apache Maven 3.9+**

## 快速开始 (Getting Started)

### 构建项目 (Building)

编译整个项目并将其构建的 artifacts 安装到本地 Maven 仓库：

```bash
mvn clean install
```

### 运行测试 (Running Tests)

执行跨所有模块的单元测试：

```bash
mvn test
```

## 目录结构向导

- 欲了解每个模块的具体职责，请参考 **[架构设计](architecture.md)**。
- 探索项目的亮点与创新，请查阅 **[亮点设计](design-highlights.md)**。
- 若需了解原 TS 项目向 Java 演进的过程，请浏览 **[迁移进度](migration/index.md)**。
