import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { Button } from "../../components/ui/button";
import { EmptyState } from "../../components/ui/empty-state";
import { Input } from "../../components/ui/input";
import { useSession } from "../../lib/auth/session";
import {
  createSalesOrder,
  getSalesOrder,
  reconcileSalesOrder,
  type SalesOrderResponse,
} from "./sales-api";
import { SalesOrderSummary } from "./sales-order-summary";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

export function SalesPage() {
  const { api, user } = useSession();
  const queryClient = useQueryClient();
  const canRead = user?.permissions.includes("SALES_READ") ?? false;
  const canWrite = user?.permissions.includes("SALES_WRITE") ?? false;
  const canReconcile = user?.permissions.includes("SALES_USE") ?? false;
  const [orderNumber, setOrderNumber] = useState("");
  const [customerId, setCustomerId] = useState("");
  const [inventoryItemId, setInventoryItemId] = useState("");
  const [quantity, setQuantity] = useState("");
  const [unitPrice, setUnitPrice] = useState("");
  const [currencyCode, setCurrencyCode] = useState("VND");
  const [lookupInput, setLookupInput] = useState("");
  const [lookupId, setLookupId] = useState("");
  const [currentOrder, setCurrentOrder] = useState<SalesOrderResponse | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const orderQuery = useQuery({
    queryKey: ["sales", "order", lookupId],
    queryFn: ({ signal }) => getSalesOrder(api, lookupId, signal),
    enabled: Boolean(lookupId) && canRead,
  });
  const createMutation = useMutation({
    mutationFn: () => createSalesOrder(api, {
      orderNumber: orderNumber.trim(),
      customerId: customerId.trim(),
      inventoryItemId: inventoryItemId.trim(),
      quantity: Number(quantity),
      unitPrice: unitPrice.trim() ? Number(unitPrice) : null,
      currencyCode: currencyCode.trim() || null,
    }),
    onSuccess: (order) => { setCurrentOrder(order); setFormError(null); },
    onError: (error) => setFormError(errorMessage(error, "Không thể tạo đơn bán.")),
  });
  const reconcileMutation = useMutation({
    mutationFn: (action: "RELEASE" | "CONFIRM") => {
      const order = currentOrder ?? orderQuery.data;
      if (!order) throw new Error("Chưa có đơn để reconcile.");
      return reconcileSalesOrder(api, order.id, action);
    },
    onSuccess: (order) => {
      setCurrentOrder(order);
      if (lookupId === order.id) {
        queryClient.setQueryData(["sales", "order", lookupId], order);
      }
      setFormError(null);
    },
    onError: (error) => setFormError(errorMessage(error, "Không thể reconcile đơn bán.")),
  });

  function submitOrder(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsedQuantity = Number(quantity);
    if (!canWrite) return;
    if (!orderNumber.trim() || !UUID_PATTERN.test(customerId.trim()) || !UUID_PATTERN.test(inventoryItemId.trim()) || !Number.isFinite(parsedQuantity) || parsedQuantity <= 0) {
      setFormError("Nhập mã đơn và các UUID hợp lệ; số lượng phải lớn hơn 0.");
      return;
    }
    setFormError(null);
    createMutation.mutate();
  }

  function lookupOrder(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canRead) {
      setFormError("Phiên hiện tại thiếu quyền SALES_READ.");
      return;
    }
    const value = lookupInput.trim();
    if (!UUID_PATTERN.test(value)) { setFormError("Order ID phải là UUID hợp lệ."); return; }
    setFormError(null);
    setCurrentOrder(null);
    setLookupId(value);
  }

  const orderError = orderQuery.error ? errorMessage(orderQuery.error, "Không thể tải đơn bán.") : null;
  const displayedOrder = currentOrder ?? orderQuery.data;
  const canReconcileOrder = canReconcile
    && Boolean(displayedOrder)
    && (Boolean(displayedOrder?.reservationId) || displayedOrder?.sagaStep === "RESERVATION_OUTCOME_UNKNOWN")
    && displayedOrder?.status !== "CONFIRMED"
    && displayedOrder?.status !== "CANCELLED";

  return (
    <div className="animate-fade-in-up space-y-6">
      <header>
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">Chuỗi cung ứng</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-ink">Bán hàng & saga giữ kho</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">Tạo một đơn bán hoặc mở đơn đã biết để đọc trạng thái điều phối. Response body là nguồn sự thật kể cả khi HTTP trả 201.</p>
      </header>

      {!canRead ? <section className="rounded-card border border-harvest-600/40 bg-harvest-100 p-4 text-sm text-ink" role="alert">Phiên hiện tại thiếu quyền <span className="font-mono">SALES_READ</span>. Tải lại phiên sau khi quyền được cấp.</section> : null}

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1.05fr)_minmax(20rem,0.95fr)]">
        <section className="rounded-card border border-border bg-surface p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-ink">Tạo đơn bán</h2>
          {!canWrite ? <p className="mt-3 rounded-control bg-harvest-100 px-3 py-2 text-sm text-ink">Chỉ SALES_STAFF hoặc SYSTEM_ADMIN được tạo đơn.</p> : null}
          <form className="mt-4 grid gap-4 sm:grid-cols-2" onSubmit={submitOrder}>
            <Input label="Mã đơn hàng" value={orderNumber} onChange={(event) => setOrderNumber(event.target.value)} disabled={!canWrite} />
            <Input label="Customer ID (UUID)" value={customerId} onChange={(event) => setCustomerId(event.target.value)} disabled={!canWrite} />
            <Input label="Inventory item ID (UUID)" value={inventoryItemId} onChange={(event) => setInventoryItemId(event.target.value)} disabled={!canWrite} />
            <Input label="Số lượng" type="number" min="0.001" step="0.001" value={quantity} onChange={(event) => setQuantity(event.target.value)} disabled={!canWrite} />
            <Input label="Đơn giá (tuỳ chọn)" type="number" min="0" step="0.0001" value={unitPrice} onChange={(event) => setUnitPrice(event.target.value)} disabled={!canWrite} />
            <Input label="Tiền tệ" value={currencyCode} onChange={(event) => setCurrencyCode(event.target.value.toUpperCase())} disabled={!canWrite} />
            <div className="sm:col-span-2"><Button type="submit" disabled={!canWrite || createMutation.isPending}>{createMutation.isPending ? "Đang tạo…" : "Tạo đơn & chạy saga"}</Button></div>
          </form>
        </section>

        <section className="rounded-card border border-border bg-surface p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-ink">Mở đơn đã biết</h2>
          <p className="mt-1 text-sm text-muted">Không có API tìm kiếm; dùng order ID từ luồng tạo hoặc correlation log.</p>
          <form className="mt-4 flex gap-3" onSubmit={lookupOrder}>
            <Input className="min-w-0 flex-1" label="Order ID (UUID)" value={lookupInput} onChange={(event) => setLookupInput(event.target.value)} />
            <div className="flex items-end"><Button type="submit" disabled={!canRead}>Tải đơn</Button></div>
          </form>
          {orderQuery.isPending ? <p className="mt-4 text-sm text-muted" role="status">Đang tải trạng thái đơn…</p> : null}
          {orderError ? <p className="mt-4 rounded-control border border-danger/40 bg-red-50 px-3 py-2 text-sm text-danger" role="alert">{orderError}</p> : null}
        </section>
      </div>

      {displayedOrder ? <SalesOrderSummary order={displayedOrder} /> : <EmptyState title="Chưa có đơn để hiển thị" description="Tạo đơn mới hoặc nhập một order ID cụ thể để xem saga." />}
      {canReconcileOrder && displayedOrder ? (
        <section className="rounded-card border border-harvest-600/40 bg-harvest-100 p-5">
          <h2 className="text-lg font-semibold text-ink">Reconcile giữ hàng</h2>
          <p className="mt-1 text-sm text-ink">Chỉ dùng khi saga dừng ở trạng thái không chắc chắn hoặc cần đối soát với inventory-service.</p>
          <div className="mt-4 flex flex-wrap gap-2">
            <Button
              variant="secondary"
              disabled={reconcileMutation.isPending}
              onClick={() => reconcileMutation.mutate("RELEASE")}
            >
              {reconcileMutation.isPending ? "Đang đối soát…" : "Đối soát & giải phóng"}
            </Button>
            <Button
              disabled={reconcileMutation.isPending}
              onClick={() => reconcileMutation.mutate("CONFIRM")}
            >
              Đối soát & xác nhận
            </Button>
          </div>
        </section>
      ) : null}
      {formError ? <p className="rounded-control border border-danger/40 bg-red-50 px-4 py-3 text-sm text-danger" role="alert">{formError}</p> : null}
    </div>
  );
}
