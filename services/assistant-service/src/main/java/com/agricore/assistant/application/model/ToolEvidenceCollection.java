package com.agricore.assistant.application.model;

import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

public record ToolEvidenceCollection(
        ToolEvidenceSnapshot evidence,
        ToolCollectionOutcome outcome,
        String reasonCode,
        long latencyMs
) {

    private static final String REASON_PATTERN = "[A-Z][A-Z0-9_]{0,63}";

    public ToolEvidenceCollection {
        evidence = evidence == null ? ToolEvidenceSnapshot.empty() : evidence;
        outcome = Objects.requireNonNull(outcome, "tool collection outcome is required");
        reasonCode = reasonCode == null || reasonCode.isBlank() ? null : reasonCode.strip();
        if (latencyMs < 0 || latencyMs > 60_000) {
            throw new IllegalArgumentException("tool collection latency must be between 0 and 60000 ms");
        }
        if (outcome == ToolCollectionOutcome.COLLECTED) {
            if (evidence.isEmpty() || reasonCode != null) {
                throw new IllegalArgumentException("collected tool evidence must contain facts without a reason");
            }
        } else if (outcome == ToolCollectionOutcome.PARTIAL) {
            if (evidence.isEmpty() || reasonCode == null || !reasonCode.matches(REASON_PATTERN)) {
                throw new IllegalArgumentException("partial tool evidence requires facts and a safe reason");
            }
        } else if (!evidence.isEmpty() || reasonCode == null || !reasonCode.matches(REASON_PATTERN)) {
            throw new IllegalArgumentException("non-collected tool evidence requires an empty snapshot and safe reason");
        }
    }

    public static ToolEvidenceCollection collected(ToolEvidenceSnapshot evidence, long latencyMs) {
        return new ToolEvidenceCollection(evidence, ToolCollectionOutcome.COLLECTED, null, latencyMs);
    }

    public static ToolEvidenceCollection partial(
            ToolEvidenceSnapshot evidence,
            String reasonCode,
            long latencyMs
    ) {
        return new ToolEvidenceCollection(
                evidence, ToolCollectionOutcome.PARTIAL, reasonCode, latencyMs
        );
    }

    public static ToolEvidenceCollection skipped(String reasonCode) {
        return new ToolEvidenceCollection(
                ToolEvidenceSnapshot.empty(), ToolCollectionOutcome.SKIPPED, reasonCode, 0
        );
    }

    public static ToolEvidenceCollection unavailable(String reasonCode, long latencyMs) {
        return new ToolEvidenceCollection(
                ToolEvidenceSnapshot.empty(), ToolCollectionOutcome.UNAVAILABLE, reasonCode, latencyMs
        );
    }

    public static ToolEvidenceCollection denied(String reasonCode, long latencyMs) {
        return new ToolEvidenceCollection(
                ToolEvidenceSnapshot.empty(), ToolCollectionOutcome.DENIED, reasonCode, latencyMs
        );
    }

    public String auditMetadata() {
        String sources = evidence.facts().stream()
                .map(fact -> fact.source().name())
                .collect(Collectors.toCollection(TreeSet::new))
                .stream().map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(","));
        String fields = evidence.facts().stream()
                .flatMap(fact -> fact.fields().keySet().stream())
                .collect(Collectors.toCollection(TreeSet::new))
                .stream().map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(","));
        return "{\"outcome\":\"%s\",\"latencyMs\":%d,\"factCount\":%d,"
                .formatted(outcome, latencyMs, evidence.facts().size())
                + "\"sources\":[" + sources + "],\"egressFields\":[" + fields + "]}";
    }
}
