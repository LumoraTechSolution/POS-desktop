# Lumora POS — backup container.
# Postgres 15 client (matches the database image) + busybox cron.
FROM postgres:15-alpine

RUN apk add --no-cache dcron tini

COPY backup.sh /scripts/backup.sh
COPY crontab   /etc/crontabs/root
RUN chmod +x /scripts/backup.sh

# tini reaps zombies from cron-spawned shells.
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["crond", "-f", "-l", "8"]
