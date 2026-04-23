# Flash Sale System - Full Design Breakdown

---

## 1. Requirements

Functional
- Admin can create a sale with limited inventory and a start time
- Users can join a sale, reserve an item, and complete checkout
- System must prevent overselling under high concurrency
- Orders expire if payment isn't completed in time (for example 10 mins)

Non-Functional
- Handle 100k+ concurrent users hitting Buy at the same time
- Inventory count must be strongly consistent (no oversell)
- P99 latency under 200ms for queue entry
- System should gracefully degrade and reject excess users instead of crashing

---

## 2. High-Level Architecture

User -> CDN / Rate Limiter -> API Gateway

API Gateway routes to:
- Queue Service (Redis / SQS)
- Inventory Service (Redis atomic operations)
- Order Service (Postgres/MySQL)
- Payment Service

---

## 3. Core Components

### A. Pre-Sale Setup
Before sale starts, preload state in Redis:
- SALE:{id}:inventory -> available stock
- SALE:{id}:start_time -> unix timestamp
- SALE:{id}:status -> pending/live/ended

During sale, Redis is the source of truth for hot inventory.

### B. Rate Limiting and Admission Control
Flow:
1. Token bucket or sliding window limiter by user_id + sale_id
2. Sale live check
3. Duplicate reservation check

Reject aggressively with 429/403 to protect downstream services.

### C. Inventory Locking (Critical)
Use Redis DECR (atomic):
1. DECR inventory
2. If value >= 0 reserve slot
3. If value < 0, INCR to rollback and return SOLD_OUT

This is optimistic and lock-free at application level.

### D. Queue-Based Checkout
After reservation:
1. Push reservation job to queue with expiration
2. Worker creates pending order
3. Worker issues payment token/link
4. Expiry timer closes unpaid orders and returns stock

Benefits:
- Absorbs spikes
- Decouples payment latency
- Enables backpressure

### E. Oversell Prevention (3 Layers)
1. Redis atomic inventory decrement
2. Database constraints to prevent invalid persistence
3. Expiry compensation to return abandoned slots

Order expiry flow:
- Paid in time -> confirm order
- Not paid -> mark expired, increment inventory, notify waitlist

---

## 4. Data Model

Core entities:
- sales(id, product_id, total_qty, price, starts_at, ends_at)
- orders(id, user_id, sale_id, status, reserved_at, expires_at, paid_at)

Recommended index:
- pending orders by expires_at for efficient expiry scanning

---

## 5. API Design

- POST /sales/:id/join        attempt reservation
- GET  /orders/:id            poll order status
- POST /orders/:id/pay        submit payment
- POST /admin/sales           create sale

Response style should explicitly return SOLD_OUT / TOO_MANY_REQUESTS / retry hints.

---

## 6. Scaling Decisions

- Admission control and Redis absorb the first burst
- Async queue isolates payment bottleneck
- Redis HA (Sentinel/Cluster + persistence)
- Idempotency keys for retry-safe operations
- Per-user per-sale reservation guard
- Worker validates sale window before processing queued jobs

---

## 7. Full Happy Path

1. User clicks Buy
2. Admission checks pass
3. Atomic inventory decrement reserves slot
4. Job pushed to queue
5. Worker creates pending order
6. Payment link returned
7. Payment completed before expiry
8. Order marked PAID

Expiry path:
- If unpaid by deadline -> EXPIRED + inventory returned + waitlist progress

---

## 8. Important Edge Cases

- Double-click and retries: idempotency key
- Clock skew: always enforce server-side sale time
- Waitlist fairness: FIFO vs lottery policy
- Reconciliation: periodic Redis vs DB consistency checks

---

## 9. Mapping To This Repository (Current Status)

Already implemented in this project:
- Sale window validation using server time
- Admin sale creation endpoint (`POST /api/admin/sales`)
- Reservation endpoint (`POST /api/sales/{id}/join`)
- Admission control:
	- per-user per-sale sliding-window rate limiting
	- duplicate reservation guard
	- per-user quantity limit checks
- Redis-first inventory reservation (`DECR`/rollback) with in-memory fallback when Redis is down
- Queue-based order processing worker (Redis list / fallback local queue)
- Order lifecycle APIs:
	- poll order status (`GET /api/orders/{id}`)
	- pay order (`POST /api/orders/{id}/pay`)
- Automatic pending-order expiry job that marks EXPIRED and returns inventory
- Frontend integration via Vite API proxy and UI controls for join/poll/pay

Partially implemented / next production upgrades:
- Dedicated payment service integration and callback signature validation
- External distributed queue with delivery guarantees and dead-letter policy
- Redis HA (Sentinel/Cluster, AOF/RDB strategy)
- Waitlist notification service and fairness strategy (FIFO vs lottery)
- Periodic Redis-vs-DB reconciliation and self-healing jobs
