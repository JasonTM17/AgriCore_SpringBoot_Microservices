const dateFormatter = new Intl.DateTimeFormat("vi-VN", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  timeZone: "UTC",
});
const weightFormatter = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 3 });
const ISO_LOCAL_DATE = /^\d{4}-\d{2}-\d{2}$/;
const NOT_PUBLISHED = "Chưa công bố";

export function publicText(value: string | null): string {
  return value?.trim() || NOT_PUBLISHED;
}

export function formatPublicDate(value: string | null): string {
  if (!value || !ISO_LOCAL_DATE.test(value)) return NOT_PUBLISHED;
  const parsed = new Date(`${value}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value) {
    return NOT_PUBLISHED;
  }
  return dateFormatter.format(parsed);
}

export function formatPublicWeight(value: number | null): string {
  return value === null || !Number.isFinite(value) || value <= 0
    ? NOT_PUBLISHED
    : `${weightFormatter.format(value)} kg`;
}
