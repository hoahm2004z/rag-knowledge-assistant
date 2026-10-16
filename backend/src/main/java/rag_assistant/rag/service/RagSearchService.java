package rag_assistant.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rag_assistant.config.GeminiConfig;
import rag_assistant.config.RagProperties;
import rag_assistant.rag.model.RagChunk;
import rag_assistant.rag.repository.RagChunkRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cosine similarity retrieval in Java over Mongo {@code rag_chunks} (full scan).
 * Not Atlas {@code $vectorSearch}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagSearchService {

    private final RagChunkRepository ragChunkRepository;
    private final EmbeddingService embeddingService;
    private final GeminiConfig config;
    private final RagProperties ragProperties;

    public Map<String, Object> search(String question) throws Exception {
        return searchInternal(question, false).toToolResult();
    }

    /**
     * Diagnostic search used by tests / admin debug — includes scoreboard before topK cut.
     */
    public SearchDiagnostics searchWithDiagnostics(String question) throws Exception {
        return searchInternal(question, true);
    }

    private SearchDiagnostics searchInternal(String question, boolean includeScoreboard) throws Exception {
        String queryId = UUID.randomUUID().toString().substring(0, 8);
        SearchDiagnostics diag = new SearchDiagnostics(queryId, question);

        if (question == null || question.isBlank()) {
            diag.setOk(false);
            diag.setMessage("Cau hoi rong.");
            return diag;
        }

        long total = ragChunkRepository.count();
        diag.setCandidateCount(total);
        if (total == 0) {
            diag.setOk(false);
            diag.setMessage("Chua co tai lieu RAG. Hay chay ingest.");
            logRetrieval(diag, 0, 0, 0);
            return diag;
        }

        List<Double> queryVector = embeddingService.embed(question);
        diag.setQueryEmbeddingDim(queryVector.size());

        int topK = config.getRagTopK() > 0 ? config.getRagTopK() : 4;
        double threshold = ragProperties.getScoreThreshold() > 0 ? ragProperties.getScoreThreshold() : 0.25;

        List<ScoredChunk> scored = new ArrayList<>();
        int skippedNoEmbedding = 0;
        int skippedDimMismatch = 0;
        for (RagChunk chunk : ragChunkRepository.findAll()) {
            if (chunk.getEmbedding() == null || chunk.getEmbedding().isEmpty()) {
                skippedNoEmbedding++;
                continue;
            }
            if (chunk.getEmbedding().size() != queryVector.size()) {
                skippedDimMismatch++;
                continue;
            }
            double score = cosineSimilarity(queryVector, chunk.getEmbedding());
            scored.add(new ScoredChunk(chunk, score));
        }
        diag.setSkippedNoEmbedding(skippedNoEmbedding);
        diag.setSkippedDimMismatch(skippedDimMismatch);

        scored.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        if (includeScoreboard || ragProperties.isDebug()) {
            diag.setScoreboard(toScoreboard(scored, Math.min(12, scored.size())));
        }

        // Filter by threshold first, then take topK (avoids dropping relevant docs ranked just outside topK).
        List<ScoredChunk> aboveThreshold = new ArrayList<>();
        for (ScoredChunk item : scored) {
            if (item.score() >= threshold) {
                aboveThreshold.add(item);
            }
        }
        diag.setAboveThresholdCount(aboveThreshold.size());

        List<Map<String, Object>> chunks = new ArrayList<>();
        int limit = Math.min(topK, aboveThreshold.size());
        for (int i = 0; i < limit; i++) {
            ScoredChunk item = aboveThreshold.get(i);
            chunks.add(toChunkRow(item));
        }
        diag.setReturnedChunks(chunks);
        diag.setOk(true);
        if (chunks.isEmpty()) {
            diag.setMessage("Khong tim thay doan tai lieu lien quan.");
        }

        logRetrieval(diag, scored.size(), aboveThreshold.size(), chunks.size());
        return diag;
    }

    private void logRetrieval(SearchDiagnostics diag, int scoredCount, int aboveThreshold, int returned) {
        if (!ragProperties.isDebug()) {
            return;
        }
        String topSummary = "";
        if (diag.getScoreboard() != null && !diag.getScoreboard().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int n = Math.min(5, diag.getScoreboard().size());
            for (int i = 0; i < n; i++) {
                Map<String, Object> row = diag.getScoreboard().get(i);
                if (i > 0) {
                    sb.append("; ");
                }
                sb.append(row.get("score")).append('@').append(row.get("documentPath"));
            }
            topSummary = sb.toString();
        }
        log.info(
                "RAG_DEBUG queryId={} candidates={} scored={} aboveThreshold={} returned={} queryLen={} top=[{}]",
                diag.getQueryId(),
                diag.getCandidateCount(),
                scoredCount,
                aboveThreshold,
                returned,
                diag.getQuestion() == null ? 0 : Math.min(diag.getQuestion().length(), 120),
                topSummary
        );
    }

    private List<Map<String, Object>> toScoreboard(List<ScoredChunk> scored, int limit) {
        List<Map<String, Object>> board = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            ScoredChunk item = scored.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", i + 1);
            row.put("score", Math.round(item.score() * 1000.0) / 1000.0);
            row.put("documentPath", item.chunk().getDocumentPath());
            row.put("title", item.chunk().getTitle());
            row.put("chunkIndex", item.chunk().getChunkIndex());
            row.put("excerpt", excerpt(item.chunk().getContent(), 160));
            row.put("embeddingDim", item.chunk().getEmbedding() == null ? 0 : item.chunk().getEmbedding().size());
            board.add(row);
        }
        return board;
    }

    private static Map<String, Object> toChunkRow(ScoredChunk item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", item.chunk().getTitle());
        row.put("content", item.chunk().getContent());
        row.put("category", item.chunk().getCategory());
        row.put("source", item.chunk().getSource());
        row.put("sourceUrl", item.chunk().getSourceUrl());
        row.put("documentPath", item.chunk().getDocumentPath());
        row.put("chunkIndex", item.chunk().getChunkIndex());
        row.put("score", Math.round(item.score() * 1000.0) / 1000.0);
        row.put("excerpt", excerpt(item.chunk().getContent(), 220));
        return row;
    }

    private static String excerpt(String content, int max) {
        if (content == null) {
            return "";
        }
        String oneLine = content.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }

    public static double cosineSimilarity(List<Double> a, List<Double> b) {
        int n = Math.min(a.size(), b.size());
        if (n == 0 || a.size() != b.size()) {
            // Dim mismatch handled by caller; unequal sizes yield 0 for safety if called directly.
            if (a.size() != b.size()) {
                return 0;
            }
        }
        if (n == 0) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < n; i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ScoredChunk(RagChunk chunk, double score) {}

    public static final class SearchDiagnostics {
        private final String queryId;
        private final String question;
        private boolean ok;
        private String message;
        private long candidateCount;
        private int queryEmbeddingDim;
        private int skippedNoEmbedding;
        private int skippedDimMismatch;
        private int aboveThresholdCount;
        private List<Map<String, Object>> scoreboard = List.of();
        private List<Map<String, Object>> returnedChunks = List.of();

        public SearchDiagnostics(String queryId, String question) {
            this.queryId = queryId;
            this.question = question;
        }

        public Map<String, Object> toToolResult() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", ok);
            result.put("queryId", queryId);
            result.put("count", returnedChunks.size());
            result.put("chunks", returnedChunks);
            if (message != null) {
                result.put("message", message);
            }
            return result;
        }

        public Map<String, Object> toDebugMap() {
            Map<String, Object> result = toToolResult();
            result.put("candidateCount", candidateCount);
            result.put("queryEmbeddingDim", queryEmbeddingDim);
            result.put("skippedNoEmbedding", skippedNoEmbedding);
            result.put("skippedDimMismatch", skippedDimMismatch);
            result.put("aboveThresholdCount", aboveThresholdCount);
            result.put("scoreboard", scoreboard);
            return result;
        }

        public String getQueryId() { return queryId; }
        public String getQuestion() { return question; }
        public boolean isOk() { return ok; }
        public void setOk(boolean ok) { this.ok = ok; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getCandidateCount() { return candidateCount; }
        public void setCandidateCount(long candidateCount) { this.candidateCount = candidateCount; }
        public void setQueryEmbeddingDim(int queryEmbeddingDim) { this.queryEmbeddingDim = queryEmbeddingDim; }
        public void setSkippedNoEmbedding(int skippedNoEmbedding) { this.skippedNoEmbedding = skippedNoEmbedding; }
        public void setSkippedDimMismatch(int skippedDimMismatch) { this.skippedDimMismatch = skippedDimMismatch; }
        public int getAboveThresholdCount() { return aboveThresholdCount; }
        public void setAboveThresholdCount(int aboveThresholdCount) { this.aboveThresholdCount = aboveThresholdCount; }
        public List<Map<String, Object>> getScoreboard() { return scoreboard; }
        public void setScoreboard(List<Map<String, Object>> scoreboard) { this.scoreboard = scoreboard; }
        public List<Map<String, Object>> getReturnedChunks() { return returnedChunks; }
        public void setReturnedChunks(List<Map<String, Object>> returnedChunks) { this.returnedChunks = returnedChunks; }
    }
}
