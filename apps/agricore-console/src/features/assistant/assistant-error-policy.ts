import { ApiClientError } from "../../lib/api/errors";
import type { AssistantCapabilitiesResponse } from "../../lib/api/types";
import { AssistantConversationValidationError } from "./assistant-conversation-validation";
import { AssistantGenerationCommandError } from "./assistant-generation-command";

const SAFE_CODE_PATTERN = /^[A-Z0-9_]{1,128}$/;
const ERROR_MESSAGES: Readonly<Record<string, string>> = {
  AI_PROVIDER_ADAPTER_UNAVAILABLE: "Bộ kết nối AI chưa sẵn sàng trên máy chủ.",
  AI_PROVIDER_AUTHENTICATION_FAILED: "Nhà cung cấp AI từ chối thông tin xác thực.",
  AI_PROVIDER_CIRCUIT_OPEN: "Nhà cung cấp AI đang tạm ngắt sau nhiều lỗi liên tiếp.",
  AI_PROVIDER_CONFIGURATION_MISSING: "Chatbot chưa được cấu hình nhà cung cấp và model.",
  AI_PROVIDER_RATE_LIMITED: "Nhà cung cấp AI đang giới hạn lưu lượng. Hãy thử lại sau.",
  AI_PROVIDER_REQUEST_REJECTED: "Nhà cung cấp AI từ chối yêu cầu này.",
  AI_PROVIDER_RESPONSE_TOO_LARGE: "Phản hồi AI vượt giới hạn an toàn.",
  AI_PROVIDER_TIMEOUT: "Nhà cung cấp AI phản hồi quá chậm.",
  AI_PROVIDER_UNAVAILABLE: "Nhà cung cấp AI tạm thời không khả dụng.",
  ASSISTANT_DRAFT_TOO_LARGE: "Phản hồi trực tiếp vượt giới hạn an toàn.",
  ASSISTANT_RUNNER_FAILED: "Kết nối nhận phản hồi đã dừng ngoài dự kiến.",
  ASSISTANT_STREAM_INTERRUPTED: "Kết nối trực tiếp bị gián đoạn.",
  CONVERSATION_FARM_REQUIRED: "Hãy chọn một nông trại hợp lệ cho phạm vi này.",
  CONVERSATION_NOT_FOUND: "Hội thoại không tồn tại hoặc quyền truy cập đã thay đổi.",
  CONVERSATION_NOT_OPEN: "Hội thoại đã lưu trữ nên không thể gửi câu hỏi mới.",
  CONVERSATION_TITLE_REQUIRED: "Tên hội thoại không được để trống.",
  CONVERSATION_TITLE_TOO_LONG: "Tên hội thoại không được vượt quá 200 ký tự.",
  GENERATION_ALREADY_ACTIVE: "Hội thoại đang xử lý một câu hỏi khác.",
  GENERATION_EVENT_REPLAY_EXPIRED: "Dữ liệu khôi phục đã hết hạn. Hãy tải lại lịch sử.",
  GENERATION_STREAM_CAPACITY_EXCEEDED: "Máy chủ đang có quá nhiều kết nối trực tiếp.",
  IDEMPOTENCY_KEY_UNAVAILABLE: "Trình duyệt không thể tạo khóa gửi an toàn.",
  INVALID_ASSISTANT_CAPABILITIES: "Thông tin khả năng chatbot không hợp lệ.",
  INVALID_CONVERSATION_PAGE: "Danh sách hội thoại không khớp hợp đồng API.",
  INVALID_CONVERSATION_RESPONSE: "Phản hồi hội thoại không khớp yêu cầu đã gửi.",
  INVALID_GENERATION_RESPONSE: "Phản hồi tạo nội dung không khớp hội thoại hiện tại.",
  INVALID_MESSAGE_PAGE: "Lịch sử tin nhắn không khớp hội thoại hiện tại.",
  PROMPT_REQUIRED: "Hãy nhập câu hỏi trước khi gửi.",
  PROMPT_TOO_LONG: "Câu hỏi vượt quá giới hạn 200.000 ký tự.",
};

function errorCode(error: unknown): string | null {
  if (error instanceof ApiClientError
    || error instanceof AssistantConversationValidationError
    || error instanceof AssistantGenerationCommandError) return error.code;
  return null;
}

export function assistantSupportCode(error: unknown): string | null {
  const code = errorCode(error);
  return code && SAFE_CODE_PATTERN.test(code) ? code : null;
}

export function assistantErrorMessage(error: unknown): string {
  const code = errorCode(error);
  if (code && ERROR_MESSAGES[code]) return ERROR_MESSAGES[code];
  if (error instanceof ApiClientError) {
    if (error.status === 401) return "Phiên đăng nhập đã hết hạn.";
    if (error.status === 403) return "Bạn không có quyền thực hiện thao tác này.";
    if (error.status === 404) return "Dữ liệu không còn khả dụng.";
    if (error.status === 409) return "Dữ liệu vừa thay đổi. Hãy tải lại rồi thử lại.";
    if (error.status === 429) return "Hệ thống đang giới hạn lưu lượng. Hãy thử lại sau.";
    if (error.status >= 500) return "Dịch vụ trợ lý đang tạm gián đoạn.";
  }
  return "Không thể hoàn tất yêu cầu. Kiểm tra kết nối rồi thử lại.";
}

export function assistantFailureMessage(code: string | null): string | null {
  if (!code) return null;
  return ERROR_MESSAGES[code] ?? "Chatbot không thể hoàn tất phản hồi này.";
}

export function assistantCapabilityMessage(
  capabilities: AssistantCapabilitiesResponse | undefined,
): string | null {
  if (!capabilities) return null;
  if (capabilities.available && capabilities.streaming) return null;
  return assistantFailureMessage(capabilities.reasonCode)
    ?? "Chatbot hiện chưa sẵn sàng nhận câu hỏi mới.";
}
