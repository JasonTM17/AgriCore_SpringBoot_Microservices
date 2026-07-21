import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { FetchFn } from "../../lib/api/event-stream-test-fixtures";
import { jsonResponse, requestUrl } from "../../lib/api/event-stream-test-fixtures";
import type { UserResponse } from "../../lib/api/types";
import { SessionContext } from "../../lib/auth/session-context";
import { FarmScopeContext } from "../farm/farm-scope-context";
import {
  assistantConversationPage,
  assistantMessagePage,
} from "./assistant-conversation-test-fixtures";
import { controllerApi } from "./assistant-generation-controller-test-fixtures";
import { AssistantPage } from "./assistant-page";
import { createAssistantQueryTestWrapper } from "./assistant-query-test-wrapper";

const USER = {
  id: "90000000-0000-0000-0000-000000000001",
  email: "manager@agricore.test",
  fullName: "Quản lý nông trại",
  status: "ACTIVE",
  roles: ["FARM_MANAGER"],
  lastLoginAt: null,
  createdAt: "2026-07-21T00:00:00Z",
} satisfies UserResponse;

describe("AssistantPage", () => {
  beforeEach(() => globalThis.sessionStorage.clear());

  it("wires authenticated conversations, capabilities, and history into chat", async () => {
    const fetchImpl = vi.fn<FetchFn>((input) => {
      const url = new URL(requestUrl(input), "http://agricore.test");
      if (url.pathname.endsWith("/capabilities")) {
        return Promise.resolve(jsonResponse(200, {
          provider: "openai",
          available: true,
          streaming: true,
          reasonCode: null,
        }));
      }
      if (url.pathname.endsWith("/messages")) {
        return Promise.resolve(jsonResponse(200, assistantMessagePage()));
      }
      if (url.pathname.endsWith("/conversations")) {
        return Promise.resolve(jsonResponse(200, assistantConversationPage()));
      }
      return Promise.resolve(new Response(null, { status: 404 }));
    });
    const api = controllerApi(fetchImpl);
    const { queryClient, Wrapper } = createAssistantQueryTestWrapper();
    const { unmount } = render(
      <Wrapper>
        <SessionContext.Provider value={{
          status: "authenticated",
          user: USER,
          accessToken: "access-token",
          api,
          login: vi.fn(),
          logout: vi.fn(),
          refreshSession: vi.fn(),
        }}>
          <FarmScopeContext.Provider value={{ activeFarm: null, selectFarm: vi.fn() }}>
            <AssistantPage />
          </FarmScopeContext.Provider>
        </SessionContext.Provider>
      </Wrapper>,
    );

    await waitFor(() => expect(screen.getByText("Mùa vụ đang ổn định."))
      .toBeInTheDocument());
    expect(screen.getByRole("heading", { name: "Theo dõi mùa vụ" })).toBeInTheDocument();
    expect(screen.getByText("Provider: openai")).toBeInTheDocument();
    expect(screen.getByLabelText("Câu hỏi cho trợ lý")).toBeEnabled();
    expect(fetchImpl.mock.calls.some(([input]) => requestUrl(input).includes("size=100")))
      .toBe(true);
    unmount();
    queryClient.clear();
  });
});
