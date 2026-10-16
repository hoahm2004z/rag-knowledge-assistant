package rag_assistant.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import rag_assistant.model.DiscountEvent;

import java.util.List;

public interface DiscountEventRepository extends MongoRepository<DiscountEvent, String> {
    List<DiscountEvent> findByActiveTrue();
}
