package rag_assistant.security;

import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory logout blacklist (local/demo; lost on restart). */
@Service
public class TokenBlacklistService {

    private final Map<String, Long> blacklisted = new ConcurrentHashMap<>();

    public void blackList(String token, Date expiryTime) {
        blacklisted.put(token, expiryTime != null ? expiryTime.getTime() : System.currentTimeMillis());
    }

    public boolean isBlackListed(String token) {
        Long exp = blacklisted.get(token);
        if (exp == null) {
            return false;
        }
        if (exp < System.currentTimeMillis()) {
            blacklisted.remove(token);
            return false;
        }
        return true;
    }
}
