# catalog-service — Product Catalog Cache

A Spring Boot 3 / Java 21 product catalog service that demonstrates **Caffeine** in-process caching — from cache tuning per use case to observing hit/miss/eviction behavior in a live REST API.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)
![Caffeine](https://img.shields.io/badge/Cache-Caffeine-blueviolet)
![MySQL](https://img.shields.io/badge/DB-MySQL%208-blue)
![Flyway](https://img.shields.io/badge/Migrations-Flyway-red)
![Docker](https://img.shields.io/badge/Container-Docker-2496ED)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

**Learning Track:** `springboot-caffeine-cache-demo` (Project 5 of 17)
**Real-World Service Name:** `catalog-service`

---

## 1. Project Overview

### The problem

A product catalog is read orders of magnitude more often than it's written: a product detail page, a category listing, a search box — every one of those hammers the same handful of rows over and over. Hitting a relational database for every single one of those reads is wasted work once you account for network round-trips, connection pool contention, and query planning overhead, especially when the underlying data changes rarely relative to how often it's read.

This project simulates that exact shape of problem: `ProductService` and `CategoryService` sit in front of a MySQL-backed catalog, and a **250ms artificial repository latency** (`simulateRepositoryLatency()`) stands in for "a slow datastore" so the effect of caching is visible end-to-end, not just theoretical.

### Why Caffeine

[Caffeine](https://github.com/ben-manes/caffeine) is the de-facto standard in-process (local, JVM-heap) cache for Java, and it's what Spring Boot uses under `spring.cache.type=caffeine`. Compared to a distributed cache (Redis, Memcached):

- **No network hop** — a cache hit is a heap read, sub-millisecond, with no serialization.
- **No extra infrastructure** — nothing else to run, patch, or monitor (this is explicitly an "Additional Components: None" project — no Redis, no external cache tier).
- **Near-optimal eviction** — Caffeine uses a Window TinyLFU admission policy that outperforms simple LRU under most real-world access patterns.
- **Built-in statistics** — `recordStats()` gives hit rate, miss rate, eviction count, and load penalty without wiring up a separate metrics pipeline.

The trade-off this project is designed to make visible: an in-process cache is **local to one JVM instance**. It doesn't survive a restart, and it isn't shared across horizontally-scaled replicas — each instance builds up its own cache independently. That's the right trade for read-mostly, "okay to be briefly stale" data like a product catalog; it's the wrong trade for data that must be perfectly consistent across a fleet (e.g., a distributed session store, a rate limiter).

### Where this pattern shows up in real companies

- **E-commerce product pages** (Amazon-style catalogs) — product detail and category pages are cached at the application tier precisely because they're read far more than written.
- **Feature flag / config services** — flags are read on every request but change rarely; local caching with short TTL is standard.
- **Pricing/inventory microservices** — a `products` cache with short TTL + explicit invalidation on write is a common pattern to keep prices fast to read but not stale for long.
- **API gateways / BFFs** — caching downstream call results locally (per-instance) to shave latency off a slow upstream, exactly like the simulated 250ms latency here.

---

## 2. Architecture

### High-Level Design (HLD)

```
                        ┌─────────────────────────┐
                        │        Clients          │
                        │  (browser / curl / etc) │
                        └────────────┬─────────────┘
                                     │ HTTP/JSON
                                     ▼
                     ┌───────────────────────────────┐
                     │      catalog-service (JVM)     │
                     │                                │
                     │  ┌──────────────────────────┐  │
                     │  │   REST Controllers        │  │
                     │  │  Product / Category /     │  │
                     │  │  CacheAdmin                │  │
                     │  └────────────┬─────────────┘  │
                     │               ▼                │
                     │  ┌──────────────────────────┐  │
                     │  │   Service Layer            │◄─┼── @Cacheable / @CachePut / @CacheEvict
                     │  │  ProductService /          │  │      (Spring Cache abstraction proxy)
                     │  │  CategoryService /         │  │
                     │  │  CacheAdminService          │  │
                     │  └────────────┬─────────────┘  │
                     │               │                │
                     │      ┌────────┴────────┐        │
                     │      ▼                 ▼        │
                     │ ┌──────────┐   ┌──────────────┐ │
                     │ │ Caffeine  │   │ Spring Data   │ │
                     │ │ In-Memory │   │ JPA / Hibernate│ │
                     │ │ Caches x4 │   │  Repositories  │ │
                     │ └──────────┘   └──────┬───────┘ │
                     └────────────────────────┼─────────┘
                                              ▼
                                       ┌─────────────┐
                                       │  MySQL 8    │
                                       │  (Flyway-   │
                                       │  managed)   │
                                       └─────────────┘
```

### Low-Level Design (LLD) — request flow for `GET /api/v1/products/{id}`

```
Client
  │  GET /api/v1/products/42
  ▼
ProductController.getProduct(42)
  │  wraps call in timed() -> measures elapsed ns, adds X-Response-Time-Ms header
  ▼
ProductService.getProductById(42)          <── Spring AOP proxy intercepts here
  │
  ├── Cache MISS path:                          ├── Cache HIT path:
  │   1. Caffeine "products" cache checked       │   1. Caffeine "products" cache checked
  │   2. Not present -> proxy invokes method     │   2. Present -> value returned directly
  │   3. simulateRepositoryLatency() (250ms)     │      (method body never executes)
  │   4. productRepository.findById(42)          │   3. ~0-2ms total
  │   5. ProductMapper.toResponse(entity)        │
  │   6. Result stored in "products" cache       │
  │   7. Returned to caller (~250ms+)            │
  ▼
ResponseEntity<ProductResponse> + X-Response-Time-Ms header
```

### Domain model (JPA)

```
┌────────────────────┐          1        N ┌────────────────────────┐
│      Category        │◄───────────────────│         Product           │
├────────────────────┤                     ├────────────────────────┤
│ id (PK)              │                     │ id (PK)                    │
│ name (unique)         │                     │ sku (unique)               │
│ description           │                     │ name                        │
│ created_at            │                     │ description                 │
│ updated_at            │                     │ price (DECIMAL 10,2)        │
└────────────────────┘                     │ stock_quantity              │
                                             │ active                      │
                                             │ category_id (FK)            │
                                             │ created_at / updated_at     │
                                             └────────────────────────┘
```

### Folder structure

```
catalog-service/
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── src/main/java/com/medha/catalogservice/
│   ├── CatalogServiceApplication.java
│   ├── config/          CacheNames, CacheProperties, CacheConfig
│   ├── domain/          Category, Product (JPA entities)
│   ├── repository/      CategoryRepository, ProductRepository (Spring Data JPA)
│   ├── dto/              Request/Response records, PageResponse, CacheStatsResponse, ErrorResponse
│   ├── mapper/           CategoryMapper, ProductMapper (entity <-> DTO)
│   ├── service/          CategoryService, ProductService, CacheAdminService
│   ├── controller/       CategoryController, ProductController, CacheAdminController
│   └── exception/        ResourceNotFoundException, DuplicateResourceException,
│                         InvalidCacheNameException, GlobalExceptionHandler
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/     V1__init_schema.sql, V2__seed_data.sql (Flyway)
└── src/test/java/com/medha/catalogservice/
    ├── CatalogServiceApplicationTests.java
    ├── service/           ProductServiceTest, CategoryServiceTest, CacheAdminServiceTest
    ├── controller/        ProductControllerTest, CategoryControllerTest
    ├── repository/        ProductRepositoryTest
    └── cache/              ProductCacheIntegrationTest
```

This is a **single-module** project (no multi-module Maven build), so there is only this one README.

---

## 3. Tech Stack

| Layer               | Technology                                   | Why                                                                 |
|---------------------|-----------------------------------------------|----------------------------------------------------------------------|
| Language / Runtime  | Java 21                                       | Current LTS, records used throughout for DTOs                        |
| Framework           | Spring Boot 3.3.4                              | Web, DI, transaction management, caching abstraction                 |
| Caching             | Caffeine + `spring-boot-starter-cache`         | The technology under study — high-performance in-process cache        |
| Persistence         | Spring Data JPA / Hibernate                    | Repository abstraction over the domain model                        |
| Database            | MySQL 8 (Docker) / H2 in-memory (tests)        | Realistic RDBMS in prod-like env; fast, dependency-free in tests      |
| Schema migration    | Flyway (`flyway-core`, `flyway-mysql`)         | Versioned, repeatable schema + seed data setup                       |
| Validation          | Jakarta Bean Validation (`spring-boot-starter-validation`) | Declarative request DTO validation                     |
| Observability       | Spring Boot Actuator                           | `/actuator/health` for container healthcheck, `/actuator/metrics`     |
| Boilerplate         | Lombok                                         | Getters/setters/builders on JPA entities                              |
| Testing             | JUnit 5, Mockito, AssertJ, Spring Boot Test, H2 | Unit + slice + full-context cache-behavior tests                     |
| Containerization    | Docker (multi-stage), Docker Compose            | Reproducible local run of `mysql` + `catalog-service`                 |

---

## 4. Configuration Explained (`application.yml`)

```yaml
spring:
  application:
    name: catalog-service
```
Names the app for logging/Actuator `/info`.

```yaml
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:catalog_db}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: ${DB_USERNAME:catalog_user}
    password: ${DB_PASSWORD:catalog_pass}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      pool-name: catalog-hikari
      maximum-pool-size: 10
      minimum-idle: 2
```
All connection details are environment-overridable (defaults work for local `docker-compose`). `useSSL=false` + `allowPublicKeyRetrieval=true` avoid TLS handshake friction in a local/dev MySQL container. HikariCP pool is named for clearer logs/metrics and capped at 10 connections — plenty for a demo service, small enough to avoid exhausting MySQL's default connection limit.

```yaml
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
```
`ddl-auto: validate` means Hibernate never mutates the schema — **Flyway is the single source of truth** for DDL, and Hibernate just checks the entity mappings match it. `open-in-view: false` closes the JPA session at the service boundary rather than holding it open through view rendering, avoiding accidental lazy-loading-in-controller bugs and keeping DB connections short-lived — important in a service whose whole point is to *not* rely on the DB for repeat reads.

```yaml
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
```
Runs `V1__init_schema.sql` and `V2__seed_data.sql` on startup against MySQL. `baseline-on-migrate: true` lets Flyway adopt a pre-existing (empty) database without manual baselining. Overridden to `enabled: false` in `application-test.yml` since tests use H2 with `ddl-auto: create-drop` instead.

```yaml
  cache:
    type: caffeine
```
Tells Spring's cache auto-configuration to back `@EnableCaching` with Caffeine — though in this project the actual `CacheManager` bean is hand-built in `CacheConfig` (four independently-tuned caches), so this mainly documents intent and would matter if the custom bean were removed.

```yaml
server:
  port: 8080
```
Standard HTTP port, matched by `EXPOSE 8080` in the Dockerfile and the compose port mapping.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,caches,metrics
  endpoint:
    health:
      show-details: when-authorized
  health:
    defaults:
      enabled: true
```
Exposes exactly the Actuator endpoints needed for this demo: `health` (container healthcheck), `info`, `caches` (Spring's own cache endpoint — lists cache names/entries, distinct from the custom Caffeine-stats endpoint this project adds), and `metrics`. `show-details: when-authorized` avoids leaking internal health details to unauthenticated callers by default.

```yaml
catalog:
  cache:
    simulated-repository-latency-ms: ${SIMULATED_LATENCY_MS:250}
    products:
      maximum-size: 500
      expire-after-write: 10m
    product-lists:
      maximum-size: 200
      expire-after-write: 5m
    product-search:
      maximum-size: 100
      expire-after-write: 2m
    categories:
      maximum-size: 100
      expire-after-access: 30m
```
This block is bound to `CacheProperties` (`@ConfigurationProperties(prefix = "catalog.cache")`, validated with Jakarta Bean Validation) and is the heart of the demo:

| Property | Cache | Value | Rationale |
|---|---|---|---|
| `simulated-repository-latency-ms` | n/a | `250` (0 in tests) | Artificial delay before hitting the repository, so a cache hit's speedup is visible in the `X-Response-Time-Ms` header. Overridable via `SIMULATED_LATENCY_MS` env var. |
| `products.maximum-size` / `expire-after-write` | `products` | 500 entries / 10m | Highest traffic, point-lookup cache. Long TTL + moderate size because individual products are read constantly and change less often than they're read. |
| `product-lists.maximum-size` / `expire-after-write` | `productLists` | 200 entries / 5m | Paged listing results. Smaller and shorter-lived than `products` because page membership shifts on almost any write and can't be selectively invalidated (see `allEntries=true` evictions below). |
| `product-search.maximum-size` / `expire-after-write` | `productSearch` | 100 entries / 2m | Free-text search results — shortest TTL of the four because search relevance is most sensitive to catalog churn. |
| `categories.maximum-size` / `expire-after-access` | `categories` | 100 entries / 30m (access-based) | Categories change rarely, so a long, **access-refreshed** (not write-refreshed) TTL is safe — an actively-read category entry stays warm indefinitely. |

Each `Spec` (in `CacheProperties`) carries exactly one of `expireAfterWrite` or `expireAfterAccess`, consumed by `CacheConfig.buildCaffeine()` which builds a distinct `Caffeine` instance per cache name via `CaffeineCacheManager.registerCustomCache(...)` — this is what lets four caches coexist with different tuning under one `CacheManager` (Spring's default `CaffeineCacheManager` otherwise applies one spec to every cache it manages).

```yaml
logging:
  level:
    root: INFO
    com.medha.catalogservice: DEBUG
    org.springframework.cache: DEBUG
```
`org.springframework.cache: DEBUG` is deliberately turned on so you can watch cache hit/miss/put/evict decisions scroll by in the console while exercising the API — a direct teaching aid for this project.

### `application-test.yml` (test profile)

Same shape, but: H2 in-memory MySQL-mode datasource, Flyway disabled, `ddl-auto: create-drop`, and `simulated-repository-latency-ms: 0` — tests assert caching behavior via **Mockito call counts**, not wall-clock timing, so the artificial delay would only slow the suite down for no benefit.

---

## 5. Project Structure Explained

| Path | Purpose |
|---|---|
| `pom.xml` | Single-module Maven build; declares Spring Boot 3.3.4 parent, Web/JPA/Cache/Validation/Actuator starters, Caffeine, Flyway (+ MySQL dialect), MySQL driver, Lombok, and test deps (JUnit via starter-test, H2). |
| `Dockerfile` | Multi-stage build: `maven:3.9.9-eclipse-temurin-21` compiles the jar, `eclipse-temurin:21-jre-alpine` runs it as a non-root `spring` user, with an Actuator-health-based `HEALTHCHECK`. |
| `docker-compose.yml` | Two services only (per this project's "no extra infra" scope): `mysql` (with a healthcheck gating startup) and `catalog-service` (built from the Dockerfile, waiting on MySQL's healthcheck). |
| `.dockerignore` / `.gitignore` | Keep build artifacts, IDE metadata, and the Docker/Compose files themselves out of the build context / VCS noise. |
| `src/main/java/.../CatalogServiceApplication.java` | Boot entrypoint; `@ConfigurationPropertiesScan` picks up `CacheProperties` without needing an explicit `@EnableConfigurationProperties`. |
| `config/CacheNames.java` | String constants for the four cache names (`products`, `productLists`, `productSearch`, `categories`) shared between annotations and cache registration — avoids typo bugs. |
| `config/CacheProperties.java` | `@ConfigurationProperties` + validated POJO binding `catalog.cache.*`; one `Spec` (maximumSize + expireAfterWrite/Access) per cache, plus the simulated latency knob. |
| `config/CacheConfig.java` | `@EnableCaching` + the `CacheManager` bean: builds one `Caffeine` instance per cache with `recordStats()` always on, registered via `registerCustomCache`. |
| `domain/Category.java`, `domain/Product.java` | JPA entities. `Product` has a `@ManyToOne(fetch = LAZY)` to `Category`; both use Hibernate `@CreationTimestamp`/`@UpdateTimestamp`. |
| `repository/CategoryRepository.java`, `repository/ProductRepository.java` | Spring Data JPA interfaces; `ProductRepository` has derived-query methods for category/active/name-search filtering used by list/search endpoints. |
| `dto/*.java` | Request records (`ProductRequest`, `CategoryRequest`, `StockAdjustmentRequest`) with Jakarta Bean Validation annotations; response records (`ProductResponse`, `CategoryResponse`); `PageResponse<T>` — a plain `Serializable` page wrapper used specifically because `@Cacheable` methods shouldn't return Spring Data's `Page<T>` directly; `CacheStatsResponse` mirrors raw Caffeine `CacheStats`; `ErrorResponse` is the uniform API error shape. |
| `mapper/CategoryMapper.java`, `mapper/ProductMapper.java` | Hand-written entity <-> DTO mapping (no MapStruct) — kept simple and explicit for teaching purposes. |
| `service/CategoryService.java` | CRUD + `@Cacheable`/`@CachePut`/`@CacheEvict` on the `categories` cache, keyed by id. |
| `service/ProductService.java` | The core of the demo — see section 12 below and the inline Javadoc in the source for the caching strategy per method. |
| `service/CacheAdminService.java` | Reaches into the native `com.github.benmanes.caffeine.cache.Cache` behind each Spring `CaffeineCache` to expose raw `CacheStats` and manual `invalidateAll()`/clear operations, deliberately separate from Actuator/Micrometer metrics. |
| `controller/ProductController.java`, `controller/CategoryController.java` | REST endpoints; `ProductController` wraps every response in a `timed()` helper that adds `X-Response-Time-Ms`. |
| `controller/CacheAdminController.java` | `GET /api/v1/cache/stats[/​{cacheName}]`, `DELETE /api/v1/cache[/​{cacheName}]` — the "look inside the cache" surface. |
| `exception/*.java` | `ResourceNotFoundException`, `DuplicateResourceException`, `InvalidCacheNameException`, and a `@RestControllerAdvice` `GlobalExceptionHandler` mapping them (plus Bean Validation failures) to a uniform `ErrorResponse`. |
| `src/main/resources/application.yml` | See section 4. |
| `src/main/resources/db/migration/V1__init_schema.sql` | Creates `categories` and `products` tables, FK, and indexes on `category_id`, `active`, `name`. |
| `src/main/resources/db/migration/V2__seed_data.sql` | Seeds 5 categories and 19 products (including one inactive/discontinued item) for a realistic demo dataset. |
| `src/test/resources/application-test.yml` | H2 test profile — see section 4. |
| `src/test/java/.../CatalogServiceApplicationTests.java` | Context-load smoke test — proves `CacheConfig`'s registrations wire up cleanly. |
| `src/test/java/.../service/*Test.java` | Mockito-based unit tests for `ProductService`, `CategoryService`, `CacheAdminService` business logic (no Spring context, so caching itself is *not* exercised here). |
| `src/test/java/.../controller/*Test.java` | `@WebMvcTest` slice tests for `ProductController`/`CategoryController` (validation, status codes, response header). |
| `src/test/java/.../repository/ProductRepositoryTest.java` | `@DataJpaTest` against H2 verifying derived-query repository methods. |
| `src/test/java/.../cache/ProductCacheIntegrationTest.java` | Full `@SpringBootTest` with `ProductRepository`/`CategoryRepository` mocked — the proof that Caffeine is actually intercepting calls through Spring's proxy (see section 10). |

---

## 6. Getting Started

### Prerequisites

- Docker + Docker Compose
- (optional, for local dev outside Docker) JDK 21 and Maven 3.9+

### Run everything with Docker Compose

```bash
git clone https://github.com/JNikhilSakthi/catalog-service.git
cd catalog-service

# Build the image and start MySQL + catalog-service
docker compose up --build

# Or run in the background
docker compose up --build -d

# Tail logs
docker compose logs -f catalog-service

# Stop everything
docker compose down

# Stop and wipe the MySQL volume too
docker compose down -v
```

The service starts on **http://localhost:8080** once MySQL passes its healthcheck (compose gates `catalog-service` startup on `mysql: condition: service_healthy`). Flyway then runs `V1__init_schema.sql` and `V2__seed_data.sql` automatically against the fresh database.

Verify it's up:

```bash
curl http://localhost:8080/actuator/health
```

### Run locally without Docker (dev loop)

```bash
# Start only MySQL from compose
docker compose up -d mysql

# Run the app against it
./mvnw spring-boot:run
```

### Run the test suite (no Docker needed — uses H2)

```bash
./mvnw test
```

---

## 7. API Documentation

Base path: `/api/v1`

### Products — `ProductController`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/products/{id}` | Get one product by id (cached: `products`) |
| `GET` | `/api/v1/products?categoryId=&activeOnly=&page=&size=` | Paged product list, optionally filtered (cached: `productLists`) |
| `GET` | `/api/v1/products/search?q=&page=&size=` | Case-insensitive name search, paged (cached: `productSearch`) |
| `POST` | `/api/v1/products` | Create a product |
| `PUT` | `/api/v1/products/{id}` | Full update of a product |
| `PATCH` | `/api/v1/products/{id}/stock` | Adjust stock by a signed delta |
| `DELETE` | `/api/v1/products/{id}` | Delete a product |

Every response from `ProductController` includes an `X-Response-Time-Ms` header so you can directly observe the cache-hit speedup.

**Example — get a product (first call, cache miss, ~250ms):**

```bash
curl -i http://localhost:8080/api/v1/products/1
```

```
HTTP/1.1 200
X-Response-Time-Ms: 253
Content-Type: application/json

{
  "id": 1,
  "sku": "ELEC-0001",
  "name": "Wireless Noise-Cancelling Headphones",
  "description": "Over-ear Bluetooth headphones with active noise cancellation",
  "price": 149.99,
  "stockQuantity": 120,
  "active": true,
  "categoryId": 1,
  "categoryName": "Electronics",
  "createdAt": "2026-07-22T10:00:00Z",
  "updatedAt": "2026-07-22T10:00:00Z"
}
```

**Second call, same id (cache hit, ~1-3ms):**

```bash
curl -i http://localhost:8080/api/v1/products/1
# X-Response-Time-Ms: 1
```

**Create a product:**

```bash
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "ELEC-0099",
    "name": "USB-C Hub",
    "description": "7-in-1 USB-C hub",
    "price": 34.99,
    "stockQuantity": 100,
    "active": true,
    "categoryId": 1
  }'
# 201 Created — evicts productLists and productSearch (allEntries=true)
```

**Adjust stock:**

```bash
curl -X PATCH http://localhost:8080/api/v1/products/1/stock \
  -H "Content-Type: application/json" \
  -d '{"delta": -5}'
# 200 OK — refreshes the "products" cache entry via @CachePut, evicts list/search caches
```

**Validation error example (missing required fields):**

```json
{
  "timestamp": "2026-07-22T10:05:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields",
  "path": "/api/v1/products",
  "fieldErrors": {
    "sku": "sku is required",
    "name": "name is required",
    "price": "price is required",
    "stockQuantity": "stockQuantity is required",
    "categoryId": "categoryId is required"
  }
}
```

### Categories — `CategoryController`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/categories` | List all categories (uncached) |
| `GET` | `/api/v1/categories/{id}` | Get one category by id (cached: `categories`) |
| `POST` | `/api/v1/categories` | Create a category |
| `PUT` | `/api/v1/categories/{id}` | Update a category (refreshes cache via `@CachePut`) |
| `DELETE` | `/api/v1/categories/{id}` | Delete a category (evicts cache entry) |

### Cache Admin — `CacheAdminController`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/cache/stats` | Raw Caffeine `CacheStats` for every registered cache |
| `GET` | `/api/v1/cache/stats/{cacheName}` | Stats for one named cache |
| `DELETE` | `/api/v1/cache/{cacheName}` | Manually clear one cache (`invalidateAll()`) |
| `DELETE` | `/api/v1/cache` | Manually clear every cache |

**Example — inspect cache stats after a few requests:**

```bash
curl http://localhost:8080/api/v1/cache/stats/products
```

```json
{
  "cacheName": "products",
  "estimatedSize": 3,
  "requestCount": 7,
  "hitCount": 4,
  "missCount": 3,
  "hitRate": 0.5714285714285714,
  "missRate": 0.42857142857142855,
  "evictionCount": 0,
  "evictionWeight": 0,
  "loadSuccessCount": 0,
  "loadFailureCount": 0,
  "averageLoadPenaltyMillis": 0.0
}
```

(`loadSuccessCount`/`loadFailureCount`/`averageLoadPenaltyMillis` stay at 0 here because these caches are populated through Spring's `@Cacheable`/`@CachePut` put-on-miss pattern rather than a Caffeine `CacheLoader`.)

---

## 8. Testing

Run everything with:

```bash
./mvnw test
```

All tests run against **H2 in-memory** (`application-test.yml`: Flyway disabled, `ddl-auto: create-drop`), so no Docker/MySQL is required.

| Test class | Type | What it proves |
|---|---|---|
| `CatalogServiceApplicationTests` | `@SpringBootTest` smoke test | The full context — including `CacheConfig`'s four Caffeine cache registrations — starts cleanly. |
| `ProductServiceTest` | Mockito unit test | `ProductService` business logic (CRUD, stock adjustment, not-found/duplicate errors) is correct **independent of caching** — the service is instantiated directly (no proxy), so no `@Cacheable` behavior is exercised here by design. |
| `CategoryServiceTest` | Mockito unit test | Same, for `CategoryService`. |
| `CacheAdminServiceTest` | Plain unit test (real Caffeine, no Spring context) | `CacheAdminService.getCacheStats()` correctly reflects real hit/miss counts from a Caffeine cache, and `clearCache()` actually invalidates entries. |
| `ProductControllerTest` | `@WebMvcTest` slice test | HTTP status codes, the `X-Response-Time-Ms` header, JSON shape, and validation error responses, with the service layer mocked. |
| `CategoryControllerTest` | `@WebMvcTest` slice test | Same, for categories, including the 409 Conflict path on duplicate name. |
| `ProductRepositoryTest` | `@DataJpaTest` | Derived-query repository methods (`findBySku`, `findByCategoryIdAndActive`, `findByNameContainingIgnoreCase`) behave correctly against a real (H2) JPA session. |
| **`ProductCacheIntegrationTest`** | Full `@SpringBootTest`, repository **mocked**, cache proxy **active** | **The core proof that Caffeine is actually intercepting calls through Spring's abstraction.** Asserts `productRepository.findById()` is called exactly once across three repeated `getProductById()` calls; asserts an update refreshes the cached value (via `@CachePut`) without a redundant repository read on the next `get`; asserts delete evicts the entry so the next `get` goes back to the repository; asserts `listProducts()` with identical parameters hits the repository only once. |

The split between `ProductServiceTest` (pure logic, no proxy) and `ProductCacheIntegrationTest` (full context, proxy active) is deliberate: `@Cacheable`/`@CachePut`/`@CacheEvict` only take effect through a Spring-managed proxy, so unit-testing the service class directly can never validate caching behavior — that's exactly what the integration test exists for.

---

## 9. Docker

### `Dockerfile` (multi-stage)

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build   # build stage: full JDK + Maven
...
RUN mvn -B -q dependency:go-offline            # cache deps in their own layer
COPY src ./src
RUN mvn -B -q clean package -DskipTests        # tests already ran in CI/locally

FROM eclipse-temurin:21-jre-alpine AS runtime  # runtime stage: JRE only, tiny image
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring                              # non-root — smaller attack surface
COPY --from=build /build/target/catalog-service.jar app.jar
EXPOSE 8080
HEALTHCHECK ... CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"'
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- **Multi-stage build** keeps the final image to a JRE + jar, not a full JDK/Maven toolchain.
- **Dependency layer caching**: `pom.xml` is copied and `dependency:go-offline` run *before* `src` is copied, so source-only changes don't invalidate the dependency download layer.
- **Non-root user** (`spring:spring`) follows container security best practice.
- **`HEALTHCHECK`** polls `/actuator/health` and greps for `"status":"UP"`, giving Docker (and `docker compose`'s `condition: service_healthy`) a real readiness signal.

### `docker-compose.yml`

Two services only, per this project's scope (no Redis, no message broker, no extra infra):

- **`mysql`**: MySQL 8.0, seeded with `catalog_db` / `catalog_user` / `catalog_pass` via env vars, a named volume `catalog-mysql-data` for persistence across restarts, and a `mysqladmin ping` healthcheck.
- **`catalog-service`**: built from the local `Dockerfile`, waits on `mysql`'s healthcheck (`depends_on: condition: service_healthy`) before starting, and gets its DB connection details and `SIMULATED_LATENCY_MS` from environment variables that map straight onto `application.yml`'s `${...:default}` placeholders.

Both are attached to a dedicated bridge network (`catalog-net`) so the service can reach MySQL by hostname `mysql`.

---

## 10. Interview Preparation

### Caffeine-specific FAQs

**Q: Why Caffeine instead of `ConcurrentHashMap` + manual TTL, or a distributed cache like Redis?**
A `ConcurrentHashMap` gives you no eviction policy, no size bound, no stats, and you'd hand-roll expiry with a background thread — reinventing a worse Caffeine. Redis solves cross-instance consistency but adds a network hop, a piece of infrastructure to run/monitor, and serialization cost on every access — overkill when staleness of a few minutes is fine and each instance can maintain its own cache independently, which is the case for a product catalog.

**Q: What eviction algorithm does Caffeine use, and why does it matter?**
Window TinyLFU — a hybrid that keeps a small LRU "window" for newly-admitted entries plus a frequency-sketch-based main cache, which approximates optimal (Belady's) eviction far better than plain LRU or LFU alone under skewed, real-world access patterns (a small number of "hot" products get most of the traffic).

**Q: `expireAfterWrite` vs `expireAfterAccess` — how did this project choose, and why?**
`expireAfterWrite` invalidates a fixed time after the entry was last **written/loaded** — used for `products`, `productLists`, `productSearch`, where a stale-but-not-ancient value is acceptable and you want a hard upper bound on staleness. `expireAfterAccess` (used for `categories`) resets the timer on every **read**, so a frequently-accessed category never expires as long as it keeps being read — appropriate because categories change so rarely that "has anyone asked for this in the last 30 minutes" is the more useful question than "was this loaded in the last 30 minutes."

**Q: Why does `ProductService` only ever populate the cache via `@CachePut`, never via `@Cacheable`-on-write?**
Separation of concerns: reads (`getProductById`) are the only methods allowed to lazily populate the `products` cache via `@Cacheable`. Writes (`updateProduct`, `adjustStock`) explicitly push the *known-correct, just-persisted* value into the cache via `@CachePut`, which always executes the method body and unconditionally overwrites the cache entry — there's no risk of caching a value that never made it to the database, and no ambiguity about "does this write count as a cache-populating read."

**Q: Why evict `productLists`/`productSearch` with `allEntries=true` on every write, instead of evicting a specific key?**
Because a single product's `id` doesn't tell you which cached **pages** it appears on — a new/updated product could land on page 1 of a category listing or page 5 of a search result depending on sort order and filters. There's no cheap, correct way to compute "which cache keys does this affect," so the pragmatic choice is to evict the whole cache on any write and accept the next read being a (individually cheap, short-TTL) miss.

**Q: How do you test that `@Cacheable` is actually working?**
You can't from a plain unit test that `new`s up the service directly — Spring's caching annotations are woven in by a **proxy** created by the Spring container, and calling a method on the raw object bypasses that proxy entirely. You need a `@SpringBootTest` (or narrower cache-focused slice) with the *real dependency injected via the container* and the underlying repository mocked, then assert on **call counts** to the mock across repeated calls — exactly what `ProductCacheIntegrationTest` does here.

**Q: What are the pitfalls of `@Cacheable` on a method that returns a mutable object?**
If the cached object is mutated by a caller after retrieval, that mutation corrupts the cached value for every subsequent reader — this project avoids the issue entirely by caching immutable `record` DTOs (`ProductResponse`, `PageResponse<T>`), never JPA entities.

**Q: Why does this project return `PageResponse<T>` instead of Spring Data's `Page<T>` from cached methods?**
`Page<T>`'s standard implementation (`PageImpl`) isn't a clean, guaranteed-serializable value object for every downstream cache/serialization concern, and coupling your cache's stored shape to a framework interface is fragile. `PageResponse<T>` is a plain `Serializable` record with exactly the fields needed, making it a well-behaved cache value regardless of which `CacheManager` implementation is behind `@Cacheable`.

### Common mistakes (and how this project avoids them)

- **Caching mutable entities directly** → avoided by caching DTOs only.
- **Self-invocation bypassing the proxy** (calling an `@Cacheable` method from another method in the *same* class) → avoided because all cache-annotated methods are called from controllers/tests, never internally from within `ProductService`/`CategoryService`.
- **One global Caffeine spec for every cache** (Spring's `CaffeineCacheManager` default) → avoided via `registerCustomCache` per cache name in `CacheConfig`, so `products` and `productSearch` can have very different TTLs/sizes.
- **Unbounded caches** (`Caffeine.newBuilder().build()` with no `maximumSize`) → every cache here has an explicit `maximumSize`, bounding worst-case heap usage.
- **No visibility into cache effectiveness** → `recordStats()` is on for every cache, exposed via the custom `/api/v1/cache/stats` endpoint (distinct from and complementary to Spring's own `/actuator/caches`).
- **Assuming a passing unit test proves caching works** → this repo explicitly separates `ProductServiceTest` (proxy-free, logic-only) from `ProductCacheIntegrationTest` (proxy-active, call-count assertions).

### Production considerations

- **Per-instance memory**: with N horizontally-scaled replicas, you get N independent copies of hot data — each `maximumSize` is a per-instance bound, so total cluster-wide cache memory scales with instance count, not with dataset size.
- **Consistency window**: a write on instance A doesn't invalidate instance B's local cache — that instance keeps serving the old value until its own TTL expires. Acceptable here (minutes-scale staleness on a catalog); not acceptable for, say, an account balance.
- **Restart = cold cache**: every deploy/restart starts every cache empty, so the first wave of requests after a rollout pays full latency again — this is a real capacity-planning consideration (thundering herd on cold start) that a synchronous `@Cacheable` (no `sync=true`) doesn't protect against under concurrent identical misses.
- **Monitoring**: `recordStats()` has a small but non-zero overhead — worth it in almost all real systems, since flying blind on hit rate makes cache tuning guesswork.
- **When to graduate to a distributed cache**: once staleness-across-instances becomes unacceptable, or the working set no longer fits comfortably in a single JVM's heap budget, Caffeine as a local **near-cache** in front of Redis (the "two-tier cache" pattern) is a common next step rather than a full replacement.

### Performance notes

- A Caffeine hit is a lock-free (or near-lock-free) heap read — typically low single-digit microseconds; this project's demo instead measures the *end-to-end HTTP round trip* (`X-Response-Time-Ms`), where the dominant cost on a hit is JSON serialization and network stack overhead, not the cache lookup itself — which is exactly why the artificial 250ms repository latency is needed to make the miss-vs-hit gap visible at all.
- `estimatedSize()` (used in `CacheAdminService`) is a fast, approximate count — Caffeine avoids maintaining an exact size under high concurrency for performance reasons.

---

## License

MIT — see [LICENSE](./LICENSE).
