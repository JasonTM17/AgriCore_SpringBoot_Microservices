import { Button } from "../../components/ui/button";
import { ApiClientError } from "../../lib/api/errors";

interface HarvestCompletionErrorProps {
  error: Error;
  isRecovering: boolean;
  onRecover: () => void;
}

function completionMessage(error: Error): string {
  if (!(error instanceof ApiClientError)) {
    return "Yêu cầu không hoàn tất. Kiểm tra kết nối rồi thử lại.";
  }
  if (error.code === "HARVEST_CODE_EXISTS") {
    return "Mã thu hoạch đã tồn tại. Hãy đổi mã rồi gửi lại.";
  }
  if (error.status === 400 || error.status === 422) {
    return "Dữ liệu chưa hợp lệ. Kiểm tra các trường và thử lại.";
  }
  if (error.status === 403 || error.status === 404) {
    return "Quyền truy cập đã thay đổi hoặc ngữ cảnh thu hoạch không còn khả dụng.";
  }
  if (error.status === 409) {
    return "Dữ liệu vừa thay đổi nên yêu cầu không còn hợp lệ. Hãy xác minh lại ngữ cảnh.";
  }
  if (error.status === 503) {
    return "Dịch vụ xác minh phạm vi đang gián đoạn. Hãy xác minh lại trước khi gửi tiếp.";
  }
  return "Không thể hoàn tất thu hoạch. Hãy thử lại.";
}

function canRecover(error: Error): boolean {
  return error instanceof ApiClientError
    && error.code !== "HARVEST_CODE_EXISTS"
    && (error.status === 403 || error.status === 404 || error.status === 409 || error.status === 503);
}

export function HarvestCompletionError({
  error,
  isRecovering,
  onRecover,
}: HarvestCompletionErrorProps) {
  const supportCode = error instanceof ApiClientError ? error.code : null;
  return (
    <div
      className="mt-4 rounded-control border border-danger/30 bg-red-50 p-4"
      role="alert"
      aria-label="Không thể hoàn tất thu hoạch"
    >
      <p className="text-sm font-semibold text-danger">{completionMessage(error)}</p>
      {supportCode ? <p className="mt-1 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
      {canRecover(error) ? (
        <Button className="mt-3 min-h-11" variant="secondary" onClick={onRecover} disabled={isRecovering}>
          {isRecovering ? "Đang xác minh lại…" : "Xác minh lại ngữ cảnh"}
        </Button>
      ) : null}
    </div>
  );
}
