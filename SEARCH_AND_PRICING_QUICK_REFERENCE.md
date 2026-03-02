# TicketWave Search & Pricing — Quick Reference

## API Endpoints (5 Total)

```
POST   /api/v1/schedules/search                    → Search schedules by criteria
GET    /api/v1/schedules/{scheduleId}              → Get schedule details
GET    /api/v1/schedules/{scheduleId}/availability → Get seat statistics
GET    /api/v1/schedules/high-demand               → Get premium-pricing schedules
GET    /api/v1/schedules/{scheduleId}/duration     → Get travel time in minutes
```

---

## 1. Search Schedules

**POST** `/api/v1/schedules/search`

**Request Body:**
```json
{
  "originCity": "New York",
  "destinationCity": "Boston",
  "travelDate": "2024-01-20",
  "sortBy": "price",
  "sortOrder": "asc"
}
```

**Sort Options:**
| sortBy | Behavior |
|--------|----------|
| `price` | Sort by dynamicPrice |
| `duration` | Sort by travel time (minutes) |
| `availability` | Sort by available seats |
| `departure` | Sort by departure time |

**Sort Order:**
- `asc`: Ascending (cheapest first, shortest duration, most available)
- `desc`: Descending (most expensive, longest duration, fewest available)

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "scheduleId": "uuid",
      "originCity": "New York",
      "destinationCity": "Boston",
      "departureTime": "2024-01-20 10:00:00",
      "arrivalTime": "2024-01-20 12:30:00",
      "totalSeats": 100,
      "availableSeats": 45,
      "availabilityPercentage": 45.0,
      "durationMinutes": 150,
      "baseFare": 1000.00,
      "dynamicPrice": 1000.00,
      "priceModifier": 1.0,
      "demandFactor": 1.0,
      "active": true
    }
  ]
}
```

**Status Codes:**
- `200 OK` — Success (may be empty list)
- `400 BAD REQUEST` — Invalid/missing required fields
- `422 UNPROCESSABLE ENTITY` — Validation failed (date in past, etc.)

**Caching:** 5 minutes per unique search key

---

## 2. Get Schedule Details

**GET** `/api/v1/schedules/{scheduleId}`

**Response:**
```json
{
  "success": true,
  "data": {
    "scheduleId": "uuid",
    "originCity": "New York",
    "destinationCity": "Boston",
    "totalSeats": 100,
    "availableSeats": 45,
    "dynamicPrice": 1000.00,
    "demandFactor": 1.0
  }
}
```

**Status Codes:**
- `200 OK` — Schedule found
- `404 NOT FOUND` — Schedule does not exist

**Caching:** 2 minutes (live query, may bypass cache)

---

## 3. Get Availability Stats

**GET** `/api/v1/schedules/{scheduleId}/availability`

**Response:**
```json
{
  "success": true,
  "data": {
    "totalSeats": 100,
    "availableSeats": 45,
    "bookedSeats": 55,
    "availabilityPercentage": 45.0,
    "isHighDemand": false,
    "demandFactor": 1.0
  }
}
```

**Status Codes:**
- `200 OK` — Availability retrieved
- `404 NOT FOUND` — Schedule not found

**High Demand Indicator:**
- `isHighDemand: true` when availability < 30%
- Indicates 1.5x or 1.8x pricing active

---

## 4. Get High-Demand Schedules

**GET** `/api/v1/schedules/high-demand?originCity={origin}&destinationCity={destination}`

**Query Parameters:**
- `originCity` (required): Departure city
- `destinationCity` (required): Arrival city

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "scheduleId": "uuid",
      "availabilityPercentage": 25.0,
      "isHighDemand": true,
      "demandFactor": 1.5,
      "dynamicPrice": 1500.00
    }
  ]
}
```

**Filtering:** Returns only schedules with `availabilityPercentage < 30%`

**Caching:** 10 minutes per route

---

## 5. Get Duration

**GET** `/api/v1/schedules/{scheduleId}/duration`

**Response:**
```json
{
  "success": true,
  "data": 150
}
```

**Output:** Duration in minutes (e.g., 150 = 2 hours 30 minutes)

---

## Pricing Formula

```
FINAL_PRICE = BASE_FARE × PRICE_MODIFIER × DEMAND_FACTOR
```

### Demand Factor Tiers

| Availability % | Range | Demand Factor | Price Multiplier |
|---|---|---|---|
| ≥ 30% | Normal | 1.0x | Base price |
| 10-30% | High | 1.5x | +50% premium |
| < 10% | Critical | 1.8x | +80% premium |

### Examples

**Scenario 1: Standard Schedule**
- Base: $1,000
- Modifier: 1.0 (no rules)
- Availability: 60%
- Demand: 1.0x
- **Final: $1,000**

**Scenario 2: High Demand Weekend**
- Base: $1,000
- Modifier: 1.2 (weekend)
- Availability: 25%
- Demand: 1.5x
- **Final: $1,800**

**Scenario 3: Last-Minute Promo**
- Base: $1,000
- Modifier: 0.8 (promotional)
- Availability: 5%
- Demand: 1.8x
- **Final: $1,440**

---

## Configuration

**Environment Variables:**

