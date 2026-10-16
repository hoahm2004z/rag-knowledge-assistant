package rag_assistant.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "shipping_address")
public class ShippingAddress {
    @Id
    private String id;

    @DBRef(lazy = true)
    @JsonIgnore
    private User user;

    @JsonProperty("userId")
    public String getUserId() {
        return user != null ? user.getId() : null;
    }

    private String recipientName;
    private String numPhone;
    private String addressLine;
    private String district;
    private String city;
}
