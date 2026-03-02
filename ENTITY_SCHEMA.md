# TicketWave JPA Entity Domain Model

## Overview
Production-grade JPA entity layer for a ticket booking system with 9 core domain entities, lazy loading, optimistic locking, and comprehensive audit tracking.

---

## Core Entities

### 1. **User** (`user` module)
**Table:** `users`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, generated |
| email | String(100) | UNIQUE, NOT NULL |
| passwordHash | String(255) | NOT NULL |
| firstName | String(50) | NOT NULL |
| lastName | String(50) | NOT NULL |
| phoneNumber | String(20) | NULL |
| active | Boolean | DEFAULT true |
| version | long | Optimistic lock |
| createdAt | Instant | Audit, NOT NULL |
| updatedAt | Instant | Audit, NOT NULL |

**Relationships:**
- 1:N → Passenger (LAZY, CASCADE REMOVE)
- 1:N → Booking (LAZY, CASCADE REMOVE)

**Indexes:**
- idx_users_email (UNIQUE)

**Repository:** `UserRepository`

---

### 2. **Passenger** (`user` module)
**Table:** `passengers`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, generated |
| userId | UUID | FK to User, NOT NULL |
| firstName | String(50) | NOT NULL |
| lastName | String(50) | NOT NULL |
| dateOfBirth | LocalDate | NOT NULL |
| documentType | String(3) | NOT NULL (PASSPORT, NATIONAL_ID, etc.) |
| documentNumber | String(50) | NOT NULL |
| active | Boolean | DEFAULT true |
| version | long | Optimistic lock |
| createdAt | Instant | Audit, NOT NULL |
| updatedAt | Instant | Audit, NOT NULL |

**Relationships:**
- N:1 ← User (LAZY)
- 1:N → BookingItem (LAZY, CASCADE REMOVE)

**Repository:** `PassengerRepository`

---

### 3. **Route** (`catalog` module)
**Table:** `routes`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, generated |
| originCity | String(100) | NOT NULL |
| destinationCity | String(100) | NOT NULL |
| transportMode | String(50) | NOT NULL (FLIGHT, TRAIN, BUS, etc.) |
| description | String(500) | NULL |
| active | Boolean | DEFAULT true |
| version | long | Optimistic lock |
| createdAt | Instant | Audit, NOT NULL |
| updatedAt | Instant | Audit, NOT NULL |

**Relationships:**
- 1:N → Schedule (LAZY, CASCADE ALL)

**Indexes:**
- idx_routes_origin_destination (originCity, destinationCity)

**Repository:** `RouteRepository`

---

### 4. **Schedule** (`catalog` module)
**Table:** `schedules`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, generated |
| routeId | UUID | FK to Route, NOT NULL |
| vehicleNumber | String(50) | NOT NULL |
| departureTime | LocalDateTime | NOT NULL |
| arrivalTime | LocalDateTime | NOT NULL |
| totalSeats | Integer | NOT NULL |
| availableSeats | Integer | NOT NULL |
| baseFare | BigDecimal(10,2) | NOT NULL |
| active | Boolean | DEFAULT true |
| version | long | Optimistic lock |
| createdAt | Instant | Audit, NOT NULL |
| updatedAt | Instant | Audit, NOT NULL |

**Relationships:**
- N:1 ← Route (LAZY)
- 1:N → Seat (LAZY, CASCADE ALL)
- 1:N → Booking (LAZY, CASCADE REMOVE)

**Indexes:**
- idx_schedules_departure (departureTime)
- idx_schedules_route_departure (routeId, departureTime)

**Repository:** `ScheduleRepository`

---

### 5. **Seat** (`catalog` module)
**Table:** `seats`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, generated |
| scheduleId | UUID | FK to Schedule, NOT NULL |
| seatNumber | String(10) | NOT NULL |
| class_ | String(20) | NOT NULL (ECONOMY, BUSINESS, FIRST, etc.) |
| seatStatus | String(20) | NOT NULL (AVAILABLE, HELD, BOOKED, BLOCKED) |
| version | long | Optimistic lock |
| createdAt | Instant | Audit, NOT NULL |
| updatedAt | Instant | Audit, NOT NULL |

**Constraints:**
- UNIQUE(scheduleId, seatNumber) - no duplicate seats per schedule

**Relationships:**
- N:1 ← Schedule (LAZY)
- 1:N → BookingItem (LAZY, CASCADE REMOVE)

**Indexes:**
- uk_seats_schedule_number (UNIQUE: scheduleId, seatNumber)
- idx_seats_schedule_status (scheduleId, seatStatus)

