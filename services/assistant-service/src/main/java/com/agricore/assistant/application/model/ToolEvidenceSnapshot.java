package com.agricore.assistant.application.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record ToolEvidenceSnapshot(List<ToolFact> facts) {

    private static final int MAX_FACTS = 25;
    private static final ToolEvidenceSnapshot EMPTY = new ToolEvidenceSnapshot(List.of());

    public ToolEvidenceSnapshot {
        List<ToolFact> candidateFacts = facts == null ? List.of() : facts;
        if (candidateFacts.size() > MAX_FACTS) {
            throw new IllegalArgumentException("tool evidence must contain at most 25 facts");
        }
        if (candidateFacts.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("tool evidence facts must not be null");
        }
        facts = List.copyOf(candidateFacts);
        Set<String> citationIds = new HashSet<>();
        if (facts.stream().anyMatch(fact -> !citationIds.add(fact.citationId()))) {
            throw new IllegalArgumentException("tool evidence citation ids must be unique");
        }
    }

    public static ToolEvidenceSnapshot empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return facts.isEmpty();
    }
}
