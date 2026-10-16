package rag_assistant.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import rag_assistant.model.Manufacturer;

public interface ManufacturerRepository extends MongoRepository<Manufacturer, String> {
}
