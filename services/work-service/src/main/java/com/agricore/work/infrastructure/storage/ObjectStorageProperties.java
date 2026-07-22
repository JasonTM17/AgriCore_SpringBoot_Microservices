package com.agricore.work.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@ConfigurationProperties("agricore.object-storage")
public class ObjectStorageProperties {

    private static final Pattern BUCKET_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");
    private static final Duration MIN_DOWNLOAD_TTL = Duration.ofMinutes(1);
    private static final Duration MAX_DOWNLOAD_TTL = Duration.ofHours(24);

    private boolean enabled;
    private String endpoint = "http://localhost:9000";
    private String publicEndpoint = "http://localhost:9000";
    private String accessKey = "";
    private String secretKey = "";
    private String bucket = "agricore-work-attachments";
    private Set<String> allowedHosts = new LinkedHashSet<>(Set.of("localhost", "127.0.0.1", "::1", "minio"));
    private boolean allowInsecureHttp;
    private Duration downloadUrlTtl = Duration.ofMinutes(15);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getPublicEndpoint() { return publicEndpoint; }
    public void setPublicEndpoint(String publicEndpoint) { this.publicEndpoint = publicEndpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = normalized(accessKey); }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = normalized(secretKey); }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = normalized(bucket); }
    public Set<String> getAllowedHosts() { return allowedHosts; }
    public void setAllowedHosts(Set<String> allowedHosts) {
        this.allowedHosts = allowedHosts == null ? null : new LinkedHashSet<>(allowedHosts);
    }
    public boolean isAllowInsecureHttp() { return allowInsecureHttp; }
    public void setAllowInsecureHttp(boolean allowInsecureHttp) { this.allowInsecureHttp = allowInsecureHttp; }
    public Duration getDownloadUrlTtl() { return downloadUrlTtl; }
    public void setDownloadUrlTtl(Duration downloadUrlTtl) { this.downloadUrlTtl = downloadUrlTtl; }

    URI validatedEndpoint() {
        return validatedEndpoint("endpoint", endpoint);
    }

    URI validatedPublicEndpoint() {
        return validatedEndpoint("public-endpoint", publicEndpoint);
    }

    private URI validatedEndpoint(String propertyName, String value) {
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException | NullPointerException exception) {
            throw invalid(propertyName + " must be a valid absolute URI", exception);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!uri.isAbsolute() || !("http".equals(scheme) || "https".equals(scheme)) || host.isBlank()) {
            throw invalid(propertyName + " must use http or https and include a host");
        }
        if (uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || !hasEmptyRootPath(uri)) {
            throw invalid(propertyName + " cannot include user info, a path, query, or fragment");
        }
        if (!isAllowedHost(host)) {
            throw invalid(propertyName + " host is not in allowed-hosts");
        }
        if ("http".equals(scheme) && !isLoopback(host) && !allowInsecureHttp) {
            throw invalid(propertyName + " plain HTTP requires allow-insecure-http for non-loopback hosts");
        }
        try {
            return new URI(scheme, null, host, uri.getPort(), null, null, null);
        } catch (URISyntaxException exception) {
            throw invalid(propertyName + " could not be normalized", exception);
        }
    }

    String validatedAccessKey() {
        return validatedCredential("access-key", accessKey, 3, 128);
    }

    String validatedSecretKey() {
        return validatedCredential("secret-key", secretKey, 8, 256);
    }

    String validatedBucket() {
        String normalizedBucket = normalized(bucket).toLowerCase(Locale.ROOT);
        if (!BUCKET_PATTERN.matcher(normalizedBucket).matches()
                || normalizedBucket.contains("..")
                || resemblesIpAddress(normalizedBucket)) {
            throw invalid("bucket must be a valid DNS-style S3 bucket name");
        }
        return normalizedBucket;
    }

    Duration validatedDownloadUrlTtl() {
        if (downloadUrlTtl == null
                || downloadUrlTtl.compareTo(MIN_DOWNLOAD_TTL) < 0
                || downloadUrlTtl.compareTo(MAX_DOWNLOAD_TTL) > 0) {
            throw invalid("download-url-ttl must be between 1 minute and 24 hours");
        }
        return downloadUrlTtl;
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

    private static String validatedCredential(String name, String credential, int min, int max) {
        String value = normalized(credential);
        if (value.length() < min || value.length() > max) {
            throw invalid(name + " length is invalid");
        }
        return value;
    }

    private static boolean hasEmptyRootPath(URI uri) {
        String path = uri.getRawPath();
        return path == null || path.isEmpty() || "/".equals(path);
    }

    private static boolean isLoopback(String host) {
        return "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private static boolean resemblesIpAddress(String bucketName) {
        return bucketName.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}$");
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid agricore.object-storage configuration: " + message);
    }

    private static IllegalArgumentException invalid(String message, Throwable cause) {
        return new IllegalArgumentException("Invalid agricore.object-storage configuration: " + message, cause);
    }
}
