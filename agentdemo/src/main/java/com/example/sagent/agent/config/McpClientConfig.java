package com.example.sagent.agent.config;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.List;

@Configuration
public class McpClientConfig {

    @Value("${mcp.server.url:http://localhost:8081/mcp}")
    private String mcpServerUrl;

    @Bean
    @Lazy
    public SyncMcpToolCallbackProvider mcpToolCallbacks() {
        var transport = HttpClientStreamableHttpTransport.builder(mcpServerUrl).build();
        var client = McpClient.sync(transport).build();
        client.initialize();
        return SyncMcpToolCallbackProvider.builder()
                .mcpClients(List.of(client))
                .build();
    }
}
