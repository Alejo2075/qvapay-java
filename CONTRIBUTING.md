# Contributing

Thanks for helping! This is an **unofficial** SDK for the QvaPay API. Behaviour must follow the [public QvaPay documentation](https://www.qvapay.com/docs); if the docs do not say something, do not assume it: open an issue or add a `TODO: VERIFY WITH QVAPAY` entry in [docs/api-coverage.md](docs/api-coverage.md).

## Ground rules

- **Never** put real credentials, customer data or real payment calls in code, tests, fixtures, issues or PRs. Tests run offline against mocks and fixtures.
- **Never** copy code from reference projects with incompatible licenses (the official WooCommerce plugin is GPL-2.0). Read behaviour from the docs; see [docs/references.md](docs/references.md).
- Money is `BigDecimal`; do not round. Operations that create resources or move money must not be retried automatically.
- Keep the public API small and documented. No Jackson types in public signatures. No new runtime dependency without discussion.
- Java 17 only: compile with `--release 17`; do not use features or APIs from Java 18+.

## Setup

You need JDK 17 (Temurin recommended). Maven is provided by the wrapper.

```bash
./mvnw -B -ntp clean verify      # format check, tests, coverage thresholds, javadoc, jars
./mvnw spotless:apply            # auto-format (google-java-format, AOSP style)
```

Run with JDK 21 as well before opening a PR that touches HTTP or JSON code.

To try the examples against your working copy:

```bash
./mvnw install -DskipTests
./mvnw -f examples/plain-java/pom.xml clean verify
(cd examples/gradle-java && ./gradlew test)
```

If you change a README code block, edit the example source and run `python3 scripts/readme_snippets.py update`; CI runs `check`.

## Adding or changing an endpoint

1. Read the official page and record it in `docs/api-coverage.md` with the verification date.
2. Add a JSON fixture under `src/test/resources/fixtures/` copied from the documented response.
3. Add the model, the API method (blocking and `...Async`) and contract tests: method, path, headers, body, response, error statuses, and "not retried" when it moves money.
4. Update `CHANGELOG.md` (`[Unreleased]`) and the status column in `docs/api-coverage.md`.

## Commits and pull requests

We use [Conventional Commits](https://www.conventionalcommits.org/), in English:

```
<type>(<scope>): <imperative summary, up to 72 characters>
```

Types: `feat`, `fix`, `test`, `docs`, `build`, `ci`, `refactor`, `chore`, `perf`, `style`.
Scopes: `core`, `auth`, `http`, `json`, `error`, `merchant`, `invoices`, `transactions`, `payments`, `webhook`, `examples`, `docs`, `readme`, `build`, `ci`, `release`, `repo`.

- One logical change per commit, with its tests; every commit must pass `./mvnw clean verify`.
- Breaking changes: `feat(core)!:` plus a `BREAKING CHANGE:` footer.
- Branches are short-lived (`feat/...`, `fix/...`, `docs/...`); `main` stays green and is merged with rebase or fast-forward.
- Releases and publishing are done by the maintainer only (see [docs/release.md](docs/release.md)).

## Reporting bugs

Use the issue templates. Include SDK version, Java version and a minimal sanitized example. For vulnerabilities, follow [SECURITY.md](SECURITY.md) instead of opening a public issue.
