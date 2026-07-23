# pi-java

Production Java port of the **pi** TypeScript monorepo. Built with Java 21 and Maven.

## Overview

`pi-java` is a modular, high-performance agentic framework and coding assistant ecosystem implemented in modern Java. It provides essential building blocks for creating AI agents, terminal interfaces, and multi-agent orchestrations.

## Architecture & Modules

The project is structured as a Maven multi-module monorepo:

* **`packages/common`**: Core utility libraries, shared interfaces, and common data models.
* **`packages/ai`**: AI/LLM client integrations, provider abstractions, and tokenization utilities.
* **`packages/agent`**: Core agent loop, prompt engineering framework, and tool execution lifecycle.
* **`packages/tui`**: Terminal User Interface components powered by JLine and JAnsi for rich interactive CLI experiences.
* **`packages/coding-agent`**: Specialized agent implementation designed for codebase understanding, editing, and software engineering workflows.
* **`packages/server`**: Multi-agent coordination, task delegation, and complex workflow management.

For the implementation-level flow—including the agent state machine, coding
harness assembly, built-in tool catalog, session/compaction behavior, and Java
extension SPI—see [Agent Loop, Harness, and Extensions](docs/agent-loop-and-harness.md).

## Prerequisites

* **JDK 21** or later
* **Apache Maven 3.9+**

## Getting Started

### Building the Project

To compile the entire project and install the artifacts to your local Maven repository:

```bash
mvn clean install
```

### Running Tests

To execute the unit test suite across all modules:

```bash
mvn test
```

## Coding Agent Settings

The coding agent reads JSONC settings from `~/.pi/agent/settings.json`, with trusted
project overrides from `.pi/settings.json`. Provider transport retry and rate-limit
settings can be configured globally and then overridden per provider id:

```jsonc
{
  "retry": {
    "enabled": true,
    "maxRetries": 4,
    "baseDelayMs": 150,
    "provider": {
      "timeoutMs": 120000,
      "maxRetries": 3,
      "maxRetryDelayMs": 4000,
      "maxConcurrentRequests": 4
    },
    "providers": {
      "openai": {
        "timeoutMs": 180000,
        "maxRetries": 5,
        "baseDelayMs": 250,
        "maxRetryDelayMs": 8000,
        "maxConcurrentRequests": 2
      },
      "ollama": {
        "timeoutMs": 600000,
        "maxRetries": 0,
        "maxConcurrentRequests": 1
      }
    }
  }
}
```

`retry.provider` applies to all provider HTTP calls. Entries under
`retry.providers` override those values for matching provider ids such as `openai`,
`google`, `groq`, `mistral`, `ollama`, and `xai`.

## Interactive Teamwork

Inside the coding-agent interactive console, `/teamwork-preview [compact]` prints the
planned sub-agent roles for the current session. To launch those roles through the
server, pass an objective:

```text
/teamwork-preview run implement the settings import flow
/teamwork-preview compact run review the auth storage migration
```

Execution uses the local server runtime settings and reports each role's
instance id, event count, response summary, and error state.

Use `/grill-me <topic>` in the interactive console to start a structured design
interview before implementation. The active interview tracks the topic, phase,
recorded answers, and assistant question summaries in the session JSONL so it
can be restored when the session is reopened. Use `/grill-me answer <text>` to
record an answer and continue the interview, `/grill-me status` to inspect the
current phase and answer history, and `/grill-me reset` to clear it.

Use `/server-status` to inspect the local server directory, runtime
settings, persisted instances, heartbeat age, stderr log index, and RPC event
stream availability. Use
`/server-status dashboard [instanceId] [events] [filters]` to print a
dashboard snapshot with instances, recent RPC events, current stderr tail
columns, and current-session skill diagnostic aggregates. Dashboard skill
diagnostic filters use the same `skill=...`, `model=visible|manual`, and
`reason=...` syntax as `/skill-diagnostics`. Use
`/server-status tail [instanceId] [lines]` to
print recent stderr log lines from the latest log for all instances or a single
instance. Use `/server-status tail --follow [instanceId]` to stream newly
appended stderr lines as structured panels in the current interactive session,
and `/server-status tail --stop` to cancel that log subscription. Use
`/server-status events [instanceId]` to subscribe the current interactive
session to live RPC events from subsequent server work; live events are
also rendered as structured panels. Use `/server-status events stop` to
cancel that subscription.

