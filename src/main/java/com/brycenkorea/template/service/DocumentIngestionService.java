package com.brycenkorea.template.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore pgVectorStore;

    // 문서 파일(Resource)을 받아 DB에 저장하는 메서드
    public void ingest(Resource resource) {
        log.info("문서 파싱 시작: {}", resource.getFilename());

        // 1. Extract (읽기): Tika가 PDF, DOCX 내용과 메타데이터를 싹 다 추출합니다.
        TikaDocumentReader documentReader = new TikaDocumentReader(resource);
        List<Document> rawDocuments = documentReader.get();

        // 2. Transform (자르기): AI가 소화하기 좋게 토큰 단위로 청크(Chunk)를 나눕니다.
        TokenTextSplitter textSplitter = new TokenTextSplitter();
        List<Document> chunkedDocuments = textSplitter.apply(rawDocuments);

        // 3. Load (저장): 임베딩 모델을 거쳐 PGVector에 저장됩니다.
        pgVectorStore.add(chunkedDocuments);

        log.info("문서 저장 완료: 총 {} 개의 청크가 저장되었습니다.", chunkedDocuments.size());
    }
}