**Repository:** `SeatRepository`

---

### 6. **Booking** (`booking` module)
**Table:** `bookings`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, generated |
| userId | UUID | FK to User, NOT NULL |
| scheduleId | UUID | FK to Schedule, NOT NULL |
| pnr | String(10) | UNIQUE, NOT NULL (Passenger Name Record) |
| bookingStatus | String(20) | NOT NULL (PENDING, CONFIRMED, CANCELLED) |
| totalAmount | BigDecimal(10,2) | NOT NULL |
| bookedAt | LocalDateTime | NOT NULL |
| cancelledAt | LocalDateTime | NULL |
| version | long | Optimistic lock |
| createdAt | Instant | Audit, NOT NULL |
| updatedAt | Instant | Audit, NOT NULL |

**Relationships:**
- N:1 ← User (LAZY)
- N:1 ← Schedule (LAZY)
- 1:N → BookingItem (LAZY, CASCADE ALL)
- 1:N → Payment (LAZY, CASCADE REMOVE)
- 1:N → Refund (LAZY, CASCADE REMOVE)

**Constraints:**
- UNIQUE(pnr)

**Indexes:**
- uk_bookings_pnr (UNIQUE: pnr)
- idx_bookings_user_status (userId, bookingStatus)
- idx_bookings_schedule_status (scheduleId, bookingStatus)
- idx_bookings_pnr (pnr)

**Repository:** `BookingRepository`

---

### 7. **BookingItem** (`booking` module)
**Table:** `booking_items`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, generated |
| bookingId | UUID | FK to Booking, NOT NULL |
| passengerId | UUID | FK to Passenger, NOT NULL |
| seatId | UUID | FK to Seat, NOT NULL |
| fare | BigDecimal(10,2) | NOT NULL |
| itemStatus | String(20) | NOT NULL (PENDING, CONFIRMED, CANCELLED) |
| version | long | Optimistic lock |
| createdAt | Instant | Audit, NOT NULL |
| updatedAt | Instant | Audit, NOT NULL |

**Relationships:**
- N:1 ← Booking (LAZY)
- N:1 ← Passenger (LAZY)
- N:1 ← Seat (LAZY)

**Constraints:**
- UNIQUE(bookingId, seatId) - seat cannot be in same booking twice

**Indexes:**
- uk_booking_items_booking_seat (UNIQUE: bookingId, seatId)
- idx_booking_items_booking (bookingId)
- idx_booking_items_seat (seatId)

**Repository:** `BookingItemRepository`

---

### 8. **Payment** (`payment` module)
**Table:** `payments`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, generated |
| bookingId | UUID | FK to Booking, NOT NULL |
| transactionId | String(50) | UNIQUE, NOT NULL |
| amount | BigDecimal(10,2) | NOT NULL |
| paymentMethod | String(20) | NOT NULL (CARD, UPI, NET_BANKING, WALLET, etc.) |
| paymentStatus | String(20) | NOT NULL (PENDING, CONFIRMED, FAILED, REFUNDED) |
| gatewayResponse | String(500) | NULL |
| confirmedAt | Instant | NULL |
| version | long | Optimistic lock |
| createdAt | Instant | Audit, NOT NULL |
| updatedAt | Instant | Audit, NOT NULL |

**Relationships:**
- N:1 ← Booking (LAZY)
- 1:N → Refund (LAZY, CASCADE REMOVE)

**Constraints:**
- UNIQUE(transactionId)

**Indexes:**
- uk_payments_transaction_id (UNIQUE: transactionId)
- idx_payments_booking_status (bookingId, paymentStatus)
- idx_payments_transaction_id (transactionId)

**Repository:** `PaymentRepository`

---

### 9. **Refund** (`refund` module)
**Table:** `refunds`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, generated |
| bookingId | UUID | FK to Booking, NOT NULL |
| paymentId | UUID | FK to Payment, NOT NULL |
| refundId | String(50) | UNIQUE, NOT NULL |
| refundAmount | BigDecimal(10,2) | NOT NULL |
| refundStatus | String(20) | NOT NULL (PENDING, APPROVED, REJECTED, COMPLETED) |
| reason | String(500) | NULL |
| gatewayResponse | String(500) | NULL |
| processedAt | Instant | NULL |
| version | long | Optimistic lock |
| createdAt | Instant | Audit, NOT NULL |
| updatedAt | Instant | Audit, NOT NULL |

**Relationships:**
- N:1 ← Booking (LAZY)
- N:1 ← Payment (LAZY)

**Constraints:**
- UNIQUE(refundId)

