# Ledger

Self-hosted, Docker-based platform for tasks, files, docs, and notifications, built for small teams who want everything in one place instead of five different SaaS subscriptions.

Ledger is a single Spring Boot application backed by Postgres and MinIO, fronted by Caddy for automatic HTTPS. It ships with role-based access control, a pluggable integration system, and a scheduled Postgres-to-Git backup job, all deployable with one `docker compose up`.

## Features

- **Tasks** — create, assign, block, and track work through a status pipeline.
- **Wiki** — nested, path-addressed pages for team documentation.
- **Budget** — a simple ledger of budget entries in a configurable currency.
- **Files** — uploads stored in MinIO (S3-compatible object storage).
- **Search** — full-text search across the entire site.
- **Repos** — a read-only browser for a Git host's public repositories.
- **Email** — optional read-only view of a Gmail inbox via the Gmail API.
- **Users & roles** — permission-based roles (see [Permission](src/main/java/com/quietterminal/ledger/enums/Permission.java)), recovery codes, and session management on top of JWT auth.
- **Notifications** — Discord, Slack, Microsoft Teams, and generic outbound webhooks, all firing on the same set of events (task created/assigned/blocked/completed, new upload, new wiki page, new budget entry).
- **Integrations** — a small SPI (`Integration`, `Notifier`, `WebhookIntegration`) for wiring in inbound webhooks, outbound notifiers, or OAuth-backed clients without forking the app. See [SETUP_GUIDE.md](SETUP_GUIDE.md#adding-integrations).
- **Backups** — a scheduled `pg_dump` committed and pushed to a Git repo, with a companion [restore script](scripts/restore-backup.sh).

## Tech stack

Java 25, Spring Boot 4 (Web, Security, Data JPA, Validation), Postgres 17, Flyway migrations, MinIO, JWT auth (jjwt), Docker Compose, Caddy 2 (automatic HTTPS via ACME).

## Quick start

The fastest way to run Ledger is Docker Compose; it starts Postgres, MinIO, the app, and a Caddy reverse proxy with automatic TLS.

```bash
git clone https://github.com/Quiet-Terminal-Interactive/Ledger ledger
cd ledger
cp .env.example .env
# edit .env: set POSTGRES_PASSWORD, JWT_SECRET, LEDGER_MINIO_ACCESS_KEY/SECRET_KEY, LEDGER_ADMIN_USERNAME/PASSWORD, and LEDGER_DOMAIN
docker compose up -d
```

Visit `https://<LEDGER_DOMAIN>` (or `https://localhost` for a self-signed cert if you left `LEDGER_DOMAIN` unset) and log in with the admin credentials you set.

For the full walkthrough (every environment variable, running as a systemd-managed service, local development without Docker, and how to add new integrations), see [SETUP_GUIDE.md](SETUP_GUIDE.md).

## Development

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile runs against an in-memory H2 database with no external dependencies, and seeds an `admin` / `admin-password` account on first boot. See [SETUP_GUIDE.md](SETUP_GUIDE.md#local-development) for details, and [CONTRIBUTING.md](CONTRIBUTING.md) before opening a PR.

## Documentation

- [SETUP_GUIDE.md](SETUP_GUIDE.md) — full deployment, configuration, and integration guide.
- [CONTRIBUTING.md](CONTRIBUTING.md) — how to contribute.
- [SECURITY.md](SECURITY.md) — how to report a vulnerability.
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) — community expectations.

## License

[MIT](LICENSE)
