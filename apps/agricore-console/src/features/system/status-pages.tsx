import { Link } from "@tanstack/react-router";

import { EmptyState } from "../../components/ui/empty-state";

export function ForbiddenPage() {
  return (
    <EmptyState
      title="Không có quyền truy cập"
      description="Tài khoản của bạn không được phép mở trang này. Backend vẫn là nguồn quyền lực cuối cùng."
      action={
        <Link
          to="/"
          className="inline-flex h-10 items-center justify-center rounded-control bg-forest-700 px-4 text-sm font-semibold text-white"
        >
          Về tổng quan
        </Link>
      }
    />
  );
}

export function NotFoundPage() {
  return (
    <EmptyState
      title="Không tìm thấy trang"
      description="Đường dẫn không tồn tại hoặc đã được di chuyển."
      action={
        <Link
          to="/"
          className="inline-flex h-10 items-center justify-center rounded-control bg-forest-700 px-4 text-sm font-semibold text-white"
        >
          Về tổng quan
        </Link>
      }
    />
  );
}

export function PlaceholderModulePage({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-3xl font-bold tracking-tight text-ink">{title}</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-muted">{description}</p>
      </div>
      <EmptyState
        title="Module đang được kết nối API"
        description="Giao diện đầy đủ sẽ được bổ sung ở phase sau khi đã có client và query/list contract phù hợp."
      />
    </div>
  );
}
