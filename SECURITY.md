# Security Policy

## Supported versions

Ledger is self-hosted and versioned as a single deployable artifact. Only the latest commit on the default branch is supported, there are no maintained release branches, so upgrading means pulling the latest code/image.

## Reporting a vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities. Instead, use GitHub's private vulnerability reporting (Security tab -> "Report a vulnerability") on this repository. If that isn't available, contact a maintainer directly rather than disclosing publicly.

Include, where possible:

- A description of the issue and its impact.
- Steps to reproduce, or a proof of concept.
- The version/commit you tested against.

We'll acknowledge reports as soon as we can and keep you updated as we investigate and fix the issue. Please give us a reasonable amount of time to ship a fix before any public disclosure.

## Operational security notes

Ledger's security model relies on a few things being handled correctly at deploy time, these are covered in more depth in [SETUP_GUIDE.md](SETUP_GUIDE.md), but worth calling out here:

- `JWT_SECRET` must be a real, random, base64-encoded 256-bit (or longer) key (`openssl rand -base64 32`). The app deliberately refuses to start with no secret set rather than falling back to a default, never commit a real value to source control or reuse the dev-profile secret in production.
- `LEDGER_ADMIN_USERNAME`/`LEDGER_ADMIN_PASSWORD` only take effect on a database with zero existing user credentials. They're safe to leave set in `.env` after first boot, but rotate the admin password afterward through the app itself rather than relying on them.
- Secrets belong in `.env`, which is gitignored by default (`.env.example` is the tracked template). Don't put real secrets in `docker-compose.yml`, `application.yml`, or a Dockerfile `ENV` layer.
- Backups (if enabled) push a `pg_dump` of the full database to a Git remote, including any secrets/PII stored in application tables. Make sure the backup repository has access controls appropriate to that.
- TLS is handled by Caddy via Let's Encrypt when `LEDGER_DOMAIN` is a real, publicly resolvable hostname. Leaving it unset falls back to a self-signed certificate — fine for local testing, not for anything reachable from the internet.
