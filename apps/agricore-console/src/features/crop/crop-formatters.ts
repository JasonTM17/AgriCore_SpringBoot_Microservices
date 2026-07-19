const numberFormatter = new Intl.NumberFormat("vi-VN", { maximumFractionDigits: 2 });

function formatNumber(value: number | null, suffix = ""): string {
  return value === null ? "Chưa cập nhật" : `${numberFormatter.format(value)}${suffix}`;
}

function formatRange(
  minimum: number | null,
  maximum: number | null,
  suffix = "",
): string {
  if (minimum === null && maximum === null) {
    return "Chưa cập nhật";
  }
  if (minimum === null) {
    return `≤ ${formatNumber(maximum, suffix)}`;
  }
  if (maximum === null) {
    return `≥ ${formatNumber(minimum, suffix)}`;
  }
  return `${formatNumber(minimum)} – ${formatNumber(maximum)}${suffix}`;
}

export function formatGrowthDays(minimum: number | null, maximum: number | null): string {
  return formatRange(minimum, maximum, " ngày");
}

export function formatTemperature(minimum: number | null, maximum: number | null): string {
  return formatRange(minimum, maximum, " °C");
}

export function formatHumidity(minimum: number | null, maximum: number | null): string {
  return formatRange(minimum, maximum, " %");
}

export function formatPh(minimum: number | null, maximum: number | null): string {
  return formatRange(minimum, maximum);
}

export function formatYield(value: number | null, unit: string | null): string {
  if (value === null) {
    return "Chưa cập nhật";
  }
  return `${numberFormatter.format(value)} ${unit ?? "đơn vị/ha"}`;
}
