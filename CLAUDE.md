# CLAUDE.md — QvaPay Java SDK

> **Project mission:** build, document, test and prepare a polished, production-quality, framework-agnostic Java SDK that can be consumed as a normal dependency from Maven Central.

> Instrucciones operativas para Claude Code (o cualquier agente de desarrollo) que implemente un SDK **Java, no oficial**, para la API REST de **QvaPay**.
>
> **Referencia investigada:** 22 de octubre de 2026. **Idioma del repositorio:** documentación y ejemplos en inglés; este archivo de instrucciones está en español. **Fuente de verdad:** documentación pública de QvaPay, no el SDK antiguo de Node.

## 1. Objetivo

Construir y mantener una librería Java moderna, segura, pequeña y tipada que encapsule la API de QvaPay. El primer entregable debe soportar de extremo a extremo las **integraciones de comerciantes** (`/v2/*`), incluyendo facturas, consulta de pagos, cobros autorizados y validación de webhooks. Después se extenderá a autenticación de usuario, recursos de lectura y otras familias de endpoints.

El resultado debe poder utilizarse desde aplicaciones **Java 17+** (compilado con `--release 17` usando **JDK 17 LTS** como toolchain principal), Spring Boot, Quarkus, Micronaut, servicios backend y AWS Lambda compatibles con esa versión de runtime **sin obligar a usar ninguno de esos frameworks**. El SDK no debe realizar cobros reales durante el desarrollo ni depender de una conexión a QvaPay para ejecutar pruebas unitarias.

**No inventar endpoints, campos, firmas, idempotencia ni semánticas.** Cuando la documentación no resuelva algo, abrir un punto `TODO: VERIFY WITH QVAPAY` y diseñar la interfaz de manera extensible en lugar de asumir comportamientos.

### Principios no negociables

1. API Java idiomática, tipada, estable y documentada; usar `BigDecimal` para dinero y `Instant`/`OffsetDateTime` para fechas UTC según corresponda.
2. Autenticación de **app** y de **usuario** son mecanismos distintos; no intercambiarlos ni incluir credenciales en cuerpos JSON salvo contrato confirmado.
3. Seguridad por defecto: HTTPS, secretos fuera del código, sin credenciales en logs ni excepciones, firmas de webhook verificadas sobre bytes originales.
4. No reintentar automáticamente operaciones que puedan mover dinero o crear recursos, salvo que una clave de idempotencia documentada garantice el comportamiento y se haya probado.
5. Los ejemplos y pruebas no deben utilizar fondos reales.
6. Preservar los nombres y tipos del JSON real por endpoint, sin imponer un wrapper universal `data`.
7. Publicar como SDK **comunitario/no oficial**, sin afirmar afiliación con QvaPay.

## 2. Fuentes de verdad y orden de precedencia

Consultar estas URL antes de escribir una implementación y volver a consultarlas cuando se actualice el SDK:

- Visión general, base URL, autorización, límites, errores y cumplimiento: https://www.qvapay.com/docs
- Documentación de integraciones/SDKs: https://www.qvapay.com/docs/integrations
- Merchant — app: https://www.qvapay.com/docs/merchants/info
- Merchant — balance: https://www.qvapay.com/docs/merchants/balance
- Merchant — crear factura: https://www.qvapay.com/docs/merchants/create-invoice
- Merchant — modificar factura: https://www.qvapay.com/docs/merchants/modify-invoice
- Merchant — transacciones: https://www.qvapay.com/docs/merchants/transactions
- Merchant — detalle: https://www.qvapay.com/docs/merchants/transaction-detail
- Merchant — autorizar cobros: https://www.qvapay.com/docs/merchants/authorize-payments
- Merchant — cobrar: https://www.qvapay.com/docs/merchants/charge
- Merchant — webhook de topups: https://www.qvapay.com/docs/merchants/topup-webhook
- Webhook de factura + pruebas sin dinero: https://www.qvapay.com/docs/miniapps/payments
- Mini-app quickstart: https://www.qvapay.com/docs/miniapps/quickstart
- Login con 2FA: https://www.qvapay.com/docs/auth/login
- Tokens de API de permisos limitados: https://www.qvapay.com/docs/auth/tokens
- Coins: https://www.qvapay.com/docs/coins/list
- Mercado P2P: https://www.qvapay.com/docs/p2p/list
- P2P con credenciales de app: https://www.qvapay.com/docs/p2p/app-credentials
- Enlaces de pago: https://www.qvapay.com/docs/payment-links
- Reglas sobre retiros/cumplimiento/idempotencia: https://www.qvapay.com/docs/withdraw/list
- SDK Python comunitario (principal inspiración de arquitectura y ergonomía): https://github.com/ragnarok22/qvapay-python
- Plugin oficial QvaPay WooCommerce (referencia de v2, payments y webhook): https://github.com/qvapay/qvapay-woocommerce
- SDK TypeScript/Node histórico (inventario de funcionalidades, puede estar desactualizado): https://github.com/qvapay/client-node
- CLI oficial QvaPay (flujos de usuario, seguridad y experiencias de desarrollador): https://github.com/qvapay/qvapay-cli
- Requisitos de publicación Maven Central: https://central.sonatype.org/publish/requirements/
- Maven Central Publisher Plugin (Portal actual): https://central.sonatype.org/publish/publish-portal-maven/
- Namespaces: https://central.sonatype.org/register/namespace/
- Java 17 LTS / especificación oficial: https://openjdk.org/projects/jdk/17/
- Maven GA: https://maven.apache.org/download.cgi
- Gradle 9.8.2: https://docs.gradle.org/9.8.2/release-notes.html
- JUnit releases: https://github.com/junit-team/junit-framework/releases
- Jackson 3 releases: https://central.sonatype.com/artifact/tools.jackson.core/jackson-databind
- Compiler Plugin GA: https://github.com/apache/maven-compiler-plugin/releases
- Surefire GA: https://central.sonatype.com/artifact/org.apache.maven.plugins/maven-surefire-plugin
- Central Publishing Plugin exact version: https://central.sonatype.com/artifact/org.sonatype.central/central-publishing-maven-plugin

Orden de prioridad para **comportamiento de red**: **documentación oficial vigente del endpoint > código del plugin oficial vigente si concuerda con las docs > fixtures/validación controlada > implementaciones no oficiales e históricas > hipótesis**. Para **diseño Java**, Python puede inspirar las capas, pero nunca es fuente normativa de URLs. Registrar en `docs/api-coverage.md` la URL y fecha de verificación por endpoint. Si una fuente difiere, documentar la discrepancia.

### 2.1 Investigación OBLIGATORIA de repositorios de referencia

Antes de crear clases Java, leer la documentación, el README y el código relevante de estos **cuatro** repositorios (no solo sus portadas). Elaborar `docs/references.md` con sus hallazgos y un cuadro "Inspiración / Qué no copiar / Qué está confirmado en la API":

