# 01 — Đăng nhập

- Stitch screen: `08b8055310fb44e7bc3959c0e208d5fd`
- Device: desktop, `2560 × 2048` export
- Audience: every authenticated role

## Intent

Provide a focused, security-aware entry point without exposing the authenticated application shell. The brand panel establishes trust; the form keeps recovery guidance close to the failed action.

## Contract anchors

- Submit credentials through the identity login endpoint.
- Treat invalid credentials, rate limiting, locked accounts, and disabled accounts as distinct recoverable states.
- The security note reflects the implemented five-attempt, 15-minute lock policy.

## Required states

- Idle, submitting, success redirect, `INVALID_CREDENTIALS`, locked, disabled, and rate-limited.
- Preserve the entered email after a failed attempt; never preserve the password.
- On narrow screens, collapse the brand panel to a compact header and keep the form first in reading order.

## Accessibility

Use persistent labels, an announced inline error summary, password visibility text, visible focus, and a 44px minimum target. Move focus to the error summary after a failed submit.
