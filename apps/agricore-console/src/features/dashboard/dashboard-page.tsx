import { EmptyState } from "../../components/ui/empty-state";
import { useSession } from "../../lib/auth/session";

const showcaseCards = [
  {
    src: "/agricore-harvest-packing.webp",
    alt: "Nông sản vừa thu hoạch được phân loại tại trạm đóng gói",
    title: "Từ thu hoạch đến tồn kho",
    description: "Theo dõi lô, hạn dùng và luồng đóng gói bằng dữ liệu vận hành có kiểm soát.",
  },
  {
    src: "/agricore-traceability-produce.webp",
    alt: "Thanh long và cà phê bên thẻ truy xuất nguồn gốc",
    title: "Truy xuất minh bạch",
    description: "Mỗi lô thành phẩm nối với hành trình canh tác và mã QR do dịch vụ truy xuất phát hành.",
  },
] as const;

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

      <section
        aria-labelledby="showcase-heading"
        className="overflow-hidden rounded-card border border-border bg-surface shadow-sm"
      >
        <div className="grid lg:grid-cols-[1.35fr_0.65fr]">
          <div className="relative min-h-72 overflow-hidden bg-forest-900">
            <img
              src="/agricore-farm-sunrise.webp"
              alt="Nông trại cao nguyên vào lúc bình minh"
              className="absolute inset-0 h-full w-full object-cover"
              decoding="async"
            />
            <div className="absolute inset-0 bg-gradient-to-r from-forest-900/90 via-forest-900/25 to-transparent" />
            <div className="relative max-w-xl p-7 text-white sm:p-10">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-white/75">
                Nông nghiệp vận hành bằng dữ liệu
              </p>
              <h2 id="showcase-heading" className="mt-3 text-3xl font-bold tracking-tight">
                Một nền tảng, toàn bộ hành trình nông sản
              </h2>
              <p className="mt-3 max-w-lg text-sm leading-6 text-white/85">
                Từ trang trại, mùa vụ và IoT đến thu hoạch, tồn kho, bán hàng và truy xuất.
              </p>
            </div>
          </div>

          <figure className="grid content-between gap-4 bg-forest-50 p-5">
            <img
              src="/agricore-farm-story.gif"
              alt="Ba khung hình giới thiệu trang trại, thu hoạch và truy xuất"
              className="aspect-video w-full rounded-lg border border-forest-100 object-cover"
              loading="lazy"
              decoding="async"
            />
            <figcaption>
              <p className="text-sm font-semibold text-forest-900">Farm-to-market story</p>
              <p className="mt-1 text-xs leading-5 text-muted">
                GIF ba khung hình được tối ưu để minh họa luồng demo mà không làm nặng trang.
              </p>
            </figcaption>
          </figure>
        </div>

        <div className="grid gap-4 border-t border-border p-5 sm:grid-cols-2">
          {showcaseCards.map((card) => (
            <figure key={card.src} className="grid grid-cols-[7rem_1fr] items-center gap-4">
              <img
                src={card.src}
                alt={card.alt}
                className="h-24 w-28 rounded-lg object-cover"
                loading="lazy"
                decoding="async"
              />
              <figcaption>
                <p className="text-sm font-semibold text-ink">{card.title}</p>
                <p className="mt-1 text-xs leading-5 text-muted">{card.description}</p>
              </figcaption>
            </figure>
          ))}
        </div>
      </section>

      <EmptyState
        title="Chưa có API tổng hợp dashboard"
        description="Theo thiết kế Stitch, KPI và dòng hoạt động chỉ hiển thị sau khi backend cung cấp contract tổng hợp. Dùng menu bên trái để mở các module đã có API."
      />
    </div>
  );
}
