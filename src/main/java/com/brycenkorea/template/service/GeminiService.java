package com.brycenkorea.template.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeminiService {
    private final Client client;
    @Value("${app.gemini.model}")
    private String model;

    public String ask(String prompt) {

        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be empty");
        }

        if (prompt.length() > 1000) {
            throw new IllegalArgumentException("Prompt too long");
        }

        GenerateContentResponse response =
            client.models.generateContent(
                model,
                prompt,
                null);

        return response.text();
    }
}