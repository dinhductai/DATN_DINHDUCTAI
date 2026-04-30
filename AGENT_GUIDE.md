# AGENT_GUIDE.md — Smart Schedule Backend

> Hướng dẫn dành cho AI Agent. Đọc file này trước khi làm bất kỳ task nào.  
> Cập nhật lần cuối: 2026-04-30

---

## 1. TỔNG QUAN NHANH

**Tên dự án:** Smart Schedule — Ứng dụng quản lý lịch thông minh tích hợp AI  
**Kiến trúc:** Microservices (Spring Boot 3.3.5, Java 21, Spring Cloud 2023.0.3)  
**Root path:** `d:\DATN\Backend\`  
**Entry point duy nhất:** `http://localhost:8080` (API Gateway)  
**Service registry:** `http://localhost:8761` (Eureka)

---

## 2. MAP SERVICES — PORT — DATABASE

| Service | Port | Database | DB Port | Trạng thái |
|---------|------|----------|---------|-----------|
| `discovery-server` | 8761 | — | — | Active |
| `api-gateway` | 8080 | — | — | Active |
| `user-service` | 9001 | `user_db` | 5432 | Active |
| `email-service` | 9002 | `email_db` | 5433 | Active |
| `auth-service` | 9004 | `auth_db` | 5436 | Active |
| `ai-service` | 9006 | `ai_db` | 5437 | Active |
| `task-service` | 9007 | `task_db` | 5435 | Active |
| `product-service` | 9003 | `product_db` | 5434 | **Disabled** |

---

## 3. CẤU TRÚC THƯ MỤC TỪNG SERVICE

```
d:\DATN\Backend\
├── <service-name>\<service-name>\src\main\java\com\microsv\<pkg>\
│   ├── controller\          ← REST controllers (public)
│   │   └── internal\        ← Internal controllers (chỉ service khác gọi)
│   ├── service\             ← Interface
│   │   └── impl\            ← Implementation
│   ├── repository\          ← JPA repositories
│   ├── entity\              ← JPA entities (@Entity)
│   ├── dto\
│   │   ├── request\         ← *Request.java
│   │   ├── response\        ← *Response.java
│   │   └── message\         ← RabbitMQ message DTOs
│   ├── config\              ← Spring configs (Security, Redis, RabbitMQ...)
│   ├── enumeration\         ← Enums
│   ├── messaging\           ← RabbitMQ Producers / Consumers
│   ├── feign\ hoặc client\  ← OpenFeign clients
│   └── util\                ← Utilities
└── common-exception-lib\    ← Shared library (ApiResponse, ErrorCode)
```

**Package gốc của mỗi service:**
- `api-gateway` → `com.example.api_gateway`
- `product-service` → `com.example.product_service`
- Tất cả còn lại → `com.microsv.<service_name>` (dấu `-` thành `_`)

---

## 4. CÔNG NGHỆ & DEPENDENCIES CHÍNH

| Loại | Công nghệ |
|------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.3.5, Spring Cloud 2023.0.3, Spring AI 1.0.0 |
| Security | JWT (Nimbus JOSE v9.31), Spring Security, OAuth2 Resource Server |
| Database | PostgreSQL 16-alpine |
| Messaging | RabbitMQ 3.13 (AMQP, Jackson2JsonMessageConverter) |
| Cache | Redis 7-alpine |
| AI | OpenAI GPT-4o-mini (Spring AI), temperature=0.7, maxTokens=2000 |
| File Storage | Cloudinary (cloudinary-http44 v1.32.0) |
| Push Notification | Web Push VAPID (web-push v5.1.1) |
| Inter-service REST | Spring Cloud OpenFeign |
| Build | Maven (parent POM + 9 modules) |
| Deploy | Docker + Docker Compose |
| Shared lib | `common-exception-lib` v1.0.0 |

---

## 5. ENTITIES — FIELDS ĐẦY ĐỦ

### `Task` (task-service)
```java
// Path: task-service/task-service/src/main/java/com/microsv/task_service/entity/Task.java
Long taskId            // @Id @GeneratedValue
String title           // nullable=false
String description     // @Column(columnDefinition="TEXT")
OffsetDateTime deadline // nullable=false
TaskStatus status      // enum: TODO, IN_PROGRESS, DONE
PriorityLevel priority // enum: HIGH, MEDIUM, LOW
OffsetDateTime createdAt  // start time of work
OffsetDateTime completedAt
Long userId            // nullable=false
```

