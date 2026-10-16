package rag_assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatbotReply {
    private String text;
    /** Tool names actually executed in this turn (empty if SELF-only). */
    private List<String> toolsUsed = new ArrayList<>();
    /** Grounded sources from tool payloads — never fabricated. */
    private List<ChatSource> sources = new ArrayList<>();

    public ChatbotReply(String text) {
        this.text = text;
        this.toolsUsed = new ArrayList<>();
        this.sources = new ArrayList<>();
    }
}
