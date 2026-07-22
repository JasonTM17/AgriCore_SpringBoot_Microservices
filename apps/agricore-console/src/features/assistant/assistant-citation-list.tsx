import { extractAssistantCitations } from "./assistant-citations";

export function AssistantCitationList({ content }: { content: string }) {
  const citations = extractAssistantCitations(content);
  if (citations.length === 0) return null;

  return (
    <footer className="mt-3 border-t border-border/70 pt-2" aria-label="Nguồn tham chiếu">
      <p className="text-[0.7rem] font-semibold uppercase tracking-wide text-muted">
        Nguồn tham chiếu
      </p>
      <ul className="mt-1 flex flex-wrap gap-1.5">
        {citations.map((citationId) => (
          <li key={citationId}>
            <span className="inline-flex rounded-full bg-forest-50 px-2 py-0.5 text-[0.7rem] font-medium text-forest-900">
              [{citationId}]
            </span>
          </li>
        ))}
      </ul>
      <p className="mt-1 text-[0.7rem] text-muted">
        Mã tham chiếu do chatbot trả về; hãy đối chiếu với dữ liệu nghiệp vụ trước khi hành động.
      </p>
    </footer>
  );
}
