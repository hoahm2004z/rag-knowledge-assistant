package rag_assistant.rag.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import rag_assistant.rag.model.RagChunk;

import java.util.List;

public interface RagChunkRepository extends MongoRepository<RagChunk, String> {
    List<RagChunk> findByCategory(String category);

    List<RagChunk> findByDocumentPath(String documentPath);

    long countByDocumentPath(String documentPath);

    void deleteByDocumentPath(String documentPath);

    long countByEmbeddingExists(boolean exists);
}
