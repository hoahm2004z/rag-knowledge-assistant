package rag_assistant.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rag_assistant.model.Categories;
import rag_assistant.repository.CategoriesRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriesCatalogService {

    private final CategoriesRepository categoriesRepository;

    public List<Categories> getAllCategories() {
        return categoriesRepository.findAll();
    }
}
