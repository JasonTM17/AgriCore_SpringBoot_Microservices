package com.agricore.cropcycle.application.service;

import com.agricore.cropcycle.domain.exception.CropCycleException;

final class AuthenticatedActor {

    private static final int MAX_LENGTH = 255;

    private AuthenticatedActor() {
    }

    static String requireValid(String actor) {
        String normalized = actor == null ? "" : actor.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH) {
            throw new CropCycleException(
                    "INVALID_AUTHENTICATED_ACTOR",
                    "Authenticated actor subject is invalid",
                    401
            );
        }
        return normalized;
    }
}
