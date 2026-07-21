import { Button } from "../../components/ui/button";
import { assistantErrorMessage, assistantSupportCode } from "./assistant-error-policy";

export function AssistantErrorNotice({
  error,
  actionLabel,
  onAction,
}: {
  error: unknown;
  actionLabel?: string;
  onAction?: () => void;
}) {
  const supportCode = assistantSupportCode(error);
  return (
    <div className="rounded-control border border-danger/30 bg-red-50 p-4" role="alert">
      <p className="text-sm font-semibold text-danger">{assistantErrorMessage(error)}</p>
      {supportCode ? <p className="mt-1 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
      {actionLabel && onAction ? (
        <Button className="mt-3" variant="secondary" onClick={onAction}>{actionLabel}</Button>
      ) : null}
    </div>
  );
}
