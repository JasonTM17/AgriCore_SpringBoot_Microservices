package com.agricore.work.infrastructure.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties("agricore.inventory-access")
public class InventoryStockClientProperties {

    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(30);
    private static final int MIN_RESPONSE_BYTES = 256;
    private static final int MAX_RESPONSE_BYTES = 65_536;

    private String baseUrl = "http://localhost:8086";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);
    private Set<String> allowedHosts = new LinkedHashSet<>(Set.of(
            "localhost", "127.0.0.1", "::1", "inventory-service"
    ));
    private boolean allowInsecureHttp;
    private int maxResponseBytes = 8_192;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public Set<String> getAllowedHosts() { return allowedHosts; }
    public void setAllowedHosts(Set<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? null : new LinkedHashSet<>(allowedHosts);
    }
    public boolean isAllowInsecureHttp() { return allowInsecureHttp; }
    public void setAllowInsecureHttp(boolean allowInsecureHttp) { this.allowInsecureHttp = allowInsecureHttp; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }

    URI validatedBaseUri() {
        URI uri;
        try {
            uri = new URI(baseUrl);
        } catch (URISyntaxException | NullPointerException exception) {
            throw invalid("base-url must be a valid absolute URI", exception);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute() || !("http".equals(scheme) || "https".equals(scheme)) || host.isBlank()) {
            throw invalid("base-url must use http or https and include a host");
        }
        if (uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || !hasEmptyRootPath(uri)) {
            throw invalid("base-url cannot include user info, a path, query, or fragment");
        }
        if (!isAllowedHost(host)) {
            throw invalid("base-url host is not in allowed-hosts");
        }
        if ("http".equals(scheme) && !isLoopback(host) && !allowInsecureHttp) {
            throw invalid("plain HTTP requires allow-insecure-http for non-loopback hosts");
        }
        try {
            return new URI(scheme, null, host, uri.getPort(), null, null, null);
        } catch (URISyntaxException exception) {
            throw invalid("base-url could not be normalized", exception);
        }
    }

    Duration validatedConnectTimeout() {
        return validatedTimeout("connect-timeout", connectTimeout);
    }

    Duration validatedReadTimeout() {
        return validatedTimeout("read-timeout", readTimeout);
    }

    int validatedMaxResponseBytes() {
        if (maxResponseBytes < MIN_RESPONSE_BYTES || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw invalid("max-response-bytes must be between 256 and 65536");
        }
        return maxResponseBytes;
    }

    private boolean isAllowedHost(String host) {
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            return false;
        }
        return allowedHosts.stream()
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .map(candidate -> candidate.trim().toLowerCase(Locale.ROOT))
                .anyMatch(host::equals);
    }

    private static boolean hasEmptyRootPath(URI uri) {
        String path = uri.getRawPath();
        return path == null || path.isEmpty() || "/".equals(path);
    }

    private static boolean isLoopback(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static Duration validatedTimeout(String name, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw invalid(name + " must be greater than zero and no more than 30 seconds");
        }
        return timeout;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid agricore.inventory-access configuration: " + message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException("Invalid agricore.inventory-access configuration: " + message, cause);
    }
}
