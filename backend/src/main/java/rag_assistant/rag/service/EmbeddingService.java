package rag_assistant.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import rag_assistant.config.GeminiConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Tạo embedding cho văn bản bằng Gemini Embedding API (OkHttp REST).
 */
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final GeminiConfig config;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public List<Double> embed(String text) throws Exception {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text for embedding is blank");
        }

        String model = config.getEmbeddingModel();
        if (model == null || model.isBlank()) {
            model = "gemini-embedding-001";
        }

        String url = config.getApiUrl() + model + ":embedContent";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "models/" + model);
        body.put("content", Map.of(
                "parts", List.of(Map.of("text", text))
        ));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(mapper.writeValueAsString(body), MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json")
                .addHeader("x-goog-api-key", config.getApiKey())
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("Embedding API Error: " + response.code() + " - " + responseBody);
            }
            JsonNode values = mapper.readTree(responseBody).path("embedding").path("values");
            if (!values.isArray() || values.isEmpty()) {
                throw new RuntimeException("Embedding API returned empty vector");
            }
            List<Double> vector = new ArrayList<>(values.size());
            for (JsonNode value : values) {
                vector.add(value.asDouble());
            }
            return vector;
        }
    }
}
