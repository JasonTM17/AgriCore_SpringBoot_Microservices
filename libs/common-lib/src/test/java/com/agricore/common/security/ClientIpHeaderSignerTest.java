package com.agricore.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpHeaderSignerTest {

    private static final String SECRET = "test-client-ip-signing-secret";

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
    void verifiesOnlyTheCanonicalIpAndMatchingSignature() {
        String signature = ClientIpHeaderSigner.sign("203.000.113.010", SECRET);

        assertThat(ClientIpHeaderSigner.verify("203.0.113.10", signature, SECRET))
                .contains("203.0.113.10");
        assertThat(ClientIpHeaderSigner.verify("203.0.113.11", signature, SECRET)).isEmpty();
        assertThat(ClientIpHeaderSigner.verify("203.0.113.10", signature + "x", SECRET)).isEmpty();
    }
}
