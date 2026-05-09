# cns-devintel

**Spring Boot Intelligence MCP Server**

AST-based static analysis tools for Spring Boot projects. No LLM on the server side — pure deterministic analysis. The calling agent's LLM does all the reasoning.

## Tools

| Tool | Description |
|------|-------------|
| `mapEndpoints` | Maps all REST endpoints — method, path, controller, handler |
| `detectN1Issues` | Finds potential N+1 queries (repo calls inside loops) |
| `analyzeTransactionalBoundaries` | Flags service methods missing `@Transactional` |
| `analyzeSecurityIssues` | Detects unguarded endpoints, open CORS, missing SecurityConfig |
| `runFullAnalysis` | Runs all four analyses in a single parse pass |

All tools accept either:
- A GitHub repository URL: `https://github.com/user/repo`
- An absolute local path: `/home/user/my-spring-project`

## Usage with Claude Desktop

Add to your `mcp.json`:

```json
{
  "servers": {
    "cns-devintel": {
      "type": "sse",
      "url": "https://cns-devintel.onrender.com/sse"
    }
  }
}
```

Then ask copilot:
- *"Analyse the security posture of https://github.com/user/my-api"*
- *"Find all REST endpoints in this Spring Boot project: https://github.com/..."*
- *"Run a full analysis on my project and tell me what to fix first"*

## Running Locally

```bash
mvn spring-boot:run
```

Server starts on `http://localhost:8080`.
SSE endpoint: `http://localhost:8080/sse`

For local path analysis, run the server locally and pass absolute paths.

## Architecture

```
Agent (Claude / Cursor / any MCP client)
        ↓  MCP tool call (SSE)
SpringIntelTools  (@Tool methods)
        ↓
AnalysisOrchestrator  (parse once, run all engines)
        ↓
JavaAstParser  (JavaParser — pure AST, no runtime)
        ↓
┌─────────────────────────────────┐
│ EndpointMapperService           │
│ N1DetectorService               │
│ TransactionalAnalyzerService    │
│ SecurityAnalyzerService         │
└─────────────────────────────────┘
```

## Tech Stack

- Java 21 / Spring Boot 3.3
- Spring AI MCP Server (SSE transport)
- JavaParser 3.25 (AST analysis)
- JGit 6.x (GitHub clone)

## Deployment (Render)

1. Push this repo to GitHub
2. Create a new **Web Service** on Render
3. Connect your repo
4. Build command: `mvn package -DskipTests`
5. Start command: `java -jar target/*.jar`
6. Or use Docker: Render auto-detects the `Dockerfile`

## Limitations

- Analysis is heuristic-based (AST only, no symbol resolution)
- N+1 detection works on naming conventions — repos must be named `*Repository` or `*repo`
- Security flagging of auth endpoints (login, register) is expected — filter by context
- GitHub cloning requires public repositories

## Coming Soon

- `analyzeProjectStructure` — layer counts, dependency graph, profile detection
- Python MCP server (thin wrapper over this engine)
- Private repo support via GitHub token

## Disclaimer
This project is an independent personal open-source initiative and is not affiliated with or endorsed by any employer or client organization.


## License

Licensed under the Apache License, Version 2.0.

Copyright © 2026 Chinmay Shivratriwar

Commercial usage permitted under the terms of Apache 2.0.
