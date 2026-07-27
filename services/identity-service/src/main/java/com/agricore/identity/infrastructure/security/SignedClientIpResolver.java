package com.agricore.identity.infrastructure.security;

import com.agricore.common.security.ClientIpHeaderSigner;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Accepts a client IP only when the Gateway authenticated its internal header.
 */
@Component
public final class SignedClientIpResolver {

    private static final String AUDIENCE = ClientIpHeaderSigner.IDENTITY_SERVICE_AUDIENCE;

    private final String signingSecret;

    public SignedClientIpResolver(
            @Value("${agricore.client-ip.signing-secret:}") String signingSecret
    ) {
        this.signingSecret = signingSecret;
    }

    public String resolve(HttpServletRequest request) {
        String fallback = immediateRemoteAddress(request);
        if (request == null) {
            return fallback;
        }
        return ClientIpHeaderSigner.verify(
                request.getHeader(ClientIpHeaderSigner.CLIENT_IP_HEADER),
                request.getHeader(ClientIpHeaderSigner.CLIENT_IP_SIGNATURE_HEADER),
                AUDIENCE,
                signingSecret
        ).orElse(fallback);
    }

    private static String immediateRemoteAddress(HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()) {
            return "unknown";
        }
        String remoteAddress = request.getRemoteAddr().strip();
        return ClientIpHeaderSigner.canonicalize(remoteAddress).orElse(remoteAddress);
    }
}
