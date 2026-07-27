import { useFarmScope } from "../farm/farm-scope-context";
import { hasAnyRole } from "../../lib/auth/roles";
import { useSession } from "../../lib/auth/session";
import { HarvestCompletionWorkflow } from "./harvest-completion-workflow";
import { HarvestReceiptLookup } from "./harvest-receipt-lookup";
import { HARVEST_WORKFLOW_ROLES } from "./harvest-roles";

export function HarvestPage() {
  const { user } = useSession();
  const { activeFarm, selectFarm } = useFarmScope();
  const canComplete = hasAnyRole(user?.roles ?? [], HARVEST_WORKFLOW_ROLES);

  return (
    <div className="space-y-6">
      <header>
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">Harvest operations</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-ink">Thu hoạch & đồng bộ</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">
          Ghi nhận đợt thu hoạch thật, sau đó theo dõi riêng producer, nhập kho và truy xuất nguồn gốc.
        </p>
      </header>

      {canComplete && activeFarm ? (
        <HarvestCompletionWorkflow
          key={activeFarm.id}
          farm={activeFarm}
          onResetScope={() => selectFarm(null)}
        />
      ) : null}
      {canComplete && !activeFarm ? (
        <section className="rounded-card border border-border bg-surface p-5 shadow-sm" role="status">
          <p className="text-sm text-muted">Chưa có nông trại trong phạm vi để hoàn tất thu hoạch.</p>
        </section>
      ) : null}
      {!canComplete ? (
        <section className="rounded-card border border-border bg-forest-50 p-5">
          <p className="font-semibold text-forest-900">Vai trò hiện tại chỉ được xem biên nhận đã có.</p>
          <p className="mt-1 text-sm text-muted">Form hoàn tất và API mùa vụ không được kích hoạt.</p>
        </section>
      ) : null}

      <HarvestReceiptLookup />
    </div>
  );
}
