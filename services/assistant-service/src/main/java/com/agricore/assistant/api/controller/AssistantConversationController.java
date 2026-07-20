package com.agricore.assistant.api.controller;

import com.agricore.assistant.api.request.CreateConversationRequest;
import com.agricore.assistant.api.response.ConversationResponse;
import com.agricore.assistant.api.response.MessageResponse;
import com.agricore.assistant.api.security.CurrentAssistantActorResolver;
import com.agricore.assistant.application.model.PageQuery;
import com.agricore.assistant.application.model.PageResult;
import com.agricore.assistant.application.service.ConversationApplicationService;
import com.agricore.assistant.domain.model.AssistantConversation;
import com.agricore.assistant.domain.model.AssistantMessage;
import com.agricore.assistant.domain.model.ConversationStatus;
import com.agricore.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assistant/conversations")
@Validated
public class AssistantConversationController {

    private final ConversationApplicationService service;
    private final CurrentAssistantActorResolver actorResolver;

    public AssistantConversationController(
            ConversationApplicationService service,
            CurrentAssistantActorResolver actorResolver
    ) {
        this.service = service;
        this.actorResolver = actorResolver;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationResponse> create(
            @Valid @RequestBody CreateConversationRequest request,
            Authentication authentication
    ) {
        AssistantConversation conversation = service.create(
                actorResolver.resolve(authentication),
                request.toCommand()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversationResponse.from(conversation));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ConversationResponse> list(
            @RequestParam(required = false) ConversationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        PageResult<AssistantConversation> result = service.list(
                actorResolver.resolve(authentication),
                status,
                new PageQuery(page, size)
        );
        return PageResponse.of(
                result.content().stream().map(ConversationResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements()
        );
    }

    @GetMapping("/{conversationId}")
    @PreAuthorize("isAuthenticated()")
    public ConversationResponse get(
            @PathVariable UUID conversationId,
            Authentication authentication
    ) {
        return ConversationResponse.from(service.get(
                actorResolver.resolve(authentication),
                conversationId
        ));
    }

    @PostMapping("/{conversationId}/archive")
    @PreAuthorize("isAuthenticated()")
    public ConversationResponse archive(
            @PathVariable UUID conversationId,
            Authentication authentication
    ) {
        return ConversationResponse.from(service.archive(
                actorResolver.resolve(authentication),
                conversationId
        ));
    }

    @GetMapping("/{conversationId}/messages")
    @PreAuthorize("isAuthenticated()")
    public PageResponse<MessageResponse> messages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        PageResult<AssistantMessage> result = service.messages(
                actorResolver.resolve(authentication),
                conversationId,
                new PageQuery(page, size)
        );
        return PageResponse.of(
                result.content().stream().map(MessageResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements()
        );
    }
}
