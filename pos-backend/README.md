# Lumora POS System

Enterprise Point of Sale System built with:

- **Frontend**: Next.js 14+ (App Router, Tailwind CSS, shadcn/ui, Zustand)
- **Backend**: Spring Boot 3.3+ (Java 17, Spring Security, JPA, Flyway)
- **Database**: PostgreSQL 15+
- **Cache**: Redis 7

## Quick Start

### Prerequisites

- Java 17+
- Node.js 20+
- Docker & Docker Compose

### 1. Start Infrastructure

```bash
docker-compose up -d
```

### 2. Start Backend

```bash
cd backend
./mvnw spring-boot:run
```

### 3. Start Frontend

```bash
cd frontend
npm run dev
```

### URLs

- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8081/api/v1
- **Health Check**: http://localhost:8081/actuator/health

## Project Structure

```
POS System/
├── backend/          # Spring Boot API
├── frontend/         # Next.js UI
├── documentation/    # Project docs
├── docker-compose.yml
└── .env.example
```
