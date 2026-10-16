package rag_assistant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import rag_assistant.dto.AuthRequest;
import rag_assistant.model.User;
import rag_assistant.repository.UserRepository;
import rag_assistant.security.JwtService;
import rag_assistant.security.TokenBlacklistService;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuthenticationManager authenticationManager;

    public String verifyUser(AuthRequest authRequest) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            authRequest.getUsername(),
                            authRequest.getPassword()
                    )
            );
            if (authentication.isAuthenticated()) {
                return jwtService.generateToken(authRequest.getUsername());
            }
        } catch (Exception e) {
            throw new RuntimeException("Invalid username or password");
        }
        throw new RuntimeException("Invalid username or password");
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public void logoutUser(String token) {
        if (tokenBlacklistService.isBlackListed(token)) {
            throw new RuntimeException("Token already invalidated");
        }
        Date expiryTime = jwtService.extractExpiration(token);
        tokenBlacklistService.blackList(token, expiryTime);
    }
}
