import { describe, expect, it } from "vitest";

import { ApiClientError } from "../../lib/api/errors";
import { AssistantGenerationCommandError } from "./assistant-generation-command";
import {
  assistantCapabilityMessage,
  assistantErrorMessage,
  assistantFailureKind,
  assistantFailureMessage,
  assistantSupportCode,
} from "./assistant-error-policy";

describe("assistant error policy", () => {
  it("maps known command and provider codes to safe Vietnamese guidance", () => {
    const promptError = new AssistantGenerationCommandError(
      "PROMPT_REQUIRED",
      "internal prompt detail",
    );
    expect(assistantErrorMessage(promptError)).toBe("Hãy nhập câu hỏi trước khi gửi.");
    expect(assistantFailureMessage("AI_PROVIDER_RATE_LIMITED"))
      .toBe("Nhà cung cấp AI đang giới hạn lưu lượng. Hãy thử lại sau.");
  });

  it("never exposes raw server messages or unsafe support codes", () => {
    const body = {
      timestamp: "2026-07-21T00:00:00Z",
      status: 500,
      error: "Internal Server Error",
      code: "unsafe code <token>",
      message: "provider leaked api-key and stack trace",
      path: "/api/v1/assistant",
    };
    const error = new ApiClientError(500, body, "fallback");

    expect(assistantErrorMessage(error)).toBe("Dịch vụ trợ lý đang tạm gián đoạn.");
    expect(assistantErrorMessage(error)).not.toContain(body.message);
    expect(assistantSupportCode(error)).toBeNull();
  });

  it("explains unavailable capabilities without trusting provider prose", () => {
    expect(assistantCapabilityMessage({
      provider: "openai",
      available: false,
      streaming: false,
      reasonCode: "AI_PROVIDER_CONFIGURATION_MISSING",
    })).toBe("Chatbot chưa được cấu hình nhà cung cấp và model.");
    expect(assistantCapabilityMessage({
      provider: "openai",
      available: true,
      streaming: true,
      reasonCode: null,
    })).toBeNull();
  });

  it("classifies safe UI outcomes without exposing provider details", () => {
    expect(assistantFailureKind("ASSISTANT_REQUEST_BUDGET_EXCEEDED")).toBe("LIMITED");
    expect(assistantFailureKind("AI_OUTPUT_CITATION_UNAUTHORIZED")).toBe("REFUSED");
    expect(assistantFailureKind("AI_OUTPUT_POLICY_UNAVAILABLE")).toBe("UNAVAILABLE");
    expect(assistantFailureKind("AI_PROVIDER_UNAVAILABLE")).toBe("UNAVAILABLE");
    expect(assistantFailureKind("AI_PROVIDER_PROTOCOL_ERROR")).toBe("ERROR");
    expect(assistantFailureKind("unsafe code <secret>")).toBe("ERROR");
    expect(assistantFailureKind(null)).toBeNull();
  });
});
