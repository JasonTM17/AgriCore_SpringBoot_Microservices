# AgriCore Console Capture Provenance

These files are screenshots of the built React Operations Console, not concept
art or generated product mockups. The console runs against
`apps/agricore-console/e2e/mock-edge-server.mjs`, the same deterministic local
edge used by the Playwright critical-journey suite.

## Files

| File | Evidence |
|---|---|
| `agricore-console-login.png` | Secure web-login surface at 1440×900 |
| `agricore-console-dashboard.png` | Authenticated operations dashboard at 1440×900 |
| `agricore-console-farms.png` | Farm membership and plot workspace at 1440×900 |
| `agricore-console-assistant.png` | Persisted assistant conversation after SSE reconnect at 1440×900 |
| `agricore-console-walkthrough.gif` | Login → farms → assistant, 960×600, three bounded frames |

The displayed identities and farm records are local deterministic fixtures.
No production data, secrets, bearer tokens, refresh cookies, or scannable
traceability identifiers appear in the captures.

## Reproduce

From the repository root:

```powershell
pnpm build
Set-Location apps/agricore-console
node e2e/mock-edge-server.mjs
```

Open `http://127.0.0.1:4174`, use any syntactically valid local email and a
password of at least eight characters, then capture at a 1440×900 viewport.
Run `pnpm e2e` to verify the same login, farm, assistant reconnect, and
cancellation journeys before publishing updated media.

The GIF is assembled from the login, farms, and assistant PNGs with
ImageMagick. Preserve the 960×600 bound and a maximum of three frames so GitHub
renders it quickly.
