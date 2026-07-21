import { useQueryClient } from "@tanstack/react-query";
import { useCallback, useState } from "react";

import { Button } from "../../components/ui/button";
import type { ApiClient } from "../../lib/api/client";
import type {
  AssistantCapabilitiesResponse,
  AssistantConversationResponse,
} from "../../lib/api/types";
import { AssistantComposer } from "./assistant-composer";
import { assistantConversationContextLabel } from "./assistant-formatters";
import { AssistantGenerationStatus } from "./assistant-generation-status";
import { isTerminalAssistantStatus } from "./assistant-generation-projection";
import {
  readActiveAssistantGeneration,
  writeActiveAssistantGeneration,
} from "./assistant-generation-session";
import { AssistantTranscript } from "./assistant-transcript";
import { AssistantErrorNotice } from "./assistant-error-notice";
import { assistantCapabilityMessage } from "./assistant-error-policy";
import { assistantQueryKeys } from "./assistant-query-keys";
import { useAssistantGeneration } from "./use-assistant-generation";
import { useAssistantMessageHistory } from "./use-assistant-message-history";

interface AssistantConversationWorkspaceProps {
  api: ApiClient;
  archiveError: Error | null;
  capabilities: AssistantCapabilitiesResponse | undefined;
  capabilitiesError: Error | null;
  conversation: AssistantConversationResponse;
  isArchiving: boolean;
  subject: string;
  onArchive: () => void;
  onRetryCapabilities: () => void;
}

export function AssistantConversationWorkspace({
  api,
  archiveError,
  capabilities,
  capabilitiesError,
  conversation,
  isArchiving,
  subject,
  onArchive,
  onRetryCapabilities,
}: AssistantConversationWorkspaceProps) {
  const queryClient = useQueryClient();
  const [initialGenerationId] = useState(() =>
    readActiveAssistantGeneration(subject, conversation.id));
  const history = useAssistantMessageHistory({
    api,
    subject,
    conversationId: conversation.id,
    enabled: true,
  });
  const onGenerationChanged = useCallback((generationId: string | null) => {
    writeActiveAssistantGeneration(subject, conversation.id, generationId);
  }, [conversation.id, subject]);
  const onHistoryChanged = useCallback(() => {
    void queryClient.invalidateQueries({
      queryKey: assistantQueryKeys.messageHistories(subject, conversation.id),
    });
    void queryClient.invalidateQueries({
      queryKey: assistantQueryKeys.conversationLists(subject),
    });
  }, [conversation.id, queryClient, subject]);
  const generation = useAssistantGeneration({
    api,
    conversationId: conversation.id,
    initialGenerationId,
    onGenerationChanged,
    onHistoryChanged,
  });
  const generationActive = generation.projection !== null
    && !isTerminalAssistantStatus(generation.projection.status);
  const generationLocked = generationActive
    || generation.isSubmitting
    || generation.phase === "SUBMIT_FAILED";
  const capabilityMessage = assistantCapabilityMessage(capabilities);
  const providerReady = capabilities?.available === true && capabilities.streaming;
  const archived = conversation.status === "ARCHIVED";
  const disabledReason = archived
    ? "Hội thoại đã lưu trữ; lịch sử vẫn ở chế độ chỉ đọc."
    : capabilitiesError
      ? "Chưa xác minh được trạng thái nhà cung cấp AI."
      : !capabilities
        ? "Đang kiểm tra trạng thái nhà cung cấp AI."
        : capabilityMessage
          ?? (generationLocked ? "Hãy hoàn tất hoặc khôi phục phản hồi hiện tại trước." : null);
  const handleSend = useCallback(async (prompt: string) => {
    history.goLatest();
    return generation.send(prompt);
  }, [generation, history]);

  return (
    <section className="flex min-h-[42rem] min-w-0 flex-col overflow-hidden rounded-card border border-border bg-surface shadow-sm" aria-labelledby="assistant-workspace-heading">
      <header className="flex flex-wrap items-start justify-between gap-3 border-b border-border px-4 py-4 md:px-5">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">
            {assistantConversationContextLabel(conversation)} · {conversation.status === "OPEN" ? "Đang mở" : "Đã lưu trữ"}
          </p>
          <h2 id="assistant-workspace-heading" className="mt-1 truncate text-xl font-bold text-ink">
            {conversation.title}
          </h2>
          {capabilities ? (
            <p className="mt-1 text-xs text-muted">Provider: {capabilities.provider}</p>
          ) : null}
        </div>
        {conversation.status === "OPEN" ? (
          <Button
            variant="secondary"
            disabled={generationLocked || isArchiving}
            onClick={onArchive}
          >
            {isArchiving ? "Đang lưu trữ…" : "Lưu trữ"}
          </Button>
        ) : null}
      </header>
      {capabilitiesError ? (
        <div className="border-b border-border p-4">
          <AssistantErrorNotice
            error={capabilitiesError}
            actionLabel="Kiểm tra lại provider"
            onAction={onRetryCapabilities}
          />
        </div>
      ) : null}
      {!capabilitiesError && capabilityMessage ? (
        <p className="border-b border-warning/30 bg-amber-50 px-4 py-3 text-sm text-warning" role="status">
          {capabilityMessage}
        </p>
      ) : null}
      {archiveError ? (
        <div className="border-b border-border p-4"><AssistantErrorNotice error={archiveError} /></div>
      ) : null}
      <AssistantTranscript
        canGoNewer={history.canGoNewer}
        canGoOlder={history.canGoOlder}
        data={history.messages.data}
        error={history.messages.error}
        isAtLatest={history.isAtLatest}
        isFetching={history.messages.isFetching}
        isPending={history.messages.isPending}
        pendingPrompt={generation.pendingPrompt}
        projection={generation.projection}
        onLatest={history.goLatest}
        onNewer={history.goNewer}
        onOlder={history.goOlder}
        onRetry={() => void history.messages.refetch()}
      />
      <AssistantGenerationStatus
        state={generation}
        onCancel={() => void generation.cancel()}
        onRetryConnection={() => void generation.retryConnection()}
        onRetrySubmission={() => void generation.retrySubmission()}
      />
      <AssistantComposer
        disabled={archived || !providerReady || generationLocked}
        disabledReason={disabledReason}
        isSubmitting={generation.isSubmitting}
        onSend={handleSend}
      />
    </section>
  );
}
