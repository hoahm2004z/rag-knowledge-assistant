package rag_assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {
    /** Base64-encoded HMAC secret — must come from env, never commit real values. */
    private String secret = "";
    /** Token lifetime in minutes (origin uses 1440). */
    private long expirationMinutes = 1440;
}
