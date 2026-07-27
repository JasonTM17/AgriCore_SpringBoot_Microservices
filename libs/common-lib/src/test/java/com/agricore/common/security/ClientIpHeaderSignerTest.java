package com.agricore.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ClientIpHeaderSignerTest {

    private static final String SECRET = "test-client-ip-signing-secret";
    private static final String IDENTITY_AUDIENCE = ClientIpHeaderSigner.IDENTITY_SERVICE_AUDIENCE;
    private static final String ASSISTANT_AUDIENCE = ClientIpHeaderSigner.ASSISTANT_SERVICE_AUDIENCE;

    @Test
    void canonicalizesStrictIpv4AndIpv6Literals() {
        assertThat(ClientIpHeaderSigner.canonicalize("203.000.113.010"))
                .contains("203.0.113.10");
        assertThat(ClientIpHeaderSigner.canonicalize("2001:0DB8:0:0:0:0:0:1"))
                .contains("2001:db8::1");
        assertThat(ClientIpHeaderSigner.canonicalize("::ffff:192.0.2.128"))
                .contains("::ffff:c000:280");
    }

    @Test
    void rejectsNonLiteralOrAmbiguousAddresses() {
        assertThat(ClientIpHeaderSigner.canonicalize("client.example.test")).isEmpty();
        assertThat(ClientIpHeaderSigner.canonicalize("203.0.113.10:443")).isEmpty();
        assertThat(ClientIpHeaderSigner.canonicalize("[2001:db8::1]")).isEmpty();
        assertThat(ClientIpHeaderSigner.canonicalize("fe80::1%eth0")).isEmpty();
    }

    @Test
    void verifiesOnlyTheCanonicalIpAndMatchingAudienceSignature() {
        String signature = ClientIpHeaderSigner.sign("203.000.113.010", IDENTITY_AUDIENCE, SECRET);

        assertThat(ClientIpHeaderSigner.verify("203.0.113.10", signature, IDENTITY_AUDIENCE, SECRET))
                .contains("203.0.113.10");
        assertThat(ClientIpHeaderSigner.verify("203.0.113.11", signature, IDENTITY_AUDIENCE, SECRET)).isEmpty();
        assertThat(ClientIpHeaderSigner.verify("203.0.113.10", signature + "x", IDENTITY_AUDIENCE, SECRET))
                .isEmpty();
        assertThat(ClientIpHeaderSigner.verify("203.0.113.10", signature, ASSISTANT_AUDIENCE, SECRET)).isEmpty();
    }

    @Test
    void rejectsBlankOrUnnormalizedAudiences() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClientIpHeaderSigner.sign("203.0.113.10", " ", SECRET))
                .withMessageContaining("audience");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ClientIpHeaderSigner.sign("203.0.113.10", " identity-service", SECRET))
                .withMessageContaining("audience");
    }
}
