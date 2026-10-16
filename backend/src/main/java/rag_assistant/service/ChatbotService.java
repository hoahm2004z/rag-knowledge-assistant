package rag_assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
import rag_assistant.dto.ChatSource;
import rag_assistant.dto.ChatbotReply;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final int MAX_HISTORY_TURNS = 100;
    private static final int MAX_TOOL_ROUNDS = 4;

    private static final String TOOL_RULES = """
            Ban la AI Pharmacy Assistant — ung dung demo/portfolio, chi DOC thong tin.
            Khong tu xung la nha thuoc that, khong noi dang ket noi he thong ban hang production.
            Tra loi bang tieng Viet, ngan gon, khong dung icon.

            PHAN LOAI CAU HOI (chon dung huong):
            1) SELF: kien thuc chung, chao hoi, huong dan dung tro ly -> tu tra loi, KHONG goi tool.
            2) DB: gia, ton kho, san pham cu the, giam gia, don hang, gio hang, ho so, dia chi -> BAT BUOC goi tool DB. Khong bia.
            3) RAG: chinh sach giao hang/doi tra/thanh toan, FAQ cua hang, huong dan mua hang -> goi searchKnowledge.
            4) DB + RAG: vua can san pham/don hang vua can chinh sach -> goi ca tool DB va searchKnowledge, roi tong hop.
            Co the goi nhieu tool trong mot luot neu can.

            Hieu cau viet tat hoac thieu dau.
            Khi dung ket qua tool:
            - sourceType=DB: day la du lieu nghiep vu hien tai (gia/ton/don). Chi dung field trong data.
            - sourceType=RAG: day la tai lieu tinh. Chi dung noi dung chunk; neu khong co thi noi khong tim thay trong tai lieu.
            Neu tool tra ok=false (NOT_FOUND, LOGIN_REQUIRED, FORBIDDEN, DB_ERROR, RAG_EMPTY, RAG_ERROR):
            bao dung loi do, KHONG noi rang da thanh cong hay bia du lieu.
            Neu tool tra LOGIN_REQUIRED: bao khach can dang nhap, khong goi lai tool private.
            Khong ke don, khong chuan doan nhu bac si. Cau y te nhay cam: khuyen gap duoc si hoac bac si.
            Khong tiet lo password, JWT, API key, secret, du lieu admin, du lieu user khac.
            Khong tao/huy/sua don, khong them/xoa gio hang, khong doi gia hay ton kho.
            Khong tu sinh URL dieu huong (chua co giao dien dieu huong).
            Neu khong co du lieu: noi khong tim thay.
            """;

    private final GeminiConfig config;
    private final ChatbotToolService chatbotToolService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private final Map<String, List<Map<String, Object>>> sessionHistories = new ConcurrentHashMap<>();

    public ChatbotReply chat(String sessionId, String message, String currentUserId) throws Exception {
        List<Map<String, Object>> history = sessionHistories.computeIfAbsent(sessionId, k -> new ArrayList<>());

        Map<String, Object> userTurn = new LinkedHashMap<>();
        userTurn.put("role", "user");
        userTurn.put("parts", List.of(Map.of("text", message)));
        history.add(userTurn);
        trimHistory(history);

        String reply = "";
        List<String> toolsUsed = new ArrayList<>();
        List<ChatSource> sources = new ArrayList<>();
        Set<String> sourceKeys = new LinkedHashSet<>();

        try {
            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                JsonNode json = callGemini(history, currentUserId);
                JsonNode parts = json.path("candidates").path(0).path("content").path("parts");
                if (!parts.isArray() || parts.isEmpty()) {
                    break;
                }

                List<Map<String, Object>> functionCalls = new ArrayList<>();
                for (JsonNode part : parts) {
                    if (part.has("functionCall") && !part.get("functionCall").isNull()) {
                        functionCalls.add(mapper.convertValue(part.get("functionCall"), new TypeReference<>() {}));
                    }
                }

                if (!functionCalls.isEmpty()) {
                    Map<String, Object> modelTurn = new LinkedHashMap<>();
                    modelTurn.put("role", "model");
                    modelTurn.put("parts", mapper.convertValue(parts, new TypeReference<List<Map<String, Object>>>() {}));
                    history.add(modelTurn);

                    List<Map<String, Object>> functionResponseParts = new ArrayList<>();
                    for (Map<String, Object> call : functionCalls) {
                        String name = String.valueOf(call.getOrDefault("name", ""));
                        Map<String, Object> args = toArgMap(call.get("args"));
                        ChatbotToolService.ToolExecution execution =
                                chatbotToolService.execute(name, args, currentUserId);
                        toolsUsed.add(name);
                        collectSources(name, execution.getPayload(), sources, sourceKeys);
                        Map<String, Object> functionResponse = new LinkedHashMap<>();
                        functionResponse.put("name", name);
                        functionResponse.put("response", execution.getPayload());
                        functionResponseParts.add(Map.of("functionResponse", functionResponse));
                    }

                    Map<String, Object> toolTurn = new LinkedHashMap<>();
                    toolTurn.put("role", "user");
                    toolTurn.put("parts", functionResponseParts);
                    history.add(toolTurn);
                    continue;
                }

                reply = extractReplyText(json);
                if (!reply.isBlank()) {
                    Map<String, Object> modelTurn = new LinkedHashMap<>();
                    modelTurn.put("role", "model");
                    modelTurn.put("parts", List.of(Map.of("text", reply)));
                    history.add(modelTurn);
                }
                break;
            }
        } catch (Exception e) {
            if (!history.isEmpty()) {
                Map<String, Object> last = history.get(history.size() - 1);
                if ("user".equals(last.get("role")) && last.get("parts") instanceof List<?> parts
                        && parts.size() == 1) {
                    history.remove(history.size() - 1);
                }
            }
            throw e;
        }

        if (reply.isBlank()) {
            reply = "Tôi chưa tìm được thông tin phù hợp.";
            Map<String, Object> modelTurn = new LinkedHashMap<>();
            modelTurn.put("role", "model");
            modelTurn.put("parts", List.of(Map.of("text", reply)));
            history.add(modelTurn);
        }

        return new ChatbotReply(reply, toolsUsed, sources);
    }

    @SuppressWarnings("unchecked")
    private void collectSources(
            String toolName,
            Map<String, Object> payload,
            List<ChatSource> sources,
            Set<String> sourceKeys) {
        if (payload == null) {
            return;
        }
        String sourceType = String.valueOf(payload.getOrDefault("sourceType", ""));
        if ("RAG".equals(sourceType)) {
            Object chunksObj = payload.get("chunks");
            if (!(chunksObj instanceof List<?>) && payload.get("data") instanceof Map<?, ?> dataMap) {
                chunksObj = dataMap.get("chunks");
            }
            if (!(chunksObj instanceof List<?> chunks)) {
                return;
            }
            for (Object item : chunks) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> chunk = (Map<String, Object>) raw;
                String documentPath = stringOrNull(chunk.get("documentPath"));
                String title = stringOrNull(chunk.get("title"));
                String excerpt = stringOrNull(chunk.get("excerpt"));
                if (excerpt == null) {
                    excerpt = excerptOf(stringOrNull(chunk.get("content")), 180);
                }
                String key = "RAG|" + (documentPath == null ? "" : documentPath) + "|"
                        + (title == null ? "" : title) + "|" + (excerpt == null ? "" : excerpt);
                if (!sourceKeys.add(key)) {
                    continue;
                }
                sources.add(new ChatSource("RAG", toolName, documentPath, title, excerpt));
            }
            return;
        }
        if ("DB".equals(sourceType) && Boolean.TRUE.equals(payload.get("ok"))) {
            String key = "DB|" + toolName;
            if (sourceKeys.add(key)) {
                sources.add(new ChatSource("DB", toolName, null, null, null));
            }
        }
    }

    private static String stringOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equals(text) ? null : text;
    }

    private static String excerptOf(String content, int max) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String oneLine = content.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "...";
    }

    public void clearHistory(String sessionId) {
        sessionHistories.remove(sessionId);
    }

    private JsonNode callGemini(List<Map<String, Object>> history, String currentUserId) throws Exception {
        String url = config.getApiUrl() + config.getModel() + ":generateContent";

        Map<String, Object> bodyMap = new LinkedHashMap<>();
        bodyMap.put("system_instruction", Map.of(
                "parts", List.of(Map.of("text", buildSystemPrompt(currentUserId)))
        ));
        bodyMap.put("contents", history);
        bodyMap.put("tools", List.of(Map.of(
                "functionDeclarations", chatbotToolService.functionDeclarations()
        )));
        bodyMap.put("generationConfig", Map.of(
                "maxOutputTokens", config.getMaxTokens(),
                "temperature", config.getTemperature()
        ));

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(mapper.writeValueAsString(bodyMap), MediaType.parse("application/json")))
                .addHeader("Content-Type", "application/json")
                .addHeader("x-goog-api-key", config.getApiKey())
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("Gemini API Error: " + response.code() + " - " + responseBody);
            }
            return mapper.readTree(responseBody);
        }
    }

    private String buildSystemPrompt(String currentUserId) {
        String loginState = (currentUserId == null || currentUserId.isBlank())
                ? "Nguoi dung hien tai: CHUA DANG NHAP."
                : "Nguoi dung hien tai: DA DANG NHAP. Cac tool getMy* chi tra du lieu cua chinh user nay.";
        String base = config.getSystemPrompt() == null ? "" : config.getSystemPrompt().trim();
        return base + "\n\n" + TOOL_RULES + "\n" + loginState;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toArgMap(Object args) {
        if (args instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new HashMap<>();
    }

    private String extractReplyText(JsonNode json) {
        JsonNode parts = json.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return "";
        }
        StringBuilder reply = new StringBuilder();
        for (JsonNode part : parts) {
            String text = part.path("text").asText("");
            if (!text.isBlank()) {
                if (reply.length() > 0) {
                    reply.append("\n");
                }
                reply.append(text);
            }
        }
        return reply.toString().trim();
    }

    private void trimHistory(List<Map<String, Object>> history) {
        int maxMessages = MAX_HISTORY_TURNS * 2;
        while (history.size() > maxMessages) {
            history.remove(0);
        }
    }
}
