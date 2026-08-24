# Setup Guide

Everything needed to deploy Ledger, configure it, run it as a real service on a server, and extend it with new integrations.

- [Setup Guide](#setup-guide)
  - [Prerequisites](#prerequisites)
  - [Quick start (Docker Compose)](#quick-start-docker-compose)
  - [Configuration reference](#configuration-reference)
    - [Server / TLS](#server--tls)
    - [Database](#database)
    - [Auth](#auth)
    - [File storage (MinIO)](#file-storage-minio)
    - [Git repo browser](#git-repo-browser)
    - [Notifications](#notifications)
    - [Gmail (read-only inbox view)](#gmail-read-only-inbox-view)
    - [Budget](#budget)
    - [Branding](#branding)
    - [Backups](#backups)
  - [Running Ledger as a service on your server](#running-ledger-as-a-service-on-your-server)
    - [Option A — Docker Compose managed by systemd (recommended)](#option-a--docker-compose-managed-by-systemd-recommended)
    - [Option B — Bare-metal JAR under systemd](#option-b--bare-metal-jar-under-systemd)
  - [First boot \& admin bootstrap](#first-boot--admin-bootstrap)
  - [Backups \& restore](#backups--restore)
  - [Local development](#local-development)
  - [Adding integrations](#adding-integrations)
    - [Outbound notifier (e.g. a new chat webhook, like Discord/Slack/Teams)](#outbound-notifier-eg-a-new-chat-webhook-like-discordslackteams)
    - [Inbound webhook (something external pushes events into Ledger)](#inbound-webhook-something-external-pushes-events-into-ledger)
    - [OAuth-backed integration (like Gmail)](#oauth-backed-integration-like-gmail)
  - [API \& permissions overview](#api--permissions-overview)
  - [Troubleshooting](#troubleshooting)

## Prerequisites

For the recommended deployment (Docker Compose):

- Docker Engine 24+ and the Docker Compose plugin (`docker compose version`).
- A server with ports 80 and 443 reachable from the internet if you want Caddy to obtain a real Let's Encrypt certificate. Not required for local/LAN-only use.
- A DNS A/AAAA record pointing your chosen domain at the server, if using a real domain.

For bare-metal/no-Docker deployment, or local development:

- Java 25 (JDK).
- Maven 3.9+.
- A Postgres 17 instance (for anything beyond the `dev` profile).
- A MinIO instance (or any S3-compatible store) if you need file uploads.

## Quick start (Docker Compose)

```bash
git clone https://github.com/Quiet-Terminal-Interactive/Ledger ledger
cd ledger
cp .env.example .env
```

Edit `.env` and set at minimum:

| Variable | Why |
|---|---|
| `POSTGRES_PASSWORD` | Database password. |
| `JWT_SECRET` | `openssl rand -base64 32`. The app refuses to boot without this. |
| `LEDGER_MINIO_ACCESS_KEY` / `LEDGER_MINIO_SECRET_KEY` | Credentials MinIO is provisioned with, and that the app uses to talk to it. Any strings are fine, MinIO creates the account from these on first boot. |
| `LEDGER_ADMIN_USERNAME` / `LEDGER_ADMIN_PASSWORD` | Creates the first admin account. Ignored once any account exists, so it's safe to leave set. |
| `LEDGER_DOMAIN` | Real hostname for the server, e.g. `ledger.example.com`. Leave unset for a `localhost` self-signed cert. |

Then:

```bash
docker compose up -d
docker compose logs -f app   # watch it come up
```

Compose brings up, in order: Postgres (with a healthcheck), MinIO, a one-shot `minio-init` job that creates the configured buckets, the app itself (waiting on both), and Caddy in front of it handling TLS. The app is not published directly, only Caddy's 80/443 are exposed on the host; everything else stays on the Compose-internal network.

Visit `https://<LEDGER_DOMAIN>` (or `https://localhost`) and log in with the admin credentials from `.env`.

## Configuration reference

All configuration is environment-driven — see [`.env.example`](.env.example) for the canonical, commented list, and [`application.yml`](src/main/resources/application.yml) for how each variable maps to a Spring property (useful if you're running outside Docker Compose and setting JVM system properties or a different `application.yml` instead of env vars).

### Server / TLS

| Variable | Default | Notes |
|---|---|---|
| `LEDGER_DOMAIN` | `localhost` | Hostname Caddy serves and requests a cert for. |
| `LEDGER_ACME_EMAIL` | — | Contact address for Let's Encrypt expiry notices. Optional. |

### Database

| Variable | Default | Notes |
|---|---|---|
| `POSTGRES_DB` | `ledger` | |
| `POSTGRES_USER` | `ledger` | |
| `POSTGRES_PASSWORD` | — | Required. |
| `JPA_DDL_AUTO` | `validate` | Schema is managed by Flyway migrations, not Hibernate, leave this as `validate` in production. |

### Auth

| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET` | — | Required, base64, ≥256 bits. `openssl rand -base64 32`. |
| `JWT_EXPIRATION_MS` | `2592000000` (30 days) | Token lifetime. |
| `LEDGER_ADMIN_USERNAME` / `LEDGER_ADMIN_PASSWORD` | — | First-run only; creates the initial `Admin`-role account. See [First boot](#first-boot--admin-bootstrap). |
| `LEDGER_ADMIN_FIRST_NAME` / `LEDGER_ADMIN_LAST_NAME` | `Admin` / `User` | |

### File storage (MinIO)

| Variable | Default | Notes |
|---|---|---|
| `LEDGER_MINIO_ENDPOINT` | `http://localhost:9000` | Set by Compose automatically for the `app` service; only needed manually outside Compose. |
| `LEDGER_MINIO_ACCESS_KEY` / `LEDGER_MINIO_SECRET_KEY` | — | Required. Also used to bootstrap the MinIO root account in Compose. |
| `LEDGER_MINIO_BUCKETS` | `ledger-uploads` | Comma-separated. Compose's `minio-init` service creates these; outside Compose you must create them yourself. |

### Git repo browser

| Variable | Default | Notes |
|---|---|---|
| `LEDGER_GIT_URL` | — | Base URL of the Git host's API. |
| `LEDGER_GIT_API_TOKEN` | — | Read token for that host. |
| `LEDGER_GIT_LISTED_USERS` | — | Comma-separated usernames whose **public** repos are shown, regardless of what the token itself can see. |

### Notifications

All four channels fire on the same events (task created/assigned/blocked/completed, new upload, new wiki page, new budget entry). Each is independent and off by default, enable any combination.

| Variable | Default | Notes |
|---|---|---|
| `LEDGER_DISCORD_ENABLED` / `LEDGER_DISCORD_WEBHOOK_URL` | `false` / — | |
| `LEDGER_SLACK_ENABLED` / `LEDGER_SLACK_WEBHOOK_URL` | `false` / — | |
| `LEDGER_TEAMS_ENABLED` / `LEDGER_TEAMS_WEBHOOK_URL` | `false` / — | |
| `LEDGER_WEBHOOK_ENABLED` / `LEDGER_WEBHOOK_URL` | `false` / — | Generic outbound webhook for anything without a first-class integration. |

### Gmail (read-only inbox view)

| Variable | Default | Notes |
|---|---|---|
| `LEDGER_GMAIL_ENABLED` | `false` | |
| `LEDGER_GMAIL_CLIENT_ID` / `LEDGER_GMAIL_CLIENT_SECRET` | — | OAuth app credentials. |
| `LEDGER_GMAIL_REFRESH_TOKEN` | — | Long-lived refresh token obtained out-of-band (one-time OAuth consent) for the mailbox account. The app always reads "me" as resolved by this token. |

### Budget

| Variable | Default | Notes |
|---|---|---|
| `LEDGER_BUDGET_CURRENCY` | `USD` | ISO 4217 code budget amounts are denominated in. |

### Branding

| Variable | Default | Notes |
|---|---|---|
| `LEDGER_LOGO_URL` | — | Optional image URL shown next to the product name on the login page. Falls back to the built-in mark if unset. |

### Backups

| Variable | Default | Notes |
|---|---|---|
| `LEDGER_BACKUP_ENABLED` | `false` | Requires `pg_dump` and `git` on `PATH` inside the container, plus a repo already cloned with push credentials configured. Not wired into `docker-compose.yml` by default — see [Backups & restore](#backups--restore). |
| `LEDGER_BACKUP_CRON` | `0 0 3 * * *` | Quartz-style cron expression; default is daily at 03:00. |
| `LEDGER_BACKUP_REPO_DIR` | — | Local path to the git clone dumps are written into (`<repo-dir>/backups`). |
| `LEDGER_BACKUP_GIT_REMOTE` | `origin` | |
| `LEDGER_BACKUP_GIT_BRANCH` | `main` | |

## Running Ledger as a service on your server

Two supported paths depending on whether you want Docker in the loop.

### Option A — Docker Compose managed by systemd (recommended)

This keeps Docker Compose as the runtime but makes systemd responsible for starting it on boot and restarting it if it crashes, so you get `systemctl status ledger`, `journalctl -u ledger`, and boot-time startup for free.

1. Clone the repo and configure `.env` on the server, e.g. under `/opt/ledger`:

   ```bash
   sudo mkdir -p /opt/ledger
   sudo chown "$USER" /opt/ledger
   git clone https://github.com/Quiet-Terminal-Interactive/Ledger /opt/ledger
   cd /opt/ledger
   cp .env.example .env
   # edit .env as in the Quick start section above
   ```

2. Create `/etc/systemd/system/ledger.service`:

   ```ini
   [Unit]
   Description=Ledger (Docker Compose)
   Requires=docker.service
   After=docker.service network-online.target
   Wants=network-online.target

   [Service]
   Type=oneshot
   RemainAfterExit=yes
   WorkingDirectory=/opt/ledger
   ExecStart=/usr/bin/docker compose up -d
   ExecStop=/usr/bin/docker compose down
   TimeoutStartSec=0

   [Install]
   WantedBy=multi-user.target
   ```

3. Enable and start it:

   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable --now ledger
   systemctl status ledger
   ```

4. To deploy an update:

   ```bash
   cd /opt/ledger
   git pull
   docker compose build app   # only needed if the app image changed
   sudo systemctl restart ledger
   ```

### Option B — Bare-metal JAR under systemd

Use this if you don't want a Docker daemon on the host at all. You're responsible for running Postgres and MinIO yourself (locally, on another host, or as managed services) and pointing Ledger's env vars at them.

1. Build the JAR:

   ```bash
   mvn -DskipTests package
   sudo mkdir -p /opt/ledger
   sudo cp target/ledger.jar /opt/ledger/
   ```

2. Create a dedicated system user and an env file it can read but others can't:

   ```bash
   sudo useradd --system --no-create-home --shell /usr/sbin/nologin ledger
   sudo cp .env /etc/ledger.env   # or hand-write it with real host/port values
   sudo chown ledger:ledger /etc/ledger.env
   sudo chmod 600 /etc/ledger.env
   ```

   Since there's no Compose network doing hostname resolution here, make sure `/etc/ledger.env` sets real reachable values for `POSTGRES_HOST`, `POSTGRES_PORT`, and `LEDGER_MINIO_ENDPOINT` (Compose normally injects the first two automatically; bare-metal needs them set explicitly).

3. Create `/etc/systemd/system/ledger.service`:

   ```ini
   [Unit]
   Description=Ledger
   After=network-online.target
   Wants=network-online.target

   [Service]
   Type=simple
   User=ledger
   Group=ledger
   EnvironmentFile=/etc/ledger.env
   ExecStart=/usr/bin/java -jar /opt/ledger/ledger.jar
   Restart=on-failure
   RestartSec=5

   [Install]
   WantedBy=multi-user.target
   ```

4. Enable and start it:

   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable --now ledger
   journalctl -u ledger -f
   ```

5. Put a reverse proxy (Caddy, nginx) in front of port 8080 for TLS; the bundled `Caddyfile` works standalone too, run it as its own systemd/Docker service pointed at `reverse_proxy 127.0.0.1:8080`.

Either option, the app exposes `GET /health` (unauthenticated) returning `200 OK`, which is what the container `HEALTHCHECK` and Compose's dependency ordering rely on, point any external uptime monitor at the same path.

## First boot & admin bootstrap

On first startup, `AdminBootstrapRunner` seeds two built-in roles, `Admin` (every permission) and `Member` (every permission except `USERS_MANAGE`/`ROLES_MANAGE`), then checks whether any user credentials exist at all.

- If none exist and `LEDGER_ADMIN_USERNAME`/`LEDGER_ADMIN_PASSWORD` are set, it creates that account with the `Admin` role and logs a confirmation.
- If none exist and those variables are unset, it logs a warning and starts anyway with no accounts, set the variables and restart to bootstrap.
- If any account already exists, both variables are ignored, so it's safe to leave them in `.env` permanently.

After first login, manage further users/roles through the app (`/users`, `/roles`) rather than environment variables.

## Backups & restore

When `LEDGER_BACKUP_ENABLED=true`, `BackupScheduler` runs `pg_dump` on the configured cron schedule, writes the dump into `<LEDGER_BACKUP_REPO_DIR>/backups`, and commits + pushes it via `BackupGitPublisher`. This requires:

- `pg_dump` and `git` binaries available on `PATH` inside wherever the app runs (the stock `Dockerfile` image does not include them, you'd need a custom image or an `apt`/`apk install` layer).
- `LEDGER_BACKUP_REPO_DIR` already a git clone of the target backup repository, with push credentials already configured (e.g. an SSH deploy key with write access, or a stored HTTPS credential), the app does not handle authentication itself, it shells out to `git push`.

This is why it's off by default and not wired into `docker-compose.yml`: it needs a volume mount for the clone/credentials that's specific to your backup destination. To enable it under Compose, add something like this to the `app` service:

```yaml
    volumes:
      - /opt/ledger/backup-repo:/backup-repo:rw
      - /opt/ledger/backup-ssh-key:/home/ledger/.ssh/id_ed25519:ro
```

with `LEDGER_BACKUP_REPO_DIR=/backup-repo` in `.env`, and `/opt/ledger/backup-repo` pre-cloned on the host with the matching deploy key already trusted (`ssh-keyscan` the Git host into a known_hosts file mounted alongside it).

Restoring a dump:

```bash
POSTGRES_PASSWORD=... ./scripts/restore-backup.sh path/to/dump.sql
```

The target database must already exist and be empty (e.g. a fresh `docker compose up` before the app has run migrations, or a fresh Postgres instance with Flyway not yet applied). See the script's usage output (`./scripts/restore-backup.sh` with no args) for the full list of env vars it reads.

## Local development

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

The `dev` profile ([`application-dev.yml`](src/main/resources/application-dev.yml)):

- Runs against an in-memory H2 database (`ddl-auto: create-drop`, Flyway disabled), no Postgres needed.
- Exposes the H2 console at `/h2-console` (only permitted by `SecurityConfig` when the `dev` profile is active).
- Seeds a fixed JWT secret and a default `admin`/`admin-password` account.
- Points the Git and MinIO integrations at placeholder values — file uploads and the repo browser won't actually work unless you override `LEDGER_MINIO_*`/`LEDGER_GIT_*` env vars with real ones, or run the supporting containers locally (`docker compose up postgres minio minio-init` and point at those instead of the H2/placeholder dev config).

Run tests with:

```bash
mvn test
```

Most integration tests use Testcontainers to start a throwaway Postgres container per test class, so Docker needs to be running locally even though the `dev` profile itself doesn't need it.

## Adding integrations

Ledger's integration system is a small SPI under `com.quietterminal.ledger.integration`, used for both Git and Gmail out of the box. Everything implements the base `Integration` interface:

```java
public interface Integration {
    String id();
    String displayName();
    IntegrationKind kind(); // OAUTH_APP, OUTBOUND_WEBHOOK, INBOUND_WEBHOOK
}
```

Registered Spring beans that implement `Integration` are automatically picked up by `IntegrationRegistry` and listed at `GET /integrations`, there's no separate registration step.

### Outbound notifier (e.g. a new chat webhook, like Discord/Slack/Teams)

Extend `AbstractWebhookNotifier`, which handles posting a JSON body with a single configurable field to a webhook URL and gate it behind a `ConditionalOnProperty` so it only activates when explicitly enabled:

```java
@Component
@ConditionalOnProperty(name = "ledger.mattermost.enabled", havingValue = "true")
public class MattermostNotifier extends AbstractWebhookNotifier {

    public MattermostNotifier(RestTemplate restTemplate,
            @Value("${ledger.mattermost.webhook-url}") String webhookUrl) {
        super(restTemplate, webhookUrl, "text",
                "ledger.mattermost.webhook-url (env LEDGER_MATTERMOST_WEBHOOK_URL)",
                "mattermost", "Mattermost");
    }
}
```

Then add matching properties to `application.yml` and `.env.example`:

```yaml
  mattermost:
    enabled: ${LEDGER_MATTERMOST_ENABLED:false}
    webhook-url: ${LEDGER_MATTERMOST_WEBHOOK_URL:}
```

It'll now fire automatically on every event `NotificationEventListener` already listens for, no changes needed there.

### Inbound webhook (something external pushes events into Ledger)

Implement `WebhookIntegration`; `handle` receives the raw request and returns an optional message to fan out to every registered `Notifier`:

```java
@Component
public class GithubWebhookIntegration implements WebhookIntegration {

    @Override public String id() { return "github"; }
    @Override public String displayName() { return "GitHub"; }

    @Override
    public Optional<String> handle(HttpHeaders headers, String rawBody) {
        // verify signature header, parse rawBody, decide whether/what to notify
        return Optional.of("New GitHub event received");
    }
}
```

It's automatically reachable at `POST /integrations/webhooks/github` (permitted without auth in `SecurityConfig`, since the caller is an external service, verify the payload's own signature/secret inside `handle` instead).

### OAuth-backed integration (like Gmail)

Extend `AbstractOAuthClient`, which handles refresh-token → access-token exchange and caching:

```java
public class JiraClient extends AbstractOAuthClient {
    public JiraClient(RestTemplate restTemplate, String clientId, String clientSecret, String refreshToken) {
        super(restTemplate, URI.create("https://auth.atlassian.com/oauth/token"),
                clientId, clientSecret, refreshToken,
                "ledger.jira.client-id/client-secret/refresh-token");
    }

    @Override
    protected RuntimeException tokenRefreshFailed(String message) {
        return new JiraUpstreamException(message);
    }

    // add methods that call ensureAccessToken() and hit the Jira API
}
```

Wrap it the way `GmailService`/`AuthController`'s Gmail wiring does: a `@ConditionalOnProperty`-gated `@Service` that implements `Integration` with `kind() == OAUTH_APP`, its own controller for whatever read/write surface you need, and matching `ledger.jira.*` properties + env vars in `application.yml`/`.env.example`. Add credentials the same one-time-OAuth-consent way as the Gmail refresh token as Ledger never runs an interactive OAuth flow itself.

## API & permissions overview

Every endpoint except `/health`, `/auth/login`, `/auth/recovery-login`, `/branding`, static assets, and inbound webhooks requires a valid JWT (`Authorization: Bearer <token>`, obtained from `/auth/login`). Beyond authentication, most resource endpoints are additionally gated by a permission on the caller's role (see [`Permission`](src/main/java/com/quietterminal/ledger/enums/Permission.java) and [`SecurityConfig`](src/main/java/com/quietterminal/ledger/config/SecurityConfig.java)):

| Resource | Read | Write |
|---|---|---|
| `/tasks/**` | `TASKS_READ` | `TASKS_WRITE` |
| `/budget/**` | `BUDGET_READ` | `BUDGET_WRITE` |
| `/wiki/**` | `WIKI_READ` | `WIKI_WRITE` |
| `/uploads/**` | `FILES_READ` | `FILES_WRITE` |
| `/email/**` | `MAIL_READ` | — (read-only) |
| `/search/**` | `SEARCH_READ` | — |
| `/repos/**` | `REPOS_READ` | — |
| `/users/**` | authenticated (GET) | `USERS_MANAGE` |
| `/roles/**` | authenticated (GET) | `ROLES_MANAGE` |

The seeded `Admin` role has every permission; `Member` has every permission except `USERS_MANAGE` and `ROLES_MANAGE`. Custom roles can be created with any subset via `/roles`.

## Troubleshooting

**App container keeps restarting / never becomes healthy** — check `docker compose logs app`. Common causes: `JWT_SECRET` unset or too short (the app refuses to start), or Postgres not yet accepting connections (Compose's healthcheck/`depends_on` should prevent this, but a very slow first-boot volume init can still race it, just restart the service).

**Login works but file uploads fail** — check the buckets in `LEDGER_MINIO_BUCKETS` actually exist and match what MinIO has. Under Compose, `minio-init` creates them from that same variable on first boot; if you changed the bucket list after the first `docker compose up`, run `docker compose up minio-init` again (or create the bucket manually via `mc` / the MinIO console at `:9001`).

**Self-signed certificate warning in the browser** — expected when `LEDGER_DOMAIN` is unset/`localhost`. Set a real, publicly resolvable domain in `.env` and restart for Caddy to obtain a real Let's Encrypt certificate.

**Backups aren't running** — confirm `LEDGER_BACKUP_ENABLED=true`, that `pg_dump`/`git` are actually present in the container (not true of the stock image — see [Backups & restore](#backups--restore)), and that `LEDGER_BACKUP_REPO_DIR` is a mounted, pre-cloned, writable repo with working push credentials.
