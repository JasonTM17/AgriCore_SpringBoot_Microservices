package com.agricore.work.application.service;

import com.agricore.work.domain.exception.WorkException;

final class AuthenticatedActor {

    private static final int MAX_LENGTH = 255;

    private AuthenticatedActor() {
    }

    static String requireValid(String actor) {
        String normalized = actor == null ? "" : actor.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_LENGTH) {
            throw new WorkException("INVALID_ACTOR", "Authenticated actor is invalid", 400);
        }
        return normalized;
    }
}
