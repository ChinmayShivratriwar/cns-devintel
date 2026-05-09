package com.chinmayshivratriwar.cns_devintel.config;


import com.chinmayshivratriwar.cns_devintel.tools.SpringIntelTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the MCP layer together.
 *
 * HOW SPRING AI MCP TOOL REGISTRATION WORKS:
 *
 *   Spring AI MCP Server scans the application context for beans of type
 *   ToolCallbackProvider. Each provider tells the MCP runtime which methods
 *   are tools and how to invoke them.
 *
 *   MethodToolCallbackProvider does the reflection work:
 *     - Finds all @Tool annotated methods on SpringIntelTools
 *     - Reads their descriptions → these become the tool "docs" agents see
 *     - Reads @ToolParam descriptions → these become parameter docs
 *     - Handles JSON arg deserialization and method invocation at runtime
 *
 *   You could register multiple tool objects here (pass varargs to toolObjects())
 *   as the project grows — e.g., when ProjectStructureTools is ready.
 *
 * WHY ObjectMapper is a bean here:
 *   Jackson's ObjectMapper is thread-safe once configured. Making it a singleton
 *   bean means we don't instantiate it on every tool call. SpringIntelTools
 *   injects it via constructor.
 */
@Configuration
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider springIntelToolProvider(SpringIntelTools tools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
