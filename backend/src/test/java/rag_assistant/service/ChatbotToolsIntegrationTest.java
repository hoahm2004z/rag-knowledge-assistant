package rag_assistant.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import rag_assistant.seed.DatabaseSeeder;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "gemini.api-key=test-not-used",
        "app.seed.enabled=false",
        "app.rag.startup-ingest=false",
        "app.jwt.secret=dGVzdC1qd3Qtc2VjcmV0LWZvci1haS1waGFybWFjeS1hc3Npc3RhbnQtdGVzdHM="
})
class ChatbotToolsIntegrationTest {

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> "mongodb://localhost:27018/ai_pharmacy_assistant_test");
    }

    @BeforeAll
    static void requireMongo() {
        Assumptions.assumeTrue(isMongoUp(), "MongoDB not reachable — skip tools IT");
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
    private ChatbotToolService chatbotToolService;

    @BeforeEach
    void seed() {
        databaseSeeder.seedAll(true);
    }

    @Test
    void searchProductsFindsBiraganFromDb() {
        var exec = chatbotToolService.execute("searchProducts", Map.of("query", "Biragan"), null);
        assertTrue((Boolean) exec.getPayload().get("ok"));
        assertEquals("DB", exec.getPayload().get("sourceType"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) exec.getPayload().get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) data.get("products");
        assertFalse(products.isEmpty());
        assertTrue(products.stream().anyMatch(p -> "prod-biragan-650mg".equals(p.get("productId"))));
    }

    @Test
    void getProductDetailReturnsPriceAndStock() {
        var exec = chatbotToolService.execute(
                "getProductDetail", Map.of("productId", "prod-biragan-650mg"), null);
        assertTrue((Boolean) exec.getPayload().get("ok"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) exec.getPayload().get("data");
        assertEquals(48000.0, ((Number) data.get("price")).doubleValue(), 0.01);
        assertEquals(80, ((Number) data.get("quantity")).intValue());
        assertEquals(true, data.get("inStock"));
    }

    @Test
    void getDiscountProductsReturnsActiveFlashSale() {
        var exec = chatbotToolService.execute("getDiscountProducts", Map.of(), null);
        assertTrue((Boolean) exec.getPayload().get("ok"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) exec.getPayload().get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) data.get("events");
        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e -> Integer.valueOf(15).equals(e.get("discountPercent"))));
    }

    @Test
    void getStoreInfoUsesStandaloneConfig() {
        var exec = chatbotToolService.execute("getStoreInfo", Map.of(), null);
        assertTrue((Boolean) exec.getPayload().get("ok"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) exec.getPayload().get("data");
        assertNotNull(data.get("name"));
        assertTrue(String.valueOf(data.get("email")).endsWith(".example"));
        assertFalse(String.valueOf(data.get("hotline")).contains("8686"));
    }

    @Test
    void privateToolsRequireLogin() {
        for (String tool : List.of("getMyProfile", "getMyOrders", "getMyCart", "getMyAddresses")) {
            var exec = chatbotToolService.execute(tool, Map.of(), null);
            assertFalse((Boolean) exec.getPayload().get("ok"));
            assertEquals("LOGIN_REQUIRED", exec.getPayload().get("error"));
        }
    }

    @Test
    void aliceCannotReadBobsOrder() {
        var exec = chatbotToolService.execute(
                "getMyOrderDetail",
                Map.of("orderId", "bill-bob-001"),
                "user-alice");
        assertFalse((Boolean) exec.getPayload().get("ok"));
        assertEquals("FORBIDDEN", exec.getPayload().get("error"));
    }

    @Test
    void aliceCanReadOwnOrderAndNotBobsCart() {
        var own = chatbotToolService.execute(
                "getMyOrderDetail", Map.of("orderId", "bill-alice-001"), "user-alice");
        assertTrue((Boolean) own.getPayload().get("ok"));

        var aliceCart = chatbotToolService.execute("getMyCart", Map.of(), "user-alice");
        assertTrue((Boolean) aliceCart.getPayload().get("ok"));
        @SuppressWarnings("unchecked")
        Map<String, Object> cartData = (Map<String, Object>) aliceCart.getPayload().get("data");
        assertTrue(((Number) cartData.get("itemCount")).intValue() >= 1);

        var bobCart = chatbotToolService.execute("getMyCart", Map.of(), "user-bob");
        assertTrue((Boolean) bobCart.getPayload().get("ok"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bobData = (Map<String, Object>) bobCart.getPayload().get("data");
        // bob has no cart seeded
        assertEquals(0, ((Number) bobData.get("itemCount")).intValue());
        assertNotEquals(cartData.get("itemCount"), bobData.get("itemCount"));
    }

    @Test
    void missingProductIdReturnsNotFound() {
        var exec = chatbotToolService.execute(
                "getProductDetail", Map.of("productId", "prod-does-not-exist"), null);
        assertFalse((Boolean) exec.getPayload().get("ok"));
        assertEquals("NOT_FOUND", exec.getPayload().get("error"));
    }

    @Test
    void unknownToolIsRejected() {
        var exec = chatbotToolService.execute("openProduct", Map.of("productId", "x"), null);
        assertFalse((Boolean) exec.getPayload().get("ok"));
        assertEquals("UNKNOWN_TOOL", exec.getPayload().get("error"));
    }
}
