# Smart Schedule API Reference

> Base URL (qua Gateway): `http://localhost:8080`
>
> Auth header (hầu hết API): `Authorization: Bearer <JWT>`
>
> Gateway cho phép public: `POST /api/auth/login`, `POST /api/users/register`.

## Quy ước chung

- Content-Type JSON: `application/json`
- Upload file: `multipart/form-data`
- Lỗi auth ở Gateway (khi thiếu/sai JWT):
  - `401 Unauthorized`
  - `403 Forbidden` (khi token hợp lệ nhưng không đủ quyền, nếu có rule)
- Một số service (user/task) ném `BaseException` từ `common-exception-lib` (không thấy `@ControllerAdvice` trong repo). HTTP status/format lỗi phụ thuộc mapping trong lib; thường gặp:
  - `400 Bad Request` (INVALID_INPUT/BAD_REQUEST/validate)
  - `404 Not Found` (USER_NOT_FOUND/ROLE_NOT_FOUND…)
  - `500 Internal Server Error` (DATABASE_QUERY_ERROR)

---

## Auth Service

### `POST /api/auth/login`
- Auth: **No**
- Request (JSON) — `AuthenticationRequest`
  - `email` (string, email, required)
  - `password` (string, required)
- Response: `200 OK` — `AuthenticationResponse`
  - `token` (string)
  - `authenticated` (boolean)
- Errors (discoverable):
  - `400 Bad Request` (validation fail từ `@Email/@NotBlank` nếu được bật)
  - `500 Internal Server Error` (controller đang `throw new RuntimeException(...)` khi auth fail/JOSE error)

---

## User Service

### `POST /api/users/register`
- Auth: **No**
- Request (JSON) — `UserCreationRequest`
  - `userName` (string, min 3)
  - `password` (string, min 6)
  - `email` (string, email)
  - `profile` (string)
- Response: `201 Created` — `UserResponse`
  - `userId` (number)
  - `userName` (string)
  - `email` (string)
  - `profile` (string)
  - `roles` (string[])
- Errors (discoverable): qua `BaseException` (email trùng, role missing, DB…)

### `POST /api/users/create`
- Auth: **Yes**
- Request (JSON) — entity `User`
  - `userId` (number, optional)
  - `userName` (string)
  - `password` (string)
  - `email` (string)
  - `profile` (string)
- Response: `201 Created` — entity `User` (bao gồm `roles`…)
- Errors: qua `BaseException` (EMAIL_ALREADY_IN_USE, INVALID_INPUT, …)

### `GET /api/users`
- Auth: **Yes**
- Request: none
- Response: `200 OK` — `User[]`
- Errors: `BaseException(USER_NOT_FOUND)` nếu rỗng

### `PUT /api/users/{userId}`
- Auth: **Yes**
- Path params:
  - `userId` (number)
- Request (JSON) — `UserUpdateRequest`
  - `password` (string, min 6, optional)
  - `userName` (string, required)
  - `email` (string, email, required)
  - `profile` (string, optional)
- Response: `200 OK` — `UserResponse`
- Errors: `BaseException(USER_NOT_FOUND/EMAIL_ALREADY_IN_USE/…)`

### `DELETE /api/users/{userId}`
- Auth: **Yes**
- Response: `204 No Content`
- Errors: `BaseException(USER_NOT_FOUND/…)`

### `GET /api/users/counts`
- Auth: **Yes**
- Response: `200 OK` — number (tổng user)

### `GET /api/users/counts-register`
- Auth: **Yes**
- Response: `200 OK` — number (user đăng ký trong tuần)

### `GET /api/users/search?keyword=...`
- Auth: **Yes**
- Query params:
  - `keyword` (string)
- Response: `200 OK` — `User[]`

---

## User Photos (Cloudinary)

### `POST /api/users/upload`
- Auth: **Yes** (lấy `userId` từ `jwt.getSubject()`)
- Request: `multipart/form-data`
  - field `file`: `MultipartFile`
- Response: `200 OK` — string (URL ảnh)
- Errors: `500` (IOException hoặc lỗi upload)

### `GET /api/users/photo`
- Auth: **Yes**
- Response: `200 OK` — string (URL ảnh)

