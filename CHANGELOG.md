# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.1] - 2026-10-23

Patch release found by running the SDK against a real app in test mode (see `docs/api-coverage.md`, "Live verification").

### Fixed

- `merchant().balance()` failed against the real API, which answers with the bare number (`25`) instead of the documented `{"balance": 25}`. Both forms are now accepted.

### Changed

- `Invoice` gains `status`, `test`, `reference` and `testBalance` (all nullable). QvaPay's test mode returns no `url` and marks the invoice `paid` immediately; `url` is documented as nullable. Constructor users of the `Invoice` record must add the four new arguments.

## [0.1.0] - 2026-10-23

First release. Unofficial, community-maintained SDK for the QvaPay merchant API (`/v2`), based on the public documentation as of 2026-10-22.

### Added

- `QvaPayClient` builder with HTTPS-only base URL, timeouts and opt-in read retries.
- Merchant `info()` (`POST /v2/info`).
- Merchant `balance()` (`POST /v2/balance`) returning `BigDecimal`.
- Invoices `create()` (`POST /v2/create_invoice`) with client-side validation of the documented limits; never retried.
- Invoices `modify()` (`POST /v2/modify_invoice`), distinguishing an absent `expire_at` from an explicit `null` (`resetExpireAt()`).
- Transactions `list(page, take)` (`POST /v2/transactions`, parameters in the JSON body) with tolerant `TransactionStatus`.
- Transactions `get(uuid)` (`POST /v2/transactions/{uuid}`) with the `PaidBy` payer.
- Payments `authorize()` (`POST /v2/authorize_payments`): returns the consent URL for the user; never retried.
- Payments `charge()` (`POST /v2/charge`): moves money, never retried (including real timeouts).
- Webhooks: `Webhooks.verify/sign` (HMAC-SHA256 over raw bytes, constant-time) and `WebhookVerifier` with optional timestamp tolerance (default 300 s, injectable `Clock`).
- Distinct `InvoiceWebhookEvent` (`transaction_uuid`) and `TopUpWebhookEvent` (`uuid`); the payload is parsed only after verification and unsigned top-up webhooks are rejected.
