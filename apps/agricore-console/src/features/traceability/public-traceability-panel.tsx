import type { PublicTraceabilityResponse } from "../../lib/api/types";
import {
  formatPublicDate,
  formatPublicWeight,
  publicText,
} from "./public-traceability-formatters";

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-control bg-canvas px-4 py-3">
      <dt className="text-xs font-semibold uppercase tracking-wide text-muted">{label}</dt>
      <dd className="mt-1 font-semibold text-ink">{value}</dd>
    </div>
  );
}

function TimelineItem({
  title,
  date,
  description,
}: {
  title: string;
  date: string;
  description: string;
}) {
  return (
    <li className="relative grid gap-1 pl-9 before:absolute before:left-[0.6875rem] before:top-7 before:h-[calc(100%+0.5rem)] before:w-px before:bg-forest-100 last:before:hidden">
      <span className="absolute left-0 top-0 grid size-6 place-items-center rounded-full bg-forest-700 text-xs font-bold text-white" aria-hidden="true">✓</span>
      <div className="flex flex-col gap-1 sm:flex-row sm:items-baseline sm:justify-between">
        <h3 className="font-semibold text-ink">{title}</h3>
        <span className="text-sm font-medium text-forest-700">{date}</span>
      </div>
      <p className="text-sm leading-6 text-muted">{description}</p>
    </li>
  );
}

export function PublicTraceabilityPanel({ data }: { data: PublicTraceabilityResponse }) {
  return (
    <article className="space-y-6 animate-fade-in-up">
      <header className="rounded-card border border-border bg-surface p-5 sm:p-7">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <span className="rounded-full bg-forest-50 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-forest-900">
            Dữ liệu nguồn gốc công khai
          </span>
          <span className="font-mono text-xs font-semibold text-muted">{data.batchLabel}</span>
        </div>
        <h1 className="mt-5 text-3xl font-bold tracking-tight text-ink sm:text-4xl">{data.productName}</h1>
        <p className="mt-2 font-mono text-sm font-semibold text-forest-700">{data.traceabilityCode}</p>
      </header>

      <section
        className="grid items-center gap-5 rounded-card border border-forest-100 bg-forest-50 p-5 sm:grid-cols-[minmax(0,1fr)_10rem] sm:p-7"
        aria-labelledby="qr-heading"
      >
        <div>
          <h2 id="qr-heading" className="text-xl font-semibold text-forest-900">Mã QR truy xuất</h2>
          <p className="mt-2 max-w-prose text-sm leading-6 text-forest-900">
            Quét mã để mở lại hồ sơ nguồn gốc công khai đã được xác thực cho lô sản phẩm này.
          </p>
        </div>
        <figure className="mx-auto w-40 rounded-control border border-forest-100 bg-white p-2 text-center sm:mx-0">
          <img
            src={data.qrImageUrl}
            alt={`Mã QR truy xuất ${data.traceabilityCode}`}
            width={144}
            height={144}
            className="aspect-square w-full"
          />
          <figcaption className="mt-2 text-xs font-medium text-muted">Quét để mở hồ sơ</figcaption>
        </figure>
      </section>

      <section className="rounded-card border border-border bg-surface p-5 sm:p-7" aria-labelledby="origin-heading">
        <h2 id="origin-heading" className="text-xl font-semibold text-ink">Thông tin nguồn gốc</h2>
        <dl className="mt-4 grid gap-3 sm:grid-cols-2">
          <Detail label="Giống cây" value={publicText(data.varietyName)} />
          <Detail label="Nông trại" value={publicText(data.farmName)} />
          <Detail label="Lô đất" value={publicText(data.plotCode)} />
          <Detail label="Chất lượng" value={publicText(data.qualityGrade)} />
          <Detail label="Khối lượng thực" value={formatPublicWeight(data.netWeightKg)} />
          <Detail label="Ngày thu hoạch" value={formatPublicDate(data.harvestDate)} />
        </dl>
      </section>

      <section className="rounded-card border border-border bg-surface p-5 sm:p-7" aria-labelledby="journey-heading">
        <h2 id="journey-heading" className="text-xl font-semibold text-ink">Hành trình sản phẩm</h2>
        <ol className="mt-5 space-y-7">
          <TimelineItem
            title="Gieo trồng"
            date={formatPublicDate(data.plantingDate)}
            description="Ngày bắt đầu mùa vụ được công bố cho lô sản phẩm này."
          />
          <TimelineItem
            title="Chăm sóc"
            date="Trong mùa vụ"
            description={publicText(data.careSummary)}
          />
          <TimelineItem
            title="Thu hoạch"
            date={formatPublicDate(data.harvestDate)}
            description={`Phân loại chất lượng: ${publicText(data.qualityGrade)}.`}
          />
        </ol>
      </section>

      <footer className="rounded-card border border-forest-100 bg-forest-50 p-4 text-sm leading-6 text-forest-900">
        Trang này chỉ hiển thị dữ liệu nguồn gốc được phép công khai; không chứa ID nội bộ, thông tin nhân sự hay chi phí.
      </footer>
    </article>
  );
}
