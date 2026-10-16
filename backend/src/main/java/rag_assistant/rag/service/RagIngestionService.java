package rag_assistant.rag.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import rag_assistant.rag.model.RagChunk;
import rag_assistant.rag.repository.RagChunkRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Đọc file Markdown trong classpath:rag-data/**, chia chunk, tạo embedding, lưu MongoDB.
 * Trước khi lưu mỗi documentPath: deleteByDocumentPath → tránh trùng khi ingest lại.
 */
@Service
@RequiredArgsConstructor
public class RagIngestionService {

    private static final int TARGET_CHUNK_SIZE = 700;
    private static final int OVERLAP = 80;

    private final RagChunkRepository ragChunkRepository;
    private final EmbeddingService embeddingService;

    public Map<String, Object> ingestAllFromClasspath() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:rag-data/**/*.md");

        int documents = 0;
        int chunksSaved = 0;
        List<String> files = new ArrayList<>();

        for (Resource resource : resources) {
            if (!resource.exists() || !resource.isReadable()) {
                continue;
            }
            String filename = resource.getFilename();
            if (filename == null) {
                continue;
            }
            String path = resolveLogicalPath(resource);
            String markdown = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int saved = ingestDocument(path, filename, markdown);
            documents++;
            chunksSaved += saved;
            files.add(path + " (" + saved + " chunks)");
            // Tránh vượt quota embedding free tier khi ingest nhiều file liên tiếp
            Thread.sleep(400);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documents", documents);
        result.put("chunks", chunksSaved);
        result.put("files", files);
        return result;
    }

    public int ingestDocument(String documentPath, String filename, String markdown) throws Exception {
        ragChunkRepository.deleteByDocumentPath(documentPath);

        Meta meta = parseMeta(markdown, filename);
        List<String> chunks = chunkText(stripFrontMatter(markdown));
        int index = 0;
        for (String chunkText : chunks) {
            if (chunkText.isBlank()) {
                continue;
            }
            RagChunk chunk = new RagChunk();
            chunk.setTitle(meta.title);
            chunk.setContent(chunkText.trim());
            chunk.setCategory(meta.category);
            chunk.setSource(meta.source);
            chunk.setSourceUrl(meta.sourceUrl);
            chunk.setProductId(meta.productId);
            chunk.setManufacturer(meta.manufacturer);
            chunk.setDocumentPath(documentPath);
            chunk.setChunkIndex(index++);
            chunk.setEmbedding(embeddingService.embed(meta.title + "\n" + chunkText));
            chunk.setCreatedAt(LocalDateTime.now());
            ragChunkRepository.save(chunk);
            Thread.sleep(250);
        }
        return index;
    }

    public long countChunks() {
        return ragChunkRepository.count();
    }

    private String resolveLogicalPath(Resource resource) throws IOException {
        String url = resource.getURL().toString().replace('\\', '/');
        int idx = url.indexOf("rag-data/");
        if (idx >= 0) {
            return url.substring(idx);
        }
        return resource.getFilename();
    }

    private Meta parseMeta(String markdown, String filename) {
        Meta meta = new Meta();
        meta.title = filename.replace(".md", "").replace('-', ' ');
        meta.category = "general";
        meta.source = "Knowledge Base";
        meta.sourceUrl = null;
        meta.productId = null;
        meta.manufacturer = null;

        if (markdown.startsWith("---")) {
            int end = markdown.indexOf("---", 3);
            if (end > 0) {
                String front = markdown.substring(3, end).trim();
                for (String line : front.split("\\R")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length < 2) {
                        continue;
                    }
                    String key = parts[0].trim().toLowerCase(Locale.ROOT);
                    String value = parts[1].trim().replaceAll("^\"|\"$", "");
                    switch (key) {
                        case "title" -> meta.title = value;
                        case "category" -> meta.category = value;
                        case "source" -> meta.source = value;
                        case "sourceurl", "source_url" -> meta.sourceUrl = value;
                        case "productid", "product_id" -> meta.productId = value;
                        case "manufacturer" -> meta.manufacturer = value;
                        default -> {
                        }
                    }
                }
            }
        }
        return meta;
    }

    private String stripFrontMatter(String markdown) {
        if (!markdown.startsWith("---")) {
            return markdown;
        }
        int end = markdown.indexOf("---", 3);
        if (end < 0) {
            return markdown;
        }
        return markdown.substring(end + 3).trim();
    }

    private List<String> chunkText(String text) {
        List<String> sections = new ArrayList<>();
        String[] byHeading = text.split("(?m)(?=^##\\s+)");
        for (String section : byHeading) {
            String cleaned = section.trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            if (cleaned.length() <= TARGET_CHUNK_SIZE) {
                sections.add(cleaned);
                continue;
            }
            int start = 0;
            while (start < cleaned.length()) {
                int end = Math.min(start + TARGET_CHUNK_SIZE, cleaned.length());
                if (end < cleaned.length()) {
                    int breakAt = cleaned.lastIndexOf(' ', end);
                    if (breakAt > start + 200) {
                        end = breakAt;
                    }
                }
                sections.add(cleaned.substring(start, end).trim());
                if (end >= cleaned.length()) {
                    break;
                }
                start = Math.max(end - OVERLAP, start + 1);
            }
        }
        return sections;
    }

    private static class Meta {
        private String title;
        private String category;
        private String source;
        private String sourceUrl;
        private String productId;
        private String manufacturer;
    }
}
