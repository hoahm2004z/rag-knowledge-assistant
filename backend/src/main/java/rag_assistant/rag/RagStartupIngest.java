package rag_assistant.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import rag_assistant.config.RagProperties;
import rag_assistant.rag.service.RagIngestionService;

import java.util.Map;

/**
 * Tự nạp tài liệu RAG nếu collection rag_chunks đang trống.
 * Nếu đã có chunk thì bỏ qua — tránh ingest trùng khi restart.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class RagStartupIngest implements ApplicationRunner {

    private final RagIngestionService ragIngestionService;
    private final RagProperties ragProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (!ragProperties.isStartupIngest()) {
            log.info("RAG startup ingest disabled (app.rag.startup-ingest=false)");
            return;
        }
        try {
            long count = ragIngestionService.countChunks();
            if (count > 0) {
                log.info("RAG already loaded: {} chunks", count);
                return;
            }
            log.info("RAG empty — ingesting classpath rag-data...");
            Map<String, Object> result = ragIngestionService.ingestAllFromClasspath();
            log.info("RAG ingest done: {}", result);
        } catch (Exception e) {
            log.warn("RAG startup ingest skipped/failed: {}", e.getMessage());
        }
    }
}
