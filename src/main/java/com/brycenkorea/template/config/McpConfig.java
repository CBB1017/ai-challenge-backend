//package com.brycenkorea.template.config;
//
//import io.modelcontextprotocol.client.McpAsyncClient;
//import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.security.core.context.ReactiveSecurityContextHolder;
//import org.springframework.web.reactive.function.client.ClientRequest;
//import org.springframework.web.reactive.function.client.WebClient;
//import reactor.core.publisher.Mono;
//
//import java.time.Duration;
//@Configuration
//public class McpConfig {
//
//    @Value("${mcp.client.streamable-http.connections.crawling.url}")
//    private String serverUrl;
//
//    @Bean
//    public McpAsyncClient mcpAsyncClient(WebClient.Builder builder) {
//
//        WebClient webClient = builder
//            .baseUrl(serverUrl)
//            .filter((request, next) ->
//                Mono.deferContextual(ctx -> {
//                    String userId = ctx.getOrDefault("userId", "anonymous");
//
//                    ClientRequest newRequest = ClientRequest.from(request)
//                        .header("X-User-Id", userId)
//                        .build();
//
//                    return next.exchange(newRequest);
//                })
//            )
//            .build();
//
//        var transport = HttpClientSseClientTransport.builder(webClient)
//            .build();
//
//        return McpAsyncClient.async(transport)
//            .requestTimeout(Duration.ofSeconds(20))
//            .build();
//    }
//}