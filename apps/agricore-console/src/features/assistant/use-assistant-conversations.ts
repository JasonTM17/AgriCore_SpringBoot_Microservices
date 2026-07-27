import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useState } from "react";

import type { ApiClient } from "../../lib/api/client";
import type {
  AssistantConversationStatus,
  CreateAssistantConversationRequest,
} from "../../lib/api/types";
import {
  archiveAssistantConversation,
  createAssistantConversation,
  getAssistantCapabilities,
  listAssistantConversations,
  type AssistantConversationListParams,
} from "./assistant-api";
import {
  normalizeCreateAssistantConversationRequest,
  validateArchivedAssistantConversation,
  validateAssistantCapabilities,
  validateAssistantConversationPage,
  validateCreatedAssistantConversation,
} from "./assistant-conversation-validation";
import { assistantQueryKeys } from "./assistant-query-keys";

const CONVERSATION_PAGE_SIZE = 20;

interface UseAssistantConversationsOptions {
  api: ApiClient;
  subject: string;
  enabled: boolean;
}

export function useAssistantConversations({
  api,
  subject,
  enabled,
}: UseAssistantConversationsOptions) {
  const queryClient = useQueryClient();
  const [status, setStatusValue] = useState<AssistantConversationStatus>("OPEN");
  const [page, setPage] = useState(0);
  const params = useMemo<AssistantConversationListParams>(() => ({
    status,
    page,
    size: CONVERSATION_PAGE_SIZE,
  }), [page, status]);
  const capabilities = useQuery({
    queryKey: assistantQueryKeys.capabilities(subject),
    queryFn: async ({ signal }) => validateAssistantCapabilities(
      await getAssistantCapabilities(api, signal),
    ),
    enabled,
    staleTime: 60_000,
  });
  const conversations = useQuery({
    queryKey: assistantQueryKeys.conversationList(subject, params),
    queryFn: async ({ signal }) => validateAssistantConversationPage(
      await listAssistantConversations(api, params, signal),
      status,
      page,
      CONVERSATION_PAGE_SIZE,
    ),
    enabled,
    staleTime: 10_000,
  });

  useEffect(() => {
    if (!conversations.data) return;
    const lastAvailablePage = Math.max(0, conversations.data.totalPages - 1);
    if (page <= lastAvailablePage) return;
    const timer = window.setTimeout(() => setPage(lastAvailablePage), 0);
    return () => window.clearTimeout(timer);
  }, [conversations.data, page]);

  const create = useMutation({
    mutationFn: async (draft: CreateAssistantConversationRequest) => {
      const request = normalizeCreateAssistantConversationRequest(draft);
      const response = await createAssistantConversation(api, request);
      return validateCreatedAssistantConversation(response, request);
    },
    onSuccess: async (conversation) => {
      setStatusValue("OPEN");
      setPage(0);
      queryClient.setQueryData(
        assistantQueryKeys.conversation(subject, conversation.id),
        conversation,
      );
      await queryClient.invalidateQueries({
        queryKey: assistantQueryKeys.conversationLists(subject),
      });
    },
  });
  const archive = useMutation({
    mutationFn: async (conversationId: string) => validateArchivedAssistantConversation(
      await archiveAssistantConversation(api, conversationId),
      conversationId,
    ),
    onSuccess: async (conversation) => {
      queryClient.setQueryData(
        assistantQueryKeys.conversation(subject, conversation.id),
        conversation,
      );
      await queryClient.invalidateQueries({
        queryKey: assistantQueryKeys.conversationLists(subject),
      });
    },
  });

  const selectStatus = useCallback((nextStatus: AssistantConversationStatus) => {
    setStatusValue(nextStatus);
    setPage(0);
  }, []);
  const goPrevious = useCallback(() => setPage((current) => Math.max(0, current - 1)), []);
  const goNext = useCallback(() => {
    if (conversations.data && !conversations.data.last) {
      setPage((current) => current + 1);
    }
  }, [conversations.data]);

  return {
    archive,
    capabilities,
    conversations,
    create,
    goNext,
    goPrevious,
    page,
    selectStatus,
    status,
  };
}
