package rag_assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Public store info for getStoreInfo — fictional standalone config, not real contacts. */
@Data
@Component
@ConfigurationProperties(prefix = "app.store")
public class StoreProperties {
    private String name = "AI Pharmacy Assistant Store";
    private String address = "123 Assistant Lane, Demo City (local seed — not a real pharmacy)";
    private String hotline = "1800 000 000";
    private String email = "support@aipharmacy.example";
    private String chatChannel = "Local demo support channel";
}
