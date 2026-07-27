#!/usr/bin/env python3
"""Fail the build when the OpenAPI contracts stop describing the code.

`contracts/openapi/` is the single source of truth for the API, as recorded in
`docs/code-standards.md`. Nothing enforced that, and by 2026-07-26 the contracts had
drifted in both directions: four endpoints declared that no controller implemented, four
implemented that no contract declared, and no service documenting the error envelope
every service returns.

Checks, in order of how badly each one bites:

1. Declared paths equal implemented paths, per service.
2. Every contract defines `ApiError`, and the copies are identical.
3. Every declared 4xx/5xx response references a response component, so it carries a schema.
4. Every internal `$ref` resolves inside its own document.
5. No status is declared twice in one `responses:` block. Duplicate YAML keys parse fine —
   the last wins — so this is the check that catches a botched edit.
6. Every service directory has a contract, and every contract has a service.
7. Every controller method guarded by `@PreAuthorize` declares a 403 response.

Deliberately stdlib-only: no PyYAML, so CI needs no install step and the check cannot
fail for a reason unrelated to the contracts.

Known limitation: path-variable names are normalised, so a contract saying `/crops/{id}`
while the code says `/crops/{cropId}` passes. Catching that would mean rejecting harmless
naming differences; the useful check is which endpoints exist.

Usage: python tools/check-openapi-contract-drift.py [--verbose]
"""
from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
CONTRACTS = ROOT / "contracts" / "openapi"
SERVICES = ROOT / "services"

VERBS = ("get", "post", "put", "patch", "delete")

# api-gateway proxies rather than implements, so its declared prefixes intentionally have no
# controller behind them in this repository. Everything else is compared.
GATEWAY_CONTRACT = "api-gateway.v1"

MAPPING_ANNOTATION = re.compile(
    r"@(Get|Post|Put|Patch|Delete|Request)Mapping\b(?:\s*\((.*?)\))?",
    re.DOTALL,
)
METHOD_DECLARATION = re.compile(r"\bpublic\s+[\w<>, ?\[\].@]+\s+\w+\s*\(", re.DOTALL)
PRE_AUTHORIZE_ANNOTATION = re.compile(r"@PreAuthorize\b")


class Drift(Exception):
    """Raised for input the checker cannot interpret, as opposed to a contract mismatch."""


def contract_for(service_dir: pathlib.Path) -> pathlib.Path:
    return CONTRACTS / f"{service_dir.name}.v1.yaml"


def declared_paths(contract: pathlib.Path) -> set[tuple[str, str]]:
    """(verb, path) pairs under `paths:`."""
    found: set[tuple[str, str]] = set()
    in_paths = False
    current: str | None = None
    for line in contract.read_text(encoding="utf-8").splitlines():
        if line.startswith("paths:"):
            in_paths = True
            continue
        if line and not line[0].isspace():
            in_paths = False
        if not in_paths:
            continue
        m = re.match(r"^  (/\S*):\s*$", line)
        if m:
            current = m.group(1)
            continue
        m = re.match(r"^    (\w+):\s*$", line)
        if m and current and m.group(1) in VERBS:
            found.add((m.group(1).upper(), current))
    return found


def implemented_paths(service_dir: pathlib.Path) -> set[tuple[str, str]]:
    """(verb, path) pairs from controller annotations.

    Raises on any annotation form this parser does not handle. A silent skip is how a
    gate becomes decorative — better a clear failure telling a contributor to extend it.
    """
    return {(verb, path) for verb, path, _, _ in implemented_operations(service_dir)}


def implemented_operations(
        service_dir: pathlib.Path,
) -> list[tuple[str, str, pathlib.Path, bool]]:
    """Controller operations with their source and method-level permission guard."""
    found: list[tuple[str, str, pathlib.Path, bool]] = []
    src = service_dir / "src" / "main" / "java"
    if not src.is_dir():
        raise Drift(f"{service_dir.name}: no src/main/java")

    for java in sorted(src.rglob("*Controller.java")):
        text = java.read_text(encoding="utf-8")
        _reject_unparseable(java, text)

        prefix = ""
        request_mapping = next(
            (hit for hit in MAPPING_ANNOTATION.finditer(text) if hit.group(1) == "Request"),
            None,
        )
        if request_mapping:
            prefix = _mapping_path(java, request_mapping) or ""

        for verb in VERBS:
            annotation = verb.capitalize()
            for hit in (
                candidate for candidate in MAPPING_ANNOTATION.finditer(text)
                if candidate.group(1) == annotation
            ):
                suffix = _mapping_path(java, hit) or ""
                found.append((
                    verb.upper(),
                    (prefix + suffix) or "/",
                    java,
                    _method_has_pre_authorize(java, text, hit),
                ))
    return found


