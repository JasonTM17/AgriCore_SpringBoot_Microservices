package com.agricore.assistant.api.controller;

import com.agricore.assistant.api.response.AssistantDtos.CapabilitiesResponse;
import com.agricore.assistant.api.response.AssistantDtos.ConversationResponse;
import com.agricore.assistant.api.response.AssistantDtos.CreateConversationRequest;
import com.agricore.assistant.api.response.AssistantDtos.MessageResponse;
import com.agricore.assistant.api.response.AssistantDtos.StartGenerationRequest;
import com.agricore.assistant.api.response.AssistantDtos.StartGenerationResponse;
import com.agricore.assistant.application.AssistantApplicationService;
import com.agricore.assistant.infrastructure.persistence.entity.GenerationEventEntity;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantApplicationService assistantService;
    private final ExecutorService sseWorkers = Executors.newCachedThreadPool();

    public AssistantController(AssistantApplicationService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping("/capabilities")
    public CapabilitiesResponse capabilities() {
        return assistantService.capabilities();
    }

    @GetMapping("/conversations")
    public List<ConversationResponse> listConversations(Authentication authentication) {
        return assistantService.listConversations(userId(authentication));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> createConversation(
            Authentication authentication,
            @RequestBody(required = false) CreateConversationRequest request
    ) {
        CreateConversationRequest body = request == null ? new CreateConversationRequest(null, null) : request;
        ConversationResponse created = assistantService.createConversation(
                userId(authentication),
                roles(authentication),
                body.title(),
                body.farmId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<MessageResponse> listMessages(
            Authentication authentication,
            @PathVariable UUID conversationId
    ) {
        return assistantService.listMessages(userId(authentication), conversationId);
    }

    @PostMapping("/conversations/{conversationId}/generations")
    public StartGenerationResponse startGeneration(
            Authentication authentication,
            @PathVariable UUID conversationId,
            @Valid @RequestBody StartGenerationRequest request
    ) {
        return assistantService.startGeneration(
                userId(authentication),
                roles(authentication),
                conversationId,
                request.content(),
                request.idempotencyKey()
        );
    }

    @GetMapping(path = "/conversations/{conversationId}/generations/{generationId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(
            Authentication authentication,
            @PathVariable UUID conversationId,
            @PathVariable UUID generationId,
            @RequestParam(defaultValue = "-1") long after
    ) {
        UUID ownerId = userId(authentication);
        // Ownership checks
        assistantService.requireOwnedGeneration(ownerId, generationId);

        SseEmitter emitter = new SseEmitter(0L);
        sseWorkers.submit(() -> {
            long cursor = after;
            try {
                while (true) {
                    List<GenerationEventEntity> batch =
                            assistantService.eventsAfter(ownerId, generationId, cursor);
                    for (GenerationEventEntity event : batch) {
                        emitter.send(SseEmitter.event()
                                .id(String.valueOf(event.getSequenceNo()))
                                .name(event.getEventType())
                                .data(event.getPayload()));
                        cursor = event.getSequenceNo();
                        if ("completed".equals(event.getEventType()) || "error".equals(event.getEventType())) {
                            emitter.complete();
                            return;
                        }
                    }
                    String status = assistantService.requireOwnedGeneration(ownerId, generationId).getStatus();
                    if ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
                        if (batch.isEmpty()) {
                            emitter.send(SseEmitter.event().name("status").data(
                                    "{\"type\":\"status\",\"status\":\"" + status + "\"}"));
                        }
                        emitter.complete();
                        return;
                    }
                    Thread.sleep(250);
                }
            } catch (IOException | InterruptedException ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private static UUID userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        return UUID.fromString(authentication.getName());
    }

    private static List<String> roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .toList();
    }
}
