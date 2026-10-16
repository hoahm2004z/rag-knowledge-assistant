package rag_assistant.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "manufacturer")
public class Manufacturer {
    @Id
    private String id;
    private String name;
    private String urlImage;
    private String description;
}
