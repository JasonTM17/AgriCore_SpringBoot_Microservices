import { expect, test, type Page } from "@playwright/test";

interface E2eState {
  users: Array<{
    email: string;
    generations: Array<{
      streamConnections: number;
      cancellationRequests: number;
      status: string;
    }>;
  }>;
}

async function login(page: Page, email: string) {
  await page.goto("/login");
  await page.locator('input[name="email"]').fill(email);
  await page.locator('input[name="password"]').fill("SecurePass123!");
  await page.locator('form[aria-labelledby="login-title"] button[type="submit"]').click();
  await page.waitForURL((url) => url.pathname === "/");
}

async function openConversation(page: Page, title: string) {
  await page.goto("/assistant");
  await expect(page.locator('input[name="assistant-conversation-title"]')).toBeVisible();
  await page.locator('input[name="assistant-conversation-title"]').fill(title);
  await page.locator('form[aria-labelledby="new-assistant-conversation-heading"] button[type="submit"]').click();
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
}

async function generationState(page: Page, email: string) {
  const response = await page.request.get("/__e2e/state");
  expect(response.ok()).toBeTruthy();
  const body = await response.json() as E2eState;
  return body.users.find((user) => user.email === email)?.generations.at(-1);
}

test("keeps refresh credentials HttpOnly, rotates them, and serves hardened browser headers", async ({ page, context }) => {
  const loginResponse = await page.goto("/login");
  expect(loginResponse?.headers()["content-security-policy"]).toContain("default-src 'self'");
  expect(loginResponse?.headers()["cross-origin-opener-policy"]).toBe("same-origin");
  expect(loginResponse?.headers()["permissions-policy"]).toContain("camera=()");
  expect(loginResponse?.headers()["referrer-policy"]).toBe("strict-origin-when-cross-origin");
  expect(loginResponse?.headers()["x-content-type-options"]).toBe("nosniff");
  expect(loginResponse?.headers()["x-frame-options"]).toBe("DENY");

  await page.locator('input[name="email"]').fill("session@example.com");
  await page.locator('input[name="password"]').fill("SecurePass123!");
  await page.locator('form[aria-labelledby="login-title"] button[type="submit"]').click();
  await page.waitForURL((url) => url.pathname === "/");

  const firstCookie = (await context.cookies()).find((cookie) => cookie.name === "agricore_refresh");
  expect(firstCookie).toMatchObject({ httpOnly: true, sameSite: "Strict", path: "/api/v1/auth/web" });
  await expect.poll(() => page.evaluate<string>("document.cookie")).not.toContain("agricore_refresh");

  const refreshed = page.waitForResponse((response) =>
    response.url().endsWith("/api/v1/auth/web/refresh") && response.status() === 200);
  await page.reload();
  await refreshed;
  const rotatedCookie = (await context.cookies()).find((cookie) => cookie.name === "agricore_refresh");
  expect(rotatedCookie?.value).not.toBe(firstCookie?.value);
  await expect(page).not.toHaveURL(/\/login/);
});

test("reconnects a durable assistant SSE stream and completes without duplicate output", async ({ page }) => {
  const email = "reconnect@example.com";
  await login(page, email);
  await openConversation(page, "Reconnect journey");

  await page.locator("#assistant-prompt").fill("Reconnect this durable response");
  await page.locator("#assistant-prompt").press("Enter");

  await expect(page.getByText("Đã hoàn tất", { exact: true })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText("Mock response after reconnect.", { exact: false })).toHaveCount(1);
  await expect.poll(async () => (await generationState(page, email))?.streamConnections).toBe(2);
  await expect.poll(async () => (await generationState(page, email))?.status).toBe("COMPLETED");
});

test("cancels an active assistant generation and reaches a durable terminal state", async ({ page }) => {
  const email = "cancel@example.com";
  await login(page, email);
  await openConversation(page, "Cancellation journey");

  await page.locator("#assistant-prompt").fill("Cancel this generation safely");
  await page.locator("#assistant-prompt").press("Enter");
  const cancelButton = page.getByRole("button", { name: "Dừng phản hồi" });
  await expect(cancelButton).toBeVisible({ timeout: 10_000 });
  await cancelButton.click();

  await expect(page.getByText("Đã hủy", { exact: true })).toBeVisible({ timeout: 10_000 });
  await expect.poll(async () => (await generationState(page, email))?.cancellationRequests).toBe(1);
  await expect.poll(async () => (await generationState(page, email))?.status).toBe("CANCELLED");
});
