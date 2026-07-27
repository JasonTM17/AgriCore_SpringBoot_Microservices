import { Button } from "../../components/ui/button";
import type { AssistantGenerationControllerState } from "./assistant-generation-controller-types";
import { isTerminalAssistantStatus } from "./assistant-generation-projection";
import { assistantFailureKind, assistantFailureMessage } from "./assistant-error-policy";
import { AssistantErrorNotice } from "./assistant-error-notice";

const PHASE_LABELS: Readonly<Record<AssistantGenerationControllerState["phase"], string>> = {
  IDLE: "Sẵn sàng",
  SUBMITTING: "Đang gửi câu hỏi",
  SUBMIT_FAILED: "Chưa gửi được câu hỏi",
  RECONCILING: "Đang đồng bộ tiến trình",
  CONNECTING: "Đang mở kết nối trực tiếp",
  RECONNECTING: "Đang nối lại kết nối",
  LIVE: "Đang nhận phản hồi",
  TERMINAL: "Đã kết thúc",
  FAILED: "Mất kết nối phản hồi",
  DETACHED: "Đã tách kết nối",
};
const STATUS_LABELS = {
  QUEUED: "Đang xếp hàng",
  RUNNING: "Đang xử lý",
  CANCEL_REQUESTED: "Đang yêu cầu dừng",
  COMPLETED: "Đã hoàn tất",
  FAILED: "Xử lý thất bại",
  CANCELLED: "Đã hủy",
} as const;
const FAILURE_KIND_LABELS = {
  LIMITED: "Đã chạm hạn mức",
  REFUSED: "Đã từ chối an toàn",
  UNAVAILABLE: "Tạm thời không khả dụng",
  ERROR: "Không hoàn tất",
} as const;

const ACTIVITY_LABELS: Readonly<Record<AssistantGenerationControllerState["phase"], string>> = {
  IDLE: "",
  SUBMITTING: "Đang kiểm tra phạm vi và dữ liệu được cấp quyền",
  SUBMIT_FAILED: "",
  RECONCILING: "Đang đồng bộ tiến trình bền vững",
  CONNECTING: "Đang mở luồng phản hồi",
  RECONNECTING: "Đang nối lại luồng phản hồi",
  LIVE: "Đang nhận phản hồi theo thứ tự",
  TERMINAL: "",
  FAILED: "",
  DETACHED: "",
};

interface AssistantGenerationStatusProps {
  state: AssistantGenerationControllerState;
  onCancel: () => void;
  onRetryConnection: () => void;
  onRetrySubmission: () => void;
}

export function AssistantGenerationStatus({
  state,
  onCancel,
  onRetryConnection,
  onRetrySubmission,
}: AssistantGenerationStatusProps) {
  const projection = state.projection;
  const active = projection !== null && !isTerminalAssistantStatus(projection.status);
  const operationError = state.submissionError ?? state.cancellationError;
  const failureMessage = assistantFailureMessage(
    projection?.errorCode ?? state.syncErrorCode,
  );
  const failureKind = assistantFailureKind(projection?.errorCode ?? state.syncErrorCode);
  const activityLabel = ACTIVITY_LABELS[state.phase];
  const showStatus = state.phase !== "IDLE" || projection !== null;

  if (!showStatus && !operationError) return null;
  return (
    <section className="space-y-3 border-t border-border px-4 py-3" aria-label="Trạng thái phản hồi">
      {showStatus ? (
        <div className="flex flex-wrap items-center justify-between gap-3" aria-live="polite">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="rounded-full bg-forest-50 px-2.5 py-1 font-semibold text-forest-900">
              {projection ? STATUS_LABELS[projection.status] : PHASE_LABELS[state.phase]}
            </span>
            {projection && state.phase !== "TERMINAL" ? (
              <span className="text-muted">{PHASE_LABELS[state.phase]}</span>
            ) : null}
            {failureKind ? (
              <span className="rounded-full border border-danger/25 bg-red-50 px-2.5 py-1 text-xs font-semibold text-danger">
                {FAILURE_KIND_LABELS[failureKind]}
              </span>
            ) : null}
          </div>
          <div className="flex flex-wrap gap-2">
            {state.phase === "SUBMIT_FAILED" ? (
              <Button variant="secondary" onClick={onRetrySubmission} disabled={state.isSubmitting}>
                Gửi lại
              </Button>
            ) : null}
            {(state.phase === "FAILED" || state.phase === "DETACHED") && active ? (
              <Button variant="secondary" onClick={onRetryConnection}>Nối lại</Button>
            ) : null}
            {active && projection.status !== "CANCEL_REQUESTED" ? (
              <Button variant="danger" onClick={onCancel} disabled={state.isCancelling}>
                {state.isCancelling ? "Đang dừng…" : "Dừng phản hồi"}
              </Button>
            ) : null}
          </div>
        </div>
      ) : null}
      {activityLabel ? (
        <p className="text-xs text-muted" role="status" aria-live="polite">
          {activityLabel}
        </p>
      ) : null}
      {state.recovery ? (
        <p className="text-xs text-warning" role="status">
          Lần nối lại {state.recovery.attempt}; thử tiếp sau {state.recovery.delayMs} ms.
        </p>
      ) : null}
      {failureMessage ? (
        <p className="rounded-control bg-red-50 px-3 py-2 text-sm text-danger" role="alert">
          {failureMessage}
        </p>
      ) : null}
      {operationError ? <AssistantErrorNotice error={operationError} /> : null}
    </section>
  );
}
