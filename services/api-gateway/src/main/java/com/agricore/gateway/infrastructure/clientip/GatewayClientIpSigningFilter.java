package com.agricore.gateway.infrastructure.clientip;

import com.agricore.common.security.ClientIpHeaderSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Defines the only trust boundary for a caller-supplied client address.
 *
 * <p>The Gateway accepts {@code X-Forwarded-For} only from a configured direct proxy, removes all
 * public forwarding metadata, and issues a signed canonical header for internal consumers.
 */
@Component
public final class GatewayClientIpSigningFilter implements GlobalFilter, Ordered {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern NO_TRUSTED_PROXY = Pattern.compile("(?!)");
    private static final Set<String> PUBLIC_CLIENT_ADDRESS_HEADERS = Set.of(
            "forwarded",
            "x-real-ip",
            "x-client-ip",
            "x-cluster-client-ip",
            "true-client-ip",
            "cf-connecting-ip",
            "fastly-client-ip",
            "x-envoy-external-address",
            "x-original-forwarded-for"
    );

    private final String signingSecret;
    private final Pattern trustedProxyAddressPattern;

    public GatewayClientIpSigningFilter(
            @Value("${agricore.gateway.client-ip.signing-secret}") String signingSecret,
            @Value("${agricore.gateway.client-ip.trusted-proxy-address-pattern:}") String trustedProxyAddressPattern
    ) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalArgumentException("agricore.gateway.client-ip.signing-secret must not be blank");
        }
        this.signingSecret = signingSecret;
        this.trustedProxyAddressPattern = compileTrustedProxyPattern(trustedProxyAddressPattern);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = resolveClientIp(exchange.getRequest()).orElse(null);
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> replaceClientAddressHeaders(headers, clientIp))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Optional<String> resolveClientIp(ServerHttpRequest request) {
        Optional<String> directPeer = canonicalRemoteAddress(request);
        if (directPeer.isEmpty() || !trustedProxyAddressPattern.matcher(directPeer.get()).matches()) {
            return directPeer;
        }
        return rightmostUntrustedForwardedIp(request.getHeaders()).or(() -> directPeer);
    }

    private Optional<String> rightmostUntrustedForwardedIp(HttpHeaders headers) {
        List<String> values = headers.getOrEmpty(X_FORWARDED_FOR);
        String[] hops = String.join(",", values).split(",", -1);
        for (int index = hops.length - 1; index >= 0; index--) {
            Optional<String> candidate = ClientIpHeaderSigner.canonicalize(hops[index].strip());
            if (candidate.isPresent() && !trustedProxyAddressPattern.matcher(candidate.get()).matches()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> canonicalRemoteAddress(ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return Optional.empty();
        }
        return ClientIpHeaderSigner.canonicalize(remoteAddress.getAddress().getHostAddress());
    }

    private void replaceClientAddressHeaders(HttpHeaders headers, String clientIp) {
        headers.keySet().stream()
                .filter(GatewayClientIpSigningFilter::isForwardingOrInternalHeader)
                .toList()
                .forEach(headers::remove);
        if (clientIp != null) {
            headers.set(ClientIpHeaderSigner.CLIENT_IP_HEADER, clientIp);
            headers.set(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER,
                    ClientIpHeaderSigner.sign(clientIp, signingSecret));
        }
    }

    private static boolean isForwardingOrInternalHeader(String headerName) {
        String normalized = headerName.toLowerCase(Locale.ROOT);
        return normalized.startsWith("x-forwarded-")
                || PUBLIC_CLIENT_ADDRESS_HEADERS.contains(normalized)
                || normalized.equals(ClientIpHeaderSigner.CLIENT_IP_HEADER.toLowerCase(Locale.ROOT))
                || normalized.equals(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER.toLowerCase(Locale.ROOT));
    }

    private static Pattern compileTrustedProxyPattern(String configuredPattern) {
        if (configuredPattern == null || configuredPattern.isBlank()) {
            return NO_TRUSTED_PROXY;
        }
        try {
            return Pattern.compile(configuredPattern.strip());
        } catch (PatternSyntaxException exception) {
            throw new IllegalArgumentException(
                    "agricore.gateway.client-ip.trusted-proxy-address-pattern is invalid",
                    exception
            );
        }
    }
}
