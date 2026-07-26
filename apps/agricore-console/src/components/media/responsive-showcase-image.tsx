import { useState, type ImgHTMLAttributes } from "react";

export interface ResponsiveImageSource {
  src: string;
  width: number;
}

export interface ShowcaseImageAsset {
  src: string;
  width: number;
  height: number;
  mimeType?: string;
  sources: readonly ResponsiveImageSource[];
}

interface ResponsiveShowcaseImageProps {
  asset: ShowcaseImageAsset;
  alt: string;
  sizes: string;
  aspectRatioClassName?: string;
  className?: string;
  imageClassName?: string;
  loading?: ImgHTMLAttributes<HTMLImageElement>["loading"];
  fetchPriority?: ImgHTMLAttributes<HTMLImageElement>["fetchPriority"];
}

export function ResponsiveShowcaseImage({
  asset,
  alt,
  sizes,
  aspectRatioClassName = "aspect-video",
  className = "",
  imageClassName = "",
  loading = "lazy",
  fetchPriority,
}: ResponsiveShowcaseImageProps) {
  const [failedSource, setFailedSource] = useState<string | null>(null);
  const frameClassName = `relative overflow-hidden bg-forest-50 ${aspectRatioClassName} ${className}`;

  if (failedSource === asset.src) {
    return (
      <div
        className={`grid place-items-center border border-forest-100 ${frameClassName}`}
        role="img"
        aria-label={`${alt}. Không thể tải hình ảnh.`}
      >
        <svg
          viewBox="0 0 48 48"
          className="size-12 text-forest-700"
          aria-hidden="true"
          fill="none"
          stroke="currentColor"
          strokeLinecap="round"
          strokeLinejoin="round"
          strokeWidth="2"
        >
          <path d="M8 35 19 23l7 7 5-6 9 11" />
          <path d="M8 10h32v28H8z" />
          <circle cx="31" cy="17" r="3" />
        </svg>
      </div>
    );
  }

  const srcSet = asset.sources.map((source) => `${source.src} ${source.width}w`).join(", ");

  return (
    <div className={frameClassName}>
      <picture className="block h-full w-full">
        <source
          {...(asset.mimeType ? { type: asset.mimeType } : {})}
          srcSet={srcSet}
          sizes={sizes}
        />
        <img
          src={asset.src}
          srcSet={srcSet}
          sizes={sizes}
          alt={alt}
          width={asset.width}
          height={asset.height}
          className={`block h-full w-full object-cover ${imageClassName}`}
          loading={loading}
          decoding="async"
          fetchPriority={fetchPriority}
          onError={() => setFailedSource(asset.src)}
        />
      </picture>
    </div>
  );
}
