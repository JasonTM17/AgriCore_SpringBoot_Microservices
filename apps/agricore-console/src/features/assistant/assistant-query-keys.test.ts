import { describe, expect, it } from "vitest";

import { assistantQueryKeys } from "./assistant-query-keys";

describe("assistant query keys", () => {
  it("isolates authenticated assistant data by subject", () => {
    const params = { status: "OPEN" as const, page: 0, size: 20 };

    expect(assistantQueryKeys.conversationList("user-a", params)).not.toEqual(
      assistantQueryKeys.conversationList("user-b", params),
    );
    expect(assistantQueryKeys.conversation("user-a", "conversation-1")).not.toEqual(
      assistantQueryKeys.conversation("user-b", "conversation-1"),
    );
  });

  it("canonicalizes the server-default OPEN conversation status", () => {
    const omitted = assistantQueryKeys.conversationList("user-a", { page: 0, size: 20 });
    const explicit = assistantQueryKeys.conversationList("user-a", {
      status: "OPEN",
      page: 0,
      size: 20,
    });

    expect(omitted).toEqual(explicit);
    expect(omitted.slice(0, 3)).toEqual(assistantQueryKeys.conversationLists("user-a"));
  });

  it("keeps messages and event replay scoped to durable parent identities", () => {
    const conversation = assistantQueryKeys.conversation("user-a", "conversation-1");
    const messages = assistantQueryKeys.messages(
      "user-a",
      "conversation-1",
      { page: 0, size: 50 },
    );
    const generation = assistantQueryKeys.generation(
      "user-a",
      "conversation-1",
      "generation-1",
    );
    const events = assistantQueryKeys.generationEvents(
      "user-a",
      "conversation-1",
      "generation-1",
      { after: 4, limit: 100 },
    );

    expect(messages.slice(0, conversation.length)).toEqual(conversation);
    expect(messages.slice(0, -2)).toEqual(
      assistantQueryKeys.messageHistories("user-a", "conversation-1"),
    );
    expect(events.slice(0, generation.length)).toEqual(generation);
    expect(events).toContain(4);
    expect(events).toContain(100);
  });
});
