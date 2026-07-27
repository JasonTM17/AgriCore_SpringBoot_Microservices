import { describe, expect, it } from "vitest";

import { ApiClientError } from "../../lib/api/errors";
import { mapLoginError } from "./login-validation";

describe("mapLoginError", () => {
  it.each([
    ["INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng."],
    [
      "ACCOUNT_LOCKED",
      "Tài khoản tạm khóa sau nhiều lần đăng nhập sai. Thử lại sau 15 phút.",
    ],
    ["ACCOUNT_DISABLED", "Tài khoản đã bị vô hiệu hóa. Liên hệ quản trị viên."],
    ["RATE_LIMITED", "Quá nhiều lần thử. Vui lòng chờ rồi thử lại."],
    ["ORIGIN_FORBIDDEN", "Nguồn gốc trình duyệt không được phép đăng nhập."],
    ["UNEXPECTED_FAILURE", "Không thể đăng nhập. Vui lòng thử lại."],
  ])("maps %s without exposing the server message", (code, expected) => {
    const error = new ApiClientError(
      401,
      {
        timestamp: "2026-07-18T00:00:00Z",
        status: 401,
        error: "Unauthorized",
        code,
        message: "internal server detail",
        path: "/api/v1/auth/web/login",
      },
      "fallback",
    );

    expect(mapLoginError(error)).toBe(expected);
  });

  it("gives a recovery action for network failures", () => {
    expect(mapLoginError(new TypeError("Failed to fetch"))).toBe(
      "Không thể kết nối máy chủ. Kiểm tra mạng và thử lại.",
    );
  });
});
