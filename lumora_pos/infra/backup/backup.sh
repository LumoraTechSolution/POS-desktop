#!/usr/bin/env sh
# Lumora POS — pg_dump driver invoked by cron.
# Usage: backup.sh <slot>
#   slot ∈ daily | weekly | monthly | manual
#
# Reads:
#   PGHOST, PGPORT, PGUSER, PGPASSWORD, PGDATABASE
#   BACKUP_DIR        (default /backups)
#   OFFSITE_SYNC_CMD  (optional; runs after a successful dump)
#
# Retention is per-slot:
#   daily=14, weekly=8, monthly=6, manual=∞ (operator deletes)

set -eu

SLOT="${1:-daily}"
BACKUP_DIR="${BACKUP_DIR:-/backups}"
LOG_DIR="${BACKUP_DIR}/logs"
LOG_FILE="${LOG_DIR}/backup.log"

mkdir -p "${BACKUP_DIR}/${SLOT}" "${LOG_DIR}"

stamp() { date -u +"%Y-%m-%dT%H:%M:%SZ"; }

case "${SLOT}" in
  daily)   FILE="lumora_pos-$(date -u +%Y-%m-%d).sql.gz"; KEEP=14 ;;
  weekly)  FILE="lumora_pos-$(date -u +%Y-W%V).sql.gz";   KEEP=8  ;;
  monthly) FILE="lumora_pos-$(date -u +%Y-%m).sql.gz";    KEEP=6  ;;
  manual)  FILE="lumora_pos-$(date -u +%Y-%m-%dT%H-%M-%S).sql.gz"; KEEP=0 ;;
  *) echo "FAIL $(stamp) unknown slot=${SLOT}" >> "${LOG_FILE}"; exit 2 ;;
esac

OUT="${BACKUP_DIR}/${SLOT}/${FILE}"
TMP="${OUT}.partial"

# Custom-format dump (-Fc): smaller, supports table-level restore.
if pg_dump -Fc "${PGDATABASE}" 2>>"${LOG_FILE}" | gzip > "${TMP}"; then
  mv "${TMP}" "${OUT}"
  SIZE=$(wc -c < "${OUT}")
  echo "OK $(stamp) ${OUT} size=${SIZE}" >> "${LOG_FILE}"
else
  rm -f "${TMP}"
  echo "FAIL $(stamp) pg_dump failed for slot=${SLOT}" >> "${LOG_FILE}"
  exit 1
fi

# Prune older dumps in the same slot.
if [ "${KEEP}" -gt 0 ]; then
  # ls -1t lists newest first; tail -n +N drops the first N-1.
  ls -1t "${BACKUP_DIR}/${SLOT}/"lumora_pos-*.sql.gz 2>/dev/null \
    | tail -n +$((KEEP + 1)) \
    | while read -r OLD; do
        rm -f "${OLD}"
        echo "PRUNE $(stamp) ${OLD}" >> "${LOG_FILE}"
      done
fi

# Off-site sync (optional).
if [ -n "${OFFSITE_SYNC_CMD:-}" ]; then
  if sh -c "${OFFSITE_SYNC_CMD}" >>"${LOG_FILE}" 2>&1; then
    echo "SYNC OK $(stamp)" >> "${LOG_FILE}"
  else
    echo "SYNC FAIL $(stamp)" >> "${LOG_FILE}"
    exit 3
  fi
fi
