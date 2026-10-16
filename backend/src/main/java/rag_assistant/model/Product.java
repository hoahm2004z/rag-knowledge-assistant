package rag_assistant.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Catalog product — fields aligned with pharmacy_webapp.model.Product
 * (collection name matches Spring Data default used by the origin app).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "product")
public class Product {
    @Id
    private String id;

    @DBRef
    private Manufacturer manufacturer;

    @DBRef
    private Categories categories;

    @Indexed
    private String name;
    private String description;
    private List<String> urlImages;
    private int quantity;
    private double originalPrice;
    private double price;
    private int percentDiscount;
    private int purchaseCount;

    /** Optional link to RAG markdown under classpath:rag-data/products/ */
    private String ragDocumentKey;
}
