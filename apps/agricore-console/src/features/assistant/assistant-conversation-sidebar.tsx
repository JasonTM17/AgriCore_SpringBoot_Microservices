import { Button } from "../../components/ui/button";
import { EmptyState } from "../../components/ui/empty-state";
import { PaginationControls } from "../../components/ui/pagination-controls";
import type {
  AssistantConversationPageResponse,
  AssistantConversationResponse,
  AssistantConversationStatus,
} from "../../lib/api/types";
import { AssistantErrorNotice } from "./assistant-error-notice";
import {
  assistantConversationContextLabel,
  formatAssistantConversationTime,
} from "./assistant-formatters";

interface AssistantConversationSidebarProps {
  data: AssistantConversationPageResponse | undefined;
  error: Error | null;
  isFetching: boolean;
  isPending: boolean;
  selectedConversationId: string | null;
  status: AssistantConversationStatus;
  onNext: () => void;
  onPrevious: () => void;
  onRetry: () => void;
  onSelect: (conversation: AssistantConversationResponse) => void;
  onStatusChange: (status: AssistantConversationStatus) => void;
}

export function AssistantConversationSidebar({
  data,
  error,
  isFetching,
  isPending,
  selectedConversationId,
  status,
  onNext,
  onPrevious,
  onRetry,
  onSelect,
  onStatusChange,
}: AssistantConversationSidebarProps) {
  return (
    <section className="rounded-card border border-border bg-surface p-4 shadow-sm" aria-labelledby="assistant-conversations-heading">
      <div className="flex items-center justify-between gap-3">
        <h2 id="assistant-conversations-heading" className="font-semibold text-ink">Hội thoại</h2>
        {isFetching && !isPending ? <span className="text-xs text-info" role="status">Đang cập nhật…</span> : null}
      </div>
      <div className="mt-3 grid grid-cols-2 rounded-control bg-canvas p-1" aria-label="Trạng thái hội thoại">
        {(["OPEN", "ARCHIVED"] as const).map((itemStatus) => (
          <Button
            key={itemStatus}
            className="h-9 px-2"
            variant={status === itemStatus ? "primary" : "ghost"}
            aria-pressed={status === itemStatus}
            onClick={() => onStatusChange(itemStatus)}
          >
            {itemStatus === "OPEN" ? "Đang mở" : "Đã lưu trữ"}
          </Button>
        ))}
      </div>

      {isPending ? (
        <div className="mt-4 space-y-2" role="status" aria-label="Đang tải hội thoại">
          {[0, 1, 2].map((item) => <div key={item} className="h-16 animate-pulse rounded-control bg-forest-50" />)}
        </div>
      ) : null}
      {!isPending && error ? (
        <div className="mt-4"><AssistantErrorNotice error={error} actionLabel="Thử lại" onAction={onRetry} /></div>
      ) : null}
      {!isPending && !error && data?.content.length === 0 ? (
        <div className="mt-4">
          <EmptyState
            title={status === "OPEN" ? "Chưa có hội thoại đang mở" : "Chưa có hội thoại lưu trữ"}
            description="Tạo hội thoại mới để bắt đầu trao đổi với trợ lý vận hành."
          />
        </div>
      ) : null}
      {data?.content.length ? (
        <div className="mt-4 max-h-[30rem] space-y-2 overflow-y-auto pr-1">
          {data.content.map((conversation) => {
            const selected = conversation.id === selectedConversationId;
            return (
              <button
                key={conversation.id}
                type="button"
                className={`w-full rounded-control border p-3 text-left transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info ${selected ? "border-forest-700 bg-forest-50" : "border-border hover:bg-canvas"}`}
                aria-pressed={selected}
                onClick={() => onSelect(conversation)}
              >
                <span className="block truncate text-sm font-semibold text-ink">{conversation.title}</span>
                <span className="mt-1 flex items-center justify-between gap-2 text-xs text-muted">
                  <span>{assistantConversationContextLabel(conversation)}</span>
                  <time dateTime={conversation.updatedAt}>{formatAssistantConversationTime(conversation.updatedAt)}</time>
                </span>
              </button>
            );
          })}
        </div>
      ) : null}
      {data ? (
        <div className="mt-4">
          <PaginationControls
            page={data.page}
            totalPages={data.totalPages}
            isFetching={isFetching}
            label="Phân trang hội thoại"
            onPrevious={onPrevious}
            onNext={onNext}
          />
        </div>
      ) : null}
    </section>
  );
}
