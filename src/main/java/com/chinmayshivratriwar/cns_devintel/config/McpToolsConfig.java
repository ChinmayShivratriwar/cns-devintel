package com.chinmayshivratriwar.cns_devintel.config;


import com.chinmayshivratriwar.cns_devintel.tools.SpringIntelTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


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
