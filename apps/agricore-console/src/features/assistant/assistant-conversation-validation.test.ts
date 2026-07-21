import { describe, expect, it } from "vitest";

import {
  assistantConversation,
  assistantConversationPage,
  assistantMessage,
  assistantMessagePage,
  TEST_ASSISTANT_CONVERSATION_ID,
  TEST_ASSISTANT_FARM_ID,
} from "./assistant-conversation-test-fixtures";
import {
  normalizeCreateAssistantConversationRequest,
  validateArchivedAssistantConversation,
  validateAssistantConversationPage,
  validateAssistantMessagePage,
  validateCreatedAssistantConversation,
} from "./assistant-conversation-validation";

describe("assistant conversation validation", () => {
  it("normalizes titles and enforces context authority", () => {
    expect(normalizeCreateAssistantConversationRequest({
      title: "  Điều hành mùa vụ  ",
      contextType: "FARM",
      farmId: TEST_ASSISTANT_FARM_ID,
    })).toEqual({
      title: "Điều hành mùa vụ",
      contextType: "FARM",
      farmId: TEST_ASSISTANT_FARM_ID,
    });
    expect(normalizeCreateAssistantConversationRequest({
      title: "Toàn doanh nghiệp",
      contextType: "ENTERPRISE",
      farmId: TEST_ASSISTANT_FARM_ID,
    })).toEqual({
      title: "Toàn doanh nghiệp",
      contextType: "ENTERPRISE",
      farmId: null,
    });
  });

  it.each([
    [{ title: " ", contextType: "ENTERPRISE" }, "CONVERSATION_TITLE_REQUIRED"],
    [{ title: "x".repeat(201), contextType: "ENTERPRISE" }, "CONVERSATION_TITLE_TOO_LONG"],
    [{ title: "Farm", contextType: "FARM", farmId: null }, "CONVERSATION_FARM_REQUIRED"],
  ] as const)("rejects invalid create input with %s", (request, code) => {
    expect(() => normalizeCreateAssistantConversationRequest(request))
      .toThrow(expect.objectContaining({ code }));
  });

  it("correlates create and archive responses to the requested authority", () => {
    const createRequest = {
      title: "Theo dõi mùa vụ",
      contextType: "FARM" as const,
      farmId: TEST_ASSISTANT_FARM_ID,
    };
    expect(validateCreatedAssistantConversation(assistantConversation(), createRequest).id)
      .toBe(TEST_ASSISTANT_CONVERSATION_ID);
    expect(() => validateCreatedAssistantConversation(
      assistantConversation({ farmId: "60000000-0000-0000-0000-000000000002" }),
      createRequest,
    )).toThrow(expect.objectContaining({ code: "INVALID_CONVERSATION_RESPONSE" }));

    const archived = assistantConversation({
      status: "ARCHIVED",
      archivedAt: "2026-07-21T01:00:00Z",
      purgeAfter: "2026-10-19T01:00:00Z",
    });
    expect(validateArchivedAssistantConversation(archived, TEST_ASSISTANT_CONVERSATION_ID))
      .toStrictEqual(archived);
    expect(() => validateArchivedAssistantConversation(
      archived,
      "50000000-0000-0000-0000-000000000002",
    )).toThrow(expect.objectContaining({ code: "INVALID_CONVERSATION_RESPONSE" }));
  });

  it("rejects conversation pages with mismatched status or metadata", () => {
    expect(validateAssistantConversationPage(assistantConversationPage(), "OPEN", 0, 20).content)
      .toHaveLength(1);
    expect(() => validateAssistantConversationPage(
      assistantConversationPage({ content: [assistantConversation({ status: "ARCHIVED" })] }),
      "OPEN",
      0,
      20,
    )).toThrow(expect.objectContaining({ code: "INVALID_CONVERSATION_PAGE" }));
    expect(() => validateAssistantConversationPage(
      assistantConversationPage({ totalPages: 2 }),
      "OPEN",
      0,
      20,
    )).toThrow(expect.objectContaining({ code: "INVALID_CONVERSATION_PAGE" }));
  });

  it("keeps message history chronological and conversation-scoped", () => {
    expect(validateAssistantMessagePage(
      assistantMessagePage(),
      TEST_ASSISTANT_CONVERSATION_ID,
      0,
      100,
    ).content.map((message) => message.sequenceNo)).toEqual([0, 1]);
    expect(() => validateAssistantMessagePage(assistantMessagePage({
      content: [
        assistantMessage({ sequenceNo: 2 }),
        assistantMessage({
          id: "70000000-0000-0000-0000-000000000002",
          sequenceNo: 1,
        }),
      ],
    }), TEST_ASSISTANT_CONVERSATION_ID, 0, 100))
      .toThrow(expect.objectContaining({ code: "INVALID_MESSAGE_PAGE" }));
    expect(() => validateAssistantMessagePage(assistantMessagePage({
      content: [assistantMessage({
        conversationId: "50000000-0000-0000-0000-000000000002",
      })],
      totalElements: 1,
    }), TEST_ASSISTANT_CONVERSATION_ID, 0, 100))
      .toThrow(expect.objectContaining({ code: "INVALID_MESSAGE_PAGE" }));
  });
});
