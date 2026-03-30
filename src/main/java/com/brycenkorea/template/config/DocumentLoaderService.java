package com.brycenkorea.template.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DocumentLoaderService {

    private final VectorStore vectorStore;

    public DocumentLoaderService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void loadDocument(Resource resource) {
        // 1. 문서 읽기 (Tika가 PDF, DOCX를 모두 알아서 판단해서 텍스트로 추출합니다)
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();

        // 2. 메타데이터 구성 (사용자 정의)
        Map<String, Object> customMetadata = new HashMap<>();
        customMetadata.put("source", resource.getFilename());
        customMetadata.put("file_path", resource.getDescription());
        customMetadata.put("ingested_at", System.currentTimeMillis());
        customMetadata.put("content_type", getExtension(resource.getFilename()));

        // 2. 상세 생성자를 이용한 다국어 최적화 스플리터 설정
        TokenTextSplitter splitter = new TokenTextSplitter(
            800,        // chunkSize: 약 800 토큰 단위로 분할
            100,        // minChunkSizeChars: 최소 100자 이상일 때만 유지
            5,          // minChunkLengthToEmbed: 임베딩하기 위한 최소 길이 (너무 짧으면 무시)
            10000,      // maxNumChunks: 최대 생성 가능 청크 수
            true,       // keepSeparator: 구분자 유지 여부
            // punctuationMarks: 문장을 끊을 기준이 되는 부호들 (일본어 마침표 포함)
            Arrays.asList('.', '!', '?', '\n', '。', '！', '？')
        );
        // 3. 텍스트 분할 실행
        List<Document> splitDocs = documents.stream()
                                            .flatMap(doc -> splitter.apply(List.of(doc)).stream())
                                            .map(doc -> {
                                                // 💡 기존 메타데이터에 커스텀 메타데이터 병합
                                                doc.getMetadata().putAll(customMetadata);
                                                return doc;
                                            })
                                            .collect(Collectors.toList());

        // 4. 적재 (메타데이터와 함께 PGVector로 전송)
        vectorStore.add(splitDocs);;

        System.out.println("문서 적재 완료: " + resource.getFilename());
    }

    private String getExtension(String fileName) {
        return fileName != null && fileName.contains(".")
            ? fileName.substring(fileName.lastIndexOf(".") + 1) : "unknown";
    }
}