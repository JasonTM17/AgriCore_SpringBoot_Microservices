package com.agricore.assistant.application.port;

import com.agricore.assistant.application.model.ToolEvidenceCollection;
import com.agricore.assistant.domain.model.AssistantConversation;

public interface ToolEvidenceCollector {
    ToolEvidenceCollection collect(AssistantConversation conversation, String prompt);
}
