# Quick Start Guide - Implementing Code Review Fixes

This guide provides step-by-step instructions to implement the critical fixes from the code review.

---

## Prerequisites

- [x] Read `CODE_REVIEW_REPORT.md` (Executive Summary at minimum)
- [x] Review refactored files in `src/main/java/.../Refactored.java`
- [x] Backup current codebase: `git checkout -b backup/before-review-fixes`
- [x] Create feature branch: `git checkout -b fix/code-review-critical`

---

## Step-by-Step Implementation (4-6 hours)

### Step 1: Add New Repository Methods (30 minutes)

**Goal**: Add batch fetch and pessimistic locking methods to repositories.

#### 1.1 Update SeatRepository

Open: `src/main/java/com/ticketwave/catalog/infrastructure/SeatRepository.java`

Add these methods:
```java
@Query("SELECT s FROM Seat s JOIN FETCH s.schedule WHERE s.id IN :seatIds")
@Lock(LockModeType.PESSIMISTIC_WRITE)
List<Seat> findAllByIdWithLock(@Param("seatIds") List<UUID> seatIds);
```

#### 1.2 Update ScheduleRepository

Open: `src/main/java/com/ticketwave/catalog/infrastructure/ScheduleRepository.java`

Add:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Schedule s WHERE s.id = :scheduleId")
Optional<Schedule> findByIdWithLock(@Param("scheduleId") UUID scheduleId);
```

#### 1.3 Update BookingRepository

Open: `src/main/java/com/ticketwave/booking/infrastructure/BookingRepository.java`

Add:
```java
@Query("SELECT b FROM Booking b " +
       "LEFT JOIN FETCH b.payments " +
       "LEFT JOIN FETCH b.schedule s " +
       "LEFT JOIN FETCH s.event " +
       "WHERE b.id = :bookingId")
Optional<Booking> findByIdWithPaymentsAndSchedule(@Param("bookingId") UUID bookingId);

@Query("SELECT b FROM Booking b " +
       "LEFT JOIN FETCH b.bookingItems bi " +
       "LEFT JOIN FETCH bi.seat " +
       "WHERE b.id = :bookingId")
Optional<Booking> findByIdWithItemsAndSeats(@Param("bookingId") UUID bookingId);
```

#### 1.4 Update PaymentRepository

Open: `src/main/java/com/ticketwave/payment/infrastructure/PaymentRepository.java`

Add:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Payment> findByIdWithLock(UUID paymentId);

Optional<Payment> findByTransactionId(String transactionId);
```

**Verify**: Run `mvn clean compile` - should succeed with 0 errors.

---

### Step 2: Refactor BookingService confirmBooking (1 hour)

**Goal**: Fix N+1 query and race condition.

Open: `src/main/java/com/ticketwave/booking/application/BookingService.java`

#### 2.1 Replace the confirmBooking method

Find this section (around lines 90-130):
```java
// OLD CODE (N+1 problem)
for (SeatHold hold : seatHolds) {
    Seat seat = seatRepository.findById(hold.getSeatId()).orElseThrow();
    seat.setSeatStatus("BOOKED");
    seatRepository.saveAndFlush(seat);
}
```

Replace with:
```java
// NEW CODE (batch operation)
// 1. Extract seat IDs
List<UUID> seatIds = seatHolds.stream()
        .map(SeatHold::getSeatId)
        .collect(Collectors.toList());

// 2. Batch fetch with lock (1 query instead of N)
List<Seat> seats = seatRepository.findAllByIdWithLock(seatIds);

if (seats.size() != seatHolds.size()) {
    throw new ResourceNotFoundException("One or more seats not found");
}

Map<UUID, Seat> seatMap = seats.stream()
        .collect(Collectors.toMap(Seat::getId, Function.identity()));

// 3. Update all seats in memory
for (SeatHold hold : seatHolds) {
    Seat seat = seatMap.get(hold.getSeatId());
    seat.setSeatStatus("BOOKED");
    
    // Create booking item (avoid N queries here too)
    BookingItem item = BookingItem.builder()
            .booking(savedBooking)
            .passenger(passengerBookings.get(hold.getSeatId()))
            .seat(seat)
            .fare(seat.getSchedule().getBaseFare())
            .itemStatus("CONFIRMED")
            .build();
    items.add(item);
}

// 4. Batch save all items
bookingItemRepository.saveAll(items);

// 5. Batch update all seats
seatRepository.saveAll(seats);

// 6. Single flush
bookingRepository.flush();
```

#### 2.2 Fix Schedule Race Condition

