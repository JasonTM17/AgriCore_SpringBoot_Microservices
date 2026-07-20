import { describe, expect, it, vi } from "vitest";

import { ApiClient } from "../../lib/api/client";
import type {
  CreateAssistantConversationRequest,
  CreateAssistantGenerationRequest,
} from "../../lib/api/types";
import {
  archiveAssistantConversation,
  cancelAssistantGeneration,
  createAssistantConversation,
  getAssistantCapabilities,
  getAssistantConversation,
  getAssistantGeneration,
  listAssistantConversations,
  listAssistantGenerationEvents,
  listAssistantMessages,
  submitAssistantGeneration,
} from "./assistant-api";

type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

function jsonResponse(body: unknown): Promise<Response> {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  }));
}

function client(fetchImpl: FetchFn): ApiClient {
  return new ApiClient({
    getAccessToken: () => "access-token",
    setAccessToken: () => undefined,
    fetchImpl,
  });
}

describe("assistant API", () => {
  it("lists capabilities and conversations with contract query parameters", async () => {
    const fetchImpl: FetchFn = vi.fn(() => jsonResponse({ content: [] }));
    const api = client(fetchImpl);
    const signal = new AbortController().signal;

    await getAssistantCapabilities(api, signal);
    await listAssistantConversations(api, { status: "ARCHIVED", page: 2, size: 25 }, signal);
    await listAssistantConversations(api, { page: 0, size: 20 });

    expect(vi.mocked(fetchImpl).mock.calls.map(([input, init]) => [input, init?.method]))
      .toEqual([
        ["/api/v1/assistant/capabilities", "GET"],
        ["/api/v1/assistant/conversations?status=ARCHIVED&page=2&size=25", "GET"],
        ["/api/v1/assistant/conversations?page=0&size=20", "GET"],
      ]);
    expect(vi.mocked(fetchImpl).mock.calls[0]?.[1]?.signal).toBeInstanceOf(AbortSignal);
  });

  it("creates a conversation with the typed request unchanged", async () => {
    const fetchImpl: FetchFn = vi.fn(() => jsonResponse({}));
    const request: CreateAssistantConversationRequest = {
      title: "Theo dõi mùa vụ",
      contextType: "FARM",
      farmId: "20000000-0000-0000-0000-000000000001",
    };

    await createAssistantConversation(client(fetchImpl), request);

    const [input, init] = vi.mocked(fetchImpl).mock.calls[0] ?? [];
    expect(input).toBe("/api/v1/assistant/conversations");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(JSON.stringify(request));
  });

  it("encodes conversation IDs for detail, archive, and message paging", async () => {
    const fetchImpl: FetchFn = vi.fn(() => jsonResponse({ content: [] }));
    const api = client(fetchImpl);
    const signal = new AbortController().signal;

    await getAssistantConversation(api, "conversation/id?part");
    await archiveAssistantConversation(api, "conversation/id?part");
    await listAssistantMessages(api, "conversation/id?part", { page: 1, size: 50 }, signal);

    expect(vi.mocked(fetchImpl).mock.calls.map(([input, init]) => [input, init?.method]))
      .toEqual([
        ["/api/v1/assistant/conversations/conversation%2Fid%3Fpart", "GET"],
        ["/api/v1/assistant/conversations/conversation%2Fid%3Fpart/archive", "POST"],
        [
          "/api/v1/assistant/conversations/conversation%2Fid%3Fpart/messages?page=1&size=50",
          "GET",
        ],
      ]);
    expect(vi.mocked(fetchImpl).mock.calls[2]?.[1]?.signal).toBeInstanceOf(AbortSignal);
  });

  it("submits the prompt with an idempotency header", async () => {
    const fetchImpl: FetchFn = vi.fn(() => jsonResponse({}));
    const request: CreateAssistantGenerationRequest = { prompt: "Tóm tắt công việc hôm nay" };

    await submitAssistantGeneration(
      client(fetchImpl),
      "conversation/id",
      request,
      "generation-key-1",
    );

    const [input, init] = vi.mocked(fetchImpl).mock.calls[0] ?? [];
    expect(input).toBe("/api/v1/assistant/conversations/conversation%2Fid/generations");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(JSON.stringify(request));
    expect(new Headers(init?.headers).get("Idempotency-Key")).toBe("generation-key-1");
  });

  it("encodes generation paths and requests bounded event replay", async () => {
    const fetchImpl: FetchFn = vi.fn(() => jsonResponse([]));
    const api = client(fetchImpl);
    const signal = new AbortController().signal;

    await getAssistantGeneration(api, "conversation/id", "generation/id?part");
    await cancelAssistantGeneration(api, "conversation/id", "generation/id?part");
    await listAssistantGenerationEvents(
      api,
      "conversation/id",
      "generation/id?part",
      { after: -1, limit: 100 },
      signal,
    );

    const base = "/api/v1/assistant/conversations/conversation%2Fid/generations/";
    expect(vi.mocked(fetchImpl).mock.calls.map(([input, init]) => [input, init?.method]))
      .toEqual([
        [`${base}generation%2Fid%3Fpart`, "GET"],
        [`${base}generation%2Fid%3Fpart/cancel`, "POST"],
        [`${base}generation%2Fid%3Fpart/events?after=-1&limit=100`, "GET"],
      ]);
    expect(vi.mocked(fetchImpl).mock.calls[2]?.[1]?.signal).toBeInstanceOf(AbortSignal);
  });
});
