package com.agricore.assistant.application.port;

import com.agricore.assistant.application.model.ToolFact;

import java.util.List;

public interface KnowledgeRetriever {
    List<ToolFact> retrieve(String query);
}