Find schedule update section (around lines 122-126):
```java
// OLD CODE (race condition)
schedule.setAvailableSeats(schedule.getAvailableSeats() - seatHolds.size());
```

Replace with:
```java
// NEW CODE (pessimistic lock)
Schedule lockedSchedule = scheduleRepository.findByIdWithLock(schedule.getId())
        .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));

int newAvailableSeats = lockedSchedule.getAvailableSeats() - seatHolds.size();
if (newAvailableSeats < 0) {
    throw new ConflictException("Insufficient seats available");
}

lockedSchedule.setAvailableSeats(newAvailableSeats);
// Auto-saved via dirty checking at transaction commit
```

**Verify**: Run `BookingServiceTest` - all tests should pass.

---

### Step 3: Add Authorization Checks (30 minutes)

**Goal**: Prevent unauthorized access to bookings.

Open: `src/main/java/com/ticketwave/booking/application/BookingService.java`

#### 3.1 Add Authorization to cancelBooking

Find the cancelBooking method (around line 147), add after fetching booking:
```java
public void cancelBooking(UUID bookingId, UUID requestingUserId) {
    Booking booking = bookingRepository.findByIdWithItemsAndSeats(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    
    // ADD THIS: Authorization check
    if (!booking.getUser().getId().equals(requestingUserId)) {
        log.warn("Unauthorized cancellation attempt - UserId: {}, BookingOwner: {}", 
                requestingUserId, booking.getUser().getId());
        throw new AccessDeniedException("Cannot cancel booking owned by another user");
    }
    
    // Idempotency check
    if ("CANCELLED".equals(booking.getBookingStatus())) {
        log.warn("Booking already cancelled: {}", bookingId);
        return;
    }
    
    // ... rest of method
}
```

#### 3.2 Add Authorization to getBookingByPnr

Find getBookingByPnr method, add check:
```java
@Transactional(readOnly = true)
public Optional<Booking> getBookingByPnr(String pnr, UUID requestingUserId) {
    Optional<Booking> booking = bookingRepository.findByPnr(pnr);
    
    // ADD THIS: Authorization check
    booking.ifPresent(b -> {
        if (!b.getUser().getId().equals(requestingUserId)) {
            throw new AccessDeniedException("Cannot access booking for another user");
        }
    });
    
    return booking;
}
```

#### 3.3 Add Exception Class (if not exists)

Create: `src/main/java/com/ticketwave/common/exception/AccessDeniedException.java`
```java
package com.ticketwave.common.exception;

public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
```

Map in `@RestControllerAdvice`:
```java
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse.builder()
                    .status(HttpStatus.FORBIDDEN.value())
                    .message(ex.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build());
}
```

---

### Step 4: Fix Secure PNR Generation (15 minutes)

**Goal**: Replace predictable UUID with secure random.

Open: `src/main/java/com/ticketwave/booking/application/BookingService.java`

Find the PNR generation (around line 182):
```java
// OLD CODE (predictable)
String pnr = "TW" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
```

Replace with:
```java
// NEW CODE (cryptographically secure)
private String generateSecurePNR() {
    SecureRandom random = new SecureRandom();
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    
    StringBuilder pnr = new StringBuilder("TW");
    for (int i = 0; i < 8; i++) {
        pnr.append(chars.charAt(random.nextInt(chars.length())));
    }
    
    String generated = pnr.toString();
    
    // Collision check (extremely rare with 36^8 = 2.8 trillion combinations)
    int maxRetries = 5;
    int attempts = 0;
    
    while (bookingRepository.findByPnr(generated).isPresent() && attempts < maxRetries) {
        pnr = new StringBuilder("TW");
        for (int i = 0; i < 8; i++) {
            pnr.append(chars.charAt(random.nextInt(chars.length())));
        }
        generated = pnr.toString();
        attempts++;
    }
    
    if (attempts == maxRetries) {
        throw new IllegalStateException("Failed to generate unique PNR");
    }
    
    return generated;
}
```

Add import:
```java
import java.security.SecureRandom;
```

---

### Step 5: Fix SeatHoldService Race Condition (1 hour)

**Goal**: Use Lua script for atomic TTL extension.

Open: `src/main/java/com/ticketwave/booking/application/SeatHoldService.java`

#### 5.1 Add Lua Script Constant

Add at class level:
```java
private static final String LUA_EXTEND_HOLD = """
    local key = KEYS[1]
    local expectedToken = ARGV[1]
    local extensionSeconds = tonumber(ARGV[2])
    
    if redis.call('EXISTS', key) == 1 then
        local currentToken = redis.call('GET', key)
        if currentToken == expectedToken then
            redis.call('EXPIRE', key, extensionSeconds)
            return 1
        else
            return 0
        end
    else
        return 0
    end
    """;
```

