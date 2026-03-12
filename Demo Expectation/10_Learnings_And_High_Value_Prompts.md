# TicketWave — Learnings & High Value Prompts

**Version:** 1.0  
**Date:** March 12, 2026  
**Project:** TicketWave — Ticket Booking System (Travel & Events)  
**Tool Used:** GitHub Copilot (Chat, Inline, Agent Modes)  

---

## Table of Contents

1. [Key Learnings](#1-key-learnings)
2. [High Value Prompts — Project Setup & Architecture](#2-high-value-prompts--project-setup--architecture)
3. [High Value Prompts — Code Generation](#3-high-value-prompts--code-generation)
4. [High Value Prompts — Code Review & Quality](#4-high-value-prompts--code-review--quality)
5. [High Value Prompts — Testing](#5-high-value-prompts--testing)
6. [High Value Prompts — Documentation](#6-high-value-prompts--documentation)
7. [Prompt Engineering Best Practices](#7-prompt-engineering-best-practices)

---

## 1. Key Learnings

### 1.1 What Worked Well

- **Repo-level custom instructions (`.github/copilot-instructions.md`)** dramatically improved Copilot output quality. Once coding conventions, naming standards, and architecture rules were defined, code generation aligned with enterprise expectations.
- **Context-rich prompts** that referenced existing code, design doc, and module names produced far more accurate results than generic requests.
- **Iterative refinement** — starting with a broad prompt, reviewing the output, then feeding specific corrections back yielded the best code quality within 2–3 iterations.
- **Using Copilot for test generation** was highly effective — especially when providing the service class content and asking for "Given/When/Then JUnit 5 tests covering happy path, edge cases, and concurrency failures."
- **Document generation from annotated code** was efficient — Copilot correctly extracted contracts, business rules, and state machines from code comments, method signatures, and entity annotations.

### 1.2 What Required Careful Oversight

- **Concurrency code** — Copilot could generate Redis-based locking patterns, but required manual review for atomicity guarantees (e.g., Lua scripts for atomic TTL extension were manually guided).
- **Security configurations** — JWT filter chains, CORS policies, and role-based access were generated but needed manual verification against OWASP guidelines.
- **Database migrations** — Copilot suggested entity designs well but did not auto-generate Flyway migrations; schema evolution required manual attention.
- **Test mocking granularity** — some generated tests over-mocked (mocking value objects) or under-mocked (missing Redis template stubs). Explicit guidance in prompts fixed this.

### 1.3 Surprises & Insights

- Copilot performed exceptionally at **boilerplate reduction** — DTOs, mappers, repository interfaces, and exception classes were generated correctly in one shot.
- When given the **full booking lifecycle state machine** as context, Copilot generated accurate state transition validation logic, including edge cases like "cancel already cancelled."
- **Stress/concurrency test generation** was the most impressive — Copilot understood ExecutorService patterns and CountDownLatch usage for testing race conditions.
- Copilot's code review suggestions caught real production issues: N+1 queries, predictable PNR generation, and missing pessimistic locks.

---

## 2. High Value Prompts — Project Setup & Architecture

### Prompt: Initialize Modular Monolith Project
```
Create a Spring Boot 3.x project for a ticket booking system called TicketWave. 
Use Java 17, PostgreSQL, Redis, JWT authentication. Structure it as a modular monolith 
with CQRS-friendly boundaries. Modules: booking, catalog, payment, refund, user, 
inventory, admin, auth. Each module should have: api, application, domain, infrastructure, 
and mapper layers. Include common module with config, security, exception handling, 
audit and logging utilities.
```
**Why it works:** Specifies technology stack, architecture pattern, module names, and layer structure upfront — eliminates ambiguity.

### Prompt: Generate Repo-Level Custom Instructions
```
Based on this TicketWave project (Spring Boot modular monolith with CQRS, PostgreSQL, 
Redis, JWT), generate a comprehensive .github/copilot-instructions.md covering: 
naming conventions, package structure, DTO vs entity separation, REST API standards, 
exception handling, logging standards, transaction boundaries, concurrency & seat hold 
strategy, idempotency, security best practices, payment webhook standards, testing 
standards (JUnit 5 + Mockito, 80% coverage), and code documentation standards. 
Make it production-grade for enterprise delivery.
```
**Why it works:** Lists every section needed, specifies the quality bar ("production-grade"), and provides enough domain context (seat holds, webhooks) for accurate rules.

---

## 3. High Value Prompts — Code Generation

### Prompt: Distributed Seat Hold with Redis
```
Implement a SeatHoldService for TicketWave that uses Redis + Redisson distributed locks. 
Requirements:
- Hold seat atomically using Redisson RLock (2s wait, 5s lease)
- Store hold in Redis: key "seat:hold:{seatId}" with value userId, TTL 600s
- Generate unique hold token stored in "hold:token:{token}"
- Validate holds by checking Redis key + userId + token match
- Release holds by deleting both Redis keys
- Handle lock acquisition failure with ConflictException (409)
- Log key business events with SLF4J
Follow the project's existing service patterns and naming conventions.
```
**Why it works:** Specifies exact Redis key format, TTL, lock parameters, exception type, and references existing project patterns.

### Prompt: Booking Lifecycle with State Machine
```
Create BookingService with these state transitions:
INITIATED → PENDING_PAYMENT (when payment intent created)
PENDING_PAYMENT → CONFIRMED (when payment webhook succeeds)
PENDING_PAYMENT → FAILED (when payment webhook fails)
CONFIRMED → CANCELLED (when user cancels within policy)

Each transition should:
- Validate current state before transitioning
- Use @Transactional with optimistic locking (@Version)
- Batch-fetch seats to avoid N+1 queries
- Generate secure PNR using SecureRandom (not UUID)
- Log state change via BookingEventLogger
- Release seat holds on failure/cancellation
```
**Why it works:** Explicit state machine with transition rules, cross-cutting concerns (transactions, locking, logging), and anti-patterns to avoid (N+1, insecure PNR).

### Prompt: Payment Webhook Controller (Idempotent)
```
Create PaymentWebhookController that:
- Accepts POST /api/v1/payments/webhook
- Verifies webhook signature via shared secret (header: X-Webhook-Signature)
- Deduplicates by provider event ID using IdempotencyKeyService
- Processes payment confirmation/failure asynchronously
- Persists raw event metadata for audit trail
- Returns 200 immediately (even for duplicate/already-processed events)
- Maps to PaymentService.confirmPayment() or failPayment()
Include proper error handling, SLF4J logging, and @Auditable annotation.
```
**Why it works:** Specifies exact endpoint, security verification, idempotency mechanism, and response contract. References existing services in the project.

### Prompt: Dynamic Pricing Engine
```
Implement PricingCalculationService with demand-based dynamic pricing:
- Formula: FINAL_PRICE = BASE_FARE × PRICE_MODIFIER × DEMAND_FACTOR
- Demand tiers based on seat availability percentage:
  - ≥30% available → factor 1.0 (normal)
  - 10–30% available → factor 1.5 (high demand)  
  - <10% available → factor 1.8 (critical)
- Handle edge cases: zero seats, sold out, negative modifiers
- Round to 2 decimal places using BigDecimal
- Include isHighDemand() convenience method
- Unit testable with no external dependencies
```
**Why it works:** Provides the exact formula, tier thresholds, edge cases, and rounding requirements — no ambiguity in business logic.

---

## 4. High Value Prompts — Code Review & Quality

### Prompt: Comprehensive Code Review
```
Review the following services for production readiness: BookingService, SeatHoldService, 
PaymentService, RefundService. Check for:
- N+1 query problems and missing batch operations
- Race conditions and missing locks (pessimistic/optimistic)
- Security vulnerabilities (predictable tokens, missing auth checks)
- Non-atomic Redis operations that need Lua scripts
- Missing idempotency on mutation endpoints
- Transaction scope issues (long transactions with network calls)
- Error handling gaps and missing audit logging
Categorize findings by severity: Critical, High, Medium, Low.
Provide code fixes for each critical finding.
```
**Why it works:** Lists specific review categories relevant to the domain (Redis atomicity, N+1, transaction scope), asks for severity classification, and requests actual code fixes.

### Prompt: Apply Code Review Corrections
```
Based on these critical findings from the code review:
1. N+1 query in BookingService.confirmBooking() — fetch seats in batch
2. Race condition in SeatHoldService.extendHold() — use Lua script for atomic TTL
3. Predictable PNR in BookingService — switch from UUID to SecureRandom alphanumeric
4. Missing webhook signature verification in PaymentWebhookController

Create refactored versions: BookingServiceRefactored.java, SeatHoldServiceRefactored.java. 
Show the exact code changes with before/after for each fix. 
Maintain all existing functionality while fixing the issues.
```
**Why it works:** References specific findings by number, names the exact methods and files, and asks for before/after comparison.

---

## 5. High Value Prompts — Testing

### Prompt: Generate Comprehensive Unit Tests
```
Generate JUnit 5 + Mockito tests for SeatHoldService covering:
- Happy path: hold seat successfully, validate hold, release hold
- Edge cases: null seatId, null userId, expired hold, zero/negative TTL,
  mismatched token, non-existent hold release (idempotent)
- Concurrency: multiple threads holding same seat (only 1 wins), 
  rapid release and re-hold, lock acquisition timeout
- Stress: 50 concurrent attempts on same seat, 100 threads on different seats,
  hold validation under high contention

Use Given/When/Then naming: shouldReturnConflictWhenSeatAlreadyHeld.
Mock RedisTemplate and RLock. Use ExecutorService + CountDownLatch for concurrency tests.
Test names should describe behavior.
```
**Why it works:** Categorizes tests by type (happy/edge/concurrency/stress), specifies exact scenarios, names the mocking strategy, and prescribes the naming convention.

### Prompt: Resolve Test Failures
```
These tests are failing:
1. testConcurrentSeatHolds_OnlyOneSucceeds — intermittent failure, 
   sometimes 2 threads succeed
2. shouldRefundPaymentSuccessfully — NPE at line 45 of RefundServiceTest

For each failure:
- Explain the root cause
- Show the exact fix (both test and production code if needed)
- Add any additional test assertions to prevent regression
```
**Why it works:** Provides specific failure details including test names and error information, asks for root cause + fix + prevention.

### Prompt: Configure Test Coverage
```
Add JaCoCo Maven plugin to pom.xml for TicketWave:
- Prepare agent in initialize phase
- Generate report after test phase
- Enforce minimum 80% line coverage at BUNDLE level
- Fail build if coverage drops below threshold
Show the complete plugin configuration.
```
**Why it works:** Specifies exact plugin, phases, coverage threshold, and enforcement behavior.

---

## 6. High Value Prompts — Documentation

### Prompt: Generate Functional Specification from Code
```
Analyze the TicketWave codebase (all modules: booking, catalog, payment, refund, 
auth, audit) and generate a Functional Specification Document including:
- System overview and architecture
- Module-by-module feature descriptions
- API endpoint catalog with request/response contracts
- Business rules and validation logic
- State machines and workflow diagrams (as text)
- Security model and access control matrix
- Error codes and exception mapping table
- Non-functional requirements (concurrency, performance, availability)
Derive all information from the annotated code, not assumptions.
```
**Why it works:** Specifies every section of the document and explicitly states "derive from code" to avoid hallucination.

### Prompt: Generate Functional Test Cases Document
```
Create a comprehensive Functional Test Cases Documentation that catalogs all 128+ 
test cases across 21 test files. For each test:
- Given/When/Then format
- Module and service being tested
- Category: Unit, Integration, Concurrency, Stress, Security

Group by module. Include coverage summary table at the end.
```
**Why it works:** Provides exact scope (128+ tests, 21 files), format requirement, and asks for structured grouping.

---

## 7. Prompt Engineering Best Practices

### DO ✓

| Practice | Example |
|----------|---------|
| **Be specific about technology** | "Spring Boot 3.x with Spring Security 6" not "a web framework" |
| **Name exact classes/methods** | "Implement `SeatHoldService.holdSeat()`" not "create a seat holding feature" |
| **Specify error handling** | "Throw `ConflictException` (HTTP 409)" not "handle errors" |
| **Include business rules** | "TTL = 600 seconds, Redisson lock wait = 2s, lease = 5s" |
| **Reference existing code** | "Follow the pattern in BookingService" |
| **Ask for specific test categories** | "Happy path, edge cases, concurrency, stress" |
| **Set quality bar** | "Production-grade, 80% coverage, zero failures" |
| **Request categorization** | "Severity: Critical, High, Medium, Low" |

### DON'T ✗

| Anti-Pattern | Problem |
|-------------|---------|
| "Create a booking system" | Too vague — no technology, patterns, or constraints |
| "Write tests" | Missing scope, naming convention, mock strategy |
| "Fix the bugs" | No specific bug description, file, or line reference |
| "Make it better" | Undefined quality criteria |
| "Add security" | Doesn't specify JWT, CORS, roles, or threat model |
| Prompting without context | Copilot can't read what's not provided — always attach relevant code or docs |

### Prompt Structure Template
```
[ACTION] [TARGET] for [PROJECT CONTEXT]:
- Requirement 1 (specific, measurable)
- Requirement 2 (with exact values/thresholds)
- Requirement 3 (referencing existing patterns)
- Edge cases to handle
- Error handling expectations
- Naming/coding conventions to follow
```

---

## Summary

| Metric | Value |
|--------|-------|
| Total prompts refined & documented | 12 |
| Estimated time saved via Copilot | 60–70% on boilerplate, 40–50% on tests |
| Key success factor | Repo-level instructions + context-rich prompts |
| Biggest productivity gain | Test generation (concurrency + stress tests) |
| Area requiring most oversight | Concurrency correctness & security config |
