import { useQuery } from "@tanstack/react-query";
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
import { LIVE_API_CAPABILITIES } from "../../lib/api/domain-types";
import { useSession } from "../../lib/auth/session";

export function SalesPage() {
  const { api } = useSession();
  const domain = createDomainApi(api);
  const [orderId, setOrderId] = useState("");
  const [activeId, setActiveId] = useState<string | null>(null);

  const orderQuery = useQuery({
    queryKey: ["sales-order", activeId],
    queryFn: ({ signal }) => domain.getSalesOrder(activeId!, signal),
    enabled: Boolean(activeId),
  });

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (orderId.trim()) {
      setActiveId(orderId.trim());
    }
  }

  return (
    <OpsPage
      title="Bán hàng & saga"
      description="Chi tiết đơn và trạng thái saga từ sales-service. HTTP 201 create vẫn phải đọc body status."
    >
      {!LIVE_API_CAPABILITIES.salesOrderList ? (
        <ApiGapNotice
          capability="salesOrderList"
          detail="Không có list orders/customers — chỉ GET order by ID."
        />
      ) : null}

      <form onSubmit={onSubmit} className="flex flex-wrap items-end gap-3">
        <div className="min-w-[16rem] flex-1">
          <Input
            label="Order ID"
            name="orderId"
            value={orderId}
            onChange={(e) => setOrderId(e.target.value)}
          />
        </div>
        <Button type="submit" variant="secondary">
          Tải đơn
        </Button>
      </form>

      {orderQuery.isLoading ? <LoadingBlock /> : null}
      {orderQuery.isError ? (
        <ErrorBlock error={orderQuery.error} onRetry={() => void orderQuery.refetch()} />
      ) : null}
      {orderQuery.data ? (
        <dl className="grid gap-3 rounded-card border border-border bg-surface p-5 sm:grid-cols-2 text-sm">
          <div>
            <dt className="text-xs uppercase text-muted">Order number</dt>
            <dd className="font-semibold">{orderQuery.data.orderNumber}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted">Status</dt>
            <dd>{orderQuery.data.status}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted">Saga</dt>
            <dd>
              {orderQuery.data.sagaStatus ?? "—"} / {orderQuery.data.sagaStep ?? "—"}
            </dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted">Failure</dt>
            <dd className="text-danger">{orderQuery.data.failureReason ?? "—"}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted">Quantity</dt>
            <dd>{String(orderQuery.data.quantity)}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted">Reservation</dt>
            <dd className="font-mono text-xs">{orderQuery.data.reservationId ?? "—"}</dd>
          </div>
        </dl>
      ) : null}
    </OpsPage>
  );
}
