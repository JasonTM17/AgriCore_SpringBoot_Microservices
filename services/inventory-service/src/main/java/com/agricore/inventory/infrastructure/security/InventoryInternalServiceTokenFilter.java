package com.agricore.inventory.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects unauthenticated internal Inventory traffic before MVC parses or
 * validates request data. The controller repeats the check as defense in depth.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InventoryInternalServiceTokenFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/api/v1/inventory/";
    private static final String TOKEN_HEADER = "X-Internal-Service-Token";

    private final InventoryInternalServiceTokenValidator tokenValidator;

    public InventoryInternalServiceTokenFilter(
            InventoryInternalServiceTokenValidator tokenValidator
    ) {
        this.tokenValidator = tokenValidator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!tokenValidator.matches(request.getHeader(TOKEN_HEADER))) {
            response.sendError(
                    HttpStatus.FORBIDDEN.value(),
                    "Internal service authentication required"
            );
            return;
        }
        filterChain.doFilter(request, response);
    }
}
