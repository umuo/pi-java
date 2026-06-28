# M1: Java 多模块拆分设计

## Maven reactor

```text
pi-java/
  pom.xml
  packages/
    common/
    ai/
    agent/
    tui/
    coding-agent/
    orchestrator/
```

## 模块依赖方向

```text
pi-common
  <- pi-ai
  <- pi-agent-core
  <- pi-tui
  <- pi-coding-agent
  <- pi-orchestrator

pi-ai <- pi-agent-core <- pi-coding-agent <- pi-orchestrator
pi-tui ------------------^
```

## 包名映射

| TS 路径 | Java package |
| --- | --- |
| `packages/ai/src` | `works.earendil.pi.ai` |
| `packages/agent/src` | `works.earendil.pi.agent` |
| `packages/tui/src` | `works.earendil.pi.tui` |
| `packages/coding-agent/src` | `works.earendil.pi.codingagent` |
| `packages/orchestrator/src` | `works.earendil.pi.orchestrator` |
| 跨包公共兜底 | `works.earendil.pi.common` |

## 领域模型原则

1. TS discriminated union 转为 Java `sealed interface` + `record`。
2. TS `Record<string, unknown>` 转为 `Map<String, JsonNode>` 或领域化 record，禁止裸 `Object` 扩散。
3. TS async stream 转为 `Flow.Publisher<T>` 或 `Stream<CompletableFuture<T>>`，provider 层统一为 `AssistantMessageEventStream`。
4. TS tool schema 转为 `ToolDefinition` + Jackson schema node + validator。
5. Node 文件/进程 API 转为 Java NIO、`ProcessBuilder`、`FileChannel.lock()`。
6. CLI 层使用 picocli；交互 TUI 层使用 JLine/Jansi，但保留原 `Component.render()` 组合模型。

## 分阶段转换顺序

| 阶段 | 模块 | 内容 |
| --- | --- | --- |
| P0 | `pi-common` | JSON/YAML、glob/ignore、ANSI、终端宽度、截断、JSONL、事件流、锁 |
| P1 | `pi-ai` | message/model/tool/provider 核心类型，统一流事件，provider registry |
| P2 | `pi-agent-core` | agent loop、tool execution、session tree、compaction/harness |
| P3 | `pi-tui` | key parsing、component tree、terminal surface、markdown/image renderer |
| P4 | `pi-coding-agent` | CLI、config、tools、session manager、skills/extensions、interactive/rpc |
| P5 | `pi-orchestrator` | IPC protocol、server/client、supervisor、storage |

