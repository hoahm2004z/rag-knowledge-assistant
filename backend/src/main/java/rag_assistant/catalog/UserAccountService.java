package rag_assistant.catalog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rag_assistant.model.User;
import rag_assistant.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;

    public User getUserById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
