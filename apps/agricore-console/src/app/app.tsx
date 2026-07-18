const readinessItems = [
  "React và TypeScript nghiêm ngặt",
  "Design token AgriCore dùng nội bộ",
  "Kiểm thử và build tái lập được",
] as const;

export function App() {
  return (
    <main className="grid min-h-screen place-items-center bg-canvas px-6 py-12 text-ink">
      <section
        className="w-full max-w-2xl rounded-card border border-border bg-surface p-8 md:p-12"
        aria-labelledby="workspace-title"
      >
        <div className="mb-8 flex items-center gap-3">
          <span
            className="grid size-11 place-items-center rounded-control bg-forest-900 text-lg font-bold text-white"
            aria-hidden="true"
          >
            A
          </span>
          <div>
            <p className="text-lg font-bold tracking-tight text-forest-900">AgriCore</p>
            <p className="text-sm text-muted">Operations Console</p>
          </div>
        </div>

        <p className="mb-3 text-sm font-semibold uppercase tracking-[0.18em] text-harvest-600">
          Nền tảng đã sẵn sàng
        </p>
        <h1 id="workspace-title" className="text-3xl font-bold tracking-tight md:text-4xl">
          Vận hành nông nghiệp trên một hệ thống thống nhất
        </h1>
        <p className="mt-4 max-w-xl leading-7 text-muted">
          Workspace frontend đã kết nối nền thiết kế AgriCore và sẵn sàng cho các luồng đăng
          nhập, trang trại, mùa vụ và trợ lý vận hành.
        </p>

        <ul className="mt-8 grid gap-3 sm:grid-cols-3" aria-label="Trạng thái workspace">
          {readinessItems.map((item) => (
            <li
              key={item}
              className="rounded-control border border-forest-100 bg-forest-50 px-4 py-3 text-sm font-medium text-forest-900"
            >
              {item}
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}
