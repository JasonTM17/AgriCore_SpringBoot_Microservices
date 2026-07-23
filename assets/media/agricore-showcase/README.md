# AgriCore showcase media

Repository-owned agriculture visuals for the console README, demos, and public
traceability walkthroughs. The three WebP images were generated with the
built-in image generation workflow on 2026-07-23, then optimized with
ImageMagick. `agricore-farm-story.gif` is a bounded 960×540, three-frame
derivative assembled from those images for lightweight previews.

The images contain no user data, production identifiers, logos, or readable
labels. The blank tag in the traceability image is intentionally non-scannable;
runtime QR codes must come from the traceability service.

## Files

| File | Intended use | Dimensions | Size |
|---|---|---:|---:|
| `agricore-farm-sunrise.webp` | Console hero / farm overview | 1672×941 | 247 KiB |
| `agricore-harvest-packing.webp` | Harvest and cold-chain card | 1536×1024 | 215 KiB |
| `agricore-traceability-produce.webp` | Public traceability card | 1122×1402 | 138 KiB |
| `agricore-farm-story.gif` | Three-step farm story preview | 960×540 × 3 | 645 KiB |

## Integrity

SHA-256 values are recorded in [`manifest.json`](./manifest.json). Recompute
them with:

```powershell
node scripts/verify-showcase-media.mjs
```

The verifier checks Git tracking, manifest membership, byte size, SHA-256,
file signatures, and the 2 MiB repository budget. The GIF is intentionally
limited to three frames and is not used as a data
transport or operational animation.
