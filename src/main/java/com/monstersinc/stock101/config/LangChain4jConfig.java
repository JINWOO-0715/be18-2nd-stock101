package com.monstersinc.stock101.config;


import dev.langchain4j.model.chat.ChatLanguageModel;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.ollama.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.ollama.chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.ollama.chat-model.temperature}")
    private Double temperature;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Bean
    public ChatLanguageModel chatModel() {
        return GoogleAiGeminiChatModel.builder()
                .modelName("gemini-2.5-flash")
                .apiKey(apiKey)
                .temperature(0.1)
                .maxOutputTokens(8000)  // 긴 리포트 생성을 위해 증가
                .timeout(Duration.ofMinutes(10))
                .responseFormat(ResponseFormat.JSON)  // JSON 응답 형식 강제
                .build();
    }
}
