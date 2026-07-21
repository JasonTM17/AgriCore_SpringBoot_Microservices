import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { FetchFn } from "../../lib/api/event-stream-test-fixtures";
import { jsonResponse, requestUrl } from "../../lib/api/event-stream-test-fixtures";
import {
  assistantMessage,
  assistantMessagePage,
  TEST_ASSISTANT_CONVERSATION_ID,
} from "./assistant-conversation-test-fixtures";
import { controllerApi } from "./assistant-generation-controller-test-fixtures";
import { createAssistantQueryTestWrapper } from "./assistant-query-test-wrapper";
import { useAssistantMessageHistory } from "./use-assistant-message-history";

const SUBJECT = "90000000-0000-0000-0000-000000000001";

function historyPage(page: number) {
  const idSuffix = String(page + 1).padStart(12, "0");
  return assistantMessagePage({
    page,
    totalElements: 201,
    totalPages: 3,
    first: page === 0,
    last: page === 2,
    content: [assistantMessage({
      id: `70000000-0000-0000-0000-${idSuffix}`,
      sequenceNo: page * 100,
    })],
  });
}

describe("useAssistantMessageHistory", () => {
  it("jumps to the bounded tail page and supports older/newer navigation", async () => {
    const requestedPages: number[] = [];
    const fetchImpl = vi.fn<FetchFn>((input) => {
      const url = new URL(requestUrl(input), "http://agricore.test");
      const page = Number(url.searchParams.get("page"));
      requestedPages.push(page);
      return Promise.resolve(jsonResponse(200, historyPage(page)));
    });
    const { queryClient, Wrapper } = createAssistantQueryTestWrapper();
    const { result, unmount } = renderHook(() => useAssistantMessageHistory({
      api: controllerApi(fetchImpl),
      subject: SUBJECT,
      conversationId: TEST_ASSISTANT_CONVERSATION_ID,
      enabled: true,
    }), { wrapper: Wrapper });

    await waitFor(() => {
      expect(result.current.page).toBe(2);
      expect(result.current.messages.data?.page).toBe(2);
    });
    expect(requestedPages).toEqual([0, 2]);
    expect(result.current.isAtLatest).toBe(true);

    act(() => result.current.goOlder());
    await waitFor(() => expect(result.current.messages.data?.page).toBe(1));
    expect(result.current).toMatchObject({
      page: 1,
      canGoOlder: true,
      canGoNewer: true,
      isAtLatest: false,
    });
    act(() => result.current.goNewer());
    await waitFor(() => expect(result.current.messages.data?.page).toBe(2));
    expect(result.current.isAtLatest).toBe(true);
    unmount();
    queryClient.clear();
  });

  it("does not build a request path from an invalid conversation ID", async () => {
    const fetchImpl = vi.fn<FetchFn>();
    const { queryClient, Wrapper } = createAssistantQueryTestWrapper();
    const { result, unmount } = renderHook(() => useAssistantMessageHistory({
      api: controllerApi(fetchImpl),
      subject: SUBJECT,
      conversationId: "../another-user",
      enabled: true,
    }), { wrapper: Wrapper });

    await Promise.resolve();
    expect(result.current.messages.fetchStatus).toBe("idle");
    expect(fetchImpl).not.toHaveBeenCalled();
    unmount();
    queryClient.clear();
  });
});
