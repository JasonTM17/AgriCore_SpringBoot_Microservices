import { Button } from "../../components/ui/button";
import { ApiClientError } from "../../lib/api/errors";

interface WorkTaskMutationErrorProps {
  actionLabel: string;
  error: Error;
  isRecovering: boolean;
  onRecover: () => void;
}

function mutationMessage(error: Error): string {
  if (!(error instanceof ApiClientError)) {
    return "Yêu cầu không hoàn tất. Kiểm tra kết nối rồi thử lại.";
  }
  if (error.code === "TASK_CODE_EXISTS") {
    return "Mã công việc đã tồn tại. Hãy đổi mã rồi gửi lại.";
  }
  if (error.status === 409) {
    return "Công việc vừa thay đổi hoặc thao tác không còn hợp lệ. Hãy tải lại danh sách trước khi tiếp tục.";
  }
  if (error.status === 403 || error.status === 404) {
    return "Quyền truy cập đã thay đổi hoặc công việc không còn khả dụng.";
  }
  if (error.status === 400 || error.status === 422) {
    return "Dữ liệu chưa hợp lệ. Kiểm tra các trường và thử lại.";
  }
  if (error.status === 503) {
    return "Dịch vụ xác thực phạm vi đang gián đoạn. Hãy tải lại danh sách rồi thử lại.";
  }
  return "Không thể hoàn tất thao tác. Hãy thử lại.";
}

function shouldOfferRecovery(error: Error): boolean {
  return error instanceof ApiClientError
    && error.code !== "TASK_CODE_EXISTS"
    && (error.status === 403 || error.status === 404 || error.status === 409 || error.status === 503);
}

export function WorkTaskMutationError({
  actionLabel,
  error,
  isRecovering,
  onRecover,
}: WorkTaskMutationErrorProps) {
  const supportCode = error instanceof ApiClientError ? error.code : null;
  return (
    <div
      className="mt-4 rounded-control border border-danger/30 bg-red-50 p-4"
      role="alert"
      aria-label={`Không thể ${actionLabel}`}
    >
      <p className="text-sm font-semibold text-danger">{mutationMessage(error)}</p>
      {supportCode ? <p className="mt-1 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
      {shouldOfferRecovery(error) ? (
        <Button className="mt-3 min-h-11" variant="secondary" onClick={onRecover} disabled={isRecovering}>
          {isRecovering ? "Đang tải lại…" : "Tải lại danh sách công việc"}
        </Button>
      ) : null}
    </div>
  );
}
