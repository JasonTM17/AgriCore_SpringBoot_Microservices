import { useEffect, useRef, useState, type FormEvent } from "react";

import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import type { CreateAssistantConversationRequest, FarmResponse } from "../../lib/api/types";
import { AssistantErrorNotice } from "./assistant-error-notice";

interface AssistantNewConversationFormProps {
  activeFarm: FarmResponse | null;
  error: Error | null;
  isPending: boolean;
  onSubmit: (request: CreateAssistantConversationRequest) => void;
}

export function AssistantNewConversationForm({
  activeFarm,
  error,
  isPending,
  onSubmit,
}: AssistantNewConversationFormProps) {
  const [title, setTitle] = useState("");
  const [contextType, setContextType] = useState<"ENTERPRISE" | "FARM">(
    activeFarm ? "FARM" : "ENTERPRISE",
  );
  const submitLockedRef = useRef(false);
  const selectedContext = contextType === "FARM" && !activeFarm ? "ENTERPRISE" : contextType;

  useEffect(() => {
    if (!isPending) submitLockedRef.current = false;
  }, [isPending]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitLockedRef.current || isPending || title.trim().length === 0) return;
    submitLockedRef.current = true;
    onSubmit(selectedContext === "FARM"
      ? { title, contextType: "FARM", farmId: activeFarm?.id ?? null }
      : { title, contextType: "ENTERPRISE", farmId: null });
  }

  return (
    <form
      className="space-y-4 rounded-card border border-border bg-surface p-4 shadow-sm"
      aria-labelledby="new-assistant-conversation-heading"
      onSubmit={handleSubmit}
    >
      <div>
        <h2 id="new-assistant-conversation-heading" className="font-semibold text-ink">
          Hội thoại mới
        </h2>
        <p className="mt-1 text-xs leading-5 text-muted">
          Phạm vi chỉ tạo ngữ cảnh; assistant-service vẫn xác minh quyền ở backend.
        </p>
      </div>
      <Input
        label="Tên hội thoại"
        name="assistant-conversation-title"
        value={title}
        maxLength={200}
        placeholder="Ví dụ: Kiểm tra mùa vụ tuần này"
        disabled={isPending}
        onChange={(event) => setTitle(event.target.value)}
      />
      <label className="grid gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted">
        Phạm vi dữ liệu
        <select
          className="h-11 rounded-control border border-border bg-surface px-3 text-sm font-medium normal-case tracking-normal text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info"
          value={selectedContext}
          disabled={isPending}
          onChange={(event) => setContextType(event.target.value as "ENTERPRISE" | "FARM")}
        >
          <option value="ENTERPRISE">Toàn doanh nghiệp</option>
          <option value="FARM" disabled={!activeFarm}>Nông trại đang chọn</option>
        </select>
      </label>
      {selectedContext === "FARM" && activeFarm ? (
        <p className="rounded-control bg-forest-50 px-3 py-2 text-xs text-forest-900">
          {activeFarm.code} · {activeFarm.name}
        </p>
      ) : null}
      {error ? <AssistantErrorNotice error={error} /> : null}
      <Button className="w-full" type="submit" disabled={isPending || title.trim().length === 0}>
        {isPending ? "Đang tạo…" : "Tạo hội thoại"}
      </Button>
    </form>
  );
}
