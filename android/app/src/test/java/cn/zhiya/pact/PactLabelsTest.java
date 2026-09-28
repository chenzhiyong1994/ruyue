package cn.zhiya.pact;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PactLabelsTest {
    @Test public void acceptsHttpsOriginAndNormalizesTrailingSlash() {
        assertEquals("https://example.com:8443", PactLabels.validateBaseUrl(" https://example.com:8443/ ", false));
    }
    @Test public void debugCleartextLimitedToExactLoopbackOrigins() {
        assertEquals("http://10.0.2.2:8765", PactLabels.validateBaseUrl("http://10.0.2.2:8765", true));
        assertEquals("http://localhost:8765", PactLabels.validateBaseUrl("http://localhost:8765", true));
        assertEquals("http://127.0.0.1:8765", PactLabels.validateBaseUrl("http://127.0.0.1:8765", true));
        for (String value : new String[]{"http://example.com", "http://localhost.example.com", "http://127.0.0.2", "http://10.0.2.2.example.com"})
            assertThrows(IllegalArgumentException.class, () -> PactLabels.validateBaseUrl(value, true));
    }
    @Test public void releaseRejectsAllCleartextIncludingLoopback() {
        assertThrows(IllegalArgumentException.class, () -> PactLabels.validateBaseUrl("http://127.0.0.1:8765", false));
    }
    @Test public void rejectsCredentialAndPathInjection() {
        for (String value : new String[]{"https://user:password@example.com", "https://example.com/v1", "https://example.com?token=secret", "https://example.com/#fragment", "not a URL", "file:///private"})
            assertThrows(IllegalArgumentException.class, () -> PactLabels.validateBaseUrl(value, true));
    }
    @Test public void rejectsInvalidPorts() {
        for (String value : new String[]{"https://example.com:0", "https://example.com:65536", "https://example.com:-1"})
            assertThrows(IllegalArgumentException.class, () -> PactLabels.validateBaseUrl(value, true));
    }
}
