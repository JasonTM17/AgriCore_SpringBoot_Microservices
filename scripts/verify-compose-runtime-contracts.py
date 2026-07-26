"""Verify runtime wiring that Docker Compose syntax validation cannot prove."""

from __future__ import annotations

import json
import subprocess
import sys


KAFKA_SERVICES = (
    "farm-service",
    "crop-cycle-service",
    "work-service",
    "inventory-service",
    "harvest-service",
    "notification-service",
    "iot-service",
    "sales-service",
    "traceability-service",
)
EXPECTED_BOOTSTRAP_SERVERS = "kafka:19092"
EXPECTED_SERVICE_ENVIRONMENT = {
    "assistant-service": {
        "FARM_SERVICE_URL": "http://farm-service:8082",
    },
}


def load_compose_config() -> dict[str, object]:
    result = subprocess.run(
        ["docker", "compose", "-f", "docker-compose.yml", "config", "--format", "json"],
        check=True,
        capture_output=True,
        text=True,
    )
    return json.loads(result.stdout)


def main() -> int:
    config = load_compose_config()
    services = config.get("services", {})
    failures: list[str] = []

    for service_name in KAFKA_SERVICES:
        service = services.get(service_name)
        if not isinstance(service, dict):
            failures.append(f"{service_name}: service is missing")
            continue

        environment = service.get("environment", {})
        bootstrap_servers = (
            environment.get("KAFKA_BOOTSTRAP_SERVERS")
            if isinstance(environment, dict)
            else None
        )
        if bootstrap_servers != EXPECTED_BOOTSTRAP_SERVERS:
            failures.append(
                f"{service_name}: KAFKA_BOOTSTRAP_SERVERS={bootstrap_servers!r}"
            )

        dependencies = service.get("depends_on", {})
        kafka_dependency = (
            dependencies.get("kafka") if isinstance(dependencies, dict) else None
        )
        condition = (
            kafka_dependency.get("condition")
            if isinstance(kafka_dependency, dict)
            else None
        )
        if condition != "service_healthy":
            failures.append(f"{service_name}: kafka dependency is not service_healthy")

    for service_name, expected_environment in EXPECTED_SERVICE_ENVIRONMENT.items():
        service = services.get(service_name)
        if not isinstance(service, dict):
            failures.append(f"{service_name}: service is missing")
            continue
        environment = service.get("environment", {})
        if not isinstance(environment, dict):
            failures.append(f"{service_name}: environment is not a mapping")
            continue
        for name, expected_value in expected_environment.items():
            actual_value = environment.get(name)
            if actual_value != expected_value:
                failures.append(
                    f"{service_name}: {name}={actual_value!r}, "
                    f"expected {expected_value!r}"
                )

    if failures:
        print("Compose runtime contract verification failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print(
        "Compose runtime contracts verified: "
        f"{len(KAFKA_SERVICES)} Kafka-connected services."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
