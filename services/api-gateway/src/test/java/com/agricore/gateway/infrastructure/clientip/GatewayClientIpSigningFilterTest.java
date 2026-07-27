package com.agricore.gateway.infrastructure.clientip;

import com.agricore.common.security.ClientIpHeaderSigner;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GatewayClientIpSigningFilterTest {

    private static final String SECRET = "test-client-ip-signing-secret";
    private static final String TRUSTED_PRIVATE_NETWORK = "10\\.0\\.0\\.\\d+";

    @Test
    void trustedProxyPreservesCanonicalRealClientDespiteForgedPrefix() {
        HttpHeaders headers = forward(
                request("10.0.0.2")
                        .header("X-Forwarded-For", "1.1.1.1, 203.000.113.010, 10.0.0.9")
                        .build()
        );

        assertThat(headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_HEADER)).isEqualTo("203.0.113.10");
        assertThat(ClientIpHeaderSigner.verify(
                headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_HEADER),
                headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER),
                SECRET
        )).contains("203.0.113.10");
    }

    @Test
    void untrustedPeerCannotSpoofForwardedClientIp() {
        HttpHeaders headers = forward(
                request("198.51.100.8")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .header(ClientIpHeaderSigner.CLIENT_IP_HEADER, "1.1.1.1")
                        .header(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER, "forged")
                        .build()
        );

        assertThat(headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_HEADER)).isEqualTo("198.51.100.8");
        assertThat(ClientIpHeaderSigner.verify(
                headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_HEADER),
                headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER),
                SECRET
        )).contains("198.51.100.8");
    }

    @Test
    void stripsPublicForwardingAndForgedInternalHeaders() {
        HttpHeaders headers = forward(
                request("198.51.100.8")
                        .header("Forwarded", "for=203.0.113.10")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .header("X-Forwarded-Host", "spoofed.example")
                        .header("X-Real-IP", "203.0.113.10")
                        .header("True-Client-IP", "203.0.113.10")
                        .header(ClientIpHeaderSigner.CLIENT_IP_HEADER, "1.1.1.1")
                        .header(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER, "forged")
                        .build()
        );

        assertThat(headers).doesNotContainKeys(
                "Forwarded",
                "X-Forwarded-For",
                "X-Forwarded-Host",
                "X-Real-IP",
                "True-Client-IP"
        );
        assertThat(headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_HEADER)).isEqualTo("198.51.100.8");
        assertThat(headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER)).isNotEqualTo("forged");
    }

    @Test
    void rejectsBlankSigningSecretAtConstruction() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GatewayClientIpSigningFilter(" ", TRUSTED_PRIVATE_NETWORK))
                .withMessageContaining("signing-secret");
    }

    private static MockServerHttpRequest.BaseBuilder<?> request(String remoteAddress) {
        return MockServerHttpRequest.get("/api/v1/auth/login")
                .remoteAddress(new InetSocketAddress(remoteAddress, 443));
    }

    private static HttpHeaders forward(MockServerHttpRequest request) {
        GatewayClientIpSigningFilter filter = new GatewayClientIpSigningFilter(SECRET, TRUSTED_PRIVATE_NETWORK);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

        filter.filter(MockServerWebExchange.from(request), exchange -> {
            captured.set(exchange);
            return Mono.empty();
        }).block();

        return captured.get().getRequest().getHeaders();
    }
}
