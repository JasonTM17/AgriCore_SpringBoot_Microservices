import { Link } from "@tanstack/react-router";

import { Button } from "../../components/ui/button";
import { ApiClientError } from "../../lib/api/errors";
import type {
  HarvestBatchResponse,
  HarvestCompletionEventStatusResponse,
  InventoryHarvestProjectionAcknowledgementResponse,
  TraceabilityHarvestProjectionAcknowledgementResponse,
} from "../../lib/api/types";
import {
  formatHarvestInstant,
  formatHarvestWeight,
  inventoryStateLabel,
  producerStateLabel,
  traceabilityStateLabel,
} from "./harvest-formatters";
import { HarvestProjectionCard } from "./harvest-projection-card";

export interface ProjectionQueryState<T> {
  data: T | undefined;
  error: Error | null;
  isPending: boolean;
  isFetching: boolean;
  onRetry: () => void;
}

interface HarvestReceiptPanelProps {
  harvest: HarvestBatchResponse;
  producer: ProjectionQueryState<HarvestCompletionEventStatusResponse>;
  inventory: ProjectionQueryState<InventoryHarvestProjectionAcknowledgementResponse>;
  traceability: ProjectionQueryState<TraceabilityHarvestProjectionAcknowledgementResponse>;
  canReadAcknowledgements: boolean;
  canRepair: boolean;
  repairError: Error | null;
  isRepairing: boolean;
  onRepair: () => void;
  onRefreshAll: () => void;
}

function acknowledgementDescription(state: "ACKNOWLEDGED" | "NOT_ACKNOWLEDGED"): string {
  return state === "ACKNOWLEDGED"
    ? "Consumer đã lưu marker idempotent cho event này."
    : "Chưa có marker; đây có thể chỉ là độ trễ xử lý, không tự động kết luận là lỗi hoặc DLT.";
}

function repairMessage(error: Error): string {
  if (!(error instanceof ApiClientError)) return "Không thể gửi lại sự kiện. Kiểm tra kết nối rồi thử lại.";
  if (error.code === "OUTBOX_EVENT_BUSY" || error.status === 503) {
    return "Sự kiện đang được xử lý. Chờ một lúc rồi tải lại trạng thái.";
  }
  if (error.status === 409) return "Sự kiện gốc không còn đủ điều kiện để gửi lại an toàn.";
  if (error.status === 403 || error.status === 404) {
    return "Quyền truy cập đã thay đổi hoặc biên nhận không còn khả dụng.";
  }
  return "Không thể gửi lại sự kiện. Hãy tải lại trạng thái rồi thử lại.";
}

