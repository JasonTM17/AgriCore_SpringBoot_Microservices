import { useMutation } from "@tanstack/react-query";
import { type FormEvent, useState } from "react";

import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { ApiGapNotice, OpsPage } from "../../components/ops/resource-state";
import { createDomainApi } from "../../lib/api/domain-api";
import { ApiClientError } from "../../lib/api/errors";
import { LIVE_API_CAPABILITIES } from "../../lib/api/domain-types";
import { useSession } from "../../lib/auth/session";

export function IotPage() {
  const { api } = useSession();
  const domain = createDomainApi(api);
  const [device, setDevice] = useState({ deviceCode: "", plotId: "", name: "" });
  const [reading, setReading] = useState({
    deviceCode: "",
    metric: "soil_moisture",
    value: "",
    unit: "%",
  });
  const [sessionLog, setSessionLog] = useState<string[]>([]);

  const registerMutation = useMutation({
    mutationFn: () => domain.registerIotDevice(device),
    onSuccess: (res) => {
      setSessionLog((prev) => [
        `Registered ${res.deviceCode} id=${res.id} status=${res.status}`,
        ...prev,
      ]);
    },
  });

  const ingestMutation = useMutation({
    mutationFn: () =>
      domain.ingestIotReading({
        deviceCode: reading.deviceCode,
        metric: reading.metric,
        value: Number(reading.value),
        unit: reading.unit,
      }),
    onSuccess: (res) => {
      setSessionLog((prev) => [
        `Ingest alertRaised=${String(res.alertRaised)} alertId=${res.alertId ?? "none"} msg=${res.message ?? ""}`,
        ...prev,
      ]);
    },
  });

  function onRegister(e: FormEvent) {
    e.preventDefault();
    registerMutation.mutate();
  }

  function onIngest(e: FormEvent) {
    e.preventDefault();
    ingestMutation.mutate();
  }

  return (
    <OpsPage
      title="Chẩn đoán IoT"
      description="Register device + ingest reading theo response tức thời. Không có history device list."
    >
      {!LIVE_API_CAPABILITIES.iotDeviceList ? (
        <ApiGapNotice
          capability="iotDeviceList"
          detail="Không có list devices/readings — session log chỉ lưu cục bộ trên trình duyệt."
        />
      ) : null}

      <form onSubmit={onRegister} className="grid gap-3 rounded-card border border-border bg-surface p-5 md:grid-cols-3">
        <h2 className="md:col-span-3 text-lg font-semibold">Đăng ký thiết bị</h2>
        <Input
          label="Device code"
          value={device.deviceCode}
          onChange={(e) => setDevice((p) => ({ ...p, deviceCode: e.target.value }))}
          required
        />
        <Input
          label="Plot ID"
          value={device.plotId}
          onChange={(e) => setDevice((p) => ({ ...p, plotId: e.target.value }))}
          required
        />
        <Input
          label="Name"
          value={device.name}
          onChange={(e) => setDevice((p) => ({ ...p, name: e.target.value }))}
          required
        />
        {registerMutation.isError ? (
          <p className="md:col-span-3 text-sm text-danger" role="alert">
            {registerMutation.error instanceof ApiClientError
              ? registerMutation.error.message
              : "Register failed"}
          </p>
        ) : null}
        <Button type="submit" disabled={registerMutation.isPending}>
          Register
        </Button>
      </form>

      <form onSubmit={onIngest} className="grid gap-3 rounded-card border border-border bg-surface p-5 md:grid-cols-4">
        <h2 className="md:col-span-4 text-lg font-semibold">Nạp reading</h2>
        <Input
          label="Device code"
          value={reading.deviceCode}
          onChange={(e) => setReading((p) => ({ ...p, deviceCode: e.target.value }))}
          required
        />
        <Input
          label="Metric"
          value={reading.metric}
          onChange={(e) => setReading((p) => ({ ...p, metric: e.target.value }))}
          required
        />
        <Input
          label="Value"
          value={reading.value}
          onChange={(e) => setReading((p) => ({ ...p, value: e.target.value }))}
          required
        />
        <Input
          label="Unit"
          value={reading.unit}
          onChange={(e) => setReading((p) => ({ ...p, unit: e.target.value }))}
          required
        />
        {ingestMutation.isError ? (
          <p className="md:col-span-4 text-sm text-danger" role="alert">
            {ingestMutation.error instanceof ApiClientError
              ? ingestMutation.error.message
              : "Ingest failed"}
          </p>
        ) : null}
        <Button type="submit" disabled={ingestMutation.isPending}>
          Ingest
        </Button>
      </form>

      <section>
        <h2 className="mb-2 text-lg font-semibold">Session log (client-local)</h2>
        <ul className="space-y-2 rounded-card border border-border bg-surface p-4 text-sm font-mono">
          {sessionLog.length === 0 ? <li className="text-muted">Chưa có sự kiện.</li> : null}
          {sessionLog.map((line) => (
            <li key={line}>{line}</li>
          ))}
        </ul>
      </section>
    </OpsPage>
  );
}
