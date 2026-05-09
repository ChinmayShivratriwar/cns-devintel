package com.chinmayshivratriwar.cns_devintel.tools;

import com.chinmayshivratriwar.cns_devintel.resolver.SourceResolver;
import com.chinmayshivratriwar.cns_devintel.resolver.SourceResolver.ResolvedSource;
import com.chinmayshivratriwar.cns_devintel.service.AnalysisOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * MCP Tool surface — these are the 5 methods the calling agent can invoke.
 *
 * HOW SPRING AI MCP WORKS:
 *   Spring AI scans this @Component for @Tool annotated methods.
 *   Each @Tool becomes one MCP tool entry — with its description exposed
 *   to the agent as the tool's documentation.
 *
 *   The agent reads the description and decides which tool to call.
 *   It passes arguments as JSON. Spring AI deserializes them into method params.
 *   We return a String (JSON). The agent's LLM interprets it.
 *
 * WHY String return type and not List<Endpoint>:
 *   MCP tool results are always text. Spring AI can serialize objects,
 *   but returning an explicit JSON string gives us full control over the output
 *   format and avoids any surprises in serialization.
 *
 * WHY no LLM here:
 *   This server is pure deterministic analysis — AST parsing only.
 *   The caller's LLM (Claude, GPT, whatever) does all the reasoning.
 *   We never touch an LLM API. Zero token cost on our side.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringIntelTools {

    private final AnalysisOrchestrator orchestrator;
    private final SourceResolver       sourceResolver;
    private final ObjectMapper         objectMapper;

    @Tool(description = """
            Maps all REST endpoints in a Spring Boot project.
            Returns a list of endpoints with HTTP method, full path, controller class, and handler method name.
            Useful for API surface discovery, documentation generation, and security audits.
            """)
    public String mapEndpoints(
            @ToolParam(description = "GitHub repository URL (https://github.com/user/repo) or absolute local path to the project root")
            String source
    ) {
        return execute(source, path -> objectMapper.writeValueAsString(
                orchestrator.mapEndpoints(path)
        ));
    }

    @Tool(description = """
            Detects potential N+1 query problems in a Spring Boot project.
            Scans @Service and @Component classes for repository method calls inside loops (for, forEach, while).
            Returns issues with the service class, method name, repository call involved, and line number.
            Results are heuristic-based (AST only, no runtime analysis) — use as a starting point for review.
            """)
    public String detectN1Issues(
            @ToolParam(description = "GitHub repository URL (https://github.com/user/repo) or absolute local path to the project root")
            String source
    ) {
        return execute(source, path -> objectMapper.writeValueAsString(
                orchestrator.detectN1Issues(path)
        ));
    }


    @Tool(description = """
            Identifies missing @Transactional boundaries in a Spring Boot project.
            Finds @Service methods that call repository methods without @Transactional on the method or class.
            Missing transaction boundaries can cause LazyInitializationException, missing rollbacks,
            and data integrity issues under concurrent load.
            Returns the service class, method, repository call found, and line number.
            """)
    public String analyzeTransactionalBoundaries(
            @ToolParam(description = "GitHub repository URL (https://github.com/user/repo) or absolute local path to the project root")
            String source
    ) {
        return execute(source, path -> objectMapper.writeValueAsString(
                orchestrator.analyzeTransactional(path)
        ));
    }


    @Tool(description = """
            Audits security posture of a Spring Boot project's REST layer.
            Detects three issue types:
              UNGUARDED_ENDPOINT — REST endpoint with no @PreAuthorize / @Secured / @RolesAllowed
              CORS_OPEN          — @CrossOrigin present without explicit origin restrictions
              NO_SECURITY_CONFIG — No SecurityFilterChain or WebSecurityConfigurerAdapter found
            Returns each issue with controller class, method, path, and a description.
            Note: auth endpoints (login, register) being flagged as UNGUARDED is expected — filter by context.
            """)
    public String analyzeSecurityIssues(
            @ToolParam(description = "GitHub repository URL (https://github.com/user/repo) or absolute local path to the project root")
            String source
    ) {
        return execute(source, path -> objectMapper.writeValueAsString(
                orchestrator.analyzeSecurity(path)
        ));
    }


    @Tool(description = """
            Runs all four analyses in a single pass on a Spring Boot project:
              - Endpoint mapping
              - N+1 query detection
              - Transactional boundary analysis
              - Security audit
            Parses the project once and runs all engines — more efficient than calling each tool separately.
            Returns a combined JSON report with keys: endpoints, n1Issues, transactionalIssues, securityIssues.
            Use this for a full project health check.
            """)
    public String runFullAnalysis(
            @ToolParam(description = "GitHub repository URL (https://github.com/user/repo) or absolute local path to the project root")
            String source
    ) {
        return execute(source, path -> objectMapper.writeValueAsString(
                orchestrator.runFullAnalysis(path)
        ));
    }

    private static final Semaphore CONCURRENCY_GATE    = new Semaphore(3);
    private static final int       GATE_TIMEOUT_SECONDS = 20;

    /**
     * WHY this wrapper exists:
     *   Every tool does the same three things: resolve → analyze → cleanup.
     *   Extracting them here means error handling and cleanup are guaranteed
     *   to run regardless of which tool is called or whether it throws.
     *
     *   The @FunctionalInterface AnalysisTask lets each tool pass in only
     *   the part that differs (what to do with the resolved path).
     */
    private String execute(String source, AnalysisTask task) {
        ResolvedSource resolved = null;
        boolean acquired = false;
        try {
            // acquire a slot — block up to GATE_TIMEOUT_SECONDS, then fail cleanly
            acquired = CONCURRENCY_GATE.tryAcquire(GATE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
            if (!acquired) {
                return errorJson("Server is busy — too many concurrent analysis requests. Please retry in a moment.");
            }

            log.info("[cns-devintel] Resolving source: {}", source);
            resolved = sourceResolver.resolve(source);

            String result = task.run(resolved.path());
            log.info("[cns-devintel] Analysis complete for: {}", source);
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorJson("Request interrupted while waiting for a free analysis slot.");
        } catch (Exception e) {
            log.error("[cns-devintel] Analysis failed for: {} — {}", source, e.getMessage());
            return errorJson(e.getMessage());
        } finally {
            if (resolved != null) sourceResolver.cleanup(resolved);
            if (acquired)         CONCURRENCY_GATE.release();
        }
    }

    private String errorJson(String message) {
        return "{\"error\": \"" + message.replace("\"", "'") + "\"}";
    }

    @FunctionalInterface
    private interface AnalysisTask {
        String run(java.nio.file.Path path) throws Exception;
    }
}