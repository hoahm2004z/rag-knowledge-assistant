package rag_assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "gemini")
@Data
public class GeminiConfig {
    private String apiKey;
    private String model;
    private String apiUrl;
    private int maxTokens;
    private double temperature;
    private String systemPrompt;
    /** Model embedding cho RAG (vd: gemini-embedding-001). */
    private String embeddingModel = "gemini-embedding-001";
    /** Số chunk RAG trả về mỗi lần search. */
    private int ragTopK = 4;
}
