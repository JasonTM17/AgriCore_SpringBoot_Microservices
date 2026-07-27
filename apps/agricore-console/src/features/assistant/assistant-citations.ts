const CITATION_PATTERN = /\[([A-Z][A-Z0-9]{0,15}-[A-Z0-9][A-Z0-9-]{0,15})\]/g;
const MAX_CITATIONS = 25;

/**
 * Extracts only the citation format accepted by the assistant output policy.
 * The renderer still treats the returned IDs as plain text; this helper never
 * turns model output into HTML or links.
 */
export function extractAssistantCitations(content: string): string[] {
  const citations: string[] = [];
  const seen = new Set<string>();
  for (const match of content.matchAll(CITATION_PATTERN)) {
    const citationId = match[1];
    if (!citationId || seen.has(citationId)) continue;
    seen.add(citationId);
    citations.push(citationId);
    if (citations.length === MAX_CITATIONS) break;
  }
  return citations;
}
