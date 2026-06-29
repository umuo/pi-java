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
* **`packages/orchestrator`**: Multi-agent coordination, task delegation, and complex workflow management.

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

## Transformation Roadmap & Progress

For a comprehensive guide detailing the feature parity between the original TypeScript version (`pi`) and `pi-java`, current progress, and next steps for continuation, please refer to:
👉 **[Pi TS to Java Transformation Roadmap](docs/PI_TS_TO_JAVA_TRANSFORMATION_ROADMAP.md)**

## License

All rights reserved.
