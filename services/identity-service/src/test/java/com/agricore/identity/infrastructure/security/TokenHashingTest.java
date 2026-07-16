package com.agricore.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHashingTest {

    @Test
    void generateOpaqueToken_isUniqueAndHashable() {
        String a = TokenHashing.generateOpaqueToken();
        String b = TokenHashing.generateOpaqueToken();
        assertThat(a).isNotBlank().isNotEqualTo(b);
        assertThat(TokenHashing.sha256Hex(a)).hasSize(64);
        assertThat(TokenHashing.sha256Hex(a)).isEqualTo(TokenHashing.sha256Hex(a));
    }
}
