# Smart Schedule - Tài liệu Tổng quan Dự án

> **Ngày cập nhật:** 2026-03-28

---

## Mục lục

1. [Giới thiệu dự án](#1-giới-thiệu-dự-án)
2. [Kiến trúc hệ thống](#2-kiến-trúc-hệ-thống)
3. [Công nghệ sử dụng](#3-công-nghệ-sử-dụng)
4. [Cấu trúc thư mục](#4-cấu-trúc-thư-mục)
5. [Chi tiết từng Service](#5-chi-tiết-từng-service)
   - [5.1 Discovery Server](#51-discovery-server)
   - [5.2 API Gateway](#52-api-gateway)
   - [5.3 Auth Service](#53-auth-service)
   - [5.4 User Service](#54-user-service)
   - [5.5 Task Service](#55-task-service)
   - [5.6 AI Service](#56-ai-service)
   - [5.7 Order Service (Demo)](#57-order-service-demo)
   - [5.8 Product Service (Demo)](#58-product-service-demo)
6. [Cơ sở dữ liệu](#6-cơ-sở-dữ-liệu)
7. [Bảo mật & Xác thực](#7-bảo-mật--xác-thực)
8. [Giao tiếp giữa các Service](#8-giao-tiếp-giữa-các-service)
9. [Hạ tầng & Triển khai Docker](#9-hạ-tầng--triển-khai-docker)
10. [Tính năng nổi bật](#10-tính-năng-nổi-bật)
11. [Luồng hoạt động chính](#11-luồng-hoạt-động-chính)
12. [Cấu hình môi trường](#12-cấu-hình-môi-trường)
13. [Hạn chế & Điểm cần cải thiện](#13-hạn-chế--điểm-cần-cải-thiện)

---

## 1. Giới thiệu dự án

**Smart Schedule** là một ứng dụng quản lý công việc và lịch trình cá nhân theo kiến trúc **microservices**. Mục tiêu của dự án là giúp người dùng:

- Tạo, theo dõi và quản lý các công việc (task) theo trạng thái, mức độ ưu tiên và deadline.
- Xem thống kê tiến độ công việc theo ngày, tuần.
- Nhận thông báo đẩy (web push) khi công việc sắp đến hạn.
- Tương tác với **AI trợ lý lên lịch** bằng tiếng Việt, có khả năng đọc danh sách công việc thực tế của người dùng để đưa ra gợi ý.
- Quản lý hồ sơ người dùng, ảnh đại diện lưu trên Cloudinary.

Dự án được xây dựng hoàn toàn bằng **Spring Boot 3 / Java 21**, sử dụng **Spring Cloud** cho service discovery và routing, **PostgreSQL** cho lưu trữ dữ liệu, và **Docker Compose** để triển khai đồng bộ toàn bộ hệ thống.

---

## 2. Kiến trúc hệ thống

```
┌─────────────────────────────────────────────────────────┐
│                        CLIENT                           │
│          (Browser / Mobile / Frontend App)              │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP (port 8080)
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    API GATEWAY (8080)                   │
│   - JWT validation (HS384)                              │
│   - Route: /api/auth/** → auth-service                  │
│   - Route: /api/users/** → user-service                 │
│   - Route: /api/tasks/** → task-service                 │
│   - Route: /api/ai/**   → ai-service                   │
│   - CORS enforcement                                    │
└──────┬──────────┬──────────┬──────────┬─────────────────┘
       │          │          │          │
       ▼          ▼          ▼          ▼
  ┌─────────┐ ┌────────┐ ┌────────┐ ┌────────┐
  │  auth   │ │  user  │ │  task  │ │   ai   │
  │ service │ │service │ │service │ │service │
  │  :9004  │ │  :9001 │ │  :9007 │ │  :9006 │
  └────┬────┘ └───┬────┘ └───┬────┘ └───┬────┘
       │          │          │          │
       │    ┌─────┘          │          │  (Feign internal)
       │    │   ┌────────────┘◄─────────┘
       │    │   │
       ▼    ▼   ▼
  [auth_db][user_db][task_db][ai_db]   ← PostgreSQL DBs
       (6 databases riêng biệt)

              ┌─────────────────────────┐
              │   DISCOVERY SERVER      │
              │   Eureka - port 8761    │
              │ (Tất cả service đăng ký)│
              └─────────────────────────┘
```

**Đặc điểm kiến trúc:**
- **Microservices**: Mỗi domain (auth, user, task, AI) là một service độc lập.
- **API Gateway**: Điểm vào duy nhất từ phía client, xử lý JWT và routing.
- **Service Discovery**: Eureka Server cho phép các service tìm nhau qua tên logic (`lb://service-name`).
- **Database per Service**: Mỗi service có PostgreSQL riêng, đảm bảo loose coupling.
- **Internal API**: Các service gọi nhau qua path `/internal/**` không cần JWT.

---

## 3. Công nghệ sử dụng

| Thành phần | Công nghệ | Phiên bản |
|---|---|---|
| Ngôn ngữ | Java | 21 |
| Framework | Spring Boot | 3.3.5 |
| Build tool | Maven | 3.9.9 |
| Service Discovery | Spring Cloud Netflix Eureka | 2023.0.3 |
| API Gateway | Spring Cloud Gateway (WebFlux) | 2023.0.3 |
| Bảo mật | Spring Security + OAuth2 Resource Server | - |
| JWT | Nimbus JOSE JWT | 9.31 |
| ORM | Spring Data JPA / Hibernate | - |
| Database | PostgreSQL | 16 Alpine |
| Inter-service | Spring Cloud OpenFeign | 2023.0.3 |
| AI Integration | Spring AI (OpenAI) | 1.0.0 |
| AI Model | OpenAI GPT-3.5-Turbo | - |
| Lưu ảnh | Cloudinary SDK | 1.32.0 (http44) |
| Push Notification | Web Push VAPID | 5.1.1 (nl.martijndwars) |
| Containerization | Docker + Docker Compose | 3.8 |
| Utilities | Lombok | - |
| Common lib | `common-exception-lib` (internal) | 1.0.0 |

---

## 4. Cấu trúc thư mục

```
d:\Smart_Schedule\
├── .env                           # OPENAI_API_KEY, POSTGRES_PASSWORD, Cloudinary keys
├── docker-compose.yml             # Orchestration toàn bộ hệ thống
├── README.md
├── API.md                         # Tài liệu API đầy đủ
├── AI_CHAT_SPRING_AI_CODE.md      # Ghi chú triển khai AI chat
├── test-task-today.html           # Trang test thủ công
├── task-service/TEST_FREE_HOURS.sql
├── task-service/TEST_FREE_HOURS_LOGIC.md
│
├── discovery-server/
│   └── discovery-server/          # Eureka Service Registry
│
├── api-gateway/
│   └── api-gateway/               # Spring Cloud Gateway
│
├── auth-service/
│   └── auth-service/              # JWT Authentication & Login
│
├── user-service/
│   └── user-service/              # User CRUD + Photo (Cloudinary)
│
├── task-service/
│   └── task-service/              # Task Management + Analytics + Push Notification
│
├── ai-service/
│   └── ai-service/                # AI Chat (Spring AI + OpenAI)
│
├── order-service/
│   └── order-service/             # Demo: Order management
│
└── product-service/
    └── product-service/           # Demo: Product catalog
```

Mỗi service theo cấu trúc Maven chuẩn:
```
<service>/<service>/
  ├── src/main/java/com/microsv/...
  │   ├── controller/
  │   ├── service/
  │   ├── repository/
  │   ├── entity/
  │   ├── dto/
  │   ├── mapper/
  │   └── config/
  ├── src/main/resources/application.yml
  ├── Dockerfile
  └── pom.xml
```

---

## 5. Chi tiết từng Service

### 5.1 Discovery Server

| | |
|---|---|
| **Port** | 8761 |
| **Mục đích** | Eureka Service Registry - trung tâm đăng ký và khám phá service |

**Chức năng:** Tất cả các service khác đăng ký với Discovery Server khi khởi động. API Gateway tra cứu địa chỉ service theo tên logic (ví dụ: `lb://task-service`). Cấu hình standalone (không tự đăng ký vào chính nó).

**Health check:** `wget --spider http://localhost:8761/actuator/health`

---

### 5.2 API Gateway

| | |
|---|---|
| **Port** | 8080 (port duy nhất public) |
| **Mục đích** | Điểm vào duy nhất, xác thực JWT, routing, CORS |

**Bảng routing:**

| Prefix | Backend Service |
|---|---|
| `/api/auth/**` | `lb://auth-service` |
| `/api/users/**` | `lb://user-service` |
| `/api/tasks/**` | `lb://task-service` |
| `/api/ai/**` | `lb://ai-service` |
| `/api/orders/**` | `lb://order-service` |
| `/api/products/**` | `lb://product-service` |

**Public endpoints (không cần JWT):**
- `POST /api/auth/login`
- `POST /api/users/register`
- `/eureka/**`

**JWT:** Xác thực bằng `NimbusReactiveJwtDecoder` với thuật toán **HS384 (HMAC SHA-384)**. Claim `scope` chứa roles/permissions.

**CORS:** Cho phép origins: `localhost`, `127.0.0.1`, port 8000/3000, `file://`.

---

### 5.3 Auth Service

| | |
|---|---|
| **Port** | 9004 |
| **Database** | `auth_db` |
| **Mục đích** | Đăng nhập, tạo JWT, quản lý token bị thu hồi |

**Endpoints:**

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| `POST` | `/api/auth/login` | Không | Xác thực email/password, trả về JWT |

**JWT Token cấu trúc:**
```json
{
  "sub": "<userId>",
  "iss": "smart_schedule.com",
  "exp": "<now + 1 hour>",
  "jti": "<random UUID>",
  "email": "<user email>",
  "scope": "ROLE_USER read:tasks ..."
}
```

**Luồng đăng nhập:**
1. Nhận `email` + `password` từ client.
2. Gọi `GET /internal/users/{email}` trên **user-service** qua Feign để lấy thông tin user.
3. Verify password bằng `PasswordEncoder`.
4. Tạo JWT HS384, encode `scope` từ roles + permissions.
5. Lưu `jti` vào bảng `invalidated_token` khi logout.

**Database:**
```sql
CREATE TABLE invalidated_token (
  id          VARCHAR PRIMARY KEY,  -- jti claim
  token       TEXT,
  expiry_time TIMESTAMP
);
```

---

### 5.4 User Service

| | |
|---|---|
| **Port** | 9001 |
| **Database** | `user_db` |
| **Mục đích** | Quản lý người dùng, ảnh đại diện, tìm kiếm, thống kê |

**Database schema:**
```sql
users           (user_id, userName, password, email, profile, created_at)
roles           (role_id, role_name)          -- ADMIN | MANAGER | USER
permissions     (permission_id, permission_name)
users_roles     (user_id, role_id)
roles_permissions (role_id, permission_id)
```

**Endpoints công khai & nội bộ:**

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| `POST` | `/api/users/register` | Không | Đăng ký, tự gán `ROLE_USER` |
| `POST` | `/api/users/create` | Có | Admin tạo user |
| `GET` | `/api/users` | Có | Danh sách tất cả user |
| `PUT` | `/api/users/{userId}` | Có | Cập nhật thông tin |
| `DELETE` | `/api/users/{userId}` | Có | Xóa user |
| `GET` | `/api/users/counts` | Có | Tổng số user |
| `GET` | `/api/users/counts-register` | Có | User đăng ký trong tuần |
| `GET` | `/api/users/search?keyword=` | Có | Tìm kiếm (full-text + LIKE) |
| `POST` | `/api/users/upload` | Có | Upload ảnh đại diện lên Cloudinary |
| `GET` | `/api/users/photo` | Có | Lấy URL ảnh đại diện |
| `DELETE` | `/api/users/del` | Có | Xóa ảnh đại diện |
| `GET` | `/internal/users/{email}` | Không | (Internal) Lấy user bởi email |

**Tìm kiếm user:** sử dụng PostgreSQL `tsvector` full-text search, kết hợp `LIKE` fallback, giới hạn 50 kết quả, sort theo relevance + `created_at`.

**Ảnh đại diện:** Upload qua **Cloudinary**, lưu URL vào cột `profile` của bảng `users`.

---

### 5.5 Task Service

| | |
|---|---|
| **Port** | 9007 |
| **Database** | `task_db` |
| **Mục đích** | Quản lý công việc, thống kê, push notification, internal API cho AI |

**Database schema:**
```sql
tasks (
  task_id, title, description,
  deadline TIMESTAMPTZ,
  status   VARCHAR,   -- TODO | IN_PROGRESS | DONE
  priority VARCHAR,   -- HIGH | MEDIUM | LOW
  created_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  user_id BIGINT
)

push_subscriptions (
  subscription_id, endpoint, p256dh, auth, user_id
)

schedule_events (
  event_id, title, start_time, end_time, user_id,
  related_task_id FK -> tasks  -- stub cho tính năng tương lai
)
```

**Endpoints quản lý task:**

| Method | Path | Mô tả |
|---|---|---|
| `POST` | `/api/tasks` | Tạo task (status mặc định TODO, priority mặc định MEDIUM) |
| `GET` | `/api/tasks` | Lấy tất cả task của user hiện tại |
| `GET` | `/api/tasks/{taskId}` | Lấy task theo ID |
| `GET` | `/api/tasks/status/{status}` | Lọc theo trạng thái |
| `GET` | `/api/tasks/upcoming?hours=24` | Task TODO sắp đến deadline trong N giờ |
| `PUT` | `/api/tasks/{taskId}` | Cập nhật đầy đủ task |
| `PATCH` | `/api/tasks/{taskId}/status?status=` | Chỉ cập nhật trạng thái |
| `DELETE` | `/api/tasks/{taskId}` | Xóa task |
| `GET` | `/api/tasks/search?title=` | Tìm task theo tiêu đề |

**Endpoints hôm nay (timezone Asia/Bangkok UTC+7):**

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api/tasks/today` | Tất cả task có deadline hôm nay |
| `GET` | `/api/tasks/today/overdue` | Task quá hạn hôm nay (deadline qua, chưa DONE) |
| `GET` | `/api/tasks/today/completed` | Task hoàn thành hôm nay |

**Endpoints thống kê:**

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api/tasks/statistics` | Tổng quan: total, todo, in_progress, done, completion rate % |
| `GET` | `/api/tasks/statistics/weekly-status` | Số DONE/IN_PROGRESS/TODO trong tuần |
| `GET` | `/api/tasks/statistics/weekly-distribution` | Số task theo từng ngày trong tuần |
| `GET` | `/api/tasks/statistics/completion-before-deadline` | % task hoàn thành trước deadline trong tuần |
| `GET` | `/api/tasks/statistics/free-hours` | Giờ rảnh ước tính trong tuần (70h baseline) |
| `GET` | `/api/tasks/statistics/creation-timeline` | Số task tạo mỗi tuần trong 6 tuần gần nhất |
| `GET` | `/api/tasks/statistics/weekly-task-complete` | Số task hoàn thành theo ngày trong tuần |
| `GET` | `/api/tasks/statistics/weekly-task-priority` | Số task theo mức độ ưu tiên |
| `GET` | `/api/tasks/active-users/weekly` | Số user active trong tuần |
| `GET` | `/api/tasks/weekly` | Tổng task tạo trong tuần |

**Endpoints thông báo & nội bộ:**

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| `POST` | `/api/notifications/subscribe` | Có | Đăng ký web push (VAPID) |
| `GET` | `/internal/tasks` | Không (header: `userId`) | (Internal) Trả về task list cho AI service |

**Tính toán giờ rảnh (free hours):**
- Baseline: **70 giờ/tuần** (10h/ngày × 7 ngày).
- `TODO`: không tính vào giờ bận.
- `IN_PROGRESS`: bận từ `created_at` đến hiện tại.
- `DONE`: bận từ `created_at` đến `completed_at`.
- Giờ rảnh = 70h - tổng giờ bận.

**Web Push (VAPID):** Lưu subscription (endpoint + p256dh + auth key) mỗi browser. Gửi push notification cho task sắp đến deadline.

---

### 5.6 AI Service

| | |
|---|---|
| **Port** | 9006 |
| **Database** | `ai_db` |
| **Mục đích** | Trợ lý AI lên lịch bằng tiếng Việt, tích hợp OpenAI GPT-3.5-Turbo |

**Database schema:**
```sql
conversation_memory (
  chat_id         SERIAL PRIMARY KEY,
  conversation_id VARCHAR NOT NULL,
  role            VARCHAR NOT NULL,  -- USER | ASSISTANT | SYSTEM
  content         VARCHAR(3000),
  create_at       TIMESTAMP,
  user_id         BIGINT NOT NULL
)
```

**Endpoints:**

| Method | Path | Auth | Mô tả |
|---|---|---|---|
| `POST` | `/api/ai` | Có | Chat với AI (multipart: message, file, conversationId) |
| `GET` | `/api/ai/id` | Có | Lấy `conversationId` mới nhất của user |
| `GET` | `/api/ai?page=0&size=10` | Có | Lịch sử hội thoại dạng phân trang |

**Kiến trúc AI:**

```
Client gửi message
       │
       ▼
ChatAIServiceImpl (ChatClient với MessageChatMemoryAdvisor)
       │
       ▼
ChatMemoryServiceImpl.get(conversationId)
  → Gọi TaskClient.getUserTasks(userId)  ←──── task-service /internal/tasks
  → Prepend SystemMessage với danh sách task của user
  → Load lịch sử chat từ PostgreSQL (conversation_memory)
       │
       ▼
OpenAI GPT-3.5-Turbo
  (System prompt tiếng Việt: "Schedule Assistant")
       │
       ▼
Lưu response vào PostgreSQL   →   Trả về ChatAIConversationResponse
```

**System Prompt (PromptUtil.SYSTEM_PROMPT):**
- Ngôn ngữ: tiếng Việt.
- Persona: "Schedule Assistant" - trợ lý lên kế hoạch, quản lý thời gian.
- Scope giới hạn: chỉ trả lời về quản lý thời gian, lịch trình, công việc.
- Biết danh sách task thực tế của người dùng (inject vào context mỗi lần chat).

**Bộ nhớ hội thoại:** Persistent trong PostgreSQL, không mất khi restart. `conversationId` cho phép duy trì nhiều cuộc hội thoại song song.

---

### 5.7 Order Service (Demo)

| | |
|---|---|
| **Port** | 9002 |
| **Database** | `order_db` |
| **Mục đích** | Demo inter-service communication (Feign: order → user + product) |

**Endpoints:**
- `POST /api/orders` - Tạo đơn hàng (gọi user-service + product-service qua Feign)
- `GET /api/orders/{id}` - Lấy đơn hàng

---

### 5.8 Product Service (Demo)

| | |
|---|---|
| **Port** | 9003 |
| **Database** | `product_db` |
| **Mục đích** | Demo: quản lý sản phẩm, được gọi bởi order-service |

**Endpoints:**
- `POST /api/products` - Tạo sản phẩm
- `GET /api/products` - Danh sách sản phẩm
- `GET /api/products/{id}` - Lấy theo ID
- `POST /api/products/{id}/decrease-stock?quantity=` - Giảm tồn kho

---

## 6. Cơ sở dữ liệu

| Database | Service | Port (host) | Nội dung chính |
|---|---|---|---|
| `user_db` | user-service | 5432 | users, roles, permissions |
| `order_db` | order-service | 5433 | orders |
| `product_db` | product-service | 5434 | products |
| `task_db` | task-service | 5435 | tasks, push_subscriptions, schedule_events |
| `auth_db` | auth-service | 5436 | invalidated_token |
| `ai_db` | ai-service | 5437 | conversation_memory |

Tất cả: PostgreSQL 16 Alpine, `ddl-auto: update`, dialect PostgreSQLDialect.

---

## 7. Bảo mật & Xác thực

**Thuật toán JWT:** HS384 (HMAC SHA-384) với shared secret `jwt.secret`.

**Luồng xác thực:**

```
1. Client → POST /api/auth/login (email + password)
2. auth-service → user-service (internal Feign) → verify credentials
3. auth-service → tạo JWT HS384 (TTL: 1 giờ)
4. JWT trả về client
5. Client gửi kèm Authorization: Bearer <JWT> cho mọi request
6. API Gateway validate JWT → forward tới downstream service
7. Downstream service cũng validate JWT (double validation)
```

**Phân quyền:**
- Claim `scope` = space-separated roles + permissions. Ví dụ: `ROLE_ADMIN ROLE_USER read:tasks`
- Roles: `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_USER`
- Task delete và admin endpoints yêu cầu `ROLE_ADMIN`
- `ROLE_USER` là role mặc định khi đăng ký

**Internal API:**
- Path `/internal/**` được `permitAll` ở tất cả services
- Không đi qua Gateway (service-to-service direct call qua Feign)
- Nhận `userId` qua HTTP header thay vì JWT

---

## 8. Giao tiếp giữa các Service

Tất cả inter-service call dùng **Spring Cloud OpenFeign** với Eureka load balancing:

| Caller | Callee | Endpoint | Mục đích |
|---|---|---|---|
| auth-service | user-service | `GET /internal/users/{email}` | Lấy thông tin user khi login |
| ai-service | task-service | `GET /internal/tasks` (header: userId) | Inject task context vào AI |
| order-service | user-service | Feign | Lấy thông tin user khi tạo order |
| order-service | product-service | Feign | Lấy sản phẩm + giảm tồn kho |

---

## 9. Hạ tầng & Triển khai Docker

**File:** `docker-compose.yml` - Compose v3.8

**Network:** `smart-schedule-network` (bridge), subnet `172.28.0.0/16`

**Thứ tự khởi động (`depends_on`):**
1. `discovery-server` (healthcheck: actuator/health)
2. 6 PostgreSQL databases (healthcheck: `pg_isready`)
3. Các service nghiệp vụ (auth, user, task, ai, product, order)
4. `api-gateway` (cuối cùng, phụ thuộc tất cả)

**Port mapping:**

| Service | Host Port | Container Port |
|---|---|---|
| discovery-server | 8761 | 8761 |
| api-gateway | 8080 | 8080 |
| user-service | 9001 | 9001 |
| order-service | 9002 | 9002 |
| product-service | 9003 | 9003 |
| auth-service | 9004 | 9004 |
| ai-service | 9006 | 9006 |
| task-service | 9007 | 9007 |
| postgres-user-db | 5432 | 5432 |
| postgres-order-db | 5433 | 5432 |
| postgres-product-db | 5434 | 5432 |
| postgres-task-db | 5435 | 5432 |
| postgres-auth-db | 5436 | 5432 |
| postgres-ai-db | 5437 | 5432 |

**Volumes (persistent):**
`user-db-data`, `auth-db-data`, `task-db-data`, `ai-db-data`, `product-db-data`, `order-db-data`

**Dockerfile patterns:**
- **Simple** (task, ai, order, product, discovery, gateway): `FROM openjdk:21-jdk-slim`, copy JAR từ `target/`
- **Multi-stage** (user-service): `FROM maven:3.9.9 AS build` + `FROM eclipse-temurin:21-jre-alpine`, non-root user `spring`, built-in healthcheck

**Lệnh khởi động toàn bộ hệ thống:**
```bash
docker compose up -d
```

---

## 10. Tính năng nổi bật

### Quản lý Task toàn diện
- CRUD task với title, description, deadline, status (TODO/IN_PROGRESS/DONE), priority (HIGH/MEDIUM/LOW)
- Tự động ghi nhận `completed_at` khi chuyển sang DONE
- Lọc task theo trạng thái, deadline, từ khóa tiêu đề
- Hỗ trợ timezone Asia/Bangkok cho query "hôm nay"

### Thống kê nâng cao
- Tổng quan completion rate
- Phân bố task theo ngày trong tuần
- Completion timeline 6 tuần gần nhất
- **Ước tính giờ rảnh** dựa trên workload thực tế (70h/tuần baseline)
- Admin stats: active users, total tasks per week

### AI Trợ lý Lên lịch
- GPT-3.5-Turbo với persona tiếng Việt
- **Context-aware**: AI biết danh sách task thực tế của user qua Feign call
- Bộ nhớ hội thoại persistent (PostgreSQL)
- Hỗ trợ nhiều cuộc hội thoại song song (conversationId)
- Lịch sử chat phân trang

### Web Push Notification (VAPID)
- Đăng ký subscription từ browser
- Gửi push notification khi task sắp đến deadline
- Lưu subscription key (p256dh + auth) trong PostgreSQL

### User Management
- Đăng ký, đăng nhập, phân quyền RBAC (role + permission)
- Upload/delete ảnh đại diện qua Cloudinary
- Full-text search (PostgreSQL `tsvector`) theo username/email
- Thống kê đăng ký theo tuần

---

## 11. Luồng hoạt động chính

### Luồng đăng ký & đăng nhập
```
1. POST /api/users/register {email, password, userName}
   → user-service lưu user, gán ROLE_USER
2. POST /api/auth/login {email, password}
   → auth-service verify → trả về JWT (1 giờ)
3. Client lưu JWT, gửi kèm mọi request tiếp theo
```

### Luồng tạo và quản lý task
```
1. POST /api/tasks {title, description, deadline, priority}
   → task-service tạo task (status=TODO), lưu DB
2. PATCH /api/tasks/{id}/status?status=IN_PROGRESS
   → Cập nhật trạng thái
3. PATCH /api/tasks/{id}/status?status=DONE
   → Cập nhật + ghi completed_at = now()
4. GET /api/tasks/statistics
   → Xem tổng quan completion rate
```

### Luồng chat với AI
```
1. POST /api/ai {message: "Tôi có bao nhiêu task chưa làm?", conversationId: "..."}
   → ai-service nhận request
   → Gọi task-service /internal/tasks để lấy danh sách task của user
   → Build context: [SystemPrompt + TaskList] + [Lịch sử chat] + [Message mới]
   → Gửi OpenAI GPT-3.5-Turbo
   → Lưu Q&A vào conversation_memory
   → Trả về response
```

### Luồng push notification
```
1. POST /api/notifications/subscribe {endpoint, p256dh, auth}
   → task-service lưu subscription
2. (Background) task-service quét task sắp đến deadline
   → Gửi web push đến endpoint đã đăng ký
```

---

## 12. Cấu hình môi trường

**File `.env` (bắt buộc):**
```env
OPENAI_API_KEY=sk-...
POSTGRES_PASSWORD=password123
CLOUDINARY_CLOUD_NAME=...
CLOUDINARY_API_KEY=...
CLOUDINARY_API_SECRET=...
```

**JWT Secret** (hiện hardcode trong `application.yml`):
```
A9mK2pX8vS4rW7tF1yD3bJ6nL0zQ5hG2eR9cU4xT8sV1wM7oP3lY6jN5iC
```
> **Lưu ý bảo mật:** Cần chuyển JWT secret vào `.env` và không commit lên git.

**VAPID Keys** (task-service `application.yml`):
- Public key: `BFG7k2HpM5dHTwC2Noi-EMKSR1MUpUdQa2SrqufF1CFVoP6NlGw-V3Zgf6A90yCYBsHwSFMukGuO9tVHZWSWWhI`
- Private key: configured in application.yml

---

## 13. Hạn chế & Điểm cần cải thiện

| Vấn đề | Ghi chú |
|---|---|
| JWT secret hardcoded | Cần đưa vào `.env` và biến môi trường |
| VAPID keys hardcoded | Cần đưa vào `.env` hoặc Vault |
| File upload AI chưa xử lý | `multipart file` trong `/api/ai` được nhận nhưng chưa có logic xử lý |
| `schedule_events` chưa có API | Entity và bảng đã tạo nhưng không có controller/service |
| Notification unsubscribe bị comment | Chức năng hủy đăng ký push notification chưa hoàn thiện |
| Double JWT validation | Gateway và service đều validate JWT (có thể bỏ ở service nếu tin Gateway) |
| Order/Product service là demo | Không có đầy đủ business logic, chỉ minh họa Feign communication |
| Common lib chưa trong repo | `common-exception-lib:1.0.0` cần cài sẵn trong Maven local |
| AI model: GPT-3.5-Turbo | Có thể nâng cấp lên GPT-4o cho chất lượng tốt hơn |

---

*Tài liệu này được tạo tự động bằng cách phân tích toàn bộ source code và cấu hình của dự án.*
