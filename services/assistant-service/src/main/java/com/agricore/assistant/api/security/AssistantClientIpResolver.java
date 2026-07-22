package com.agricore.assistant.api.security;

import com.agricore.assistant.infrastructure.configuration.AssistantBudgetProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;

@Component
public class AssistantClientIpResolver {

    private final AssistantBudgetProperties properties;

    public AssistantClientIpResolver(AssistantBudgetProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        if (properties.isTrustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String firstHop = forwarded.split(",", 2)[0].strip();
                String normalized = normalizeIpLiteral(firstHop);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.strip();
    }

    private static String normalizeIpLiteral(String value) {
        String ipv4 = normalizeIpv4Literal(value);
        if (ipv4 != null) {
            return ipv4;
        }
        if (value == null || !value.contains(":") || !value.matches("[0-9A-Fa-f:]{2,45}")) {
            return null;
        }
        try {
            InetAddress address = InetAddress.getByName(value);
            return address instanceof Inet6Address
                    ? address.getHostAddress().toLowerCase(Locale.ROOT)
                    : null;
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private static String normalizeIpv4Literal(String value) {
        if (value == null || !value.matches("[0-9.]{7,15}")) {
            return null;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] octets = new int[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            octets[index] = Integer.parseInt(part);
            if (octets[index] > 255) {
                return null;
            }
        }
        return "%d.%d.%d.%d".formatted(octets[0], octets[1], octets[2], octets[3]);
    }
}
