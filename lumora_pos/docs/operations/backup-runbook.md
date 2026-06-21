# Backup & Restore Runbook — Lumora POS

This runbook describes how the Lumora POS PostgreSQL database is backed up,
how to restore from a backup, and how the procedure is verified.

> **Audience.** Anyone with shell access to the host that runs the
> `lumora-pos-db` container (or the equivalent managed Postgres instance in
> production).

---

## TL;DR

- Nightly `pg_dump` runs at **03:00 UTC** via the `db-backup` service in
  `docker-compose.yml`.
- Dumps land in `./backups/` on the host (mounted into the container).
- Retention: **14 daily** + **8 weekly** + **6 monthly** (`backup.sh` prunes).
- A monthly **restore drill** must succeed before the runbook is considered green.

---

## 1. Backup schedule & layout

### File layout

```
./backups/
  daily/
    lumora_pos-2026-04-29.sql.gz       # rolling, last 14 kept
  weekly/
    lumora_pos-2026-W17.sql.gz          # rolling, last 8 kept (Sundays)
  monthly/
    lumora_pos-2026-04.sql.gz           # rolling, last 6 kept (1st of month)
  logs/
    backup.log                          # tail this when investigating
```

### Schedule

The `db-backup` container runs `cron`. Schedule:

| Slot     | When            | Retention |
|----------|-----------------|-----------|
| daily    | 03:00 UTC daily | 14 days   |
| weekly   | 04:00 UTC Sun   | 8 weeks   |
| monthly  | 05:00 UTC day 1 | 6 months  |

### Verification

Each successful dump appends a line to `backups/logs/backup.log` with
`OK <timestamp> <file> <size>`. A failure appends `FAIL <timestamp> <reason>`
and exits non-zero so monitoring can alert on it.

---

## 2. Manual backup

To take an ad-hoc dump (e.g. before a risky migration):

```bash
docker compose exec db-backup /scripts/backup.sh manual
# → ./backups/manual/lumora_pos-<timestamp>.sql.gz
```

Or, without the backup container, directly against the DB:

```bash
docker compose exec -T database \
    pg_dump -U lumora_user -Fc lumora_pos \
  | gzip > "lumora_pos-$(date +%Y-%m-%dT%H-%M-%S).sql.gz"
```

> Use `-Fc` (custom format) — it lets you restore individual tables and is
> roughly 2× smaller than `-Fp` (plain SQL).

---

## 3. Restore procedure

> **All restores destroy the target database.** Do this on a copy first
> unless you are explicitly recovering production from data loss.

### 3.1 Restore to a fresh local database (recommended for drills)

```bash
# 1. Pick a dump.
DUMP=./backups/daily/lumora_pos-2026-04-28.sql.gz

# 2. Spin up a throwaway Postgres for the restore.
docker run --rm -d --name pos-restore \
    -e POSTGRES_PASSWORD=restorepass \
    -p 5433:5432 \
    postgres:15-alpine

# 3. Restore.
gunzip -c "$DUMP" | \
  docker exec -i pos-restore \
    pg_restore -U postgres -d postgres --create --clean --if-exists

# 4. Smoke test.
docker exec -it pos-restore \
    psql -U postgres -d lumora_pos -c "SELECT COUNT(*) FROM sales;"

# 5. Tear down.
docker rm -f pos-restore
```

### 3.2 Restore over the live database (production recovery)

> **Stop the backend before restoring.** A live backend writing while we
> restore will leave the database in an inconsistent state.

```bash
# 1. Stop the backend (frontend can stay up — it'll just show errors).
docker compose stop backend

# 2. Drop & recreate the schema.
docker compose exec -T database \
    psql -U lumora_user -d postgres \
      -c "DROP DATABASE lumora_pos;" \
      -c "CREATE DATABASE lumora_pos OWNER lumora_user;"

# 3. Restore.
gunzip -c ./backups/daily/lumora_pos-YYYY-MM-DD.sql.gz | \
    docker compose exec -T database \
        pg_restore -U lumora_user -d lumora_pos --no-owner --role=lumora_user

# 4. Restart backend.
docker compose start backend

# 5. Verify Flyway sees the restored schema as up-to-date (no pending migrations).
docker compose logs backend | grep -i flyway | tail -20
```

### 3.3 Partial restore (single table)

```bash
gunzip -c "$DUMP" | \
    pg_restore -U lumora_user -d lumora_pos --data-only --table=sales
```

---

## 4. Monthly restore drill

> An untested backup is not a backup.

On the **1st of each month** (after the monthly dump runs at 05:00 UTC):

1. Pick yesterday's daily dump.
2. Run **3.1** end-to-end against a throwaway container.
3. Confirm:
   - Restore exit code is 0.
   - `SELECT COUNT(*) FROM sales` returns the count from the source DB ± 1 row
     (allow for the in-flight transaction at dump time).
   - `SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1`
     matches the latest migration in the repo.
4. Append a line to `docs/operations/backup-drill-log.md`:

   ```
   2026-04-01  PASS  daily/lumora_pos-2026-03-31.sql.gz  rows=84321  flyway=V37
   ```

5. If the drill **fails**, file an INC ticket and treat backups as RED until the
   root cause is fixed and the drill passes.

---

## 5. Off-site replication

The local `./backups/` directory is **not** sufficient on its own — a host
failure loses both the live DB and the backups.

**Required:** sync `./backups/` to off-site storage (S3, B2, or equivalent)
within 1 hour of each daily dump.

`backup.sh` will call `${OFFSITE_SYNC_CMD}` (env var) at the end of each run
if set. Example:

```bash
# In .env or compose environment block
OFFSITE_SYNC_CMD=aws s3 sync /backups s3://lumora-pos-backups/<host-id>/ --storage-class STANDARD_IA --delete
```

Leave unset in dev.

---

## 6. Secrets rotation

Backup files contain everything in the database, **including hashed credentials
and JWT secrets if they were stored in `tenants.settings`**. Treat them as
sensitive:

- Off-site bucket: encrypted at rest (SSE-KMS), restricted IAM (no public ACLs).
- Local `./backups/` directory: `chmod 700`, owned by the docker daemon user.
- Never commit a dump to git.

If a dump is exposed, rotate `JWT_SECRET` and force-revoke all sessions (see
`docs/audit/fix-plan.md` § Secret rotation).

---

## 7. Disaster scenarios — quick decision tree

| Scenario | What to do |
|---|---|
| Single dropped table, recent | 3.3 Partial restore from latest daily |
| Corrupt rows, point-in-time | Restore the last clean daily into a side DB (3.1), `pg_dump --table=...` the clean rows, import into live |
| Host lost, DB volume lost | Pull latest off-site dump → 3.2 against a fresh stack |
| Ransomware on host | Treat backups as suspect → fall back to off-site, monthly drill output as known-good baseline |

---

## 8. Files referenced

- `docker-compose.yml` — `db-backup` service definition
- `infra/backup/backup.sh` — the script the cron container runs
- `infra/backup/Dockerfile` — Postgres client + cron
- `docs/operations/backup-drill-log.md` — append-only drill record
