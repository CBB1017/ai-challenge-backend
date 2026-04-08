package com.brycenkorea.template.config;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder().chatMemoryRepository(repository).maxMessages(20).build();
    }

    @Bean
    public ChatClient baseChatClient(
        ChatClient.Builder builder,
        ChatMemory chatMemory,
        AsyncMcpToolCallbackProvider mcpTools
    )
    {

        String systemText = """
                너는 우리 회사의 친절하고 똑똑한 AI 비서야.
                [사용자 정보]
                - 현재 대화 중인 사용자 ID: {userId}
                - 소속 부서: {userDept}
                
                외부 정보 확인이 필요하면 반드시 도구를 먼저 호출한다.
                추측하지 않는다.
                특정 시간에 작업을 예약해달라는 요청이 오면 스케줄러 도구를 사용한다.
                전자결재, 일정, 신청 관련 도구 호출 시 필요한 사용자 정보는 시스템에서 자동 전달된다.
                
                Context information is below.
                
                ---------------------
                {context}
                ---------------------
                
                """;

        return builder.defaultSystem(systemText)
            .defaultToolCallbacks(mcpTools)
            .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
            .build();
    }

    @Bean
    public RetrievalAugmentationAdvisor ragAdvisor(VectorStore pgVectorStore) {

        DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
            .vectorStore(pgVectorStore)
            .similarityThreshold(0.8d)
            .topK(3)
            .build();

        PromptTemplate template = PromptTemplate.builder()
            .renderer(StTemplateRenderer.builder().startDelimiterToken('<').endDelimiterToken('>').build())
            .template("""
                <query>
                
                Context information is below.
                
                ---------------------
                <context>
                ---------------------
                
                Given the context information and no prior knowledge, answer the query.
                
                Rules:
                1. If unknown, say you don't know.
                2. No phrases like "Based on context".
                """)
            .build();

        return RetrievalAugmentationAdvisor.builder()
            .documentRetriever(retriever)
            .queryAugmenter(ContextualQueryAugmenter.builder().promptTemplate(template).allowEmptyContext(true).build())
            .build();
    }
}