def _method_has_pre_authorize(java: pathlib.Path, text: str, mapping: re.Match[str]) -> bool:
    """Whether the controller method following ``mapping`` has ``@PreAuthorize``."""
    method = METHOD_DECLARATION.search(text, mapping.end())
    if method is None:
        raise Drift(
            f"{java.relative_to(ROOT)}: mapping annotation is not followed by a public method; "
            "the contract gate cannot determine its authorization requirements"
        )

    class_body = re.search(r"\bclass\s+\w+\b[^{}]*\{", text)
    if class_body is None:
        raise Drift(f"{java.relative_to(ROOT)}: could not locate controller class body")

    previous_member_end = class_body.end()
    for close in re.finditer(r"(?m)^\s*}", text[class_body.end():method.start()]):
        previous_member_end = class_body.end() + close.end()
    annotations = text[previous_member_end:method.start()]
    return PRE_AUTHORIZE_ANNOTATION.search(annotations) is not None


def _reject_unparseable(java: pathlib.Path, text: str) -> None:
    """Every mapping annotation must expose one literal path, positionally or by name."""
    for hit in MAPPING_ANNOTATION.finditer(text):
        _mapping_path(java, hit)


def _mapping_path(java: pathlib.Path, hit: re.Match[str]) -> str | None:
    """Literal path from a Spring mapping annotation.

    Supports the forms used in this repository: bare mappings, a positional
    string, or named ``value``/``path`` followed by attributes such as
    ``produces`` and ``consumes``.
    """
    arguments = hit.group(2)
    if arguments is None or not arguments.strip():
        return None
    positional = re.match(r'\s*"([^"]*)"', arguments)
    if positional:
        return positional.group(1)
    named = re.search(r'\b(?:value|path)\s*=\s*"([^"]*)"', arguments)
    if named:
        return named.group(1)
    snippet = " ".join(arguments.split())[:80]
    raise Drift(
        f"{java.relative_to(ROOT)}: unsupported mapping annotation form "
        f"'@{hit.group(1)}Mapping({snippet})'. The contract gate requires one literal path."
    )


def normalise(pairs: set[tuple[str, str]]) -> set[tuple[str, str]]:
    return {(verb, re.sub(r"\{[^}]+\}", "{}", path)) for verb, path in pairs}


def declared_response_statuses(contract: pathlib.Path) -> dict[tuple[str, str], set[str]]:
    """Response statuses declared for each path operation under ``paths:``."""
    found: dict[tuple[str, str], set[str]] = {}
    in_paths = False
    current_path: str | None = None
    current_verb: str | None = None
    response_operation: tuple[str, str] | None = None

    for line in contract.read_text(encoding="utf-8").splitlines():
        if line.startswith("paths:"):
            in_paths = True
            continue
        if line and not line[0].isspace():
            in_paths = False
        if not in_paths:
            continue

        path = re.match(r"^  (/\S*):\s*$", line)
        if path:
            current_path = path.group(1)
            current_verb = None
            response_operation = None
            continue
        verb = re.match(r"^    (\w+):\s*$", line)
        if verb and verb.group(1) in VERBS:
            current_verb = verb.group(1).upper()
            response_operation = None
            continue
        if line == "      responses:" and current_path and current_verb:
            response_operation = (current_verb, current_path)
            found.setdefault(response_operation, set())
            continue
        if response_operation:
            status = re.match(r"^        ['\"]?(\d{3})['\"]?:\s*$", line)
            if status:
                found[response_operation].add(status.group(1))
            elif line.strip() and len(line) - len(line.lstrip()) <= 6:
                response_operation = None
    return found


def normalise_response_statuses(
        response_statuses: dict[tuple[str, str], set[str]],
) -> dict[tuple[str, str], set[str]]:
    return {
        (verb, re.sub(r"\{[^}]+\}", "{}", path)): statuses
        for (verb, path), statuses in response_statuses.items()
    }


def pre_authorize_response_problems(
        operations: list[tuple[str, str, pathlib.Path, bool]],
        contract: pathlib.Path,
) -> list[str]:
    """Permission-gated controller operations must advertise 403 Forbidden."""
    statuses = normalise_response_statuses(declared_response_statuses(contract))
    problems: list[str] = []
    for verb, path, java, pre_authorized in operations:
        if pre_authorized and "403" not in statuses.get(
                (verb, re.sub(r"\{[^}]+\}", "{}", path)), set()
        ):
            problems.append(
                f"{java.relative_to(SERVICES).parts[0]}: {verb} {path} is "
                f"guarded by @PreAuthorize in {java.relative_to(ROOT)} but its contract does "
                "not declare 403 Forbidden"
            )
    return problems


def block(text: str, header: str) -> list[str]:
    """Lines belonging to `header`, by indentation. Empty when the header is absent."""
    lines = text.splitlines()
    try:
        start = next(n for n, l in enumerate(lines) if l.rstrip() == header)
    except StopIteration:
        return []
    depth = len(header) - len(header.lstrip())
    out = []
    for line in lines[start + 1:]:
        if line.strip() and (len(line) - len(line.lstrip())) <= depth:
            break
        out.append(line)
    return out