#### 5.2 Replace extendSeatHold Method

Find extendSeatHold (around line 188), replace with:
```java
public boolean extendSeatHold(UUID seatId, UUID userId, String holdToken, int extensionSeconds) {
    if (seatId == null || userId == null || holdToken == null) {
        throw new IllegalArgumentException("Parameters cannot be null");
    }
    if (extensionSeconds <= 0) {
        throw new IllegalArgumentException("Extension seconds must be positive");
    }

    String holdKey = SEAT_HOLD_KEY_PREFIX + seatId;
    String expectedValue = userId + ":" + holdToken;
    
    // Execute Lua script atomically
    RedisScript<Long> script = RedisScript.of(LUA_EXTEND_HOLD, Long.class);
    Long result = redisTemplate.execute(
            script, 
            Collections.singletonList(holdKey), 
            expectedValue, 
            String.valueOf(extensionSeconds)
    );
    
    boolean extended = result != null && result == 1L;
    
    if (extended) {
        log.info("Seat hold extended: seatId={}, extensionSeconds={}", seatId, extensionSeconds);
    } else {
        log.warn("Failed to extend seat hold: seatId={}", seatId);
    }
    
    return extended;
}
```

Add imports:
```java
import org.springframework.data.redis.core.script.RedisScript;
import java.util.Collections;
```

---

### Step 6: Add Null Validation (30 minutes)

**Goal**: Validate all public method parameters.

For each public method in services, add validation at the start:

#### Example - BookingService.confirmBooking
```java
public Booking confirmBooking(User user, Schedule schedule, List<SeatHold> seatHolds, ...) {
    // Add at the very start
    if (user == null) throw new IllegalArgumentException("User cannot be null");
    if (schedule == null) throw new IllegalArgumentException("Schedule cannot be null");
    if (seatHolds == null || seatHolds.isEmpty()) {
        throw new IllegalArgumentException("Seat holds cannot be null or empty");
    }
    // ... rest of method
}
```

Apply same pattern to:
- `SeatHoldService.holdSeat()`
- `PaymentService.createPayment()`
- `RefundService.initiateRefund()`

**Shortcut**: Use Jakarta Bean Validation:
```java
public Booking confirmBooking(@NotNull User user, 
                               @NotNull Schedule schedule,
                               @NotEmpty List<SeatHold> seatHolds) {
    // Validation happens automatically
}
```

---

### Step 7: Update Configuration (15 minutes)

**Goal**: Enable JPA batching and connection pool monitoring.

Open: `src/main/resources/application.yml`

Add these properties:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
          fetch_size: 50
        order_inserts: true
        order_updates: true
        
  datasource:
    hikari:
      leak-detection-threshold: 60000  # 60 seconds
      maximum-pool-size: 20
```

---

### Step 8: Run Tests (30 minutes)

**Goal**: Verify all changes work correctly.

```bash
# Compile
mvn clean compile

# Run unit tests
mvn test

# Run integration tests
mvn verify

# Check test coverage
mvn jacoco:report
# Open: target/site/jacoco/index.html
```

**Expected Results**:
- ✅ All existing tests pass
- ✅ Test coverage ≥ 80%
- ✅ 0 compilation errors
- ✅ Database queries reduced (check logs)

---

### Step 9: Manual Verification (30 minutes)

**Goal**: Test critical flows manually.

#### 9.1 Start Application
```bash
mvn spring-boot:run
```

#### 9.2 Test Booking Flow (Use Postman/curl)

**Create Booking**:
```bash
curl -X POST http://localhost:8080/api/v1/bookings \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "schedule_id": "uuid-here",
    "seats": ["seat-uuid-1", "seat-uuid-2"],
    "passengers": [...]
  }'
```

**Check Logs** - Should see:
```
Seat hold created: seatId=..., userId=..., ttl=600s
Confirming booking for user: ... with 2 seats
Booking confirmed: pnr=TWAB12CD34, totalSeats=2
```

**Verify Query Count** - Enable SQL logging temporarily:
```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
```

Should see **3 queries** for 2 seats (not 6+).

#### 9.3 Test Authorization

**Try Canceling Another User's Booking** (should fail with 403):
```bash
curl -X DELETE http://localhost:8080/api/v1/bookings/{other-user-booking-id} \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

Expected response:
```json
{
  "status": 403,
  "message": "Cannot cancel booking owned by another user",
  "timestamp": "2024-01-..."
}
```

---

## Verification Checklist

After completing Steps 1-9:

