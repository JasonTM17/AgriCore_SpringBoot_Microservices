# Code Review — ck:cook finalize — AgriCore Web Assistant

| Field | Value |
|---|---|
| Date | 2026-07-18 |
| Worktree | `D:\worktrees\AgriCore_SpringBoot_Microservices-feature-agricore-web-assistant` |
| Branch | `feature/agricore-web-assistant` |
| Base | `main..HEAD` (~21 commits, 145 files, +15020 / −19) |
| Plan | `plans/260718-1232-agricore-web-assistant/plan.md` (status: completed) |
| Reviewer posture | Adversarial / production-readiness |
| Tester gate (parallel) | PASS — 56 tests green (`plans/reports/tester-ck-cook-finalize-agricore-web-assistant.md`) |

---

## Code Review Summary

### Scope

- **Files:** console (`apps/agricore-console`), identity web auth, assistant-service, gateway route, compose/helm/CI/docker-publish, OpenAPI assistant contract, design assets.
- **LOC:** ~15k insertions (large design HTML/PNG share of bulk).
- **Focus:** Acceptance criteria 1–5 for cook finalize → testing handoff.
- **Scout findings:** cookie path/origin defaults; SSE unbounded workers; provider registry stub; Helm identity browser-auth env gap; advertised tools not executed; error leakage on generation failure path.

### Overall Assessment

The branch delivers a coherent **local/compose testable** slice: memory-only access tokens, HttpOnly refresh cookies with origin allowlist + atomic rotation, role-gated console nav, honest `LIVE_API_CAPABILITIES` for missing list/aggregate APIs, owner-scoped assistant persistence + SSE, gateway/compose/helm/CI wiring, and no committed secrets/`node_modules`/`.env`.

It is **not** production-hardened. Real OpenAI/Ollama generation is a lie (test provider stands in), Helm does not provision browser-auth cookie/origin env for identity, assistant has no rate limits and unbounded SSE/generation thread pools, and generation failures can stream raw exception messages to the browser.

**Cook finalize for testing handoff: CONDITIONAL APPROVE** (local compose + unit/integration suites).  
**Production / Helm browser auth / real LLM: NOT APPROVED.**

**Advisory score: 74 / 100**

---

### Acceptance criteria verdict

| # | Criterion | Verdict | Evidence |
|---|---|---|---|
| 1 | Browser cookie auth: access memory-only; refresh HttpOnly path; atomic refresh; role-gated nav | **PASS** | `AccessTokenStore` memory-only; `WebAuthTokensResponse` omits refresh; cookie path `/api/v1/auth/web`, HttpOnly+SameSite; `findByTokenHashForUpdate` + rotation; `RoleGate`/`NAV_ITEMS` |
| 2 | Ops UI real contracts only; gaps labeled via `LIVE_API_CAPABILITIES` | **PASS** | `domain-api.ts` hits live paths; inventory/sales/iot/dashboard mark list/aggregate false + `ApiGapNotice` |
| 3 | assistant-service boots without key; owner-scoped; SSE; unsafe refusal in test provider | **PASS** | default `provider=none` → 503; ownership on conversations/messages/generations/events; `TestChatProvider` refuses unsafe prompts; tests cover none + unsafe |
| 4 | Gateway + compose + helm console/assistant; CI frontend; docker-publish matrix; console nginx-unprivileged 8080/uid 101 | **PASS (wiring)** / **GAP (Helm auth env)** | routes, compose services, helm values/templates, CI `frontend` job, matrix images, Dockerfile `USER 101` / `EXPOSE 8080` |
| 5 | No secrets, node_modules, or .env committed | **PASS** | gitignore + `git ls-files` clean for those paths |

---

## Critical Issues

*None that fully break the intended local testing path with default compose + `localhost:5173`.*

Production-breaking items are filed under High so testing handoff is not falsely blocked.

---

## High Priority

### H1 — Helm identity missing browser web-auth env (prod cookie flow will 403)

**Where:** `infrastructure/helm/agricore/templates/deployment.yaml` identity env block (~L85–98); `values.yaml` identity section; contrast `services/identity-service/src/main/resources/application.yml` L61–66.

