# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- `QvaPayClient` builder with HTTPS-only base URL, timeouts and opt-in read retries.
- Merchant `info()` (`POST /v2/info`).
- Merchant `balance()` (`POST /v2/balance`) returning `BigDecimal`.
