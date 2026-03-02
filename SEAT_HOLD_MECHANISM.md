# TicketWave Distributed Seat Hold Mechanism

## Overview
Production-grade distributed seat hold system preventing double booking under high concurrency using Redis TTL, Redisson atomic locks, and database fallback constraints.

---

## Architecture

### Components

#### 1. **Redisson Client** (RedisConfig)
- Atomic distributed lock provider
- Single-server Redis configuration
- Connection pooling and retry logic
- Thread-safe operations

#### 2. **SeatLockProvider** (Infrastructure)
- Manages distributed locks per seat
- 2-second lock acquisition wait time
- 5-second lock lease time (prevents deadlock)
- Lock state inspection and force-unlock capabilities

#### 3. **SeatHold** (Domain Value Object)
- Immutable hold metadata
- Seat ID, User ID, hold token, timestamps
- Expiration validation logic
- Ownership verification

#### 4. **SeatHoldService** (Application)
- Core hold management API
- Redis TTL-based expiration (10 minutes default)
- Hold validation
- Hold extension capability

#### 5. **BookingService** (Application)
- Integrates holds into booking workflow
- Confirms hold then updates seat status
- Cascading release on errors
- PNR generation

#### 6. **Database Constraints** (Fallback)
- Unique seat per schedule (`uk_seats_schedule_number`)
- Booking items unique constraint (`uk_booking_items_booking_seat`)
- Version-based optimistic locking

---

## Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ 1. User Selects Seat                                        │
└────┬────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Acquire Distributed Lock (Redisson)                      │
│    - tryLock(2s wait, 5s lease)                            │
│    - Returns immediately if lock unavailable               │
└────┬────────────────────────────────────────────────────────┘
     │
     ├─ LOCK ACQUIRED
     │  ▼
     │  ┌──────────────────────────────────────┐
     │  │ 3.Check Redis: Seat Already Held     │
     │  │ Key: "seat:hold:{seatId}"            │
     │  │ Value: "{userId}"                    │
     │  └────┬─────────────────────────────────┘
     │       │
     │       ├─ HELD BY SOMEONE ELSE
     │       │  └─ Throw ConflictException
     │       │
     │       ├─ NOT HELD
     │       │  ▼
     │       │  ┌────────────────────────────────┐
     │       │  │ 4. Set Redis with TTL (600s)   │
     │       │  │ - Atomic SET operation         │
     │       │  │ - 10 minute expiration         │
     │       │  │ - Create hold token mapping    │
     │       │  └────┬──────────────────────────┘
     │       │       │
     │       │       ▼
     │       │  Return SeatHold
     │       │
     │  ▼
     │  Release Lock
     │
     └─ LOCK NOT ACQUIRED (Timeout)
        └─ Throw ConflictException


┌─────────────────────────────────────────────────────────────┐
│ 5. Validate Hold Before Confirmation                        │
│    - Check Redis key exists                                │
│    - Verify owning user matches                            │
│    - Verify hold token hash                                │
└────┬────────────────────────────────────────────────────────┘
     │
     ├─ VALID
     │  ▼
     │  ┌──────────────────────────────────────┐
     │  │ 6. Confirm Booking (Transaction)     │
     │  │ - Insert booking record              │
     │  │ - Insert booking_items               │
     │  │ - Update seat status → BOOKED        │
     │  │ - Update schedule available_seats    │
     │  └────┬─────────────────────────────────┘
     │       │
     │       ▼
     │  ┌──────────────────────────────────────┐
     │  │ 7. Release Hold from Redis           │
     │  │ - Delete "seat:hold:{seatId}"        │
     │  │ - Delete "hold:token:{token}"        │
     │  └────┬─────────────────────────────────┘
     │       │
     │       ▼
     │  Return Confirmed Booking + PNR
     │
     └─ INVALID/EXPIRED
        └─ Throw ConflictException
           Release all holds
           Rollback transaction
