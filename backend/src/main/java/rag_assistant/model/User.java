package rag_assistant.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.HashSet;

/**
 * Synthetic local user for assistant demos. Not production customer data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user")
public class User {
    @Id
    private String id;
    @Indexed(unique = true)
    private String username;
    private String password;
    private String fullName;
    private String birthDate;
    private String gender;
    private String email;
    private String phoneNumber;
    private Boolean isActive = true;
    private HashSet<String> roles = new HashSet<>();
}