**Problem:** Defaults are:

- `web-allowed-origins: http://localhost:5173,http://127.0.0.1:5173`
- `refresh-cookie-secure: false`

Helm sets neither `AGRICORE_WEB_ALLOWED_ORIGINS` nor `AGRICORE_REFRESH_COOKIE_SECURE`. Any non-localhost console origin gets `ORIGIN_FORBIDDEN` (403) from `RefreshCookieSupport.requireAllowedBrowserOrigin`. HTTPS clusters with `Secure=false` cookies are also wrong.

**Impact:** Helm “integration” of console is incomplete for real browser login/refresh. Compose works only because host map `5173:8080` matches the YAML default.

**Fix:** Wire identity env from values (console public origin list, `refreshCookieSecure: true` in prod, optional cookie name/path). Document required Ingress same-origin edge (console proxies `/api`).

---

### H2 — `openai` / `ollama` providers silently use `TestChatProvider`

**Where:** `ChatProviderRegistry.java` L23–29.

```java
case "openai", "ollama" -> properties.generationAvailable() ? testChatProvider : noneChatProvider;
// OpenAI-compatible HTTP adapter intentionally deferred
```

**Problem:** Setting `ASSISTANT_PROVIDER=openai` + API key reports `generationAvailable=true` and `provider=openai` in capabilities, but generations echo the deterministic test assistant. No egress to OpenAI/Ollama.

**Impact:** Operators believe LLM is live; demos and “configured provider” acceptance are false. Key material may be present while never used (false sense of integration).

**Fix:** Either implement HTTP adapter, or refuse boot / capabilities for openai/ollama until adapter exists (`PROVIDER_NOT_IMPLEMENTED`), and never report those providers as available.

---

### H3 — Generation failure path leaks `ex.getMessage()` over SSE

**Where:** `AssistantApplicationService.java` L286–299; consumed by `assistant-page.tsx` L94–96.

**Problem:** REST `GlobalExceptionHandler` redacts generic errors, but async generation catch stores and streams `ex.getMessage()` as SSE `error` payload. JDBC/driver/NPE messages can reach the browser.

**Impact:** Internal detail disclosure; violates trust-boundary hygiene for a user-facing stream.

**Fix:** Persist stable `errorCode` + sanitized user message (`GENERATION_FAILED`); log full exception server-side only.

---

### H4 — Unbounded SSE + generation worker pools (DoS / thread exhaustion)

**Where:**

- `AssistantController.java` L38, L104–132 — `Executors.newCachedThreadPool()`, `SseEmitter(0L)`, poll loop `Thread.sleep(250)` forever.
- `AssistantApplicationService.java` L55 — second unbounded cached pool for generations.

**Problem:** Each stream holds a thread until terminal status or IOException. No max concurrent generations per user/IP, no emitter timeout, no rate limit (plan called for rate limits).

**Impact:** Authenticated user (or stolen JWT) can open many SSE connections / generations and exhaust threads/memory.

**Fix:** Bounded `ExecutorService` (or virtual-thread + semaphore), emitter timeout, per-user generation rate limit (Redis), max concurrent streams.

---

### H5 — Concurrent idempotent `startGeneration` can 500 instead of replaying

**Where:** `AssistantApplicationService.startGeneration` L141–145 + unique constraint `uk_generation_idempotency` in `V1__init_assistant.sql` L39.

**Problem:** Check-then-insert is not race-safe. Concurrent identical idempotency keys → one insert wins, loser hits `DataIntegrityViolationException` → generic 500 (handler does not map uniqueness conflict back to existing generation).

**Impact:** Client retries under load get hard failures instead of idempotent success.

**Fix:** Catch uniqueness violation and re-read by key; or use `INSERT … ON CONFLICT` / upsert pattern.

---

## Medium Priority

### M1 — Capabilities advertise tools that are never executed

**Where:** `AssistantApplicationService.ALLOWED_TOOLS` L42–44, L79–88; `runGeneration` never invokes tools.

