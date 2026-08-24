#!/usr/bin/env bash
# Restores a Ledger Postgres backup dump produced by the scheduled backup job. See README.md "Backups" section for the full disaster-recovery flow.
set -euo pipefail

usage() {
    echo "Usage: $0 <path-to-dump.sql>" >&2
    echo "" >&2
    echo "Restores a plain-SQL Ledger backup into POSTGRES_DB. The target database must" >&2
    echo "already exist and be empty (e.g. freshly created by docker compose up)." >&2
    echo "" >&2
    echo "Required env vars: POSTGRES_PASSWORD" >&2
    echo "Optional env vars: POSTGRES_HOST (default localhost), POSTGRES_PORT (default 5432)," >&2
    echo "                   POSTGRES_DB (default ledger), POSTGRES_USER (default ledger)" >&2
    exit 1
}

[ $# -eq 1 ] || usage
DUMP_FILE="$1"

if [ ! -f "$DUMP_FILE" ]; then
    echo "No such file: $DUMP_FILE" >&2
    exit 1
fi

: "${POSTGRES_HOST:=localhost}"
: "${POSTGRES_PORT:=5432}"
: "${POSTGRES_DB:=ledger}"
: "${POSTGRES_USER:=ledger}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}"

export PGPASSWORD="$POSTGRES_PASSWORD"

echo "Restoring $DUMP_FILE into ${POSTGRES_USER}@${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}..."
psql -h "$POSTGRES_HOST" -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
    -v ON_ERROR_STOP=1 -f "$DUMP_FILE"

echo "Restore complete."
