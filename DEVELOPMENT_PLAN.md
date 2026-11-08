# Java Enterprise E-commerce Platform — Development Plan

**Status:** Phase 8 — **COMPLETE** (local portfolio; `docs/PHASE8_COMPLETION_REPORT.md`); Failsafe Docker ITs re-verify pending host Docker; **not** production-certified; Phase 9 not started
**Created:** 2026-11-04
**Updated:** 2026-11-04 — Phase 2 authentication/authorization implemented and verified (18 tests)
**Architecture style:** Modular monolith (package-by-module)
**Local-first:** Docker Compose → Kubernetes (local/kind) → AWS EKS (approval required)
**Project path:** `C:\Users\84919\source\repos\java-enterprise-ecommerce`

---

## 0. Workspace Analysis

| Finding | Decision |
|---------|----------|
| Cursor session started with no meaningful project root | New project created at `C:\Users\84919\source\repos\java-enterprise-ecommerce` |
| Existing `C:\Users\84919\ecommerce` contains only empty Laravel-style `resources/views` stubs | **Not reused.** Unrelated and incomplete. New greenfield project. |
| No existing backend/frontend/infra code for this product | Full greenfield build |

---

## 1. Locked Technology Stack (Verified 2026-11-04)

### 1.1 Decision (confirmed)

| Decision | Value |
|----------|-------|
| Spring Boot line | **3.5.x** (user confirmed) |
| Exact Boot patch | **3.5.16** — final OSS release on Maven Central (published 2026-06-25); no newer community 3.5.x patches after OSS EOL 2026-06-30 |
| Java | **21 LTS** (Boot 3.5 supports Java 17–25) |
| Payment | **Stripe test mode** (PaymentIntents + webhooks) |
| Project directory | `C:\Users\84919\source\repos\java-enterprise-ecommerce` |
| Architecture / roadmap | Unchanged modular monolith + **11-phase** plan |

> **Note:** Versions `3.5.17+` appearing on some commercial NES catalogs are **not** Maven Central OSS artifacts. This project pins **3.5.16** from Maven Central unless you later approve a commercial support channel.

**Source of BOM versions:** [`spring-boot-dependencies:3.5.16`](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.16/spring-boot-dependencies-3.5.16.pom)

### 1.2 Core platform (BOM-managed — do not override)

| Component | Version | Starter / artifact | Compatibility notes |
|-----------|---------|--------------------|---------------------|
| **Spring Boot** | **3.5.16** | `spring-boot-starter-parent` | Parent POM + dependency management |
| **Spring Framework** | **6.2.19** | managed | Required by Boot 3.5.16 |
| **Spring Security** | **6.5.11** | `spring-boot-starter-security` | JWT resource-server patterns + custom JWT filter OK |
| **Spring Data BOM** | **2025.0.13** | — | Aligns Spring Data JPA / Redis |
| **Spring Data JPA** | via Data BOM | `spring-boot-starter-data-jpa` | Hibernate ORM underneath |
| **Hibernate ORM** | **6.6.53.Final** | managed | Compatible with Jakarta Persistence 3.1 |
| **Spring Kafka** | **3.3.16** | `spring-kafka` | Matches Boot 3.5 Kafka auto-config |
| **Apache Kafka clients** | **3.9.2** | managed by Boot | Broker image: Kafka **3.9.x** (KRaft) |
| **Spring Data Redis** | via Data BOM | `spring-boot-starter-data-redis` | Cache + rate-limit counters |
| **Lettuce** (Redis driver) | **6.6.0.RELEASE** | default Redis client | Prefer Lettuce over Jedis |
| **Jedis** (available, not default) | **6.0.0** | managed | Do **not** use unless required |
| **Flyway** | **11.7.2** | `flyway-core` + `flyway-database-postgresql` | Schema migrations |
| **PostgreSQL JDBC** | **42.7.11** | `postgresql` | Server: **PostgreSQL 16** |
| **HikariCP** | **6.3.3** | managed | Connection pool |
| **Tomcat (embedded)** | **10.1.55** | via `spring-boot-starter-web` | Servlet 6.0 |
| **Jackson** | **2.21.4** (BOM) | managed | JSON for APIs + JJWT Jackson module |
| **Validation** | Jakarta Validation via Boot | `spring-boot-starter-validation` | Bean Validation 3.0 |
| **Actuator** | Boot 3.5.16 | `spring-boot-starter-actuator` | Health + Prometheus endpoint |
| **Micrometer** | **1.15.12** | managed | Metrics |
| **Micrometer Tracing** | **1.5.12** | managed | Bridges to OpenTelemetry |
| **OpenTelemetry** | **1.49.0** | managed BOM alignment | Observability phase |
| **Logback** | **1.5.34** | managed | Logging |
| **SLF4J** | **2.0.18** | managed | Logging API |
| **Maven** | **3.9.x+** | — | Boot minimum 3.6.3; use 3.9+ |