### `Event` (task-service) — liên kết 1:1 với Task
```java
// Path: task-service/task-service/src/main/java/com/microsv/task_service/entity/Event.java
Long eventId
Long taskId            // unique=true, nullable=false
String eventDescription // TEXT
String linkEvent       // length=500
String location        // length=500
Boolean isOnline       // default=false
Integer reminderMinutesBefore // default=30
Task task              // @OneToOne
```

### `ScheduleEvent` (task-service)
```java
// Path: task-service/task-service/src/main/java/com/microsv/task_service/entity/ScheduleEvent.java
Long eventId
String title           // nullable=false
OffsetDateTime startTime // nullable=false
OffsetDateTime endTime   // nullable=false
Long userId
Task relatedTask       // @ManyToOne @Lazy
```

### `PushSubscription` (task-service)
```java
Long id
String endpoint        // TEXT, nullable=false
String p256dh
String auth
Long userId
```

### `User` (user-service)
```java
// Path: user-service/user-service/src/main/java/com/microsv/user_service/entity/User.java
Long userId            // @Id @GeneratedValue
String userName        // length=50
String password        // nullable=false
String email           // unique=true, length=100, nullable=false
String profile
LocalDateTime createdAt // @CreationTimestamp
Set<Role> roles        // @ManyToMany
```

### `Role` (user-service)
```java
Long roleId
RoleName roleName      // enum: ADMIN, MANAGER, USER (unique=true)
Set<Permission> permissions // @ManyToMany
```

### `ConversationMemory` (ai-service)
```java
// Path: ai-service/ai-service/src/main/java/com/microsv/ai_service/entity/ConversationMemory.java
Integer chatId         // @Id @GeneratedValue
String conversationId  // nullable=false
String role            // "user" hoặc "assistant"
String content         // length=3000
Timestamp createAt     // default=now
Long userId
```

### `InvalidatedToken` (auth-service)
```java
String tokenId
String jti
LocalDateTime expiryTime
```

### `Email` (email-service)
```java
Long id
Long userId
String toEmail
String subject
String body            // TEXT
String status
String errorMessage    // TEXT
LocalDateTime createdAt // @CreationTimestamp
LocalDateTime sentAt
```

---

## 6. API ENDPOINTS ĐẦY ĐỦ

### AUTH SERVICE (`/api/auth`)
```
POST /api/auth/login          → AuthenticationRequest{email, password} → token
POST /api/auth/introspect     → IntrospectRequest{token} → valid:boolean
POST /api/auth/logout         → LogoutRequest{token}
```

### USER SERVICE (`/api/users`)
```
POST   /api/users/register           → UserCreationRequest → UserResponse  (public)
POST   /api/users/create             → UserCreationRequest → UserResponse
GET    /api/users                    → List<UserResponse>
GET    /api/users/{userId}           → UserResponse
PUT    /api/users/{userId}           → UserUpdateRequest → UserResponse
DELETE /api/users/{userId}
GET    /api/users/counts             → Long
GET    /api/users/counts-register    → Long (users this week)
GET    /api/users/search?keyword=X   → List<UserResponse>
POST   /api/users/upload             → MultipartFile → photo URL
GET    /api/users/photo
DELETE /api/users/del

[Internal - không qua Gateway]
GET    /internal/users/{email}       → UserAuthResponse  (dùng cho auth-service)
GET    /internal/users/{id}/email    → String            (dùng cho task-service)
```

### TASK SERVICE (`/api/tasks`)
```
POST   /api/tasks                              → TaskCreationRequest → TaskResponse
GET    /api/tasks                              → List<TaskResponse>
GET    /api/tasks/{taskId}                     → TaskResponse
GET    /api/tasks/status/{status}              → List<TaskResponse>
GET    /api/tasks/upcoming?hours=24            → List<TaskResponse>
PUT    /api/tasks/{taskId}                     → TaskUpdateRequest → TaskResponse
PATCH  /api/tasks/{taskId}/status?status=X     → TaskResponse
DELETE /api/tasks/{taskId}
GET    /api/tasks/today                        → List<TaskResponse>
GET    /api/tasks/today/overdue                → List<TaskResponse>
GET    /api/tasks/today/completed              → List<TaskResponse>
GET    /api/tasks/search?title=X               → List<TaskResponse>
GET    /api/tasks/weekly                       → List<TaskResponse>

[Statistics]
GET    /api/tasks/statistics                             → TaskStatisticResponse
GET    /api/tasks/statistics/weekly-status               → StatusTaskWeekResponse
GET    /api/tasks/statistics/weekly-distribution         → DailyTaskCountResponse
GET    /api/tasks/statistics/completion-before-deadline  → Double
GET    /api/tasks/statistics/free-hours                  → Double
GET    /api/tasks/statistics/creation-timeline           → TaskTimelineResponse
GET    /api/tasks/statistics/weekly-task-complete        → DailyCompletedTasksResponse
GET    /api/tasks/statistics/weekly-task-priority        → TaskPriorityCountResponse
GET    /api/tasks/active-users/weekly                    → Long

[Notifications]
POST   /api/notifications/subscribe   → SubscriptionRequest
POST   /api/notifications/trigger-daily

[Internal - không qua Gateway]
GET    /internal/tasks                → List<TaskResponse>     (dùng cho ai-service)
GET    /internal/tasks/filter         → List<TaskResponse>     (dùng cho ai-service)
GET    /internal/tasks/ai/json        → String (Redis cached JSON, dùng cho ai-service)
```

