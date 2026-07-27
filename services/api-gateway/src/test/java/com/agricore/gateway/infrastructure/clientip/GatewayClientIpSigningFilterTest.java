package com.agricore.gateway.infrastructure.clientip;

import com.agricore.common.security.ClientIpHeaderSigner;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayClientIpSigningFilterTest {

    private static final String SECRET = "test-client-ip-signing-secret";
    private static final String TRUSTED_PRIVATE_NETWORK = "10\\.0\\.0\\.\\d+";

    @Test
    void signsTrustedProxyClientForIdentityRouteOnly() {
        HttpHeaders headers = forward(ClientIpHeaderSigner.IDENTITY_SERVICE_AUDIENCE,
                request("10.0.0.2")
                        .header("X-Forwarded-For", "1.1.1.1, 203.000.113.010, 10.0.0.9")
                        .build()
        );

        assertThat(headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_HEADER)).isEqualTo("203.0.113.10");
        assertThat(ClientIpHeaderSigner.verify(
                headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_HEADER),
                headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER),
                ClientIpHeaderSigner.IDENTITY_SERVICE_AUDIENCE,
                SECRET
        )).contains("203.0.113.10");
    }

    @Test
    void signsUntrustedPeerForAssistantRouteOnly() {
        HttpHeaders headers = forward(ClientIpHeaderSigner.ASSISTANT_SERVICE_AUDIENCE,
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
                ClientIpHeaderSigner.ASSISTANT_SERVICE_AUDIENCE,
                SECRET
        )).contains("198.51.100.8");
        assertThat(ClientIpHeaderSigner.verify(
                headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_HEADER),
                headers.getFirst(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER),
                ClientIpHeaderSigner.IDENTITY_SERVICE_AUDIENCE,
                SECRET
        )).isEmpty();
    }

    @Test
    void normalRoutesStripPublicForwardingAndForgedInternalHeadersWithoutResigning() {
        HttpHeaders headers = forward("farm-service",
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
        assertThat(headers).doesNotContainKeys(
                ClientIpHeaderSigner.CLIENT_IP_HEADER,
                ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER
        );
    }

    @Test
    void missingRouteStripsForgedInternalHeadersWithoutEmittingSignedHeaders() {
        HttpHeaders headers = forward(null,
                request("198.51.100.8")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .header(ClientIpHeaderSigner.CLIENT_IP_HEADER, "1.1.1.1")
                        .header(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER, "forged")
                        .build()
        );

        assertThat(headers).doesNotContainKeys(
                "X-Forwarded-For",
                ClientIpHeaderSigner.CLIENT_IP_HEADER,
                ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER
        );
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

    private static HttpHeaders forward(String routeId, MockServerHttpRequest request) {
        GatewayClientIpSigningFilter filter = new GatewayClientIpSigningFilter(SECRET, TRUSTED_PRIVATE_NETWORK);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        if (routeId != null) {
            Route route = mock(Route.class);
            when(route.getId()).thenReturn(routeId);
            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        }

        filter.filter(exchange, forwardedExchange -> {
            captured.set(forwardedExchange);
            return Mono.empty();
        }).block();

        return captured.get().getRequest().getHeaders();
    }
}