export function HarvestReceiptPanel({
  harvest,
  producer,
  inventory,
  traceability,
  canReadAcknowledgements,
  canRepair,
  repairError,
  isRepairing,
  onRepair,
  onRefreshAll,
}: HarvestReceiptPanelProps) {
  const producerData = producer.data;
  const inventoryData = inventory.data;
  const traceabilityData = traceability.data;
  const hasMissingProjection = inventoryData?.state === "NOT_ACKNOWLEDGED"
    || traceabilityData?.state === "NOT_ACKNOWLEDGED";
  const offerRepair = canRepair
    && producerData?.state === "PUBLISHED"
    && hasMissingProjection;
  const lossKg = Math.max(0, harvest.grossWeightKg - harvest.netWeightKg);

  function handleRepair() {
    const confirmed = window.confirm(
      "Chỉ gửi lại sau khi đã kiểm tra nguyên nhân projection bị chậm. Hệ thống sẽ dùng đúng event ID cũ. Tiếp tục?",
    );
    if (confirmed) onRepair();
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">Harvest receipt</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-ink">Biên nhận {harvest.code}</h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">
            Hoàn tất thu hoạch chỉ bắt đầu đồng bộ. Kho và truy xuất có acknowledgement riêng bên dưới.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link to="/harvests" className="inline-flex h-10 items-center rounded-control border border-border bg-surface px-4 text-sm font-semibold text-ink hover:bg-forest-50">
            Về thu hoạch
          </Link>
          <Button variant="secondary" onClick={onRefreshAll}>Tải lại tất cả</Button>
        </div>
      </header>

      <section className="rounded-card border border-border bg-surface p-5 shadow-sm" aria-labelledby="receipt-details-heading">
        <h2 id="receipt-details-heading" className="text-lg font-semibold text-ink">Kết quả đã ghi nhận</h2>
        <dl className="mt-4 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          <div><dt className="text-xs font-semibold uppercase text-muted">Sản phẩm</dt><dd className="mt-1 font-semibold">{harvest.productCode}</dd></div>
          <div><dt className="text-xs font-semibold uppercase text-muted">Chất lượng</dt><dd className="mt-1 font-semibold">{harvest.qualityGrade}</dd></div>
          <div><dt className="text-xs font-semibold uppercase text-muted">Khối lượng thô</dt><dd className="mt-1 font-semibold">{formatHarvestWeight(harvest.grossWeightKg)}</dd></div>
          <div><dt className="text-xs font-semibold uppercase text-muted">Khối lượng thực</dt><dd className="mt-1 font-semibold">{formatHarvestWeight(harvest.netWeightKg)}</dd></div>
          <div><dt className="text-xs font-semibold uppercase text-muted">Hao hụt</dt><dd className="mt-1 font-semibold">{formatHarvestWeight(lossKg)}</dd></div>
          <div><dt className="text-xs font-semibold uppercase text-muted">Thời điểm</dt><dd className="mt-1 font-semibold">{formatHarvestInstant(harvest.harvestedAt)}</dd></div>
          <div className="sm:col-span-2"><dt className="text-xs font-semibold uppercase text-muted">Event ID ổn định</dt><dd className="mt-1 break-all font-mono text-sm">{harvest.lastOutboxEventId ?? "Không có (legacy)"}</dd></div>
        </dl>
        {harvest.notes ? <p className="mt-4 border-t border-border pt-4 text-sm text-ink">{harvest.notes}</p> : null}
      </section>

      <section aria-labelledby="projection-heading">
        <h2 id="projection-heading" className="text-xl font-semibold text-ink">Tiến độ đồng bộ</h2>
        <div className="mt-4 grid gap-4 lg:grid-cols-3">
          <HarvestProjectionCard
            title="Harvest producer"
            stateLabel={producerData ? producerStateLabel(producerData.state) : "Chưa có trạng thái"}
            description="Trạng thái phát Kafka của outbox gốc; không đại diện cho consumer downstream."
            timestamp={producerData ? formatHarvestInstant(producerData.publishedAt) : null}
            error={producer.error}
            errorMessage="Không thể đọc trạng thái phát sự kiện"
            isPending={producer.isPending}
            isFetching={producer.isFetching}
            onRetry={producer.onRetry}
          />
          <HarvestProjectionCard
            title="Inventory projection"
            stateLabel={!canReadAcknowledgements
              ? "Vai trò hiện tại không được phép xem acknowledgement"
              : inventoryData ? inventoryStateLabel(inventoryData.state) : "Chờ producer"}
            description={!canReadAcknowledgements
              ? "Backend giới hạn acknowledgement cho vai trò vận hành thu hoạch."
              : inventoryData ? acknowledgementDescription(inventoryData.state) : "Sẽ kiểm tra sau khi producer phát thành công."}
            timestamp={inventoryData ? formatHarvestInstant(inventoryData.acknowledgedAt) : null}
            error={inventory.error}
            errorMessage="Không thể đọc trạng thái nhập kho"
            isPending={canReadAcknowledgements && inventory.isPending}
            isFetching={inventory.isFetching}
            onRetry={inventory.onRetry}
          />
          <HarvestProjectionCard
            title="Traceability projection"
            stateLabel={!canReadAcknowledgements
              ? "Vai trò hiện tại không được phép xem acknowledgement"
              : traceabilityData ? traceabilityStateLabel(traceabilityData.state) : "Chờ producer"}
            description={!canReadAcknowledgements
              ? "Backend giới hạn acknowledgement cho vai trò vận hành thu hoạch."
              : traceabilityData ? acknowledgementDescription(traceabilityData.state) : "Sẽ kiểm tra sau khi producer phát thành công."}
            timestamp={traceabilityData ? formatHarvestInstant(traceabilityData.acknowledgedAt) : null}
            error={traceability.error}
            errorMessage="Không thể đọc trạng thái truy xuất"
            isPending={canReadAcknowledgements && traceability.isPending}
            isFetching={traceability.isFetching}
            onRetry={traceability.onRetry}
          />
        </div>
      </section>

      {offerRepair || repairError ? (
        <section className="rounded-card border border-warning/40 bg-amber-50 p-5" aria-labelledby="repair-heading">
          <h2 id="repair-heading" className="font-semibold text-ink">Khôi phục projection</h2>
          <p className="mt-2 text-sm leading-6 text-ink">
            Chỉ dùng sau khi xử lý nguyên nhân consumer bị chậm. Thao tác gửi lại đúng payload và event ID cũ.
          </p>
          {repairError ? <p className="mt-3 text-sm font-semibold text-danger" role="alert">{repairMessage(repairError)}</p> : null}
          {offerRepair ? (
            <Button className="mt-4 min-h-11" onClick={handleRepair} disabled={isRepairing}>
              {isRepairing ? "Đang yêu cầu gửi lại…" : "Gửi lại sự kiện gốc"}
            </Button>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
