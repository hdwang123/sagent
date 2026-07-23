package com.example.mcpserver;

import org.springframework.ai.mcp.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpConfig {

    @Bean
    public MethodToolCallbackProvider mcpToolCallbackProvider(McpServerController mcpServerController) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(mcpServerController)
                .build();
    }
}
