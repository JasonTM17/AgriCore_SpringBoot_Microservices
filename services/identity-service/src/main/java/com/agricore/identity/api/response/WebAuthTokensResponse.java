package com.agricore.identity.api.response;

/**
 * Browser auth response: access token only. Refresh credential stays in HttpOnly cookie.
 */
public record WebAuthTokensResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    public static WebAuthTokensResponse from(AuthTokensResponse tokens) {
        return new WebAuthTokensResponse(
                tokens.accessToken(),
                tokens.tokenType(),
                tokens.expiresIn(),
                tokens.user()
        );
    }
}
