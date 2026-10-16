package rag_assistant.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rag_assistant.dto.ApiResponse;
import rag_assistant.dto.AuthRequest;
import rag_assistant.dto.AuthResponse;
import rag_assistant.model.User;
import rag_assistant.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody AuthRequest authRequest) {
        try {
            String token = authService.verifyUser(authRequest);
            User user = authService.getUserByUsername(authRequest.getUsername());
            AuthResponse body = new AuthResponse(user.getId(), user.getUsername(), token);
            return ResponseEntity.ok(ApiResponse.success("User logged in successfully!", body));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.badRequest().body(ApiResponse.error("No token provided"));
            }
            authService.logoutUser(authHeader.substring(7));
            return ResponseEntity.ok(ApiResponse.success("User logged out successfully!", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}
