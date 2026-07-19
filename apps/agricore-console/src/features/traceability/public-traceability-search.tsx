import { useNavigate } from "@tanstack/react-router";
import { useState, type FormEvent } from "react";

import { Button } from "../../components/ui/button";
import { normalizeTraceabilityCode } from "./public-traceability-query";

export function PublicTraceabilitySearch({ initialCode }: { initialCode: string }) {
  const navigate = useNavigate();
  const [code, setCode] = useState(initialCode);
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = normalizeTraceabilityCode(code);
    if (!normalized) {
      setError("Mã truy xuất phải có từ 1 đến 64 ký tự.");
      return;
    }
    setError(null);
    void navigate({
      to: "/public/traceability/$code",
      params: { code: normalized },
    });
  }

  return (
    <form className="rounded-card border border-white/20 bg-white/10 p-4" onSubmit={handleSubmit} noValidate>
      <label className="grid gap-1.5">
        <span className="text-xs font-semibold uppercase tracking-[0.12em] text-white/75">Mã truy xuất</span>
        <div className="flex flex-col gap-2 sm:flex-row">
          <input
            className="h-11 min-w-0 flex-1 rounded-control border border-white/30 bg-white px-3 text-base text-ink focus-visible:outline-white md:text-sm"
            aria-label="Mã truy xuất"
            aria-invalid={error !== null}
            aria-describedby={error ? "public-trace-code-error" : undefined}
            autoComplete="off"
            maxLength={64}
            value={code}
            onChange={(event) => {
              setCode(event.target.value);
              if (error) setError(null);
            }}
          />
          <Button className="min-h-11" type="submit" variant="secondary">
            Xem nguồn gốc
          </Button>
        </div>
        {error ? <span id="public-trace-code-error" className="text-sm font-medium text-harvest-100">{error}</span> : null}
      </label>
    </form>
  );
}
