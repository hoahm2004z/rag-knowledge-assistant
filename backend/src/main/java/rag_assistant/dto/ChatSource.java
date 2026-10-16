package rag_assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatSource {
    /** DB or RAG — taken from tool payload only. */
    private String sourceType;
    private String toolName;
    /** RAG document path from retrieval (e.g. store/chinh-sach-doi-tra.md). */
    private String documentPath;
    private String title;
    /** Short excerpt from retrieved chunk content; never invented on the client. */
    private String excerpt;
}
