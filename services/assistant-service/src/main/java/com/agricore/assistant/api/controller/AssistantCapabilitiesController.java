package com.agricore.assistant.api.controller;

import com.agricore.assistant.api.response.AssistantCapabilitiesResponse;
import com.agricore.assistant.application.port.ChatProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantCapabilitiesController {

    private final ChatProvider chatProvider;

    public AssistantCapabilitiesController(ChatProvider chatProvider) {
        this.chatProvider = chatProvider;
    }

    @GetMapping("/capabilities")
    public AssistantCapabilitiesResponse getCapabilities() {
        return AssistantCapabilitiesResponse.from(chatProvider.capabilities());
    }
}
