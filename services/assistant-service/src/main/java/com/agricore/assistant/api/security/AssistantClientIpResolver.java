package com.agricore.assistant.api.security;

import com.agricore.common.security.ClientIpHeaderSigner;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AssistantClientIpResolver {

    private final String signingSecret;

    public AssistantClientIpResolver(
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
