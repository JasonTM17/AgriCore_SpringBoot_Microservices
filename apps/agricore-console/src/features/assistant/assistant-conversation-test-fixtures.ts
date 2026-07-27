import type {
  AssistantConversationPageResponse,
  AssistantConversationResponse,
  AssistantMessagePageResponse,
  AssistantMessageResponse,
} from "../../lib/api/types";

export const TEST_ASSISTANT_CONVERSATION_ID = "50000000-0000-0000-0000-000000000001";
export const TEST_ASSISTANT_FARM_ID = "60000000-0000-0000-0000-000000000001";

export function assistantConversation(
  overrides: Partial<AssistantConversationResponse> = {},
): AssistantConversationResponse {
  return {
    id: TEST_ASSISTANT_CONVERSATION_ID,
    title: "Theo dõi mùa vụ",
    contextType: "FARM",
    farmId: TEST_ASSISTANT_FARM_ID,
    status: "OPEN",
    roleSnapshot: ["FARM_MANAGER"],
    nextMessageSequence: 0,
    version: 0,
    createdAt: "2026-07-21T00:00:00Z",
    updatedAt: "2026-07-21T00:00:00Z",
    archivedAt: null,
    purgeAfter: null,
    ...overrides,
  };
}

export function assistantConversationPage(
  overrides: Partial<AssistantConversationPageResponse> = {},
): AssistantConversationPageResponse {
  return {
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    content: [assistantConversation()],
    ...overrides,
  };
}

export function assistantMessage(
  overrides: Partial<AssistantMessageResponse> = {},
): AssistantMessageResponse {
  return {
    id: "70000000-0000-0000-0000-000000000001",
    conversationId: TEST_ASSISTANT_CONVERSATION_ID,
    generationId: "80000000-0000-0000-0000-000000000001",
    sequenceNo: 0,
    role: "USER",
    content: "Tình trạng mùa vụ hôm nay?",
    tokenCount: 8,
    createdAt: "2026-07-21T00:01:00Z",
    ...overrides,
  };
}

export function assistantMessagePage(
  overrides: Partial<AssistantMessagePageResponse> = {},
): AssistantMessagePageResponse {
  return {
    page: 0,
    size: 100,
    totalElements: 2,
    totalPages: 1,
    first: true,
    last: true,
    content: [
      assistantMessage(),
      assistantMessage({
        id: "70000000-0000-0000-0000-000000000002",
        sequenceNo: 1,
        role: "ASSISTANT",
        content: "Mùa vụ đang ổn định.",
      }),
    ],
    ...overrides,
  };
}
