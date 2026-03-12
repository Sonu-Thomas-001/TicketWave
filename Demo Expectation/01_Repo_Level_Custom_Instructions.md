# Copilot Instructions — TicketWave (Production-Grade Spring Boot)

## 1) Project Intent
TicketWave is a production-grade travel and event ticket booking system implemented as a Spring Boot modular monolith with CQRS-friendly boundaries.

Core requirements:
- PostgreSQL as the system of record
- Redis for distributed seat locking/holds
- JWT-based authentication and authorization
- Concurrency safety via optimistic locking + distributed hold semantics
- Payment confirmation via webhook-driven workflow
- Test stack: JUnit 5 + Mockito
- Minimum test coverage: **80% line coverage**

Copilot must generate code that is reliable, secure, observable, and maintainable for enterprise delivery.

---

## 2) Technology & Version Baseline
When generating code, align with these assumptions unless explicitly overridden:
- Java 21+
- Spring Boot 3.x
- Spring Security 6.x
- Spring Data JPA + Hibernate
- PostgreSQL dialect
- Redis via Spring Data Redis (or Redisson if project uses it)
- Build tool: Maven or Gradle based on existing repository setup

Do not introduce alternative frameworks unless requested.

---

## 3) Naming Conventions
Use consistent, explicit names.

### 3.1 Classes & Interfaces
- Controllers: `*Controller`
- Application services/use cases: `*Service` or `*UseCase`
- Domain services: `*DomainService`
- Repositories: `*Repository`
- Mappers: `*Mapper`
- DTOs: `*Request`, `*Response`, `*Dto`
- Entities: singular nouns, e.g., `Booking`, `SeatHold`, `Payment`
- Exceptions: `*Exception` and domain-specific names (e.g., `SeatAlreadyHeldException`)

### 3.2 Methods
- Verb-first names with clear intent: `createBooking`, `holdSeats`, `confirmPaymentWebhook`
- Query methods should read naturally: `findActiveHoldBySeatIdAndUserId`

### 3.3 Variables
- Avoid abbreviations unless industry standard (`jwt`, `id`, `url`)
- Boolean names should read as predicates: `isExpired`, `hasCapacity`

---

## 4) Package Structure (Modular Monolith, CQRS Friendly)
Use feature-first modules with explicit boundaries.

```text
com.ticketwave
  ├─ common
  │   ├─ config
  │   ├─ exception
  │   ├─ security
  │   ├─ logging
  │   └─ util
  ├─ booking
  │   ├─ api                (controllers, request/response DTOs)
  │   ├─ application        (use cases, command/query handlers)
  │   ├─ domain             (entities, value objects, domain services)
  │   ├─ infrastructure     (jpa repositories, redis adapters, external clients)
  │   └─ mapper
  ├─ inventory              (same layered pattern)
  ├─ payment                (same layered pattern)
  ├─ event                  (same layered pattern)
  └─ user                   (same layered pattern)
```

Rules:
- Keep module internals encapsulated.
- Cross-module access should go through application-level contracts, not direct entity manipulation.
- Separate command and query paths where complexity warrants it.

---

## 5) DTO vs Entity Separation
Strictly separate persistence models from API contracts.

- Never expose JPA entities directly in REST responses.
- Use dedicated DTOs for input/output.
- Validate request DTOs using Jakarta Bean Validation annotations.
- Map using explicit mappers (MapStruct or manual mapper based on existing project pattern).
- Do not place web-layer annotations in domain entities.

---

## 6) REST API Standards
### 6.1 URI & HTTP Semantics
- Use plural resource names: `/api/v1/bookings`, `/api/v1/events/{eventId}/seats`
- Use proper methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`
- Return correct status codes: `200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, `422`, `429`, `500`

### 6.2 Response Shape
- Prefer consistent envelope for errors.
- Include `timestamp`, `status`, `errorCode`, `message`, `path`, `correlationId` in error responses.

### 6.3 Pagination & Filtering
- For list endpoints, support pagination (`page`, `size`, `sort`) and bounded limits.
- Never return unbounded datasets in production endpoints.

### 6.4 Versioning
- Prefix APIs with version (`/api/v1`).
- Avoid breaking response contracts without version increments.

---

## 7) Exception Handling Standards
- Centralize with `@RestControllerAdvice`.
- Use domain-specific exceptions for business failures.
- Map exceptions to stable error codes (machine-readable) and meaningful messages.
- Do not leak stack traces, SQL details, secrets, or infrastructure internals in API responses.
- Log full exception details server-side with correlation identifiers.

Recommended mapping examples:
- Validation failure → `400`
- Resource not found → `404`
- Optimistic lock / seat conflict → `409`
- Unauthorized/forbidden → `401/403`
- Unexpected server errors → `500`

---

## 8) Logging & Observability Standards
Use structured, production-safe logs.

