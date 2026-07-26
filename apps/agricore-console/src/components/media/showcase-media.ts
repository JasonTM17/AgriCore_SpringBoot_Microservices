import type { ShowcaseImageAsset } from "./responsive-showcase-image";

export const farmSunriseShowcase: ShowcaseImageAsset = {
  src: "/agricore-farm-sunrise.webp",
  width: 1672,
  height: 941,
  mimeType: "image/webp",
  sources: [
    { src: "/agricore-farm-sunrise-thumbnail-240w.webp", width: 240 },
    { src: "/agricore-farm-sunrise-480w.webp", width: 480 },
    { src: "/agricore-farm-sunrise-960w.webp", width: 960 },
    { src: "/agricore-farm-sunrise.webp", width: 1672 },
  ],
};

export const harvestPackingShowcase: ShowcaseImageAsset = {
  src: "/agricore-harvest-packing.webp",
  width: 1536,
  height: 1024,
  mimeType: "image/webp",
  sources: [
    { src: "/agricore-harvest-packing-thumbnail-240w.webp", width: 240 },
    { src: "/agricore-harvest-packing-480w.webp", width: 480 },
    { src: "/agricore-harvest-packing-960w.webp", width: 960 },
    { src: "/agricore-harvest-packing.webp", width: 1536 },
  ],
};

export const traceabilityProduceShowcase: ShowcaseImageAsset = {
  src: "/agricore-traceability-produce.webp",
  width: 1122,
  height: 1402,
  mimeType: "image/webp",
  sources: [
    { src: "/agricore-traceability-produce-thumbnail-240w.webp", width: 240 },
    { src: "/agricore-traceability-produce-480w.webp", width: 480 },
    { src: "/agricore-traceability-produce-960w.webp", width: 960 },
    { src: "/agricore-traceability-produce.webp", width: 1122 },
  ],
};

export const farmStoryShowcase: ShowcaseImageAsset = {
  src: "/agricore-farm-story.gif",
  width: 960,
  height: 540,
  mimeType: "image/gif",
  sources: [{ src: "/agricore-farm-story.gif", width: 960 }],
};