```bash
PRICING_HIGH_DEMAND_THRESHOLD=0.3              # 30% threshold
PRICING_HIGH_DEMAND_MULTIPLIER=1.5             # High demand
PRICING_LOW_AVAILABILITY_MULTIPLIER=1.8        # Critical demand
```

**Adjust Pricing Strategy:**
```bash
# More aggressive pricing
export PRICING_HIGH_DEMAND_THRESHOLD=0.2       # Activate sooner (20%)
export PRICING_HIGH_DEMAND_MULTIPLIER=1.8      # Higher multiplier

# More conservative pricing
export PRICING_HIGH_DEMAND_THRESHOLD=0.4       # Activate later (40%)
export PRICING_HIGH_DEMAND_MULTIPLIER=1.2      # Lower multiplier
```

---

## Error Handling

### Request Validation

**Missing Required Field:**
```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "Origin city is required",
  "status": 400
}
```

**Invalid Date (in past):**
```json
{
  "success": false,
  "errorCode": "VALIDATION_ERROR",
  "message": "Travel date must be today or in the future",
  "status": 422
}
```

### Resource Not Found

```json
{
  "success": false,
  "errorCode": "RESOURCE_NOT_FOUND",
  "message": "Schedule not found",
  "status": 404
}
```

---

## Performance Tips

### Client-Side

1. **Cache search results locally** for 30 seconds
   - Reduces server load on page refreshes
   - Improves UX

2. **Batch requests** when fetching multiple schedules
   - Get search results first
   - Then fetch details for selected schedules only

3. **Use `high-demand` endpoint** for special offers display
   - Pre-filtered list of premium-priced options
   - ~10 minute cache = fresher than main search

### Authorization

All endpoints require JWT authentication in `Authorization` header:

```bash
curl -H "Authorization: Bearer {token}" \
     http://localhost:8080/api/v1/schedules/search
```

---

## Caching Behavior

| Endpoint | Cache | TTL | Key |
|----------|-------|-----|-----|
| `POST /search` | Yes | 5 min | `{origin}-{destination}-{date}-{sortBy}-{sortOrder}` |
| `GET /{id}` | Optional | 2 min | `{scheduleId}` |
| `GET /{id}/availability` | No | — | Re-calculated each time |
| `GET /high-demand` | Yes | 10 min | `{origin}-{destination}` |
| `GET /{id}/duration` | No | — | Calculated each time |

**Cache Invalidation:** TTL-based (automatic), no manual invalidation needed

---

## Integration Example

### Booking Flow (with search)

```
1. GET /api/v1/schedules/search
   └─ User searches "NYC → Boston, Jan 20"
   └─ Receives 15 schedules sorted by price
   └─ Sees pricing: $1,000 (normal), $1,800 (high demand), $2,700 (critical)

2. GET /api/v1/schedules/{scheduleId}/availability
   └─ User clicks schedule
   └─ Check: 8 seats left (critical demand, 1.8x pricing)

3. POST /api/v1/bookings/hold-seat
   └─ User holds seats (see SEAT_HOLD_MECHANISM.md)
   └─ Price locked: $2,700

4. POST /api/v1/bookings/confirm
   └─ Payment processed
   └─ Booking confirmed at locked price
```

---

## Troubleshooting

### Issue: Prices not changing despite low availability

**Check:**
1. Availability percentage calculation: `(availableSeats / totalSeats) * 100`
2. Demand factor thresholds in configuration
3. Cache not returning stale results: `GET /availability` endpoint

**Solution:**
```bash
# Check pricing configuration
curl http://localhost:8080/actuator/env | grep PRICING_

# Force recalculation (cache miss)
# Use different sort order: /search?sortBy=duration instead of price
```

### Issue: High-demand endpoint returns empty list

**Check:**
1. Any schedules with availability < 30%?
2. Query parameters correct (originCity, destinationCity)?

**Verify:**
```bash
# Test main search first
curl -X POST http://localhost:8080/api/v1/schedules/search \
  -d "{\"originCity\":\"NYC\",\"destinationCity\":\"Boston\"}"

# Look for schedules with availabilityPercentage < 30%
# Then call high-demand endpoint
```

---

## HTTP Status Reference

| Code | Meaning | Cause |
|------|---------|-------|
| 200 | OK | Success, data returned |
| 400 | Bad Request | Missing/invalid field in request |
| 401 | Unauthorized | Missing/invalid JWT token |
| 403 | Forbidden | User lacks permission |
| 404 | Not Found | Schedule/resource does not exist |
| 422 | Unprocessable Entity | Validation failed (e.g., date in past) |
| 429 | Too Many Requests | Rate limited (if enabled) |
| 500 | Internal Error | Server error |

---

## Related Documentation

- Full API details: See `SEARCH_AND_PRICING.md`
- Booking workflow: See `SEAT_HOLD_MECHANISM.md`
- Database schema: See `ENTITY_SCHEMA.md`
- Development standards: See `.github/copilot-instructions.md`

---

**Quick Reference Version**: 1.0  
**Last Updated**: 2024-01-15  
**Scope**: POST `/search`, GET `/{id}`, GET `/{id}/availability`, GET `/high-demand`, GET `/{id}/duration`
