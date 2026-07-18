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

export function InventoryPage() {
  const { api } = useSession();
  const domain = createDomainApi(api);
  const [itemId, setItemId] = useState("");
  const [activeId, setActiveId] = useState<string | null>(null);

  const itemQuery = useQuery({
    queryKey: ["inventory-item", activeId],
    queryFn: ({ signal }) => domain.getInventoryItem(activeId!, signal),
    enabled: Boolean(activeId),
  });

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (itemId.trim()) {
      setActiveId(itemId.trim());
    }
  }

  return (
    <OpsPage
      title="Kho vận"
      description="Chi tiết item và lệnh reserve/stock-in theo ID. Không giả lập danh sách kho."
    >
      {!LIVE_API_CAPABILITIES.inventoryList ? (
        <ApiGapNotice
          capability="inventoryList"
          detail="Backend không có list/search inventory items — nhập UUID item đã biết."
        />
      ) : null}

      <form onSubmit={onSubmit} className="flex flex-wrap items-end gap-3">
        <div className="min-w-[16rem] flex-1">
          <Input
            label="Inventory item ID"
            name="itemId"
            value={itemId}
            onChange={(e) => setItemId(e.target.value)}
            placeholder="UUID"
          />
        </div>
        <Button type="submit" variant="secondary">
          Tải item
        </Button>
      </form>

      {itemQuery.isLoading ? <LoadingBlock /> : null}
      {itemQuery.isError ? (
        <ErrorBlock error={itemQuery.error} onRetry={() => void itemQuery.refetch()} />
      ) : null}
      {itemQuery.data ? (
        <dl className="grid gap-3 rounded-card border border-border bg-surface p-5 sm:grid-cols-2 text-sm">
          <div>
            <dt className="text-xs uppercase text-muted">SKU</dt>
            <dd className="font-mono font-semibold">{itemQuery.data.sku}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted">Name</dt>
            <dd>{itemQuery.data.name}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted">On hand</dt>
            <dd>{String(itemQuery.data.onHandQuantity)}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted">Reserved</dt>
            <dd>{String(itemQuery.data.reservedQuantity)}</dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted">Available</dt>
            <dd className="font-semibold text-forest-700">
              {String(itemQuery.data.availableQuantity)}
            </dd>
          </div>
          <div>
            <dt className="text-xs uppercase text-muted">Version</dt>
            <dd className="font-mono">{itemQuery.data.version}</dd>
          </div>
        </dl>
      ) : null}
    </OpsPage>
  );
}
