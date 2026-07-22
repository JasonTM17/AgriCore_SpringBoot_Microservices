package com.agricore.assistant.infrastructure.safety;

import com.agricore.assistant.application.model.OutputSafetyAssessment;
import com.agricore.assistant.application.model.ToolEvidenceSnapshot;
import com.agricore.assistant.application.model.ToolFact;
import com.agricore.assistant.application.model.ToolSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicAssistantOutputSafetyPolicyTest {

    private final DeterministicAssistantOutputSafetyPolicy policy =
            new DeterministicAssistantOutputSafetyPolicy();
    private final ToolEvidenceSnapshot evidence = new ToolEvidenceSnapshot(List.of(
            new ToolFact("FARM-1", ToolSource.FARM, Map.of("status", "ACTIVE"))
    ));

    @Test
    void permitsGeneralGuidanceAndAuthorizedCitations() {
        assertThat(policy.evaluateFinal(
                "Kiểm tra độ ẩm đất trước khi tưới.", ToolEvidenceSnapshot.empty()).permitted())
                .isTrue();
        assertThat(policy.evaluateFinal(
                "Trang trại đang hoạt động [FARM-1].", evidence).permitted())
                .isTrue();
        assertThat(policy.evaluateFinal(
                "Bearer authentication uses an Authorization header.",
                ToolEvidenceSnapshot.empty()).permitted())
                .isTrue();
        assertThat(policy.evaluateFinal(
                "Cây 🌱‍🌾 đang phát triển.",
                ToolEvidenceSnapshot.empty()).permitted())
                .isTrue();
    }

    @Test
    void requiresAndAllowlistsToolBackedCitations() {
        assertDenied(
                policy.evaluateFinal("Trang trại đang hoạt động.", evidence),
                "AI_OUTPUT_CITATION_REQUIRED"
        );
        assertDenied(
                policy.evaluatePartial("Thông tin lô đất [PLOT-9].", evidence),
                "AI_OUTPUT_CITATION_UNAUTHORIZED"
        );
        assertDenied(
                policy.evaluateFinal("Thông tin lô đất [PLOT-9].", ToolEvidenceSnapshot.empty()),
                "AI_OUTPUT_CITATION_UNAUTHORIZED"
        );
    }

    @Test
    void rejectsSensitiveValuesUnsafeControlsAndProviderToolCalls() {
        assertDenied(
                policy.evaluatePartial(
                        "Authorization: Bearer " + "abcdefgh12345678", evidence),
                "AI_OUTPUT_SENSITIVE_DATA"
        );
        assertDenied(
                policy.evaluatePartial("secret " + "sk-" + "abcdefgh12345678", evidence),
                "AI_OUTPUT_SENSITIVE_DATA"
        );
        assertDenied(
                policy.evaluatePartial("<tool_call>{}</tool_call>", evidence),
                "AI_OUTPUT_POLICY_VIOLATION"
        );
        assertDenied(
                policy.evaluatePartial("safe\u202Eunsafe", evidence),
                "AI_OUTPUT_UNSAFE_CONTROL"
        );
    }

    private static void assertDenied(OutputSafetyAssessment assessment, String reasonCode) {
        assertThat(assessment.permitted()).isFalse();
        assertThat(assessment.reasonCode()).isEqualTo(reasonCode);
    }
}
