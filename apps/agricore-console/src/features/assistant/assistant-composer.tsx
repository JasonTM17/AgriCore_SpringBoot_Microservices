import { useState, type FormEvent, type KeyboardEvent } from "react";

import { Button } from "../../components/ui/button";

interface AssistantComposerProps {
  disabled: boolean;
  disabledReason: string | null;
  isSubmitting: boolean;
  onSend: (prompt: string) => Promise<boolean>;
}

export function AssistantComposer({
  disabled,
  disabledReason,
  isSubmitting,
  onSend,
}: AssistantComposerProps) {
  const [prompt, setPrompt] = useState("");

  async function submitPrompt() {
    if (disabled || isSubmitting) return;
    const accepted = await onSend(prompt);
    if (accepted) setPrompt("");
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void submitPrompt();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== "Enter" || event.shiftKey || event.nativeEvent.isComposing) return;
    event.preventDefault();
    event.currentTarget.form?.requestSubmit();
  }

  return (
    <form className="border-t border-border p-4" onSubmit={handleSubmit}>
      <label htmlFor="assistant-prompt" className="sr-only">Câu hỏi cho trợ lý</label>
      <textarea
        id="assistant-prompt"
        className="min-h-28 w-full resize-y rounded-control border border-border bg-surface px-3 py-3 text-sm leading-6 text-ink placeholder:text-muted focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info disabled:bg-canvas"
        value={prompt}
        rows={4}
        maxLength={200_000}
        placeholder="Hỏi về vận hành, mùa vụ hoặc dữ liệu trong phạm vi hội thoại…"
        disabled={disabled || isSubmitting}
        aria-describedby="assistant-composer-help"
        onChange={(event) => setPrompt(event.target.value)}
        onKeyDown={handleKeyDown}
      />
      <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
        <div id="assistant-composer-help" className="text-xs text-muted">
          <p>Enter để gửi · Shift+Enter để xuống dòng · {prompt.length.toLocaleString("vi-VN")}/200.000</p>
          <p className="mt-1">Nội dung AI có thể sai; hãy kiểm tra trước khi thực hiện thao tác quan trọng.</p>
          {disabledReason ? <p className="mt-1 font-medium text-warning">{disabledReason}</p> : null}
        </div>
        <Button type="submit" disabled={disabled || isSubmitting || prompt.trim().length === 0}>
          {isSubmitting ? "Đang gửi…" : "Gửi câu hỏi"}
        </Button>
      </div>
    </form>
  );
}
