package rag_assistant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fail-fast if Mongo URI does not target the dedicated local assistant database.
 */
@Component
@Order(0)
@Slf4j
public class MongoDatabaseGuard implements ApplicationRunner {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Override
    public void run(ApplicationArguments args) {
        if (mongoUri == null || mongoUri.isBlank()) {
            throw new IllegalStateException("spring.data.mongodb.uri is empty");
        }
        String lower = mongoUri.toLowerCase();
        if (lower.contains("mongodb+srv://") && !lower.contains("localhost") && !lower.contains("127.0.0.1")) {
            throw new IllegalStateException(
                    "Refusing to start: Mongo URI looks like a remote Atlas/SRV endpoint. "
                            + "Use local mongodb://localhost:27018/ai_pharmacy_assistant for this project.");
        }
        String dbName = extractDbName(mongoUri);
        if (dbName == null || !(dbName.equals("ai_pharmacy_assistant")
                || dbName.startsWith("ai_pharmacy_assistant_"))) {
            throw new IllegalStateException(
                    "Refusing to start: database name must be ai_pharmacy_assistant (or ai_pharmacy_assistant_* for tests). Found: "
                            + dbName);
        }
        log.info("MongoDatabaseGuard OK — dedicated DB '{}'", dbName);
    }

    static String extractDbName(String uri) {
        // mongodb://host:port/dbname?...
        int scheme = uri.indexOf("://");
        if (scheme < 0) {
            return null;
        }
        int pathStart = uri.indexOf('/', scheme + 3);
        if (pathStart < 0 || pathStart + 1 >= uri.length()) {
            return null;
        }
        String path = uri.substring(pathStart + 1);
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        path = path.replace("/", "");
        return path.isBlank() ? null : path;
    }
}
