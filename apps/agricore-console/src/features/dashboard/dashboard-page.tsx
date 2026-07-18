import { EmptyState } from "../../components/ui/empty-state";
import { useSession } from "../../lib/auth/session";

export function DashboardPage() {
  const { user } = useSession();

  return (
    <div className="animate-fade-in-up space-y-6">
      <div>
        <p className="text-sm font-semibold uppercase tracking-[0.16em] text-muted">Tổng quan</p>
        <h1 className="mt-1 text-3xl font-bold tracking-tight">
          Xin chào, <span className="text-forest-700">{user?.fullName}</span>
        </h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">
          Bảng điều khiển dùng dữ liệu mẫu thiết kế cho đến khi có API tổng hợp. Các module
          vận hành sẽ mở theo vai trò của bạn.
        </p>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        {[
          { label: "Trạng thái phiên", value: "Đã xác thực" },
          { label: "Vai trò", value: user?.roles.join(", ") ?? "—" },
          { label: "Email", value: user?.email ?? "—" },
        ].map((card) => (
          <article
            key={card.label}
            className="rounded-card border border-border bg-surface p-5 shadow-sm transition-all hover:-translate-y-0.5 hover:shadow-md"
          >
            <p className="text-xs font-semibold uppercase tracking-wide text-muted">{card.label}</p>
            <p className="mt-2 text-sm font-semibold text-ink break-all">{card.value}</p>
          </article>
        ))}
      </div>

      <EmptyState
        title="Chưa có API tổng hợp dashboard"
        description="Theo thiết kế Stitch, KPI và dòng hoạt động chỉ hiển thị sau khi backend cung cấp contract tổng hợp. Dùng menu bên trái để mở các module đã có API."
      />
    </div>
  );
}
