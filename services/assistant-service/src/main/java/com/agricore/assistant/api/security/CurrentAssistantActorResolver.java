package com.agricore.assistant.api.security;

import com.agricore.assistant.domain.exception.AssistantException;
import com.agricore.assistant.domain.model.AssistantActor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentAssistantActorResolver {

    public AssistantActor resolve(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw AssistantException.invalidActorSubject();
        }

        UUID subject;
        try {
            subject = UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw AssistantException.invalidActorSubject();
        }

        return new AssistantActor(
                subject,
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .toList()
        );
    }
}
