import { useEffect, useRef } from "react";

import { Button } from "../../components/ui/button";
import type { AssistantMessagePageResponse, AssistantMessageResponse } from "../../lib/api/types";
import type { AssistantGenerationProjection } from "./assistant-generation-projection";
import { AssistantErrorNotice } from "./assistant-error-notice";
import { AssistantCitationList } from "./assistant-citation-list";
import { formatAssistantConversationTime } from "./assistant-formatters";

function MessageBubble({ message }: { message: AssistantMessageResponse }) {
  const assistant = message.role === "ASSISTANT";
  return (
    <article
      className={`max-w-[88%] rounded-card px-4 py-3 ${assistant ? "mr-auto border border-border bg-surface" : "ml-auto bg-forest-700 text-white"}`}
      aria-label={assistant ? "Trợ lý" : "Bạn"}
    >
      <p className={`text-xs font-semibold ${assistant ? "text-forest-700" : "text-white/80"}`}>
        {assistant ? "Trợ lý AgriCore" : "Bạn"}
      </p>
      <p className="mt-1 whitespace-pre-wrap break-words text-sm leading-6">{message.content}</p>
      {assistant ? <AssistantCitationList content={message.content} /> : null}
      <time
        className={`mt-2 block text-[0.7rem] ${assistant ? "text-muted" : "text-white/70"}`}
        dateTime={message.createdAt}
      >
        {formatAssistantConversationTime(message.createdAt)}
      </time>
    </article>
  );
}

interface AssistantTranscriptProps {
  canGoNewer: boolean;
  canGoOlder: boolean;
  data: AssistantMessagePageResponse | undefined;
  error: Error | null;
  isAtLatest: boolean;
  isFetching: boolean;
  isPending: boolean;
  pendingPrompt: string | null;
  projection: AssistantGenerationProjection | null;
  onLatest: () => void;
  onNewer: () => void;
  onOlder: () => void;
  onRetry: () => void;
}

export function AssistantTranscript({
  canGoNewer,
  canGoOlder,
  data,
  error,
  isAtLatest,
  isFetching,
  isPending,
  pendingPrompt,
  projection,
  onLatest,
  onNewer,
  onOlder,
  onRetry,
}: AssistantTranscriptProps) {
  const tailRef = useRef<HTMLDivElement | null>(null);
  const persistedLiveMessage = projection !== null && data?.content.some(
    (message) => message.role === "ASSISTANT" && message.generationId === projection.generationId,
  );
  const showLiveDraft = isAtLatest
    && projection !== null
    && projection.draft.length > 0
    && !persistedLiveMessage;
  const latestMessageId = data?.content.at(-1)?.id;

  useEffect(() => {
    if (isAtLatest) tailRef.current?.scrollIntoView?.({ block: "nearest" });
  }, [isAtLatest, latestMessageId, projection?.lastSequence]);

  return (
    <section className="min-h-[28rem] flex-1 overflow-y-auto bg-canvas/60 p-4" aria-label="Nội dung hội thoại">
      {isPending ? (
        <div className="space-y-3" role="status" aria-label="Đang tải lịch sử">
          <div className="h-20 w-3/4 animate-pulse rounded-card bg-forest-50" />
          <div className="ml-auto h-20 w-2/3 animate-pulse rounded-card bg-forest-100" />
        </div>
      ) : null}
      {!isPending && error ? <AssistantErrorNotice error={error} actionLabel="Tải lại lịch sử" onAction={onRetry} /> : null}
      {!isPending && !error && data?.totalElements === 0 && !pendingPrompt && !showLiveDraft ? (
        <div className="grid min-h-72 place-items-center text-center">
          <div className="max-w-sm">
            <p className="font-semibold text-ink">Bắt đầu bằng một câu hỏi vận hành</p>
            <p className="mt-2 text-sm leading-6 text-muted">
              Trợ lý chỉ dùng phạm vi của hội thoại và backend vẫn là nguồn quyền lực cuối cùng.
            </p>
          </div>
        </div>
      ) : null}
      {data?.content.length ? (
        <div className="space-y-3">
          {data.content.map((message) => <MessageBubble key={message.id} message={message} />)}
        </div>
      ) : null}
      {isAtLatest && pendingPrompt ? (
        <div className="mt-3 ml-auto max-w-[88%] rounded-card border border-forest-700/30 bg-forest-50 px-4 py-3" aria-label="Câu hỏi đang chờ xác nhận">
          <p className="text-xs font-semibold text-forest-700">Đang xác nhận gửi</p>
          <p className="mt-1 whitespace-pre-wrap break-words text-sm leading-6 text-ink">{pendingPrompt}</p>
        </div>
      ) : null}
      {showLiveDraft ? (
        <article className="mt-3 mr-auto max-w-[88%] rounded-card border border-info/30 bg-surface px-4 py-3" aria-label="Phản hồi đang tạo">
          <p className="text-xs font-semibold text-info">Trợ lý AgriCore · đang trả lời</p>
          <p className="mt-1 whitespace-pre-wrap break-words text-sm leading-6 text-ink">{projection.draft}</p>
          <AssistantCitationList content={projection.draft} />
        </article>
      ) : null}
      <div ref={tailRef} />
      {data && data.totalPages > 1 ? (
        <div className="sticky bottom-0 mt-4 flex flex-wrap items-center justify-between gap-2 border-t border-border bg-canvas/95 pt-3">
          <span className="text-xs text-muted">Trang tin nhắn {data.page + 1}/{data.totalPages}{isFetching ? " · đang tải" : ""}</span>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" disabled={!canGoOlder || isFetching} onClick={onOlder}>Cũ hơn</Button>
            <Button variant="secondary" disabled={!canGoNewer || isFetching} onClick={onNewer}>Mới hơn</Button>
            {!isAtLatest ? <Button variant="ghost" disabled={isFetching} onClick={onLatest}>Mới nhất</Button> : null}
          </div>
        </div>
      ) : null}
    </section>
  );
}