| Prioridad | Referencia | Qué estudiar y adoptar en Java | Restricción |
|---|---|---|---|
| 1 · Diseño | **[ragnarok22/qvapay-python](https://github.com/ragnarok22/qvapay-python)**, SDK comunitario | `README.md`, clientes `SyncQvaPayClient` / `AsyncQvaPayClient`, separación `User` vs `Merchant`, módulos/servicios, DTOs, errores, tests, `docs/`, `CHANGELOG`, `CONTRIBUTING` y API fácil de aprender. Replicar ideas idiomáticamente con `CompletableFuture` y servicios Java. | Es **no oficial**. Algunas operaciones pueden usar endpoints anteriores. Licencia reportada MIT; verificar archivo `LICENSE` y conservar atribuciones si se reutiliza material sujeto a licencia. |
| 2 · Compatibilidad de pagos | **[qvapay/qvapay-woocommerce](https://github.com/qvapay/qvapay-woocommerce)**, plugin oficial | `README.md`, cliente HTTP merchant, `POST /v2/create_invoice`, estados, manejo de checkout/redirect, seguridad del webhook HMAC-SHA256, timestamps, anti-replay, idempotencia y tests si existen. | Es un **plugin PHP** y tiene licencia **GPL-2.0**. Examinar funcionalidad pero **NO copiar/traducir código GPL** a un SDK MIT/Apache sin asesoramiento y cumplimiento de licencias. Sus rutas deben contrastarse con las docs actuales. |
| 3 · Cobertura de módulos | **[qvapay/client-node](https://github.com/qvapay/client-node)**, SDK Node histórico | `README.md`, estructura `src/api`, `src/helpers`, `src/interfaces`, métodos auth, usuario, transacciones, pagos y sus ejemplos. Usarlo para crear un checklist de funciones y descubrir casos límite. | No asumir compatibilidad con `v2` ni condiciones de licencia: verificar el repositorio, no copiar código no licenciado. |
| 4 · Experiencia de usuario y flujos | **[qvapay/qvapay-cli](https://github.com/qvapay/qvapay-cli)**, CLI oficial | `README.md`, flujo login con PIN, balances, tx list/get, transferencias con intervención del usuario, cuidados de logging, limitación de operaciones, mensajes claros y experiencia de documentación. | **No** copiar CLI, interfaz TUI, almacenamiento de credenciales, automatización de pagos ni política local; un SDK es una biblioteca, no un ejecutable. |

**Procedimiento de estudio:** leer los README completos; revisar estructura, código, LICENSE, últimos cambios y ejemplos; extraer un mapa de posibles funcionalidades; comparar una por una con https://www.qvapay.com/docs; guardar diferencias y decisiones en `docs/references.md` y `docs/api-coverage.md`. No prometer en Java todas las funcionalidades que aparezcan en un SDK antiguo.

**Inspiración visual del README:** revisar especialmente el `README.md` de Python para estructura, ejemplos sync/async y documentación para contribuidores; el de WooCommerce para el diagrama de pago y las advertencias de seguridad; el del CLI para los ejemplos claros de terminal. Crear una documentación **original, moderna y en inglés**; no reutilizar imágenes protegidas, el logo oficial ni texto extenso de terceros sin permisos.

## 3. Base URL y autenticación

**Base URL de producción documentada:** `https://api.qvapay.com` (sin `/v2` global automático: hay rutas `/v2/*`, `/auth/*`, `/user/*`, `/coins`, `/p2p/*`, etc.). No asumir que existe un hostname de sandbox independiente. El **Modo prueba** se activa en la App de QvaPay, y el simulador de mini-apps está documentado.

### A. Credenciales de aplicación — comerciantes

En cada request correspondiente, enviar:

```http
Content-Type: application/json
Accept: application/json
app-id: <APP_UUID>
app-secret: <APP_SECRET>
```

Aplica a `/v2/info`, `/v2/balance`, facturas, transacciones, cobros y autorización de cobros. El `app-secret` se muestra una vez al crear la aplicación; nunca se debe loguear ni incrustar en el binario. Guardar en variables de entorno, un secret manager o un proveedor de credenciales inyectable.

### B. Token de usuario

En endpoints de usuario:

```http
Authorization: Bearer <TOKEN>
Accept: application/json
```

`POST /auth/login` es un flujo interactivo de dos pasos: primero email/contraseña, normalmente respuesta **202** indicando 2FA; luego la misma petición con `two_factor_code` (PIN u OTP) y respuesta **200** con `accessToken`. Los tokens de sesión por defecto duran 2 horas, o hasta 180 días con `remember`. **No hacer auto-login con contraseña para procesos de backend.**

QvaPay permite generar desde su web tokens de API con permisos específicos (`read`, `p2p:read`, `p2p:buy`, `p2p:sell`, `store`, `transfer`, `withdraw`). **No existe en la documentación un endpoint para crear esos tokens**: se obtienen en el panel y se pasan al SDK. Exponer `BearerTokenProvider`, no almacenar ni renovar tokens automáticamente sin contrato de renovación confirmado.

### C. Autenticación por operación

- Hay endpoints públicos (`GET /coins`), otros solo con Bearer y algunos que admiten Bearer **o** credenciales de App (`GET /p2p` si la app tiene permisos y cumple requisitos).
- Usar una política explícita de autenticación por operación (e.g. `APP`, `BEARER`, `PUBLIC` o `APP_OR_BEARER`).
- Evitar enviar simultáneamente ambos métodos salvo requerimiento comprobado: cuando ambos son aceptados, la documentación da prioridad a las credenciales de app.
- Las credenciales de app P2P operan en nombre del **propietario de la app**, con permisos específicos de compra/venta habilitados en el panel. No eludir KYC ni limitaciones.

## 4. Contrato VERIFICADO de comerciantes (fase MVP)

Todas las rutas siguientes están documentadas con **autenticación de app en cabeceras**.

| Operación Java propuesta | Método y ruta REST | Entrada principal | Salida documentada |
| --- | --- | --- | --- |
| `merchant().info()` | `POST /v2/info` | Sin body obligatorio | Objeto directo de app (`uuid`, `name`, `active`, `enabled`, `callback`, etc.) |
| `merchant().balance()` | `POST /v2/balance` | Sin body obligatorio | `{ "balance": 150.75 }` (USD) |
| `invoices().create(request)` | `POST /v2/create_invoice` | `amount`, `description`, `remote_id`, opcionales | Objeto de factura con `transaction_uuid`, `url`, etc. |
| `invoices().modify(request)` | `POST /v2/modify_invoice` | `transaction_uuid` y campos modificados | Objeto actualizado de factura |
| `transactions().list(page, take)` | `POST /v2/transactions` | `page`, `take` en **body JSON** | `{ "message": "APP Transactions", "transactions": [...] }` |
| `transactions().get(uuid)` | `POST /v2/transactions/{uuid}` | UUID en la ruta | `{ "message": "APP Transaction", "transaction": {...} }` |
| `payments().authorize(request)` | `POST /v2/authorize_payments` | `remote_id`, `callback` | `{ "message": "Authorize payments", "url": "..." }` |
| `payments().charge(request)` | `POST /v2/charge` | `amount`, `user_uuid`, `description`, `remote_id` | `{ "success": true, "message": "...", "transaction": {...} }` |

### Detalles de las solicitudes y tipos

**Crear factura** (`POST /v2/create_invoice`):

```json
{
  "amount": 25.00,
  "description": "Compra de producto digital",
  "remote_id": "order-12345",
  "webhook": "https://example.com/webhooks/qvapay",
  "products": [
    { "name": "Producto A", "price": 15.00, "quantity": 1 },
    { "name": "Producto B", "price": 10.00, "quantity": 1 }
  ],
  "expire_at": "2026-12-31T23:59:59.000Z"
}
```

- Requeridos: `amount` (> 0 y <= 100000), `description` (1–255 caracteres), `remote_id` (1–255 caracteres).
- Opcionales: `webhook` (URL válida), `products` (productos `name`, `price`, `quantity`, esta última por defecto 1), `expire_at` (ISO 8601 futuro).
- Respuesta **directa**, no dentro de `data`: `app_id`, `amount`, `description`, `remote_id`, `transaction_uuid`, `expire_at` nullable, `url`.
- `url` es la URL de pago devuelta por QvaPay; **no** construirla manualmente si ya se recibe.
- No agregar el campo `signed` solo porque aparece en un SDK histórico: no figura entre los parámetros de creación actuales documentados.

**Modificar factura** (`POST /v2/modify_invoice`): requiere `transaction_uuid`; permite modificar `amount`, `description`, `remote_id`, `webhook`, `products`, `expire_at`. Solo se modifica si está `pending`. En la documentación, `expire_at: null` restablece a 30 días: el modelo debe distinguir entre **campo ausente** y **campo explícitamente nulo** (no perder la semántica del PATCH lógico aunque el método sea POST).

**Listar transacciones**: `page >= 1`, `take` por defecto 30, máximo 100. Estados documentados `pending`, `paid`, `cancelled`. No asumir que es GET. La lista devuelve `transactions`, no una página Laravel genérica. **El documento no muestra metadatos de paginación en la respuesta**; no inventarlos.

**Detalle de transacción**: `POST /v2/transactions/{uuid}`. La respuesta incluye opcionalmente `PaidBy`, con `uuid`, `name`, `username`, `lastname`, `image`, `vip`, `kyc`, `golden_check`. Mantener `@JsonProperty("PaidBy")` o política equivalente; no cambiar su capitalización en el wire format.

**Autorizar cobros**: `remote_id` y `callback` obligatorios; la respuesta es una URL que se abre para que el usuario conceda permiso. La app necesita `allowed_payment_auth`; el SDK nunca debe simular el consentimiento.

**Cobro directo**: requiere autorización previa de ese usuario y permiso `allowed_payment_auth` en la app; `POST /v2/charge` mueve saldo. La documentación describe una comisión de procesamiento del 0.5% deducida del pagador (exención para plan GOLD). **No reintentar automáticamente** ni asumir idempotencia a partir de `remote_id` sin prueba/contrato oficial.

### Modelos recomendados del MVP

- `AppInfo`, `AppBalance`
- `CreateInvoiceRequest`, `ModifyInvoiceRequest`, `InvoiceProduct`, `Invoice`
- `AppTransaction`, `AppTransactionPageResponse`, `AppTransactionDetailResponse`, `PayerInfo`, `TransactionStatus`
- `AuthorizePaymentsRequest`, `AuthorizePaymentsResponse`, `ChargeRequest`, `ChargeResponse`
- `QvaPayApiError`, `QvaPayException` y subclases.

**Dinero:** `BigDecimal` tanto si JSON trae número como string decimal. No usar `double` para cálculos. No redondear silenciosamente; validar importes mínimos y máximos específicos, pero no imponer decimales que no estén documentados. **Fechas:** ISO 8601 a `Instant` cuando corresponda; valores anulables soportados.

## 5. Webhooks (parte obligatoria del MVP)

### Facturas pagadas

Fuente: https://www.qvapay.com/docs/miniapps/payments

QvaPay envía un `POST` al `webhook` de la factura, o al callback de la aplicación cuando no se especifica. Ejemplo de **payload de factura**:

```json
{
  "transaction_uuid": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "remote_id": "order-12345",
  "status": "paid",
  "amount": "5.00",
  "signed": 0,
  "created_at": "2026-10-05T12:00:00.000Z",
  "updated_at": "2026-10-05T12:00:30.000Z"
}
```

Cabeceras: `x-qvapay-signature: sha256=<hex>` y `x-qvapay-timestamp: <segundos Unix>`. Firma = **HMAC-SHA256 sobre los bytes crudos del body, llave = `app-secret`**, salida hexadecimal con prefijo `sha256=`. El timestamp no forma parte del material firmado según el ejemplo documentado; por eso **la deduplicación y/o comprobación del estado por API es necesaria** además de la ventana temporal.

Exponer un helper de framework neutral, por ejemplo:

```java
boolean valid = Webhooks.verify(
    rawBodyBytes,
    signatureHeader,
    appSecret
);
```

Requisitos del helper:

1. Usar `Mac.getInstance("HmacSHA256")` y `SecretKeySpec` de Java.
2. Firmar los **bytes exactos** del body, sin parsear ni reserializar antes.
3. Validar formato `sha256=<64 hex chars>` y comparación en tiempo constante (`MessageDigest.isEqual` sobre bytes decodificados), rechazando firmas ausentes, malformadas o distintas.
4. Ofrecer validación opcional de timestamp con `Clock` inyectable y ventana configurable (por defecto ±300 s, según plugin oficial); documentar que el timestamp por sí solo no es garantía criptográfica contra replay.
5. Devolver un resultado verificable (`VerifiedWebhook<T>` o similar) y parsar el payload **solo después** de verificar la firma.
6. El servidor del integrador debe hacer **idempotente** el procesamiento usando `transaction_uuid` y verificar `remote_id`, importe, estado y propiedad de la transacción contra su pedido local. Si hay dudas, consultar `/v2/transactions/{uuid}` antes de entregar bienes/servicios.
7. Responder `2xx` rápidamente **después** de guardar el evento en cola/almacenamiento durable; no es responsabilidad del SDK levantar un servidor HTTP.

**Reintentos de entrega documentados:** hasta tres intentos, aproximadamente en `0s`, `5s` y `30s` si recibe `429`, `5xx`, timeout o error de red. Los `3xx` y otros `4xx` no disparan reintento. No depender de que el evento llegue una sola vez ni de que QvaPay siga intentándolo indefinidamente.

### TopUp webhook — distinto contrato de evento

Fuente: https://www.qvapay.com/docs/merchants/topup-webhook

Este webhook usa `uuid` (no `transaction_uuid`) y trae `status: paid`, `is_partial`, `amount` **string** USD acreditado, `requested_usd`, `received_crypto` **string**, `coin`, `tx_id`, `paid_at`. Si el topup se creó con credenciales de app, tiene firma HMAC con el mismo esquema. Si fue creado con Bearer de usuario, QvaPay advierte que puede llegar **sin firma**: para integraciones merchant-to-merchant, crear con credenciales de app y **rechazar webhooks sin firma**. No tratar topups y pagos de factura como el mismo DTO.

## 6. Alcance incremental: después del MVP

No intentar implementar toda la plataforma en una sola iteración. La documentación incluye numerosas familias y distintos modelos de permisos. Crear el inventario de rutas en `docs/api-coverage.md` y clasificar cada una (`verified`, `implemented`, `tested`, `blocked`, `undocumented`).

### Fase 1 — Merchant (release `0.1.0`)

- [ ] Cliente HTTP, configuración, autenticación de app, serialización, errores, test fixtures.
- [ ] Los 8 endpoints de la tabla de merchant.
- [ ] Verificación de webhook de factura y topup, modelos diferenciados y pruebas de firma.
- [ ] README, ejemplo de aplicación, Javadoc, CI, comprobación de secretos y validación con mocks.

### Fase 2 — Auth y recursos de lectura

- [ ] `POST /auth/login`: respuesta `202` de 2FA versus `200` con token (modelarlo como `LoginChallenge` / `LoginSuccess`, no usar excepciones para una respuesta normal 202).
- [ ] Tokens Bearer limitados proporcionados por el usuario: el SDK no los crea.
- [ ] `GET /user` — perfil. Fuente: https://www.qvapay.com/docs/user/me
- [ ] `GET /coins` — listado público de categorías/monedas. Algunos campos monetarios/precios/comisiones son **strings**; los IDs pueden llegar como strings.
- [ ] `GET /coins/{id}` — detalle (documentado con Bearer): https://www.qvapay.com/docs/coins/detail
- [ ] `GET /coins/price-history/{tick}` — historial público: https://www.qvapay.com/docs/coins/price-history
- [ ] Enlaces de pago de usuario (Bearer): `GET /user/payment-links`, `POST /user/payment-links`, `DELETE /user/payment-links` (DELETE envía body `{ "id": "..." }`). Fuente: https://www.qvapay.com/docs/payment-links
- [ ] Explorar `App`, `Transactions`, `Stocks`, `Saving` y demás categorías, pero agregar endpoints solo después de capturar su contrato individual.

### Fase 3 — P2P

- [ ] `GET /p2p`: listado con filtros `page`, `take`, `type`, `coin`, `orderBy`, `orderType`, `my`, etc. Paginación **estilo Laravel** (`current_page`, `data`, `per_page`, `total`) y montos frecuentemente serializados como strings. Fuente: https://www.qvapay.com/docs/p2p/list
- [ ] `GET /p2p/{uuid}`: detalle de oferta. Fuente: https://www.qvapay.com/docs/p2p/detail
- [ ] Solo tras estudiar permisos y documentación: creación, aplicación, marcar pagado, liberar, cancelar, chat, feed por webhook/stream. Fuente: https://www.qvapay.com/docs/p2p/app-credentials
- [ ] Usar `p2p:read` o credenciales app habilitadas; distinguir `p2p:buy` de `p2p:sell`. Las rutas P2P **no** llevan necesariamente prefijo `/v2`.
- [ ] No implementar automáticamente operaciones con escrow o liberación irreversible.

### Fase 4 — Operaciones sensibles (opt-in explícito)

- [ ] Depósitos/topups, transferencias, retiros y compras en Store **solo** cuando sus contratos de auth, validación, compliance, prevención de duplicados y pruebas estén confirmados.
- [ ] Para retiros, la documentación contempla `idempotency_key` en el body (8–64 caracteres, carácter `[A-Za-z0-9._-]`, deduplicación documentada por 24h), **pero no extender esta garantía a otros endpoints**.
- [ ] Manejar KYC, sanciones, requisitos de Travel Rule y códigos de error estables. Fuente: https://www.qvapay.com/docs/withdraw/list
- [ ] No completar automáticamente atestaciones legales por el usuario; exigir sus selecciones expresas y nunca incluir un `compliance` afirmativo predeterminado.
- [ ] Cada método que mueva dinero debe ser claramente nombrado, documentado y cubierto con pruebas de errores e idempotencia antes de liberarse.

**Fuera de alcance del núcleo Java:** implementar un equivalente a `window.QvaPay` (es un puente JavaScript dentro de mini-apps). Sí se puede añadir luego, como paquete utilitario independiente, la verificación *backend* de `init_data` si su algoritmo oficial está documentado: https://www.qvapay.com/docs/miniapps/quickstart

## 7. Arquitectura y API pública recomendadas

Stack:

Stack **actualizado y verificado al 22 de octubre de 2026**. La API de QvaPay sigue siendo **v2**, y la versión inicial del SDK sigue siendo **0.1.0**; cambiar el JDK/build no cambia automáticamente la versión funcional ni la API REST.

| Componente | Versión objetivo | Política |
| --- | --- | --- |
| JDK del mantenedor / CI principal | **17 LTS** | JDK base para desarrollo, compilación, pruebas y publicación |
| Java mínimo de la librería | **17 LTS** | Compilar con `--release 17` para facilitar adopción desde aplicaciones modernas y existentes |
| Java adicional para compatibilidad | **21 LTS y 25 LTS (opcional)** | Probar que el JAR compilado para 17 sigue funcionando en JDK superiores |
| Apache Maven + Maven Wrapper | **3.10.0** | GA estable, no Maven 4 RC en publishing |
| Apache Maven Compiler Plugin | **3.16.0** | Configurar `release=17` |
| Maven Surefire | **3.6.0** | Ejecutar JUnit 6 |
| JUnit Jupiter | **6.1.3** | Dependencia de tests, no runtime |
| Jackson Databind | **3.2.4** | `tools.jackson.core:jackson-databind`; importación vía BOM y revisar anotaciones |
| Gradle (ejemplos consumidores) | **9.8.2** | Gradle wrapper solo en ejemplos; no dependencia de core |
| Sonatype Central Publishing Plugin | **0.12.0** | Compatible Maven 3.9/3.10; activación solo por perfil de release |

**Regla de mantenimiento:** fijar versiones exactas en `pom.xml`/wrappers, verificar que existen en repositorios oficiales y que los plugins son compatibles; no degradar silenciosamente ni utilizar builds RC/SNAPSHOT. Usar Dependabot/Renovate para proponer actualizaciones probadas, nunca publicar automáticamente cambios de dependencias.

**Decisión de compatibilidad:** **Java 17 LTS es tanto el JDK principal de desarrollo como el mínimo de ejecución**. Compilar y publicar con `--release 17` para impedir la incorporación accidental de APIs posteriores. Java 21 y Java 25 solo sirven como verificaciones adicionales de compatibilidad; no elevar el mínimo sin una decisión explícita y actualización de README, CI y POM.

- **JDK 17 LTS** como plataforma principal de desarrollo/build y **Java 17 LTS como mínimo de los consumidores**. Compilar con `maven.compiler.release=17` y probar bajo **Java 17 y 21** (Java 25 opcional, no bloqueante). Se admiten `record`, `sealed interface`, `instanceof` pattern matching, switch expressions y APIs estándar de Java 17; **no usar APIs ni características exclusivas de Java 18+**, ni funcionalidades preview/incubator en el core. Evitar record patterns, virtual threads y patrones de switch modernos no presentes en Java 17.
- **Apache Maven 3.10.0** estable para build y publicación; Maven Wrapper (`mvnw`) fijado y documentado. **No usar Maven 4 RC** en el release pipeline: el Central Publishing Plugin 0.12.0 documenta soporte Maven 3.9/3.10 y no Maven 4. Publicar un JAR ligero, sin imponer dependencias Spring.
- HTTP: `java.net.http.HttpClient` (JDK) con instancias reutilizables, timeouts configurables, y opción de inyectar un transporte HTTP para tests.
- JSON: **Jackson 3.2.4** (`tools.jackson.core:jackson-databind`) con versiones alineadas por el BOM oficial. No confundir imports `tools.jackson.*` de Jackson 3 con `com.fasterxml.jackson.*` de Jackson 2; `jackson-annotations` aún utiliza `com.fasterxml.jackson.annotation`. Conservar la implementación de serialización encapsulada para evitar introducir tipos Jackson en la API pública y permitir integraciones con proyectos que empleen Jackson 2. Usar un mapper inmutable/configurado una vez y seguro para concurrencia.
- Test: **JUnit Jupiter 6.1.3** (solo scope `test`), **Maven Surefire 3.6.0**, y un servidor HTTP simulado compatible con Java 17 (MockWebServer o WireMock, fijar versión GA verificada). No usar credenciales reales; tests de serialización con JSON fixtures.
- Quality: Maven Enforcer, Checkstyle/Spotless (escoger uno consistente), JaCoCo; CI en GitHub Actions.

Propuesta de layout (adaptar packages al groupId definitivo):

```text
qvapay-java/
├── CLAUDE.md
├── README.md                       # landing page profesional, ejemplos compilables
├── README.es.md                    # traducción en español del onboarding (sin desactualizar)
├── CHANGELOG.md
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── SECURITY.md
├── NOTICE                           # atribuciones solo si aplican
├── LICENSE
├── pom.xml
├── .github/workflows/
│   ├── ci.yml
│   └── release.yml                 # manual y protegido; NUNCA publica solo por push
├── .mvn/                           # solo si hace falta para build reproducible
├── docs/
│   ├── api-coverage.md
│   ├── references.md
│   ├── architecture.md
│   ├── security.md
│   ├── troubleshooting.md
│   └── release.md
├── examples/
│   ├── plain-java/                  # proyecto consumidor externo al módulo SDK
│   ├── spring-boot/                 # opcional, NO dependencia del core
│   └── webhook/                     # servidor de ejemplo, solo si compila
├── .env.example                    # placeholders; nunca credenciales reales
└── src/
    ├── main/java/io/github/USERNAME/qvapay/
    │   ├── QvaPayClient.java
    │   ├── QvaPayClientConfig.java
    │   ├── auth/
    │   │   ├── AppCredentials.java
    │   │   └── BearerTokenProvider.java
    │   ├── http/
    │   │   ├── HttpTransport.java
    │   │   ├── JdkHttpTransport.java
    │   │   └── ApiRequest.java
    │   ├── api/
    │   │   ├── MerchantApi.java
    │   │   ├── InvoiceApi.java
    │   │   ├── TransactionApi.java
    │   │   └── PaymentsApi.java
    │   ├── model/
    │   │   └── ...
    │   ├── exception/
    │   │   └── ...
    │   └── webhook/
    │       ├── Webhooks.java
    │       └── ...
    └── test/
        ├── java/.../
        └── resources/fixtures/
```

`USERNAME` es **placeholder**: sustituir antes de compilar por un package Java válido del propietario del repositorio. Establecer en `pom.xml` `groupId=io.github.<owner>` y `artifactId=qvapay-java` (o nombre definitivo acordado) antes de publicar. Evitar paquetes llamados `com.qvapay` sin permiso de la marca.

### Superficie pública ilustrativa (DISEÑO PROPUESTO, no SDK existente)

```java
QvaPayClient client = QvaPayClient.builder()
    .appCredentials(
        System.getenv("QVAPAY_APP_ID"),
        System.getenv("QVAPAY_APP_SECRET")
    )
    .connectTimeout(Duration.ofSeconds(5))
    .requestTimeout(Duration.ofSeconds(15))
    .build();

AppBalance balance = client.merchant().balance();

Invoice invoice = client.invoices().create(
    CreateInvoiceRequest.builder()
        .amount(new BigDecimal("25.00"))
        .description("Compra de producto digital")
        .remoteId("order-12345")
        .build()
);

System.out.println(invoice.url());

AppTransactionDetailResponse payment =
    client.transactions().get(invoice.transactionUuid());
```

El agente debe elegir y mantener una convención consistente: **records inmutables Java 17 cuando faciliten DTOs**, builders o clases finales cuando se necesiten validaciones, y Jackson 3 compatible; nombres Java camelCase y JSON snake_case mediante anotaciones o configuración. `Invoice`, `AppBalance`, etc. son nombres **propuestos**: desarrollar los tipos reales en el repositorio.

### Comportamiento HTTP

- `User-Agent: qvapay-java/<version>` y `Accept: application/json`; `Content-Type` cuando hay JSON.
- `baseUrl` personalizable **solo para tests/entornos controlados**, HTTPS requerido por defecto en uso real.
- No concatenar URLs de usuario sin encoding; encodear segmentos y query parameters correctamente.
- Respuestas `2xx` exitosas: aceptar objetos directos, arrays y wrappers por contrato. `202` del login no es un fallo.
- En error: conservar `status`, código programático (`code`), `message`/`error`, metadata no sensible y cabeceras de rate limiting cuando existan; no mostrar request headers con secretos.
- Excepciones tipadas: `AuthenticationException` (401), `PermissionDeniedException` (403), `ValidationException` (400), `NotFoundException` (404), `RateLimitException` (429), `ServerException` (5xx), `TransportException`, `SerializationException`. Mantener acceso al HTTP status y código original.
- Desconocidos de enums: parsing tolerante (`UNKNOWN` o conservar `String`), sin inventar estados.
- Timeouts y cancelación; la interrupción de Java debe preservarse.
- Retries: desactivados para `POST` de factura/charge y cualquier acción de dinero. Permitir como opt-in para lecturas seguras (incluidos POST *read-only* conocidos si se ha comprobado), con jitter exponencial y respeto por `Retry-After` cuando lo haya. Límite de intentos configurable y sensato.
- QvaPay documenta límites variables; orientativamente: merchant charge 5 solicitudes/20 s por app, endpoints generales 3/5 s, P2P 25/min/cuenta. El SDK debe manejar `429`, **no prometer** un rate limiter perfecto universal.
- Diseñar el cliente para concurrencia y no filtrar credenciales entre clientes configurados con distintas apps.

## 8. Seguridad, normativa y protección del dinero

- Los secretos van en variables de entorno/secret manager. `.env` real en `.gitignore`. Incluir solo `.env.example` sin valores.
- Nunca registrar `app-secret`, Bearer tokens, password, PIN, OTP, detalles bancarios ni cuerpos completos con PII.
- No usar `String.valueOf(secret)` en errores ni `toString()` generado que imprima secretos. Redactar campos sensibles.
- Rechazar TLS inseguro, `trustAllCertificates`, redirects arbitrarios que reenvíen cabeceras de credenciales a otros hosts.
- No enviar credenciales a URL de pago devuelta por QvaPay: esa URL es solo para el navegador del cliente.
- KYC y controles AML/OFAC son responsabilidad de QvaPay + integrador: exponer mensajes y errores, nunca crear bypasses.
- QvaPay documenta errores estructurados como `KYC_REQUIRED`, `INSUFFICIENT_SCOPE`, `US_PERSON_ATTESTATION_REQUIRED`, `W1_ATTESTATION_REQUIRED`, `CACR_ATTESTATION_REQUIRED`, `DUPLICATE_REQUEST`, entre otros. La lógica debe actuar sobre `code` (si está presente), **no** analizar el idioma del mensaje.
- Desde 29-sep-2026 QvaPay documenta atestaciones obligatorias, incluso en clientes API, para determinadas operaciones cuando la cuenta es considerada US person. No fabricar consentimiento (`attestations: true`) ni asumir que la exención de apps se aplica a todos.
- Algunos movimientos >= USD 3,000 requieren datos de remitente/beneficiario por Travel Rule. No ocultar rechazos ni inventar información.
- `remote_id` representa una referencia del comercio; salvo garantía oficial, **no equivale** a una clave de idempotencia para cobros.

## 9. Pruebas y criterios de aceptación

### Unitarias/contract tests, sin red real

- [ ] Headers exactos de auth de app y Bearer, sin fugas cruzadas.
- [ ] Ruta y método de cada endpoint merchant (especialmente `POST /v2/transactions/{uuid}`).
- [ ] Serializar `CreateInvoiceRequest` con `amount` exacto y opcionales correctos.
- [ ] Deserializar respuestas **directas** de info, balance y factura; wrappers distintos para lista/detalle/cargo.
- [ ] `PaidBy` con mayúsculas; importes como número y como string; `null` en expiración.
- [ ] Campo ausente vs `expire_at: null` al modificar factura.
- [ ] `200`, `202`, `400`, `401`, `403`, `404`, `429`, `500`; errores con `code` o solo `error`.
- [ ] No reintentar `POST /v2/charge` en timeout/5xx/429 por defecto.
- [ ] Respetar un `Retry-After` verificable en lecturas opt-in.
- [ ] Verificar firma HMAC válida, firma inválida, cuerpo reserializado, faltante, prefijo erróneo, hex inválido y timestamp fuera de ventana.
- [ ] Distinguir webhook invoice (`transaction_uuid`) de webhook topup (`uuid`).
- [ ] Pruebas de URL encoding, timeouts y múltiples instancias con credenciales diferentes.
- [ ] Un test demuestra que logs/excepciones y `toString()` no muestran secretos.
- [ ] **Sin tests que carguen, transfieran, retiren o cobren dinero real.**

### Integración (manual, desactivada por defecto)

- [ ] App QvaPay configurada en **Modo prueba** y secretos proporcionados por entorno.
- [ ] Verificar `info()`, `balance()` y creación/consulta de **factura de prueba**.
- [ ] Verificar el webhook en simulador o con fixture firmado auténticamente.
- [ ] Validar manualmente modelos/semántica y registrar diferencias con documentación.
- [ ] Tests de integración nunca deben ejecutarse en CI público sin autorización explícita.

### Definition of Done para `0.1.0`

1. `./mvnw -B -ntp clean verify` pasa con Java **17 y 21** (matriz CI; Java 25 opcional, no bloqueante). El bytecode publicado tiene target **Java 17** (`--release 17`), comprobado al ejecutar el artefacto final con JDK 17.
2. Ocho operaciones merchant de la tabla implementadas, tipadas y probadas con mocks.
3. Webhooks de factura y topup verificados; ejemplos de consumo seguro.
4. Javadoc para todos los métodos públicos y un README que permita crear una factura de prueba en minutos.
5. Archivos `docs/api-coverage.md`, `docs/security.md`, `CHANGELOG.md`, `LICENSE` y `.github/workflows/ci.yml` incluidos.
6. No hay secretos, datos de clientes ni llamadas de pago real en el repositorio.
7. Artefacto JAR + sources + Javadoc, POM con metadatos Maven Central, perfil release y workflow manual; **no publicar sin autorización del mantenedor**.

## 10. README.md: requisito de primera clase (NO opcional)

El README es la landing page pública del proyecto. Debe quedar **bonito, profesional, original y extraordinariamente fácil de usar** para alguien que no conozca QvaPay. Claude debe **escribir `README.md` realmente**, no limitarse a mencionarlo en este archivo. Debe ser consistente con la API compilable y no presentar versiones no publicadas como disponibles.

### Diseño y tono del README

- **Idioma principal inglés**, con `README.es.md` enlazado de forma visible para comunidad hispanohablante. Documentar el código en inglés.
- Apertura centrada con título **QvaPay Java SDK**, tagline concreta ("A lightweight, type-safe, framework-agnostic Java SDK for the QvaPay API") y aclaración visible **Unofficial / community maintained**.
- Badges discretos y reales, estilo GitHub: Java 17+ / Built with JDK 17 LTS, CI, License, Maven Central (solo después de publicar), Javadoc (solo si existe), cobertura (solo con datos reales). **Nunca falsificar badges ni enlaces rotos para aparentar éxito**. Antes de la primera release usar un indicador honesto: `Status: pre-release`.
- Visual limpio: headings breves, separadores sutiles, tablas compactas, bloques de código copiables, diagrama Mermaid pequeño y útil para explicar invoice → pay → webhook → verify; sin banners gigantes, GIFs, emojis excesivos, insignias redundantes ni claims de QvaPay oficial.
- README conciso, atractivo y orientado a la acción. Mover detalles internos a `docs/` y enlazarlos. Evitar paredes de texto.
- Si se usa branding gráfico, que sea diseño **original** del repositorio y no implique respaldo de QvaPay. Mantener compatibilidad con tema claro/oscuro GitHub.
- No usar rutas relativas o badges de repositorios que aún no existan. Sustituir `OWNER` y demás placeholders **antes de considerar terminada la publicación**, nunca inventar dueño/namespace.

### Estructura de contenido OBLIGATORIA y en este orden

1. **Hero:** nombre, tagline, estado y aviso de no oficialidad; enlace a docs oficiales de QvaPay.
2. **Why this SDK?** 3–5 beneficios concretos: Java idiomático, API tipada, `BigDecimal`, sync/async, seguridad de webhooks y sin acoplamiento a framework (solo los que existan de verdad).
3. **Features:** tabla pequeña `Feature / Since version / Status` con Merchant v2, invoices, transactions, payments authorization, charge, signed webhooks y funcionalidad futura. No marcar como disponible lo que no esté implementado/probado.
4. **Requirements:** Java mínimo, cuenta/app QvaPay, token o app credentials según caso; prohibición de llevar secretos al cliente/frontend.
5. **Installation:** snippets verificables de **Maven, Gradle Groovy y Gradle Kotlin DSL**, usando el `groupId`, `artifactId` y `version` reales. Antes de publicación dar pasos para `mvn install` local y marcar las coordenadas públicas como pendientes; después de publicación mostrar Maven Central. Ningún consumidor debe agregar JitPack ni repositorios privados para una versión ya publicada.
6. **Quick start (≤ 30 líneas):** `QvaPayClient.builder()` con `System.getenv`, balance, creación de invoice con `BigDecimal`, lectura de `invoice.getUrl()` o acceso real que corresponda. No inventar getters; compilar el snippet con pruebas.
7. **Usage examples:** consultar transacción, autorización de pagos y charge **con advertencia explícita** de efectos financieros/consentimiento, operación asíncrona con `CompletableFuture`, verificación de webhook sobre `byte[]`, tratamiento de errores 401/403/429. Incluir solo funcionalidades implementadas.
8. **Payment flow:** Mermaid `merchant backend → API QvaPay → checkout del usuario → webhook firmado → validación/dedupe → actualización del pedido`. Aclarar que el SDK no hospeda el endpoint HTTP.
9. **Security:** no copiar secretos, validar firma antes de parsear, deduplicar, confirmar importe/UUID, no reintentar cargos; enlace `SECURITY.md`.
10. **Compatibility:** Maven y Gradle; plain Java, Spring Boot, Quarkus, Micronaut, AWS Lambda (sin integración nativa ficticia). Marcar los ejemplos opcionales como tales.
11. **Documentation index:** `docs/api-coverage.md`, Javadoc, `docs/architecture.md`, `docs/troubleshooting.md`, `docs/release.md` y API QvaPay.
12. **Contributing / Roadmap / License / Disclaimer:** instrucciones breves y enlaces completos, incluyendo las limitaciones y la independencia del proyecto.

### Contrato editorial exigido a Claude

- **README inglés y español** coherentes; versión ES puede condensar detalles avanzados enlazando a docs EN, pero conservar instalación, ejemplo y disclaimer.
- Snippets del README cubiertos con `examples/plain-java` y, cuando sea posible, `javac`/Maven o tests que prueben el mismo código; **sin pseudo-API ni código que no compile**.
- README debe enseñar a usar la librería **en un proyecto diferente**, no desde el código fuente del SDK. Contenido de ejemplo usa Java puro, no asume Spring.
- Crear capturas/logos solo si se han generado con derechos claros; no se exige ninguna imagen para que luzca bien.
- Si no existe una release, usar algo como `> Package status: not yet published to Maven Central.` en lugar de un snippet que parezca instalable.
- Incluir una tabla de URLs que haga obvio dónde encontrar ejemplos completos, preguntas frecuentes y documentación oficial.
- **Definition of Done del README:** un desarrollador sin contexto puede descubrir el paquete, instalarlo, autenticarse y generar una factura de prueba sin leer el código fuente; ninguna afirmación engañosa o link roto.

### Boceto visual orientativo para producir el archivo final (NO copiarlo ciegamente)

```markdown
<div align="center">

# QvaPay Java SDK

**A lightweight, type-safe Java client for QvaPay.**

`Java 17+` · `JDK 17 LTS` · `Maven & Gradle` · `Framework-agnostic` · `Community maintained`

[Getting started](#quick-start) · [Examples](examples/) · [API coverage](docs/api-coverage.md) · [Español](README.es.md)

</div>

> **Unofficial SDK.** This project is not affiliated with or endorsed by QvaPay.

## Installation

<!-- Antes de publicar: mostrar únicamente instalación local, no falso Maven Central. -->

## Requirements

Java 17+ (built and tested with JDK 17 LTS; compatibility also checked on Java 21). Maven or Gradle. No framework required.

## Quick start

<!-- Código REAL que compile en examples/plain-java. -->
```

## 11. Maven Central: el repositorio debe quedar REALMENTE listo para publicar

No basta con hacer `mvn package`: el SDK debe tener un **pipeline de distribución completo**, metadatos correctos, código consumible, artefactos firmados, ejemplos de dependencias y una guía de publicación sin pasos secretos omitidos. Publicar **solo** con aprobación explícita del propietario. El objetivo inmediato es estar *release-ready*, no decir que ya está en Maven Central.

### 11.1 Coordenadas y compatibilidad

- Nombre de trabajo: `qvapay-java`; artefacto: `qvapay-java`; versión inicial objetivo: `0.1.0` (al hacer release). Desarrollo puede usar `0.1.0-SNAPSHOT` **solo en local**.
- `groupId`: **namespace real verificado** en https://central.sonatype.com, por ejemplo `io.github.<github_username>` para el GitHub correcto, o `com.<dominio_propiedad_del_publicador>`. No usar `com.qvapay` ni `io.github.qvapay` si no pertenece al mantenedor.
- Java package elegible: `io.github.<github_username>.qvapay` o paquete válido equivalente; puede diferir de `groupId` si el nombre incluye guiones. **Reemplazar todos los placeholders** cuando se conozca el dueño real.
- **JVM mínimo Java 17 LTS**, compilado con `maven.compiler.release=17` bajo **JDK 17 LTS**; documentar claramente que **Java 8/11 no son compatibles** con el JAR. Consumible en proyectos **Java 17, 21 y 25** (Java 25 se prueba opcionalmente) desde Maven y Gradle; sin dependencia obligatoria de Spring, Android, Jakarta EE o Quarkus.
- `jar` de biblioteca normal, sin `main`, fat jar, shading arbitrario ni dependencias ocultas. Mantener dependencias transitivas mínimas, conocidas y auditables.
- Incluya `Automatic-Module-Name` estable en el `MANIFEST.MF` si se define una API de módulos y se comprueba compatibilidad; no declarar compatibilidad JPMS total sin pruebas.
- SemVer y compatibilidad binaria: evitar romper métodos públicos en patch/minor; revisar con `japicmp`/Revapi cuando sea razonable.

### 11.2 POM mínimo de metadatos y calidad (OBLIGATORIO)

Configurar `pom.xml` con campos reales y consistentes:

- `<groupId>`, `<artifactId>`, `<version>`, `<packaging>jar</packaging>`.
- `<name>`, `<description>`, `<url>` apuntando al repo público.
- `<licenses>` con nombre y URL correctos (preferir **MIT** para código original si el autor lo decide; usar Apache-2.0 solo si se elige correctamente). El texto de `LICENSE` debe corresponder. Revisar atribuciones de terceros.
- `<developers>` con mantenedor real y enlace público válido; **no inventar** correos, organizaciones ni identidades.
- `<scm>` con `connection`, `developerConnection`, `url` y tag, usando URL de Git real.
- `<issueManagement>` opcional y legítimo; propiedades de codificación UTF-8, `maven.compiler.release=17` y JDK 17 como toolchain preferido.
- `maven-compiler-plugin` **3.16.0** (`release=17`), `maven-surefire-plugin` **3.6.0**, `maven-failsafe-plugin` si hace falta, `maven-jar-plugin`, `maven-source-plugin` (`sources.jar`), `maven-javadoc-plugin` (`javadoc.jar`), `maven-gpg-plugin` (firma POM/JAR/sources/Javadoc en **perfil release**), `maven-enforcer-plugin` y plugin de publicación Central.
- Ejecutar documentación Javadoc **real** sin ignorar advertencias indiscriminadamente ni falsear enlaces.
- Versiones concretas de plugins/dependencias fijadas tras comprobar Maven Central; no rangos abiertos ni `LATEST`/`RELEASE`.
- `mvn dependency:tree` debe mostrar dependencias de runtime razonables; evitar arrastrar grandes frameworks.

**Propiedades mínimas coherentes en `pom.xml` (fijar también las versiones de plugins verificadas arriba):**

```xml
<properties>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  <maven.compiler.release>17</maven.compiler.release>
  <junit.version>6.1.3</junit.version>
  <jackson.version>3.2.4</jackson.version>
</properties>
```

- En `dependencyManagement` importar el BOM Jackson 3 (`tools.jackson:jackson-bom:3.2.4`) tras comprobar sus coordenadas actuales; usar `tools.jackson.core:jackson-databind` sin versión individual. Si el BOM/cambios de Jackson 3 difieren, consultar el POM oficial, no sustituir a ciegas. Usar JUnit `org.junit:junit-bom:6.1.3` para mantener coherencia del motor.
- Jackson 3 debe mantenerse **como detalle de implementación**; el SDK no debe exponer `JsonNode`, `ObjectMapper` ni otros tipos de librerías JSON en firmas públicas, salvo que se diseñe una extensión opcional explícita. Probar cohabitación del consumidor con Spring/Jackson 2 sin conflictos de clases o anotaciones.
- Java 17 permite `record`, `sealed`, `instanceof` pattern matching y switch expressions; evitar pattern matching para `switch`, record patterns, virtual threads y cualquier API/característica posterior, ya que impedirían la ejecución en Java 17.
- Registrar este baseline técnico en `README.md`, `README.es.md`, `docs/architecture.md`, `docs/release.md` y en el POM, sin contradicciones.

### 11.3 Publicador oficial (Portal moderno)

Usar documentación actual: https://central.sonatype.org/publish/publish-portal-maven/ . A fecha de redacción (22-oct-2026) muestra **`org.sonatype.central:central-publishing-maven-plugin:0.12.0`**. En la fecha de verificación esta versión permite Maven 3.10; **no** usar Maven 4 para publicar hasta que exista compatibilidad demostrada. Verificar vigencia antes de generar el POM final.

**Configuración orientativa — integrarla en `pom.xml` con los plugins de sources/Javadoc/GPG (no reemplazarlos):**

```xml
<plugin>
  <groupId>org.sonatype.central</groupId>
  <artifactId>central-publishing-maven-plugin</artifactId>
  <version>0.12.0</version>
  <extensions>true</extensions>
  <configuration>
    <publishingServerId>central</publishingServerId>
    <autoPublish>false</autoPublish>
  </configuration>
</plugin>
```

- Credenciales se obtienen como **Portal User Token**, se referencian en `~/.m2/settings.xml` o en `settings.xml` temporal del CI: server id `central`, token username y token password. **Nunca en Git.**
- Crear clave OpenPGP del propietario en entorno privado; firmar los archivos con GPG, conservar clave privada fuera del repositorio. `GPG_PRIVATE_KEY`/`GPG_PASSPHRASE` solo como secrets protegidos en CI, si se autoriza su uso.
- Verificar generación de `.jar`, `-sources.jar`, `-javadoc.jar`, POM, firmas `.asc` y checksums. La plataforma publica únicamente artefactos **inmutables**; no reutilizar versiones.
- Preparar un perfil opt-in `central` o `release` para activar firmas y publicación; el build común `mvn verify` NO debe pedir GPG ni tokens ni ejecutar `deploy`.
- El `central-publishing-maven-plugin` puede generar un bundle local con `skipUpload` documentado; **testear esta ruta sin llamadas a Central** antes de cualquier deployment. Hacer inspección de POM y firmas.
- Tras verificar namespace + token + GPG y aprobación expresa, `mvn -Pcentral deploy` sube a **Portal** para validación. Con `autoPublish=false`, la promoción es manual en https://central.sonatype.com/publishing/deployments. Nunca asumir que una subida significa publicación final.
- **No usar el flujo obsoleto OSSRH/Nexus staging antiguo** como solución principal. No subir paquetes a Maven Central desde una cuenta no verificada.

### 11.4 CI / Release con GitHub Actions

Crear `.github/workflows/ci.yml`:

- Build y tests offline contra QvaPay en PR/push; matriz de Java **17 (mínimo y principal) y 21 (compatibilidad)**; 25 puede agregarse como job opcional/no bloqueante. Usar Maven Wrapper 3.10.0, `--release 17` y `temurin` (o distribución soportada) en GitHub Actions.
- Cache Maven, ejecuta `mvn -B -ntp clean verify`; valida style, unit/contract tests, Javadoc, coverage, seguridad de dependencias y escaneo de secretos.
- No se necesitan tokens QvaPay ni claves GPG en PR. No almacenar ni imprimir datos sensibles.
- No marcar el workflow badge como verde antes de que corra realmente.

Crear `.github/workflows/release.yml`:

- Desencadenador **solo `workflow_dispatch`** y environment protegido llamado `maven-central` con aprobación humana. NO desplegar automáticamente en `push`, `pull_request`, etiquetas o forks.
- Verificar `version` del POM, Git tag/commit esperado y release notes. Reejecutar checks completos antes de firmar.
- Importar clave GPG y crear `settings.xml` temporal usando GitHub Secrets (`CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`, `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`). Redactar stdout, secretos nunca se exponen.
- Ejecutar perfil `central` con `autoPublish=false`; guardar y reportar ID/estado de deployment sin declarar 'publicado' hasta que sea visible en Central.
- Crear tag/Releases/GitHub release notes solo después de confirmar versión y coordinar con publicación; un tag no debe causar una publicación involuntaria.
- Workflow solo **preparado**; no intentar crearlo o ejecutarlo en un GitHub real sin autorización.

### 11.5 Probar como consumidor independiente (gate de calidad esencial)

Además de tests unitarios, el agente debe crear **2 proyectos de ejemplo** en carpetas independientes, usando el `.jar` del SDK instalado localmente con `mvn install`, no clases internas:

1. `examples/plain-java/`: Maven consumidor con **Java 17**, importa el paquete público, crea cliente y factura simulada contra un mock HTTP (sin dinero). Debe compilar y ejecutar tests con Java 17 y 21.
2. `examples/gradle-java/` o ejemplo Gradle mínimo: snippets Groovy y Kotlin DSL correctos, sin repositorios extra al instalar desde Maven Central. **Gradle Wrapper 9.8.2** (GA, compatible con Java 17) y **Java toolchain 17**, con comprobación de importación/ejecución contra el JAR local. El SDK no requiere Gradle como dependencia del consumidor: es únicamente un ejemplo de build.

Ejemplos adicionales de Spring Boot/AWS Lambda solo cuando compilables, y **nunca** acoplados al core. Antes de la primera publicación exigir al menos Maven + Gradle desde repo local; tras la publicación comprobar resolución real de las coordenadas desde `mavenCentral()` en máquina limpia.

### 11.6 Procedimiento humano documentado en `docs/release.md`

1. Elegir repo, licencia y namespace; verificar la propiedad del namespace en Central Portal (con GitHub `io.github.<username>` o dominio propio).
2. Elegir versión SemVer e incrementar `CHANGELOG`; quitar `-SNAPSHOT` en release.
3. Confirmar URL de repo SCM, maintainer POM, campos licencia, dependencias y matriz Java.
4. `mvn -B -ntp clean verify` y pruebas de consumidor independiente, Javadoc, sources.
5. Crear clave GPG (si no existe), distribuir pública si procede, configurar token de Portal fuera del repo.
6. Generar bundle de validación **sin upload**, inspeccionar POM/JARs/firmas/checksums.
7. Con aprobación humana, ejecutar release manual protegido, subir al Portal, esperar validación, revisar bundle y **publicar manualmente**.
8. Comprobar que las coordenadas se resuelven en https://central.sonatype.com y desde un consumidor Maven y otro Gradle limpios.
9. Actualizar README con coordenadas reales, badge Maven Central y enlaces de Javadoc correctos **solo entonces**; publicar GitHub Release y tag de versión inmutable.
10. Rotar credenciales si se filtraron, nunca reutilizar números de versión; documentar rollback como una **nueva versión corregida**.

**Lo que puede quedar pendiente por depender del usuario:** namespace verificado, GitHub repo real, datos de mantenedor/licencia definitiva, Central Portal Token, GPG privado y aprobación de publicación. Claude debe listarlos como `HUMAN SETUP REQUIRED` y dejar el código/pipeline preparado para completarlos sin reescritura. **No afirmar 'ya publicado' sin un enlace central verificable.**

## 12. Licencias, atribución y buenas prácticas OSS

- Tratar referencias como **insumos de investigación**, no como repositorios de código para copiar sin revisión. El plugin oficial WooCommerce tiene **GPL-2.0**; un port directo puede obligar al proyecto a cumplir la GPL. Priorizar una implementación original basada en la API pública. El SDK Python comunitario y el CLI tienen MIT reportada; leer cada archivo LICENSE en la versión concreta.
- El SDK Node requiere revisar qué licencia aplica; la visibilidad pública de un repositorio NO concede permisos de reutilización.
- Si se reusa código bajo licencia compatible, conservar atribución/copyright y el aviso correspondiente; crear `NOTICE` si hace falta.
- Incluir `LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `.editorconfig`, `.gitignore`, `.gitattributes`, issue y PR templates (mínimos), guía de vulnerabilidades sin publicar información sensible y `CHANGELOG.md` bajo SemVer.
- Evitar nombres de packages, logos, dominios o copy que sugieran que esta librería es oficial de QvaPay.

## 13. Cómo debe trabajar Claude Code

1. **Inspeccionar el repositorio**. Si está vacío, inicializar estructura Maven e infraestructura mínima, sin crear frameworks ajenos al SDK.
2. **Verificar documentación** de cada endpoint contra las URLs de la sección 2. Registrar decisiones/discrepancias en `docs/api-coverage.md` con fecha.
3. **Documentar el diseño técnico en `docs/architecture.md`**, nunca usar el README como borrador interno; luego codificar de forma incremental: transporte y auth -> modelos -> merchant -> webhooks -> pruebas -> docs.
4. Tras cada endpoint implementado, escribir fixture JSON con respuesta del contrato oficial y pruebas unitarias de request/response/errores.
5. Mantener la superficie Java pequeña; evitar abstracciones innecesarias y dependencias voluminosas. Verificar que importa y funciona desde otro proyecto Maven/Gradle con **Java 17**; repetir con JDK 21.
6. Ejecutar `mvn -B clean verify`, compilar los ejemplos y Javadoc/sources, corregir problemas y agregar tests de regresión.
7. Documentar cualquier comportamiento sin confirmar en `docs/api-coverage.md` y no simular que está implementado.
8. No ejecutar llamadas reales a endpoints que muevan dinero. **Pedir autorización explícita** para cualquier prueba real/operación con credenciales de producción.
9. No publicar paquetes, crear releases, subir a Maven Central ni alterar credenciales sin aprobación explícita.
10. Al finalizar, entregar: resumen de archivos creados, endpoints cubiertos, comandos de build, pruebas ejecutadas, enlaces del README/Javadoc y qué falta para publicar (namespace, credenciales, autorización).

### Primera tarea concreta

**Construir un repositorio open source release-ready, no un ejemplo académico:** SDK `0.1.0` con API Merchant v2 + webhooks, pruebas offline, `README.md` bello y completo (incluyendo `README.es.md`), ejemplos compilables para consumidores Maven y Gradle, Javadoc, POM de Central, sources JAR, firmas GPG preparadas, CI y release manual seguro. No empezar por P2P, retiros o login interactivo hasta completar este alcance. No subir a Maven Central ni ejecutar pagos reales sin aprobación. El código debe quedar preparado para incorporar Bearer y más módulos sin rehacer HTTP.

---

### Lista de verificación rápida para agentes

- [ ] Estoy usando `https://api.qvapay.com` y rutas reales por familia.
- [ ] Estoy enviando `app-id` y `app-secret` en los **headers**, no en el body.
- [ ] Estoy usando el método HTTP real, incluso cuando una consulta es `POST`.
- [ ] No estoy asumiendo que todas las respuestas tienen `{ data: ... }`.
- [ ] Estoy usando `BigDecimal` para cantidades monetarias.
- [ ] He distinguido pago de factura de topup.
- [ ] Estoy verificando firma del **raw body** y gestionando eventos repetidos.
- [ ] No hago retries ciegos en cobros u operaciones financieras.
- [ ] Estoy documentando incertidumbres, no inventando comportamientos.
- [ ] Puedo ejecutar los tests sin conexión a QvaPay.
- [ ] He leído README/código/LICENSE de los cuatro repositorios de referencia y documenté diferencias.
- [ ] Existe README en inglés atractivo + README.es.md, con snippets que compilan.
- [ ] Se consume desde proyectos **Java 17+** externos con Maven y Gradle sin frameworks; se construye sobre JDK 17 LTS.
- [ ] POM contiene metadata Central real/pendiente declarada, `sources.jar`, `javadoc.jar` y preparación GPG.
- [ ] `release.yml` solo corre manualmente con aprobación humana y `autoPublish=false`.
- [ ] No afirmo que está disponible en Maven Central hasta confirmar la publicación.
