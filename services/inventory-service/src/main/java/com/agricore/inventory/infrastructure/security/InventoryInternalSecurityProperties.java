package com.agricore.inventory.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agricore.inventory.internal")
public class InventoryInternalSecurityProperties {

    private String serviceToken = "";

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken == null ? "" : serviceToken.trim();
    }
}
