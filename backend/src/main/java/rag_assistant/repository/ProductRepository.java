package rag_assistant.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import rag_assistant.model.Product;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByNameContainingIgnoreCase(String name);

    List<Product> findByCategories_Id(String categoriesId);

    List<Product> findByManufacturer_Id(String manufacturerId);
}
