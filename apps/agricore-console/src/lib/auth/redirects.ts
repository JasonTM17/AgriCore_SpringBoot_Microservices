const CONSOLE_ORIGIN = "https://console.agricore.invalid";

function containsControlCharacter(value: string): boolean {
  return Array.from(value).some((character) => {
    const code = character.charCodeAt(0);
    return code <= 0x1f || code === 0x7f;
  });
}

/**
 * Accept only normalized same-origin paths for post-login navigation.
 * TanStack Router still owns route matching; this helper only closes the
 * external/open-redirect boundary.
 */
export function sanitizeInternalRedirect(value: unknown): string | undefined {
  if (typeof value !== "string" || value.length === 0 || value !== value.trim()) {
    return undefined;
  }

  const lowerValue = value.toLowerCase();
  if (
    !value.startsWith("/") ||
    value.startsWith("//") ||
    value.includes("\\") ||
    lowerValue.startsWith("/%2f") ||
    lowerValue.includes("%5c") ||
    containsControlCharacter(value)
  ) {
    return undefined;
  }

  try {
    const url = new URL(value, CONSOLE_ORIGIN);
    if (url.origin !== CONSOLE_ORIGIN || url.pathname === "/login") {
      return undefined;
    }
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return undefined;
  }
}
