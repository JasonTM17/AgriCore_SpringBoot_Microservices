import { useNavigate } from "@tanstack/react-router";
import { useState, type FormEvent } from "react";

import { Button } from "../../components/ui/button";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function HarvestReceiptLookup() {
  const navigate = useNavigate();
  const [harvestId, setHarvestId] = useState("");
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedId = harvestId.trim();
    if (!UUID_PATTERN.test(normalizedId)) {
      setError("ID biên nhận phải là UUID hợp lệ.");
      return;
    }
    setError(null);
    void navigate({ to: "/harvests/$harvestId", params: { harvestId: normalizedId } });
  }

  return (
    <section className="rounded-card border border-border bg-surface p-5 shadow-sm" aria-labelledby="receipt-lookup-heading">
      <h2 id="receipt-lookup-heading" className="text-lg font-semibold text-ink">Mở biên nhận đã có</h2>
      <p className="mt-1 text-sm text-muted">
        Biên nhận tải lại trạng thái producer, kho và truy xuất trực tiếp từ API.
      </p>
      <form className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-start" onSubmit={handleSubmit} noValidate>
        <label className="grid flex-1 gap-1.5">
          <span className="text-xs font-semibold uppercase tracking-wide text-muted">ID biên nhận</span>
          <input
            className="h-11 rounded-control border border-border bg-surface px-3 text-base text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-info md:text-sm"
            aria-label="ID biên nhận"
            aria-invalid={error !== null}
            aria-describedby={error ? "receipt-id-error" : undefined}
            autoComplete="off"
            value={harvestId}
            onChange={(event) => {
              setHarvestId(event.target.value);
              if (error) setError(null);
            }}
          />
          {error ? <span id="receipt-id-error" className="text-sm font-medium text-danger">{error}</span> : null}
        </label>
        <Button className="min-h-11 sm:mt-[1.375rem]" type="submit">Mở biên nhận</Button>
      </form>
    </section>
  );
}
