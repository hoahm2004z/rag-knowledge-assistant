package rag_assistant.catalog;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import rag_assistant.model.Product;
import rag_assistant.model.User;
import rag_assistant.repository.BillRepository;
import rag_assistant.repository.ProductRepository;
import rag_assistant.repository.UserRepository;
import rag_assistant.seed.DatabaseSeeder;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Requires local MongoDB at localhost:27018 (docker compose host port).
 * Skips automatically when Mongo is unreachable.
 */
@SpringBootTest(properties = {
        "gemini.api-key=test-not-used",
        "app.seed.enabled=false",
        "app.rag.startup-ingest=false",
        "app.jwt.secret=dGVzdC1qd3Qtc2VjcmV0LWZvci1haS1waGFybWFjeS1hc3Npc3RhbnQtdGVzdHM="
})
class CatalogSeedIntegrationTest {

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> "mongodb://localhost:27018/ai_pharmacy_assistant_test");
    }

    @BeforeAll
    static void requireMongo() {
        Assumptions.assumeTrue(isMongoUp(), "MongoDB not reachable on localhost:27018 — skip catalog IT");
    }

    private static boolean isMongoUp() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 27018), 800);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Autowired
    private DatabaseSeeder databaseSeeder;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductCatalogService productCatalogService;
    @Autowired
    private DiscountCatalogService discountCatalogService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BillRepository billRepository;
    @Autowired
    private OrderQueryService orderQueryService;

    @Test
    void seedCreatesProductsWithPriceAndStockFields() {
        databaseSeeder.seedAll(true);

        assertTrue(productRepository.count() >= 10);

        Product biragan = productCatalogService.getProductById("prod-biragan-650mg");
        assertEquals("Biragan 650", biragan.getName());
        assertTrue(biragan.getPrice() > 0);
        assertTrue(biragan.getQuantity() > 0);
        assertEquals("biragan-650mg", biragan.getRagDocumentKey());
        assertNotNull(biragan.getManufacturer());
        assertNotNull(biragan.getCategories());

        Product oos = productCatalogService.getProductById("prod-out-of-stock-demo");
        assertEquals(0, oos.getQuantity());

        List<Product> search = productCatalogService.searchProductByName("Biragan");
        assertFalse(search.isEmpty());
        assertTrue(search.stream().anyMatch(p -> "prod-biragan-650mg".equals(p.getId())));
    }

    @Test
    void discountFinalPriceUsesEventWhenActive() {
        databaseSeeder.seedAll(true);
        Product listerine = productCatalogService.getProductById("prod-listerine-100ml");
        int event = discountCatalogService.getBestEventDiscountPercent(listerine.getId(), LocalDateTime.now());
        assertEquals(15, event);
        double finalPrice = discountCatalogService.calculateFinalPrice(
                listerine.getPrice(), listerine.getPercentDiscount(), event);
        // max(10, 15) = 15% off list price
        assertEquals(listerine.getPrice() * 0.85, finalPrice, 0.01);
    }

    @Test
    void syntheticOrdersAreScopedByUser() {
        databaseSeeder.seedAll(true);
        User alice = userRepository.findByUsername("alice_demo").orElseThrow();
        User bob = userRepository.findByUsername("bob_demo").orElseThrow();

        assertEquals(1, orderQueryService.getUserBills(alice.getId()).size());
        assertEquals(1, orderQueryService.getUserBills(bob.getId()).size());

        var bobBill = orderQueryService.getBillById("bill-bob-001");
        assertEquals(bob.getId(), bobBill.getUserId());
        assertFalse(alice.getId().equals(bobBill.getUserId()));
        assertEquals(2, billRepository.count());
    }
}