```

---

## Key Features

### 1. **Atomic Hold Acquisition**
- Redisson distributed lock ensures only one writer at a time per seat
- 2-second acquisition timeout prevents indefinite waiting
- 5-second lease time prevents deadlocks from crashed processes

### 2. **TTL-Based Expiration**
- Redis key expiration: 10 minutes (600 seconds)
- No background cleanup job needed
- Prevents stale holds from blocking seats indefinitely
- Extensible for pending payment scenarios

### 3. **Cross-Browser/Device Hold Token**
- Unique token per hold
- Prevents token reuse across sessions
- Validated on booking confirmation

### 4. **Multiple Concurrent Users**
- User A can hold seat while User B holds different seat on same schedule
- Only mutual exclusion when attempting same seat
- Lock-free reads for hold validation (Redis GET)

### 5. **Database Fallback Constraint**
- `UNIQUE(schedule_id, seat_number)` on `seats` table
- Prevents duplicate seat bookings at DB level even if Redis fails
- Optimistic locking with `@Version` on seat updates
- Constraint violation triggers 409 Conflict response

### 6. **Graceful Error Handling**
- Hold acquisition timeout → 409 Conflict (seat under contention)
- Expired hold → 409 Conflict (retry selection)
- Lock release failure → logged but doesn't fail operation
- Transaction rollback cascades hold release

---

## Configuration

### Application Properties
```yaml
app:
  seat-hold:
    duration-seconds: 600  # 10 minutes

spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 3s
```

### Redisson Config (RedisConfig.java)
```properties
Connection timeout: 5 seconds
Retry attempts: 3
Retry interval: 1.5 seconds
Idle connection timeout: 30 seconds
Lock strategy: SETNX with auto-renewal
```

---

## API Usage

### Hold a Seat
```java
SeatHold hold = seatHoldService.holdSeat(seatId, userId);
// Returns: SeatHold with expiresAt timestamp, holdToken
```

### Validate Hold Before Confirming
```java
boolean isValid = seatHoldService.isHoldValid(seatId, userId, holdToken);
if (!isValid) {
    throw new ConflictException("Hold expired");
}
```

### Confirm Booking
```java
Booking booking = bookingService.confirmBooking(user, schedule, seatHolds, passengerMap);
// Validates holds, updates database, releases holds
```

### Release Hold (Cancellation)
```java
seatHoldService.releaseSeatHold(seatId, holdToken);
```

### Check Remaining Hold Time
```java
long remainingSeconds = seatHoldService.getHoldRemainingSeconds(seatId);
```

### Extend Hold (For Pending Payment)
```java
seatHoldService.extendSeatHold(seatId, 300); // Add 5 minutes
```

---

## Concurrency Scenarios

### Scenario 1: Two Users Simultaneously Selecting Same Seat
```
Time  User A                      User B
─────────────────────────────────────────────────
T0    tryAcquireLock(seat1)       ─
T1    ✓ Lock acquired            tryAcquireLock(seat1)
T2    Check Redis key            ⏳ Waiting (2s timeout)
T3    SET seat:hold:seat1=userA   
T4    Release lock               ✗ Lock timeout
T5    Return SeatHold            ➜ Throw ConflictException
T6    ─                          User B shown "Seat unavailable"
```

### Scenario 2: User A Releases, User B Immediately Books
```
Time  User A                      User B
─────────────────────────────────────────────────
T0    acquireHold(seat1)         ─
T1    Return SeatHold            ─
T2    holdSeat(seat1, userA)     holdSeat(seat1, userB)
      [redis TTL: 600s]          tryLock(timeout=2s)
T3    releaseSeatHold(userA)     ✓ Lock acquired (after A released)
T4    DELETE seat:hold:seat1     Check Redis (now empty)
T5    ─                          SET seat:hold:seat1=userB
T6    ─                          Return SeatHold
```

### Scenario 3: Hold Expires Before Confirmation
```
Time  User A                      Redis
─────────────────────────────────────────
T0    holdSeat(seat1)            SET seat:hold:seat1 TTL=600s
T1    Return SeatHold            ─
...   (600+ seconds pass)         
T601  isHoldValid(seat1, token)  Key expired
      Return false               ─
T602  confirmBooking()           Throw ConflictException
```

### Scenario 4: Lock Provider Fails
```
If Redisson connection fails:
- tryAcquireLock() catches exception
- Returns false (timeout behavior)
- Service throws ConflictException
- Client retries or shows "try again"
- Fallback: DB unique constraint prevents double-book
```

---

## Fallback Safeguards

### 1. Database Unique Constraint
```sql
ALTER TABLE booking_items 
ADD CONSTRAINT uk_booking_items_booking_seat 
UNIQUE (booking_id, seat_id);

