# Java Enterprise E-commerce Platform

Production-oriented modular monolith e-commerce platform.

| Item | Value |
|------|-------|
| Java | 21 LTS |
| Spring Boot | **3.5.16** |
| Frontend | React 18 + TypeScript + Vite + Tailwind |
| Database | PostgreSQL 16 + Flyway |
| Cache | Redis 7 |
| Messaging | Apache Kafka 3.9 (KRaft) |
| Payments | Stripe test mode (from Phase 6) |

## Phase status

**Phase 1 — Foundation** is implemented in this repository. Later phases require explicit approval.

Planning docs:

- [DEVELOPMENT_PLAN.md](./DEVELOPMENT_PLAN.md)
- [TASK_TRACKER.md](./TASK_TRACKER.md)

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 22+
- Docker Desktop / Compose v2 (for infra + full stack)

## Quick start (infra)

```bash
cp .env.example .env
docker compose -f infra/docker/docker-compose.yml up -d postgres redis kafka mailhog
```

## Backend

```bash
cd backend
# Full local stack (requires Compose infra running)
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Offline smoke without Docker (H2; Redis/Kafka disabled)
mvn spring-boot:run -Dspring-boot.run.profiles=standalone
```

Useful endpoints:

- `GET /api/v1/platform/info`
- `POST /api/v1/auth/register` / `login` / `refresh`
- `POST /api/v1/auth/logout` / `GET /api/v1/auth/me` (Bearer token)
- `GET /api/v1/customer/ping` / `GET /api/v1/admin/ping` (RBAC probes)
- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `GET /swagger-ui.html`

Set `JWT_SECRET` (min 32 chars) for non-test runs. See `docs/SECURITY.md`.

## Frontend

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173

## Tests

```bash
# Backend
cd backend && mvn test

# Optional Testcontainers (requires Docker)
cd backend && set RUN_TESTCONTAINERS=true&& mvn -Dtest=PostgresTestcontainersIT test

# Frontend
cd frontend && npm test
```

## Full stack via Compose

```bash
docker compose -f infra/docker/docker-compose.yml --profile app up --build
```

- API: http://localhost:8080
- Storefront: http://localhost:3000
- Mailhog UI: http://localhost:8025

## Configuration profiles

| Profile | Purpose |
|---------|---------|
| `local` | Developer machine against Compose infra |
| `docker` | Containers talking over the Compose network |
| `standalone` | Offline smoke without Docker (H2; Redis/Kafka off) |
| `test` | Automated tests (H2, Redis/Kafka autoconfig off) |
| `prod` | Production (env-provided secrets only) |

## Security note

Never commit real JWT secrets, DB passwords, or Stripe keys. Use `.env` locally and a secret manager in cloud environments.
