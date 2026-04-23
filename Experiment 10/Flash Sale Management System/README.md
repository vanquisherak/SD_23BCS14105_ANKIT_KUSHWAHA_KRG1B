# Flash Sale System (React + Spring Boot + MySQL)

This project is a complete starter for an academic system design evaluation of a flash sale platform.

Detailed architecture and scaling design: see [SYSTEM_DESIGN.md](SYSTEM_DESIGN.md).

## Tech Stack

- Frontend: React + Vite
- Backend: Spring Boot 3 (Java 17), Spring Web, Spring Data JPA, Spring Data Redis
- Database: MySQL (managed in MySQL Workbench)
- Queue/Hot state: Redis (with in-memory fallback mode for local demo)

## System Design Focus

The implementation demonstrates design-level flash-sale requirements:

- Sale window control with server-side validation
- Admission control (rate limit + duplicate reservation guard)
- Atomic inventory reservation with Redis-first strategy
- Queue-based order creation worker
- Order lifecycle: queued -> pending -> paid/expired
- Automatic expiry compensation (returns inventory)

## Project Structure

- `backend`: Spring Boot API and business logic
- `frontend`: React UI dashboard for product, sale-event, and purchase simulation

## Backend Setup

1. Open `backend/src/main/resources/application.properties`
2. Replace placeholders:
   - `spring.datasource.username=YOUR_DB_USERNAME`
   - `spring.datasource.password=YOUR_DB_PASSWORD`
3. Ensure MySQL is running.
4. Optional but recommended: run Redis on `localhost:6379`.
   - If Redis is unavailable, the app uses in-memory fallback for demo continuity.
5. Run backend:

```bash
cd backend
mvn spring-boot:run
```

Backend URL: `http://localhost:8081` (or configured port)

## Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

Frontend URL: `http://localhost:5173`

The frontend uses a Vite proxy for `/api` and forwards to backend target configured in `frontend/vite.config.js`.

## API Endpoints

- `POST /api/products` create product
- `GET /api/products` list products
- `POST /api/admin/sales` create sale event (admin)
- `GET /api/sales` list sale events
- `POST /api/sales/{saleEventId}/join` reserve slot / join sale
- `GET /api/orders/{orderId}` poll order status
- `POST /api/orders/{orderId}/pay` pay pending order

## Suggested Evaluation Scenarios

1. Create a product with stock (for example 100).
2. Create a sale event with lower stock (for example 20) and max per user (for example 2).
3. Join sale from multiple users and verify:
   - before start time (should fail)
   - during live window with valid qty (should reserve)
   - repeated join for same user (should reject)
   - exceeding per-user limit (should reject)
   - exhausted inventory (should return SOLD_OUT)
4. Poll order status then call pay endpoint.
5. Leave one order unpaid and verify automatic expiry returns stock.

## Notes

- Database schema is auto-created via JPA (`ddl-auto=update`) for quick iteration.
- For production hardening, add Flyway/Liquibase migrations, dedicated external queue, and Redis HA setup.
