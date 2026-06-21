# Observability — Lumora POS

How metrics and logs are collected, where they live, and how to read them.

---

## Metrics — Prometheus + Grafana

Both run as opt-in Docker Compose services under the `monitoring` profile:

```bash
docker compose --profile monitoring up -d
```

| Service    | URL                       | Notes                                |
|------------|---------------------------|--------------------------------------|
| Prometheus | http://localhost:9090     | Raw metrics + ad-hoc queries         |
| Grafana    | http://localhost:3001     | Dashboards (`admin` / `admin` by default) |

### What we expose

The backend exposes `/actuator/prometheus` (Micrometer + Prometheus registry).
Useful metric families:

| Metric                                     | Use                              |
|--------------------------------------------|----------------------------------|
| `http_server_requests_seconds_count`       | Request rate, by status / URI    |
| `http_server_requests_seconds_bucket`      | Latency histograms (p95/p99)     |
| `hikaricp_connections_active`              | DB pool pressure                 |
| `jvm_memory_used_bytes`                    | Heap / non-heap memory           |
| `flyway_migrations`                        | Migration count (sanity check)   |

### Default dashboard

The provisioned dashboard ("Lumora POS — Backend") panels:

- Request rate (total + by status)
- p95 / p99 latency
- 5xx error rate
- DB pool — active / idle / pending
- JVM heap used
- Flyway migrations applied

Edit it inline; changes persist in the `grafana_data` volume.

### Security note

`/actuator/prometheus` is `permitAll` at the Spring Security layer so the
in-network Prometheus container can scrape without auth. **In production this
endpoint must NOT be exposed to the public internet.** Two acceptable
deployments:

1. Bind the container only to an internal Docker network (default for the
   `monitoring` profile — Prometheus reaches `backend:8081` via the compose
   network, never via a public port).
2. Move actuator to a separate management port via `management.server.port`
   and firewall it.

---

## Logs

### Format

- `prod` profile → JSON to stdout (logstash-logback-encoder).
- everything else → human-readable pattern with `[correlationId]` prefix.

### Correlation IDs

Every request gets an `X-Correlation-Id` header (echoed in the response).
The same value is set on the SLF4J MDC, so:

- JSON logs include it as a top-level `correlationId` field.
- Pattern logs include it inside `[ ]` after the level.

When a user reports an issue, ask them for the `X-Correlation-Id` from the
failing response and grep your log aggregator for it.

Implementation: `CorrelationIdFilter` (HIGHEST_PRECEDENCE) in
`com.lumora.pos.config`.

### Levels

| Profile | `com.lumora.pos` | `org.springframework` | `org.hibernate` |
|---------|------------------|-----------------------|-----------------|
| dev     | INFO             | INFO (defaults)       | WARN            |
| prod    | WARN             | WARN                  | WARN            |

To bump a single deploy to INFO during incident triage:

```bash
LOGGING_LEVEL_COM_LUMORA_POS=INFO docker compose up -d backend
```

---

## Alerts (not yet provisioned)

The dashboard is the eyeball-pane today. Suggested first alerts (paste into
Grafana → Alerting once you have a notification channel):

- 5xx rate > 1 req/s for 5 min → page
- p95 latency > 1s for 10 min → warn
- HikariCP pending connections > 0 for 1 min → warn
- Backend `up` == 0 for 1 min → page
