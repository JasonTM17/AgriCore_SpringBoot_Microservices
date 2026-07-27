import type { InventoryItemResponse } from "./inventory-api";

export function InventoryBalanceCard({ item }: { item: InventoryItemResponse }) {
  const cards = [
    ["Tồn kho", item.onHandQuantity, "text-ink"],
    ["Đang giữ", item.reservedQuantity, "text-harvest-600"],
    ["Có thể dùng", item.availableQuantity, "text-forest-700"],
  ] as const;
  return (
    <div className="grid gap-3 sm:grid-cols-3">
      {cards.map(([label, value, color]) => (
        <article key={label} className="rounded-card border border-border bg-surface p-4 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted">{label}</p>
          <p className={`mt-2 font-mono text-2xl font-bold tabular-nums ${color}`}>
            {value.toLocaleString("vi-VN")} <span className="text-sm font-medium text-muted">{item.unit}</span>
          </p>
        </article>
      ))}
    </div>
  );
}
