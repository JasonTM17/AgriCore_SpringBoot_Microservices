package com.agricore.assistant.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agricore.assistant.provider")
public class AssistantProviderProperties {

    private ProviderType type = ProviderType.NONE;

    public ProviderType getType() {
        return type;
    }

    public void setType(ProviderType type) {
        this.type = type == null ? ProviderType.NONE : type;
    }

    public enum ProviderType {
        NONE("none"),
        OPENAI("openai"),
        OLLAMA("ollama");

        private final String externalName;

        ProviderType(String externalName) {
            this.externalName = externalName;
        }

        public String externalName() {
            return externalName;
        }
    }
}
