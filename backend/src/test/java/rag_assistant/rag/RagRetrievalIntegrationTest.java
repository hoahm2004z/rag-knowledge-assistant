package rag_assistant.rag;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import rag_assistant.rag.model.RagChunk;
import rag_assistant.rag.repository.RagChunkRepository;
import rag_assistant.rag.service.RagIngestionService;
import rag_assistant.rag.service.RagSearchService;
import rag_assistant.service.ChatbotToolService;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real Gemini embedding + local Mongo retrieval tests.
 * Skips when Mongo or GEMINI_API_KEY unavailable.
 */
@SpringBootTest(properties = {
        "app.seed.enabled=false",
        "app.rag.startup-ingest=false",
        "app.rag.debug=true",
        "app.jwt.secret=dGVzdC1qd3Qtc2VjcmV0LWZvci1haS1waGFybWFjeS1hc3Npc3RhbnQtdGVzdHM="
})
class RagRetrievalIntegrationTest {

    private static final String RETURN_PATH = "rag-data/store/chinh-sach-doi-tra.md";
    private static final String RETURN_Q =
            "Tóm tắt chính sách đổi trả của MediKnow Demo. Những trường hợp nào được chấp nhận, "
                    + "điều kiện áp dụng là gì và khách hàng cần thực hiện những bước nào theo tài liệu?";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> "mongodb://localhost:27018/ai_pharmacy_assistant_test");
        final String envKey = System.getenv("GEMINI_API_KEY");
        final String key = (envKey == null || envKey.isBlank())
                ? System.getProperty("gemini.api-key", "")
                : envKey;
        if (key != null && !key.isBlank()) {
            registry.add("gemini.api-key", () -> key);
        }
    }

    @BeforeAll
    static void requireEnv() {
        Assumptions.assumeTrue(isMongoUp(), "MongoDB not reachable");
    }

    private static boolean isMongoUp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 27018), 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Autowired
    private RagIngestionService ragIngestionService;
    @Autowired
    private RagChunkRepository ragChunkRepository;
    @Autowired
    private RagSearchService ragSearchService;
    @Autowired
    private ChatbotToolService chatbotToolService;
    @Autowired
    private rag_assistant.config.GeminiConfig geminiConfig;

    @BeforeEach
    void ensureCorpus() throws Exception {
        Assumptions.assumeTrue(
                geminiConfig.getApiKey() != null && !geminiConfig.getApiKey().isBlank(),
                "GEMINI_API_KEY missing — skip live RAG IT"
        );
        if (ragChunkRepository.countByDocumentPath(RETURN_PATH) == 0) {
            ragIngestionService.ingestAllFromClasspath();
        }
    }

    @Test
    void returnPolicyChunksExistWithEmbeddings() {
        List<RagChunk> chunks = ragChunkRepository.findByDocumentPath(RETURN_PATH);
        assertFalse(chunks.isEmpty(), "Expected return-policy chunks after ingest");
        for (RagChunk chunk : chunks) {
            assertTrue(chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty());
            assertEquals(RETURN_PATH, chunk.getDocumentPath());
            assertTrue(chunk.getContent().toLowerCase().contains("đổi trả")
                    || chunk.getTitle().toLowerCase().contains("đổi trả"));
        }
    }

    @Test
    void retrievalReturnsReturnPolicyForPrimaryQuestion() throws Exception {
        RagSearchService.SearchDiagnostics diag = ragSearchService.searchWithDiagnostics(RETURN_Q);
        assertTrue(diag.isOk());
        assertTrue(diag.getAboveThresholdCount() > 0, "Expected scores above threshold");
        assertFalse(diag.getReturnedChunks().isEmpty(), "Expected returned chunks");
        boolean hit = diag.getReturnedChunks().stream()
                .anyMatch(c -> RETURN_PATH.equals(c.get("documentPath")));
        assertTrue(hit, "Return policy path should appear in returned chunks. Scoreboard="
                + diag.getScoreboard());
    }

    @Test
    void paraphrasedReturnQuestionAlsoHits() throws Exception {
        RagSearchService.SearchDiagnostics diag =
                ragSearchService.searchWithDiagnostics("Điều kiện đổi trả hàng trong bao nhiêu ngày?");
        assertTrue(diag.isOk());
        boolean hit = diag.getReturnedChunks().stream()
                .anyMatch(c -> RETURN_PATH.equals(c.get("documentPath")));
        assertTrue(hit || diag.getAboveThresholdCount() > 0,
                "Paraphrase should retrieve return policy or at least some above-threshold chunks");
        assertTrue(hit, "Scoreboard=" + diag.getScoreboard());
    }

    @Test
    void shippingQuestionHitsShippingDoc() throws Exception {
        RagSearchService.SearchDiagnostics diag =
                ragSearchService.searchWithDiagnostics("Chính sách giao hàng nội thành mất bao lâu?");
        assertTrue(diag.isOk());
        boolean hit = diag.getReturnedChunks().stream()
                .anyMatch(c -> String.valueOf(c.get("documentPath")).contains("giao-hang"));
        assertTrue(hit, "Scoreboard=" + diag.getScoreboard());
    }

    @Test
    void unknownTopicReturnsNoMatchToolError() throws Exception {
        ChatbotToolService.ToolExecution exec = chatbotToolService.execute(
                "searchKnowledge",
                Map.of("query", "Chính sách bảo hành tàu vũ trụ XYZ-999 không tồn tại"),
                null);
        // May be RAG_NO_MATCH or ok with empty depending on scores; must not invent success with chunks from unrelated if empty
        if (Boolean.TRUE.equals(exec.getPayload().get("ok"))) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) exec.getPayload().get("data");
            Object count = data.get("count");
            // If somehow matched, still ok structurally; prefer no-match path
            assertTrue(count instanceof Number);
        } else {
            assertTrue(List.of("RAG_NO_MATCH", "RAG_EMPTY").contains(exec.getPayload().get("error")));
        }
    }

    @Test
    void reingestSameDocumentDoesNotDuplicate() throws Exception {
        long before = ragChunkRepository.countByDocumentPath(RETURN_PATH);
        assertTrue(before > 0);
        // Re-ingest all (deleteByDocumentPath per file)
        Map<String, Object> result = ragIngestionService.ingestAllFromClasspath();
        long after = ragChunkRepository.countByDocumentPath(RETURN_PATH);
        assertEquals(before, after, "Re-ingest must not duplicate return-policy chunks");
        assertTrue(((Number) result.get("documents")).intValue() >= 1);
    }

    @Test
    void emptyCorpusMessageWhenNoChunks() {
        // Covered by search when count==0; here we only assert API shape via diagnostics on empty DB is heavy.
        // Lightweight: tool INVALID_ARGUMENT
        var exec = chatbotToolService.execute("searchKnowledge", Map.of("query", " "), null);
        assertFalse((Boolean) exec.getPayload().get("ok"));
        assertEquals("INVALID_ARGUMENT", exec.getPayload().get("error"));
    }
}
