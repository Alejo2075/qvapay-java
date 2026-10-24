# API coverage

Source of truth: the public QvaPay documentation at <https://www.qvapay.com/docs>. Base URL: `https://api.qvapay.com`.

**Statuses:** `verified` (contract read in the docs) → `implemented` → `tested` (offline contract tests with fixtures). `blocked` = needs an answer from QvaPay. `undocumented` = not in the docs, will not be implemented.

Last verification of every row below: **2026-10-22**.

## Merchant API (release 0.1.0)

All merchant operations authenticate with `app-id` + `app-secret` **headers** (never the body).

| Java operation | Method and path | Request | Response shape (not wrapped in `data`) | Doc page | Status |
|---|---|---|---|---|---|
| `merchant().info()` | `POST /v2/info` | no body | direct object: `uuid, name, url, desc, callback, success_url, cancel_url, logo, active, enabled, card, created_at, updated_at, app_photo_url` | `/docs/merchants/info` | tested |
| `merchant().balance()` | `POST /v2/balance` | no body | `{ "balance": 150.75 }` (USD) | `/docs/merchants/balance` | tested |
| `invoices().create(r)` | `POST /v2/create_invoice` | `amount`, `description`, `remote_id`; optional `webhook`, `products[]`, `expire_at` | direct: `app_id, amount, description, remote_id, transaction_uuid, expire_at (nullable), url` | `/docs/merchants/create-invoice` | tested |
| `invoices().modify(r)` | `POST /v2/modify_invoice` | `transaction_uuid` + any of `amount, description, remote_id, webhook, products, expire_at` | same shape as create | `/docs/merchants/modify-invoice` | tested |
| `transactions().list(page, take)` | `POST /v2/transactions` | JSON body `page` (≥1, default 1), `take` (default 30, max 100) | `{ "message": "APP Transactions", "transactions": [...] }` | `/docs/merchants/transactions` | tested |
| `transactions().get(uuid)` | `POST /v2/transactions/{uuid}` | uuid in path | `{ "message": "APP Transaction", "transaction": {..., "PaidBy": {...} \| null } }` | `/docs/merchants/transaction-detail` | tested |
| `payments().authorize(r)` | `POST /v2/authorize_payments` | `remote_id`, `callback` | `{ "message": "Authorize payments", "url": "..." }` | `/docs/merchants/authorize-payments` | tested |
| `payments().charge(r)` | `POST /v2/charge` | `amount`, `user_uuid`, `description`, `remote_id` | `{ "success": true, "message": "...", "transaction": {...} }` | `/docs/merchants/charge` | tested |

### Field notes

- `amount` limits: `> 0` and `<= 100000` (create, modify, charge).
- `description`, `remote_id`: 1–255 characters (create/modify).
- `modify_invoice`: only `pending` invoices; `expire_at: null` resets the expiry to 30 days; an **absent** field means "unchanged". The SDK models these differently.
- `PaidBy` keeps its capital P on the wire. Fields: `uuid, name, username, lastname, image, vip, kyc, golden_check`.
- Transaction `status` documented values: `pending`, `paid`, `cancelled` (parsed tolerantly; unknown values map to `UNKNOWN`).
- `charge` requires prior user authorization and the app permission `allowed_payment_auth`. A 0.5 % fee is deducted from the payer (GOLD plan exempt). Rate limit: 5 requests / 20 s per app.
- The transaction list documents **no pagination metadata**; none is modelled.

### Documented errors

| Endpoint | Status | Meaning |
|---|---|---|
| balance | 404 | owner user not found |
| create_invoice | 400 | invalid request data |
| modify_invoice | 400 / 403 / 404 | missing uuid or invalid data / not in `pending` / transaction not owned by app / not found |
| authorize_payments | 400 | missing `remote_id` or `callback`; app lacks permission |
| charge | 400 / 404 / 429 | invalid data, no permission, user has not authorized, insufficient balance / user or app not found / rate limit |
| transactions/{uuid} | 404 | not found or not owned by app |

General error body: `{ "error": "message" }`, optionally with a stable `code` (e.g. `KYC_REQUIRED`, `DUPLICATE_REQUEST`). Logic must use `code` when present, never the message text.

## Live verification in test mode (2026-10-23)

Run by hand with SDK 0.1.0/0.2.0-SNAPSHOT against `https://api.qvapay.com` using an app in **test mode** (no real funds). Only read operations plus a few 1.00 USD test invoices; `charge` and `authorize_payments` were **not** called.

| Operation | Result | Difference from the docs |
|---|---|---|
| `POST /v2/info` | OK, all documented fields present | none |
| `POST /v2/balance` | OK | **The body is the bare number `25`, not `{"balance": 25}`.** 0.1.0 failed to parse it; fixed (both forms accepted). |
| `POST /v2/create_invoice` | OK | In test mode the response has **no `url`** and adds `status: "paid"`, `test: true`, `reference: "TEST-<uuid>"`, `test_balance`. `amount` comes back as the string `"1"`. The invoice is **paid immediately**. |
| `POST /v2/transactions/{uuid}` | OK | `amount` is a string; `PaidBy` is `null` even though `status` is `paid` (test mode). |
| `POST /v2/modify_invoice` | 400 `Solo se pueden modificar transacciones pendientes` | Matches the docs (only `pending`); test invoices are never pending, so this path could not be exercised end to end. |
| `POST /v2/transactions` (list) | OK, but **empty** (`"transactions": []`) with `{}`, `{page:1,take:30}` and `{page:0}` even right after creating test invoices | Not documented: test-mode transactions do not appear in the list. Production behaviour unverified. |
| `POST /v2/transactions/{unknown-uuid}` | 404 `{"error": "APP Transaction not found"}` | matches |
| wrong `app-secret` | 401 `Unauthorized` | message text only; no `code` |
| invoice webhook | verified only with a locally signed payload | QvaPay's own webhook delivery was not exercised (needs a public HTTPS endpoint) |