### AI SERVICE (`/api/ai`)
```
POST   /api/ai            @RequestParam message, @RequestParam(required=false) file → ChatAIConversationResponse
POST   /api/ai/rich       @RequestParam message, @RequestParam(required=false) file → AIRichResponse
GET    /api/ai/id         → String (conversationId)
GET    /api/ai?page=0&size=10 → Page<ConversationMemory>
```

### PRODUCT SERVICE (`/api/products`) — Disabled
```
POST   /api/products
GET    /api/products
GET    /api/products/{id}
POST   /api/products/{id}/decrease-stock
```

---

## 7. RABBITMQ — QUEUES & FLOWS

### Queues đang dùng:
```
task-notification-queue   ← task-service → email-service
event-email-queue         ← task-service → email-service
event-creation-queue      ← task-service → email-service
event-reminder-queue      ← task-service → email-service
event-update-queue        ← task-service → email-service
```

### Producers (task-service):
| Class | File | Gửi gì |
|-------|------|---------|
| `TaskNotificationProducer` | `task-service/.../messaging/TaskNotificationProducer.java` | Daily task digest |
| `EventEmailProducer` | `task-service/.../messaging/EventEmailProducer.java` | Event create/remind/update |

### Consumers (email-service):
| Class | File | Lắng nghe queue |
|-------|------|-----------------|
| `EmailConsumer` | `email-service/.../messaging/EmailConsumer.java` | `task-notification-queue` |
| `EventInvitationService` | `email-service/.../service/EventInvitationService.java` | `event-creation-queue`, `event-reminder-queue` |

### Message DTOs (phải đồng bộ giữa producer và consumer):
```
TaskNotificationMessage → {userId, userEmail, date, tasks: List<TaskInfo>}
EventCreationMessage    → {eventId, taskId, invitedEmails, task details}
EventReminderMessage    → {eventId, event details, invitedEmails}
EventUpdateMessage      → {eventId, invitedEmails, updated details}
```

---

## 8. INTER-SERVICE COMMUNICATION (FEIGN)

### Feign Clients & Targets:

| Service | Client Class | Gọi đến | Path |
|---------|-------------|---------|------|
| auth-service | `UserClient` | user-service | `GET /internal/users/{id}` |
| task-service | `UserClient` | user-service | `GET /internal/users/{id}/email` |
| ai-service | `TaskClient` | task-service | `GET /internal/tasks`, `GET /internal/tasks/filter`, `GET /internal/tasks/ai/json` |

**Lưu ý:** Internal endpoints không đi qua API Gateway, gọi trực tiếp qua Eureka load balancing.

---

## 9. CHUỖI DỮ LIỆU (DATA FLOWS)

### Login:
```
Client → POST /api/auth/login → Gateway → auth-service
       → [Feign] user-service GET /internal/users/{email}
       → validate password → generate JWT → return token
```

### Tạo Task:
```
Client (JWT) → POST /api/tasks → Gateway (validate JWT) → task-service
             → extract userId từ JWT → persist task_db → return TaskResponse
```

### Daily Email Notification:
```
DailyTaskNotificationService (@Scheduled)
→ fetch today's tasks từ DB
→ TaskNotificationProducer → RabbitMQ task-notification-queue
→ EmailConsumer (email-service)
→ format HTML email → SMTP → user email
```

