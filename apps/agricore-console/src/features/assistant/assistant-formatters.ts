import type { AssistantConversationResponse } from "../../lib/api/types";

const dateTimeFormatter = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short",
  timeStyle: "short",
});

export function formatAssistantConversationTime(value: string): string {
  return dateTimeFormatter.format(new Date(value));
}

export function assistantConversationContextLabel(
  conversation: Pick<AssistantConversationResponse, "contextType">,
): string {
  return conversation.contextType === "FARM" ? "Nông trại" : "Doanh nghiệp";
}
