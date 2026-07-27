export const harvestQueryKeys = {
  all: ["harvests"] as const,
  subject: (subject: string) => ["harvests", subject] as const,
  detail: (subject: string, harvestId: string) =>
    ["harvests", subject, "detail", harvestId] as const,
  producer: (subject: string, harvestId: string) =>
    [...harvestQueryKeys.detail(subject, harvestId), "producer"] as const,
  event: (subject: string, eventId: string) =>
    ["harvests", subject, "event", eventId] as const,
  inventory: (subject: string, eventId: string) =>
    [...harvestQueryKeys.event(subject, eventId), "inventory"] as const,
  traceability: (subject: string, eventId: string) =>
    [...harvestQueryKeys.event(subject, eventId), "traceability"] as const,
};
