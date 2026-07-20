import type {
  AssistantConversationListParams,
  AssistantGenerationEventListParams,
  AssistantMessageListParams,
} from "./assistant-api";

export const assistantQueryKeys = {
  all: ["assistant"] as const,
  subject: (subject: string) => ["assistant", subject] as const,
  capabilities: (subject: string) => ["assistant", subject, "capabilities"] as const,
  conversationLists: (subject: string) => ["assistant", subject, "conversations"] as const,
  conversationList: (subject: string, params: AssistantConversationListParams) => [
    "assistant",
    subject,
    "conversations",
    params.status ?? "OPEN",
    params.page,
    params.size,
  ] as const,
  conversation: (subject: string, conversationId: string) =>
    ["assistant", subject, "conversation", conversationId] as const,
  messages: (
    subject: string,
    conversationId: string,
    params: AssistantMessageListParams,
  ) => [
    "assistant",
    subject,
    "conversation",
    conversationId,
    "messages",
    params.page,
    params.size,
  ] as const,
  generation: (subject: string, conversationId: string, generationId: string) => [
    "assistant",
    subject,
    "conversation",
    conversationId,
    "generation",
    generationId,
  ] as const,
  generationEvents: (
    subject: string,
    conversationId: string,
    generationId: string,
    params: AssistantGenerationEventListParams,
  ) => [
    ...assistantQueryKeys.generation(subject, conversationId, generationId),
    "events",
    params.after,
    params.limit,
  ] as const,
};