**Impact:** Contract/UI claim read-only domain tools; runtime is pure chat echo (test) or 503 (none). Misleading security/product surface.

**Fix:** Return empty tools until tool runner ships, or implement allowlisted read-only tools with farm/role checks.

---

### M2 — SSE path ignores `conversationId` ownership binding

**Where:** `AssistantController.streamEvents` L100–102; `requireOwnedGeneration` only checks `(generationId, ownerId)`.

**Impact:** Owner can stream a generation under a mismatched conversation path segment (contract inconsistency; weak IDOR-class hygiene). No cross-user leak given owner check, but API is sloppy.

**Fix:** Assert `generation.conversationId.equals(conversationId)` after load.

---

### M3 — No CSP / security headers on console nginx

**Where:** `apps/agricore-console/nginx.conf.template` — only proxy + SPA fallback. Plan red-team explicitly listed CSP.

**Impact:** XSS in console JS would more easily exfiltrate in-memory access tokens (Bearer in `Authorization` and React session state).

**Fix:** Add restrictive CSP, `X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors 'none'`.

---

### M4 — Helm has no Ingress / same-origin edge template

**Where:** `infrastructure/helm/agricore/templates/` only `deployment.yaml` + `secret-template.yaml`.

**Impact:** Console nginx can same-origin proxy `/api` only if traffic enters via console Service. Split Ingress (`/` → console, `/api` → gateway on different hosts) breaks cookies / CORS assumptions.

**Fix:** Document or ship Ingress routing all browser traffic through console (or a shared edge) with TLS.

---

### M5 — Console Deployment inherits DB secret env it does not need

**Where:** `deployment.yaml` L60–74 applied to every service including `console`.

**Impact:** Console pod depends on `agricore-db` Secret existence; unnecessary privilege surface (env contains DB password in process list).

**Fix:** Conditional env blocks — console only gets `GATEWAY_UPSTREAM` / nginx vars.

---

### M6 — Assistant OpenAPI contract is skeletal

**Where:** `contracts/openapi/assistant-service.v1.yaml` — paths without request/response schemas.

**Impact:** FE is hand-written (`domain-api.ts`); contract drift risk; fails “single source of truth” project rule for generated clients.

**Fix:** Expand schemas; generate TS types in a follow-up (non-blocking for handoff if documented).

---

### M7 — Refresh cookie `Secure=false` default is correct for local HTTP only

**Where:** identity `application.yml` L64; compose does not override.

**Impact:** Fine for local testing; production must force `true` (see H1).

---

## Low Priority

- `userId(Authentication)` NPE if `authentication == null` (`AssistantController` L143–148) — mitigated by resource-server filter chain, still brittle.
- Dual access-token storage (`AccessTokenStore` + React state) is intentional for re-renders; ensure no logging of token.
- Design HTML/PNG bulk in repo inflates clone size; acceptable for this plan phase.
- Assistant Dockerfile uses full JRE alpine (not distroless) — project universal rule debt, pre-existing pattern across services.

---

## Edge Cases Found by Scout

1. **StrictMode double bootstrap:** mitigated by single-flight `webRefresh()` when no AbortSignal (`client.ts` L67–81).
2. **Concurrent refresh rotation:** pessimistic lock + 5s grace avoids family wipe on legitimate races (`AuthApplicationService` L40, L212–225); reuse outside grace still revokes family.
3. **SSE disconnect:** `IOException` completes emitter with error; no explicit `onTimeout`/`onCompletion` cleanup registration — rely on send failure.
4. **Cookie path narrow:** `/api/v1/auth/web` correctly keeps refresh off other API routes; requires same-site browser origin for console proxy (compose OK).
5. **Origin vs Referer fallback:** non-browser clients can forge Origin once cookie is stolen; SameSite=Strict is the primary browser CSRF control.
6. **Idempotency race:** see H5.
7. **Provider=none + existing conversation:** create works; generate 503 — verified by `NoneProviderGenerationTest`.

---

## Contract / RBAC / Security notes

