package com.agricore.identity.infrastructure.security;

import com.agricore.identity.infrastructure.configuration.SecurityProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * Loads RSA keys from files or generates an ephemeral pair for local development.
 * Private keys must never be committed to git.
 */
@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

    private final SecurityProperties properties;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String keyId;

    public RsaKeyProvider(SecurityProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() throws Exception {
        if (StringUtils.hasText(properties.privateKeyPath()) && StringUtils.hasText(properties.publicKeyPath())) {
            this.privateKey = loadPrivateKey(Path.of(properties.privateKeyPath()));
            this.publicKey = loadPublicKey(Path.of(properties.publicKeyPath()));
            this.keyId = "file-key";
            log.info("Loaded RSA JWT keys from configured paths (kid={})", keyId);
        } else {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            this.privateKey = (RSAPrivateKey) pair.getPrivate();
            this.publicKey = (RSAPublicKey) pair.getPublic();
            this.keyId = "ephemeral-" + UUID.randomUUID().toString().substring(0, 8);
            log.warn("Generated ephemeral RSA JWT keypair for development (kid={}). Configure key paths for production.", keyId);
        }
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public String keyId() {
        return keyId;
    }

    private static RSAPrivateKey loadPrivateKey(Path path) throws Exception {
        byte[] decoded = decodePem(Files.readString(path), "PRIVATE KEY");
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private static RSAPublicKey loadPublicKey(Path path) throws Exception {
        byte[] decoded = decodePem(Files.readString(path), "PUBLIC KEY");
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    private static byte[] decodePem(String pem, String type) {
        String normalized = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }
}
