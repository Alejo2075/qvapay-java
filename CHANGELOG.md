# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/).

## [Unreleased]

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
