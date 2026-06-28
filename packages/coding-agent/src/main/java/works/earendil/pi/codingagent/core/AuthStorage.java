package works.earendil.pi.codingagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.codingagent.tools.PathUtils;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class AuthStorage {
    private final AuthStorageBackend storage;
    private final Map<String, AuthCredential> data = new LinkedHashMap<>();
    private final Map<String, String> runtimeOverrides = new HashMap<>();
    private final List<Exception> errors = new ArrayList<>();
    private final Map<String, OAuthProvider> oauthProviders = new LinkedHashMap<>();
    private Exception loadError;
    private Map<String, String> environment = Map.of();

    private AuthStorage(AuthStorageBackend storage) {
        this.storage = storage;
        reload();
    }

    public record AuthStatus(boolean configured, Source source, String label) {
        public enum Source {
            STORED,
            RUNTIME,
            ENVIRONMENT,
            FALLBACK,
            MODELS_JSON_KEY,
            MODELS_JSON_COMMAND
        }
    }

    public record GetApiKeyOptions(boolean includeFallback) {
        public static GetApiKeyOptions defaults() {
            return new GetApiKeyOptions(true);
        }
    }

    public sealed interface AuthCredential permits ApiKeyCredential, OAuthCredential {
        String type();
    }

    public record ApiKeyCredential(String key, Map<String, String> env) implements AuthCredential {
        @Override
        public String type() {
            return "api_key";
        }
    }

    public record OAuthCredential(String access, String refresh, long expires, Map<String, JsonNode> extra)
            implements AuthCredential {
        @Override
        public String type() {
            return "oauth";
        }
    }

    public record OAuthRefreshResult(String apiKey, OAuthCredential newCredentials) {
    }

    public interface OAuthProvider {
        String id();

        OAuthCredential login(OAuthLoginCallbacks callbacks) throws Exception;

        OAuthCredential refreshToken(OAuthCredential credentials) throws Exception;

        String getApiKey(OAuthCredential credentials);
    }

    public interface OAuthLoginCallbacks {
    }

    public static AuthStorage create(Path authPath) {
        return new AuthStorage(new FileAuthStorageBackend(authPath));
    }

    public static AuthStorage fromStorage(AuthStorageBackend storage) {
        return new AuthStorage(storage);
    }

    public static AuthStorage inMemory() {
        return inMemory(Map.of());
    }

    public static AuthStorage inMemory(Map<String, AuthCredential> data) {
        InMemoryAuthStorageBackend storage = new InMemoryAuthStorageBackend();
        storage.withLock(current -> new LockResult<>(null, stringifyData(data)));
        return fromStorage(storage);
    }

    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    public void registerOAuthProvider(OAuthProvider provider) {
        oauthProviders.put(provider.id(), provider);
    }

    public List<String> getOAuthProviders() {
        return List.copyOf(oauthProviders.keySet());
    }

    public void setRuntimeApiKey(String provider, String apiKey) {
        runtimeOverrides.put(provider, apiKey);
    }

    public void removeRuntimeApiKey(String provider) {
        runtimeOverrides.remove(provider);
    }

    public void reload() {
        try {
            String content = storage.withLock(current -> new LockResult<>(current, null));
            data.clear();
            data.putAll(parseStorageData(content));
            loadError = null;
        } catch (Exception e) {
            loadError = e;
            recordError(e);
        }
    }

    public Optional<AuthCredential> get(String provider) {
        return Optional.ofNullable(data.get(provider));
    }

    public Map<String, String> getProviderEnv(String provider) {
        AuthCredential credential = data.get(provider);
        if (credential instanceof ApiKeyCredential apiKeyCredential && apiKeyCredential.env() != null) {
            return Map.copyOf(apiKeyCredential.env());
        }
        return null;
    }

    public void set(String provider, AuthCredential credential) {
        data.put(provider, credential);
        persistProviderChange(provider, credential);
    }

    public void remove(String provider) {
        data.remove(provider);
        persistProviderChange(provider, null);
    }

    public List<String> list() {
        return List.copyOf(data.keySet());
    }

    public boolean has(String provider) {
        return data.containsKey(provider);
    }

    public boolean hasAuth(String provider) {
        return runtimeOverrides.containsKey(provider)
                || data.containsKey(provider)
                || EnvApiKeys.getEnvApiKey(provider, environment).isPresent();
    }

    public AuthStatus getAuthStatus(String provider) {
        if (data.containsKey(provider)) {
            return new AuthStatus(true, AuthStatus.Source.STORED, null);
        }
        if (runtimeOverrides.containsKey(provider)) {
            return new AuthStatus(false, AuthStatus.Source.RUNTIME, "--api-key");
        }
        List<String> envKeys = EnvApiKeys.findEnvKeys(provider, environment);
        if (envKeys != null && !envKeys.isEmpty()) {
            return new AuthStatus(false, AuthStatus.Source.ENVIRONMENT, envKeys.getFirst());
        }
        return new AuthStatus(false, null, null);
    }

    public Map<String, AuthCredential> getAll() {
        return Map.copyOf(data);
    }

    public List<Exception> drainErrors() {
        List<Exception> drained = List.copyOf(errors);
        errors.clear();
        return drained;
    }

    public void login(String providerId, OAuthLoginCallbacks callbacks) throws Exception {
        OAuthProvider provider = oauthProviders.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown OAuth provider: " + providerId);
        }
        set(providerId, provider.login(callbacks));
    }

    public void logout(String provider) {
        remove(provider);
    }

    public Optional<String> getApiKey(String providerId) {
        return getApiKey(providerId, GetApiKeyOptions.defaults());
    }

    public Optional<String> getApiKey(String providerId, GetApiKeyOptions options) {
        String runtimeKey = runtimeOverrides.get(providerId);
        if (runtimeKey != null && !runtimeKey.isEmpty()) {
            return Optional.of(runtimeKey);
        }
        AuthCredential credential = data.get(providerId);
        if (credential instanceof ApiKeyCredential apiKeyCredential) {
            return ConfigValueResolver.resolveConfigValue(apiKeyCredential.key(), apiKeyCredential.env());
        }
        if (credential instanceof OAuthCredential oauthCredential) {
            OAuthProvider provider = oauthProviders.get(providerId);
            if (provider == null) {
                return Optional.empty();
            }
            if (Instant.now().toEpochMilli() >= oauthCredential.expires()) {
                try {
                    Optional<OAuthRefreshResult> result = refreshOAuthTokenWithLock(providerId);
                    if (result.isPresent()) {
                        return Optional.of(result.get().apiKey());
                    }
                } catch (Exception e) {
                    recordError(e);
                    reload();
                    AuthCredential updatedCredential = data.get(providerId);
                    if (updatedCredential instanceof OAuthCredential updatedOAuth
                            && Instant.now().toEpochMilli() < updatedOAuth.expires()) {
                        return Optional.ofNullable(provider.getApiKey(updatedOAuth));
                    }
                    return Optional.empty();
                }
            } else {
                return Optional.ofNullable(provider.getApiKey(oauthCredential));
            }
        }
        if (options != null && !options.includeFallback()) {
            return Optional.empty();
        }
        return EnvApiKeys.getEnvApiKey(providerId, environment);
    }

    private Optional<OAuthRefreshResult> refreshOAuthTokenWithLock(String providerId) throws Exception {
        OAuthProvider provider = oauthProviders.get(providerId);
        if (provider == null) {
            return Optional.empty();
        }
        return storage.withLock(current -> {
            try {
                Map<String, AuthCredential> currentData = parseStorageData(current);
                data.clear();
                data.putAll(currentData);
                loadError = null;
                AuthCredential credential = currentData.get(providerId);
                if (!(credential instanceof OAuthCredential oauthCredential)) {
                    return new LockResult<>(Optional.<OAuthRefreshResult>empty(), null);
                }
                if (Instant.now().toEpochMilli() < oauthCredential.expires()) {
                    return new LockResult<>(Optional.of(new OAuthRefreshResult(
                            provider.getApiKey(oauthCredential), oauthCredential)), null);
                }
                OAuthCredential refreshed = provider.refreshToken(oauthCredential);
                Map<String, AuthCredential> merged = new LinkedHashMap<>(currentData);
                merged.put(providerId, refreshed);
                data.clear();
                data.putAll(merged);
                loadError = null;
                return new LockResult<>(Optional.of(new OAuthRefreshResult(provider.getApiKey(refreshed), refreshed)),
                        stringifyData(merged));
            } catch (Exception e) {
                throw new AuthStorageException(e);
            }
        });
    }

    private void persistProviderChange(String provider, AuthCredential credential) {
        if (loadError != null) {
            return;
        }
        try {
            storage.withLock(current -> {
                Map<String, AuthCredential> currentData = parseStorageData(current);
                Map<String, AuthCredential> merged = new LinkedHashMap<>(currentData);
                if (credential == null) {
                    merged.remove(provider);
                } else {
                    merged.put(provider, credential);
                }
                return new LockResult<>(null, stringifyData(merged));
            });
        } catch (Exception e) {
            recordError(e);
        }
    }

    private void recordError(Exception error) {
        errors.add(error);
    }

    private static Map<String, AuthCredential> parseStorageData(String content) {
        if (content == null || content.isBlank()) {
            return new LinkedHashMap<>();
        }
        JsonNode root = JsonCodec.parse(content);
        if (!root.isObject()) {
            throw new IllegalArgumentException("Auth storage root must be an object");
        }
        Map<String, AuthCredential> parsed = new LinkedHashMap<>();
        root.fields().forEachRemaining(entry -> parsed.put(entry.getKey(), parseCredential(entry.getValue())));
        return parsed;
    }

    private static AuthCredential parseCredential(JsonNode node) {
        String type = node.path("type").asText();
        return switch (type) {
            case "api_key" -> {
                Map<String, String> env = null;
                if (node.get("env") != null && node.get("env").isObject()) {
                    Map<String, String> parsedEnv = new LinkedHashMap<>();
                    node.get("env").fields().forEachRemaining(entry ->
                            parsedEnv.put(entry.getKey(), entry.getValue().asText()));
                    env = parsedEnv;
                }
                yield new ApiKeyCredential(node.path("key").asText(), env);
            }
            case "oauth" -> {
                Map<String, JsonNode> extra = new LinkedHashMap<>();
                node.fields().forEachRemaining(entry -> {
                    if (!Set.of("type", "access", "refresh", "expires").contains(entry.getKey())) {
                        extra.put(entry.getKey(), entry.getValue());
                    }
                });
                yield new OAuthCredential(node.path("access").asText(), node.path("refresh").asText(),
                        node.path("expires").asLong(), extra);
            }
            default -> throw new IllegalArgumentException("Unknown auth credential type: " + type);
        };
    }

    private static String stringifyData(Map<String, AuthCredential> data) {
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        for (Map.Entry<String, AuthCredential> entry : data.entrySet()) {
            root.set(entry.getKey(), credentialNode(entry.getValue()));
        }
        try {
            return JsonCodec.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to serialize auth storage", e);
        }
    }

    private static ObjectNode credentialNode(AuthCredential credential) {
        ObjectNode node = JsonCodec.mapper().createObjectNode();
        node.put("type", credential.type());
        if (credential instanceof ApiKeyCredential apiKeyCredential) {
            node.put("key", apiKeyCredential.key());
            if (apiKeyCredential.env() != null && !apiKeyCredential.env().isEmpty()) {
                ObjectNode env = JsonCodec.mapper().createObjectNode();
                apiKeyCredential.env().forEach(env::put);
                node.set("env", env);
            }
        } else if (credential instanceof OAuthCredential oauthCredential) {
            node.put("access", oauthCredential.access());
            node.put("refresh", oauthCredential.refresh());
            node.put("expires", oauthCredential.expires());
            if (oauthCredential.extra() != null) {
                oauthCredential.extra().forEach(node::set);
            }
        }
        return node;
    }

    public interface AuthStorageBackend {
        <T> T withLock(LockCallback<T> callback);

        default <T> CompletableFuture<T> withLockAsync(LockCallback<T> callback) {
            return CompletableFuture.completedFuture(withLock(callback));
        }
    }

    @FunctionalInterface
    public interface LockCallback<T> {
        LockResult<T> apply(String current);
    }

    public record LockResult<T>(T result, String next) {
    }

    public static final class InMemoryAuthStorageBackend implements AuthStorageBackend {
        private String value;

        @Override
        public synchronized <T> T withLock(LockCallback<T> callback) {
            LockResult<T> result = callback.apply(value);
            if (result.next() != null) {
                value = result.next();
            }
            return result.result();
        }
    }

    public static final class FileAuthStorageBackend implements AuthStorageBackend {
        private final Path authPath;

        public FileAuthStorageBackend(Path authPath) {
            this.authPath = PathUtils.resolvePath(authPath.toString());
        }

        @Override
        public <T> T withLock(LockCallback<T> callback) {
            try {
                ensureParentDir();
                ensureFileExists();
                try (FileChannel channel = FileChannel.open(authPath,
                        StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                     FileLock ignored = channel.lock()) {
                    String current = Files.exists(authPath) ? Files.readString(authPath, StandardCharsets.UTF_8) : null;
                    LockResult<T> result = callback.apply(current);
                    if (result.next() != null) {
                        channel.truncate(0);
                        channel.position(0);
                        channel.write(StandardCharsets.UTF_8.encode(result.next()));
                        setOwnerOnlyPermissions(authPath);
                    }
                    return result.result();
                }
            } catch (IOException e) {
                throw new AuthStorageException(e);
            }
        }

        public Path authPath() {
            return authPath;
        }

        private void ensureParentDir() throws IOException {
            Files.createDirectories(authPath.getParent());
            setOwnerOnlyPermissions(authPath.getParent());
        }

        private void ensureFileExists() throws IOException {
            if (!Files.exists(authPath)) {
                Files.writeString(authPath, "{}", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                setOwnerOnlyPermissions(authPath);
            }
        }
    }

    public static final class AuthStorageException extends RuntimeException {
        public AuthStorageException(Throwable cause) {
            super(cause);
        }
    }

    private static void setOwnerOnlyPermissions(Path path) {
        try {
            if (Files.isDirectory(path)) {
                Files.setPosixFilePermissions(path, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
            } else {
                Files.setPosixFilePermissions(path, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE));
            }
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and some mounted filesystems do not support POSIX mode bits.
        }
    }
}
