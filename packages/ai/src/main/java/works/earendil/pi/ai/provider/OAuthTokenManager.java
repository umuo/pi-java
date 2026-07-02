package works.earendil.pi.ai.provider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class OAuthTokenManager {
    private static final Map<String, TokenInfo> cache = new ConcurrentHashMap<>();

    public record TokenInfo(
            String accessToken,
            String refreshToken,
            Instant expiresAt,
            String providerId
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt.minusSeconds(60));
        }
    }

    private OAuthTokenManager() {}

    public static Optional<String> getValidAccessToken(String providerId) {
        TokenInfo info = cache.get(providerId);
        if (info != null && !info.isExpired()) {
            return Optional.of(info.accessToken());
        }
        return loadTokenFromDisk(providerId).map(t -> {
            cache.put(providerId, t);
            return t.accessToken();
        });
    }

    public static void saveToken(String providerId, TokenInfo info) {
        cache.put(providerId, info);
        saveTokenToDisk(providerId, info);
    }

    private static Path getAuthDir() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".pi", "agent", "auth");
    }

    private static Optional<TokenInfo> loadTokenFromDisk(String providerId) {
        try {
            Path file = getAuthDir().resolve(providerId + ".token");
            if (Files.exists(file)) {
                String content = Files.readString(file);
                String[] parts = content.split(":");
                if (parts.length >= 3) {
                    return Optional.of(new TokenInfo(parts[0], parts[1], Instant.ofEpochMilli(Long.parseLong(parts[2])), providerId));
                }
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private static void saveTokenToDisk(String providerId, TokenInfo info) {
        try {
            Path dir = getAuthDir();
            Files.createDirectories(dir);
            Path file = dir.resolve(providerId + ".token");
            String content = info.accessToken() + ":" + info.refreshToken() + ":" + info.expiresAt().toEpochMilli();
            Files.writeString(file, content);
        } catch (IOException ignored) {
        }
    }
}
