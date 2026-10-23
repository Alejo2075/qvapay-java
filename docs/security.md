# Security guide

This SDK is unofficial and community maintained. It handles credentials that can move money, so read this page before going to production. To report a vulnerability in the SDK itself see [SECURITY.md](../SECURITY.md).

## Credentials

| Secret | Where it belongs | Never |
|---|---|---|
| `app-secret` | Environment variable or secret manager on **your server** | In a browser, mobile app, mini-app frontend, Git, logs or screenshots |
| User Bearer token | Memory of the process acting for that user | In logs, URLs or shared config |

- The secret is shown once when you create the app in QvaPay. If it leaks, rotate it in QvaPay immediately.
- Use an app in **test mode** for development. Keep `.env` out of Git (`.env.example` only has empty placeholders).
- App credentials are sent as **HTTP headers** (`app-id`, `app-secret`), never in a JSON body. Each operation sends only the credential type it needs, so a client that holds both an app and a Bearer token never mixes them.
- Several clients with different apps can coexist; each keeps its own credentials.

## What the SDK does for you

- HTTPS is required for the base URL. Plain `http://` needs `allowInsecureBaseUrlForTesting()`, meant for a local mock server.
- Redirects are never followed, so credentials cannot be forwarded to another host.
- `toString()` of credentials, tokens, requests and clients is redacted; exception messages never contain headers or request bodies; the SDK does not log.
- Error handling uses the stable `code` field of the API (`KYC_REQUIRED`, `DUPLICATE_REQUEST`, ...) through `QvaPayApiException.errorCode()`. Do not parse message text.
- Money uses `BigDecimal` and is never rounded.
- `create_invoice`, `modify_invoice`, `authorize_payments` and `charge` are **never retried**, even with a retry policy. Opt-in retries apply only to read-only queries.

## Payments: do not trust the browser

The redirect back from the payment page proves nothing. Deliver goods only when:

1. A webhook passes `WebhookVerifier`, **and**
2. you deduplicated it by `transaction_uuid`, **and**
3. `remote_id`, amount and status match your own order (or `transactions().get(uuid)` confirms `PAID`).

Never build or modify the payment URL yourself and never send credentials to it: it is only for the customer's browser.

## Webhooks

- Verify the signature over the **raw bytes** before parsing. Re-serialized JSON will not match.
- `x-qvapay-timestamp` is **not signed**. The default ±300 s window discards stale deliveries but is not replay protection, so deduplication is mandatory. The 300 s figure comes from the official WooCommerce plugin, not from the QvaPay docs.
- Top-up webhooks are signed only when the top-up was created with app credentials. The SDK rejects any webhook without a signature.
- Answer `2xx` only after persisting the event. QvaPay retries on `429`, `5xx`, timeouts and network errors (about 0 s, 5 s and 30 s) and may deliver the same payment more than once.
- Serve the endpoint over HTTPS.

## Charging users

`payments().charge()` moves real money. Before using it:

- The user must have authorized your app (`payments().authorize()` gives them the consent URL) and the app needs the `allowed_payment_auth` permission from QvaPay.
- A transport error (timeout, connection reset) leaves the outcome **unknown**. List transactions and look for your `remote_id` before charging again. `remote_id` is your reference, not an idempotency key, unless QvaPay documents otherwise.
- QvaPay applies KYC, AML and OFAC rules. The SDK exposes errors such as `KYC_REQUIRED` and does not bypass them.
- Rate limit documented for charges: 5 requests per 20 seconds per app.

## Supply chain

- Runtime dependencies: `jackson-databind` 3.x (and its `jackson-core` / `jackson-annotations`). Nothing else.
- Releases are signed with GPG and published only through a manually approved workflow.