| Area | Status |
|---|---|
| Access token not in localStorage/sessionStorage | OK (memory `AccessTokenStore`) |
| Refresh not in JSON body | OK (`WebAuthTokensResponse`) |
| HttpOnly + path-scoped cookie | OK |
| Origin allowlist on web auth | OK (compose); Helm gap H1 |
| Atomic refresh rotation | OK (`PESSIMISTIC_WRITE`) |
| Gateway JWT on `/api/v1/assistant/**` | OK (`anyExchange().authenticated()`; auth paths permitAll) |
| Domain services JWT via `common-security` | OK for assistant |
| Frontend RoleGate | UX only — backend must enforce (admin does; assistant is any authenticated user by design) |
| Owner isolation assistant | OK (service-layer owner checks + tests) |
| LIVE API honesty | OK |
| Secrets in git | OK |
| Real LLM / tools / rate limits | Incomplete |

---

## Positive observations (risk calibration only)

- Cookie web auth is deliberately separated from body-token mobile/API login; refresh never exposed to JS.
- Frontend single-flight refresh + credentials `include` matches cookie design.
- Missing inventory/sales list/dashboard aggregate are explicitly not faked.
- `provider=none` default + 503 path is the right fail-closed generation posture.
- Console container contract tests encode non-root 8080/uid 101 requirements.

---

## Recommended actions (priority order)

1. **Before any Helm/prod browser test:** H1 web origins + Secure cookie env on identity.
2. **Before claiming LLM integration:** H2 real adapter or explicit not-implemented.
3. **Before multi-user load:** H3 redact SSE errors; H4 bound pools + rate limits; H5 idempotent race.
4. **Follow-ups:** M1 tools honesty, M2 conversationId bind, M3 CSP, M4 Ingress, M6 OpenAPI depth.

---

## Cook finalize decision

| Question | Answer |
|---|---|
| Approve implementation for **local/compose testing handoff**? | **YES — conditional** |
| Approve for **production / public Helm**? | **NO** |
| Required conditions for handoff | Use compose (or Vite `:5173` proxy) with identity default origins; set `ASSISTANT_PROVIDER=test` for chat demos (not `openai`); treat openai/ollama as unimplemented; track H1–H5 as post-handoff blockers for prod |
| Plan status recommendation | Leave plan **completed** for feature delivery scope; open follow-up plan/issues for prod auth env, real provider, rate limits, CSP |

---

## Metrics (advisory)

| Metric | Value |
|---|---|
| Type coverage (console) | Not measured in this review; `pnpm typecheck` PASS (tester) |
| Test coverage % | Not measured; console **33/33**, identity selected **15/15**, assistant selected **8/8** |
| Linting issues | 0 (tester `pnpm lint` PASS) |
| Secrets / node_modules / .env in git | 0 |
| Critical findings | 0 |
| High findings | 5 |
| Medium findings | 7 |

---

## Unresolved questions

1. Is intentional scope for this branch “test provider only,” with OpenAI deferred to a later plan? Code comments say yes; capabilities still advertise openai when key set — product decision needed.
2. Will production edge always terminate TLS at console nginx (same-origin), or split hostnames? Determines cookie Domain/SameSite and H1 origin list.
3. Are assistant rate limits deferred by product decision, or an incomplete phase-6/8 item?

---

## Behavioral checklist

- [x] Concurrency — refresh lock OK; generation idempotency race (H5); SSE thread growth (H4)
- [x] Error boundaries — REST redacted; SSE leak (H3)
- [x] API contracts — live domain paths OK; tools advertised but dead (M1); OpenAPI thin (M6)
- [x] Backwards compatibility — web auth additive under `/api/v1/auth/web`
- [x] Input validation — generation content/idempotency required; bean validation on start request
- [x] Auth/authz — JWT resource server + owner checks; frontend RoleGate UX-only
- [x] N+1 / query efficiency — not a primary risk in this slice; SSE poll every 250ms is chatty
- [x] Data leaks — SSE exception messages (H3); no refresh in JSON
- [x] Fact-checked vs plan — phase files marked completed; AC mapped above

---

*Report only. No production source modified. No push.*
