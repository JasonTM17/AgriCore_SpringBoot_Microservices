import { useQuery } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useState } from "react";

import type { ApiClient } from "../../lib/api/client";
import { listAssistantMessages, type AssistantMessageListParams } from "./assistant-api";
import { validateAssistantMessagePage } from "./assistant-conversation-validation";
import { isAssistantIdentifier } from "./assistant-identifiers";
import { assistantQueryKeys } from "./assistant-query-keys";

const MESSAGE_PAGE_SIZE = 100;

interface UseAssistantMessageHistoryOptions {
  api: ApiClient;
  subject: string;
  conversationId: string | null;
  enabled: boolean;
}

interface MessagePaginationState {
  conversationId: string | null;
  page: number;
  followTail: boolean;
}

export function useAssistantMessageHistory({
  api,
  subject,
  conversationId,
  enabled,
}: UseAssistantMessageHistoryOptions) {
  const [pagination, setPagination] = useState<MessagePaginationState>({
    conversationId,
    page: 0,
    followTail: true,
  });
  const currentPagination = pagination.conversationId === conversationId
    ? pagination
    : { conversationId, page: 0, followTail: true };
  const { page, followTail } = currentPagination;
  const params = useMemo<AssistantMessageListParams>(
    () => ({ page, size: MESSAGE_PAGE_SIZE }),
    [page],
  );
  const validConversation = isAssistantIdentifier(conversationId);
  const messages = useQuery({
    queryKey: assistantQueryKeys.messages(subject, conversationId ?? "none", params),
    queryFn: async ({ signal }) => {
      if (!validConversation || conversationId === null) {
        throw new Error("Cannot load messages without a valid conversation");
      }
      return validateAssistantMessagePage(
        await listAssistantMessages(api, conversationId, params, signal),
        conversationId,
        page,
        MESSAGE_PAGE_SIZE,
      );
    },
    enabled: enabled && validConversation,
    staleTime: 0,
  });
  const lastPage = Math.max(0, (messages.data?.totalPages ?? 1) - 1);

  useEffect(() => {
    if (!conversationId || !messages.data || !followTail || page === lastPage) return;
    const timer = window.setTimeout(() => {
      setPagination({ conversationId, page: lastPage, followTail: true });
    }, 0);
    return () => window.clearTimeout(timer);
  }, [conversationId, followTail, lastPage, messages.data, page]);

  const goOlder = useCallback(() => {
    if (page === 0) return;
    setPagination({
      conversationId,
      page: Math.max(0, page - 1),
      followTail: false,
    });
  }, [conversationId, page]);
  const goNewer = useCallback(() => {
    if (page >= lastPage) return;
    const nextPage = Math.min(lastPage, page + 1);
    setPagination({ conversationId, page: nextPage, followTail: nextPage === lastPage });
  }, [conversationId, lastPage, page]);
  const goLatest = useCallback(() => {
    setPagination({ conversationId, page: lastPage, followTail: true });
  }, [conversationId, lastPage]);

  return {
    canGoNewer: page < lastPage,
    canGoOlder: page > 0,
    goLatest,
    goNewer,
    goOlder,
    isAtLatest: page === lastPage,
    messages,
    page,
  };
}
