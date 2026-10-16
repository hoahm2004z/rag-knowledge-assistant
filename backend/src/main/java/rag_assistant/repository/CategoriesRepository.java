package rag_assistant.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import rag_assistant.model.Categories;

public interface CategoriesRepository extends MongoRepository<Categories, String> {
}
