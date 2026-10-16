package rag_assistant.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.HashSet;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "discount_events")
public class DiscountEvent {
    @Id
    private String id;
    private String name;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean active;
    private Integer discountPercent;
    private HashSet<String> productIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
