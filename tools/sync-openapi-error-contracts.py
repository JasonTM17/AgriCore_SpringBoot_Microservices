#!/usr/bin/env python3
"""Synchronize the shared OpenAPI error envelope and response references.

The platform keeps one contract per service, but the runtime error payload is
defined in common-lib. This utility makes that duplication deterministic:

* every endpoint 4xx/5xx response references a reusable response component;
* every contract contains the same ApiError schema; and
* every response component needed by the statuses currently declared exists.

The transformation is intentionally text based. It preserves endpoint order,
descriptions, examples, and service-specific components instead of reformatting
the complete documents through a YAML serializer.
"""
from __future__ import annotations

import pathlib
import re


ROOT = pathlib.Path(__file__).resolve().parent.parent
CONTRACTS = ROOT / "contracts" / "openapi"

STATUS_COMPONENT = {
    "400": "BadRequest",
    "401": "Unauthorized",
    "403": "Forbidden",
    "404": "NotFound",
    "406": "NotAcceptable",
    "409": "Conflict",
    "410": "Gone",
    "413": "PayloadTooLarge",
    "415": "UnsupportedMediaType",
    "423": "Locked",
    "429": "RateLimitExceeded",
    "500": "InternalServerError",
    "502": "BadGateway",
    "503": "ServiceUnavailable",
}

API_ERROR = """\
    ApiError:
      type: object
      description: |
        Uniform error body for every AgriCore service, defined by ApiError in common-lib.
        `code` is the stable machine-readable discriminator and is part of the public contract.
        Null fields are omitted, so `violations`, `details`, and `traceId` are absent unless set.
      required: [timestamp, status, error, code, message, path]
      properties:
        timestamp: { type: string, format: date-time }
        status: { type: integer }
        error: { type: string, description: HTTP reason phrase }
        code: { type: string, example: DUPLICATE_RESOURCE }
        message: { type: string }
        path: { type: string }
        traceId: { type: string }
        violations:
          type: array
          description: Present only when code is VALIDATION_FAILED.
          items:
            type: object
            required: [field, message]
            properties:
              field: { type: string }
              message: { type: string }
        details:
          type: object
          additionalProperties: true
""".splitlines()

COMPONENTS = {
    "BadRequest": "The request is malformed, invalid, or fails validation.",
    "Unauthorized": (
        "Bearer credential missing, expired, or invalid. The security filter chain may "
        "return this response without a body."
    ),
    "Forbidden": "Authenticated, but missing the required role or permission.",
    "NotFound": "The requested resource does not exist or is not visible to the caller.",
    "NotAcceptable": "The requested response media type is not supported.",
    "Conflict": "The request conflicts with existing resource or domain state.",
    "Gone": "The requested resource or replay window is no longer available.",
    "PayloadTooLarge": "The request body exceeds the supported size.",
    "UnsupportedMediaType": "The request Content-Type is not supported.",
    "Locked": "The resource is temporarily locked by an in-progress operation.",
    "RateLimitExceeded": "The caller exceeded an enforced request or concurrency limit.",
    "InternalServerError": "The service could not complete the request.",
    "BadGateway": "An upstream dependency returned an invalid response.",
    "ServiceUnavailable": "The service or a required dependency is temporarily unavailable.",
}


def indentation(line: str) -> int:
    return len(line) - len(line.lstrip())


def response_component(name: str, description: str) -> list[str]:
    lines = [
        f"    {name}:",
        f"      description: {description}",
    ]
    if name != "Unauthorized":
        lines.extend(
            [
                "      content:",
                "        application/json:",
                "          schema:",
                "            $ref: '#/components/schemas/ApiError'",
            ]
        )
    return lines


def endpoint_response_refs(lines: list[str]) -> list[str]:
    output: list[str] = []
    index = 0
    while index < len(lines):
        match = re.match(r"^        '([45]\d\d)':", lines[index])
        if not match:
            output.append(lines[index])
            index += 1
            continue

        status = match.group(1)
        component = STATUS_COMPONENT.get(status, "InternalServerError")
        output.extend(
            [
                f"        '{status}':",
                f"          $ref: '#/components/responses/{component}'",
            ]
        )
        index += 1
        while (
            index < len(lines)
            and lines[index].strip()
            and indentation(lines[index]) > 8
        ):
            index += 1
    return output


def shared_api_error(lines: list[str]) -> list[str]:
    try:
        start = lines.index("    ApiError:")
    except ValueError:
        try:
            schemas = lines.index("  schemas:")
        except ValueError:
            components = lines.index("components:")
            end = section_end(lines, components, 0)
            return lines[:end] + ["  schemas:"] + API_ERROR + lines[end:]
        else:
            end = section_end(lines, schemas, 2)
            return lines[:end] + API_ERROR + lines[end:]

    end = block_end(lines, start, 4)
    return lines[:start] + API_ERROR + lines[end:]


def shared_response_components(lines: list[str]) -> list[str]:
    try:
        start = lines.index("  responses:")
    except ValueError:
        schemas = lines.index("  schemas:")
        additions = ["  responses:"]
        for name, description in COMPONENTS.items():
            additions.extend(response_component(name, description))
        return lines[:schemas] + additions + lines[schemas:]

    end = section_end(lines, start, 2)
    existing = {
        match.group(1)
        for line in lines[start + 1 : end]
        if (match := re.match(r"^    ([A-Za-z][A-Za-z0-9]*):\s*$", line))
    }
    additions: list[str] = []
    for name, description in COMPONENTS.items():
        if name not in existing:
            additions.extend(response_component(name, description))
    return lines[:end] + additions + lines[end:]


def block_end(lines: list[str], start: int, depth: int) -> int:
    index = start + 1
    while index < len(lines):
        if lines[index].strip() and indentation(lines[index]) <= depth:
            break
        index += 1
    return index


def section_end(lines: list[str], start: int, depth: int) -> int:
    return block_end(lines, start, depth)


def synchronize(contract: pathlib.Path) -> bool:
    original = contract.read_text(encoding="utf-8")
    had_trailing_newline = original.endswith(("\n", "\r"))
    lines = original.splitlines()
    lines = endpoint_response_refs(lines)
    lines = shared_api_error(lines)
    lines = shared_response_components(lines)
    updated = "\n".join(lines) + ("\n" if had_trailing_newline else "")
    if updated == original.replace("\r\n", "\n"):
        return False
    with contract.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write(updated)
    return True


def main() -> int:
    changed = [
        contract.name
        for contract in sorted(CONTRACTS.glob("*.yaml"))
        if synchronize(contract)
    ]
    if changed:
        print(f"Synchronized {len(changed)} OpenAPI contracts: {', '.join(changed)}")
    else:
        print("OpenAPI error contracts already synchronized.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
