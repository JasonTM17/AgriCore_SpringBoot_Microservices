import { useState } from "react";

import { EmptyState } from "../../components/ui/empty-state";
import type { CreateAssistantConversationRequest } from "../../lib/api/types";
import { useSession } from "../../lib/auth/session";
import { useFarmScope } from "../farm/farm-scope-context";
import { AssistantConversationSidebar } from "./assistant-conversation-sidebar";
import { AssistantConversationWorkspace } from "./assistant-conversation-workspace";
import { AssistantNewConversationForm } from "./assistant-new-conversation-form";
import { useAssistantConversations } from "./use-assistant-conversations";

export function AssistantPage() {
  const { api, user } = useSession();
  const { activeFarm } = useFarmScope();
  const subject = user?.id ?? "unauthenticated";
  const [selectedConversationId, setSelectedConversationId] = useState<string | null>(null);
  const assistant = useAssistantConversations({
    api,
    subject,
    enabled: user !== null,
  });
  const pageConversations = assistant.conversations.data?.content ?? [];
  const selectedConversation = pageConversations.find(
    (conversation) => conversation.id === selectedConversationId,
  ) ?? pageConversations[0] ?? null;

  function createConversation(request: CreateAssistantConversationRequest) {
    assistant.create.mutate(request, {
      onSuccess: (conversation) => setSelectedConversationId(conversation.id),
    });
  }

  return (
    <div className="space-y-6">
      <header>
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">
          AI operations assistant
        </p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-ink">Trợ lý vận hành AgriCore</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">
          Hội thoại được cô lập theo tài khoản và phạm vi dữ liệu. Trợ lý chỉ tư vấn; mọi API nghiệp vụ vẫn kiểm tra quyền độc lập.
        </p>
      </header>
      <div className="grid items-start gap-5 xl:grid-cols-[minmax(18rem,22rem)_minmax(0,1fr)]">
        <aside className="space-y-4" aria-label="Quản lý hội thoại trợ lý">
          <AssistantNewConversationForm
            key={assistant.create.data?.id ?? subject}
            activeFarm={activeFarm}
            error={assistant.create.error}
            isPending={assistant.create.isPending}
            onSubmit={createConversation}
          />
          <AssistantConversationSidebar
            data={assistant.conversations.data}
            error={assistant.conversations.error}
            isFetching={assistant.conversations.isFetching}
            isPending={assistant.conversations.isPending}
            selectedConversationId={selectedConversation?.id ?? null}
            status={assistant.status}
            onNext={assistant.goNext}
            onPrevious={assistant.goPrevious}
            onRetry={() => void assistant.conversations.refetch()}
            onSelect={(conversation) => setSelectedConversationId(conversation.id)}
            onStatusChange={(status) => {
              setSelectedConversationId(null);
              assistant.selectStatus(status);
            }}
          />
        </aside>
        {selectedConversation ? (
          <AssistantConversationWorkspace
            key={selectedConversation.id}
            api={api}
            archiveError={assistant.archive.variables === selectedConversation.id
              ? assistant.archive.error
              : null}
            capabilities={assistant.capabilities.data}
            capabilitiesError={assistant.capabilities.error}
            conversation={selectedConversation}
            isArchiving={assistant.archive.isPending
              && assistant.archive.variables === selectedConversation.id}
            subject={subject}
            onArchive={() => assistant.archive.mutate(selectedConversation.id, {
              onSuccess: () => setSelectedConversationId(null),
            })}
            onRetryCapabilities={() => void assistant.capabilities.refetch()}
          />
        ) : (
          <EmptyState
            title={assistant.conversations.isPending ? "Đang tải hội thoại" : "Chọn hoặc tạo một hội thoại"}
            description="Workspace chat sẽ mở tại đây và tiếp tục generation dang dở sau khi tải lại trang."
          />
        )}
      </div>
    </div>
  );
}
