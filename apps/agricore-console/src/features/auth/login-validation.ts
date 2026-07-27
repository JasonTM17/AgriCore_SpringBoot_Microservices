import { z } from "zod";

import { ApiClientError } from "../../lib/api/errors";

export const loginSchema = z.object({
  email: z.email("Email không hợp lệ"),
  password: z.string().min(8, "Mật khẩu tối thiểu 8 ký tự"),
});

export function mapLoginError(error: unknown): string {
  if (!(error instanceof ApiClientError)) {
    return "Không thể kết nối máy chủ. Kiểm tra mạng và thử lại.";
  }

  switch (error.code) {
    case "INVALID_CREDENTIALS":
      return "Email hoặc mật khẩu không đúng.";
    case "ACCOUNT_LOCKED":
      return "Tài khoản tạm khóa sau nhiều lần đăng nhập sai. Thử lại sau 15 phút.";
    case "ACCOUNT_DISABLED":
      return "Tài khoản đã bị vô hiệu hóa. Liên hệ quản trị viên.";
    case "RATE_LIMITED":
      return "Quá nhiều lần thử. Vui lòng chờ rồi thử lại.";
    case "ORIGIN_FORBIDDEN":
    case "ORIGIN_REQUIRED":
      return "Nguồn gốc trình duyệt không được phép đăng nhập.";
    default:
      return "Không thể đăng nhập. Vui lòng thử lại.";
  }
}
