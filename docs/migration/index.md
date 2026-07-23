# 迁移进度 (Migration Progress)

`pi-java` 是从原始的 TypeScript 项目 `pi` 迁移而来的生产级仓库。在整个迁移过程中，我们维护了详细的功能缺口与开发执行情况。

本目录下包含两份核心进展文档，作为对整个演进过程的历史记录与参考：

当前 TS 上游同步基线见 **[2026-07-23 同步记录](PI_TS_SYNC_2026-07-23.md)**；供自动化读取的完整提交 ID 保存在 [`PI_TS_UPSTREAM_COMMIT`](PI_TS_UPSTREAM_COMMIT)。

## 1. 迁移执行进度
**[JAVA_MIGRATION_EXECUTION_PROGRESS.md](JAVA_MIGRATION_EXECUTION_PROGRESS.md)**

这里记录了迁移中**已完成的数百项优化点**。您可以将其视为一份详细的 Changelog，展示了我们是如何一步步将功能从 TS 迁移，并在 Java 生态中完成增强与落地。

## 2. 原始 TS 功能缺口
**[PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md](PI_TS_EXCELLENT_FEATURES_NOT_MIGRATED.md)**

这里记录了原始 TS 项目中**尚未完全迁移到 Java** 的高级功能。通过对比这份文档，开发者能够清楚当前框架在哪部分存在局限，以及未来的建设方向。
