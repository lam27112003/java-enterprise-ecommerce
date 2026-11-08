# Phase 8 — Observability runbook (local)

## Stack

```bash
# App (requires non-default JWT_SECRET)
export JWT_SECRET="$(openssl rand -base64 48)"   # or PowerShell equivalent
docker compose -f infra/docker/docker-compose.yml --profile app up -d --build

# Metrics UI
docker compose -f infra/docker/docker-compose.yml --profile observability up -d
```

| Service | Host port (default) |
|---------|---------------------|
| Frontend | 3000 |
| Backend | 8080 |
| Prometheus | 9090 |
| Grafana | **3001** (not 3000) |

Grafana login defaults: `admin` / `admin` (override via `GRAFANA_ADMIN_*`).

## Secure Prometheus scrape (U1 / P8-D10)

- `ACTUATOR_PROMETHEUS_PUBLIC` must stay **`false`** for Docker.
- Scrape uses **HTTP Basic** on the Docker network:
  - Env: `ACTUATOR_PROMETHEUS_USERNAME` / `ACTUATOR_PROMETHEUS_PASSWORD`
  - Compose defaults: `prometheus` / `prometheus-local-change-me` (local only — change them)
- Prometheus config: `infra/docker/observability/prometheus/prometheus.yml` (`basic_auth`)

Verify:

```bash
# Anonymous must fail
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/actuator/prometheus
# expect 401

# Authorized scrape
curl -u prometheus:prometheus-local-change-me -s http://localhost:8080/actuator/prometheus | head
# expect jvm_ / http_server samples

curl -s http://localhost:9090/-/ready
```

**Forbidden:** setting `ACTUATOR_PROMETHEUS_PUBLIC=true` only to make scrape work.

Note: `application-local.yml` may still set prometheus public for IDE convenience — do **not** copy that into Docker.

## Correlation IDs (U3/U5)

- Response header `X-Correlation-Id`
- Logging pattern includes `%X{correlationId}`
- Invalid incoming IDs ignored (no HTTP 400)
- When `TRACING_ENABLED=true`, correlation id follows Micrometer/W3C traceId
- Never log JWTs, refresh tokens, Stripe client secrets, or PAN

## Optional OpenTelemetry (P8-D4)

```bash
export TRACING_ENABLED=true
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces
```

Default local = metrics-first (`TRACING_ENABLED=false`).

## Dashboards

Provisioned: **Ecommerce Platform Overview** (`ecommerce-platform-overview`) — HTTP rate, 4xx/5xx, p95/p99, JVM, HikariCP, rate_limit_rejections.

Business counters (if present) are **observational only** — never payment/order status authority.

## Rate-limit knobs

See `docs/SECURITY.md` / `application.yml` `app.security.rate-limit.*` (WP-A.2). Stripe webhooks are **not** on the business API limiter (U2).
