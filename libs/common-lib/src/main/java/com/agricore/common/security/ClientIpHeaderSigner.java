package com.agricore.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Canonicalizes IP literals and authenticates the Gateway-issued client address header.
 *
 * <p>Only literal IPv4 and IPv6 values are accepted. Host names, ports, bracket notation, and
 * IPv6 scope identifiers are deliberately rejected so signing never performs name resolution.
 */
public final class ClientIpHeaderSigner {

    public static final String CLIENT_IP_HEADER = "X-AgriCore-Client-IP";
    public static final String CLIENT_IP_SIGNATURE_HEADER = "X-AgriCore-Client-IP-Signature";

    private static final String HMAC_SHA_256 = "HmacSHA256";

    private ClientIpHeaderSigner() {
    }

    public static Optional<String> canonicalize(String literal) {
        if (literal == null || literal.isEmpty() || !literal.equals(literal.strip())) {
            return Optional.empty();
        }
        String ipv4 = canonicalIpv4(literal);
        return Optional.ofNullable(ipv4 != null ? ipv4 : canonicalIpv6(literal));
    }

    public static String sign(String clientIp, String signingSecret) {
        String canonicalIp = canonicalize(clientIp)
                .orElseThrow(() -> new IllegalArgumentException("client IP must be a strict IP literal"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(canonicalIp, signingSecret));
    }

    /**
     * Returns the canonical client IP only when its signature verifies in constant time.
     */
    public static Optional<String> verify(String clientIp, String signature, String signingSecret) {
        Optional<String> canonicalIp = canonicalize(clientIp);
        if (canonicalIp.isEmpty() || signature == null || signature.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] supplied = Base64.getUrlDecoder().decode(signature);
            byte[] expected = hmac(canonicalIp.get(), signingSecret);
            return MessageDigest.isEqual(expected, supplied) ? canonicalIp : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static byte[] hmac(String canonicalIp, String signingSecret) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalArgumentException("client IP signing secret must not be blank");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            return mac.doFinal(canonicalIp.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
        }
    }

    private static String canonicalIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] octets = new int[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (!part.matches("[0-9]{1,3}")) {
                return null;
            }
            octets[index] = Integer.parseInt(part);
            if (octets[index] > 255) {
                return null;
            }
        }
        return "%d.%d.%d.%d".formatted(octets[0], octets[1], octets[2], octets[3]);
    }

    private static String canonicalIpv6(String value) {
        int separator = value.indexOf("::");
        boolean compressed = separator >= 0;
        if (value.indexOf('%') >= 0 || (compressed && value.indexOf("::", separator + 2) >= 0)) {
            return null;
        }
        List<String> leftTokens = tokens(compressed ? value.substring(0, separator) : value);
        List<String> rightTokens = tokens(compressed ? value.substring(separator + 2) : "");
        if (leftTokens == null || rightTokens == null) {
            return null;
        }
        List<String> allTokens = new ArrayList<>(leftTokens);
        allTokens.addAll(rightTokens);
        List<Integer> suppliedGroups = new ArrayList<>(8);
        int leftGroupCount = 0;
        for (int index = 0; index < allTokens.size(); index++) {
            String token = allTokens.get(index);
            if (token.indexOf('.') >= 0) {
                if (index != allTokens.size() - 1) {
                    return null;
                }
                String ipv4 = canonicalIpv4(token);
                if (ipv4 == null) {
                    return null;
                }
                String[] octets = ipv4.split("\\.");
                suppliedGroups.add((Integer.parseInt(octets[0]) << 8) | Integer.parseInt(octets[1]));
                suppliedGroups.add((Integer.parseInt(octets[2]) << 8) | Integer.parseInt(octets[3]));
            } else if (token.matches("[0-9A-Fa-f]{1,4}")) {
                suppliedGroups.add(Integer.parseInt(token, 16));
            } else {
                return null;
            }
            if (index + 1 == leftTokens.size()) {
                leftGroupCount = suppliedGroups.size();
            }
        }
        if (compressed ? suppliedGroups.size() >= 8 : suppliedGroups.size() != 8) {
            return null;
        }
        int[] groups = new int[8];
        for (int index = 0; index < leftGroupCount; index++) {
            groups[index] = suppliedGroups.get(index);
        }
        int zeroGroups = groups.length - suppliedGroups.size();
        for (int index = leftGroupCount; index < suppliedGroups.size(); index++) {
            groups[index + zeroGroups] = suppliedGroups.get(index);
        }
        return formatIpv6(groups);
    }

    private static List<String> tokens(String side) {
        if (side.isEmpty()) {
            return List.of();
        }
        String[] tokens = side.split(":", -1);
        for (String token : tokens) {
            if (token.isEmpty()) {
                return null;
            }
        }
        return List.of(tokens);
    }

    private static String formatIpv6(int[] groups) {
        int bestStart = -1;
        int bestLength = 1;
        for (int index = 0; index < groups.length; ) {
            if (groups[index] != 0) {
                index++;
                continue;
            }
            int end = index;
            while (end < groups.length && groups[end] == 0) {
                end++;
            }
            if (end - index > bestLength) {
                bestStart = index;
                bestLength = end - index;
            }
            index = end;
        }
        StringBuilder canonical = new StringBuilder();
        for (int index = 0; index < groups.length; index++) {
            if (index == bestStart) {
                canonical.append("::");
                index += bestLength - 1;
                continue;
            }
            if (!canonical.isEmpty() && canonical.charAt(canonical.length() - 1) != ':') {
                canonical.append(':');
            }
            canonical.append(Integer.toHexString(groups[index]));
        }
        return canonical.toString();
    }
}
