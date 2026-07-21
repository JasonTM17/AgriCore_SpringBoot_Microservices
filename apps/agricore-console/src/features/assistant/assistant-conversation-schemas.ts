import { z } from "zod";

import { isAssistantIdentifier } from "./assistant-identifiers";

const safeIntegerSchema = z.number().int().min(0).max(Number.MAX_SAFE_INTEGER);
const uuidSchema = z.string().refine(isAssistantIdentifier);
const dateTimeSchema = z.iso.datetime({ offset: true });
const nullableDateTimeSchema = dateTimeSchema.nullable();
const pageMetadataShape = {
  page: z.number().int().min(0),
  size: z.number().int().min(1).max(100),
  totalElements: safeIntegerSchema,
  totalPages: z.number().int().min(0),
  first: z.boolean(),
  last: z.boolean(),
};

export const assistantCapabilitiesSchema = z.object({
  provider: z.string().min(1).max(32),
  available: z.boolean(),
  streaming: z.boolean(),
  reasonCode: z.string().nullable(),
}).strict();
const conversationSchema = z.object({
  id: uuidSchema,
  title: z.string().min(1).max(200).refine((title) => title.trim().length > 0),
  contextType: z.enum(["ENTERPRISE", "FARM"]),
  farmId: uuidSchema.nullable(),
  status: z.enum(["OPEN", "ARCHIVED"]),
  roleSnapshot: z.array(z.string()),
  nextMessageSequence: safeIntegerSchema,
  version: safeIntegerSchema,
  createdAt: dateTimeSchema,
  updatedAt: dateTimeSchema,
  archivedAt: nullableDateTimeSchema,
  purgeAfter: nullableDateTimeSchema,
}).strict().superRefine((conversation, context) => {
  if ((conversation.contextType === "FARM") !== (conversation.farmId !== null)) {
    context.addIssue({ code: "custom", message: "Conversation context and farm ID disagree" });
  }
});
export const assistantConversationPageSchema = z.object({
  ...pageMetadataShape,
  content: z.array(conversationSchema),
}).strict();
const messageSchema = z.object({
  id: uuidSchema,
  conversationId: uuidSchema,
  generationId: uuidSchema.nullable(),
  sequenceNo: safeIntegerSchema,
  role: z.enum(["USER", "ASSISTANT"]),
  content: z.string(),
  tokenCount: safeIntegerSchema.nullable(),
  createdAt: dateTimeSchema,
}).strict();
export const assistantMessagePageSchema = z.object({
  ...pageMetadataShape,
  content: z.array(messageSchema),
}).strict();
export { conversationSchema as assistantConversationSchema };
