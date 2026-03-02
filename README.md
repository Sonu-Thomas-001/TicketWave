# TicketWave

A production-grade travel and event ticket booking system built as a **Spring Boot modular monolith** with a **React (Vite)** frontend.

## Tech Stack

### Backend
- **Java 21+** / Spring Boot 3.x
- Spring Security 6.x with JWT authentication
- Spring Data JPA + Hibernate / PostgreSQL
- Redis (Redisson) for distributed seat locking/holds
- CQRS-friendly modular monolith architecture
- Optimistic locking + distributed hold semantics

### Frontend
- **React 18** + Vite 5
- Tailwind CSS + Radix UI primitives
- Framer Motion animations
- Code-split lazy-loaded routes

## Features

- Browse events (concerts, sports, theatre, festivals, shows)
- Interactive seat map with real-time selection
- Secure checkout with multi-step stepper
- Booking management (view, cancel, refund)
- Operator & Admin dashboards
- JWT authentication (login/register)
- Dark mode support
- Responsive design (mobile-first)
- Webhook-driven payment confirmation
- Idempotency for mutations

## Project Structure

```
TicketWave/
  src/                  # Spring Boot backend
    main/java/com/ticketwave/
      admin/            # Admin module
      auth/             # Authentication (JWT)
      booking/          # Booking lifecycle
      catalog/          # Events, routes, seats, schedules
      common/           # Shared config, security, exceptions, audit
      inventory/        # Seat inventory management
      payment/          # Payment processing
      refund/           # Refund & cancellation policies
      user/             # User & passenger management
    main/resources/     # application.yml configs
    test/               # JUnit 5 + Mockito tests
  frontend/             # React + Vite frontend
    src/
      components/       # UI, layout, shared, features
      pages/            # All page components
      context/          # Auth & Theme providers
      lib/              # Utils, API client, mock data
```

## Prerequisites

- Java 21+ (JDK)
- Maven 3.9+
- Node.js 18+
- PostgreSQL 15+
- Redis 7+

## Quick Start

### 1. Clone & configure

```bash
git clone https://github.com/Sonu-Thomas-001/TicketWave.git
cd TicketWave
```

Create an `env.ps1` (PowerShell) with your environment variables:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:DB_PASSWORD = 'your_password'
$env:REDIS_HOST = 'localhost'
$env:REDIS_PORT = '6379'
$env:SPRING_PROFILES_ACTIVE = 'dev'
```

### 2. Start everything (one command)

```powershell
.\start.ps1
```

This will:
- Run pre-flight checks (Java, Maven, Node, PostgreSQL, Redis)
- Start the Spring Boot backend on port **8080**
- Start the Vite frontend on port **3000**
- Open your browser automatically

### 3. Manual start

**Backend:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dmaven.test.skip=true
```

**Frontend:**
```bash
cd frontend
npm install
npm run dev
```

## API

- Backend: `http://localhost:8080`
- Frontend: `http://localhost:3000` (proxies `/api` to backend)
- Actuator: `http://localhost:8080/actuator`

## License

MIT
