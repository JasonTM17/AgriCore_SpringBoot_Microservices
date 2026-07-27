import { describe, expect, it } from "vitest";

import {
  readActiveAssistantGeneration,
  type AssistantGenerationSessionStore,
  writeActiveAssistantGeneration,
} from "./assistant-generation-session";

const SUBJECT = "10000000-0000-0000-0000-000000000001";
const CONVERSATION_ID = "20000000-0000-0000-0000-000000000001";
const GENERATION_ID = "30000000-0000-0000-0000-000000000001";

function memoryStore(): AssistantGenerationSessionStore {
  const values = new Map<string, string>();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => {
      values.set(key, value);
    },
    removeItem: (key) => {
      values.delete(key);
    },
  };
}

describe("assistant generation session", () => {
  it("isolates resumable generation IDs by subject and conversation", () => {
    const store = memoryStore();

    writeActiveAssistantGeneration(SUBJECT, CONVERSATION_ID, GENERATION_ID, store);

    expect(readActiveAssistantGeneration(SUBJECT, CONVERSATION_ID, store)).toBe(GENERATION_ID);
    expect(readActiveAssistantGeneration(
      "10000000-0000-0000-0000-000000000002",
      CONVERSATION_ID,
      store,
    )).toBeNull();
  });

  it("removes terminal and malformed descriptors", () => {
    const store = memoryStore();
    writeActiveAssistantGeneration(SUBJECT, CONVERSATION_ID, GENERATION_ID, store);
    writeActiveAssistantGeneration(SUBJECT, CONVERSATION_ID, null, store);
    expect(readActiveAssistantGeneration(SUBJECT, CONVERSATION_ID, store)).toBeNull();

    store.setItem(
      "agricore.assistant.active-generation.v1:"
        + `${SUBJECT}:${CONVERSATION_ID}`,
      "not-a-generation-id",
    );
    expect(readActiveAssistantGeneration(SUBJECT, CONVERSATION_ID, store)).toBeNull();
  });

  it("degrades safely when browser storage is unavailable", () => {
    const unavailable: AssistantGenerationSessionStore = {
      getItem: () => {
        throw new DOMException("Denied", "SecurityError");
      },
      setItem: () => {
        throw new DOMException("Denied", "SecurityError");
      },
      removeItem: () => {
        throw new DOMException("Denied", "SecurityError");
      },
    };

    expect(readActiveAssistantGeneration(SUBJECT, CONVERSATION_ID, unavailable)).toBeNull();
    expect(() => writeActiveAssistantGeneration(
      SUBJECT,
      CONVERSATION_ID,
      GENERATION_ID,
      unavailable,
    )).not.toThrow();
  });
});
