# Reference repository study

Research performed on **2026-10-22**. These repositories were **read as research input only**. No code was copied or translated from any of them. Network behaviour in this SDK is derived from the public QvaPay documentation (see [api-coverage.md](api-coverage.md)).

Order of precedence for network behaviour: official docs > official plugin code (if consistent with the docs) > controlled fixtures > unofficial/historical implementations > hypotheses.

## Summary

| Repository | Commit studied | License (verified in repo) | Role for this SDK |
|---|---|---|---|
| [ragnarok22/qvapay-python](https://github.com/ragnarok22/qvapay-python) | `f896778` (2026-10-07) | MIT (`LICENSE`, © 2021-2026 Reinier Hernández) | Design inspiration (layers, sync/async, models, tests) |
| [qvapay/qvapay-woocommerce](https://github.com/qvapay/qvapay-woocommerce) | `9531009` (2026-05-17) | **GPL-2.0** (`LICENSE`) | Behavioural reference for invoices and webhooks. **Never port code.** |
| [qvapay/client-node](https://github.com/qvapay/client-node) | `db5c5e2` (2023-02-18) | `package.json` says MIT, but **no `LICENSE` file** in the repo | Feature checklist only. Outdated. **No code reuse.** |
| [qvapay/qvapay-cli](https://github.com/qvapay/qvapay-cli) | `5ab07e5` (2026-08-31) | MIT (`LICENSE`, © 2026 Pep3M) | UX and safety ideas (a library is not a CLI) |

## 1. qvapay-python (priority 1: design)

| Inspiration | What not to copy | Confirmed by the API docs |
|---|---|---|
| Separate **user** (Bearer) and **merchant** (app credentials) clients | Credentials are sent **inside the JSON body** (`json=self._auth`). Current docs require `app-id` / `app-secret` **headers**. | Merchant ops use app credentials in headers |
| Sync and async twins generated from one source | Returns `float` for balance. This SDK uses `BigDecimal`. | `POST /v2/balance` → `{ "balance": 150.75 }` |
| Small module-per-resource layout (`merchant`, `coins`, `p2p`, `payment_links`, …) | `get_transactions()` takes no `page`/`take`. The docs define both. | `POST /v2/transactions` body: `page`, `take` |
| Typed models, per-feature tests, `CHANGELOG`, `CONTRIBUTING`, `docs/api_endpoints.md` | Generic `Any` return types for charge/authorize | Charge/authorize have documented response shapes |
| Single error type carrying HTTP status | Its endpoint list comes from a Postman collection dated 2025; use the live docs instead | Error body is `{ "error": "..." }`, optionally with `code` |

Notes: base URLs used are `https://api.qvapay.com` and `https://api.qvapay.com/v2`, which agrees with the docs. README structure (install → quick start → modules) is a good template, but this project's README is written from scratch.

## 2. qvapay-woocommerce (priority 2: payment compatibility)

**License is GPL-2.0. Nothing from this repo is copied, translated or adapted.** Only behaviour that is also stated in the public docs is relied upon.

| Inspiration (behaviour only) | What not to copy | Confirmed by the API docs |
|---|---|---|
| Sends `app-id` / `app-secret` as **headers** on `POST /v2/create_invoice` | PHP/WordPress code, class structure, REST route names | Yes (`docs/merchants/create-invoice`) |
| Webhook acceptance order: headers present → timestamp within ±300 s → HMAC over **raw body** → JSON valid → order lookup → idempotency | The order/refund state machine, `payment_complete()` flow | HMAC-SHA256 over raw body with `app-secret`: yes. ±300 s window: **plugin convention only**; docs say the timestamp is "useful to mitigate replays" |
| Rejects a webhook whose `transaction_uuid` does not match the stored order | WooCommerce-specific order storage | Docs: use `transaction_uuid` as idempotency key |
| Constant-time signature comparison | n/a | Docs sample uses `timingSafeEqual` |

## 3. client-node (priority 3: module coverage)

| Inspiration | What not to copy | Confirmed by the API docs |
|---|---|---|
| Module inventory: `auth`, `merchant`, `p2p`, `paymentLinks`, `rates`, `services`, `transactions`, `user` | Everything else: no `LICENSE` file, so no reuse rights are granted | Only the families that appear in current docs |
| Reminder that apps need `info`, `balance`, `create_invoice`, transactions | Targets `https://qvapay.com/api` and `/v1/*` paths: **obsolete** | Current base: `https://api.qvapay.com`, `/v2/*` |
| | Credentials in body; errors swallowed and returned as if they were successful data | Errors must raise typed exceptions |
| | Fields such as `signed` on invoice creation | `signed` is **not** a documented creation parameter (it only appears in the invoice webhook payload) |

## 4. qvapay-cli (priority 4: UX and flows)

| Inspiration | What not to copy | Confirmed by the API docs |
|---|---|---|
| Token is redacted in verbose logs; password never persisted | Local credential storage, TUI, config file | Bearer tokens last 2 h (180 days with `remember`) |
| Transfers always need a human step (PIN by email); automation must not bypass it | Local spend policy (max per tx, daily cap, whitelist): that is application policy, not SDK scope | Auth/consent flows are interactive |
| Clear, stable error messages and exit codes | CLI commands and process-level behaviour | Programmatic `code` field is the stable contract |

## Discrepancies found

1. **Credentials location:** Python/Node send them in the body; the official docs and the official plugin send headers. SDK follows headers (see `CLAUDE.md` principle 2).
2. **API version:** Node uses `/v1`, Python/docs use `/v2`.
3. **Topup path:** the topup webhook page mentions `POST /api/topup`. That route is not part of the merchant contract documented under `/v2/*`. Out of scope for 0.1.0; tracked in `api-coverage.md`.
4. **Timestamp tolerance:** ±300 s comes from the official plugin, not from the docs. It is configurable here and described as a convention.