- Use SLF4J (`log.info/debug/warn/error`) with parameterized messages.
- Include correlation/trace IDs (MDC) in all request flows.
- Log key business events: seat hold created/released, booking created, payment webhook processed.
- Never log credentials, JWT payload secrets, full payment PAN/CVV, or PII beyond policy.
- `INFO` for lifecycle/business milestones, `WARN` for recoverable anomalies, `ERROR` for failures.
- Avoid noisy logs inside tight loops.

---

## 9) Transaction Boundaries
- Define transactions at service/use-case layer (`@Transactional`).
- Keep transactional methods small and cohesive.
- Use read-only transactions for query-only paths.
- Avoid network calls inside long-running DB transactions where possible.
- For booking + seat operations, ensure consistency rules are explicit and tested.
- Use optimistic locking (`@Version`) on contention-prone aggregates.

---

## 10) Concurrency & Seat Hold Strategy
- Seat hold must be atomic and time-bound (TTL) in Redis.
- Use unique hold tokens and owner context (user/session).
- Confirm booking only if hold is valid and not expired.
- Release holds on timeout/cancel/failure paths.
- Handle race conditions with deterministic conflict responses (`409`).
- Persist final booking state in PostgreSQL as source of truth.

---

## 11) Idempotency Handling
Required for payment/webhook and booking-confirmation operations.

- Support idempotency keys for mutation endpoints where retries are expected.
- Persist idempotency key + request fingerprint + outcome for replay safety.
- For webhook processing, enforce deduplication by provider event ID.
- Return same semantic result for repeated requests with same idempotency key.
- Define expiry/retention policy for idempotency records.

---

## 12) Security Best Practices
- JWT validation must verify signature, expiry, issuer, and audience (if applicable).
- Use stateless security config; disable session state unless explicitly needed.
- Apply least privilege with role/authority checks at endpoint and/or service level.
- Hash passwords with strong algorithms (`BCrypt` or stronger supported alternative).
- Enforce input validation and output encoding where relevant.
- Protect sensitive endpoints with rate limiting and anti-abuse checks.
- Configure CORS narrowly for approved origins.
- Never hardcode secrets; use environment variables/secret manager.
- Sanitize logs and error payloads to prevent data leakage.

---

## 13) Payment Webhook Standards
- Verify webhook authenticity (signature/shared secret/cert validation per provider).
- Treat webhook handlers as at-least-once delivery consumers.
- Ensure idempotent processing and safe retries.
- Persist raw event metadata (non-sensitive) for auditability.
- Decouple heavy processing via async/event-driven internal workflow when needed.

---

## 14) Code Documentation Standards
- Public classes/methods in application/domain layers require concise Javadoc for intent and invariants.
- Document non-obvious business rules (seat hold expiry, cancellation windows, refund constraints).
- Keep comments accurate; remove stale comments in refactors.
- Prefer self-descriptive code over excessive inline comments.

---

## 15) Test Structure Standards (JUnit 5 + Mockito)
### 15.1 Test Types
- Unit tests: domain logic, mappers, utility and service behavior with mocks.
- Slice/integration tests: repository, controller, security, transactional behavior.
- Add concurrency-focused tests for seat hold and optimistic locking conflict paths.

### 15.2 Naming & Pattern
- Use Given/When/Then structure.
- Test names should describe behavior, e.g., `shouldReturnConflictWhenSeatAlreadyHeld`.
- One behavior/assertion focus per test (or cohesive assertion set for one scenario).

### 15.3 Coverage Gate
- Maintain minimum **80%** line coverage.
- Cover happy path + edge cases + failure/retry/idempotency scenarios.
- Do not use Mockito for simple value objects/entities where real instances are clearer.

---

## 16) Data & Persistence Standards
- Use Flyway/Liquibase migrations for schema evolution; no manual schema drift.
- Add indexes for frequent lookup paths (event/date/seat status/hold expiry).
- Prefer explicit fetch strategies to avoid N+1 issues.
- Use audit fields (`createdAt`, `updatedAt`, optional `createdBy`, `updatedBy`).
- Keep entities persistence-focused; place business orchestration in services.

---

## 17) Performance & Reliability Guidelines
- Use caching deliberately; define TTL and invalidation rules.
- Guard against thundering herd and hot-key seat contention.
- Apply timeout/retry policies for external integrations (payment providers).
- Implement graceful degradation for non-critical dependencies.

---

## 18) Copilot Output Rules (Mandatory)
When generating code:
- Follow existing project conventions and dependency choices first.
- Prefer minimal, focused changes over large rewrites.
- Include validation, exception mapping, and tests for new behaviors.
- Add or update API documentation annotations if already in use.
- Ensure new endpoints are secured by default unless explicitly public.
- Ensure all mutating operations consider idempotency and concurrency.
- Avoid generating placeholder TODO code in production paths.

If a requirement conflicts with existing code, propose the safest production-ready approach and explain trade-offs briefly in comments/PR notes.