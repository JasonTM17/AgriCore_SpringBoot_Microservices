import { isAssistantIdentifier } from "./assistant-identifiers";

const STORAGE_PREFIX = "agricore.assistant.active-generation.v1";

export interface AssistantGenerationSessionStore {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

function storageKey(subject: string, conversationId: string): string {
  return `${STORAGE_PREFIX}:${encodeURIComponent(subject)}:${encodeURIComponent(conversationId)}`;
}

export function readActiveAssistantGeneration(
  subject: string,
  conversationId: string,
  store?: AssistantGenerationSessionStore,
): string | null {
  try {
    const key = storageKey(subject, conversationId);
    const activeStore = store ?? globalThis.sessionStorage;
    const generationId = activeStore.getItem(key);
    if (generationId === null || isAssistantIdentifier(generationId)) return generationId;
    activeStore.removeItem(key);
    return null;
  } catch {
    return null;
  }
}

export function writeActiveAssistantGeneration(
  subject: string,
  conversationId: string,
  generationId: string | null,
  store?: AssistantGenerationSessionStore,
): void {
  try {
    const key = storageKey(subject, conversationId);
    const activeStore = store ?? globalThis.sessionStorage;
    if (generationId === null) {
      activeStore.removeItem(key);
    } else if (isAssistantIdentifier(generationId)) {
      activeStore.setItem(key, generationId);
    }
  } catch {
    // Storage can be unavailable in hardened browser contexts; streaming still works in-memory.
  }
}
