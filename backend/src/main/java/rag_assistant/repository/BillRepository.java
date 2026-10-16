package rag_assistant.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import rag_assistant.model.Bill;

import java.util.List;

public interface BillRepository extends MongoRepository<Bill, String> {
    List<Bill> findByUser_Id(String userId);
}
