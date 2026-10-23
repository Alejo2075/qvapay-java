# PLAN.md — Plan de desarrollo del QvaPay Java SDK (0.1.0)

> Plan operativo derivado de `CLAUDE.md`. Cubre desde el scaffold vacío hasta un repositorio **release-ready** para Maven Central y consumible desde Maven/Gradle.
> Fecha: 22-oct-2026. Estado inicial del repo: solo `CLAUDE.md` y `.idea/`; **sin git, sin JDK y sin Maven instalados** en la máquina.
> Este archivo es interno (español, como `CLAUDE.md`). Toda la documentación pública del repo va en inglés.

---

## 0. Resumen

| Hito | Contenido | Resultado verificable |
|---|---|---|
| M0 | Entorno y decisiones humanas | `java -version` = 17, owner/namespace decididos |
| M1 | Scaffold Maven + higiene OSS | `./mvnw -B -ntp clean verify` verde en un proyecto vacío |
| M2 | Investigación y diseño | `docs/references.md`, `docs/api-coverage.md`, `docs/architecture.md` |
| M3 | Núcleo: config, auth, transporte, JSON, errores | Tests de headers, errores y redacción de secretos |
| M4 | 8 endpoints Merchant v2 | Un fixture + tests por endpoint |
| M5 | API asíncrona (`CompletableFuture`) | Paridad sync/async probada |
| M6 | Webhooks (factura + topup) | Tests HMAC completos |
| M7 | Hardening y tests transversales | Checklist §9 de `CLAUDE.md` completo |
| M8 | Ejemplos consumidores (Maven + Gradle) | Compilan contra el JAR instalado en `~/.m2` |
| M9 | Documentación pública | README EN/ES, docs/, archivos comunitarios |
| M10 | CI | `ci.yml` con matriz 17/21 (+25 opcional) |
| M11 | Preparación Maven Central | Bundle firmado generado **sin upload** |
| M12 | Release 0.1.0 (gate humano) | Solo con aprobación explícita |

Orden obligatorio según `CLAUDE.md §13`: **transporte y auth → modelos → merchant → webhooks → pruebas → docs**. Nada de P2P, retiros ni login hasta cerrar 0.1.0.

---

## 1. Estrategia de commits

### 1.1 Convención: Conventional Commits 1.0

```
<type>(<scope>): <resumen en inglés, imperativo, ≤72 chars>

<cuerpo opcional: qué y por qué, no cómo>

<footers: Refs, BREAKING CHANGE, Co-Authored-By>
```

**Types:** `feat`, `fix`, `test`, `docs`, `build`, `ci`, `refactor`, `chore`, `perf`, `style`.

**Scopes del proyecto:**

| Scope | Abarca |
|---|---|
| `build` | `pom.xml`, wrapper, plugins, enforcer, spotless, jacoco |
| `core` | `QvaPayClient`, `QvaPayClientConfig`, builder |
| `auth` | `AppCredentials`, `BearerTokenProvider`, `AuthPolicy` |
| `http` | `HttpTransport`, `JdkHttpTransport`, `ApiRequest`, retries |
| `json` | mapper Jackson 3 encapsulado |
| `error` | jerarquía de excepciones, `QvaPayApiError` |
| `merchant`, `invoices`, `transactions`, `payments` | APIs y modelos de cada grupo |
| `webhook` | `Webhooks`, verificadores, payloads |
| `examples` | `examples/**` |
| `docs`, `readme` | `docs/**`, `README*.md` |
| `ci`, `release` | workflows, perfil `central`, `docs/release.md` |
| `repo` | `.gitignore`, `.editorconfig`, plantillas, licencias |

### 1.2 Reglas

