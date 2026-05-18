package com.brycenkorea.template.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class RagToolConfig {

    @Bean
    @Description("사내 규정 및 지식 베이스에서 관련 정보를 검색합니다.")
    public Function<SearchRequestPayload, String> searchCompanyInfo(VectorStore vectorStore) {
        return request -> {
            // 💡 에러 해결: SearchRequest.query() 메서드를 사용하고 필터링 조건을 설정합니다.
            var searchRequest = SearchRequest.builder().query(request.query()).topK(3).similarityThreshold(0.5).build();

            return vectorStore.similaritySearch(searchRequest)
                .stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));
        };
    }

    public record SearchRequestPayload(String query) {}
}