import { Link } from "@tanstack/react-router";

import { Button } from "../../components/ui/button";
import { ApiClientError } from "../../lib/api/errors";
import type { CropCycleResponse } from "../../lib/api/types";
import {
  cycleStageLabel,
  cycleStatusLabel,
  formatCycleDate,
  formatCycleDateRange,
  shortResourceId,
} from "./crop-cycle-formatters";
import { CropCycleStageForm } from "./crop-cycle-stage-form";

interface CropCycleDetailPanelProps {
  cycle: CropCycleResponse;
  canMutate: boolean;
  allowedStages: readonly CropCycleResponse["stage"][];
  isMutating: boolean;
  isReloading: boolean;
  isInteractionLocked: boolean;
  actionError: Error | null;
  refreshError: Error | null;
  formResetKey: number;
  onChangeStage: (stage: CropCycleResponse["stage"], notes: string | null) => void;
  onReload: () => void;
}

function actionMessage(error: Error): string {
  if (!(error instanceof ApiClientError)) {
    return "Không thể cập nhật giai đoạn. Hãy tải lại trạng thái mới rồi thử lại.";
  }
  if (error.status === 409) {
    return "Mùa vụ vừa thay đổi hoặc transition không hợp lệ. Hãy tải lại trạng thái mới trước khi thử lại.";
  }
  if (error.status === 403 || error.status === 404) {
    return "Quyền truy cập mùa vụ đã thay đổi hoặc mùa vụ không còn khả dụng.";
  }
  if (error.status === 503) {
    return "Dịch vụ xác thực phạm vi đang gián đoạn. Hãy tải lại trạng thái rồi thử lại.";
  }
  return "Không thể cập nhật giai đoạn. Kiểm tra dữ liệu và thử lại.";
}

export function CropCycleDetailPanel({
  cycle,
  canMutate,
  allowedStages,
  isMutating,
  isReloading,
  isInteractionLocked,
  actionError,
  refreshError,
  formResetKey,
  onChangeStage,
  onReload,
}: CropCycleDetailPanelProps) {
  const supportCode = actionError instanceof ApiClientError ? actionError.code : null;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link
          to="/crop-cycles"
          className="text-sm font-semibold text-forest-800 underline-offset-4 hover:underline"
        >
          ← Quay lại danh sách mùa vụ
        </Link>
        <span className="rounded-full bg-forest-50 px-3 py-1 text-xs font-semibold text-forest-900">
          Phiên bản {cycle.version}
        </span>
      </div>

      <header className="rounded-card border border-border bg-surface p-5 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.16em] text-forest-700">{cycle.code}</p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight text-ink">Chi tiết mùa vụ</h1>
            <p className="mt-2 text-sm text-muted">Trạng thái hiển thị trực tiếp từ crop-cycle-service.</p>
          </div>
          <div className="flex flex-wrap gap-2 text-xs font-semibold">
            <span className="rounded-full bg-forest-50 px-3 py-1 text-forest-900">
              {cycleStageLabel(cycle.stage)}
            </span>
            <span className="rounded-full border border-border px-3 py-1 text-ink">
              {cycleStatusLabel(cycle.status)}
            </span>
          </div>
        </div>

        <dl className="mt-6 grid gap-4 text-sm sm:grid-cols-2 lg:grid-cols-3">
          <div className="sm:col-span-2 lg:col-span-3">
            <dt className="text-xs text-muted">Kế hoạch</dt>
            <dd className="mt-1 font-semibold text-ink">
              {formatCycleDateRange(cycle.plannedStartDate, cycle.plannedEndDate)}
            </dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Bắt đầu thực tế</dt>
            <dd className="mt-1 font-medium text-ink">{formatCycleDate(cycle.actualStartDate)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Kết thúc thực tế</dt>
            <dd className="mt-1 font-medium text-ink">{formatCycleDate(cycle.actualEndDate)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">Cập nhật gần nhất</dt>
            <dd className="mt-1 font-medium text-ink">{new Date(cycle.updatedAt).toLocaleString("vi-VN")}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">ID nông trại</dt>
            <dd className="mt-1 font-mono text-xs font-semibold text-ink" title={cycle.farmId}>{shortResourceId(cycle.farmId)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">ID lô</dt>
            <dd className="mt-1 font-mono text-xs font-semibold text-ink" title={cycle.plotId}>{shortResourceId(cycle.plotId)}</dd>
          </div>
          <div>
            <dt className="text-xs text-muted">ID cây trồng</dt>
            <dd className="mt-1 font-mono text-xs font-semibold text-ink" title={cycle.cropId}>{shortResourceId(cycle.cropId)}</dd>
          </div>
        </dl>
        {cycle.notes ? (
          <p className="mt-5 whitespace-pre-wrap break-words rounded-control bg-canvas p-3 text-sm leading-6 text-muted">
            {cycle.notes}
          </p>
        ) : null}
      </header>

      {refreshError ? (
        <div className="rounded-card border border-warning/30 bg-harvest-100/50 p-4" role="alert">
          <p className="font-semibold text-ink">Không thể làm mới mùa vụ; dữ liệu hiện tại được giữ nguyên.</p>
          <p className="mt-1 text-sm text-muted">Hãy tải lại thành công trước khi tiếp tục cập nhật.</p>
          {!actionError ? (
            <Button
              className="mt-3 min-h-11 md:min-h-10"
              variant="secondary"
              onClick={onReload}
              disabled={isMutating || isReloading}
            >
              {isReloading ? "Đang tải lại…" : "Tải lại trạng thái"}
            </Button>
          ) : null}
        </div>
      ) : null}

      {actionError ? (
        <div className="rounded-card border border-danger/30 bg-red-50 p-4" role="alert">
          <p className="font-semibold text-danger">{actionMessage(actionError)}</p>
          {supportCode ? <p className="mt-1 text-xs text-muted">Mã hỗ trợ: {supportCode}</p> : null}
          <Button className="mt-3 min-h-11 md:min-h-10" variant="secondary" onClick={onReload} disabled={isMutating || isReloading}>
            {isReloading ? "Đang tải lại…" : "Tải lại trạng thái"}
          </Button>
        </div>
      ) : null}

      {canMutate && allowedStages.length > 0 ? (
        <CropCycleStageForm
          key={`${cycle.id}-${formResetKey}`}
          cycleCode={cycle.code}
          allowedStages={allowedStages}
          isPending={isMutating}
          isDisabled={isInteractionLocked}
          onSubmit={onChangeStage}
        />
      ) : null}
      {!canMutate ? (
        <p className="rounded-card border border-border bg-surface p-4 text-sm text-muted">
          Tài khoản của bạn chỉ có quyền xem. Cần vai trò Quản trị hệ thống, Quản lý nông trại hoặc Chuyên gia nông học để chuyển giai đoạn.
        </p>
      ) : null}
      {canMutate && allowedStages.length === 0 ? (
        <p className="rounded-card border border-border bg-surface p-4 text-sm text-muted">
          Mùa vụ đã ở trạng thái kết thúc và không còn transition hợp lệ.
        </p>
      ) : null}
    </div>
  );
}
