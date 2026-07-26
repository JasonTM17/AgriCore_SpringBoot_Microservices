import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";

import { Button } from "../../components/ui/button";
import { EmptyState } from "../../components/ui/empty-state";
import { Input } from "../../components/ui/input";
import { useSession } from "../../lib/auth/session";
import {
  listAdminUsers,
  updateAdminUserRoles,
  type AdminRoleCode,
  type AdminUser,
} from "./admin-api";

const PAGE_SIZE = 20;
const ROLE_CODES: readonly AdminRoleCode[] = [
  "SYSTEM_ADMIN",
  "FARM_MANAGER",
  "AGRONOMIST",
  "FIELD_WORKER",
  "WAREHOUSE_MANAGER",
  "SALES_STAFF",
  "AUDITOR",
];
const EMPTY_USERS: AdminUser[] = [];
const roleLabels: Record<AdminRoleCode, string> = {
  SYSTEM_ADMIN: "Quản trị hệ thống",
  FARM_MANAGER: "Quản lý nông trại",
  AGRONOMIST: "Kỹ thuật nông học",
  FIELD_WORKER: "Nhân viên hiện trường",
  WAREHOUSE_MANAGER: "Quản lý kho",
  SALES_STAFF: "Nhân viên bán hàng",
  AUDITOR: "Kiểm toán",
};

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

function userStatus(status: AdminUser["status"]): string {
  return status === "ACTIVE" ? "Đang hoạt động" : status === "LOCKED" ? "Đang khóa" : "Đã vô hiệu";
}

