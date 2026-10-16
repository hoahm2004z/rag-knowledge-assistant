package rag_assistant.seed;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.seed")
public class SeedProperties {
    /** When true, seed business collections on startup if empty. */
    private boolean enabled = true;
    /** When true, wipe business collections then re-seed (never touches rag_chunks). */
    private boolean force = false;
}
