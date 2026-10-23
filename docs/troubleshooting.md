# Troubleshooting

## Exceptions

All SDK exceptions extend the unchecked `QvaPayException`.

| Exception | HTTP | Typical cause | What to do |
|---|---|---|---|
| `AuthenticationException` | 401 | Wrong `app-id`/`app-secret`, expired token | Call `client.merchant().info()` to test the credentials; check the environment variables |
| `PermissionDeniedException` | 403 | Missing permission, ownership, `KYC_REQUIRED` | Read `errorCode()`; for `/v2/modify_invoice` the transaction belongs to another app |
| `ValidationException` | 400 | Invalid data, invoice not `pending`, app lacks `allowed_payment_auth`, insufficient balance | Read `apiMessage()` |
| `NotFoundException` | 404 | Unknown transaction/user, or not owned by your app | Check the UUID |
| `RateLimitException` | 429 | Too many requests | Wait `retryAfter()`; charges allow 5 per 20 s per app |
| `ServerException` | 5xx | QvaPay failure | Retry reads later; for writes check state first |
| `TransportException` | none | Timeout, connection reset, TLS, interruption | **Outcome unknown** for writes: query before repeating |
| `SerializationException` | 2xx | Unexpected body | Open an issue with a sanitized response |
| `WebhookVerificationException` | n/a | Bad signature or timestamp | See `reason()` |

`IllegalArgumentException` is thrown locally when a documented limit is violated (amount, lengths, webhook URL, `take` > 100). `IllegalStateException` means the operation needs credentials the client does not have.

## Common problems

**`IllegalStateException: This operation needs app credentials`**: the builder was created without `appCredentials(...)`. Make sure `QVAPAY_APP_ID` and `QVAPAY_APP_SECRET` are set in the process that runs the code (an unset variable makes `System.getenv` return `null`, which the builder rejects).

**`baseUrl must use https`**: only a local mock server may use `http://`, and only with `allowInsecureBaseUrlForTesting()`.

**Webhook signature never matches**
1. Are you verifying the raw bytes? Frameworks that bind JSON first (Spring `@RequestBody` on a DTO, Express `express.json()`) destroy the original bytes. Read `byte[]`/the raw stream.
2. Is the secret the *app* secret of the app that created the invoice?
3. A proxy or gateway that modifies the body (compression, re-encoding) also breaks it.
4. `TIMESTAMP_OUT_OF_RANGE`: the server clock is off by more than 300 s, or the delivery is old. Fix NTP, or widen the tolerance with `timestampTolerance(...)`.

**Same payment arrives twice**: expected. Deduplicate by `transaction_uuid`.

**Top-up webhook rejected as `MISSING_SIGNATURE`**: the top-up was created with a user Bearer token. Create it with app credentials.

**`403 KYC_REQUIRED`**: the account of the app owner/user needs identity verification in QvaPay. The SDK cannot bypass it.

**Timeouts**: defaults are 5 s to connect and 15 s per request. Change them with `connectTimeout(...)` / `requestTimeout(...)`.

## Unsure what the API does?

Check [api-coverage.md](api-coverage.md), including the list of open questions for QvaPay. Behaviour not covered by the official documentation is not assumed by the SDK.
