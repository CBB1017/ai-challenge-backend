//package com.brycenkorea.template.controller;
//
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//@RestController
//@RequestMapping("/api/browser")
//public class BrowserAutomationController {
//
//    private final ChatClient chatClient;
//
//    @Autowired
//    public BrowserAutomationController(ChatClient.Builder builder, McpSyncClient mcpClient) {
//
//        // 1. MCP 클라이언트의 22개 도구를 Spring AI가 인식할 수 있는 Provider로 묶음
//        var mcpToolProvider = new SyncMcpToolCallbackProvider(mcpClient);
//
//        // 2. ChatClient에 시스템 프롬프트와 도구 바인딩
//        this.chatClient = builder
//            .defaultSystem("너는 브라우저를 제어하는 AI 에이전트야. 제공된 도구를 활용해 사용자의 요청을 수행해.")
//            .defaultTools(mcpToolProvider)
//            .build();
//    }
//
//    @PostMapping("/execute")
//    public String execute(@RequestBody String prompt) {
//        return chatClient.prompt(prompt).call().content();
//    }
//}