### 1.3 Test stack (BOM-managed)

| Component | Version | Notes |
|-----------|---------|-------|
| JUnit Jupiter | **5.12.2** | Unit + integration |
| Mockito | **5.17.0** | Mocking |
| AssertJ | **3.27.7** | Assertions |
| Awaitility | **4.2.2** | Async assertions (Kafka/webhooks) |
| Testcontainers | **1.21.4** | PostgreSQL, Redis, Kafka modules |
| Spring Boot Test | **3.5.16** | `@SpringBootTest`, `@DataJpaTest`, `@WebMvcTest` |
| spring-kafka-test | managed with Spring Kafka **3.3.16** | Embedded/broker test support where needed |

### 1.4 Explicit third-party libraries (not in Boot BOM — pinned for Boot 3.5.16)

| Library | Version | Purpose | Compatibility |
|---------|---------|---------|---------------|
| **springdoc-openapi** `springdoc-openapi-starter-webmvc-ui` | **2.10.0** | Swagger UI / OpenAPI 3 | Explicitly built for **Spring Boot 3.5.x / 3.5.16** (v3.x line is for Boot 4) |
| **JJWT** `jjwt-api` / `jjwt-impl` / `jjwt-jackson` | **0.13.0** | Access-token create/parse | Maintained; Java 21 OK; use split modules (not legacy `jjwt` uber-jar) |
| **stripe-java** | **34.0.0** | Stripe PaymentIntents + webhooks (test mode) | Maintained SDK; keys via env only; no raw card storage |
| **MapStruct** (optional) | **1.6.3** | DTO mapping | Stable with Java 21 annotation processing |
| **Bucket4j** or Redis token-bucket (phase 8) | latest maintained at implementation | Rate limiting | Prefer Redis-backed limiter aligned with Lettuce |

### 1.5 Runtime infrastructure images

| Service | Version | Role |
|---------|---------|------|
| PostgreSQL | **16** | OLTP database |
| Redis | **7.x** | Cache + rate limits |
| Apache Kafka | **3.9.x** (KRaft) | Domain events + DLQ |
| Mailhog | latest stable | Local SMTP sandbox |
| OpenJDK runtime image | **21** | Backend container |

### 1.6 Frontend stack

| Component | Version | Notes |
|-----------|---------|-------|
| Node.js | **22 LTS** | Build toolchain |
| React | **18.3.x** (or 19.x if approved at Phase 7) | SPA |
| TypeScript | **5.x** | Strict mode |
| Vite | **6.x** | Bundler |
| Tailwind CSS | **3.4.x** | Utility CSS (pin 3.x for stability unless Phase 7 chooses 4.x) |
| React Router | **6.x / 7.x** | Routing (decide at Phase 7) |

### 1.7 Infra / CI / cloud tooling

| Component | Version | Notes |
|-----------|---------|-------|
| Docker / Compose | Engine 24+ / Compose **v2** | Local full stack |
| Kubernetes | **1.30+** | Deployments |
| Helm | **3.x** | Charts |
| GitHub Actions | `ubuntu-latest` | CI |
| AWS | EKS + RDS + S3 + IAM + Secrets Manager + CloudWatch | Docs/scripts only until spend approved |

### 1.8 Compatibility rules for implementation

1. Inherit `spring-boot-starter-parent` **3.5.16**; omit versions for all Boot-managed dependencies.
2. Do **not** force newer Spring Framework / Security / Kafka / Hibernate versions outside the Boot BOM.
3. Use **Lettuce** via `spring-boot-starter-data-redis` (default).
4. Use **springdoc 2.10.x** only (not 3.x — that targets Boot 4).
5. Use JJWT **0.13.x** split artifacts.
6. Stripe keys, JWT secrets, DB passwords: environment / Secrets Manager only — never commit.
7. Architecture, modules, and **11-phase roadmap remain unchanged**.

---

## 2. Complete System Architecture

