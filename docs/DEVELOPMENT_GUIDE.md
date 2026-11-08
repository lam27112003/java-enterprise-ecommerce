# Development Guide (Phase 1)

## Repository layout

```text
backend/     Spring Boot 3.5.16 modular monolith
frontend/    React + TypeScript + Vite + Tailwind
infra/docker Docker Compose for Postgres, Redis, Kafka, Mailhog, apps
.github/     CI workflows
docs/        Architecture and operations docs
```

## Backend conventions

- Package root: `com.ecommerce.platform`
- Modules land under dedicated packages (`auth`, `product`, …) starting Phase 2+
- Controllers return DTOs / `ApiResponse`, never JPA entities
- Flyway owns schema; `ddl-auto=validate`

## Local toolchain

1. JDK 21 on `PATH` / `JAVA_HOME`
2. Maven 3.9+
3. Node 22 + npm
4. Docker Compose for dependencies

## Running profiles

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Environment overrides are documented in `.env.example`.

## CI

GitHub Actions workflow `.github/workflows/ci.yml` runs backend `mvn verify` and frontend lint/test/build on push and pull requests.
