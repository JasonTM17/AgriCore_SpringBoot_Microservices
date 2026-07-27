import { useMutation } from "@tanstack/react-query";
import { useState } from "react";

import { Button } from "../../components/ui/button";
import { EmptyState } from "../../components/ui/empty-state";
import { Input } from "../../components/ui/input";
import { useSession } from "../../lib/auth/session";
import {
  ingestIotReading,
  registerIotDevice,
  type DeviceResponse,
  type IngestResultResponse,
} from "./iot-api";

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const DEVICE_PATTERN = /^[A-Za-z0-9._-]{1,64}$/;

interface SessionLog {
  id: number;
  at: string;
  action: string;
  result: string;
  detail: string;
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}

function ResultCard({ result }: { result: IngestResultResponse }) {
  const suppressed = !result.alertRaised && result.alertId && result.alertStatus === "OPEN";
  return (
    <section className={`rounded-card border p-5 ${result.alertRaised ? "border-danger/40 bg-red-50" : suppressed ? "border-harvest-600/40 bg-harvest-100" : "border-success/40 bg-green-50"}`} aria-live="polite">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted">Kết quả gửi số đo</p>
      <h3 className="mt-1 text-lg font-semibold text-ink">{result.alertRaised ? "Đã mở cảnh báo" : suppressed ? "Không tạo cảnh báo mới trong thời gian chờ" : "Đã tiếp nhận, chưa có cảnh báo"}</h3>
      <p className="mt-2 text-sm text-ink">{result.message}</p>
      <dl className="mt-4 grid gap-2 text-xs sm:grid-cols-3">
        <div><dt className="text-muted">readingId</dt><dd className="mt-1 break-all font-mono">{result.readingId}</dd></div>
        <div><dt className="text-muted">alertId</dt><dd className="mt-1 break-all font-mono">{result.alertId ?? "—"}</dd></div>
        <div><dt className="text-muted">alertStatus</dt><dd className="mt-1 font-mono">{result.alertStatus ?? "—"}</dd></div>
      </dl>
    </section>
  );
}

