package rag_assistant.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rag_assistant.catalog.DiscountCatalogService;
import rag_assistant.catalog.ProductCatalogService;
import rag_assistant.dto.ApiResponse;
import rag_assistant.model.Product;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read-only catalog endpoints for verifying local DB seed (not production commerce).
 */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final ProductCatalogService productCatalogService;
    private final DiscountCatalogService discountCatalogService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("productCount", productCatalogService.count());
        data.put("source", "local-seed-mongodb");
        return ResponseEntity.ok(ApiResponse.success("Catalog status", data));
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> products(
            @RequestParam(required = false) String q) {
        List<Product> list = (q == null || q.isBlank())
                ? productCatalogService.getAllProducts()
                : productCatalogService.searchProductByName(q.trim());
        List<Map<String, Object>> rows = list.stream().map(this::toRow).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Products", rows));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> product(@PathVariable String id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Product", toRow(productCatalogService.getProductById(id))));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Product not found"));
        }
    }

    private Map<String, Object> toRow(Product product) {
        LocalDateTime now = LocalDateTime.now();
        int eventDiscount = discountCatalogService.getBestEventDiscountPercent(product.getId(), now);
        double finalPrice = discountCatalogService.calculateFinalPrice(
                product.getPrice(), product.getPercentDiscount(), eventDiscount);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productId", product.getId());
        row.put("name", product.getName());
        row.put("price", product.getPrice());
        row.put("originalPrice", product.getOriginalPrice());
        row.put("percentDiscount", product.getPercentDiscount());
        row.put("eventDiscountPercent", eventDiscount);
        row.put("finalPrice", finalPrice);
        row.put("quantity", product.getQuantity());
        row.put("inStock", product.getQuantity() > 0);
        row.put("ragDocumentKey", product.getRagDocumentKey());
        row.put("categoryName", product.getCategories() != null ? product.getCategories().getName() : null);
        row.put("manufacturerName", product.getManufacturer() != null ? product.getManufacturer().getName() : null);
        return row;
    }
}
