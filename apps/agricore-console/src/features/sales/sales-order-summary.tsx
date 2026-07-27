import type { SalesOrderResponse } from "./sales-api";

function statusText(status: string | null | undefined): string {
  if (status === "CONFIRMED") return "Đã xác nhận";
  if (status === "OUT_OF_STOCK") return "Không đủ tồn kho";
  if (status === "CANCELLED") return "Đã bù trừ / hủy";
  return status ?? "Chưa rõ";
}

function SagaTimeline({ order }: { order: SalesOrderResponse }) {
  const failed = order.sagaStatus === "FAILED"
    || order.status === "OUT_OF_STOCK"
    || order.status === "CANCELLED";
  const steps = [
    ["Tạo đơn", true],
    ["Giữ hàng", order.reservationId !== null],
    ["Xác nhận", order.status === "CONFIRMED"],
  ] as const;

  return (
    <ol className="grid gap-3 md:grid-cols-3">
      {steps.map(([label, complete], index) => (
        <li key={label} className={`rounded-control border p-3 ${complete ? "border-success/40 bg-green-50" : failed ? "border-danger/30 bg-red-50" : "border-border bg-canvas"}`}>
          <div className="flex items-center gap-2">
            <span className={`grid size-7 place-items-center rounded-full text-xs font-bold ${complete ? "bg-success text-white" : failed ? "bg-danger text-white" : "bg-border text-muted"}`}>
              {complete ? "✓" : index + 1}
            </span>
            <span className="text-sm font-semibold text-ink">{label}</span>
          </div>
          <p className="mt-2 text-xs text-muted">
            {complete ? "Đã ghi nhận" : failed ? "Dừng do lỗi saga" : "Đang chờ kết quả"}
          </p>
        </li>
      ))}
    </ol>
  );
}

export function SalesOrderSummary({ order }: { order: SalesOrderResponse }) {
  const failed = order.sagaStatus === "FAILED"
    || order.status === "OUT_OF_STOCK"
    || order.status === "CANCELLED";

  return (
    <section className="space-y-5">
      <div className={`rounded-card border p-5 ${failed ? "border-danger/40 bg-red-50" : "border-success/40 bg-green-50"}`}>
        <p className="text-xs font-semibold uppercase tracking-wide text-muted">Trạng thái hiện tại</p>
        <h2 className="mt-1 text-2xl font-bold text-ink">{statusText(order.status)}</h2>
        <p className="mt-1 text-sm text-muted">
          Saga: <span className="font-mono">{order.sagaStatus ?? "—"}</span>
          {" "}· bước <span className="font-mono">{order.sagaStep ?? "—"}</span>
        </p>
        {order.failureReason ? (
          <p className="mt-3 rounded-control border border-danger/30 bg-white px-3 py-2 font-mono text-xs text-danger">
            {order.failureReason}
          </p>
        ) : null}
      </div>

      <div className="grid gap-6 lg:grid-cols-[1.25fr_0.75fr]">
        <section className="rounded-card border border-border bg-surface p-5 shadow-sm">
          <h3 className="text-lg font-semibold text-ink">Dòng saga</h3>
          <p className="mt-1 text-sm text-muted">
            Các bước do sales-service điều phối; không thao tác thủ công trên timeline.
          </p>
          <div className="mt-4"><SagaTimeline order={order} /></div>
        </section>

        <section className="rounded-card border border-border bg-surface p-5 shadow-sm">
          <h3 className="text-lg font-semibold text-ink">Thông tin đơn</h3>
          <dl className="mt-4 grid gap-3 text-sm">
            {[
              ["Mã đơn hàng", order.orderNumber],
              ["Order ID", order.id],
              ["Customer ID", order.customerId],
              ["Reservation ID", order.reservationId ?? "—"],
              ["Correlation ID", order.correlationId ?? "—"],
              ["Số lượng", order.quantity.toLocaleString("vi-VN")],
            ].map(([label, value]) => (
              <div key={label}>
                <dt className="text-xs font-semibold uppercase tracking-wide text-muted">{label}</dt>
                <dd className="mt-1 break-all font-mono text-xs text-ink">{value}</dd>
              </div>
            ))}
          </dl>
        </section>
      </div>
    </section>
  );
}
