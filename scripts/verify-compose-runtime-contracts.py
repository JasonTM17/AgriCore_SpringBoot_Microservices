"""Verify runtime wiring that Docker Compose syntax validation cannot prove."""

from __future__ import annotations

import json
import ipaddress
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
CLIENT_IP_EDGE_NETWORK = "client-ip-edge"
CLIENT_IP_EDGE_SERVICES = {"api-gateway", "agricore-console"}
CLIENT_IP_SIGNING_SERVICES = ("api-gateway", "identity-service", "assistant-service")
LEGACY_CLIENT_IP_ENVIRONMENT = (
    "AGRICORE_TRUST_FORWARDED_HEADERS",
    "ASSISTANT_BUDGET_TRUST_FORWARDED_HEADERS",
)
EXPECTED_SERVICE_ENVIRONMENT = {
    "assistant-service": {
        "FARM_SERVICE_URL": "http://farm-service:8082",
    },
    "harvest-service": {
        "FARM_SERVICE_URL": "http://farm-service:8082",
        "CROP_CYCLE_SERVICE_URL": "http://crop-cycle-service:8084",
        "CROP_CYCLE_SERVICE_ALLOWED_HOSTS": "crop-cycle-service",
        "CROP_CYCLE_SERVICE_ALLOW_INSECURE_HTTP": "true",
    },
    "sales-service": {
        "FARM_SERVICE_URL": "http://farm-service:8082",
        "INVENTORY_SERVICE_URL": "http://inventory-service:8086",
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
    networks = config.get("networks", {})
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

    gateway = services.get("api-gateway")
    console = services.get("agricore-console")
    edge_network = networks.get(CLIENT_IP_EDGE_NETWORK) if isinstance(networks, dict) else None
    gateway_ip: str | None = None
    console_ip: str | None = None
    edge_service_names = {
        name for name, service in services.items()
        if isinstance(service, dict)
        and isinstance(service.get("networks"), dict)
        and CLIENT_IP_EDGE_NETWORK in service["networks"]
    }
    if edge_service_names != CLIENT_IP_EDGE_SERVICES:
        failures.append(
            f"client-ip-edge: attached services={sorted(edge_service_names)!r}, "
            f"expected {sorted(CLIENT_IP_EDGE_SERVICES)!r}"
        )

    if not isinstance(gateway, dict):
        failures.append("api-gateway: service is missing")
    else:
        gateway_networks = gateway.get("networks", {})
        if not isinstance(gateway_networks, dict) or set(gateway_networks) != {"default", CLIENT_IP_EDGE_NETWORK}:
            failures.append("api-gateway: must attach only to default and client-ip-edge networks")
        gateway_edge_attachment = (
            gateway_networks.get(CLIENT_IP_EDGE_NETWORK)
            if isinstance(gateway_networks, dict)
            else None
        )
        gateway_ip = (
            gateway_edge_attachment.get("ipv4_address")
            if isinstance(gateway_edge_attachment, dict)
            else None
        )
        if not isinstance(gateway_ip, str):
            failures.append("api-gateway: fixed edge IPv4 address is missing")

    if not isinstance(console, dict):
        failures.append("agricore-console: service is missing")
    else:
        console_networks = console.get("networks", {})
        if not isinstance(console_networks, dict) or set(console_networks) != {CLIENT_IP_EDGE_NETWORK}:
            failures.append("agricore-console: must attach only to the client-ip-edge network")
        edge_attachment = console_networks.get(CLIENT_IP_EDGE_NETWORK) if isinstance(console_networks, dict) else None
        console_ip = edge_attachment.get("ipv4_address") if isinstance(edge_attachment, dict) else None
        if not isinstance(console_ip, str):
            failures.append("agricore-console: edge IPv4 address is missing")

    if not isinstance(edge_network, dict):
        failures.append("client-ip-edge: network is missing")
    else:
        ipam = edge_network.get("ipam", {})
        ipam_config = ipam.get("config") if isinstance(ipam, dict) else None
        subnet = ipam_config[0].get("subnet") if isinstance(ipam_config, list) and ipam_config else None
        if edge_network.get("driver") != "bridge":
            failures.append("client-ip-edge: must use the bridge driver")
        try:
            edge_subnet = ipaddress.ip_network(subnet)
            gateway_address = ipaddress.ip_address(gateway_ip)
            console_address = ipaddress.ip_address(console_ip)
            if edge_subnet.version != 4 or edge_subnet.num_addresses > 1024:
                failures.append("client-ip-edge: subnet must be IPv4 with at most 1024 addresses")
            elif gateway_address not in edge_subnet or console_address not in edge_subnet:
                failures.append("client-ip-edge: fixed Gateway and Console addresses must be inside the edge subnet")
            elif gateway_address == console_address:
                failures.append("client-ip-edge: Gateway and Console must use distinct fixed addresses")
            elif isinstance(gateway, dict):
                gateway_environment = gateway.get("environment", {})
                trusted_proxy_pattern = (
                    gateway_environment.get("GATEWAY_TRUSTED_PROXY_ADDRESS_PATTERN")
                    if isinstance(gateway_environment, dict)
                    else None
                )
                if not isinstance(trusted_proxy_pattern, str) or not trusted_proxy_pattern.strip():
                    failures.append("api-gateway: trusted proxy pattern is missing")
                else:
                    expected_pattern = str(console_address).replace(".", "[.]")
                    if trusted_proxy_pattern != expected_pattern:
                        failures.append(
                            "api-gateway: trusted proxy pattern must equal the exact Console edge address pattern"
                        )
        except (TypeError, ValueError):
            failures.append("client-ip-edge: subnet, Console IPv4 address, or trusted proxy pattern is invalid")

    for service_name in CLIENT_IP_SIGNING_SERVICES:
        service = services.get(service_name)
        environment = service.get("environment", {}) if isinstance(service, dict) else {}
        secret = environment.get("AGRICORE_CLIENT_IP_SIGNING_SECRET") if isinstance(environment, dict) else None
        if not isinstance(secret, str) or not secret.strip():
            failures.append(f"{service_name}: client-IP signing secret is missing")

    for service_name, service in services.items():
        environment = service.get("environment", {}) if isinstance(service, dict) else {}
        if isinstance(environment, dict):
            for legacy_name in LEGACY_CLIENT_IP_ENVIRONMENT:
                if legacy_name in environment:
                    failures.append(f"{service_name}: legacy {legacy_name} must be absent")

    if failures:
        print("Compose runtime contract verification failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1

    print(
        "Compose runtime contracts verified: "
        f"{len(KAFKA_SERVICES)} Kafka-connected services and isolated client-IP edge."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
