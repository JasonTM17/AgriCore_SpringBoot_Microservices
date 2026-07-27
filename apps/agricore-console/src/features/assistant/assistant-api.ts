import type { ApiClient } from "../../lib/api/client";
import type {
  AssistantCapabilitiesResponse,
  AssistantConversationPageResponse,
  AssistantConversationResponse,
  AssistantConversationStatus,
  AssistantGenerationEventResponse,
  AssistantGenerationResponse,
  AssistantMessagePageResponse,
  CreateAssistantConversationRequest,
  CreateAssistantGenerationRequest,
} from "../../lib/api/types";

export interface AssistantConversationListParams {
  status?: AssistantConversationStatus;
  page: number;
  size: number;
}

export interface AssistantMessageListParams {
  page: number;
  size: number;
}

export interface AssistantGenerationEventListParams {
  after: number;
  limit: number;
}

function queryString(
  values: ReadonlyArray<readonly [string, string | number | undefined]>,
): string {
  const search = new URLSearchParams();
  values.forEach(([key, value]) => {
    if (value !== undefined) {
      search.set(key, String(value));
    }
  });
  return search.toString();
}

function conversationPath(conversationId: string): string {
  return `/api/v1/assistant/conversations/${encodeURIComponent(conversationId)}`;
}

function generationPath(conversationId: string, generationId: string): string {
  return `${conversationPath(conversationId)}/generations/${encodeURIComponent(generationId)}`;
}

export function getAssistantCapabilities(
  api: ApiClient,
  signal?: AbortSignal,
): Promise<AssistantCapabilitiesResponse> {
  return api.request<AssistantCapabilitiesResponse>("/api/v1/assistant/capabilities", {
    method: "GET",
    ...(signal ? { signal } : {}),
  });
}

export function listAssistantConversations(
  api: ApiClient,
  params: AssistantConversationListParams,
  signal?: AbortSignal,
): Promise<AssistantConversationPageResponse> {
  const search = queryString([
    ["status", params.status],
    ["page", params.page],
    ["size", params.size],
  ]);
  return api.request<AssistantConversationPageResponse>(
    `/api/v1/assistant/conversations?${search}`,
    { method: "GET", ...(signal ? { signal } : {}) },
  );
}

export function createAssistantConversation(
  api: ApiClient,
  request: CreateAssistantConversationRequest,
  signal?: AbortSignal,
): Promise<AssistantConversationResponse> {
  return api.request<AssistantConversationResponse>("/api/v1/assistant/conversations", {
    method: "POST",
    body: request,
    ...(signal ? { signal } : {}),
  });
}

export function getAssistantConversation(
  api: ApiClient,
  conversationId: string,
  signal?: AbortSignal,
): Promise<AssistantConversationResponse> {
  return api.request<AssistantConversationResponse>(conversationPath(conversationId), {
    method: "GET",
    ...(signal ? { signal } : {}),
  });
}

export function archiveAssistantConversation(
  api: ApiClient,
  conversationId: string,
  signal?: AbortSignal,
): Promise<AssistantConversationResponse> {
  return api.request<AssistantConversationResponse>(`${conversationPath(conversationId)}/archive`, {
    method: "POST",
    ...(signal ? { signal } : {}),
  });
}

export function listAssistantMessages(
  api: ApiClient,
  conversationId: string,
  params: AssistantMessageListParams,
  signal?: AbortSignal,
): Promise<AssistantMessagePageResponse> {
  const search = queryString([
    ["page", params.page],
    ["size", params.size],
  ]);
  return api.request<AssistantMessagePageResponse>(
    `${conversationPath(conversationId)}/messages?${search}`,
    { method: "GET", ...(signal ? { signal } : {}) },
  );
}

export function submitAssistantGeneration(
  api: ApiClient,
  conversationId: string,
  request: CreateAssistantGenerationRequest,
  idempotencyKey: string,
  signal?: AbortSignal,
): Promise<AssistantGenerationResponse> {
  return api.request<AssistantGenerationResponse>(
    `${conversationPath(conversationId)}/generations`,
    {
      method: "POST",
      body: request,
      headers: { "Idempotency-Key": idempotencyKey },
      ...(signal ? { signal } : {}),
    },
  );
}

export function getAssistantGeneration(
  api: ApiClient,
  conversationId: string,
  generationId: string,
  signal?: AbortSignal,
): Promise<AssistantGenerationResponse> {
  return api.request<AssistantGenerationResponse>(
    generationPath(conversationId, generationId),
    { method: "GET", ...(signal ? { signal } : {}) },
  );
}

export function cancelAssistantGeneration(
  api: ApiClient,
  conversationId: string,
  generationId: string,
  signal?: AbortSignal,
): Promise<AssistantGenerationResponse> {
  return api.request<AssistantGenerationResponse>(
    `${generationPath(conversationId, generationId)}/cancel`,
    { method: "POST", ...(signal ? { signal } : {}) },
  );
}

export function listAssistantGenerationEvents(
  api: ApiClient,
  conversationId: string,
  generationId: string,
  params: AssistantGenerationEventListParams,
  signal?: AbortSignal,
): Promise<AssistantGenerationEventResponse[]> {
  const search = queryString([
    ["after", params.after],
    ["limit", params.limit],
  ]);
  return api.request<AssistantGenerationEventResponse[]>(
    `${generationPath(conversationId, generationId)}/events?${search}`,
    { method: "GET", ...(signal ? { signal } : {}) },
  );
}
