package com.agricore.assistant.infrastructure.safety;

import com.agricore.assistant.application.model.OutputSafetyAssessment;
import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.port.AssistantOutputSafetyPolicy;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DeterministicAssistantOutputSafetyPolicy implements AssistantOutputSafetyPolicy {

    private static final Pattern CITATION = Pattern.compile(
            "\\[([A-Z][A-Z0-9]{0,15}-[A-Z0-9][A-Z0-9-]{0,15})]"
    );
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)(?:\\bsk-[A-Za-z0-9_-]{16,}"
                    + "|\\b(?:authorization|bearer)\\s*(?::|=)?\\s+[A-Za-z0-9._~+/=-]{16,}"
                    + "|\\b(?:api|access|refresh)[_-]?key\\s*(?::|=)\\s*[^\\s]{16,}"
                    + "|\\beyJ[A-Za-z0-9_-]{8,}\\.eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"
                    + "|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)"
    );
    private static final Set<String> FORBIDDEN_MARKERS = Set.of(
            "untrusted_tool_data_jsonl_begin",
            "untrusted_tool_data_jsonl_end",
            "<tool_call",
            "</tool_call",
            "\"tool_calls\"",
            "\"function_call\"",
            "<function_call",
            "</function_call"
    );

    @Override
    public OutputSafetyAssessment evaluatePartial(
            String accumulatedContent,
            ToolEvidenceSnapshot evidence
    ) {
        return evaluate(accumulatedContent, evidence, false);
    }

    @Override
    public OutputSafetyAssessment evaluateFinal(
            String completedContent,
            ToolEvidenceSnapshot evidence
    ) {
        return evaluate(completedContent, evidence, true);
    }

    private OutputSafetyAssessment evaluate(
            String content,
            ToolEvidenceSnapshot evidence,
            boolean finalAssessment
    ) {
        if (content == null || hasUnsafeControls(content)) {
            return OutputSafetyAssessment.deny("AI_OUTPUT_UNSAFE_CONTROL");
        }
        if (SENSITIVE_VALUE.matcher(content).find()) {
            return OutputSafetyAssessment.deny("AI_OUTPUT_SENSITIVE_DATA");
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_MARKERS.stream().anyMatch(normalized::contains)) {
            return OutputSafetyAssessment.deny("AI_OUTPUT_POLICY_VIOLATION");
        }

        ToolEvidenceSnapshot snapshot = evidence == null ? ToolEvidenceSnapshot.empty() : evidence;
        Set<String> allowedCitations = snapshot.facts().stream()
                .map(fact -> fact.citationId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> observedCitations = citations(content);
        if (!allowedCitations.containsAll(observedCitations)) {
            return OutputSafetyAssessment.deny("AI_OUTPUT_CITATION_UNAUTHORIZED");
        }
        if (finalAssessment && !snapshot.isEmpty() && observedCitations.isEmpty()) {
            return OutputSafetyAssessment.deny("AI_OUTPUT_CITATION_REQUIRED");
        }
        return OutputSafetyAssessment.allow();
    }

    private static Set<String> citations(String content) {
        Set<String> citations = new HashSet<>();
        Matcher matcher = CITATION.matcher(content);
        while (matcher.find()) {
            citations.add(matcher.group(1));
        }
        return citations;
    }

    private static boolean hasUnsafeControls(String content) {
        return content.codePoints().anyMatch(codePoint -> {
            if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
                return false;
            }
            return Character.isISOControl(codePoint)
                    || isDisallowedFormat(codePoint);
        });
    }

    private static boolean isDisallowedFormat(int codePoint) {
        if (Character.getType(codePoint) != Character.FORMAT) {
            return false;
        }
        return codePoint != 0x200C
                && codePoint != 0x200D
                && !(codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                && !(codePoint >= 0xE0100 && codePoint <= 0xE01EF);
    }
}