### 2.1 High-level view

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                         Clients (Browser / Admin SPA)                   │
│                         React + TypeScript + Vite                       │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ HTTPS / REST (JWT)
┌───────────────────────────────────▼─────────────────────────────────────┐
│                    API Gateway edge (Ingress / Nginx)                   │
│              Rate limiting · TLS termination · Routing                  │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼─────────────────────────────────────┐
│              Spring Boot Modular Monolith (single deployable)           │
│  ┌──────────┐ ┌──────┐ ┌─────────┐ ┌──────────┐ ┌───────────┐          │
│  │   Auth   │ │ User │ │ Product │ │ Category │ │ Inventory │          │
│  └──────────┘ └──────┘ └─────────┘ └──────────┘ └───────────┘          │
│  ┌──────────┐ ┌───────┐ ┌─────────┐ ┌───────────┐ ┌──────────────┐     │
│  │   Cart   │ │ Order │ │ Payment │ │ Promotion │ │ Notification │     │
│  └──────────┘ └───────┘ └─────────┘ └───────────┘ └──────────────┘     │
│  ┌──────────────┐ ┌─────────────────────────────────────────────────┐  │
│  │ Administration│ │ Shared: security, config, kafka, cache, common │  │
│  └──────────────┘ └─────────────────────────────────────────────────┘  │
└───────┬──────────────────┬──────────────────┬───────────────────────────┘
        │                  │                  │
   ┌────▼────┐       ┌─────▼─────┐      ┌─────▼─────┐
   │PostgreSQL│       │   Redis   │      │   Kafka   │
   │  (OLTP)  │       │  cache /  │      │ events +  │
   │          │       │ rate limit│      │   DLQ     │
   └──────────┘       └───────────┘      └─────┬─────┘
                                               │
                                    ┌──────────▼──────────┐
                                    │ Notification worker │
                                    │ (same app consumer) │
                                    └──────────┬──────────┘
                                               │
                                         ┌─────▼─────┐
                                         │SMTP / Mail│
                                         │ (sandbox) │
                                         └───────────┘

External: Stripe sandbox · AWS S3 (images) · CloudWatch (prod only)
```

### 2.2 Architectural principles

1. **Modular monolith first** — one Maven module deployable JAR; packages isolated by business module; clear boundaries for a future split into services.
2. **API never exposes JPA entities** — Controllers ↔ DTOs ↔ Services ↔ Domain/Entities ↔ Repositories.
3. **Transactional consistency at write boundaries** — checkout, stock reservation, payment confirmation use `@Transactional` + optimistic locking / DB constraints.
4. **Async via Kafka** — notifications, analytics side-effects, post-payment workflows; **not** for the core money-path commit itself.
5. **Cache-aside Redis** — product catalog, category trees; invalidate on writes.
6. **Idempotency** — payment webhooks and Kafka consumers use idempotency keys / processed-event store.

### 2.3 Request / checkout flow (critical path)

```text
Customer → Add to Cart → Apply Promo → Checkout
  → Create Order (PENDING)
  → Reserve Inventory (optimistic lock / row version)
  → Create Payment Intent (Stripe sandbox, idempotency key = orderId)
  ← Webhook payment.succeeded / failed
  → Mark Order PAID / PAYMENT_FAILED
  → Publish OrderPaidEvent → Kafka
  → Notification Module emails confirmation
  → Inventory finalize (reserved → sold)
