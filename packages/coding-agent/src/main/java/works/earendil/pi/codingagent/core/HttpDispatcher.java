package works.earendil.pi.codingagent.core;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public final class HttpDispatcher {
    public static final int DEFAULT_HTTP_IDLE_TIMEOUT_MS = 300_000;
    public static final List<TimeoutChoice> HTTP_IDLE_TIMEOUT_CHOICES = List.of(
            new TimeoutChoice("30 sec", 30_000),
            new TimeoutChoice("1 min", 60_000),
            new TimeoutChoice("2 min", 120_000),
            new TimeoutChoice("5 min", 300_000),
            new TimeoutChoice("disabled", 0)
    );

    private HttpDispatcher() {
    }

    public record TimeoutChoice(String label, int timeoutMs) {
    }

    public static Optional<Integer> parseHttpIdleTimeoutMs(Object value) {
        if (value instanceof String string) {
            String trimmed = string.trim();
            if (trimmed.equalsIgnoreCase("disabled")) {
                return Optional.of(0);
            }
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            try {
                double parsed = Double.parseDouble(trimmed);
                return parseHttpIdleTimeoutMs(parsed);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        if (value instanceof Number number) {
            double parsed = number.doubleValue();
            if (!Double.isFinite(parsed) || parsed < 0) {
                return Optional.empty();
            }
            return Optional.of((int) Math.floor(parsed));
        }
        return Optional.empty();
    }

    public static String formatHttpIdleTimeoutMs(int timeoutMs) {
        for (TimeoutChoice choice : HTTP_IDLE_TIMEOUT_CHOICES) {
            if (choice.timeoutMs() == timeoutMs) {
                return choice.label();
            }
        }
        return (timeoutMs / 1000.0) + " sec";
    }

    public static void applyHttpProxySettings(String httpProxy) {
        String proxy = httpProxy == null ? "" : httpProxy.trim();
        if (proxy.isEmpty()) {
            return;
        }
        applyProxyToScheme("http", proxy);
        applyProxyToScheme("https", proxy);
    }

    public static HttpClient configureHttpDispatcher() {
        return configureHttpDispatcher(DEFAULT_HTTP_IDLE_TIMEOUT_MS, null);
    }

    public static HttpClient configureHttpDispatcher(int timeoutMs) {
        return configureHttpDispatcher(timeoutMs, null);
    }

    public static HttpClient configureHttpDispatcher(int timeoutMs, String httpProxy) {
        int normalizedTimeout = parseHttpIdleTimeoutMs(timeoutMs)
                .orElseThrow(() -> new IllegalArgumentException("Invalid HTTP idle timeout: " + timeoutMs));
        if (httpProxy != null && !httpProxy.trim().isEmpty()) {
            applyHttpProxySettings(httpProxy);
        }
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(HttpClient.Version.HTTP_1_1);
        if (normalizedTimeout > 0) {
            builder.connectTimeout(Duration.ofMillis(normalizedTimeout));
        }
        proxySelectorFromProperties().ifPresent(builder::proxy);
        return builder.build();
    }

    private static void applyProxyToScheme(String scheme, String proxy) {
        String hostProperty = scheme + ".proxyHost";
        String portProperty = scheme + ".proxyPort";
        if (System.getProperty(hostProperty) != null || System.getProperty(portProperty) != null) {
            return;
        }
        try {
            URI uri = URI.create(proxy);
            if (uri.getHost() == null) {
                return;
            }
            System.setProperty(hostProperty, uri.getHost());
            int port = uri.getPort() >= 0 ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
            System.setProperty(portProperty, Integer.toString(port));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static Optional<ProxySelector> proxySelectorFromProperties() {
        String host = System.getProperty("https.proxyHost");
        String port = System.getProperty("https.proxyPort");
        if (host == null || host.isBlank()) {
            host = System.getProperty("http.proxyHost");
            port = System.getProperty("http.proxyPort");
        }
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        int parsedPort = 80;
        if (port != null && !port.isBlank()) {
            try {
                parsedPort = Integer.parseInt(port);
            } catch (NumberFormatException ignored) {
                parsedPort = 80;
            }
        }
        return Optional.of(ProxySelector.of(new InetSocketAddress(host, parsedPort)));
    }
}
