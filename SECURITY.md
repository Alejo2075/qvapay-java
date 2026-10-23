# Security policy

## Supported versions

This project is pre-release. Security fixes go to the latest release (and to `main` before the first release).

## Reporting a vulnerability

**Do not open a public issue** for security problems. Use GitHub's private vulnerability reporting on this repository (*Security* tab → *Report a vulnerability*). If that is not available, contact the maintainer privately through the contact options on their GitHub profile (<https://github.com/Alejo2075>).

Include: affected version, impact, steps to reproduce and, if possible, a suggested fix. Please **do not include real credentials, tokens or customer data**; use test-mode values.

You can expect an acknowledgement within a few days. Fixes are released as a new version (published artifacts are immutable) with credit if you want it, after coordinating disclosure.

## Scope

In scope: this SDK (credential handling, redaction, signature verification, request building, retries, dependencies).

Out of scope: vulnerabilities in the QvaPay service itself. Report those to QvaPay through their official channels. This SDK is unofficial and not affiliated with QvaPay.

## Secrets exposure

If you accidentally published an `app-secret`, rotate it in QvaPay immediately. Treat anything that appeared in logs, issues or commits as compromised.

See also [docs/security.md](docs/security.md) for guidance on using the SDK safely.
