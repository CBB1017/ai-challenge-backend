package com.brycenkorea.template.config;

import io.modelcontextprotocol.client.McpAsyncClient;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;

public class McpConfig {
    @Bean
    public ToolCallbackProvider crawlingToolProvider(McpAsyncClient crawlingMcpClient) {
        return AsyncMcpToolCallbackProvider.builder()
                                           .mcpClients(crawlingMcpClient)
                                           .build();
    }
}
