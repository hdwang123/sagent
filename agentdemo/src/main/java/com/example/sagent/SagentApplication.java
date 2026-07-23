package com.example.sagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.ai.mcp.client.common.autoconfigure.annotations.McpClientAnnotationScannerAutoConfiguration;
import org.springframework.ai.mcp.client.httpclient.autoconfigure.StreamableHttpHttpClientTransportAutoConfiguration;
import org.springframework.ai.mcp.client.httpclient.autoconfigure.SseHttpClientTransportAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = "com.example",
        exclude = {
            McpClientAutoConfiguration.class,
            McpToolCallbackAutoConfiguration.class,
            McpClientAnnotationScannerAutoConfiguration.class,
            StreamableHttpHttpClientTransportAutoConfiguration.class,
            SseHttpClientTransportAutoConfiguration.class
        }
)
public class SagentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagentApplication.class, args);
    }
}
