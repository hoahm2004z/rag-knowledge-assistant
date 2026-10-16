package rag_assistant.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rag_assistant.config.RagProperties;
import rag_assistant.dto.ApiResponse;
import rag_assistant.rag.repository.RagChunkRepository;
import rag_assistant.rag.service.RagIngestionService;
import rag_assistant.rag.service.RagSearchService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RAG status / ingest / admin diagnostics.
 * Debug search requires ROLE_ADMIN and app.rag.debug=true.
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagIngestionService ragIngestionService;
    private final RagChunkRepository ragChunkRepository;
    private final RagSearchService ragSearchService;
    private final RagProperties ragProperties;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("chunkCount", ragIngestionService.countChunks());
        data.put("returnPolicyChunks",
                ragChunkRepository.countByDocumentPath("rag-data/store/chinh-sach-doi-tra.md"));
        data.put("chunksWithEmbedding", ragChunkRepository.countByEmbeddingExists(true));
        return ResponseEntity.ok(ApiResponse.success("RAG status", data));
    }

    @PostMapping("/ingest")
    public ResponseEntity<ApiResponse<Map<String, Object>>> ingest() {
        try {
            Map<String, Object> result = ragIngestionService.ingestAllFromClasspath();
            return ResponseEntity.ok(ApiResponse.success("RAG ingest completed", result));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * Admin-only retrieval diagnosis. Disabled unless app.rag.debug=true.
     * Does not expose secrets or full chat history.
     */
    @GetMapping("/debug/search")
    public ResponseEntity<ApiResponse<Map<String, Object>>> debugSearch(@RequestParam String q) {
        if (!ragProperties.isDebug()) {
            return ResponseEntity.status(404).body(ApiResponse.error("RAG debug disabled"));
        }
        try {
            RagSearchService.SearchDiagnostics diag = ragSearchService.searchWithDiagnostics(q);
            return ResponseEntity.ok(ApiResponse.success("RAG debug search", diag.toDebugMap()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