export function AdminUsersPage() {
  const { api, user } = useSession();
  const canRead = user?.permissions.includes("IDENTITY_USER_READ") ?? false;
  const canEdit = user?.permissions.includes("IDENTITY_USER_ADMIN") ?? false;
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [draftRoles, setDraftRoles] = useState<AdminRoleCode[]>([]);
  const [confirming, setConfirming] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const usersQuery = useQuery({
    queryKey: ["admin", "users", page],
    queryFn: ({ signal }) => listAdminUsers(api, page, PAGE_SIZE, signal),
    enabled: canRead,
  });
  const users = usersQuery.data?.content ?? EMPTY_USERS;
  const filteredUsers = useMemo(() => {
    const term = search.trim().toLocaleLowerCase();
    return term ? users.filter((user) => `${user.fullName} ${user.email}`.toLocaleLowerCase().includes(term)) : users;
  }, [search, users]);
  const selectedUser = users.find((user) => user.id === selectedId) ?? filteredUsers[0] ?? null;
  const selectedRoles = selectedId === selectedUser?.id ? draftRoles : selectedUser?.roles ?? [];
  const roleMutation = useMutation({
    mutationFn: () => {
      if (!selectedUser) throw new Error("Chưa chọn người dùng.");
      return updateAdminUserRoles(api, selectedUser.id, { roles: selectedRoles });
    },
    onSuccess: (updated) => {
      setConfirming(false);
      setFormError(null);
      setSelectedId(updated.id);
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
    onError: (error) => setFormError(errorMessage(error, "Không thể cập nhật vai trò.")),
  });

  function selectUser(user: AdminUser) {
    setSelectedId(user.id);
    setDraftRoles(user.roles);
    setConfirming(false);
    setFormError(null);
  }

  function requestSave() {
    if (!selectedUser) return;
    if (!canEdit) {
      setFormError("Phiên hiện tại thiếu quyền IDENTITY_USER_ADMIN.");
      return;
    }
    if (selectedRoles.length === 0) {
      setFormError("Phải chọn ít nhất một vai trò.");
      return;
    }
    setFormError(null);
    setConfirming(true);
  }

  const loadError = usersQuery.error ? errorMessage(usersQuery.error, "Không thể tải danh sách người dùng.") : null;
  return (
    <div className="animate-fade-in-up space-y-6">
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">Quản trị</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-ink">Người dùng & vai trò</h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">Danh sách phân trang từ identity-service. Cập nhật role theo nguyên tắc least privilege; token cũ vẫn giữ snapshot cho đến khi refresh.</p>
        </div>
        <span className="rounded-control border border-forest-100 bg-forest-50 px-3 py-2 text-xs font-semibold text-forest-900">SYSTEM_ADMIN</span>
      </header>

      {!canRead ? <section className="rounded-card border border-harvest-600/40 bg-harvest-100 p-4 text-sm text-ink" role="alert">Phiên hiện tại thiếu quyền <span className="font-mono">IDENTITY_USER_READ</span>. Tải lại phiên sau khi quyền được cấp.</section> : null}

      <section className="flex flex-col gap-3 rounded-card border border-border bg-surface p-4 shadow-sm sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1"><Input label="Tìm trong trang hiện tại" value={search} onChange={(event) => setSearch(event.target.value)} hint="API chỉ hỗ trợ phân trang; bộ lọc này không thay đổi truy vấn server." /></div>
        <Button variant="secondary" onClick={() => void usersQuery.refetch()}>Làm mới</Button>
      </section>

      {canRead && usersQuery.isPending ? <section className="rounded-card border border-border bg-surface p-6" role="status">Đang tải danh sách người dùng…</section> : null}
      {loadError ? <section className="rounded-card border border-danger/40 bg-red-50 p-5" role="alert"><p className="font-semibold text-danger">Không thể tải danh sách</p><p className="mt-1 text-sm text-ink">{loadError}</p><Button variant="secondary" className="mt-4" onClick={() => void usersQuery.refetch()}>Thử lại</Button></section> : null}
      {canRead && !usersQuery.isPending && !loadError && users.length === 0 ? <EmptyState title="Chưa có người dùng" description="Identity-service trả về trang rỗng; không tạo dữ liệu mẫu trong console." /> : null}

      {users.length > 0 ? (
        <div className="grid gap-6 xl:grid-cols-[minmax(0,1.25fr)_minmax(20rem,0.75fr)]">
          <section className="overflow-hidden rounded-card border border-border bg-surface shadow-sm">
            <div className="border-b border-border p-4"><h2 className="text-lg font-semibold text-ink">Danh sách người dùng</h2><p className="mt-1 text-xs text-muted">{usersQuery.data?.totalElements ?? users.length} tài khoản · trang {(usersQuery.data?.page ?? page) + 1}</p></div>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[38rem] text-left text-sm">
                <thead className="bg-canvas text-xs uppercase tracking-wide text-muted">
                  <tr>
                    <th className="px-4 py-3">Người dùng</th>
                    <th className="px-4 py-3">Trạng thái</th>
                    <th className="px-4 py-3">Vai trò</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredUsers.map((user) => {
                    const selected = selectedUser?.id === user.id;
                    return (
                      <tr
                        key={user.id}
                        className={`border-t border-border transition-colors hover:bg-forest-50 ${
                          selected ? "bg-forest-100" : ""
                        }`}
                      >
                        <td className="p-0">
                          <button
                            type="button"
                            className="block w-full cursor-pointer px-4 py-3 text-left"
                            aria-label={`Chọn người dùng ${user.fullName}`}
                            aria-pressed={selected}
                            onClick={() => selectUser(user)}
                          >
                            <span className="block font-semibold text-ink">{user.fullName}</span>
                            <span className="mt-1 block text-xs text-muted">{user.email}</span>
                          </button>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={`rounded px-2 py-1 text-xs font-semibold ${
                              user.status === "ACTIVE"
                                ? "bg-green-50 text-success"
                                : user.status === "LOCKED"
                                  ? "bg-harvest-100 text-warning"
                                  : "bg-red-50 text-danger"
                            }`}
                          >
                            {userStatus(user.status)}
                          </span>
                        </td>
                        <td className="px-4 py-3 font-mono text-xs text-muted">
                          {user.roles.join(" · ") || "—"}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            {filteredUsers.length === 0 ? <p className="p-6 text-sm text-muted">Không có kết quả trong trang hiện tại.</p> : null}
            <div className="flex items-center justify-between border-t border-border p-4 text-sm"><span className="text-muted">Hiển thị {filteredUsers.length} tài khoản</span><div className="flex gap-2"><Button variant="secondary" disabled={page === 0 || usersQuery.isFetching} onClick={() => setPage((current) => Math.max(0, current - 1))}>Trước</Button><Button variant="secondary" disabled={Boolean(usersQuery.data?.last) || usersQuery.isFetching} onClick={() => setPage((current) => current + 1)}>Sau</Button></div></div>
          </section>

          <section className="rounded-card border border-border bg-surface shadow-sm">
            {selectedUser ? <><div className="border-b border-border p-5"><p className="text-xs font-semibold uppercase tracking-wide text-muted">Đang chọn</p><h2 className="mt-1 text-xl font-semibold text-ink">{selectedUser.fullName}</h2><p className="mt-1 text-sm text-muted">{selectedUser.email}</p><span className={`mt-3 inline-flex rounded px-2 py-1 text-xs font-semibold ${selectedUser.status === "ACTIVE" ? "bg-green-50 text-success" : "bg-harvest-100 text-warning"}`}>{userStatus(selectedUser.status)}</span></div><div className="p-5"><h3 className="text-base font-semibold text-ink">Vai trò được cấp</h3><div className="mt-4 grid gap-2">{ROLE_CODES.map((role) => <label key={role} className={`flex items-start gap-3 rounded-control border p-3 ${canEdit ? "cursor-pointer" : "cursor-not-allowed opacity-70"} ${selectedRoles.includes(role) ? "border-forest-700 bg-forest-50" : "border-border bg-surface"}`}><input type="checkbox" checked={selectedRoles.includes(role)} disabled={!canEdit} onChange={(event) => setDraftRoles((current) => event.target.checked ? [...current, role] : current.filter((item) => item !== role))} className="mt-0.5 size-4 accent-forest-700" /><span><span className="block text-sm font-semibold text-ink">{roleLabels[role]}</span><span className="mt-1 block font-mono text-xs text-muted">{role}</span></span></label>)}</div>{formError ? <p className="mt-4 rounded-control border border-danger/40 bg-red-50 px-3 py-2 text-sm text-danger" role="alert">{formError}</p> : null}{confirming ? <div className="mt-4 rounded-control border border-harvest-600/40 bg-harvest-100 p-4"><p className="text-sm text-ink">Bạn sắp cấp: <span className="font-mono">{selectedRoles.join(" · ")}</span>. Xác nhận thay đổi quyền truy cập?</p><div className="mt-3 flex gap-2"><Button variant="secondary" onClick={() => setConfirming(false)}>Quay lại</Button><Button disabled={roleMutation.isPending} onClick={() => roleMutation.mutate()}>{roleMutation.isPending ? "Đang lưu…" : "Xác nhận"}</Button></div></div> : <Button className="mt-5 w-full" disabled={!canEdit} onClick={requestSave}>Lưu vai trò</Button>}</div><div className="border-t border-border p-5"><div className="grid gap-2 sm:grid-cols-2"><Button variant="secondary" disabled title="API chưa hỗ trợ">Tạo người dùng</Button><Button variant="secondary" disabled title="API chưa hỗ trợ">Đặt lại mật khẩu</Button></div><p className="mt-3 text-xs text-muted">Các thao tác tài khoản khác chưa có trong identity controller.</p></div></> : <EmptyState title="Chọn một người dùng" description="Chọn một dòng để xem và chỉnh sửa role." />}
          </section>
        </div>
      ) : null}
    </div>
  );
}
