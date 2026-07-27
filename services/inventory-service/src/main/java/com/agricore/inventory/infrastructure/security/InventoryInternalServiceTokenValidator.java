package com.agricore.inventory.infrastructure.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class InventoryInternalServiceTokenValidator {

    private final InventoryInternalSecurityProperties properties;

    public InventoryInternalServiceTokenValidator(InventoryInternalSecurityProperties properties) {
        this.properties = properties;
    }

    public boolean matches(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank() || properties.getServiceToken().isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                properties.getServiceToken().getBytes(StandardCharsets.UTF_8),
                presentedToken.trim().getBytes(StandardCharsets.UTF_8)
        );
    }
}