## Skills

When a project is trusted, pi-java loads project skills from `.pi/skills` and
from `.agents/skills` in the current directory or its ancestors up to the git
repository root. Skill descriptions in the system prompt support scoped
placeholders such as `{{cwd}}`, `${agent_dir}`, `{{date}}`, `{{skill_name}}`,
`{{skill_dir}}`, and `{{skill_path}}`.

Type `/skill:name optional instructions` in interactive, print, or RPC prompt
paths to expand a loaded skill's `SKILL.md` body into the user prompt. Set
`"enableSkillCommands": false` in settings to keep `/skill:*` text literal.
Interactive `/help` lists the currently loaded skill commands.
JSON print and RPC event streams emit `skill_command` events with `start`, `end`,
or `error` phases when a skill command is processed.
Skills with `disable-model-invocation: true` are hidden from model-visible skill
lists but remain available through explicit `/skill:name` commands.
Skills can also declare `model-invocation: manual` for the same manual-only
behavior, or add `trigger-terms`, `trigger-patterns`, and `trigger-globs` lists
to expose more precise activation hints in the model-visible skill prompt.
When a normal prompt matches those trigger hints, JSON print and RPC event
streams emit `skill_trigger_diagnostic` events with matched `skill`, `path`,
`modelVisible`, and `reasons` fields. Explicit `/skill:name` commands still emit
only the `skill_command` lifecycle event for that invocation. Interactive mode
renders the same diagnostic as a terminal-width panel before the assistant
response and stores recent diagnostics in the session JSONL so they can be
restored when the session is reopened. Use `/skill-diagnostics` to show the
latest diagnostic again, `/skill-diagnostics history` to list recent matches,
`/skill-diagnostics json` to export the same snapshot for external views, or
`/skill-diagnostics clear` to persistently clear that diagnostic history.
History and JSON export support filters such as `skill=demo`, `model=visible`,
`model=manual`, and `reason=term`; interactive `history` and `json` also accept
`branch=<entryId>` to inspect diagnostics from a specific session branch without
switching the active conversation. JSON-RPC clients can call
`skill_diagnostics` with optional `params.skill`, `params.model`, and
`params.reason` to retrieve the same filtered session snapshot. RPC snapshots
also accept `params.offset`, `params.limit`, and `params.sort` (`newest` or
`oldest`) and include page metadata plus summary counts for skills, reasons,
model-visible matches, and manual-only matches. Add `params.branch` to query a
specific branch leaf, or `params.session` with a JSONL session file path to pull
the same diagnostics from another saved session; responses include `source`
metadata with the resolved session id, session file, and branch.
Use `skill_diagnostic_sources` with optional `params.limit` and
`params.includeEmpty` to list selectable diagnostic sources across recent
sessions. The result includes each session plus a pruned `branchTree` whose
nodes carry diagnostics counts, latest capture time, top skills, and reasons.
Use `skill_diagnostic_picker` for a flattened picker-ready view with one row per
diagnostic branch source; interactive mode exposes the same view as
`/skill-diagnostics picker`.

## Server Settings

The server reads runtime settings from `~/.pi/server/server.json`,
or from `$PI_SERVER_DIR/server.json` when `PI_SERVER_DIR` is set:

```json
{
  "restart": {
    "maxAttempts": 3,
    "baseBackoffMs": 30000,
    "maxBackoffMs": 300000
  },
  "logRotation": {
    "enabled": true,
    "maxBytes": 1048576,
    "maxBackups": 3
  }
}
```

`restart` controls stale instance restart retries and exponential backoff.
`logRotation` controls per-instance stderr log rotation; set
`"enabled": false` to disable rotation.

## Transformation Roadmap & Progress

For a comprehensive guide detailing the feature parity between the original TypeScript version (`pi`) and `pi-java`, current progress, and next steps for continuation, please refer to:
👉 **[Pi TS to Java Transformation Roadmap](docs/migration/index.md)**

## License

All rights reserved.