- [ ] **Compilation**: `mvn clean compile` succeeds
- [ ] **Tests**: `mvn test` - all pass
- [ ] **Coverage**: `mvn jacoco:report` shows ≥ 80%
- [ ] **Query Count**: Booking confirmation uses ≤ 5 queries (was 15+)
- [ ] **Authorization**: Cannot cancel other user's booking (403 error)
- [ ] **PNR Security**: PNRs are now random (not sequential)
- [ ] **Concurrency**: Schedule updates don't lose data under load
- [ ] **Redis**: Seat hold extension doesn't fail due to race conditions

---

## Performance Testing (Optional)

### Load Test Booking Endpoint

Use Apache JMeter or k6:

**k6 script** (`load-test.js`):
```javascript
import http from 'k6/http';
import { check } from 'k6';

export let options = {
  vus: 50,  // 50 virtual users
  duration: '30s',
};

export default function () {
  let payload = JSON.stringify({
    schedule_id: '__SCHEDULE_UUID__',
    seats: ['__SEAT_UUID_1__', '__SEAT_UUID_2__'],
    passengers: [...]
  });
  
  let res = http.post('http://localhost:8080/api/v1/bookings', payload, {
    headers: { 
      'Content-Type': 'application/json',
      'Authorization': 'Bearer __JWT_TOKEN__'
    },
  });
  
  check(res, {
    'status is 201': (r) => r.status === 201,
    'response time < 200ms': (r) => r.timings.duration < 200,
  });
}
```

Run: `k6 run load-test.js`

**Target Metrics**:
- P99 latency < 200ms
- No database connection pool exhaustion
- No Redis connection errors
- 0% error rate

---

## Rollback Plan (If Needed)

If issues arise:

```bash
# Revert to backup branch
git checkout backup/before-review-fixes

# Or revert specific commit
git log --oneline  # Find commit hash
git revert <commit-hash>
```

**Before Production Deployment**:
1. Test in staging environment for 24-48 hours
2. Monitor for any anomalies
3. Have rollback script ready
4. Deploy during low-traffic window

---

## Next Steps After Implementation

1. **Create Pull Request**:
   - Title: "Fix Critical Code Review Issues - N+1, Race Conditions, Security"
   - Description: Link to CODE_REVIEW_REPORT.md
   - Include before/after performance metrics

2. **Code Review by Team**:
   - Focus on BookingService changes
   - Review new repository methods
   - Verify authorization logic

3. **Deploy to Staging**:
   - Run full regression suite
   - Load test for 1 hour
   - Check monitoring dashboards

4. **Production Deployment**:
   - Deploy during maintenance window
   - Monitor error rates
   - Verify query count improvements
   - Check connection pool metrics

5. **Post-Deployment**:
   - Update team documentation
   - Share performance improvements in team meeting
   - Plan Phase 2 fixes (DTO layer, enums, etc.)

---

## Support & Troubleshooting

### Common Issues

**Issue**: "Method findAllByIdWithLock not found"
**Fix**: Make sure you added the method to the repository interface, then run `mvn clean compile`.

**Issue**: "Cannot resolve symbol LockModeType"
**Fix**: Add import: `import jakarta.persistence.LockModeType;`

**Issue**: "Lua script returns nil"
**Fix**: Check Redis version (must be ≥ 2.6). Verify key exists before extending.

**Issue**: "Tests fail after refactoring"
**Fix**: Update mocks in tests to match new method signatures. Check test data setup.

---

## Estimated Time Breakdown

| Step | Task | Time |
|------|------|------|
| 1 | Add repository methods | 30 min |
| 2 | Refactor BookingService | 1 hour |
| 3 | Add authorization | 30 min |
| 4 | Fix PNR generation | 15 min |
| 5 | Fix SeatHoldService | 1 hour |
| 6 | Add null validation | 30 min |
| 7 | Update configuration | 15 min |
| 8 | Run tests | 30 min |
| 9 | Manual verification | 30 min |
| **TOTAL** | | **5 hours** |

**With breaks and troubleshooting**: Allow 6-8 hours.

---

## Success Criteria

Implementation is complete when:
- ✅ All unit tests pass
- ✅ Integration tests pass
- ✅ Query count reduced by 80%+
- ✅ Authorization prevents unauthorized access
- ✅ PNRs are cryptographically random
- ✅ Race conditions eliminated
- ✅ Null pointer exceptions prevented

**Ready for Production**: After staging validation + load testing.

---

**Good luck with the implementation! 🚀**

For questions, refer to:
- `CODE_REVIEW_REPORT.md` for detailed issue context
- `IMPLEMENTATION_SUMMARY.md` for high-level overview
- Refactored `.java` files for complete working examples
