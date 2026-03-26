package com.brycenkorea.template.config;

import com.brycenkorea.template.tools.PythonCrawlerTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ChatMemoryConfig {
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                                      .chatMemoryRepository(chatMemoryRepository)
                                      .maxMessages(20) // 최신 20개 메시지만 기억
                                      .build();
    }

    // 3. 기본 ChatClient (RAG Advisor 제외)
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, PythonCrawlerTools crawlerTools, ChatMemory chatMemory) {
        return builder.defaultSystem("""
                          너는 우리 회사의 친절하고 똑똑한 AI 비서야.
                          너는 사용자와의 이전 대화 내용을 모두 기억하고 있어.
                          사용자가 본인의 정보나 이전 대화에 대해 물어보면,
                          반드시 대화 기록(Chat Memory)을 확인해서 정확하게 답변해줘.
                          """)
                      .defaultTools(crawlerTools)
                      .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                      .build();
    }

    // 2. RAG용 Retriever (나중에 서비스에서 갖다 쓸 수 있게 빈으로 등록)
    @Bean
    public ChatClient documentRetriever(
        ChatClient.Builder builder,
        PythonCrawlerTools crawlerTools,
        ChatMemory chatMemory,
        VectorStore pgVectorStore
    )
    {

        // 1. Retriever 설정 (기존 SearchRequest 대체)
        DocumentRetriever documentRetriever = VectorStoreDocumentRetriever.builder()
                                                                          .vectorStore(pgVectorStore)
                                                                          .similarityThreshold(0.5d)
                                                                          .topK(3)
                                                                          .build();

        // 2. 프롬프트 템플릿 및 Augmenter 설정
        // 주의: ContextualQueryAugmenter는 기본적으로 'context'라는 변수명을 컨텍스트 주입에 사용합니다.
        PromptTemplate customPromptTemplate = PromptTemplate.builder()
                                                            .renderer(StTemplateRenderer.builder()
                                                                                        .startDelimiterToken('<')
                                                                                        .endDelimiterToken('>')
                                                                                        .build())
                                                            .template("""
                                                                <query>
                                                                
                                                                Context information is below.
                                                                
                                                                ---------------------
                                                                <context>
                                                                ---------------------
                                                                
                                                                Given the context information and no prior knowledge, answer the query.
                                                                
                                                                Follow these rules:
                                                                
                                                                1. If the answer is not in the context, just say that you don't know.
                                                                2. Avoid statements like "Based on the context..." or "The provided information...".
                                                                """)
                                                            .build();

        QueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                                                                // 지원하는 Spring AI 버전에 따라 프롬프트 템플릿 주입 방식이 다를 수 있습니다.
                                                                // 최신 버전에서는 아래와 같이 커스텀 프롬프트를 주입하거나, 기본값을 사용합니다.
                                                                .promptTemplate(customPromptTemplate)
                                                                .allowEmptyContext(true) // 컨텍스트가 없어도 AI가 답변을 시도하도록 허용할지 여부
                                                                .build();

        // 3. 최신 RAG Advisor 조립
        RetrievalAugmentationAdvisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
                                                                              .documentRetriever(documentRetriever)
                                                                              .queryAugmenter(queryAugmenter)
                                                                              // 추후 Advanced RAG가 필요하다면 여기에 .queryTransformers() 등을 쉽게 추가할 수 있습니다.
                                                                              .build();

        return builder.defaultSystem("너는 사내 지식 비서야. 아래 제공된 컨텍스트(Context)를 바탕으로 정직하게 답변해줘.")
                      .defaultTools(crawlerTools)
                      .defaultAdvisors(List.of(
                          MessageChatMemoryAdvisor.builder(chatMemory).build(),
                          ragAdvisor // 기존 QuestionAnswerAdvisor를 대체
                      ))
                      .build();
    }
}

