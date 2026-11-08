# Phase 8 — Local SLOs (aspirational)

**Scope:** Local Compose / developer workstation only. **Not** a production SLA.

| Indicator | Aspirational local target | Notes |
|-----------|---------------------------|-------|
| API availability (liveness) | UP during normal demos | `/actuator/health/liveness` |
| HTTP 5xx ratio | &lt; 1% of requests in quiet demos | Grafana 4xx/5xx panel |
| p95 latency (local) | &lt; 500 ms for catalog/cart reads | Excludes cold start / first Hit |
| Checkout/pay success (happy path) | Succeeds under WP-A.2 budgets | Rate limits must not block demos |
| Prometheus scrape | Target UP with Basic Auth | Public flag remains false |
| Money-path authority | Unchanged | Webhook sole `PAID`; metrics observational only |

Tune after evidence; do not invent production traffic assumptions (U6).
