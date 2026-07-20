package com.agricore.assistant.api.controller;

import com.agricore.assistant.api.security.CurrentAssistantActorResolver;
import com.agricore.assistant.api.streaming.GenerationEventCursorResolver;
import com.agricore.assistant.api.streaming.GenerationEventStreamService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assistant/conversations")
@Validated
public class GenerationEventStreamController {

    private static final String LAST_EVENT_ID = "Last-Event-ID";

    private final GenerationEventStreamService streamService;
    private final GenerationEventCursorResolver cursorResolver;
    private final CurrentAssistantActorResolver actorResolver;

    public GenerationEventStreamController(
            GenerationEventStreamService streamService,
            GenerationEventCursorResolver cursorResolver,
            CurrentAssistantActorResolver actorResolver
    ) {
        this.streamService = streamService;
        this.cursorResolver = cursorResolver;
        this.actorResolver = actorResolver;
    }

    @GetMapping(
            value = "/{conversationId}/generations/{generationId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream(
            @PathVariable UUID conversationId,
            @PathVariable UUID generationId,
            @RequestParam(name = "after", defaultValue = "-1") @Min(-1) long after,
            @RequestHeader(name = LAST_EVENT_ID, required = false) String lastEventId,
            Authentication authentication,
            HttpServletResponse response
    ) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store");
        response.setHeader("X-Accel-Buffering", "no");
        long cursor = cursorResolver.resolve(after, lastEventId);
        return streamService.open(
                actorResolver.resolve(authentication), conversationId, generationId, cursor);
    }
}
