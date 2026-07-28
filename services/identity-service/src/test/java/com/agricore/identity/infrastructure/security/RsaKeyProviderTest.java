package com.agricore.identity.infrastructure.security;

import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class RsaKeyProviderTest {

    @Test
    void init_loadsConfiguredTemporaryPemKeyPair(@TempDir Path temporaryDirectory) throws Exception {
        KeyPair configuredPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        Path privateKeyPath = temporaryDirectory.resolve("test-private.pem");
        Path publicKeyPath = temporaryDirectory.resolve("test-public.pem");
        Files.writeString(privateKeyPath, pem("PRIVATE KEY", configuredPair.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
        Files.writeString(publicKeyPath, pem("PUBLIC KEY", configuredPair.getPublic().getEncoded()), StandardCharsets.US_ASCII);
        RsaKeyProvider provider = new RsaKeyProvider(properties(privateKeyPath.toString(), publicKeyPath.toString()));

        provider.init();

        assertThat(provider.keyId()).isEqualTo("file-key");
        assertThat(provider.privateKey().getEncoded()).containsExactly(configuredPair.getPrivate().getEncoded());
        assertThat(provider.publicKey().getEncoded()).containsExactly(configuredPair.getPublic().getEncoded());
    }

    @Test
    void init_generatesEphemeralPairWhenBothKeyPathsAreBlank() throws Exception {
        RsaKeyProvider provider = new RsaKeyProvider(properties("", ""));

        provider.init();

        assertThat(provider.keyId()).startsWith("ephemeral-");
        assertThat(provider.privateKey().getModulus()).isEqualTo(provider.publicKey().getModulus());
    }

    private static String pem(String type, byte[] encodedKey) {
        String body = Base64.getMimeEncoder(64, new byte[]{0x0A}).encodeToString(encodedKey);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    private static SecurityProperties properties(String privateKeyPath, String publicKeyPath) {
        return new SecurityProperties(
                "https://agricore.test/identity",
                "agricore-api",
                300L,
                3600L,
                4,
                5,
                15,
                20,
                privateKeyPath,
                publicKeyPath,
                true,
                false,
                "agricore_refresh",
                "/api/v1/auth/web",
                false,
                "Strict",
                "http://localhost:5173"
        );
    }
}
