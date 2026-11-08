# Phase 7 WP-B Completion Report — Customer Storefront

**Date:** 2026-11-05  
**Status:** Implementation complete — awaiting review  
**Scope:** Customer catalog + cart only (no checkout / Stripe)

---

## 1. Features implemented

| Feature | Status |
|---------|--------|
| Product listing (image, name, price, SKU, status) | Done |
| Search (`q`) | Done |
| Category filter (`categoryId`) | Done |
| Sort (`name`, `priceCents`, `createdAt`, `sku` × asc/desc) | Done |
| Pagination (`page` / `size`) | Done |
| Loading skeletons / empty / error | Done |
| Product detail (images, description, category, price) | Done |
| Quantity validation (1–999) + add-to-cart | Done |
| Cart list / update qty / remove / clear / subtotal | Done |
| Auth: public catalog; cart CUSTOMER-only; ADMIN blocked | Done |
| Account page (`GET /auth/me`) | Done (P7-D3, no address book) |
| Category browse route `/categories/:slug` | Done |
| Checkout / payment / promo UI | **Not implemented** (WP-C+) |

---

## 2. UI routes

| Route | Access |
|-------|--------|
| `/products` | Public |
| `/products/:idOrSlug` | Public (add-to-cart requires CUSTOMER) |
| `/categories/:slug` | Public |
| `/cart` | CUSTOMER (`CustomerRoute`) |
| `/account` | Authenticated (`RequireAuth`) |
| `/checkout` | Still placeholder |

---

## 3. API endpoints used (existing only)

| Method | Path | Usage |
|--------|------|-------|
| GET | `/api/v1/products` | List/search/filter/sort/page |
| GET | `/api/v1/products/{idOrSlug}` | Detail |
| GET | `/api/v1/categories` | Filter tree |
| GET | `/api/v1/categories/{idOrSlug}` | Category page |
| GET | `/api/v1/cart` | Load cart |
| POST | `/api/v1/cart/items` | Add |
| PATCH | `/api/v1/cart/items/{id}` | Update qty |
| DELETE | `/api/v1/cart/items/{id}` | Remove |
| DELETE | `/api/v1/cart` | Clear |
| GET | `/api/v1/auth/me` | Account |

**No new backend APIs.** No public inventory quantity endpoint exists; stock is enforced by cart API (`INSUFFICIENT_STOCK` / `available` flag).

---

## 4. Files created / modified (high level)

**Created:** `features/catalog/**`, `features/cart/**`, `features/auth/pages/AccountPage.tsx`, shared `money` / catalog / cart / paging types, tests.

**Modified:** `app/router.tsx`, `pages/HomePage.tsx`, `vite.config.ts` (`/media` proxy), `TASK_TRACKER.md`.

---

## 5. Tests and verification

| Command | Result |
|---------|--------|
| `npm test` | **49 passed** (includes all WP-A tests) |
| `npm run build` | OK |
| `npm run lint` | OK |
| `npm audit` | **0 vulnerabilities** |

WP-B coverage includes listing/search/empty/error, detail/qty/add-to-cart/admin block/login redirect, cart update/remove/empty/errors.

---

## 6. Manual QA checklist

1. `npm run dev` with backend + seed data.  
2. `/products` — search, category, sort, pagination.  
3. Open a product — gallery, add to cart as CUSTOMER.  
4. Unauthenticated add-to-cart → login.  
5. ADMIN account — cart route redirects; add-to-cart message blocks.  
6. `/cart` — change qty, remove, clear; note unavailable flags.  
7. Confirm no checkout CTA beyond “WP-C” note.

---

## 7. Known limitations

| Limitation | Reason |
|------------|--------|
| No public stock quantity on PDP | Inventory HTTP is admin-only |
| Cart lines have no product image | `CartItemResponse` has no image field |
| Category name on product cards | Resolved via categories tree (`ProductSummary` has `categoryId` only) |
| No promo / checkout / Stripe | Explicitly out of WP-B |
| `/media` depends on backend static serving | Vite proxies `/media` for local dev |

---

## 8. Confirmations

- Checkout and payment **were not** implemented.  
- Phase 1–6 backend behavior **unchanged**.  
- WP-C **not started**.
