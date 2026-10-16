package rag_assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {
    /** Cosine similarity minimum (same as previous hardcoded 0.25). */
    private double scoreThreshold = 0.25;
    /** Structured retrieval logs for local diagnosis (no secrets / PII). */
    private boolean debug = false;
    private boolean startupIngest = true;
}