### AI Chat:
```
Client (JWT) → POST /api/ai?message=... → Gateway → ai-service
            → extract userId from JWT
            → [Feign] task-service GET /internal/tasks/ai/json (Redis cached)
            → build context prompt (PromptUtil.SYSTEM_PROMPT + tasks JSON)
            → Spring AI ChatClient → OpenAI GPT-4o-mini
            → save ConversationMemory → return response
```

### Tạo Event + Invite Email:
```
Client → POST /api/tasks (isEvent=true, eventCreationRequest.invitedEmails=[...])
→ task-service tạo Task + Event
→ EventEmailProducer → event-creation-queue
→ EventInvitationService (email-service) → SMTP invite emails
```

### Event Reminder:
```
EventReminderRedisService (Redis ZSET, @Scheduled check)
→ phát hiện event sắp đến hạn
→ EventEmailProducer → event-reminder-queue
→ EventInvitationService → SMTP reminder emails
```

---

## 10. SECURITY — JWT FLOW

- JWT được tạo tại `auth-service` (AuthenticationServiceImpl) dùng Nimbus JOSE
- JWT chứa: `userId`, `roles`, `exp`
- API Gateway validate JWT trước khi forward request
- Mỗi service cũng configure `SecurityConfig` với OAuth2 Resource Server để validate độc lập
- Token blacklist: `InvalidatedToken` table trong `auth_db`
- `JWT_SECRET` env var phải giống nhau trên tất cả services

---

## 11. REDIS — CACHING

Redis được dùng trong **task-service**:

| Class | Mục đích |
|-------|---------|
| `RedisConfig` | Cấu hình RedisTemplate, Jackson serializer |
| `TaskCacheService` | Cache task list theo userId |
| `EventReminderRedisService` | ZSET lưu event reminders theo thời gian, scheduled polling |

**AI service** đọc task JSON từ Redis qua:
```
GET /internal/tasks/ai/json → task-service → TaskCacheService → Redis
```

---

## 12. RESPONSE CHUẨN (từ common-exception-lib)

```json
{
  "code": 200,
  "message": "Success",
  "data": { }
}
```

`ApiResponse<T>` nằm tại: `common-exception-lib/src/main/java/com/microsv/common/response/ApiResponse.java`

`ErrorCode` enum: `common-exception-lib/src/main/java/com/microsv/common/enumeration/ErrorCode.java`

---

## 13. KEY CONFIG FILES — VỊ TRÍ

| File | Service | Nội dung quan trọng |
|------|---------|---------------------|
| `docker-compose.yml` | root | Toàn bộ stack, network `smart-schedule-network` |
| `.env` | root | Tất cả secrets/env vars |
| `pom.xml` | root | Maven parent, danh sách modules |
| `application.yml` | mỗi service | Port, datasource URL, secret keys |
| `SecurityConfig.java` | mỗi service | OAuth2 + JWT config |
| `RabbitMQConfig.java` | task-service, email-service | Queue/Exchange definitions |
| `RedisConfig.java` | task-service | Redis template config |
| `PromptUtil.java` | ai-service | SYSTEM_PROMPT cho OpenAI |

---

## 14. NAMING CONVENTIONS (để sinh code nhất quán)

| Loại | Pattern | Ví dụ |
|------|---------|-------|
| Controller | `*Controller` | `TaskController`, `UserController` |
| Internal Controller | `*InternalController` | `TaskInternalController` |
| Service interface | `*Service` | `TaskService` |
| Service impl | `*ServiceImpl` | `TaskServiceImpl` |
| Repository | `*Repository` | `TaskRepository` |
| Entity | PascalCase | `Task`, `ConversationMemory` |
| Request DTO | `*Request` | `TaskCreationRequest` |
| Response DTO | `*Response` | `TaskResponse` |
| Message DTO | `*Message` | `EventCreationMessage` |
| Config | `*Config` | `SecurityConfig`, `RabbitMQConfig` |
| RabbitMQ Producer | `*Producer` | `EventEmailProducer` |
| RabbitMQ Consumer | `*Consumer` | `EmailConsumer` |
| Feign Client | `*Client` | `UserClient`, `TaskClient` |
| Enum | PascalCase, values UPPER_SNAKE | `TaskStatus.IN_PROGRESS` |

---

## 15. ENUMS QUAN TRỌNG

```java
// TaskStatus (task-service & ai-service phải đồng bộ)
TODO, IN_PROGRESS, DONE

// PriorityLevel (task-service & ai-service phải đồng bộ)
HIGH, MEDIUM, LOW

// RoleName (user-service)
ADMIN, MANAGER, USER
```