Second round (same day, published 0.1.1, test-mode app, safe calls only):

| Operation | Result | Notes |
|---|---|---|
| `POST /v2/authorize_payments` | 400 `No tienes habilitado el acceso a pagos autorizados` | Matches the documented requirement: the app lacks `allowed_payment_auth`, which QvaPay support must enable. So the consent URL and `charge` could not be exercised. |
| `POST /v2/charge` with a nonexistent user UUID, 0.01 USD | 404 `Usuario no encontrado` | Matches the docs (404 user not found); typed as `NotFoundException`. It cannot charge anyone. |
| `create_invoice` with `webhook`, `expire_at` and two `products` (one without `quantity`) | OK | `expire_at` returned as sent; the product without `quantity` comes back with `quantity: 1`, as documented. `amount` `1.50` comes back as `"1.5"`. |
| `create_invoice` with `amount: 0` or a missing `description` | 400 `Faltan campos requeridos` | the server treats `0` as missing |
| `create_invoice` with `amount: 100001` | 400 `El monto debe ser un número positivo entre 0 y 100000` | same limit the SDK enforces locally |
| `create_invoice` with an invalid `webhook` / past `expire_at` | 400 `El webhook debe ser una URL válida` / `expire_at debe ser una fecha futura` | same rules the SDK enforces locally |
| `authorize_payments` without `callback` | 400 `Faltan campos requeridos` | |
| any `/v2/*` call without credentials | 401 `Unauthorized` | |
| `transactions` with `page=2, take=1` | OK, empty | consistent with the empty list above |

Error bodies seen so far are always `{"error": "<Spanish message>"}` with no `code`; no rate-limit headers were observed in these few calls.

Not verified live: a real (non-test) invoice, `url` format in production, `modify_invoice` on a pending invoice, a successful `authorize_payments` and `charge` (the app needs `allowed_payment_auth`), top-up webhook, delivery retries, rate-limit headers.

## Webhooks (release 0.1.0)

| Event | Source | Identifier | Signature | Status |
|---|---|---|---|---|
| Invoice paid | `/docs/miniapps/payments` | `transaction_uuid` | `x-qvapay-signature: sha256=<hex>` = HMAC-SHA256(raw body, `app-secret`); `x-qvapay-timestamp` = Unix seconds | tested |
| Top-up credited | `/docs/merchants/topup-webhook` | `uuid` | same scheme, **only** when the top-up was created with app credentials; Bearer-created top-ups arrive **unsigned** (the SDK rejects unsigned) | tested |

Invoice payload: `transaction_uuid, remote_id, status, amount (string), signed (number), created_at, updated_at`.
Top-up payload: `uuid, status ("paid"), is_partial, amount (string USD credited), requested_usd (number), received_crypto (string), coin, tx_id (nullable), paid_at`.

Delivery: up to three attempts (about 0 s, 5 s, 30 s) on `429`, `5xx`, timeout or network error. Other `4xx` and `3xx` are not retried. Handlers must be idempotent.

## Later phases (not in 0.1.0)

| Family | Routes (from the docs/index) | Status |
|---|---|---|
| Login (2FA) | `POST /auth/login` | verified in `CLAUDE.md`, not implemented |
| Profile | `GET /user` | not implemented |
| Coins | `GET /coins`, `GET /coins/{id}`, `GET /coins/price-history/{tick}` | not implemented |
| Payment links | `GET/POST/DELETE /user/payment-links` | not implemented |
| P2P | `GET /p2p`, `GET /p2p/{uuid}`, app-credential operations | not implemented |
| Withdraw, transfers, store, top-up creation | various | not implemented; need compliance and idempotency review first |
| Mini-app `init_data` backend verification | n/a | blocked until the algorithm is documented |

## Open points (`TODO: VERIFY WITH QVAPAY`)

1. Is `remote_id` unique per app? What happens on duplicates in `create_invoice` / `charge`? (Not documented, so no idempotency is assumed.)
2. Does `charge` return `code: "DUPLICATE_REQUEST"` in some cases? (Mentioned only generically in the overview.)
3. Exact decimal precision accepted for `amount`. (No limit is imposed by the SDK.)
4. Whether `info` / `balance` accept a missing request body with `Content-Type: application/json` on all gateways. (The SDK sends an empty JSON object `{}`; verify with a test-mode app.)
5. Meaning and possible values of `PaidBy.kyc`. Stored as a string.
6. The topup webhook page mentions creating top-ups with `POST /api/topup`, a route outside the documented merchant set.
8. Is the bare-number `balance` response the normal format or a test-mode artifact? Both are accepted.
9. Why does `POST /v2/transactions` return no test-mode transactions? Does production list them?
10. Which fields (`status`, `test`, `reference`, `test_balance`) can appear in `create_invoice` responses, and is `url` always present outside test mode?
7. Whether the ±300 s timestamp window is a QvaPay-side guarantee or only the official plugin's convention.
