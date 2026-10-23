## What and why

## Checklist

- [ ] `./mvnw -B -ntp clean verify` passes (JDK 17; also tried on 21 if HTTP/JSON changed)
- [ ] Tests added or updated; none touches the network or real money
- [ ] Behaviour matches the official docs (link or `docs/api-coverage.md` updated)
- [ ] Money-moving or resource-creating calls are not retried
- [ ] No secrets, tokens or customer data in code, fixtures or logs
- [ ] `CHANGELOG.md` updated under `[Unreleased]`
- [ ] README snippets still in sync (`python3 scripts/readme_snippets.py check`)
