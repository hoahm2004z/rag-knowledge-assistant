package rag_assistant.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rag_assistant.dto.ApiResponse;
import rag_assistant.dto.ChatbotReply;
import rag_assistant.model.UserPrincipal;
import rag_assistant.service.ChatbotService;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatbotReply>> chat(
            Authentication authentication,
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId,
            @RequestParam String message) {
        try {
            if (message == null || message.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Something went wrong"));
            }
            ChatbotReply reply = chatbotService.chat(sessionId, message, resolveUserId(authentication));
            return ResponseEntity.ok(ApiResponse.success("Chatbot successfully reply", reply));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(publicErrorMessage(e)));
        }
    }

    @DeleteMapping("/history")
    public ResponseEntity<ApiResponse<String>> clearHistory(
            @RequestHeader(value = "X-Session-Id", defaultValue = "default") String sessionId) {
        chatbotService.clearHistory(sessionId);
        return ResponseEntity.ok(ApiResponse.success("message", "Deleted history message"));
    }

    private String resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUserId();
        }
        return null;
    }

    private String publicErrorMessage(Exception e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        if (raw.contains("429") || raw.contains("RESOURCE_EXHAUSTED") || raw.contains("quota")) {
            return "Hệ thống đang bận, vui lòng thử lại sau vài giây.";
        }
        if (raw.contains("Gemini API Error") || raw.contains("Embedding API Error")) {
            return "Không kết nối được trợ lý lúc này. Vui lòng thử lại.";
        }
        return "Đã có lỗi xảy ra. Vui lòng thử lại.";
    }
}
