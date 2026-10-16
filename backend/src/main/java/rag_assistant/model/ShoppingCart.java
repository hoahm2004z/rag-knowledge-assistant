package rag_assistant.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "shopping_cart")
public class ShoppingCart {
    @Id
    private String id;

    @DBRef(lazy = true)
    @JsonIgnore
    private User user;

    @JsonProperty("userId")
    public String getUserId() {
        return user != null ? user.getId() : null;
    }

    private HashMap<String, Integer> items = new HashMap<>();
}
