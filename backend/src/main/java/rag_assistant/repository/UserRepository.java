package rag_assistant.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import rag_assistant.model.User;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);
}
