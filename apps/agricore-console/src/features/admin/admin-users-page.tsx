import { useQuery } from "@tanstack/react-query";

import {
  DataTable,
  ErrorBlock,
  LoadingBlock,
  OpsPage,
} from "../../components/ops/resource-state";
import { createDomainApi } from "../../lib/api/domain-api";
import { useSession } from "../../lib/auth/session";

export function AdminUsersPage() {
  const { api } = useSession();
  const domain = createDomainApi(api);

  const usersQuery = useQuery({
    queryKey: ["admin-users"],
    queryFn: ({ signal }) => domain.listAdminUsers(0, 50, signal),
  });

  return (
    <OpsPage
      title="Quản trị người dùng"
      description="Danh sách user và roles (SYSTEM_ADMIN). Không có create/lock/reset password API."
    >
      {usersQuery.isLoading ? <LoadingBlock /> : null}
      {usersQuery.isError ? (
        <ErrorBlock error={usersQuery.error} onRetry={() => void usersQuery.refetch()} />
      ) : null}
      {usersQuery.data ? (
        <DataTable
          headers={["Email", "Họ tên", "Status", "Roles"]}
          empty="Không có user."
          rows={usersQuery.data.content.map((u) => [
            u.email,
            u.fullName,
            u.status,
            u.roles.join(", "),
          ])}
        />
      ) : null}
    </OpsPage>
  );
}