def api_error_schema(contract: pathlib.Path) -> str | None:
    body = block(contract.read_text(encoding="utf-8"), "    ApiError:")
    return "\n".join(body).rstrip() if body else None


def response_problems(contract: pathlib.Path) -> list[str]:
    """Bare error responses, unresolved refs, and statuses declared twice in one block."""
    text = contract.read_text(encoding="utf-8")
    lines = text.splitlines()
    problems: list[str] = []

    def defined(kind: str) -> set[str]:
        return {
            m.group(1)
            for m in (re.match(r"^    (\w+):\s*$", l) for l in block(text, f"  {kind}:"))
            if m
        }

    # Every component section, not just the two this checker adds — farm already used
    # components/parameters, and looking up only responses and schemas reported those as
    # unresolved. A false positive is worse than no check.
    pools = {kind: defined(kind) for kind in
             ("schemas", "responses", "parameters", "requestBodies", "headers", "examples")}

    for kind, name in sorted(set(re.findall(r"\$ref:\s*'#/components/(\w+)/(\w+)'", text))):
        if kind not in pools:
            problems.append(f"$ref into unknown component section '{kind}'")
        elif name not in pools[kind]:
            problems.append(f"unresolved $ref '#/components/{kind}/{name}'")

    # Walk each responses: block once, checking for bare errors and repeated statuses.
    n = 0
    while n < len(lines):
        if re.match(r"^      responses:\s*$", lines[n]):
            n += 1
            seen: set[str] = set()
            while n < len(lines) and (not lines[n].strip() or lines[n].startswith("        ")):
                m = re.match(r"^        '(\d{3})':(.*)$", lines[n])
                if m:
                    status, rest = m.group(1), m.group(2)
                    if status in seen:
                        problems.append(f"status '{status}' declared twice in one responses block")
                    seen.add(status)
                    if status[0] in "45":
                        entry = rest
                        look = n + 1
                        while look < len(lines) and lines[look].startswith("          "):
                            entry += lines[look]
                            look += 1
                        if "$ref" not in entry:
                            problems.append(
                                f"error response '{status}' carries no $ref, so it has no schema"
                            )
                n += 1
            continue
        n += 1
    return problems


def main(argv: list[str]) -> int:
    verbose = "--verbose" in argv
    failures: list[str] = []

    service_dirs = sorted(d for d in SERVICES.iterdir() if d.is_dir())
    contracts = sorted(CONTRACTS.glob("*.yaml"))

    # 6. Coverage both ways, so a new service cannot arrive without a contract.
    contract_stems = {c.stem.removesuffix(".v1") for c in contracts}
    for service in service_dirs:
        if service.name not in contract_stems:
            failures.append(f"{service.name}: no contract at contracts/openapi/{service.name}.v1.yaml")
    for stem in sorted(contract_stems):
        if not (SERVICES / stem).is_dir():
            failures.append(f"contracts/openapi/{stem}.v1.yaml: no service directory services/{stem}")

    # 1. Path drift, every service except the proxying gateway.
    for service in service_dirs:
        contract = contract_for(service)
        if not contract.exists() or contract.stem == GATEWAY_CONTRACT:
            continue
        try:
            declared = normalise(declared_paths(contract))
            operations = implemented_operations(service)
            implemented = normalise({(verb, path) for verb, path, _, _ in operations})
        except Drift as exc:
            failures.append(str(exc))
            continue
        for verb, path in sorted(declared - implemented):
            failures.append(f"{service.name}: {verb} {path} is declared in the contract but no controller implements it")
        for verb, path in sorted(implemented - declared):
            failures.append(f"{service.name}: {verb} {path} is implemented but the contract does not declare it")
        failures.extend(pre_authorize_response_problems(operations, contract))
        if verbose:
            print(f"  {service.name}: {len(declared)} endpoints, matched")

    # 2-5. Envelope and response hygiene, every contract including the gateway.
    schemas: dict[str, str] = {}
    for contract in contracts:
        schema = api_error_schema(contract)
        if schema is None:
            failures.append(f"{contract.name}: defines no ApiError schema")
        else:
            schemas[contract.name] = schema
        for problem in response_problems(contract):
            failures.append(f"{contract.name}: {problem}")

    distinct = set(schemas.values())
    if len(distinct) > 1:
        failures.append(
            f"ApiError schema differs between contracts ({len(distinct)} variants across "
            f"{len(schemas)} files); the copies must stay identical"
        )

    if failures:
        print("OpenAPI contract check FAILED\n", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        print(
            f"\n{len(failures)} problem(s). The code is the reference: fix the contract to match "
            f"the controllers, not the other way round.",
            file=sys.stderr,
        )
        return 1

    print(
        f"OpenAPI contract check passed: {len(service_dirs)} services, {len(contracts)} contracts, "
        f"paths aligned and ApiError identical across all of them."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
