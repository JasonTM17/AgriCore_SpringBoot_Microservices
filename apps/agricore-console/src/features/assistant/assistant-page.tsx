import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { type FormEvent, useMemo, useState } from "react";

import { Button } from "../../components/ui/button";
import {
  ApiGapNotice,
  ErrorBlock,
  LoadingBlock,
  OpsPage,
} from "../../components/ops/resource-state";
import { createDomainApi } from "../../lib/api/domain-api";
import { ApiClientError } from "../../lib/api/errors";
import { useSession } from "../../lib/auth/session";

export function AssistantPage() {
  const { api, accessToken } = useSession();
  const domain = createDomainApi(api);
  const queryClient = useQueryClient();
  const [activeId, setActiveId] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const [streamText, setStreamText] = useState("");
  const [streamError, setStreamError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  const capsQuery = useQuery({
    queryKey: ["assistant-capabilities"],
    queryFn: ({ signal }) => domain.assistantCapabilities(signal),
  });

  const listQuery = useQuery({
    queryKey: ["assistant-conversations"],
    queryFn: ({ signal }) => domain.listConversations(signal),
  });

  const messagesQuery = useQuery({
    queryKey: ["assistant-messages", activeId],
    queryFn: ({ signal }) => domain.listMessages(activeId!, signal),
    enabled: Boolean(activeId),
  });

  const createMutation = useMutation({
    mutationFn: () => domain.createConversation({ title: "Hội thoại mới" }),
    onSuccess: async (conversation) => {
      setActiveId(conversation.id);
      await queryClient.invalidateQueries({ queryKey: ["assistant-conversations"] });
    },
  });

  const generationAvailable = capsQuery.data?.generationAvailable === true;
  const sortedMessages = useMemo(() => messagesQuery.data ?? [], [messagesQuery.data]);

  async function streamGeneration(conversationId: string, generationId: string) {
    setStreamText("");
    setStreamError(null);
    const response = await fetch(
      `/api/v1/assistant/conversations/${conversationId}/generations/${generationId}/events?after=-1`,
      {
        headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
        credentials: "include",
      },
    );
    if (!response.ok || !response.body) {
      throw new Error(`SSE failed: ${response.status}`);
    }
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      const chunks = buffer.split("\n\n");
      buffer = chunks.pop() ?? "";
      for (const chunk of chunks) {
        const dataLine = chunk.split("\n").find((line) => line.startsWith("data:"));
        if (!dataLine) {
          continue;
        }
        const payload = dataLine.slice(5).trim();
        if (!payload || payload === "[DONE]") {
          continue;
        }
        try {
          const event = JSON.parse(payload) as {
            type?: string;
            delta?: string;
            message?: string;
          };
          if (event.type === "delta" && event.delta) {
            setStreamText((prev) => prev + event.delta);
          }
          if (event.type === "error" && event.message) {
            setStreamError(event.message);
          }
        } catch {
          // ignore malformed frames
        }
      }
    }
  }

  async function onSend(event: FormEvent) {
    event.preventDefault();
    if (!activeId || !draft.trim() || sending) {
      return;
    }
    if (!generationAvailable) {
      setStreamError(
        capsQuery.data?.reason ?? "Assistant generation unavailable (no provider key).",
      );
      return;
    }
    const content = draft.trim();
    setDraft("");
    setSending(true);
    try {
      const started = await domain.startGeneration(activeId, {
        content,
        idempotencyKey: crypto.randomUUID(),
      });
      await streamGeneration(activeId, started.generationId);
      await queryClient.invalidateQueries({ queryKey: ["assistant-messages", activeId] });
      setStreamText("");
    } catch (error) {
      setStreamError(
        error instanceof ApiClientError
          ? `${error.code}: ${error.message}`
          : error instanceof Error
            ? error.message
            : "Generation failed",
      );
    } finally {
      setSending(false);
    }
  }

  return (
    <OpsPage
      title="Trợ lý vận hành"
      description="Chat read-only domain tools, SSE streaming, ownership trên assistant-service."
    >
      {capsQuery.isLoading ? <LoadingBlock label="Đang kiểm tra capabilities..." /> : null}
      {capsQuery.data && !capsQuery.data.generationAvailable ? (
        <ApiGapNotice
          capability="llmProvider"
          detail={
            capsQuery.data.reason ??
            "Provider=none — service boot OK, generation trả 503 cho đến khi cấu hình key."
          }
        />
      ) : null}
      {capsQuery.isError ? (
        <ErrorBlock error={capsQuery.error} onRetry={() => void capsQuery.refetch()} />
      ) : null}

      <div className="grid gap-4 lg:grid-cols-[240px_1fr]">
        <aside className="rounded-card border border-border bg-surface p-3">
          <Button
            className="mb-3 w-full"
            variant="secondary"
            onClick={() => createMutation.mutate()}
            disabled={createMutation.isPending}
          >
            Hội thoại mới
          </Button>
          {listQuery.isLoading ? <LoadingBlock label="..." /> : null}
          <ul className="space-y-1">
            {(listQuery.data ?? []).map((c) => (
              <li key={c.id}>
                <button
                  type="button"
                  className={`w-full rounded-control px-3 py-2 text-left text-sm ${
                    activeId === c.id ? "bg-forest-100 font-semibold" : "hover:bg-forest-50"
                  }`}
                  onClick={() => setActiveId(c.id)}
                >
                  {c.title || c.id.slice(0, 8)}
                </button>
              </li>
            ))}
          </ul>
        </aside>

        <section className="flex min-h-[24rem] flex-col rounded-card border border-border bg-surface">
          <div className="flex-1 space-y-3 overflow-y-auto p-4">
            {!activeId ? <p className="text-sm text-muted">Chọn hoặc tạo hội thoại.</p> : null}
            {messagesQuery.isLoading ? <LoadingBlock /> : null}
            {sortedMessages.map((m) => (
              <div
                key={m.id}
                className={`max-w-[90%] rounded-control px-3 py-2 text-sm ${
                  m.role === "USER" ? "ml-auto bg-forest-700 text-white" : "bg-forest-50 text-ink"
                }`}
              >
                <p className="text-[10px] uppercase opacity-70">{m.role}</p>
                <p className="whitespace-pre-wrap">{m.content}</p>
              </div>
            ))}
            {streamText ? (
              <div className="max-w-[90%] rounded-control bg-info/10 px-3 py-2 text-sm">
                <p className="text-[10px] uppercase text-info">streaming</p>
                <p className="whitespace-pre-wrap">{streamText}</p>
              </div>
            ) : null}
            {streamError ? (
              <p className="text-sm text-danger" role="alert">
                {streamError}
              </p>
            ) : null}
          </div>
          <form onSubmit={(e) => void onSend(e)} className="flex gap-2 border-t border-border p-3">
            <input
              className="h-10 flex-1 rounded-control border border-border px-3 text-sm"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="Hỏi về nông trại, mùa vụ, tồn kho (read-only)..."
              disabled={!activeId || sending}
            />
            <Button type="submit" disabled={!activeId || !draft.trim() || sending}>
              Gửi
            </Button>
          </form>
        </section>
      </div>
    </OpsPage>
  );
}
