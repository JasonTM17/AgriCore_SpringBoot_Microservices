import { useQuery } from "@tanstack/react-query";
import { useMemo, useState, type FormEvent } from "react";

import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { useSession } from "../../lib/auth/session";
import { CropCatalogPanel } from "./crop-catalog-panel";
import { listCrops, type CropListParams } from "./crop-api";
import { cropQueryKeys } from "./crop-query-keys";

const PAGE_SIZE = 20;

export function CropsPage() {
  const { api, user } = useSession();
  const subject = user?.id ?? "unauthenticated";
  const [page, setPage] = useState(0);
  const [draftQuery, setDraftQuery] = useState("");
  const [draftCategory, setDraftCategory] = useState("");
  const [filters, setFilters] = useState({ q: "", category: "" });
  const params = useMemo<CropListParams>(
    () => ({
      page,
      size: PAGE_SIZE,
      ...(filters.q ? { q: filters.q } : {}),
      ...(filters.category ? { category: filters.category } : {}),
    }),
    [filters, page],
  );
  const cropsQuery = useQuery({
    queryKey: cropQueryKeys.list(subject, params),
    queryFn: ({ signal }) => listCrops(api, params, signal),
    enabled: user !== null,
  });

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setFilters({ q: draftQuery.trim(), category: draftCategory.trim() });
  }

  function clearFilters() {
    setDraftQuery("");
    setDraftCategory("");
    setFilters({ q: "", category: "" });
    setPage(0);
  }

  return (
    <div className="space-y-6">
      <header>
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">
          Crop catalog
        </p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-ink">Danh mục cây trồng</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">
          Tra cứu thông số cây trồng từ catalog thật. Bộ lọc danh mục dùng đúng mã do API trả về;
          màn hình không tự suy diễn giống hoặc yêu cầu canh tác khi API chưa cung cấp.
        </p>
      </header>

      <form
        className="grid gap-4 rounded-card border border-border bg-surface p-5 shadow-sm md:grid-cols-[1.3fr_1fr_auto_auto] md:items-end"
        onSubmit={applyFilters}
      >
        <Input
          label="Tìm theo tên"
          value={draftQuery}
          onChange={(event) => setDraftQuery(event.target.value)}
          placeholder="Ví dụ: cà phê"
        />
        <Input
          label="Mã danh mục"
          value={draftCategory}
          onChange={(event) => setDraftCategory(event.target.value)}
          placeholder="Ví dụ: PERENNIAL"
        />
        <Button type="submit">Lọc danh mục</Button>
        <Button type="button" variant="secondary" onClick={clearFilters}>
          Xóa lọc
        </Button>
      </form>

      <CropCatalogPanel
        data={cropsQuery.data}
        error={cropsQuery.error}
        isPending={cropsQuery.isPending}
        isFetching={cropsQuery.isFetching}
        onRetry={() => void cropsQuery.refetch()}
        onPrevious={() => setPage((current) => Math.max(0, current - 1))}
        onNext={() => setPage((current) => current + 1)}
      />
    </div>
  );
}
