# Backup Restore Drill — Log

Append-only record of monthly restore drills. See
`docs/operations/backup-runbook.md` § 4 for the procedure.

Format: `YYYY-MM-DD  PASS|FAIL  <dump-path>  rows=<N>  flyway=<version>  [notes]`

```
# example:
# 2026-04-01  PASS  daily/lumora_pos-2026-03-31.sql.gz  rows=84321  flyway=V37
```

---