### `DELETE /api/users/del`
- Auth: **Yes**
- Response: `200 OK` (body rỗng)

---

## Task Service

### `POST /api/tasks`
- Auth: **Yes**
- Request (JSON) — `TaskCreationRequest`
  - `title` (string)
  - `description` (string)
  - `startTime` (string, OffsetDateTime)
  - `deadline` (string, OffsetDateTime)
  - `priority` (enum `PriorityLevel`, optional; default MEDIUM trong mapper)
- Response: `201 Created` — `TaskResponse`
  - `taskId`, `title`, `description`, `deadline`, `status`, `priority`, `createdAt`, `completedAt`, `userId`
- Errors (discoverable): `BaseException(DATABASE_QUERY_ERROR, …)`

### `GET /api/tasks`
- Auth: **Yes**
- Response: `200 OK` — `TaskResponse[]`

### `GET /api/tasks/{taskId}`
- Auth: **Yes**
- Response: `200 OK` — `TaskResponse`
- Errors: `BaseException(...)` (khi không tìm thấy task theo `taskId` + `userId`)

### `GET /api/tasks/status/{status}`
- Auth: **Yes**
- Path params:
  - `status` (enum `TaskStatus`: `TODO|IN_PROGRESS|DONE`)
- Response: `200 OK` — `TaskResponse[]`

### `GET /api/tasks/upcoming?hours=24`
- Auth: **Yes**
- Query params:
  - `hours` (int, optional, default 24)
- Response: `200 OK` — `TaskResponse[]` (lọc TODO có deadline trong khoảng)

### `GET /api/tasks/statistics`
- Auth: **Yes**
- Response: `200 OK` — `TaskStatisticResponse`
  - `totalTasks`, `todoCount`, `inProgressCount`, `doneCount` (number)
  - `completionRate` (number, %)

### `PUT /api/tasks/{taskId}`
- Auth: **Yes**
- Request (JSON) — `TaskUpdateRequest`
  - `title`, `description` (string)
  - `deadline` (OffsetDateTime)
  - `priority` (PriorityLevel)
  - `status` (TaskStatus)
- Response: `200 OK` — `TaskResponse`

### `PATCH /api/tasks/{taskId}/status?status=...`
- Auth: **Yes**
- Query params:
  - `status` (TaskStatus)
- Response: `200 OK` — `TaskResponse`

### `DELETE /api/tasks/{taskId}`
- Auth: **Yes**
- Response: `204 No Content`

### `GET /api/tasks/today`
- Auth: **Yes**
- Response: `200 OK` — `TaskResponse[]`

### `GET /api/tasks/today/overdue`
- Auth: **Yes**
- Response: `200 OK` — `TaskResponse[]`

### `GET /api/tasks/today/completed`
- Auth: **Yes**
- Response: `200 OK` — `TaskResponse[]`

### `GET /api/tasks/statistics/weekly-status`
- Auth: **Yes**
- Response: `200 OK` — `StatusTaskWeekResponse`
  - `completedRate`, `inProgressRate`, `todoRate` (number)

### `GET /api/tasks/statistics/weekly-distribution`
- Auth: **Yes**
- Response: `200 OK` — `DailyTaskCountResponse[]`
  - `dayName` (string), `taskCount` (number)

### `GET /api/tasks/statistics/completion-before-deadline`
- Auth: **Yes**
- Response: `200 OK` — number (Double)

### `GET /api/tasks/statistics/free-hours`
- Auth: **Yes**
- Response: `200 OK` — number (Double)
- Domain note: logic không tính `TODO` là busy; work-week = 70h. Tham khảo `task-service/TEST_FREE_HOURS_LOGIC.md`.

### `GET /api/tasks/statistics/creation-timeline`
- Auth: **Yes**
- Response: `200 OK` — `TaskTimelineResponse[]`
  - `weekLabel` (string), `taskCount` (number)

### `GET /api/tasks/active-users/weekly`
- Auth: **Yes**
- Response: `200 OK` — number

### `GET /api/tasks/weekly`
- Auth: **Yes**
- Response: `200 OK` — number

