package rag_assistant.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Một đoạn tài liệu (chunk) đã được embedding để tìm kiếm RAG.
 * Không chứa secret / mật khẩu / API key.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rag_chunks")
public class RagChunk {
    @Id
    private String id;

    private String title;
    private String content;
    private String category;
    private String source;
    private String sourceUrl;
    private String productId;
    private String manufacturer;
    private String documentPath;
    private int chunkIndex;

    /** Vector embedding của content (Gemini embedContent). */
    private List<Double> embedding = new ArrayList<>();

    private LocalDateTime createdAt;
}
