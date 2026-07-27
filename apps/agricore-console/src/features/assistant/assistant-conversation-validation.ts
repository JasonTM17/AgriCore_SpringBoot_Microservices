import type { z } from "zod";

import type {
  AssistantCapabilitiesResponse,
  AssistantConversationPageResponse,
  AssistantConversationResponse,
  AssistantConversationStatus,
  AssistantMessagePageResponse,
  CreateAssistantConversationRequest,
} from "../../lib/api/types";
import {
  assistantCapabilitiesSchema,
  assistantConversationPageSchema,
  assistantConversationSchema,
  assistantMessagePageSchema,
} from "./assistant-conversation-schemas";
import { isAssistantIdentifier } from "./assistant-identifiers";

export type AssistantConversationValidationErrorCode =
  | "CONVERSATION_TITLE_REQUIRED"
  | "CONVERSATION_TITLE_TOO_LONG"
  | "CONVERSATION_FARM_REQUIRED"
  | "INVALID_ASSISTANT_CAPABILITIES"
  | "INVALID_CONVERSATION_RESPONSE"
  | "INVALID_CONVERSATION_PAGE"
  | "INVALID_MESSAGE_PAGE";

export class AssistantConversationValidationError extends Error {
  readonly code: AssistantConversationValidationErrorCode;

  constructor(code: AssistantConversationValidationErrorCode, message: string) {
    super(message);
    this.name = "AssistantConversationValidationError";
    this.code = code;
  }
}

function invalid(code: AssistantConversationValidationErrorCode): never {
  throw new AssistantConversationValidationError(code, "Assistant API response violated its contract");
}

function parseSchema<T>(
  schema: z.ZodType<T>,
  value: unknown,
  code: AssistantConversationValidationErrorCode,
): T {
  const result = schema.safeParse(value);
  return result.success ? result.data : invalid(code);
}

function validatePageMetadata(
  page: { page: number; size: number; totalElements: number; totalPages: number; content: unknown[] },
  expectedPage: number,
  expectedSize: number,
  code: AssistantConversationValidationErrorCode,
): void {
  const calculatedPages = Math.ceil(page.totalElements / page.size);
  if (page.page !== expectedPage
    || page.size !== expectedSize
    || page.totalPages !== calculatedPages
    || page.content.length > page.size) invalid(code);
}

export function normalizeCreateAssistantConversationRequest(
  request: CreateAssistantConversationRequest,
): CreateAssistantConversationRequest {
  const title = request.title.trim();
  if (!title) {
    throw new AssistantConversationValidationError(
      "CONVERSATION_TITLE_REQUIRED",
      "Conversation title is required",
    );
  }
  if (title.length > 200) {
    throw new AssistantConversationValidationError(
      "CONVERSATION_TITLE_TOO_LONG",
      "Conversation title exceeds the supported limit",
    );
  }
  if (request.contextType === "FARM") {
    if (!isAssistantIdentifier(request.farmId)) {
      throw new AssistantConversationValidationError(
        "CONVERSATION_FARM_REQUIRED",
        "Farm context requires a valid farm ID",
      );
    }
    return { title, contextType: "FARM", farmId: request.farmId };
  }
  return { title, contextType: "ENTERPRISE", farmId: null };
}

export function validateAssistantCapabilities(value: unknown): AssistantCapabilitiesResponse {
  return parseSchema(assistantCapabilitiesSchema, value, "INVALID_ASSISTANT_CAPABILITIES");
}

export function validateCreatedAssistantConversation(
  value: unknown,
  request: CreateAssistantConversationRequest,
): AssistantConversationResponse {
  const conversation = parseSchema(
    assistantConversationSchema,
    value,
    "INVALID_CONVERSATION_RESPONSE",
  );
  if (conversation.status !== "OPEN"
    || conversation.title !== request.title
    || conversation.contextType !== request.contextType
    || conversation.farmId !== (request.farmId ?? null)) invalid("INVALID_CONVERSATION_RESPONSE");
  return conversation;
}

export function validateArchivedAssistantConversation(
  value: unknown,
  expectedConversationId: string,
): AssistantConversationResponse {
  const conversation = parseSchema(
    assistantConversationSchema,
    value,
    "INVALID_CONVERSATION_RESPONSE",
  );
  if (!isAssistantIdentifier(expectedConversationId)
    || conversation.id !== expectedConversationId
    || conversation.status !== "ARCHIVED"
    || conversation.archivedAt === null
    || conversation.purgeAfter === null) invalid("INVALID_CONVERSATION_RESPONSE");
  return conversation;
}

export function validateAssistantConversationPage(
  value: unknown,
  expectedStatus: AssistantConversationStatus,
  expectedPage: number,
  expectedSize: number,
): AssistantConversationPageResponse {
  const page = parseSchema(assistantConversationPageSchema, value, "INVALID_CONVERSATION_PAGE");
  validatePageMetadata(page, expectedPage, expectedSize, "INVALID_CONVERSATION_PAGE");
  if (page.content.some((conversation) => conversation.status !== expectedStatus)) {
    invalid("INVALID_CONVERSATION_PAGE");
  }
  return page;
}

export function validateAssistantMessagePage(
  value: unknown,
  expectedConversationId: string,
  expectedPage: number,
  expectedSize: number,
): AssistantMessagePageResponse {
  const page = parseSchema(assistantMessagePageSchema, value, "INVALID_MESSAGE_PAGE");
  validatePageMetadata(page, expectedPage, expectedSize, "INVALID_MESSAGE_PAGE");
  const ids = new Set<string>();
  let previousSequence = -1;
  for (const message of page.content) {
    if (message.conversationId !== expectedConversationId
      || ids.has(message.id)
      || message.sequenceNo <= previousSequence) invalid("INVALID_MESSAGE_PAGE");
    ids.add(message.id);
    previousSequence = message.sequenceNo;
  }
  return page;
}
