package rag_assistant.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import rag_assistant.model.ShippingAddress;

import java.util.List;

public interface ShippingAddressRepository extends MongoRepository<ShippingAddress, String> {
    List<ShippingAddress> findByUser_Id(String userId);
}