```

### 2.4 Module responsibilities

| Module | Responsibility | Publishes Kafka topics | Consumes |
|--------|----------------|------------------------|----------|
| Authentication | Register, login, refresh, logout, password reset tokens | `user.registered` | — |
| User | Profile, addresses, role queries | `user.updated` | — |
| Category | Category CRUD, tree | `category.changed` | — |
| Product | Catalog CRUD, search, images metadata | `product.changed` | — |
| Inventory | Stock levels, reserve/release/commit | `inventory.low_stock` | `order.cancelled` |
| Cart | Per-user/session cart | — | — |
| Order | Place, cancel, status, history | `order.created`, `order.paid`, `order.cancelled`, `order.shipped` | `payment.succeeded`, `payment.failed` |
| Payment | Stripe intents, webhooks, idempotency | `payment.succeeded`, `payment.failed` | — |
| Promotion | Coupons, validation, redemption | — | `order.paid` (usage increment) |
| Notification | Email templates, send | — | order/payment/shipping events |
| Administration | Aggregated admin APIs + dashboard stats | — | — |

---

## 3. Proposed Folder Structure

```text
java-enterprise-ecommerce/
├── DEVELOPMENT_PLAN.md
├── TASK_TRACKER.md
├── README.md                          # Phase 0/docs phase
├── docs/
│   ├── PROJECT_OVERVIEW.md
│   ├── ARCHITECTURE.md
│   ├── DATABASE_DESIGN.md
│   ├── API_DOCUMENTATION.md
│   ├── SECURITY.md
│   ├── TESTING.md
│   ├── DOCKER_GUIDE.md
│   ├── KUBERNETES_GUIDE.md
│   ├── AWS_DEPLOYMENT.md
│   └── DEVELOPMENT_GUIDE.md
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
│       ├── main/
│       │   ├── java/com/ecommerce/platform/
│       │   │   ├── EcommerceApplication.java
│       │   │   ├── common/                 # ApiResponse, errors, paging, utils
│       │   │   ├── config/                 # Security, Redis, Kafka, OpenAPI, S3
│       │   │   ├── security/               # JWT filter, UserDetails, RBAC helpers
│       │   │   ├── auth/
│       │   │   ├── user/
│       │   │   ├── category/
│       │   │   ├── product/
│       │   │   ├── inventory/
│       │   │   ├── cart/
│       │   │   ├── order/
│       │   │   ├── payment/
│       │   │   ├── promotion/
│       │   │   ├── notification/
│       │   │   └── admin/
│       │   │       # each module:
│       │   │       #   api/ (controllers)
│       │   │       #   service/
│       │   │       #   repository/
│       │   │       #   domain/ (entities)
│       │   │       #   dto/
│       │   │       #   mapper/
│       │   │       #   event/ (publishers/listeners)
│       │   │       #   exception/
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-local.yml
│       │       ├── application-docker.yml
│       │       ├── application-prod.yml
│       │       ├── db/migration/           # Flyway V1__..., V2__...
│       │       └── templates/mail/
│       └── test/
│           ├── java/...                    # unit + @SpringBootTest + Testcontainers
│           └── resources/application-test.yml
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── Dockerfile
│   ├── nginx.conf
│   └── src/
│       ├── app/                            # providers, router
│       ├── features/                       # auth, catalog, cart, orders, admin
│       ├── shared/                         # ui, api client, hooks
│       └── styles/
├── infra/
│   ├── docker/
│   │   └── docker-compose.yml              # postgres, redis, kafka, zookeeper/kraft, mailhog, app, web
│   ├── kubernetes/
│   │   ├── base/                           # Deployments, Services, ConfigMaps, Secrets templates, Ingress, HPA
│   │   └── overlays/
│   │       ├── local/
│   │       └── aws/
│   ├── helm/ecommerce/
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   ├── values-aws.yaml
│   │   └── templates/
│   └── aws/
│       ├── README.md                       # architecture + manual steps
│       ├── iam-policies/                   # JSON templates (no secrets)
│       └── scripts/                        # optional eksctl/terraform stubs (no apply)
├── monitoring/
│   ├── prometheus/
│   ├── grafana/dashboards/
│   └── otel/
├── .github/workflows/
│   ├── ci.yml                              # build, test, lint, docker build
│   └── cd-staging.yml                      # gated; no auto prod spend
├── .env.example
├── .gitignore
└── Makefile / scripts/dev.sh
```

**Module package convention (example for `order`):**

```text
com.ecommerce.platform.order
  ├── api/OrderController.java
  ├── service/OrderService.java
  ├── repository/OrderRepository.java
  ├── domain/Order.java, OrderItem.java, OrderStatus.java
  ├── dto/...
  ├── mapper/OrderMapper.java
  ├── event/OrderEventPublisher.java
  └── exception/OrderNotFoundException.java
```

---

## 4. Database ERD

```text
┌────────────┐       ┌─────────────────┐       ┌──────────────┐
│   roles    │◄──────│  user_roles     │──────►│    users     │
└────────────┘       └─────────────────┘       └──────┬───────┘
                                                      │
                 ┌────────────────────────────────────┼──────────────────────────┐
                 │                                    │                          │
                 ▼                                    ▼                          ▼
        ┌────────────────┐                   ┌────────────────┐         ┌────────────────┐
        │ refresh_tokens │                   │ user_addresses │         │ password_reset │
        └────────────────┘                   └────────────────┘         │    _tokens     │
                                                                        └────────────────┘

┌──────────────┐      ┌──────────────┐      ┌────────────────┐
│  categories  │◄─────│  products    │─────►│ product_images │
└──────────────┘      └──────┬───────┘      └────────────────┘
                             │
                             ▼
                      ┌──────────────┐
                      │  inventory   │  (1:1 product, qty, reserved, version)
                      └──────────────┘

