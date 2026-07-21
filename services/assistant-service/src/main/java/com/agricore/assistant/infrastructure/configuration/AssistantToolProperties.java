package com.agricore.assistant.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties("agricore.assistant.tools")
public class AssistantToolProperties {

    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(10);

    private boolean enabled;
    private String farmBaseUrl = "http://localhost:8082";
    private Set<String> allowedHosts = new LinkedHashSet<>(Set.of(
            "localhost", "127.0.0.1", "::1", "farm-service"
    ));
    private boolean allowInsecureHttp;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(3);
    private int maxResponseBytes = 32_768;
    private int maxPlots = 20;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getFarmBaseUrl() { return farmBaseUrl; }
    public void setFarmBaseUrl(String farmBaseUrl) { this.farmBaseUrl = farmBaseUrl; }
    public Set<String> getAllowedHosts() {
        return allowedHosts == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedHosts);
    }
    public void setAllowedHosts(Set<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? null : new LinkedHashSet<>(allowedHosts);
    }
    public boolean isAllowInsecureHttp() { return allowInsecureHttp; }
    public void setAllowInsecureHttp(boolean allowInsecureHttp) { this.allowInsecureHttp = allowInsecureHttp; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getMaxResponseBytes() { return maxResponseBytes; }
    public void setMaxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }
    public int getMaxPlots() { return maxPlots; }
    public void setMaxPlots(int maxPlots) { this.maxPlots = maxPlots; }

    public URI validatedFarmBaseUri() {
        URI uri;
        try {
            uri = new URI(farmBaseUrl);
        } catch (URISyntaxException | NullPointerException ex) {
            throw invalid("farm-base-url must be a valid absolute URI", ex);
        }
        String scheme = normalize(uri.getScheme());
        String host = normalize(uri.getHost());
        if (!uri.isAbsolute() || !("http".equals(scheme) || "https".equals(scheme)) || host.isBlank()) {
            throw invalid("farm-base-url must use http or https and include a host");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !hasEmptyRootPath(uri)) {
            throw invalid("farm-base-url cannot include user info, path, query, or fragment");
        }
        if (!isAllowedHost(host)) {
            throw invalid("farm-base-url host is not allowlisted");
        }
        if ("http".equals(scheme) && !isLoopback(host) && !allowInsecureHttp) {
            throw invalid("plain HTTP requires allow-insecure-http for non-loopback hosts");
        }
        try {
            return new URI(scheme, null, host, uri.getPort(), null, null, null);
        } catch (URISyntaxException ex) {
            throw invalid("farm-base-url could not be normalized", ex);
        }
    }

    public Duration validatedConnectTimeout() {
        return validatedTimeout("connect-timeout", connectTimeout);
    }

    public Duration validatedReadTimeout() {
        return validatedTimeout("read-timeout", readTimeout);
    }

    public int validatedMaxResponseBytes() {
        if (maxResponseBytes < 1_024 || maxResponseBytes > 131_072) {
            throw invalid("max-response-bytes must be between 1024 and 131072");
        }
        return maxResponseBytes;
    }

    public int validatedMaxPlots() {
        if (maxPlots < 1 || maxPlots > 20) {
            throw invalid("max-plots must be between 1 and 20");
        }
        return maxPlots;
    }

    private boolean isAllowedHost(String host) {
        return allowedHosts != null && allowedHosts.stream()
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .map(AssistantToolProperties::normalize)
                .anyMatch(host::equals);
    }

    private static boolean hasEmptyRootPath(URI uri) {
        return uri.getRawPath() == null || uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath());
    }

    private static boolean isLoopback(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static Duration validatedTimeout(String name, Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw invalid(name + " must be positive and at most 10 seconds");
        }
        return timeout;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid assistant tool configuration: " + message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException("Invalid assistant tool configuration: " + message, cause);
    }
}
