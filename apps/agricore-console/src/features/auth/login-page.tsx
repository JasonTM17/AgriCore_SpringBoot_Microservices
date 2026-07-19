import { Navigate, useNavigate, useSearch } from "@tanstack/react-router";
import { type FormEvent, useEffect, useRef, useState } from "react";

import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { useSession } from "../../lib/auth/session";
import { loginSchema, mapLoginError } from "./login-validation";

export function LoginPage() {
  const { login, status } = useSession();
  const navigate = useNavigate();
  const search = useSearch({ from: "/session/login" });
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const formErrorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (formError) {
      formErrorRef.current?.focus();
    }
  }, [formError]);

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
      if (next.email) {
        emailRef.current?.focus();
      } else {
        passwordRef.current?.focus();
      }
      return;
    }

    setFieldErrors({});
    setSubmitting(true);
    try {
      await login(parsed.data);
      await navigate({ to: search.redirect || "/" });
    } catch (error) {
      setPassword("");
      setFormError(mapLoginError(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="grid min-h-dvh lg:grid-cols-[45fr_55fr]">
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
        <div className="w-full max-w-[440px]">
          <form
            onSubmit={(event) => void onSubmit(event)}
            className="rounded-card border border-border bg-surface p-6 shadow-sm sm:p-8"
            noValidate
            aria-labelledby="login-title"
          >
            <p className="mb-6 text-base font-bold text-forest-900 lg:hidden">AgriCore</p>
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
                ref={emailRef}
                label="Email"
                name="email"
                type="email"
                autoComplete="username"
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value);
                  if (fieldErrors.email) {
                    setFieldErrors((current) => {
                      const next = { ...current };
                      delete next.email;
                      return next;
                    });
                  }
                }}
                error={fieldErrors.email}
                required
              />
              <Input
                ref={passwordRef}
                label="Mật khẩu"
                name="password"
                type={showPassword ? "text" : "password"}
                autoComplete="current-password"
                value={password}
                onChange={(event) => {
                  setPassword(event.target.value);
                  if (fieldErrors.password) {
                    setFieldErrors((current) => {
                      const next = { ...current };
                      delete next.password;
                      return next;
                    });
                  }
                }}
                error={fieldErrors.password}
                endAdornment={
                  <button
                    type="button"
                    className="h-9 rounded-control px-2 text-xs font-semibold text-forest-700 hover:bg-forest-50"
                    aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
                    aria-pressed={showPassword}
                    onClick={() => setShowPassword((visible) => !visible)}
                  >
                    {showPassword ? "Ẩn" : "Hiện"}
                  </button>
                }
                required
              />
            </div>

            {formError ? (
              <div
                ref={formErrorRef}
                className="mt-4 rounded-control border border-danger/30 bg-danger/5 px-3 py-2 text-sm text-danger"
                role="alert"
                tabIndex={-1}
              >
                {formError}
              </div>
            ) : null}

            <Button
              type="submit"
              className="mt-6 w-full"
              disabled={submitting || status === "bootstrapping"}
            >
              <span aria-live="polite">{submitting ? "Đang đăng nhập..." : "Đăng nhập"}</span>
            </Button>
          </form>
          <aside className="mt-4 rounded-control border border-warning/30 bg-harvest-100/50 px-4 py-3 text-sm leading-6 text-soil-700">
            Tài khoản sẽ tạm khóa sau 5 lần đăng nhập sai trong 15 phút. Liên hệ quản trị viên nếu cần hỗ trợ.
          </aside>
        </div>
      </section>
    </main>
  );
}
