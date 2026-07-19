import type { CropListParams } from "./crop-api";

export const cropQueryKeys = {
  all: ["crops"] as const,
  list: (subject: string, params: CropListParams) => ["crops", subject, "list", params] as const,
};