┌──────────────┐      ┌──────────────┐      ┌────────────────┐
│    carts     │─────►│  cart_items  │◄─────│   products     │
└──────────────┘      └──────────────┘      └────────────────┘

┌──────────────┐      ┌──────────────┐      ┌────────────────┐
│   orders     │─────►│  order_items │      │   payments     │
└──────┬───────┘      └──────────────┘      └────────┬───────┘
       │                                             │
       │              ┌──────────────┐               │
       └─────────────►│ promotions   │◄──────────────┘ (optional promo on order)
                      │  + redemptions│
                      └──────────────┘

┌────────────────────┐     ┌────────────────────┐
│ processed_events   │     │ notification_logs  │
│ (idempotency)      │     └────────────────────┘
└────────────────────┘
```

### Entity relationship notes

- `users` 1—N `orders`, 1—1/N `carts`, N—N `roles`
- `products` 1—1 `inventory`, N—1 `categories`, 1—N `product_images`
- `orders` 1—N `order_items`, 1—N `payments` (retries), optional N—1 `promotions`
- Soft deletes optional for products/categories (`deleted_at`); hard rules for payments (immutable audit)

---

## 5. Database Schema Design (summary)

> Full DDL will live in Flyway (`V1__init_schema.sql`). Monetary values: `NUMERIC(19,2)` + `currency CHAR(3)` default `USD`. Timestamps: `TIMESTAMPTZ`. UUIDs as primary keys (`UUID`).

### Core tables

| Table | Key columns | Indexes |
|-------|-------------|---------|
| `users` | id, email(unique), password_hash, full_name, status, created_at | email |
| `roles` | id, code(unique) — `ADMIN`, `CUSTOMER` | code |
| `user_roles` | user_id, role_id | PK(user_id, role_id) |
| `refresh_tokens` | id, user_id, token_hash, expires_at, revoked_at | user_id, token_hash |
| `password_reset_tokens` | id, user_id, token_hash, expires_at, used_at | token_hash |
| `user_addresses` | id, user_id, line1, city, country, postal_code, is_default | user_id |
| `categories` | id, parent_id, name, slug(unique), active | slug, parent_id |
| `products` | id, category_id, sku(unique), name, slug, description, price, currency, status, created_at | sku, slug, category_id, (name gin/trgm later) |
| `product_images` | id, product_id, storage_key, url, sort_order, is_primary | product_id |
| `inventory` | product_id(PK/FK), quantity_available, quantity_reserved, version | — |
| `carts` | id, user_id(unique nullable), session_id(unique nullable), updated_at | user_id, session_id |
| `cart_items` | id, cart_id, product_id, quantity, unit_price_snapshot | unique(cart_id, product_id) |
| `promotions` | id, code(unique), type(PERCENT/FIXED), value, starts_at, ends_at, usage_limit, used_count, min_order_amount, active | code |
| `promotion_redemptions` | id, promotion_id, user_id, order_id, redeemed_at | promotion_id, user_id |
| `orders` | id, order_number(unique), user_id, status, subtotal, discount, tax, total, currency, shipping_address_json, promotion_id, version, created_at | user_id, status, order_number |
| `order_items` | id, order_id, product_id, sku, name, unit_price, quantity, line_total | order_id |
| `payments` | id, order_id, provider, provider_ref, amount, currency, status, idempotency_key(unique), raw_event_ref, created_at | order_id, idempotency_key, provider_ref |
| `processed_events` | id, consumer_name, event_id(unique with consumer), processed_at | unique(consumer_name, event_id) |
| `notification_logs` | id, type, recipient, payload_ref, status, error, created_at | recipient, type |

### Order status machine

```text
PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → DELIVERED
       │            │
       │            └→ CANCELLED (admin / policy; restock if reserved)
       └→ PAYMENT_FAILED / CANCELLED (release reservation)
```

### Inventory concurrency strategy

- Columns: `quantity_available`, `quantity_reserved`, `@Version`
- Reserve: `available -= qty; reserved += qty` with version check; fail with `409 Conflict` / business error if insufficient
- Cancel: reverse reservation
- Paid: `reserved -= qty` (stock consumed)
- DB check constraints: quantities ≥ 0

---

## 6. API Endpoint List

Base: `/api/v1`
Auth: `Authorization: Bearer <access_token>` unless marked public.

### Auth

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/auth/register` | Public | Register CUSTOMER |
| POST | `/auth/login` | Public | Access + refresh tokens |
| POST | `/auth/refresh` | Public (refresh cookie/body) | Rotate refresh token |
| POST | `/auth/logout` | Authenticated | Revoke refresh token |
| POST | `/auth/password-reset/request` | Public | Send reset email |
| POST | `/auth/password-reset/confirm` | Public | Set new password |

