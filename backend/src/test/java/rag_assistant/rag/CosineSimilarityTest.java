package rag_assistant.rag;

import org.junit.jupiter.api.Test;
import rag_assistant.rag.service.RagSearchService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosineSimilarityTest {

    @Test
    void identicalVectorsScoreOne() {
        List<Double> v = List.of(1.0, 0.0, 0.0);
        assertEquals(1.0, RagSearchService.cosineSimilarity(v, v), 1e-9);
    }

    @Test
    void orthogonalVectorsScoreZero() {
        List<Double> a = List.of(1.0, 0.0);
        List<Double> b = List.of(0.0, 1.0);
        assertEquals(0.0, RagSearchService.cosineSimilarity(a, b), 1e-9);
    }

    @Test
    void emptyOrMismatchedDimensionsScoreZero() {
        assertEquals(0.0, RagSearchService.cosineSimilarity(List.of(), List.of()), 1e-9);
        assertEquals(0.0, RagSearchService.cosineSimilarity(List.of(1.0), List.of(1.0, 2.0)), 1e-9);
    }

    @Test
    void similarVectorsScoreHigh() {
        List<Double> a = List.of(1.0, 1.0, 0.0);
        List<Double> b = List.of(0.9, 1.1, 0.0);
        assertTrue(RagSearchService.cosineSimilarity(a, b) > 0.9);
    }
}
