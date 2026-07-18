import { useMutation, useQuery } from "@tanstack/react-query";
import { type FormEvent, useState } from "react";

import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import {
  ApiGapNotice,
  ErrorBlock,
  LoadingBlock,
  OpsPage,
} from "../../components/ops/resource-state";
import { createDomainApi } from "../../lib/api/domain-api";
import { ApiClientError } from "../../lib/api/errors";
import { useSession } from "../../lib/auth/session";
import { hasAnyRole } from "../../lib/auth/roles";

export function HarvestPage() {
  const { api, user } = useSession();
  const domain = createDomainApi(api);
  const canComplete = hasAnyRole(user?.roles ?? [], [
    "SYSTEM_ADMIN",
    "FARM_MANAGER",
    "AGRONOMIST",
    "FIELD_WORKER",
  ]);

  const [lookupId, setLookupId] = useState("");
  const [activeId, setActiveId] = useState<string | null>(null);
  const [form, setForm] = useState({
    code: "",
    cropCycleId: "",
    plotId: "",
    warehouseId: "",
    productCode: "",
    grossWeightKg: "",
    netWeightKg: "",
    qualityGrade: "A",
    notes: "",
  });

  const detailQuery = useQuery({
    queryKey: ["harvest", activeId],
    queryFn: ({ signal }) => domain.getHarvest(activeId!, signal),
    enabled: Boolean(activeId),
  });

  const completeMutation = useMutation({
    mutationFn: () => {
      const body: Parameters<typeof domain.completeHarvest>[0] = {
        code: form.code,
        cropCycleId: form.cropCycleId,
        plotId: form.plotId,
        warehouseId: form.warehouseId,
        productCode: form.productCode,
        grossWeightKg: Number(form.grossWeightKg),
        netWeightKg: Number(form.netWeightKg),
        qualityGrade: form.qualityGrade,
      };
      if (form.notes.trim()) {
        body.notes = form.notes.trim();
      }
      return domain.completeHarvest(body);
    },
    onSuccess: (batch) => {
      setActiveId(batch.id);
    },
  });

  function onLookup(event: FormEvent) {
    event.preventDefault();
    if (lookupId.trim()) {
      setActiveId(lookupId.trim());
    }
  }

  return (
    <OpsPage
      title="Thu hoạch"
      description="Hoàn tất thu hoạch gửi event bất đồng bộ. Thành công HTTP không đồng nghĩa inventory/QR đã sẵn sàng."
    >
      <ApiGapNotice
        capability="harvestList"
        detail="Không có API list harvest — chỉ GET by ID và POST /complete. Projection inventory/QR theo dõi bằng ID đã biết."
      />

      <form onSubmit={onLookup} className="flex flex-wrap items-end gap-3">
        <div className="min-w-[16rem] flex-1">
          <Input
            label="Tra cứu harvest ID"
            name="harvestId"
            value={lookupId}
            onChange={(e) => setLookupId(e.target.value)}
            placeholder="UUID"
          />
        </div>
        <Button type="submit" variant="secondary">
          Tải chi tiết
        </Button>
      </form>

      {detailQuery.isLoading ? <LoadingBlock /> : null}
      {detailQuery.isError ? (
        <ErrorBlock error={detailQuery.error} onRetry={() => void detailQuery.refetch()} />
      ) : null}
      {detailQuery.data ? (
        <article className="rounded-card border border-border bg-surface p-5 text-sm">
          <h2 className="text-lg font-semibold">{detailQuery.data.code}</h2>
          <p className="mt-2 text-muted">
            Status: <strong>{detailQuery.data.status}</strong> · Net{" "}
            {String(detailQuery.data.netWeightKg)} kg · Grade {detailQuery.data.qualityGrade}
          </p>
          <p className="mt-2 text-xs text-muted">
            Sau complete: kiểm tra tồn kho/QR bằng ID liên quan khi projection xong — không giả định đồng bộ ngay.
          </p>
        </article>
      ) : null}

      {canComplete ? (
        <form
          className="grid gap-3 rounded-card border border-border bg-surface p-5 md:grid-cols-2"
          onSubmit={(e) => {
            e.preventDefault();
            completeMutation.mutate();
          }}
        >
          <h2 className="md:col-span-2 text-lg font-semibold">Hoàn tất thu hoạch</h2>
          {(
            [
              ["code", "Mã batch"],
              ["cropCycleId", "Crop cycle ID"],
              ["plotId", "Plot ID"],
              ["warehouseId", "Warehouse ID"],
              ["productCode", "Product code"],
              ["grossWeightKg", "Gross kg"],
              ["netWeightKg", "Net kg"],
              ["qualityGrade", "Quality grade"],
            ] as const
          ).map(([key, label]) => (
            <Input
              key={key}
              label={label}
              name={key}
              value={form[key]}
              onChange={(e) => setForm((prev) => ({ ...prev, [key]: e.target.value }))}
              required
            />
          ))}
          <div className="md:col-span-2">
            <Input
              label="Ghi chú"
              name="notes"
              value={form.notes}
              onChange={(e) => setForm((prev) => ({ ...prev, notes: e.target.value }))}
            />
          </div>
          {completeMutation.isError ? (
            <p className="md:col-span-2 text-sm text-danger" role="alert">
              {completeMutation.error instanceof ApiClientError
                ? `${completeMutation.error.code}: ${completeMutation.error.message}`
                : "Complete harvest failed"}
            </p>
          ) : null}
          <div className="md:col-span-2">
            <Button type="submit" disabled={completeMutation.isPending}>
              {completeMutation.isPending ? "Đang gửi..." : "Complete harvest"}
            </Button>
          </div>
        </form>
      ) : (
        <ApiGapNotice
          capability="completeHarvest"
          detail="Vai trò hiện tại không được complete harvest theo RBAC backend."
        />
      )}
    </OpsPage>
  );
}