### Users

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/users/me` | CUSTOMER/ADMIN | Profile |
| PUT | `/users/me` | CUSTOMER/ADMIN | Update profile |
| GET/POST/PUT/DELETE | `/users/me/addresses` | CUSTOMER/ADMIN | Address book |

### Categories / Products (catalog)

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/categories` | Public | List/tree |
| GET | `/categories/{id\|slug}` | Public | Detail |
| POST/PUT/DELETE | `/admin/categories/**` | ADMIN | Manage |
| GET | `/products` | Public | Search, filter, sort, page |
| GET | `/products/{id\|slug}` | Public | Detail |
| POST/PUT/DELETE | `/admin/products/**` | ADMIN | Manage |
| POST | `/admin/products/{id}/images` | ADMIN | Upload metadata → S3/local |

### Inventory

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/admin/inventory` | ADMIN | Stock list |
| PATCH | `/admin/inventory/{productId}` | ADMIN | Adjust stock |

### Cart

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/cart` | CUSTOMER | Get cart |
| POST | `/cart/items` | CUSTOMER | Add item |
| PATCH | `/cart/items/{itemId}` | CUSTOMER | Update qty |
| DELETE | `/cart/items/{itemId}` | CUSTOMER | Remove |
| DELETE | `/cart` | CUSTOMER | Clear |
| POST | `/cart/promo` | CUSTOMER | Apply code preview |

### Orders

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/orders/checkout` | CUSTOMER | Create order + reserve + payment intent |
| GET | `/orders` | CUSTOMER | My history |
| GET | `/orders/{id}` | CUSTOMER* | Detail (*owner or ADMIN) |
| POST | `/orders/{id}/cancel` | CUSTOMER* | Cancel if eligible |
| PATCH | `/admin/orders/{id}/status` | ADMIN | Status transition |
| GET | `/admin/orders` | ADMIN | All orders |

### Payments

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/payments/webhook/stripe` | Public + signature | Provider callback |
| GET | `/payments/{id}` | CUSTOMER*/ADMIN | Payment status |

### Promotions

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| POST | `/promotions/validate` | CUSTOMER | Validate code |
| CRUD | `/admin/promotions/**` | ADMIN | Manage coupons |

### Admin / Analytics

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/admin/dashboard/summary` | ADMIN | KPIs |
| GET | `/admin/users` | ADMIN | User management |
| PATCH | `/admin/users/{id}/roles` | ADMIN | Role assign |
| PATCH | `/admin/users/{id}/status` | ADMIN | Enable/disable |

### Platform

| Method | Path | Roles | Description |
|--------|------|-------|-------------|
| GET | `/actuator/health` | Restricted in prod | Health |
| GET | `/actuator/prometheus` | Internal | Metrics |
| GET | `/v3/api-docs`, `/swagger-ui.html` | Dev/staging | OpenAPI |

---

## 7. Security Architecture

```text
Client
  → HTTPS
  → Ingress rate limit / WAF (AWS stage)
  → Spring Security filter chain
       1. CorsFilter
       2. RateLimitFilter (Redis token bucket per IP + user)
       3. JwtAuthenticationFilter (access token)
       4. Authorization (@PreAuthorize / method security)
  → Controller validation (@Valid)
  → Service authorization (resource ownership checks)
