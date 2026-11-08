# Docker Guide (Phase 1)

## Services

| Service | Image | Ports |
|---------|-------|-------|
| postgres | postgres:16-alpine | 5432 |
| redis | redis:7-alpine | 6379 |
| kafka | apache/kafka:3.9.1 (KRaft) | 9092 |
| mailhog | mailhog/mailhog:v1.0.1 | 1025, 8025 |
| backend | local build | 8080 (`--profile app`) |
| frontend | local build | 3000 (`--profile app`) |

## Commands

```bash
# Infrastructure only
docker compose -f infra/docker/docker-compose.yml up -d postgres redis kafka mailhog

# Full application stack
docker compose -f infra/docker/docker-compose.yml --profile app up --build

# Tear down
docker compose -f infra/docker/docker-compose.yml --profile app down -v
```

## Secrets

Copy `.env.example` to `.env`. Compose reads it automatically from the working directory when present; for this layout you can also export variables or place `.env` beside the compose file.

Do not put production credentials in images or compose files.