### `GET /api/tasks/statistics/weekly-task-complete`
- Auth: **Yes**
- Response: `200 OK` — `DailyCompletedTasksResponse[]`
  - `dayName` (string), `completedCount` (number)

### `GET /api/tasks/statistics/weekly-task-priority`
- Auth: **Yes**
- Response: `200 OK` — `TaskPriorityCountResponse[]`
  - `priorityLevel` (string), `taskCount` (number)

### `GET /api/tasks/search?title=...`
- Auth: **Yes**
- Query params:
  - `title` (string)
- Response: `200 OK` — entity `Task[]` (khác với `TaskResponse`)

---

## Notification (Web Push)

### `POST /api/notifications/subscribe`
- Auth: **Yes**
- Request (JSON) — `SubscriptionRequest`
  - `endpoint` (string)
  - `p256dh` (string)
  - `auth` (string)
- Response: `201 Created` (body rỗng)

---

## AI Service

### `POST /api/ai`
- Auth: **Yes**
- Request: `multipart/form-data`
  - `message` (string, optional)
  - `file` (file, optional)
  - `conversationId` (string, optional; nếu trống sẽ tự tạo UUID)
- Response: `200 OK` — `ChatAIConversationResponse`
  - `conversationId` (string)
  - `chatAIResponses` (string)

### `GET /api/ai/id`
- Auth: **Yes**
- Response: `200 OK` — string (`conversationId`)

### `GET /api/ai?page=0&size=10`
- Auth: **Yes**
- Query params:
  - `page` (int, default 0)
  - `size` (int, default 10)
- Response: `200 OK` — Spring Data `Page<ConversationMemory>`
  - `content[]`: các record `ConversationMemory` gồm `chatId`, `conversationId`, `role`, `content`, `createAt`, `userId`
  - kèm metadata paging (`totalElements`, `totalPages`, `number`, ...)

---

## Product Service

### `POST /api/products`
- Auth: **Yes**
- Request (JSON) — entity `Product`
  - `id` (number, optional)
  - `name` (string)
  - `description` (string)
  - `price` (number)
  - `stockQuantity` (number)
- Response: `201 Created` — `Product`

### `GET /api/products`
- Auth: **Yes**
- Response: `200 OK` — `Product[]`

### `GET /api/products/{id}`
- Auth: **Yes**
- Response: `200 OK` — `Product`
- Errors (discoverable): `404 Not Found` (controller catch RuntimeException và trả notFound)

### `POST /api/products/{id}/decrease-stock?quantity=...`
- Auth: **Yes**
- Query params:
  - `quantity` (int)
- Response: `200 OK` (body rỗng)
- Errors (discoverable): `400 Bad Request` (khi service ném RuntimeException)

---

## Order Service

### `POST /api/orders`
- Auth: **Yes**
- Request (JSON) — `OrderRequest`
  - `userId` (number)
  - `productId` (number)
  - `quantity` (int)
- Response: `201 Created` — `OrderResponse`
  - `orderId` (number)
  - `user` (`UserResponse`: `id`, `name`, `email`)
  - `product` (`ProductResponse`: `id`, `name`, `description`, `price`, `stockQuantity`)
- Errors (discoverable):
  - `400 Bad Request`: khi product-service trả 400 qua Feign (`FeignException.BadRequest`), body trả về `e.contentUTF8()`
  - `500 Internal Server Error`: lỗi runtime khác (ví dụ user không tồn tại…)

### `GET /api/orders/{id}`
- Auth: **Yes**
- Response: `200 OK` — `OrderResponse`
- Errors (discoverable): `404 Not Found` (catch Exception và trả notFound)

---

## Internal APIs (service-to-service, không qua Gateway)

> Các endpoint này được gọi nội bộ bằng Eureka/Feign (ví dụ: auth-service → user-service, ai-service → task-service).

### `GET /internal/users/{email}` (user-service)
- Auth: **Internal** (không dùng JWT ở controller)
- Response: `200 OK` — `UserAuthResponse`
  - `userId`, `userName`, `email`, `profile`, `password`, `roles[]`

### `GET /internal/tasks` (task-service)
- Auth: **Internal** (dùng header)
- Headers:
  - `userId` (number)
- Response: `200 OK` — `TaskResponse[]`
