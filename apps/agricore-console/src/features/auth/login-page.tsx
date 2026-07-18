import { Navigate, useNavigate, useSearch } from "@tanstack/react-router";
import { type FormEvent, useState } from "react";
import { z } from "zod";

import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { ApiClientError } from "../../lib/api/errors";
import { useSession } from "../../lib/auth/session";

const loginSchema = z.object({
  email: z.email("Email không hợp lệ"),
  password: z.string().min(8, "Mật khẩu tối thiểu 8 ký tự"),
});

function mapLoginError(error: unknown): string {
  if (error instanceof ApiClientError) {
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
        return error.message || "Không thể đăng nhập.";
    }
  }
  return "Không thể kết nối máy chủ. Kiểm tra mạng và thử lại.";
}

export function LoginPage() {
  const { login, status } = useSession();
  const navigate = useNavigate();
  const search = useSearch({ from: "/login" });
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (status === "authenticated") {
    return <Navigate to={search.redirect || "/"} replace />;
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    const parsed = loginSchema.safeParse({ email, password });
    if (!parsed.success) {
      const next: { email?: string; password?: string } = {};
      for (const issue of parsed.error.issues) {
        const key = issue.path[0];
        if (key === "email" || key === "password") {
          next[key] = issue.message;
        }
      }
      setFieldErrors(next);
      return;
    }

    setFieldErrors({});
    setSubmitting(true);
    try {
      await login(parsed.data);
      await navigate({ to: search.redirect || "/" });
    } catch (error) {
      setFormError(mapLoginError(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="grid min-h-screen lg:grid-cols-2">
      <section className="hidden bg-forest-900 px-12 py-16 text-white lg:flex lg:flex-col lg:justify-between">
        <div>
          <p className="text-sm font-semibold uppercase tracking-[0.2em] text-harvest-100">
            AgriCore
          </p>
          <h1 className="mt-6 max-w-md text-4xl font-bold leading-tight">
            Vận hành nông nghiệp trên một hệ thống thống nhất
          </h1>
          <p className="mt-4 max-w-md text-white/80 leading-7">
            Đăng nhập để quản lý nông trại, mùa vụ, thu hoạch, kho và truy xuất nguồn gốc.
          </p>
        </div>
        <p className="text-sm text-white/60">Phiên làm việc được bảo vệ bằng JWT RS256 + cookie refresh.</p>
      </section>

      <section className="grid place-items-center bg-canvas px-6 py-12">
        <form
          onSubmit={(event) => void onSubmit(event)}
          className="w-full max-w-md rounded-card border border-border bg-surface p-8 shadow-sm"
          noValidate
          aria-labelledby="login-title"
        >
          <p className="text-sm font-semibold uppercase tracking-[0.18em] text-harvest-600">
            Đăng nhập
          </p>
          <h2 id="login-title" className="mt-2 text-2xl font-bold text-ink">
            Chào mừng trở lại
          </h2>
          <p className="mt-2 text-sm text-muted">
            Sử dụng tài khoản nội bộ AgriCore. Refresh token không bao giờ lưu trong JavaScript.
          </p>

          <div className="mt-8 grid gap-4">
            <Input
              label="Email"
              name="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              error={fieldErrors.email}
              required
            />
            <Input
              label="Mật khẩu"
              name="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              error={fieldErrors.password}
              required
            />
          </div>

          {formError ? (
            <div
              className="mt-4 rounded-control border border-danger/30 bg-danger/5 px-3 py-2 text-sm text-danger"
              role="alert"
            >
              {formError}
            </div>
          ) : null}

          <Button type="submit" className="mt-6 w-full" disabled={submitting || status === "bootstrapping"}>
            {submitting ? "Đang đăng nhập..." : "Đăng nhập"}
          </Button>
        </form>
      </section>
    </main>
  );
}