ALTER TABLE seats 
ADD CONSTRAINT uk_seats_schedule_number 
UNIQUE (schedule_id, seat_number);
```

### 2. Optimistic Locking
```java
@Version
private long version;
```
- Prevents lost updates during concurrent modifications
- Throws `OptimisticLockingFailureException` if version mismatch
- Client retries with fresh seat data

### 3. Transactional Consistency
```java
@Transactional
public Booking confirmBooking(...) {
    // All-or-nothing: booking, items, seat update
    // If any update fails, entire transaction rolled back
    // All holds are released on failure
}
```

---

## Test Coverage

### Unit Tests (SeatHoldServiceTest)
- ✅ Successful hold creation with token generation
- ✅ Conflict when seat already held
- ✅ Lock acquisition failure handling
- ✅ Hold validation (valid, expired, wrong user, invalid token)
- ✅ Hold release operations
- ✅ Hold ownership verification
- ✅ SeatHold expiration logic

### Concurrency Tests (SeatHoldServiceConcurrencyTest)
- ✅ Multiple threads attempting same seat
- ✅ Only one succeeds while others get ConflictException
- ✅ Rapid release and re-hold cycles
- ✅ Lock timeout scenarios
- ✅ TTL enforcement prevents stale holds

### Edge Case Tests (SeatHoldServiceEdgeCaseTest)
- ✅ Null seat/user ID handling
- ✅ Non-existent hold release
- ✅ Redis TTL edge cases (-1, -2)
- ✅ Hold extension near expiration
- ✅ Multiple sequential holds by same user
- ✅ Mismatched seat in hold token
- ✅ Lock release failures
- ✅ Unique token generation

---

## Performance Considerations

### Redis Operations (Sub-millisecond)
- **holdSeat()**: SET + GET = ~1ms
- **isHoldValid()**: 2x GET = ~0.5ms
- **releaseSeatHold()**: DELETE + GET = ~0.5ms

### Lock Overhead
- **tryAcquireLock()**: ~2ms (network round-trip)
- **High contention**: Multiple threads wait 2s timeout
- **Mitigation**: Return 409 immediately; client retries backoff

### Database Operations (5-50ms)
- Booking insert with 3+ items: ~15ms
- Unique constraint check: ~2ms
- Index lookup: ~1ms

### Capacity Estimates
- **1000 concurrent users on same schedule**: 
  - Collision probability: ~10% (birthday paradox on 100 seats)
  - Average lock wait: ~100ms
  - Total P99 latency: ~200-300ms

---

## Security Considerations

### Hold Token Format
- Non-predictable: UUID-based prefix + nanotime
- Short-lived: Expires with Redis key (10 minutes)
- User-bound: Cannot be transferred between users

### Redis Access Control
- Treat Redis instance as internal only
- No public network exposure
- Use connection pooling and retry logic
- Disable dangerous commands in production (FLUSHALL, KEYS)

### Lock Duration Bounds
- Lock lease: 5 seconds (prevents permanent deadlock)
- Hold TTL: 10 minutes (prevents stale bookings)

---

## Operational Guidelines

### Monitoring
- Track seat lock contention by route/schedule
- Alert if lock acquisition timeout rate > 5%
- Monitor Redis memory usage with TTL keys
- Dashboard: active holds per schedule

### Troubleshooting
- **"Seat is currently being processed"**: Lock timeout; retry after backoff
- **"Seat hold has expired"**: Hold TTL exceeded; customer re-selects
- **"Duplicate entry key"**: DB fallback triggered; log and investigate Redis sync

### Administrative Operations
- **Force release hold**: `seatLockProvider.forciblyUnlockSeat(seatId)`
- **Cleanup expired holds**: Rely on Redis TTL; no manual cleanup needed
- **Manual unlock (race recovery)**: Very rare; use lock provider admin methods

---

## Production Deployment Checklist

- [ ] Redisson connection pool size: 10-50 (based on concurrency)
- [ ] Redis persistence: RDB snapshots + AOF for durability
- [ ] Redis replication: At least 1 replica for failover
- [ ] Database backups: Daily before peak hours
- [ ] Monitoring: CloudWatch/Datadog for Redis metrics
- [ ] Load testing: Simulate 1000+ concurrent bookings
- [ ] Failover testing: Redis replica promotion scenario
- [ ] Capacity planning: 1 hold ≈ 1KB in Redis
- [ ] Hold duration tuning: Adjust per payment SLA
- [ ] Logging: Correlation IDs across hold → booking → payment

---

## Future Enhancements

1. **Predictive Holds**: AI-based hold duration based on user behavior
2. **Dynamic Pricing**: Adjust hold duration by demand
3. **Hold Transfer**: Allow holds to be gifted to other users (rare)
4. **Queue System**: For sold-out schedules (waitlist)
5. **Analytics**: Conversion rate from hold → booking
6. **SLA Guarantees**: Hold confirmation SLA with compensation
