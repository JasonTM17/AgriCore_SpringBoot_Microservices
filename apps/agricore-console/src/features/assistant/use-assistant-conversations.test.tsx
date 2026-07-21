import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { FetchFn } from "../../lib/api/event-stream-test-fixtures";
import { jsonResponse, requestUrl } from "../../lib/api/event-stream-test-fixtures";
import {
  assistantConversation,
  assistantConversationPage,
  TEST_ASSISTANT_CONVERSATION_ID,
} from "./assistant-conversation-test-fixtures";
import { createAssistantQueryTestWrapper } from "./assistant-query-test-wrapper";
import { assistantQueryKeys } from "./assistant-query-keys";
import { controllerApi } from "./assistant-generation-controller-test-fixtures";
import { useAssistantConversations } from "./use-assistant-conversations";

const SUBJECT = "90000000-0000-0000-0000-000000000001";

function archivedConversation() {
  return assistantConversation({
    status: "ARCHIVED",
    archivedAt: "2026-07-21T01:00:00Z",
    purgeAfter: "2026-10-19T01:00:00Z",
  });
}

describe("useAssistantConversations", () => {
  it("loads capabilities, switches status, and applies correlated mutations", async () => {
    const requestBodies: unknown[] = [];
    const fetchImpl = vi.fn<FetchFn>((input, init) => {
      const url = new URL(requestUrl(input), "http://agricore.test");
      if (url.pathname.endsWith("/capabilities")) {
        return Promise.resolve(jsonResponse(200, {
          provider: "openai",
          available: true,
          streaming: true,
          reasonCode: null,
        }));
      }
      if (url.pathname.endsWith("/archive")) {
        return Promise.resolve(jsonResponse(200, archivedConversation()));
      }
      if (url.pathname.endsWith("/conversations") && init?.method === "POST") {
        if (typeof init.body !== "string") {
          return Promise.reject(new Error("Expected a JSON request body"));
        }
        const body = JSON.parse(init.body) as {
          title: string;
          contextType: "ENTERPRISE" | "FARM";
          farmId: string | null;
        };
        requestBodies.push(body);
        return Promise.resolve(jsonResponse(200, assistantConversation({
          title: body.title,
          contextType: body.contextType,
          farmId: body.farmId,
        })));
      }
      if (url.pathname.endsWith("/conversations")) {
        const status = url.searchParams.get("status") ?? "OPEN";
        const conversation = status === "ARCHIVED"
          ? archivedConversation()
          : assistantConversation();
        return Promise.resolve(jsonResponse(
          200,
          assistantConversationPage({ content: [conversation] }),
        ));
      }
      return Promise.reject(new Error(`Unexpected request: ${url.pathname}`));
    });
    const api = controllerApi(fetchImpl);
    const { queryClient, Wrapper } = createAssistantQueryTestWrapper();
    const { result, unmount } = renderHook(() => useAssistantConversations({
      api,
      subject: SUBJECT,
      enabled: true,
    }), { wrapper: Wrapper });

    await waitFor(() => {
      expect(result.current.capabilities.isSuccess).toBe(true);
      expect(result.current.conversations.data?.content[0]?.status).toBe("OPEN");
    });
    act(() => result.current.selectStatus("ARCHIVED"));
    await waitFor(() => {
      expect(result.current.conversations.data?.content[0]?.status).toBe("ARCHIVED");
    });

    await act(async () => {
      await result.current.create.mutateAsync({
        title: "  Báo cáo toàn doanh nghiệp  ",
        contextType: "ENTERPRISE",
      });
    });
    expect(requestBodies).toContainEqual({
      title: "Báo cáo toàn doanh nghiệp",
      contextType: "ENTERPRISE",
      farmId: null,
    });
    expect(result.current.status).toBe("OPEN");

    await act(async () => {
      await result.current.archive.mutateAsync(TEST_ASSISTANT_CONVERSATION_ID);
    });
    expect(queryClient.getQueryData(
      assistantQueryKeys.conversation(SUBJECT, TEST_ASSISTANT_CONVERSATION_ID),
    )).toMatchObject({ status: "ARCHIVED" });
    unmount();
    queryClient.clear();
  });

  it("does not request authenticated data while disabled", async () => {
    const fetchImpl = vi.fn<FetchFn>();
    const { queryClient, Wrapper } = createAssistantQueryTestWrapper();
    const { unmount } = renderHook(() => useAssistantConversations({
      api: controllerApi(fetchImpl),
      subject: "unauthenticated",
      enabled: false,
    }), { wrapper: Wrapper });

    await Promise.resolve();
    expect(fetchImpl).not.toHaveBeenCalled();
    unmount();
    queryClient.clear();
  });
});
