# Contributing to Ledger

Thanks for taking the time to contribute. This document covers how to get a dev environment running, the expectations for a pull request, and where things live.

## Getting set up

You need Java 25 and Maven. No Docker is required for day-to-day development, the `dev` Spring profile runs against an in-memory H2 database.

```bash
git clone https://github.com/Quiet-Terminal-Interactive/Ledger ledger
cd ledger
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

This seeds an `admin` / `admin-password` account (see [application-dev.yml](src/main/resources/application-dev.yml)) and serves the app at `http://localhost:8080`. Full details, including how to point the dev profile at real MinIO/Git/Gmail integrations, are in [SETUP_GUIDE.md](SETUP_GUIDE.md#local-development).

## Running tests

```bash
mvn test
```

Most integration tests use Testcontainers to spin up a real Postgres instance, so Docker must be running locally. Unit tests that don't need a database run without it.

## Before opening a PR

- Run `mvn test` and make sure it's green.
- Keep changes scoped to the problem you're solving, avoid unrelated refactors or formatting churn in the same PR.
- Follow the existing code style: no doc comments on self-explanatory code; only comment on non-obvious why (a workaround, a subtle invariant, a hidden constraint).
- Add or update tests for any behavior change. New controllers/services should have both a focused unit test and, where they touch the database, an integration test (see existing `*IntegrationTest.java` for the pattern).
- If you're changing configuration (`application.yml`, `.env.example`, `docker-compose.yml`), update [SETUP_GUIDE.md](SETUP_GUIDE.md) in the same PR, it's the source of truth for deployment and is easy to let drift.
- Database schema changes go in a new Flyway migration under `src/main/resources/db/migration/`, never by editing an already-shipped migration.

## Adding an integration

If your change adds a new outbound notifier, inbound webhook, or OAuth-backed integration, implement it against the SPI in `com.quietterminal.ledger.integration` rather than special-casing it elsewhere. See [SETUP_GUIDE.md](SETUP_GUIDE.md#adding-integrations) for the pattern and a worked example.

## Commit messages

Write commit messages that explain why a change was made, not just what changed — the diff already shows the what. Keep the subject line under ~70 characters.

## Reporting bugs / requesting features

Open an issue with steps to reproduce (for bugs) or the problem you're trying to solve (for features). For security issues, do **not** open a public issue, see [SECURITY.md](SECURITY.md).

## Code of conduct

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you're expected to uphold it.