```

### AuthN / AuthZ design

| Concern | Approach |
|---------|----------|
| Password | BCrypt (strength 12) |
| Access token | JWT, short-lived (15m), claims: `sub`, `email`, `roles`, `jti` |
| Refresh token | Opaque random, hashed at rest, 7–30d, rotation + reuse detection |
| Logout | Revoke refresh; optional access denylist in Redis until expiry |
| Roles | `ROLE_ADMIN`, `ROLE_CUSTOMER` |
| CSRF | Stateless JWT API → CSRF disabled; refresh preferably HttpOnly Secure cookie |
| Secrets | Env / Secrets Manager; never in git |
| Card data | Never touch PAN/CVV; Stripe Elements / Checkout only |
| Webhook security | Stripe signature verification |
| SQL injection | JPA/parameterized queries only |
| XSS | Frontend escaping; API JSON only |
| File upload | Content-type allowlist, size limits, virus-scan hook optional; store in S3/local object store |

### Public vs protected

- Public: catalog read, auth register/login/reset, Stripe webhook
- Authenticated: cart, orders, profile
- Admin: all `/admin/**`

---

## 8. Development Roadmap

| Phase | Name | Goal | Exit criteria |
|-------|------|------|---------------|
| **0** | Planning (this doc) | Architecture + trackers | User approval |
| **1** | Foundation | Maven app, Flyway, Security skeleton, Docker Compose infra, Actuator, OpenAPI | App boots locally with Postgres/Redis/Kafka; health green; CI skeleton |
| **2** | Identity | Auth + User modules, JWT, refresh, password reset, RBAC | Auth tests pass |
| **3** | Catalog | Category + Product + images + search/filter/page + Redis cache | Product API tests pass |
| **4** | Inventory & Cart | Stock + cart + availability checks | Concurrency inventory tests pass |
| **5** | Order & Promo | Checkout, order state machine, promotions | Order integration tests pass |
| **6** | Payment & Events | Stripe sandbox, webhooks, Kafka events, notifications | Payment + Kafka tests pass |
| **7** | Admin & Frontend | Admin APIs + React storefront/admin UI | E2E happy path locally |
| **8** | Hardening | Rate limit, observability (OTel/Prometheus/Grafana), more tests | Dashboards + SLOs documented |
| **9** | K8s & Helm | Manifests/charts, local deploy | `helm install` on kind/minikube works |
| **10** | AWS pack | EKS/RDS/S3/IAM/Secrets/CloudWatch docs + scripts | Docs complete; **no paid deploy without approval** |
| **11** | Docs polish & release | All `docs/*`, README, sample data | Checklist complete |

Phases are sequential for core domains (1→6). Frontend (7) may start UI scaffolding in parallel after Phase 2 APIs exist.

---

## 9. Testing Strategy

| Layer | Tools | Scope |
|-------|-------|-------|
| Unit | JUnit 5, Mockito | Services, mappers, pricing, promo rules, JWT utilities |
| Repository | `@DataJpaTest` + Testcontainers PostgreSQL | Queries, constraints, optimistic lock |
| Web | `@WebMvcTest` | Controllers, validation, security annotations |
| Integration | `@SpringBootTest` + Testcontainers (Postgres, Redis, Kafka) | Auth flows, checkout, payment webhook simulation |
| Concurrency | Parallel test threads / Executor | Inventory oversell prevention |
| Contract/API | Spring REST docs or OpenAPI snapshot (optional) | Breaking change detection |
| Frontend | Vitest + React Testing Library (phase 7+) | Critical UI logic |
| E2E | Playwright (optional late phase) | Login → purchase |

### Mandatory business scenarios (must automate)

1. Register → login → refresh → logout
2. Create product + stock → list/search
3. Add to cart → checkout → reserve stock
4. Concurrent checkout cannot oversell
5. Stripe webhook success → order PAID + event + email log
6. Cancel eligible order → stock restored
7. Promo percent/fixed with expiry and usage limit
8. Admin-only endpoints reject CUSTOMER
9. Idempotent payment webhook replay
10. Kafka consumer idempotency / DLQ path

**Rule:** Never claim tests passed unless executed in this environment; failures block phase exit.

---

## 10. Docker Architecture

```text
docker-compose services:
  postgres          :5432
  redis             :6379
  kafka (KRaft)     :9092
  mailhog           :1025 / :8025     # email sandbox UI
  backend           :8080             # Spring Boot
  frontend          :80               # Nginx serving Vite build + /api proxy
  prometheus        :9090             # optional profile
  grafana           :3000             # optional profile
```

- Multi-stage Dockerfiles (Maven build → JRE 21 runtime; Node build → Nginx)
- Non-root users in images
- Healthchecks on all stateful services and apps
- Secrets via `.env` (gitignored); `.env.example` only
- Compose profiles: `core`, `observability`, `full`

---

## 11. AWS Deployment Architecture

```text
Internet → Route53 → ALB / Ingress (AWS LB Controller)
                  → EKS (backend Deployment + frontend Deployment)
                       ├─ IAM Roles for Service Accounts (IRSA)
                       ├─ Secrets Manager → External Secrets Operator
                       ├─ CloudWatch Container Insights + Fluent Bit / OTel
                       └─ S3 for product images (presigned upload/download)

Managed data plane (outside cluster):
  RDS PostgreSQL Multi-AZ (prod) / single-AZ (staging)
  ElastiCache Redis (or self-managed on EKS for staging only)
  MSK (Kafka) or self-managed Kafka on EKS for non-prod
  ECR for images
```

**Guardrail:** Provisioning scripts documented but **not executed** without explicit approval (cost).

---

## 12. Risk Assessment

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Spring Boot 3.5 OSS EOL (accepted) | No further community security patches after 3.5.16 | High (accepted) | Stay on 3.5.16 per decision; plan optional future migration to Boot 4.1; avoid unapproved BOM overrides |
| Inventory race / oversell | Financial / trust | Medium | Optimistic locking + DB constraints + tests |
| Payment webhook duplicates | Double fulfillment | Medium | Idempotency keys + `payments` unique constraints |
| Kafka dual-write inconsistency | Missing notifications | Medium | Outbox pattern (phase 6 hardening) or transactional staging table |
| Scope creep (full enterprise surface) | Schedule slip | High | Strict phase gates; MVP within phases 1–7 |
| AWS cost accidents | Bill shock | Medium | Local-first; no `terraform apply` / EKS create without approval |
| Stripe API changes | Payment breaks | Low | Pin SDK; sandbox integration tests |
| JWT refresh theft | Account takeover | Medium | Rotation + reuse detection + HttpOnly cookie option |
| Large monolith PR | Review fatigue | Medium | Phase PRs / incremental commits |
| Windows host + Docker Desktop quirks | Dev friction | Medium | Document WSL2 path; CI on Linux runners |

---

## 13. Documented Assumptions

1. Single currency MVP: **USD** (schema supports multi-currency later).
2. Tax calculation: **simple configurable rate** or zero in MVP; not full tax engine.
3. Shipping: address captured; carrier integration **out of scope** (status updates manual/admin).
4. Guest checkout: **out of scope** for v1 (authenticated customers only).
5. Image storage local disk in Docker; S3 adapter for AWS profile.
6. Email via **Mailhog** locally; SES/SMTP configurable for AWS.
7. One region deployment docs (e.g., `ap-southeast-1`) as example.
8. Admin dashboard analytics: **SQL aggregations**, not a separate OLAP stack.
9. Spring Boot **3.5.16** (confirmed). Future Boot 4.x migration is out of current scope unless re-approved.
10. Payment provider: **Stripe test mode** (confirmed).
11. Project directory confirmed: `C:\Users\84919\source\repos\java-enterprise-ecommerce`.

---

## 14. Approval Gate

**STOP here.** Stack and prior architecture are documented. No Phase 1 code until you approve the locked stack below.

### Confirmed
- [x] Spring Boot **3.5.x** → pin **3.5.16**
- [x] Java **21 LTS**
- [x] Stripe test mode
- [x] Project path retained
- [x] Modular monolith + 11-phase roadmap retained

### Phase 0 / stack approvals
- [x] Approve locked dependency versions in §1 (BOM + springdoc 2.10.0, JJWT 0.13.0, stripe-java 34.0.0)
- [x] Approve starting **Phase 1 — Foundation**

### Phase gates
- [x] Approve starting **Phase 1 — Foundation**
- [x] Approve starting **Phase 2 — Authentication & User**
- [x] Approve starting **Phase 3 — Category & Product Catalog**
- [x] Approve starting **Phase 4 — Inventory & Shopping Cart**
- [x] Approve starting **Phase 5 — Orders & Promotions**
- [x] Approve starting **Phase 6 — Payment, Kafka Events, Notifications**
- [x] Approve **final Phase 7 implementation plan** (`docs/PHASE7_IMPLEMENTATION_PLAN.md`) locking P7-D1…D15
- [x] Approve starting **Phase 7 — Administration & Frontend** implementation
- [x] Phase 7 local exit — `docs/PHASE7_FINAL_COMPLETION_REPORT.md` (2026-11-05)
- [x] Phase 7 final acceptance — **ACCEPT WITH LIMITATIONS** — `docs/PHASE7_FINAL_ACCEPTANCE_REPORT.md` (Compose PASS; local portfolio delivery; not production)
- [x] Approve **Phase 8 implementation plan** locking P8-D1…D15 + U1–U6
- [x] Complete **Phase 8 — Hardening & Observability** — `docs/PHASE8_COMPLETION_REPORT.md` (Surefire 82; Vitest 68; Failsafe pending Docker)
- [ ] Re-run Failsafe with Docker Desktop when available
- [ ] Approve starting **Phase 9** separately (do not auto-start)

See `docs/PHASE7_IMPLEMENTATION_PLAN.md`, `docs/PHASE6_COMPLETION_REPORT.md`, `docs/SECURITY.md`, `docs/API_DOCUMENTATION.md`, and `TASK_TRACKER.md`.
