# TicketWave — Test Failure Resolution & Coverage Enhancement

**Version:** 1.0  
**Date:** March 12, 2026  

---

## Overview

This document records how test failures were identified, root-caused, and resolved using GitHub Copilot, along with coverage enhancement strategies applied.

---

## 1. Test Failures Identified & Resolved

### 1.1 N+1 Query in BookingService.confirmBooking()

**Failure:** `testConfirmBooking_MultipleSeats` — slow execution, excessive DB calls  
**Root Cause:** Loop fetched seats individually: `seatRepository.findById()` per booking item  
**Fix (BookingServiceRefactored.java):**
- Batch-fetched all seat IDs in one query using `seatRepository.findAllById(seatIds)`
- Reduced queries from N+1 to 3 for N seats
- Performance: 5x improvement (15 queries → 3 for 5 seats)

### 1.2 Race Condition in SeatHoldService.extendHold()

**Failure:** `testRapidHoldExtension` — intermittent: hold expired mid-extension  
**Root Cause:** Non-atomic read-then-write on Redis TTL  
**Fix (SeatHoldServiceRefactored.java):**
- Replaced `GET + EXPIRE` with atomic Lua script for TTL extension
- Ensured single round-trip to Redis for atomic guarantee

### 1.3 Predictable PNR Generation

**Failure:** Code review finding, not test failure  
**Root Cause:** PNR used `UUID.randomUUID()` which is predictable (Type 4 UUID)  
**Fix:** Switched to `SecureRandom` with alphanumeric character set for PNR generation

### 1.4 Concurrent Seat Hold Double-Win

**Failure:** `testConcurrentSeatHolds_OnlyOneSucceeds` — sometimes 2 threads won  
**Root Cause:** Redisson lock wait/lease parameters too generous  
**Fix:** Tightened lock parameters (2s wait, 5s lease) and added post-lock Redis key verification

---

## 2. Coverage Enhancement Strategies

### 2.1 Added Edge Case Tests
- Null input validation (SeatHoldServiceEdgeCaseTest — 15 tests)
- Boundary values at pricing tiers (PricingCalculationServiceTest — 16 tests)
- Empty/zero scenarios (zero seats, no refunds, empty results)

### 2.2 Added Concurrency & Stress Tests
- SeatHoldServiceConcurrencyTest (6 tests) — race condition validation
- SeatHoldServiceConcurrencyStressTest (9 tests) — high-volume operations
- PaymentServiceConcurrencyStressTest (6 tests) — idempotent webhook processing
- RefundServiceConcurrencyStressTest (4 tests) — concurrent approval conflicts

### 2.3 Added Controller Integration Tests
- ScheduleSearchControllerTest (11 tests) — MockMvc with security context
- AuditControllerTest (20 tests) — Admin-only access, pagination, filtering

### 2.4 Coverage Enforcement
- Added `jacoco-maven-plugin` to `pom.xml` with 80% minimum line coverage
- Build fails automatically if coverage drops below threshold

---

## 3. Coverage Configuration

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## 4. Test Run Commands

```bash
# Run all tests with coverage
mvn clean test

# Generate coverage report
mvn jacoco:report

# View report
# Open: target/site/jacoco/index.html
```
