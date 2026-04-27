# Smart Schedule AI Agent Instructions

## System Architecture

This is a **Spring Boot microservices** system for AI-powered task management. Services communicate via **Eureka discovery** and **Spring Cloud Gateway**.

### Service Registry (Port 8761)
- **discovery-server**: Eureka server - all services register here
- Health check required: `/actuator/health`

### API Gateway (Port 8080)
- **api-gateway**: Routes all `/api/*` requests to backend services
- Uses **Spring Cloud Gateway** (reactive/WebFlux, NOT servlet-based)
- JWT validation happens here - passes `userId` header to internal endpoints
- CORS configured for localhost:3000 (frontend) and file:// protocol

### Backend Services
Each service follows `<service-name>/<service-name>/` nested structure:
- **auth-service** (9004): Login/registration, JWT token generation
- **user-service** (9001): User profiles, roles (ROLE_USER/ROLE_ADMIN), Cloudinary photo uploads
- **task-service** (9007): Task CRUD, Web Push notifications, schedule events, free hours calculation
- **ai-service** (9006): OpenAI integration via Spring AI, conversation memory
- **product/order-service** (9003/9002): Example services demonstrating Feign inter-service calls

### Databases
Separate PostgreSQL instance per service: `postgres-<name>-db` (ports 5432-5437)

## Authentication & Security

### JWT Flow
1. User logs in via `/api/auth/login` → auth-service generates JWT
2. Gateway validates JWT using HMAC HS512 with shared `JWT_SECRET`
3. Token contains claims: `sub` (userId), `scope` (roles), `exp` (expiration)
4. Services extract userId from `@AuthenticationPrincipal Jwt jwt` via `jwt.getSubject()`

### Security Config Pattern
- Gateway: `ReactiveJwtDecoder` with `MacAlgorithm.HS512`
- Services: `@EnableWebFluxSecurity` (gateway) or servlet-based security
- Scope claim converted to authorities without prefix (`grantedAuthoritiesConverter.setAuthorityPrefix("")`)

### Internal Controllers
Pattern for service-to-service calls (bypassing gateway):
```java
@RestController
@RequestMapping("/internal/tasks")
public class TaskInternalController {
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks(@RequestHeader("userId") Long userId) {
        // Gateway passes userId as header
    }
}
```

## Inter-Service Communication

### Feign Clients (Synchronous)
Used in order-service for cross-service calls:
```java
@FeignClient(name = "product-service", path = "/api/products")
public interface ProductClient {
    @GetMapping("/{id}")
    ProductResponse getProductById(@PathVariable("id") Long id);
}
```
Enable with `@EnableFeignClients` on main application class.

## Code Conventions

### Package Structure
Standard layout per service:
```
src/main/java/com/microsv/<service>/
├── controller/        # @RestController
│   └── internal/      # Internal endpoints (no gateway)
├── dto/
│   ├── request/
│   └── response/
├── entity/            # JPA entities
├── service/           # Business logic interfaces
│   └── impl/          # Service implementations
├── repository/        # Spring Data JPA
├── mapper/            # Manual entity↔DTO mapping (no MapStruct)
├── config/            # Security, Cloudinary, etc.
├── exception/         # Custom exceptions
├── enumeration/       # Enums (TaskStatus, PriorityLevel)
└── util/              # Helper classes
```

### Lombok Usage
All DTOs and entities use:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true) // for controllers/services
```

### Shared Library
`common-exception-lib` (version 1.0.0) used in user-service and task-service for exception handling.

### Mapper Pattern
Manual mappers in `@Component` classes - NO automatic mapping libraries:
```java
@Component
public class TaskMapper {
    public TaskResponse toTaskResponse(Task task) { /* manual mapping */ }
    public TaskResponse tupleToTaskResponse(Tuple tuple) { /* from native queries */ }
}
```

## Critical Domain Logic

### Task Service - Free Hours Calculation
**DO NOT** count TODO tasks as busy time - see [TEST_FREE_HOURS_LOGIC.md](../task-service/TEST_FREE_HOURS_LOGIC.md):
- `DONE` tasks: busy from `created_at` to `completed_at`
- `IN_PROGRESS`: busy from `created_at` to `CURRENT_TIMESTAMP`
- `TODO`: **0 hours busy** (not started)
- Avoid counting overlapping tasks multiple times
- Work week assumption: 70 hours (10h/day × 7 days), not 168 hours

### AI Service
- Uses Spring AI OpenAI integration with `SPRING_AI_OPENAI_API_KEY`
- Maintains conversation memory per user via `conversationId` (UUID)
- Supports multipart requests: text message + optional file upload

### Task Notifications
Web Push API integration using `web-push` library (version 5.1.1):
- Store user's push subscription in `PushSubscription` entity
- VAPID keys configured in application.yml

## Development Workflow

### Build & Run
```bash
# Build all services (from workspace root)
docker-compose build

# Start entire system
docker-compose up

# Rebuild specific service
docker-compose build task-service
docker-compose up -d task-service
```

### Service Dependencies
Services start in order via `depends_on` with health checks:
1. discovery-server (must be healthy)
2. postgres-* databases (must be healthy)
3. Business services (user, auth, task, ai, product, order)
4. api-gateway (starts last)

### Local Testing
- Gateway: http://localhost:8080/api/*
- Eureka dashboard: http://localhost:8761
- Direct service access: http://localhost:900X (bypass gateway for debugging)

### Database Changes
- Hibernate DDL: `spring.jpa.hibernate.ddl-auto=update` (auto-schema updates)
- Manual migrations in [TEST_FREE_HOURS.sql](../task-service/TEST_FREE_HOURS.sql) for complex logic

## Configuration Files

### application.yml Pattern
Every service needs:
```yaml
spring:
  application:
    name: <service-name>  # Must match Eureka registration
  datasource:
    url: jdbc:postgresql://postgres-<name>-db:5432/<name>_db
eureka:
  client:
    service-url:
      defaultZone: http://discovery-server:8761/eureka/
jwt:
  secret: ${JWT_SECRET}  # Shared across all services
```

### Gateway Routes
Load-balanced routes using service names:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: task-service-route
          uri: lb://task-service  # 'lb://' triggers Eureka lookup
          predicates:
            - Path=/api/tasks/**
```

## Common Pitfalls

1. **Gateway is reactive**: Use `ServerHttpSecurity`, not `HttpSecurity`
2. **JWT secret must match**: Same value in gateway + all services
3. **Service names matter**: Eureka registration name = Feign client name = Gateway URI
4. **Nested directories**: Services live in `<service>/<service>/` not `<service>/`
5. **Health checks required**: Docker won't start dependent services until DB is ready
6. **Port conflicts**: Each service + DB needs unique port (check docker-compose.yml)