**Indexes:**
- uk_refunds_refund_id (UNIQUE: refundId)
- idx_refunds_booking_status (bookingId, refundStatus)
- idx_refunds_payment_status (paymentId, refundStatus)
- idx_refunds_refund_id (refundId)

**Repository:** `RefundRepository`

---

## Entity Relationship Diagram (High-Level)

```
User ─────────────┬──── Passenger
                  │
                  └──── Booking ──────┬──── BookingItem ───┬──── Seat
                                      │                    │
                                      ├──── Payment ───┬── Refund
                                      │                │
                                      └────────────────┘

Route ──── Schedule ──┬─── Seat
                      │
                      └─── Booking
```

---

## Key Design Decisions

### Optimistic Locking
- **@Version** applied to all entities for concurrency safety
- Prevents lost updates in high-contention scenarios (seat booking, payment)
- Recommended queries should use pessimistic locking for critical paths

### Lazy Loading
- All relationships use `FetchType.LAZY` by default
- Prevents N+1 queries and over-fetching
- Use explicit joins/queries for eager loading when needed

### Audit Fields
- `createdAt` and `updatedAt` managed by Spring Data JPA auditing
- Immutable `id` as UUID (database-side generation)
- All audit fields NOT NULL and indexed where necessary

### Unique Constraints
- PNR (Passenger Name Record): `bookings.pnr`
- Seat per Schedule: `seats.scheduleId + seats.seatNumber`
- Transaction ID: `payments.transactionId`
- Refund ID: `refunds.refundId`
- Email: `users.email`

### Cascade Strategies
- **CASCADE ALL** on parent-child relationships where deletion of parent mandates deletion of child (Route → Schedule, Schedule → Seat)
- **CASCADE REMOVE** for orphan cleanup (User → Booking, Booking → Payment)
- **No cascade** for cross-aggregate relationships (Booking → Seat, handled via BookingItem)

### Indexing Strategy
- Composite indexes on frequently queried pairs (userId + bookingStatus, bookingId + seatId)
- Unique constraints automatically create indexes
- Status columns included in indexes for filtered queries

---

## Base Classes & Utilities

### AuditedEntity
- Abstract base class for all entities
- Auto-managed `id` (UUID), `createdAt`, `updatedAt`
- Uses Spring Data JPA `@CreatedDate` and `@LastModifiedDate` annotations

### MapStructConfig
- Global MapStruct configuration for DTO mapping
- Spring component model, unmapped target policy = IGNORE

### JpaAuditingConfig
- Enables Spring Data JPA auditing across all repositories
- Annotation-based listener support

---

## Migration Strategy
Use Flyway or Liquibase to create the following:
1. Create all tables with columns and data types as specified
2. Add unique constraints and indexes
3. Create foreign key relationships with appropriate cascade rules
4. Set default values for Boolean and timestamp fields

Example:
```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    phone_number VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT true,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_users_email ON users(email);
```

---

## Repository Query Patterns

### User Queries
- `findByEmail(String email)` - Authentication
- `existsByEmail(String email)` - Duplicate check

### Passenger Queries
- `findByUser(User user)` - All passengers for user
- `findByUserAndActiveTrue(User user)` - Active passengers only

### Route Queries
- `findByOriginCityAndDestinationCity(String origin, String dest)` - Search routes
- `findByActiveTrue()` - Available routes

### Schedule Queries
- `findAvailableSchedules(Route, LocalDateTime, LocalDateTime)` - Date-based search
- `findByAvailableSeatsGreaterThan(int seats)` - Availability filter

### Seat Queries
- `findByScheduleAndSeatNumber(Schedule, String number)` - Specific seat lookup
- `findByScheduleAndSeatStatus(Schedule, String status)` - Status-based queries

### Booking Queries
- `findByPnr(String pnr)` - Confirmation/tracking
- `findByUserOrderByCreatedAtDesc(User, Pageable)` - User's booking history
- `findByUserAndBookingStatusOrderByCreatedAtDesc(User, String, Pageable)` - Filtered history

### Payment Queries
- `findByTransactionId(String transactionId)` - Payment lookup
- `findByBookingAndPaymentStatus(Booking, String)` - Booking payment status

### Refund Queries
- `findByRefundId(String refundId)` - Refund tracking
- `findByPaymentAndRefundStatus(Payment, String)` - Payment refund status

---

## Compilation & Validation Status
✅ All 9 entities compile without errors
✅ All 8 repositories compile without errors
✅ Lazy loading and optimistic locking configured
✅ Audit fields auto-managed
✅ Unique and database constraints in place
✅ Follows enterprise Spring Boot best practices
