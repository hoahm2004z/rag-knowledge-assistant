package rag_assistant.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import rag_assistant.seed.DatabaseSeeder;

import java.net.InetSocketAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "gemini.api-key=test-not-used",
        "app.seed.enabled=false",
        "app.rag.startup-ingest=false",
        "app.jwt.secret=dGVzdC1qd3Qtc2VjcmV0LWZvci1haS1waGFybWFjeS1hc3Npc3RhbnQtdGVzdHM="
})
@AutoConfigureMockMvc
class AuthLoginIntegrationTest {

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri",
                () -> "mongodb://localhost:27018/ai_pharmacy_assistant_test");
    }

    @BeforeAll
    static void requireMongo() {
        Assumptions.assumeTrue(isMongoUp(), "MongoDB not reachable — skip auth IT");
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
    private MockMvc mockMvc;
    @Autowired
    private DatabaseSeeder databaseSeeder;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seed() {
        databaseSeeder.seedAll(true);
    }

    @Test
    void loginReturnsJwtForSeedUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice_demo\",\"password\":\"DemoUser123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(body.path("data").path("token").asText());
    }

    @Test
    void seedEndpointForbiddenWithoutAdmin() throws Exception {
        mockMvc.perform(post("/api/admin/seed?force=false"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanCallSeed() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin_demo\",\"password\":\"DemoUser123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString())
                .path("data").path("token").asText();
        assertTrue(token.length() > 20);

        mockMvc.perform(post("/api/admin/seed?force=false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
