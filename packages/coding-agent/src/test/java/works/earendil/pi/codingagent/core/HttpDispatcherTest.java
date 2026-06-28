package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpDispatcherTest {
    private final String httpHost = System.getProperty("http.proxyHost");
    private final String httpPort = System.getProperty("http.proxyPort");
    private final String httpsHost = System.getProperty("https.proxyHost");
    private final String httpsPort = System.getProperty("https.proxyPort");

    @AfterEach
    void restoreProxyProperties() {
        restore("http.proxyHost", httpHost);
        restore("http.proxyPort", httpPort);
        restore("https.proxyHost", httpsHost);
        restore("https.proxyPort", httpsPort);
    }

    @Test
    void parsesIdleTimeoutValues() {
        assertThat(HttpDispatcher.parseHttpIdleTimeoutMs("disabled")).contains(0);
        assertThat(HttpDispatcher.parseHttpIdleTimeoutMs(" 60000 ")).contains(60_000);
        assertThat(HttpDispatcher.parseHttpIdleTimeoutMs(12.9)).contains(12);
        assertThat(HttpDispatcher.parseHttpIdleTimeoutMs("")).isEmpty();
        assertThat(HttpDispatcher.parseHttpIdleTimeoutMs(-1)).isEmpty();
        assertThat(HttpDispatcher.parseHttpIdleTimeoutMs(Double.POSITIVE_INFINITY)).isEmpty();
    }

    @Test
    void formatsKnownAndCustomTimeouts() {
        assertThat(HttpDispatcher.formatHttpIdleTimeoutMs(30_000)).isEqualTo("30 sec");
        assertThat(HttpDispatcher.formatHttpIdleTimeoutMs(300_000)).isEqualTo("5 min");
        assertThat(HttpDispatcher.formatHttpIdleTimeoutMs(12_500)).isEqualTo("12.5 sec");
    }

    @Test
    void appliesProxySettingsWithoutOverwritingExistingProperties() {
        clearProxyProperties();

        HttpDispatcher.applyHttpProxySettings("http://127.0.0.1:7890");

        assertThat(System.getProperty("http.proxyHost")).isEqualTo("127.0.0.1");
        assertThat(System.getProperty("http.proxyPort")).isEqualTo("7890");
        assertThat(System.getProperty("https.proxyHost")).isEqualTo("127.0.0.1");
        assertThat(System.getProperty("https.proxyPort")).isEqualTo("7890");

        HttpDispatcher.applyHttpProxySettings("http://settings:8080");

        assertThat(System.getProperty("http.proxyHost")).isEqualTo("127.0.0.1");
        assertThat(System.getProperty("https.proxyHost")).isEqualTo("127.0.0.1");
    }

    @Test
    void configuresHttpClientAndRejectsInvalidTimeout() {
        clearProxyProperties();

        HttpClient client = HttpDispatcher.configureHttpDispatcher(0, "http://localhost:8080");

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(client.connectTimeout()).isEmpty();
        assertThat(System.getProperty("http.proxyHost")).isEqualTo("localhost");
        assertThatThrownBy(() -> HttpDispatcher.configureHttpDispatcher(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid HTTP idle timeout");
    }

    private static void clearProxyProperties() {
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
