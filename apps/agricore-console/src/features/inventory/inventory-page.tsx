import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { Button } from "../../components/ui/button";
import { EmptyState } from "../../components/ui/empty-state";
import { Input } from "../../components/ui/input";
import { useSession } from "../../lib/auth/session";
import {
  confirmReservation,
  getInventoryItem,
  releaseReservation,
  reserveStock,
  type ReservationResponse,
} from "./inventory-api";
import { InventoryBalanceCard } from "./inventory-balance-card";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const statusLabel: Record<ReservationResponse["status"], string> = {
  ACTIVE: "Đang giữ",
  RELEASED: "Đã giải phóng",
  FULFILLED: "Đã xác nhận",
};

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

export function InventoryPage() {
  const { api, user } = useSession();
  const queryClient = useQueryClient();
  const [itemInput, setItemInput] = useState(() => new URLSearchParams(window.location.search).get("itemId") ?? "");
  const [itemId, setItemId] = useState(() => new URLSearchParams(window.location.search).get("itemId") ?? "");
  const [reservationId, setReservationId] = useState("");
  const [referenceType, setReferenceType] = useState("SALES_ORDER");
  const [referenceId, setReferenceId] = useState("");
  const [quantity, setQuantity] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [reservation, setReservation] = useState<ReservationResponse | null>(null);
  const canRead = user?.permissions.includes("INVENTORY_READ") ?? false;
  const canMutate = user?.permissions.includes("INVENTORY_USE") ?? false;

  const itemQuery = useQuery({
    queryKey: ["inventory", "item", itemId],
    queryFn: ({ signal }) => getInventoryItem(api, itemId, signal),
    enabled: Boolean(itemId) && canRead,
  });
  const reservationMutation = useMutation({
    mutationFn: () =>
      reserveStock(api, {
        inventoryItemId: itemId,
        quantity: Number(quantity),
        referenceType: referenceType.trim(),
        referenceId: referenceId.trim(),
      }),
    onSuccess: (result) => {
      setReservation(result);
      setReservationId(result.id);
      setFormError(null);
      void queryClient.invalidateQueries({ queryKey: ["inventory", "item", itemId] });
    },
    onError: (error) => setFormError(errorMessage(error, "Không thể tạo phiếu giữ hàng.")),
  });
  const actionMutation = useMutation({
    mutationFn: (action: "confirm" | "release") =>
      action === "confirm" ? confirmReservation(api, reservationId) : releaseReservation(api, reservationId),
    onSuccess: (result) => {
      setReservation(result);
      setFormError(null);
      void queryClient.invalidateQueries({ queryKey: ["inventory", "item", itemId] });
    },
    onError: (error) => setFormError(errorMessage(error, "Không thể cập nhật phiếu giữ hàng.")),
  });

  function loadItem(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canRead) {
      setFormError("Phiên hiện tại thiếu quyền INVENTORY_READ.");
      return;
    }
    const value = itemInput.trim();
    if (!UUID_PATTERN.test(value)) {
      setFormError("Mã mặt hàng phải là UUID hợp lệ.");
      return;
    }
    setFormError(null);
    setReservation(null);
    setReservationId("");
    setItemId(value);
  }

  function submitReservation(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const item = itemQuery.data;
    const parsedQuantity = Number(quantity);
    if (!item || !canMutate) return;
    if (!referenceType.trim() || !referenceId.trim() || !Number.isFinite(parsedQuantity) || parsedQuantity <= 0) {
      setFormError("Nhập loại tham chiếu, ID tham chiếu và số lượng lớn hơn 0.");
      return;
    }
    if (parsedQuantity > item.availableQuantity) {
      setFormError("Không đủ tồn kho khả dụng cho số lượng này.");
      return;
    }
    setFormError(null);
    reservationMutation.mutate();
  }

  const itemError = itemQuery.error ? errorMessage(itemQuery.error, "Không thể tải số dư tồn kho.") : null;
  return (
    <div className="animate-fade-in-up space-y-6">
      <header>
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">Chuỗi cung ứng</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-ink">Kho vận & giữ hàng</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">
          Workspace này dùng lookup-by-ID để giữ ngữ cảnh từ luồng thu hoạch hoặc bán hàng; không suy diễn dữ liệu ngoài response backend.
        </p>
      </header>

      {!canRead ? <section className="rounded-card border border-harvest-600/40 bg-harvest-100 p-4 text-sm text-ink" role="alert">Phiên hiện tại thiếu quyền <span className="font-mono">INVENTORY_READ</span>. Tải lại phiên sau khi quản trị viên cấp quyền.</section> : null}

      <section className="rounded-card border border-border bg-surface p-5 shadow-sm">
        <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={loadItem}>
          <div className="min-w-0 flex-1">
            <Input
              label="Mã mặt hàng (UUID)"
              value={itemInput}
              onChange={(event) => setItemInput(event.target.value)}
              hint="Nhập ID từ luồng thu hoạch hoặc bán hàng; backend vẫn kiểm tra quyền farm."
            />
          </div>
          <Button type="submit" className="shrink-0" disabled={!canRead}>Tải số dư</Button>
        </form>
      </section>

      {canRead && itemQuery.isPending && itemId ? (
        <section className="rounded-card border border-border bg-surface p-6" role="status">Đang tải số dư mặt hàng…</section>
      ) : null}
      {itemError ? (
        <section className="rounded-card border border-danger/40 bg-red-50 p-5" role="alert">
          <p className="font-semibold text-danger">Không thể tải mặt hàng</p>
          <p className="mt-1 text-sm text-ink">{itemError}</p>
          <Button variant="secondary" className="mt-4" onClick={() => void itemQuery.refetch()}>Thử lại</Button>
        </section>
      ) : null}
      {canRead && !itemQuery.data && !itemQuery.isPending && !itemError ? (
        <EmptyState title="Chưa chọn mặt hàng" description="Tải một mặt hàng cụ thể để xem phương trình tồn kho và mở phiếu giữ." />
      ) : null}

      {itemQuery.data ? (
        <>
          <section className="space-y-4">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-muted">Mặt hàng</p>
                <h2 className="mt-1 text-xl font-semibold text-ink">{itemQuery.data.name}</h2>
                <p className="mt-1 font-mono text-xs text-muted">{itemQuery.data.sku} · {itemQuery.data.itemType}</p>
              </div>
              <p className="font-mono text-xs text-muted">version {itemQuery.data.version}</p>
            </div>
            <InventoryBalanceCard item={itemQuery.data} />
          </section>

          <div className="grid gap-6 xl:grid-cols-[minmax(0,1.15fr)_minmax(20rem,0.85fr)]">
            <section className="rounded-card border border-border bg-surface p-5 shadow-sm">
              <h2 className="text-lg font-semibold text-ink">Giữ hàng theo tham chiếu</h2>
              <p className="mt-1 text-sm text-muted">Lặp lại cùng tham chiếu sẽ trả về reservation hiện tại mà không tăng giữ hàng.</p>
              {!canMutate ? <p className="mt-3 rounded-control bg-harvest-100 px-3 py-2 text-sm text-ink">Chỉ WAREHOUSE_MANAGER hoặc SALES_STAFF được tạo và cập nhật giữ hàng.</p> : null}
              <form className="mt-4 grid gap-4 sm:grid-cols-2" onSubmit={submitReservation}>
                <Input label="Loại tham chiếu" value={referenceType} onChange={(event) => setReferenceType(event.target.value)} disabled={!canMutate} />
                <Input label="ID tham chiếu" value={referenceId} onChange={(event) => setReferenceId(event.target.value)} disabled={!canMutate} />
                <Input label={`Số lượng (${itemQuery.data.unit})`} type="number" min="0.001" step="0.001" value={quantity} onChange={(event) => setQuantity(event.target.value)} disabled={!canMutate} />
                <div className="flex items-end"><Button type="submit" disabled={!canMutate || reservationMutation.isPending}>{reservationMutation.isPending ? "Đang giữ…" : "Giữ hàng"}</Button></div>
              </form>
            </section>

            <section className="rounded-card border border-border bg-surface p-5 shadow-sm">
              <h2 className="text-lg font-semibold text-ink">Reservation cụ thể</h2>
              <p className="mt-1 text-sm text-muted">Tra cứu theo ID từ luồng nghiệp vụ; endpoint ledger phân trang được dành cho màn hình vận hành chuyên sâu.</p>
              <Input className="mt-4" label="Reservation ID" value={reservationId} onChange={(event) => setReservationId(event.target.value)} disabled={!canMutate} />
              {reservation ? (
                <dl className="mt-4 grid gap-2 text-sm">
                  <div className="flex justify-between gap-3"><dt className="text-muted">Trạng thái</dt><dd className="font-semibold text-forest-700">{statusLabel[reservation.status]}</dd></div>
                  <div className="flex justify-between gap-3"><dt className="text-muted">Số lượng</dt><dd className="font-mono">{reservation.quantity.toLocaleString("vi-VN")}</dd></div>
                  <div className="flex justify-between gap-3"><dt className="text-muted">Tham chiếu</dt><dd className="font-mono text-xs">{reservation.referenceType}/{reservation.referenceId}</dd></div>
                </dl>
              ) : null}
              {reservationId.trim() ? (
                <div className="mt-5 flex flex-wrap gap-2">
                  <Button variant="primary" disabled={!canMutate || actionMutation.isPending} onClick={() => actionMutation.mutate("confirm")}>Xác nhận giữ hàng</Button>
                  <Button variant="danger" disabled={!canMutate || actionMutation.isPending} onClick={() => actionMutation.mutate("release")}>Giải phóng</Button>
                </div>
              ) : null}
            </section>
          </div>
        </>
      ) : null}

      {formError ? <p className="rounded-control border border-danger/40 bg-red-50 px-4 py-3 text-sm text-danger" role="alert">{formError}</p> : null}
    </div>
  );
}