> **Lưu ý:** ai-service tự định nghĩa lại `TaskStatus` và `PriorityLevel` (không import từ task-service). Khi sửa enum ở task-service, phải sửa cả ai-service.

---

## 16. ENVIRONMENT VARIABLES CẦN THIẾT

```bash
# Database
POSTGRES_PASSWORD=password123

# JWT (phải đồng nhất giữa tất cả services)
JWT_SECRET=A9mK2pX8vS4rW7tF1yD3bJ6nL0zQ5hG2eR9cU4xT8sV1wM7oP3lY6jN5iC

# AI
OPENAI_API_KEY=sk-proj-...
CLAUDE_API_KEY=...           # dùng trong task-service (ClaudeTaskConvertService)

# Cloudinary (user-service)
CLOUDINARY_CLOUD_NAME=...
CLOUDINARY_API_KEY=...
CLOUDINARY_API_SECRET=...

# Email (email-service)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...            # Gmail App Password (16 ký tự)

# RabbitMQ
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_HOST=rabbitmq       # service name trong docker network
RABBITMQ_PORT=5672

# Redis (task-service)
REDIS_HOST=redis
REDIS_PORT=6379

# Push Notifications (task-service)
VAPID_PUBLIC_KEY=...
VAPID_PRIVATE_KEY=...

# Default users (user-service)
DEFAULT_ADMIN_PASSWORD=admin123
DEFAULT_USER_PASSWORD=user123
```

---

## 17. BUILD & RUN

```bash
# Build tất cả
mvn clean package -DskipTests

# Hoặc dùng script
./build-all.sh   # Linux/Mac
build-all.bat    # Windows

# Chạy toàn bộ stack
docker-compose up -d

# Chỉ chạy 1 service (sau khi build)
docker-compose up -d task-service

# Xem logs
docker-compose logs -f task-service

# Access points
http://localhost:8080       # API Gateway
http://localhost:8761       # Eureka Dashboard
http://localhost:15672      # RabbitMQ Admin (guest/guest)
```

---

## 18. TIPS CHO AI AGENT KHI LÀM VIỆC

### Khi thêm endpoint mới:
1. Thêm method vào interface `*Service.java`
2. Implement trong `*ServiceImpl.java`
3. Thêm endpoint vào `*Controller.java`
4. Nếu cần internal → thêm vào `*InternalController.java`
5. Tạo/cập nhật DTO trong `dto/request/` hoặc `dto/response/`
6. Không cần sửa Gateway (đã wildcard route)

### Khi thêm RabbitMQ queue mới:
1. Khai báo Queue/Exchange/Binding bean trong `RabbitMQConfig.java` của **cả 2 service** (producer và consumer)
2. Tạo Message DTO trong `dto/message/` của **cả 2 service** (phải giống nhau)
3. Tạo Producer method trong service phát
4. Tạo `@RabbitListener` trong Consumer service

### Khi thêm Feign call mới:
1. Thêm method vào `*Client.java` (Feign interface)
2. Thêm endpoint tương ứng vào `*InternalController.java` của service bị gọi
3. Internal endpoints không cần qua Gateway

### Khi sửa Entity:
- `spring.jpa.hibernate.ddl-auto: update` → DDL tự migrate, nhưng không xóa cột cũ
- Nếu cần migration phức tạp → thêm Flyway/Liquibase (hiện chưa có)

### Khi thêm field JWT cần propagate:
- Sửa `AuthenticationServiceImpl` (tạo token)
- Sửa `SecurityConfig` ở service cần đọc field đó
- userId được extract từ JWT trong controller qua `@AuthenticationPrincipal` hoặc `SecurityContextHolder`

### Lỗi phổ biến:
- `TaskStatus` / `PriorityLevel` giữa task-service và ai-service **phải đồng bộ tay**
- Cloudinary config class bị typo: `CloundinaryConfig` (không phải `CloudinaryConfig`)
- Email service DB port trong docker-compose: `postgres-email-db:5432` (internal) nhưng expose ra ngoài là `5433`

---

## 19. THỐNG KÊ PROJECT

| Metric | Số liệu |
|--------|---------|
| Java files | ~150 |
| Active microservices | 7 |
| PostgreSQL databases | 5 |
| RabbitMQ queues | 5 |
| Public API endpoints | ~45 |
| Internal API endpoints | ~5 |
| External APIs | OpenAI, Cloudinary |
| Maven modules | 9 |
