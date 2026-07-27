package com.agricore.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import java.util.UUID;

/**
 * Supplies ephemeral request attributes without persisting a session or cookie. A request matched
 * by the domain CSRF policy can therefore never authenticate through ambient cookie state.
 */
final class StatelessRejectingCsrfTokenRepository implements CsrfTokenRepository {

    static final String HEADER_NAME = "X-AGRICORE-DOMAIN-XSRF-TOKEN";
    static final String PARAMETER_NAME = "_agricore_domain_xsrf";

    @Override
    public CsrfToken generateToken(HttpServletRequest request) {
        return new DefaultCsrfToken(HEADER_NAME, PARAMETER_NAME, UUID.randomUUID().toString());
    }

    @Override
    public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
        // Domain services deliberately persist no browser credential or CSRF state.
    }

    @Override
    public CsrfToken loadToken(HttpServletRequest request) {
        return null;
    }
}
