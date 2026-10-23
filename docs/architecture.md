# Architecture

Design of the QvaPay Java SDK (unofficial, community maintained).

## Baseline

| Item | Decision |
|---|---|
| Minimum runtime | **Java 17 LTS**. Built with JDK 17 and `--release 17`. CI also runs on 21 (and optionally 25). Java 8/11 are not supported. |
| Build | Apache Maven 3.10.0 via the Maven Wrapper |
| HTTP | `java.net.http.HttpClient` (JDK), injectable `HttpTransport` for tests |
| JSON | Jackson **3.2.4** (`tools.jackson.core:jackson-databind`), an implementation detail |
| Only runtime dependency | `jackson-databind` (and its transitive dependencies) |
| Tests | JUnit Jupiter 6.1.3, JDK `com.sun.net.httpserver.HttpServer` and an in-memory transport. No mock-server dependency. |
| Java package | `io.github.alejo2075.qvapay` (groupId `io.github.alejo2075`) |

## Layers

```
QvaPayClient ──┬── merchant()      MerchantApi    (info, balance)
               ├── invoices()      InvoiceApi     (create, modify)
               ├── transactions()  TransactionApi (list, get)
               └── payments()      PaymentsApi    (authorize, charge)
               every operation: blocking form + `...Async` form (CompletableFuture)
                         │
                  RequestExecutor (internal)  ──  builds ApiRequest, maps ApiResponse → model or exception
                         │
                  HttpTransport (interface) ──  JdkHttpTransport | test doubles
```

| Package | Contents | Visibility |
|---|---|---|
| `io.github.alejo2075.qvapay` | `QvaPayClient`, `MerchantApi`, `InvoiceApi`, `TransactionApi`, `PaymentsApi`, `QvaPayVersion`. The API classes live here so their constructors can be package-private and no internal type leaks into a public signature. | public |
| `.auth` | `AppCredentials`, `BearerTokenProvider`, `AuthPolicy` | public |
| `.http` | `HttpTransport`, `JdkHttpTransport`, `ApiRequest`, `ApiResponse`, `RetryPolicy` | public (extension points) |
| `.model` | immutable request and response types | public |
| `.exception` | exception hierarchy | public |
| `.webhook` | `Webhooks`, `VerifiedWebhook<T>`, event types | public |
| `.internal` | `JsonCodec`, `RequestExecutor`, tri-state helper | not part of the API (documented as such, no `module-info` exports) |

## Key decisions

### Models
- Responses are Java 17 `record`s. Names are camelCase; JSON uses snake_case through one naming strategy in the mapper, with explicit names only where the wire format differs (`PaidBy`).
- Request types are final classes with validating builders, because they enforce constraints and (for `modify_invoice`) need a tri-state.
- Money is always `BigDecimal`, read from a JSON number **or** a decimal string, written with `WRITE_BIGDECIMAL_AS_PLAIN` so `25.00` stays `25.00`. The SDK never rounds.
- Instants are `Instant` parsed from ISO-8601 (nullable).
- Unknown enum values map to `UNKNOWN` (statuses) and unknown JSON properties are ignored.
- No universal `data` wrapper: each type mirrors its endpoint.

### Jackson encapsulation
No Jackson type appears in a public signature. Only `com.fasterxml.jackson.annotation` annotations (still the Jackson 3 annotations package) may decorate model records; `jackson-annotations` is a transitive dependency anyway. The mapper is built once, immutable and thread-safe, in `internal.JsonCodec`. A test keeps Jackson 2 on the classpath to prove coexistence.

### Authentication
Each operation declares an `AuthPolicy` (`PUBLIC`, `APP`, `BEARER`, `APP_OR_BEARER`). The executor attaches only the headers that policy needs, so app credentials never leak into Bearer calls and vice versa. Credentials are never put in a JSON body. `AppCredentials.toString()` redacts the secret. Bearer tokens come from a `BearerTokenProvider` supplied by the user; the SDK never logs in or refreshes by itself.

### Errors
All exceptions are unchecked and extend `QvaPayException`.

| HTTP | Exception |
|---|---|
| 400 | `ValidationException` |
| 401 | `AuthenticationException` |
| 403 | `PermissionDeniedException` |
| 404 | `NotFoundException` |
| 429 | `RateLimitException` (carries `Retry-After` when present) |
| 5xx | `ServerException` |
| other non-2xx | `QvaPayApiException` |
| I/O, timeout, interruption | `TransportException` |
| JSON problems | `SerializationException` |

API exceptions expose `statusCode()`, `errorCode()` (the stable `code`, may be null) and the server message. They never include request headers or bodies. Code must branch on `errorCode()`, not on message text.

### Retries
Disabled by default. `RetryPolicy` is opt-in and only applies to operations flagged **safe to retry** (read-only calls such as `info`, `balance`, `transactions().list/get`; those are `POST` on the wire, which is why the flag is explicit per operation). `create_invoice`, `modify_invoice`, `authorize_payments` and `charge` are never retried. Backoff is exponential with jitter and honours `Retry-After`. `remote_id` is not treated as an idempotency key.

### Sync and async
Every operation is defined once (request builder plus response mapper) and exposed twice: a blocking method (`create`) and a `CompletableFuture` method with an `Async` suffix (`createAsync`). The blocking path uses `HttpClient.send` so interruption is preserved (the interrupt flag is restored and a `TransportException` is thrown). Async failures complete the future exceptionally with the typed exception as the cause.

### HTTP behaviour
- Base URL `https://api.qvapay.com`. Custom base URLs must be HTTPS unless `allowInsecureBaseUrlForTesting()` is called explicitly.
- Redirects are never followed (`Redirect.NEVER`), so credential headers cannot be forwarded to another host.
- Headers: `User-Agent: qvapay-java/<version>`, `Accept: application/json`, `Content-Type: application/json` for bodies.
- Path segments and query values are percent-encoded.
- Configurable connect and request timeouts.

### Webhooks
`Webhooks` is a static, framework-neutral helper; the SDK never starts an HTTP server.
1. Compute HMAC-SHA256 over the **raw bytes** with `app-secret` using `javax.crypto.Mac`.
2. Require the `sha256=<64 hex>` format, decode, and compare with `MessageDigest.isEqual`.
3. Optional timestamp check with an injectable `Clock` and a default tolerance of ±300 s. This is documented as a freshness check, **not** replay protection, because the timestamp is not part of the signed material.
4. Only after verification is the payload parsed into `InvoiceWebhookEvent` (`transaction_uuid`) or `TopUpWebhookEvent` (`uuid`). They are different types on purpose.
5. Unsigned top-up webhooks are rejected by default.

Integrators must still deduplicate by `transaction_uuid` and confirm amount, status and ownership (see `security.md`).

### Logging
The core has no logging dependency and logs nothing. Secrets can only leak through exceptions or `toString()`, both of which are tested for redaction.

## Extensibility
Bearer-authenticated families (login, user, coins, P2P, payment links) will reuse the same transport, executor and error mapping. Adding a family means adding an `*Api` class, its models and fixtures, with no change to the HTTP layer.