export function IotPage() {
  const { api, user } = useSession();
  const canRegister = user?.permissions.includes("IOT_WRITE") ?? false;
  const canIngest = user?.permissions.some((permission) => permission === "IOT_WRITE" || permission === "IOT_USE") ?? false;
  const [deviceCode, setDeviceCode] = useState("");
  const [plotId, setPlotId] = useState("");
  const [deviceName, setDeviceName] = useState("");
  const [readingDeviceCode, setReadingDeviceCode] = useState("");
  const [metricType, setMetricType] = useState("SOIL_MOISTURE");
  const [metricValue, setMetricValue] = useState("");
  const [unit, setUnit] = useState("%");
  const [recordedAt, setRecordedAt] = useState("");
  const [registered, setRegistered] = useState<DeviceResponse | null>(null);
  const [result, setResult] = useState<IngestResultResponse | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [logs, setLogs] = useState<SessionLog[]>([]);

  const registerMutation = useMutation({
    mutationFn: () => registerIotDevice(api, { deviceCode: deviceCode.trim(), plotId: plotId.trim(), name: deviceName.trim() }),
    onSuccess: (device) => {
      setRegistered(device);
      setReadingDeviceCode(device.deviceCode);
      setFormError(null);
      setLogs((current) => [{ id: Date.now(), at: new Date().toLocaleTimeString("vi-VN"), action: "Đăng ký thiết bị", result: "201 Thành công", detail: JSON.stringify(device) }, ...current].slice(0, 10));
    },
    onError: (error) => setFormError(errorMessage(error, "Không thể đăng ký thiết bị.")),
  });
  const readingMutation = useMutation({
    mutationFn: () => ingestIotReading(api, {
      deviceCode: readingDeviceCode.trim(),
      metricType: metricType.trim(),
      metricValue: Number(metricValue),
      unit: unit.trim(),
      recordedAt: recordedAt ? new Date(recordedAt).toISOString() : null,
    }),
    onSuccess: (reading) => {
      setResult(reading);
      setFormError(null);
      setLogs((current) => [{ id: Date.now(), at: new Date().toLocaleTimeString("vi-VN"), action: "Gửi số đo", result: reading.alertRaised ? "200 Cảnh báo mở" : "200 Đã tiếp nhận", detail: JSON.stringify(reading) }, ...current].slice(0, 10));
    },
    onError: (error) => setFormError(errorMessage(error, "Không thể gửi số đo.")),
  });

  function submitRegistration(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!canRegister) return;
    if (!DEVICE_PATTERN.test(deviceCode.trim()) || !UUID_PATTERN.test(plotId.trim()) || !deviceName.trim()) {
      setFormError("Mã thiết bị chỉ gồm chữ/số . _ -, plot ID phải là UUID và tên không được trống.");
      return;
    }
    setFormError(null);
    registerMutation.mutate();
  }

  function submitReading(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = Number(metricValue);
    if (!canIngest) return;
    if (!DEVICE_PATTERN.test(readingDeviceCode.trim()) || !metricType.trim() || !Number.isFinite(value) || !unit.trim()) {
      setFormError("Nhập mã thiết bị, loại chỉ số, giá trị số và đơn vị hợp lệ.");
      return;
    }
    setFormError(null);
    readingMutation.mutate();
  }

  return (
    <div className="animate-fade-in-up space-y-6">
      <header>
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-forest-700">Theo dõi</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-ink">Chẩn đoán & gửi dữ liệu IoT</h1>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-muted">Thực hiện hai request có kiểm soát: đăng ký thiết bị theo plot và gửi một reading. Không hiển thị lịch sử thiết bị/alert vì backend chưa có GET list.</p>
      </header>

      <div className="rounded-card border border-info/30 bg-sky-50 p-4 text-sm text-ink" role="note">
        Phiên chẩn đoán chỉ lưu cục bộ trên trình duyệt này. Cảnh báo cooldown có thể trả về <span className="font-mono">alertRaised=false</span> nhưng vẫn giữ <span className="font-mono">alertStatus=OPEN</span>.
      </div>

      <div className="grid gap-6 xl:grid-cols-3">
        <section className="rounded-card border border-border bg-surface p-5 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted">01 · Thiết bị</p>
          <h2 className="mt-1 text-lg font-semibold text-ink">Đăng ký thiết bị</h2>
          {!canRegister ? <p className="mt-3 rounded-control bg-harvest-100 px-3 py-2 text-sm text-ink">Cần SYSTEM_ADMIN, FARM_MANAGER hoặc AGRONOMIST.</p> : null}
          <form className="mt-4 grid gap-4" onSubmit={submitRegistration}>
            <Input label="Mã thiết bị" value={deviceCode} onChange={(event) => setDeviceCode(event.target.value)} disabled={!canRegister} />
            <Input label="Tên thiết bị" value={deviceName} onChange={(event) => setDeviceName(event.target.value)} disabled={!canRegister} />
            <Input label="Plot ID (UUID)" value={plotId} onChange={(event) => setPlotId(event.target.value)} disabled={!canRegister} />
            <Button type="submit" disabled={!canRegister || registerMutation.isPending}>{registerMutation.isPending ? "Đang đăng ký…" : "Đăng ký thiết bị"}</Button>
          </form>
          {registered ? <dl className="mt-4 rounded-control bg-forest-50 p-3 text-xs"><div><dt className="text-muted">deviceId</dt><dd className="mt-1 break-all font-mono">{registered.id}</dd></div><div className="mt-2"><dt className="text-muted">status</dt><dd className="mt-1 font-mono text-forest-700">{registered.status}</dd></div></dl> : null}
        </section>

        <section className="rounded-card border border-border bg-surface p-5 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted">02 · Reading</p>
          <h2 className="mt-1 text-lg font-semibold text-ink">Gửi số đo</h2>
          {!canIngest ? <p className="mt-3 rounded-control bg-harvest-100 px-3 py-2 text-sm text-ink">Cần quyền IoT ingest theo vai trò và farm access.</p> : null}
          <form className="mt-4 grid gap-4" onSubmit={submitReading}>
            <Input label="Mã thiết bị" value={readingDeviceCode} onChange={(event) => setReadingDeviceCode(event.target.value)} disabled={!canIngest} />
            <Input label="Loại chỉ số" value={metricType} onChange={(event) => setMetricType(event.target.value)} disabled={!canIngest} />
            <div className="grid grid-cols-2 gap-3"><Input label="Giá trị" type="number" step="0.0001" value={metricValue} onChange={(event) => setMetricValue(event.target.value)} disabled={!canIngest} /><Input label="Đơn vị" value={unit} onChange={(event) => setUnit(event.target.value)} disabled={!canIngest} /></div>
            <Input label="Thời gian ghi nhận (tuỳ chọn)" type="datetime-local" value={recordedAt} onChange={(event) => setRecordedAt(event.target.value)} disabled={!canIngest} />
            <Button type="submit" disabled={!canIngest || readingMutation.isPending}>{readingMutation.isPending ? "Đang gửi…" : "Gửi số đo"}</Button>
          </form>
        </section>

        <section className="space-y-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-muted">03 · Phản hồi</p>
          {result ? <ResultCard result={result} /> : <EmptyState title="Chưa có phản hồi" description="Kết quả alert chỉ xuất hiện sau khi request gửi số đo hoàn tất." />}
        </section>
      </div>

      {logs.length > 0 ? <section className="rounded-card border border-border bg-surface shadow-sm"><div className="border-b border-border p-4"><h2 className="text-lg font-semibold text-ink">Nhật ký phiên chẩn đoán</h2><p className="mt-1 text-xs text-muted">Client-local, không phải lịch sử server.</p></div><div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead className="bg-canvas text-xs uppercase tracking-wide text-muted"><tr><th className="px-4 py-3">Thời gian</th><th className="px-4 py-3">Hành động</th><th className="px-4 py-3">Kết quả</th><th className="px-4 py-3">Chi tiết</th></tr></thead><tbody>{logs.map((log) => <tr key={log.id} className="border-t border-border"><td className="whitespace-nowrap px-4 py-3 font-mono text-xs">{log.at}</td><td className="px-4 py-3 font-medium">{log.action}</td><td className="px-4 py-3 font-mono text-xs">{log.result}</td><td className="max-w-md truncate px-4 py-3 font-mono text-xs text-muted">{log.detail}</td></tr>)}</tbody></table></div></section> : null}
      {formError ? <p className="rounded-control border border-danger/40 bg-red-50 px-4 py-3 text-sm text-danger" role="alert">{formError}</p> : null}
    </div>
  );
}
