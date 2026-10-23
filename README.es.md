<div align="center">

<img src="docs/assets/logo.png" alt="QvaPay Java SDK" width="560">

**Un SDK de Java ligero, tipado y sin dependencia de frameworks para la API de QvaPay.**

`Java 17+` · `Construido con JDK 17 LTS` · `Maven y Gradle` · `Mantenido por la comunidad`

[Inicio rápido](#inicio-rápido) · [Ejemplos](examples/) · [Cobertura de la API](docs/api-coverage.md) · [Javadoc](https://alejo2075.github.io/qvapay-java/) · [English](README.md)

</div>

> **SDK no oficial.** Este proyecto lo mantiene la comunidad y no tiene relación con QvaPay, ni cuenta con su respaldo o soporte. Documentación oficial de la API: <https://www.qvapay.com/docs>.

> **Estado: `0.1.0` disponible en [Maven Central](https://central.sonatype.com/artifact/io.github.alejo2075/qvapay-java).** Versión temprana: sigue la documentación pública de QvaPay y solo se ha probado sin red (ver [Funcionalidades](#funcionalidades)).

[![CI](https://github.com/Alejo2075/qvapay-java/actions/workflows/ci.yml/badge.svg)](https://github.com/Alejo2075/qvapay-java/actions/workflows/ci.yml) [![Maven Central](https://img.shields.io/maven-central/v/io.github.alejo2075/qvapay-java)](https://central.sonatype.com/artifact/io.github.alejo2075/qvapay-java) [![Javadoc](https://img.shields.io/badge/javadoc-0.1.0-blue)](https://alejo2075.github.io/qvapay-java/) ![License](https://img.shields.io/badge/license-MIT-blue) ![Java](https://img.shields.io/badge/Java-17%2B-orange)

---

## ¿Por qué este SDK?

- **Idiomático y tipado:** respuestas inmutables (`record`), builders para las solicitudes.
- **Dinero con `BigDecimal`:** los importes nunca se redondean (`25.00` sigue siendo `25.00`).
- **Síncrono y asíncrono:** cada operación tiene su método bloqueante y uno `...Async` con `CompletableFuture`.
- **Seguro por defecto:** solo HTTPS, secretos ocultos en `toString()` y excepciones, sin redirecciones y **sin reintentos automáticos** en operaciones que crean recursos o mueven dinero.
- **Webhooks firmados:** verificación HMAC-SHA256 sobre los bytes crudos, con eventos tipados para facturas y recargas (top-ups).
- **Sin acoplarte a un framework:** `HttpClient` del JDK y una sola dependencia de ejecución (Jackson 3).

## Funcionalidades

| Funcionalidad | Desde | Estado |
|---|---|---|
| App merchant: `info`, `balance` | 0.1.0 | Implementado, probado sin red |
| Facturas: crear y modificar | 0.1.0 | Implementado, probado sin red |
| Transacciones: listar y detalle con pagador | 0.1.0 | Implementado, probado sin red |
| Autorizar pagos y cobro directo | 0.1.0 | Implementado, probado sin red |
| Webhooks firmados (factura pagada y top-up) | 0.1.0 | Implementado, probado sin red |
| Login con 2FA, perfil, coins, enlaces de pago | planificado | No implementado |
| Mercado P2P | planificado | No implementado |
| Retiros, transferencias, tienda | más adelante | No implementado |

"Probado sin red" significa pruebas de contrato con mocks y fixtures. **Nada se ha ejecutado contra producción y ninguna prueba mueve dinero real.** Detalle por endpoint en [docs/api-coverage.md](docs/api-coverage.md).

## Requisitos

- **Java 17 o superior** (Java 8 y 11 no son compatibles).
- Maven o Gradle.
- Una cuenta de QvaPay y una **app** (App ID + App Secret). Usa el *modo prueba* mientras desarrollas.
- Las credenciales van en tu **servidor**. Nunca las pongas en un navegador, app móvil o frontend de mini-app.

## Instalación

Disponible en Maven Central; no necesitas repositorios adicionales.

**Maven**

```xml
<dependency>
  <groupId>io.github.alejo2075</groupId>
  <artifactId>qvapay-java</artifactId>
  <version>0.1.0</version>
</dependency>
```

**Gradle (Kotlin DSL)**

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.alejo2075:qvapay-java:0.1.0")
}
```

**Gradle (Groovy DSL)**

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'io.github.alejo2075:qvapay-java:0.1.0'
}
```

## Inicio rápido

Exporta las credenciales de una app en **modo prueba**:

```bash
export QVAPAY_APP_ID=tu-app-uuid
export QVAPAY_APP_SECRET=tu-app-secret
```

<!-- snippet:plain-java/QuickStart.java -->
```java
public static void main(String[] args) {
    QvaPayClient client = QvaPayClient.builder()
            .appCredentials(System.getenv("QVAPAY_APP_ID"), System.getenv("QVAPAY_APP_SECRET"))
            .build();

    AppBalance balance = client.merchant().balance();
    System.out.println("Balance: " + balance.balance() + " USD");

    Invoice invoice = client.invoices().create(
            CreateInvoiceRequest.builder()
                    .amount(new BigDecimal("25.00"))
                    .description("Test invoice")
                    .remoteId("order-12345")
                    .build());

    System.out.println("Send the customer to: " + invoice.url());
    System.out.println("Keep this next to your order: " + invoice.transactionUuid());
}
```

> **Modo prueba.** Con una app en modo prueba QvaPay marca la factura como `paid` al instante y no devuelve página de pago, por lo que `invoice.url()` es `null`. Fuera del modo prueba, `url()` es la página de pago del cliente.

La versión completa y compilable está en [QuickStart.java](examples/plain-java/src/main/java/example/QuickStart.java).

## Verificar un webhook

<!-- snippet:plain-java/AdvancedUsage.java#webhook -->
```java
/**
 * Call this from your HTTP endpoint with the raw request body bytes and the two headers.
 * Throws {@code WebhookVerificationException} if the delivery must be rejected (answer 401).
 */
static InvoiceWebhookEvent handleWebhook(byte[] rawBody, String signature, String timestamp) {
    WebhookVerifier verifier = WebhookVerifier.forSecret(System.getenv("QVAPAY_APP_SECRET"));
    InvoiceWebhookEvent event = verifier.verifyInvoice(rawBody, signature, timestamp).event();
    // Now: deduplicate by event.transactionUuid(), compare amount and remoteId with your order,
    // and only then mark it as paid.
    return event;
}
```

Lee el cuerpo como `byte[]` **antes** de cualquier parseo JSON: si se vuelve a serializar, la firma ya no coincide. Hay un receptor ejecutable en [examples/webhook](examples/webhook). Después de verificar: **deduplica** por `transaction_uuid` y confirma importe, `remote_id` y estado contra tu pedido.

## Cobros directos (mueven dinero)

> **Advertencia:** `charge` descuenta saldo real de un usuario fuera del modo prueba, requiere el consentimiento previo del usuario (el SDK nunca lo concede por él) y tu app necesita el permiso `allowed_payment_auth`. **Nunca se reintenta automáticamente**: tras un timeout el resultado es desconocido; revisa tus transacciones por el `remote_id` antes de volver a cobrar.

Ejemplo completo: [PaymentsExample.java](examples/plain-java/src/main/java/example/PaymentsExample.java).

## Seguridad

- Secretos en variables de entorno o un gestor de secretos; nunca en Git, logs o código cliente.
- Verifica la firma sobre el cuerpo crudo, deduplica y confirma los datos con tu pedido.
- La cabecera de timestamp no está firmada: descarta entregas antiguas, pero no protege contra replays.
- No reintentes cobros ni confíes en la redirección del navegador como prueba de pago.

Más detalles (en inglés): [docs/security.md](docs/security.md) y [SECURITY.md](SECURITY.md).

## Documentación

| Qué | Dónde |
|---|---|
| Ejemplos ejecutables | [examples/](examples/) |
| Cobertura de endpoints y dudas abiertas | [docs/api-coverage.md](docs/api-coverage.md) |
| Arquitectura | [docs/architecture.md](docs/architecture.md) |
| Seguridad / solución de problemas | [docs/security.md](docs/security.md), [docs/troubleshooting.md](docs/troubleshooting.md) |
| Proceso de publicación | [docs/release.md](docs/release.md) |
| API oficial de QvaPay | <https://www.qvapay.com/docs> |

## Contribuir y licencia

Lee [CONTRIBUTING.md](CONTRIBUTING.md) y el [Código de Conducta](CODE_OF_CONDUCT.md). Licencia [MIT](LICENSE).

## Aviso legal

QvaPay es una marca de su propietario. Este es un proyecto independiente, sin afiliación ni respaldo de QvaPay. Úsalo bajo tu responsabilidad, revisa el código antes de mover dinero con él y respeta los términos, KYC y requisitos de cumplimiento de QvaPay. El SDK refleja la documentación pública a fecha 2026-10-22 y puede quedar desactualizado frente a cambios de la API.
