# TicketWave — Test Setup & JUnit Test Configuration

**Version:** 1.0  
**Date:** March 12, 2026  
**Framework:** JUnit 5 (Jupiter) + Mockito + Spring Boot Test  

---

## Test Dependencies (pom.xml)

| Dependency | Purpose |
|-----------|---------|
| `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ, Spring Test, MockMvc |
| `spring-security-test` | `@WithMockUser`, Security test utilities |
| `jacoco-maven-plugin` | Code coverage reporting & 80% enforcement |

---

## Test Structure

```
src/test/java/com/ticketwave/
├── TicketWaveApplicationTests.java          (context load)
├── booking/application/
│   ├── BookingServiceTest.java              (10 tests)
│   ├── BookingServiceEnhancedTest.java      (7 tests)
│   ├── BookingEventLoggerTest.java          (event logging)
│   ├── SeatHoldServiceTest.java             (7 tests)
│   ├── SeatHoldServiceEdgeCaseTest.java     (15 tests)
│   ├── SeatHoldServiceConcurrencyTest.java  (6 tests)
│   ├── SeatHoldServiceConcurrencyStressTest.java (9 tests)
│   └── IdempotencyKeyServiceTest.java       (8 tests)
├── catalog/
│   ├── api/ScheduleSearchControllerTest.java (11 tests)
│   └── application/
│       ├── ScheduleSearchServiceTest.java   (6 tests)
│       └── PricingCalculationServiceTest.java (16 tests)
├── payment/application/
│   ├── PaymentServiceTest.java              (17 tests)
│   └── PaymentServiceConcurrencyStressTest.java (6 tests)
├── refund/application/
│   ├── RefundServiceTest.java               (14 tests)
│   ├── RefundServiceConcurrencyStressTest.java (4 tests)
│   ├── CancellationPolicyEngineTest.java    (9 tests)
│   └── RefundLedgerServiceTest.java         (13 tests)
└── common/audit/
    ├── AuditServiceTest.java                (17 tests)
    ├── AuditAspectTest.java                 (13 tests)
    └── AuditControllerTest.java             (20 tests)
```

**Total: 21 test files | 128+ test methods**

---

## Test Categories

| Category | Count | Description |
|----------|-------|-------------|
| Unit Tests | ~85 | Service logic with mocked dependencies |
| Controller Tests | ~31 | MockMvc-based slice tests with security |
| Concurrency Tests | ~15 | ExecutorService + CountDownLatch race conditions |
| Stress Tests | ~19 | High-volume parallel operations |
| Edge Case Tests | ~15 | Null inputs, boundary values, error paths |

---

## Test Execution Commands

```bash
# Run all tests
mvn clean test

# Run specific module tests
mvn test -Dtest="com.ticketwave.booking.**"
mvn test -Dtest="com.ticketwave.payment.**"
mvn test -Dtest="com.ticketwave.refund.**"
mvn test -Dtest="com.ticketwave.catalog.**"
mvn test -Dtest="com.ticketwave.common.audit.**"

# Run with coverage report
mvn clean test jacoco:report

# View HTML coverage report
# target/site/jacoco/index.html
```

---

## Coverage Configuration (JaCoCo)

- **Plugin:** `jacoco-maven-plugin 0.8.12`
- **Minimum Threshold:** 80% line coverage (BUNDLE level)
- **Report Location:** `target/site/jacoco/index.html`
- **Build Enforcement:** Build fails if coverage < 80%