1. **Atómico:** un commit = un cambio lógico (p. ej. un endpoint con su modelo, fixture y tests). Código y sus tests viajan juntos.
2. **Verde en cada commit:** `./mvnw -B -ntp clean verify` debe pasar en cada commit de `main` (permite `git bisect`). No se hace commit de código roto “para arreglar después”.
3. **Sin secretos jamás:** `gitleaks` como hook pre-commit local (opcional) y obligatorio en CI. `.env` en `.gitignore` desde el primer commit.
4. **Idioma:** mensajes en inglés.
5. **Breaking changes:** `feat(core)!:` + footer `BREAKING CHANGE:` (relevante a partir de 0.1.0).
6. **Commits hechos por Claude** terminan con `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
7. **TODOs de la API:** se marcan `TODO: VERIFY WITH QVAPAY` en código y se listan en `docs/api-coverage.md` en el mismo commit.

### 1.3 Ramas y merges

- `main`: siempre verde y protegida (tras crear el repo en GitHub: PR obligatorio, CI requerido, sin force-push).
- Ramas cortas por hito/feature: `feat/m3-http-transport`, `feat/m4-invoices`, `docs/readme`, `ci/workflows`, `release/0.1.0`.
- Merge por **rebase/fast-forward** (preserva commits atómicos y lineales). Squash solo si la rama tiene commits “wip” que no se pudieron limpiar.
- Mientras no exista remoto en GitHub: trabajo local en ramas y `git merge --ff-only` a `main`.

### 1.4 Versionado y tags

- `main` en desarrollo: `0.1.0-SNAPSHOT` (solo local, nunca se publica).
- Release: commit `chore(release): prepare 0.1.0` (quita `-SNAPSHOT`, fecha en `CHANGELOG.md`) → tag anotado `v0.1.0` → commit `chore(release): start 0.2.0-SNAPSHOT`.
- Los tags **no** disparan publicación (el workflow de release es solo `workflow_dispatch`).
- `CHANGELOG.md` (Keep a Changelog) se actualiza en la sección `[Unreleased]` en el mismo commit que introduce la funcionalidad, a partir de M4.

---

## 2. Hitos detallados con su secuencia de commits

> Cada bloque lista los commits previstos en orden. El número es orientativo; lo importante es la atomicidad y que cada uno quede verde.

### M0 — Entorno y decisiones (sin commits)

**Acciones en la máquina (requieren al usuario):**

```bash
# SDKMAN (o Homebrew/Temurin)
sdk install java 17.0.x-tem      # JDK principal
sdk install java 21.0.x-tem      # compatibilidad
sdk install maven 3.10.0         # solo para generar el wrapper la primera vez
```

**Decisiones bloqueantes (HUMAN INPUT):**

| Decisión | Propuesta por defecto | Impacto |
|---|---|---|
| Owner GitHub | *pendiente* (no inventar) | `groupId = io.github.<owner>`, package `io.github.<owner>.qvapay` |
| Licencia | MIT | `LICENSE`, `<licenses>` del POM |
| Nombre del repo | `qvapay-java` | URLs de SCM, README, badges |
| Mantenedor en POM | nombre + URL GitHub públicos | `<developers>` |
| Formatter | Spotless + google-java-format (AOSP) | estilo en CI |
| Mock HTTP en tests | `com.sun.net.httpserver.HttpServer` del JDK + transporte fake en memoria | **cero dependencias extra** (evita OkHttp/Kotlin de MockWebServer) |

Verificar que las versiones fijadas en `CLAUDE.md §7` existen en Maven Central (Maven 3.10.0, compiler 3.16.0, surefire 3.6.0, JUnit 6.1.3, Jackson 3.2.4, central-publishing 0.12.0, Gradle 9.8.2). Si alguna no existe, **detenerse y consultar**, no degradar en silencio.

---

### M1 — Scaffold (rama `chore/m1-scaffold`)

| # | Commit | Contenido |
|---|---|---|
| 1 | `chore(repo): initialize repository with project instructions` | `git init`, `CLAUDE.md`, `PLAN.md`, `.gitignore` (target/, .idea/, *.iml, .env, *.asc no deseados, .gradle/, build/) |
| 2 | `chore(repo): add editorconfig, gitattributes and MIT license` | `.editorconfig`, `.gitattributes` (`mvnw` con LF, `*.cmd` CRLF), `LICENSE` |
| 3 | `build: add Maven wrapper 3.10.0` | `.mvn/wrapper/*`, `mvnw`, `mvnw.cmd` |
| 4 | `build: add base pom with Java 17 release and pinned plugins` | `pom.xml` con coordenadas, `maven.compiler.release=17`, BOMs Jackson 3 y JUnit 6, compiler 3.16.0, surefire 3.6.0, jar plugin con `Automatic-Module-Name` |
| 5 | `build: enforce Maven 3.10+, Java 17+ and dependency convergence` | `maven-enforcer-plugin` (requireMavenVersion, requireJavaVersion, banDuplicatePomDependencyVersions, dependencyConvergence, requireUpperBoundDeps) |
| 6 | `build: add spotless formatting and jacoco coverage` | Spotless (check en `verify`), JaCoCo con umbral inicial bajo que sube en M7 |
| 7 | `build: add sources and javadoc jars to the default build` | `maven-source-plugin`, `maven-javadoc-plugin` (doclint activo), `package-info.java` raíz |
| 8 | `chore(repo): add .env.example with placeholders` | `QVAPAY_APP_ID=`, `QVAPAY_APP_SECRET=` vacíos |

**DoD M1:** `./mvnw -B -ntp clean verify` verde con JDK 17 y 21; el JAR (vacío) tiene bytecode major 61 (`javap -v`).

---

### M2 — Investigación y diseño (rama `docs/m2-research`)

Antes de crear clases Java (`CLAUDE.md §2.1`). Leer README, código y LICENSE de los 4 repos + las URLs de docs de QvaPay.

| # | Commit | Contenido |
|---|---|---|
| 9 | `docs: add reference repository study` | `docs/references.md`: tabla Inspiración / Qué no copiar / Qué está confirmado en la API para python, woocommerce (GPL → no portar), client-node, cli; licencias verificadas |
| 10 | `docs: add API coverage inventory with verification dates` | `docs/api-coverage.md`: todas las rutas conocidas con estado `verified/implemented/tested/blocked/undocumented`, URL fuente y fecha; discrepancias entre fuentes |
| 11 | `docs: add architecture design` | `docs/architecture.md`: capas, decisiones (ver §3 de este plan), política de auth por operación, retries, sync/async, tri-estado ausente/nulo, encapsulación de Jackson |

**DoD M2:** cada uno de los 8 endpoints merchant y los 2 webhooks tiene contrato capturado (request, response, auth, errores) y su fixture JSON planificado.

---

### M3 — Núcleo (rama `feat/m3-core`)

| # | Commit | Contenido |
|---|---|---|
| 12 | `feat(error): add exception hierarchy and api error model` | `QvaPayException` (base, unchecked) → `QvaPayApiException` (status, code, message, rate-limit headers) → `ValidationException` 400, `AuthenticationException` 401, `PermissionDeniedException` 403, `NotFoundException` 404, `RateLimitException` 429 (`retryAfter`), `ServerException` 5xx; `TransportException`, `SerializationException` |
| 13 | `feat(auth): add app credentials and bearer token provider` | `AppCredentials` (secreto redactado en `toString`, `equals` sin filtrar), `BearerTokenProvider` funcional, `AuthPolicy {PUBLIC, APP, BEARER, APP_OR_BEARER}` |
| 14 | `feat(json): add encapsulated Jackson 3 mapper` | `JsonCodec` interno (package-private/`internal`), `BigDecimal` desde número o string, `Instant` ISO-8601, enums tolerantes, `FAIL_ON_UNKNOWN_PROPERTIES=false` |
| 15 | `feat(http): add transport abstraction and api request model` | `HttpTransport` (`send`, `sendAsync`), `ApiRequest`/`ApiResponse` inmutables, encoding de segmentos de ruta |
| 16 | `feat(http): add JDK HttpClient transport with timeouts` | `JdkHttpTransport`, `Redirect.NEVER`, timeouts, preservación de interrupción |
| 17 | `feat(http): map error responses to typed exceptions` | parsing de `{code, message}` / `{error}`, sin incluir headers de request |
| 18 | `feat(http): add opt-in retry policy for safe reads` | `RetryPolicy` (desactivado por defecto, backoff exponencial con jitter, `Retry-After`, máx. intentos); las operaciones se marcan `idempotent=false` y nunca se reintentan |
| 19 | `feat(core): add QvaPayClient builder and configuration` | `QvaPayClient.builder()`, `baseUrl` (HTTPS obligatorio salvo `allowInsecureBaseUrlForTesting`), `User-Agent: qvapay-java/<version>` leído de recurso filtrado por Maven |
| 20 | `test(http): add in-memory transport and JDK HttpServer test harness` | utilidades de test reutilizables |

Cada commit incluye sus tests (headers exactos, mapeo 400/401/403/404/429/500, redacción de secretos, HTTPS requerido).

---

### M4 — Endpoints Merchant v2 (rama `feat/m4-merchant`)

Un commit por endpoint con **modelo + API + fixture JSON (copiado del contrato oficial) + tests de request y response + entrada en CHANGELOG + estado en api-coverage**.

| # | Commit | Endpoint |
|---|---|---|
| 21 | `feat(merchant): add app info endpoint` | `POST /v2/info` → `AppInfo` |
| 22 | `feat(merchant): add app balance endpoint` | `POST /v2/balance` → `AppBalance(BigDecimal)` |
| 23 | `feat(invoices): add create invoice endpoint` | `POST /v2/create_invoice`, `CreateInvoiceRequest` con validación (amount >0 ≤100000, longitudes 1–255, `expire_at` futuro), `InvoiceProduct`, `Invoice` |
| 24 | `feat(invoices): add modify invoice with absent vs null semantics` | `POST /v2/modify_invoice`; builder con `expireAt(Instant)` vs `resetExpireAt()` → serializa `null` explícito; campos no tocados no se envían |
| 25 | `feat(transactions): add paginated app transactions list` | `POST /v2/transactions` con `page`,`take` en body (take ≤100, default 30); `AppTransactionPageResponse` sin metadatos inventados; `TransactionStatus` con `UNKNOWN` |
| 26 | `feat(transactions): add transaction detail with PaidBy payer` | `POST /v2/transactions/{uuid}` con UUID encodeado; `PayerInfo` mapeado a `"PaidBy"` |
| 27 | `feat(payments): add authorize payments endpoint` | `POST /v2/authorize_payments` → URL de consentimiento |
| 28 | `feat(payments): add direct charge endpoint without retries` | `POST /v2/charge`; Javadoc con advertencia financiera; test explícito: 5xx/429/timeout → **una sola** petición |

**DoD M4:** 8 operaciones tipadas, rutas y métodos verificados por test, `BigDecimal` exacto en serialización (`25.00` no se convierte en `25.0`).

---

### M5 — API asíncrona (rama `feat/m5-async`)

| # | Commit | Contenido |
|---|---|---|
| 29 | `feat(core): add async client view returning CompletableFuture` | `client.async().invoices().create(...)` reutilizando el mismo transporte/config; errores como `CompletionException` con causa tipada |
| 30 | `test(core): verify sync and async parity for merchant operations` | tests parametrizados |

---

### M6 — Webhooks (rama `feat/m6-webhooks`)

| # | Commit | Contenido |
|---|---|---|
| 31 | `feat(webhook): add HMAC-SHA256 signature verification on raw bytes` | `Webhooks.verify(byte[], String, String)`; formato `sha256=<64 hex>`, `MessageDigest.isEqual` |
| 32 | `feat(webhook): add optional timestamp tolerance with injectable clock` | ventana ±300 s por defecto, `Clock` inyectable, Javadoc: no es protección anti-replay |
| 33 | `feat(webhook): add verified invoice webhook parsing` | `InvoiceWebhookEvent` (`transaction_uuid`, amount string→BigDecimal, `signed`), `VerifiedWebhook<T>`; parseo solo tras firma válida |
| 34 | `feat(webhook): add topup webhook model and unsigned rejection` | `TopUpWebhookEvent` (`uuid`, `is_partial`, `requested_usd`, `received_crypto`, `coin`, `tx_id`, `paid_at`); sin firma → rechazo por defecto |
| 35 | `test(webhook): cover tampered, reserialized, malformed and expired signatures` | matriz completa del checklist §9 |

---

### M7 — Hardening (rama `test/m7-hardening`)

| # | Commit | Contenido |
|---|---|---|
| 36 | `test(core): ensure secrets never appear in toString, logs or exceptions` | inspección de mensajes/`toString` de todos los tipos públicos |
| 37 | `test(core): verify isolation between clients with different credentials` | concurrencia con 2 apps |
| 38 | `test(http): cover URL encoding, timeouts and interruption` | |
| 39 | `test(http): verify Retry-After handling on opt-in reads` | |
| 40 | `test(json): verify coexistence with Jackson 2 on the classpath` | test con `com.fasterxml.jackson.core:jackson-databind` 2.x en scope test |
| 41 | `build: raise jacoco coverage threshold` | p. ej. 85 % líneas en paquetes públicos |

---

### M8 — Ejemplos consumidores (rama `feat/m8-examples`)

Los ejemplos **no son módulos del reactor**: dependen del SDK instalado con `./mvnw install`.

| # | Commit | Contenido |
|---|---|---|
| 42 | `feat(examples): add plain Java Maven consumer with mock server` | `examples/plain-java/`: crea cliente, factura simulada contra `HttpServer` local, verifica webhook firmado; tests propios. Contiene **literalmente** el quick start del README |
| 43 | `feat(examples): add Gradle consumers with Groovy and Kotlin DSL` | `examples/gradle-java/` con wrapper 9.8.2, toolchain 17, `mavenLocal()` solo para pre-release (comentado lo de `mavenCentral()`) |
| 44 | `feat(examples): add framework-neutral webhook receiver example` | `examples/webhook/` con `com.sun.net.httpserver`, dedupe en memoria y advertencias |

---

### M9 — Documentación pública (rama `docs/m9-docs`)

| # | Commit | Contenido |
|---|---|---|
| 45 | `docs: add security, troubleshooting and release guides` | `docs/security.md`, `docs/troubleshooting.md`, `docs/release.md` (10 pasos de `CLAUDE.md §11.6`) |
| 46 | `docs(readme): add English README` | estructura obligatoria de 12 secciones de `CLAUDE.md §10`, Mermaid del flujo, `Status: pre-release`, sin badges falsos |
| 47 | `docs(readme): add Spanish README` | `README.es.md` coherente |
| 48 | `docs: add contributing guide, code of conduct and security policy` | `CONTRIBUTING.md` (incluye esta convención de commits), `CODE_OF_CONDUCT.md`, `SECURITY.md` |
| 49 | `chore(repo): add issue and pull request templates` | `.github/ISSUE_TEMPLATE/*`, `pull_request_template.md` |
| 50 | `docs: add changelog for 0.1.0` | consolidar `[Unreleased]` |

---

### M10 — CI (rama `ci/m10-workflows`)

| # | Commit | Contenido |
|---|---|---|
| 51 | `ci: add build workflow with Java 17 and 21 matrix` | `ci.yml`: temurin, cache Maven, `./mvnw -B -ntp clean verify`; Java 25 `continue-on-error` |
| 52 | `ci: build example consumers against the locally installed SDK` | job que hace `install` y compila/testea `examples/*` (Maven y Gradle) con 17 y 21 |
| 53 | `ci: add secret scanning and dependency review` | gitleaks, `dependency-review-action` en PR |
| 54 | `ci: add Dependabot for Maven, Gradle and GitHub Actions` | sin auto-merge |
| 55 | `test: verify README snippets compile` | script/test que extrae los bloques ` ```java ` marcados del README y los compara con `examples/plain-java` |

---

### M11 — Preparación Maven Central (rama `release/m11-central-ready`)

| # | Commit | Contenido |
|---|---|---|
| 56 | `build(release): complete POM metadata for Maven Central` | `name`, `description`, `url`, `licenses`, `developers`, `scm`, `issueManagement` reales (placeholders solo si M0 sigue pendiente, listados en `HUMAN SETUP REQUIRED`) |
| 57 | `build(release): add central profile with GPG signing and publishing plugin` | perfil `central`: `maven-gpg-plugin` (loopback pinentry), `central-publishing-maven-plugin` 0.12.0 con `autoPublish=false`; `verify` normal no pide GPG |
| 58 | `build(release): support local bundle generation without upload` | propiedad `skipUpload` documentada; inspección de `.jar`, `-sources.jar`, `-javadoc.jar`, `.pom`, `.asc`, checksums |
| 59 | `ci(release): add manual release workflow behind protected environment` | `release.yml`: solo `workflow_dispatch`, environment `maven-central`, verifica versión sin `-SNAPSHOT` y tag, `settings.xml` temporal con secrets, no imprime secretos |
| 60 | `docs(release): document human setup checklist` | sección `HUMAN SETUP REQUIRED` en `docs/release.md` |

**Validación local de M11 (sin red a Central):**

```bash
./mvnw -B -ntp clean verify                       # JDK 17
./mvnw -B -ntp clean verify                       # JDK 21
./mvnw -B -ntp -Pcentral -DskipUpload=true clean deploy   # genera bundle firmado localmente (clave de prueba)
./mvnw -B -ntp install && (cd examples/plain-java && ../../mvnw -B verify)
(cd examples/gradle-java && ./gradlew build)
```

---

### M12 — Release 0.1.0 (gate humano, rama `release/0.1.0`)

Solo con **aprobación explícita del mantenedor**.

| # | Commit / acción | Contenido |
|---|---|---|
| 61 | `chore(release): prepare 0.1.0` | versión `0.1.0`, fecha en CHANGELOG |
| — | tag `v0.1.0` (anotado) | no publica nada por sí solo |
| — | `release.yml` manual → upload al Portal | revisar deployment en central.sonatype.com y **publicar manualmente** |
| — | verificación | consumidores Maven y Gradle limpios resuelven desde `mavenCentral()` |
| 62 | `docs(readme): add Maven Central coordinates and badges` | solo tras ver el artefacto en Central |
| 63 | `chore(release): start 0.2.0-SNAPSHOT` | |

**HUMAN SETUP REQUIRED:** owner GitHub y repo público, namespace verificado en Central Portal, datos de mantenedor y licencia final, Portal User Token, clave GPG (privada fuera del repo, pública en keyserver), secrets de GitHub (`CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`), environment `maven-central` con reviewers, App QvaPay en Modo prueba para la validación manual de integración.

---

## 3. Decisiones de arquitectura a fijar en M2

| Tema | Decisión propuesta |
|---|---|
| Package | `io.github.<owner>.qvapay` con subpaquetes `auth`, `http`, `api`, `model`, `exception`, `webhook`, `internal` |
| DTOs | `record` Java 17 para respuestas; clases finales con builder para requests que validan o tienen tri-estado |
| Jackson | Solo en `internal`; ningún tipo Jackson en firmas públicas; anotaciones `com.fasterxml.jackson.annotation` en records (o mixins si se quiere ocultar también eso) |
| Excepciones | Unchecked, con `statusCode()`, `errorCode()`, `rawErrorMessage()`; nunca incluyen headers de request |
| Auth por operación | Cada operación declara `AuthPolicy`; el transporte añade solo los headers correspondientes |
| Retries | Off por defecto; opt-in solo para operaciones marcadas `safeToRetry`; `charge`, `create_invoice`, `modify_invoice`, `authorize_payments` nunca |
| Async | Vista `client.async()` sobre `HttpClient.sendAsync` |
| Tri-estado | Tipo interno `Optional`-like (`Absent / Null / Value`) serializado construyendo el JSON explícitamente |
| Logging | Ninguna dependencia de logging en el core (`System.Logger` solo si se justifica, sin cuerpos ni headers) |
| Dependencias runtime | Únicamente `jackson-databind` 3.x (+ sus transitivas) |

## 4. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Versiones del stack inexistentes o incompatibles | Verificación en M0 antes del primer `pom.xml`; nunca degradar en silencio |
| Docs de QvaPay ambiguas (errores, paginación, idempotencia) | `TODO: VERIFY WITH QVAPAY` + `docs/api-coverage.md`; interfaces extensibles |
| Copiar código GPL del plugin WooCommerce | Solo lectura funcional; implementación original desde la doc pública |
| Snippets del README que no compilan | Commit 55: test que vincula README ↔ `examples/plain-java` |
| Publicación accidental | `autoPublish=false`, workflow manual con environment protegido, tags inertes |
| Fugas de secretos | Redacción en `toString`, tests dedicados, gitleaks local y en CI |

## 5. Después de 0.1.0 (resumen)

- **0.2.0 — Fase 2:** `POST /auth/login` (`LoginChallenge`/`LoginSuccess`), `GET /user`, `/coins*`, payment links. Cada endpoint: verificación → fixture → commit atómico.
- **0.3.0 — Fase 3:** P2P lectura (`GET /p2p`, `GET /p2p/{uuid}`) con paginación Laravel; operaciones con escrow solo tras estudio.
- **Fase 4:** operaciones sensibles opt-in (retiros con `idempotency_key`, transferencias), sin atestaciones por defecto.
- Activar `japicmp` contra la versión publicada anterior a partir de 0.2.0.
