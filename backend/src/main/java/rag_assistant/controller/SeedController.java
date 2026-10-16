package rag_assistant.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rag_assistant.dto.ApiResponse;
import rag_assistant.seed.DatabaseSeeder;

import java.util.Map;

/**
 * Local-only helper to (re)build seed data. Does not touch rag_chunks.
 */
@RestController
@RequestMapping("/api/admin/seed")
@RequiredArgsConstructor
public class SeedController {

    private final DatabaseSeeder databaseSeeder;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> seed(
            @RequestParam(defaultValue = "false") boolean force) {
        Map<String, Object> result = databaseSeeder.seedAll(force);
        return ResponseEntity.ok(ApiResponse.success("Seed completed (local database)", result));
    }
}
