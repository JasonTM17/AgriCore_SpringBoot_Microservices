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

MAPPING_ANNOTATION = re.compile(r"@(Get|Post|Put|Patch|Delete|Request)Mapping\b(\s*\()?")


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
    found: set[tuple[str, str]] = set()
    src = service_dir / "src" / "main" / "java"
    if not src.is_dir():
        raise Drift(f"{service_dir.name}: no src/main/java")

    for java in sorted(src.rglob("*Controller.java")):
        text = java.read_text(encoding="utf-8")
        _reject_unparseable(java, text)

        prefix = ""
        m = re.search(r'@RequestMapping\(\s*"([^"]*)"\s*\)', text)
        if m:
            prefix = m.group(1)

        for verb in VERBS:
            pattern = r"@" + verb.capitalize() + r'Mapping(?:\(\s*"([^"]*)"\s*\))?(?=\s|$|\()'
            for hit in re.finditer(pattern, text):
                suffix = hit.group(1) or ""
                found.add((verb.upper(), (prefix + suffix) or "/"))
    return found


def _reject_unparseable(java: pathlib.Path, text: str) -> None:
    """Every mapping annotation must be bare or carry exactly one string literal."""
    for hit in MAPPING_ANNOTATION.finditer(text):
        if not hit.group(2):
            continue  # bare annotation, e.g. @GetMapping
        tail = text[hit.end() - 1:]
        if not re.match(r'\(\s*"[^"]*"\s*\)', tail):
            snippet = tail[:60].splitlines()[0]
            raise Drift(
                f"{java.relative_to(ROOT)}: unsupported mapping annotation form "
                f"'@{hit.group(1)}Mapping{snippet}'. This checker understands a bare "
                f"annotation or a single string literal. Extend it rather than working around it."
            )


def normalise(pairs: set[tuple[str, str]]) -> set[tuple[str, str]]:
    return {(verb, re.sub(r"\{[^}]+\}", "{}", path)) for verb, path in pairs}


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
            implemented = normalise(implemented_paths(service))
        except Drift as exc:
            failures.append(str(exc))
            continue
        for verb, path in sorted(declared - implemented):
            failures.append(f"{service.name}: {verb} {path} is declared in the contract but no controller implements it")
        for verb, path in sorted(implemented - declared):
            failures.append(f"{service.name}: {verb} {path} is implemented but the contract does not declare it")
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
