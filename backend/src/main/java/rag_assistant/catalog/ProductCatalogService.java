package rag_assistant.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rag_assistant.model.Product;
import rag_assistant.repository.ProductRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductCatalogService {

    private final ProductRepository productRepository;

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(String productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    public List<Product> searchProductByName(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    public List<Product> getProductByCategories(String categoriesId) {
        return productRepository.findByCategories_Id(categoriesId);
    }

    public List<Product> getProductByManufacturer(String manufacturerId) {
        return productRepository.findByManufacturer_Id(manufacturerId);
    }

    public long count() {
        return productRepository.count();
    }
}
