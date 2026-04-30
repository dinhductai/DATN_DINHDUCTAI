# Event Service - Kiến Trúc Nâng Cấp Hệ Thống

## Mục lục
1. [Tổng quan hệ thống hiện tại](#1-tổng-quan-hệ-thống-hiện-tại)
2. [Vấn đề cần giải quyết](#2-vấn-đề-cần-giải-quyết)
3. [Đề xuất giải pháp](#3-đề-xuất-giải-pháp)
4. [Thiết kế Database](#4-thiết-kế-database)
5. [Luồng xử lý Event](#5-luồng-xử-lý-event)
6. [Message Queue Design](#6-message-queue-design)
7. [API Endpoints](#7-api-endpoints)
8. [Implementation Plan](#8-implementation-plan)

---

## 1. Tổng quan hệ thống hiện tại

### 1.1 Các Services hiện tại

| Service | Port | Database | Chức năng |
|---------|------|----------|-----------|
| gateway-service | 8888 | - | API Gateway |
| user-service | 8080 | user_service | Quản lý users |
| task-service | 8081 | task_service | Quản lý tasks |
| notification-service | 8082 | notification_service | Gửi notifications |
| email-service | 8083 | email_service | Gửi emails |
| ai-service | 8084 | ai_service | AI chat |
| file-service | 8085 | file_service | Lưu trữ files |
| chat-service | 8086 | chat_service | Real-time chat |

### 1.2 Task Entity hiện tại

```sql
CREATE TABLE tasks (
    task_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    deadline TIMESTAMP,
    status VARCHAR(50),
    priority VARCHAR(50),
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 1.3 Email Entity hiện tại

```sql
CREATE TABLE emails (
    id BIGSERIAL PRIMARY KEY,
    sender VARCHAR(255) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(500),
    content TEXT,
    status VARCHAR(50),
    sent_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 1.4 RabbitMQ Configuration hiện tại

| Queue | Exchange | Routing Key | Chức năng |
|-------|----------|-------------|-----------|
| email.notification.queue | email.exchange | email.notification | Gửi email thông báo |

---

## 2. Vấn đề cần giải quyết

### 2.1 Hạn chế hiện tại

1. **Task chứa cả Event**: Không phải task nào cũng là event → lãng phí storage
2. **Thông tin Event không đầy đủ**: Thiếu link, location, event_description
3. **InvitorEmail chưa có bảng riêng**: Chưa có cách lưu danh sách người được mời
4. **Task-service phải đợi Email-service**: Coupled, không async hoàn toàn
5. **Email content không đa dạng**: Chỉ gửi notification đơn giản

### 2.2 Yêu cầu mới

- Tách Event thành bảng riêng (1:1 với Task)
- InvitorEmail lưu danh sách người được mời (1:N với Event)
- Task-service gửi message → Email-service tự xử lý (decouple)
- Email content bao gồm: link, description, location

---

## 3. Đề xuất giải pháp

### 3.1 Kiến trúc tổng quan mới

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           DATABASE RELATIONSHIP                               │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌─────────────┐         ┌─────────────────────┐                              │
│  │    Task     │ 1 ─── 1 │       Event        │ 1 ─── N │  InvitorEmail     │
│  ├─────────────┤         ├─────────────────────┤         ├─────────────────────│
│  │ task_id (PK)│────────▶│ task_id (FK)       │◄────────│ event_id (FK)      │
│  │ title       │         │ event_id (PK)      │         │ id (PK)           │
│  │ user_id     │         │ event_description  │         │ email             │
│  │ deadline    │         │ link_event         │         │ invitation_status │
│  │ status      │         │ location           │         │ created_at        │
│  │ created_at  │         │ is_online          │         └─────────────────────┘
│  │ updated_at  │         └─────────────────────┘
│  └─────────────┘                                                                │
│                                                                               │
└──────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐           ┌──────────────────┐           ┌──────────────────┐
│  Task-Service    │──────────▶│     RabbitMQ     │──────────▶│  Email-Service   │
│  (Producer)      │           │                  │           │  (Consumer)      │
└──────────────────┘           └──────────────────┘           └──────────────────┘
        │                                                              │
        │  Khi tạo Event:                                              │
        │  1. Tạo Task trước                                          │
        │  2. Tạo Event (link với Task)                               │
        │  3. Tạo InvitorEmails (link với Event)                      │
        │  4. Schedule reminder                                       │
        │                                                              ▼
        │                                                     ┌──────────────────┐
        │                                                     │   Email Table    │
        │                                                     │ (lưu email đã gửi)│
        └────────────────────────────────────────────────────▶└──────────────────┘
```

**Event kế thừa thông tin từ Task:**
- `title`, `user_id`, `created_at`, `updated_at` → lấy từ Task
- `event_description`, `link_event`, `location`, `is_online` → lưu trong Event

### 3.2 Các thành phần mới cần tạo

| Thành phần | Service | Mô tả |
|------------|---------|-------|
| Event Entity | task-service | Lưu thông tin event mở rộng |
| InvitorEmail Entity | task-service | Lưu danh sách người được mời |
| EventController | task-service | API CRUD cho Event |
| EventProducer | task-service | Gửi message qua RabbitMQ |
| EmailConsumer | email-service | Nhận message từ RabbitMQ |

---

## 4. Thiết kế Database

### 4.1 Task Table (Giữ nguyên)



### 4.2 Event Table (MỚI)

**Lưu ý quan trọng:**
- Event KHÔNG có `created_at, updated_at, user_id` → lấy từ Task (1:1 relationship)
- Khi tạo Event, hệ thống phải tạo Task TRƯỚC, rồi mới tạo Event
- Event chỉ lưu các thông tin RIÊNG của event (link, location, event_description)

```sql
CREATE TABLE events (
    event_id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL UNIQUE,
    event_description TEXT,
    link_event VARCHAR(500),
    location VARCHAR(500),
    is_online BOOLEAN DEFAULT FALSE,
    reminder_minutes_before INTEGER DEFAULT 30,
    CONSTRAINT fk_task FOREIGN KEY (task_id) REFERENCES tasks(task_id) ON DELETE CASCADE
);

CREATE INDEX idx_events_task_id ON events(task_id);
CREATE INDEX idx_events_is_online ON events(is_online);
```

**Event kế thừa từ Task:**
| Thuộc tính | Nguồn |
|------------|-------|
| title | Task.title |
| user_id | Task.user_id |
| created_at | Task.created_at |
| deadline | Task.deadline |
| status | Task.status |

### 4.3 InvitorEmail Table (MỚI)

```sql
CREATE TABLE invitor_emails (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_event FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE CASCADE
);

CREATE INDEX idx_invitor_emails_event_id ON invitor_emails(event_id);
CREATE INDEX idx_invitor_emails_email ON invitor_emails(email);
```


---

## 5. Luồng xử lý Event

### 5.1 Flow 1: Tạo Task có Event (POST /api/tasks với isEvent=true)

```
User                    Task-Service                RabbitMQ                 Email-Service
 │                            │                          │                          │
 │  POST /api/events         │                          │                          │
 │  (chứa thông tin Task      │                          │                          │
 │   + Event + InvitorEmails)│                          │                          │
 │───────────────────────────▶│                          │                          │
 │                            │                          │                          │
 │                            │ 1. Tạo TASK              │                          │
 │                            │    (title, description,  │                          │
 │                            │     deadline, user_id)    │                          │
 │                            │                          │                          │
 │                            │ 2. Tạo EVENT             │                          │
 │                            │    (liên kết với Task)   │                          │
 │                            │                          │                          │
 │                            │ 3. Lưu INVITOR_EMAILS   │                          │
 │                            │    vào task_service DB   │                          │
 │                            │                          │                          │
 │  Response: Task + Event    │                          │                          │
 │◀───────────────────────────│                          │                          │
 │                            │                          │                          │
 │                            │ 4. Gửi message TẠO     │                          │
 │                            │    EVENT với:           │                          │
 │                            │    - eventId            │                          │
 │                            │    - taskId             │                          │
 │                            │    - subject (tiêu đề)  │                          │
 │                            │    - content (nội dung) │                          │
 │                            │    - invitedEmails      │ ← GỬI KÈM              │
 │                            │    - messageType        │                          │
 │                            │    (Không delay)         │                          │
 │                            │──────────────────────────▶│                          │
 │                            │                          │                          │
 │                            │                          │ 5. Deserialize message   │
 │                            │                          │ 6. Lưu InvitorEmails    │
 │                            │                          │    vào email_service DB │
 │                            │                          │─────────────────────────▶│
```

**Lưu ý:**
- InvitorEmails được gửi KÈM trong message khi tạo event
- Email-Service lưu InvitorEmails vào database riêng để query khi gửi mail
- Message không delay → Email-Service nhận và lưu ngay

### 5.2 Flow 2: Thông báo trước Event

```
Scheduler               Task-Service                RabbitMQ                 Email-Service
 │                            │                          │                          │
 │  Trigger (đúng giờ đã     │                          │                          │
 │   schedule, ví dụ 5 phút   │                          │                          │
 │   trước event)             │                          │                          │
 │───────────────────────────▶│                          │                          │
 │                            │                          │                          │
 │                            │ 1. Query Event by taskId  │                          │
 │                            │ 2. Query Task để lấy    │                          │
 │                            │    title, user_id        │                          │
 │                            │                          │                          │
 │                            │ 3. Tạo message:         │                          │
 │                            │    - eventId             │                          │
 │                            │    - subject (tiêu đề)   │                          │
 │                            │    - content (nội dung)  │                          │
 │                            │    - messageType         │                          │
 │                            │    (KHÔNG gửi invitedEmails │
 │                            │     - đã lưu ở bước 5.1)  │                          │
 │                            │──────────────────────────▶│                          │
 │                            │                          │                          │
 │                            │                          │ 4. Deserialize message   │
 │                            │                          │ 5. Query InvitorEmails  │
 │                            │                          │    từ email_service DB   │
 │                            │                          │ 6. Gửi email cho từng   │
 │                            │                          │    invitor (dùng subject │
 │                            │                          │    & content từ message) │
 │                            │                          │─────────────────────────▶│
 │                            │                          │                          │
 │                            │                          │                          │ 7. Lưu vào email table
```

**Lưu ý:**
- InvitorEmails đã được lưu khi CREATED (Flow 5.1) → không cần gửi lại
- Email-Service query từ database riêng (email_service.invitor_emails)
- Nội dung email (subject, content) được Task-Service tạo sẵn

### 5.3 Flow 3: Cập nhật/Hủy Event

```
User                    Task-Service                RabbitMQ                 Email-Service
 │                            │                          │                          │
 │  PUT /api/tasks/{taskId}  │                          │                          │
 │  (request có thêm         │                          │                          │
 │   eventId non-required)    │                          │                          │
 │  hoặc                     │                          │                          │
 │  DELETE /api/tasks/{taskId}                          │                          │
 │───────────────────────────▶│                          │                          │
 │                            │                          │                          │
 │                            │ 1. Update/Delete Task   │                          │
 │                            │    theo request bình thường│                          │
 │                            │                          │                          │
 │                            │ 2. Nếu request có       │                          │
 │                            │    eventId:              │                          │
 │                            │    - Update/Delete      │                          │
 │                            │      Event tương ứng    │                          │
 │                            │                          │                          │
 │                            │ 3. LOGIC GỬI MESSAGE:   │                          │
 │                            │    - Sửa Task content   │                          │
 │                            │      (title, desc) →    │                          │
 │                            │      KHÔNG gửi message  │                          │
 │                            │    - Sửa Event content │                          │
 │                            │      (link, location) → │                          │
 │                            │      GỬI message        │                          │
 │                            │    - Xóa Event →        │                          │
 │                            │      GỬI message        │                          │
 │                            │                          │                          │
 │  Response                 │ 4. Gửi message:        │                          │
 │◀───────────────────────────│    - eventId           │                          │
 │                            │    - subject            │                          │
 │                            │    - content           │                          │
 │                            │    - messageType       │                          │
 │                            │    - invitedEmails      │ ← GỬI KÈM               │
 │                            │    (delay 15 phút)      │                          │
 │                            │──────────────────────────▶│                          │
 │                            │                          │                          │
 │                            │                          │ (Sau 15 phút)           │
 │                            │                          │ 5. Deserialize message  │
 │                            │                          │ 6. Query InvitorEmails  │
 │                            │                          │    từ email_service DB  │
 │                            │                          │ 7. Gửi email THÔNG BÁO │
 │                            │                          │─────────────────────────▶│
 │                            │                          │                          │
 │                            │                          │                          │ 8. Lưu vào email table
```

**Lưu ý:**
- Message gửi invitedEmails để Email-Service query đúng danh sách
- UPDATE: Nếu có thêm/bớt invitor → gửi danh sách mới trong message
- CANCELLED: Xóa InvitorEmails ở cả 2 database (task_service & email_service)
- Message delay 15 phút cho phép user undo nếu cần

---

## 6. Message Queue Design

### 6.1 RabbitMQ Configuration

#### Exchange và Queue hiện tại (Giữ nguyên)

| Exchange | Type | Queue | Routing Key | Chức năng |
|----------|------|-------|-------------|-----------|
| email.exchange | direct | email.notification.queue | email.notification | Gửi email thông báo |

#### Exchange và Queue mới

| Exchange | Type | Queue | Routing Key | TTL/Delay | Chức năng |
|----------|------|-------|-------------|-----------|-----------|
| event.exchange | direct | event.notification.queue | event.notification | - | Nhắc nhở trước event |
| event.exchange | direct | event.cancellation.queue | event.cancellation | 15 phút | Hủy/cập nhật event |

#### Message Properties

```yaml
# Cấu hình delay cho message hủy/cập nhật (15 phút)
rabbitmq:
  delay-queue:
    ttl: 900000  # 15 phút = 900000ms
    exchange: event.exchange
    routing-key: event.cancellation
```

**Giải thích:**
- REMINDER: Gửi ngay (không delay)
- UPDATED/CANCELLED: Delay 15 phút → cho phép user undo nếu cần

### 6.2 Message Structures

#### EventNotificationMessage

```java
public class EventNotificationMessage {
    private Long eventId;
    private Long taskId;
    private String subject;              // Tiêu đề email (do Task-Service tạo)
    private String content;             // Nội dung email (do Task-Service tạo)
    private List<String> invitedEmails; // Danh sách email người được mời
    private String messageType;         // "CREATED", "REMINDER", "UPDATED", "CANCELLED"
    private LocalDateTime createdAt;     // Thời điểm message được tạo
}
```

**Lưu ý:**
- InvitorEmails được gửi KÈM trong message khi tạo event
- Email-Service lưu InvitorEmails vào database riêng (email_service.invitor_emails)
- Email-Service query InvitorEmails từ database khi gửi mail (reminder/update/cancelled)

#### Message Types

| Type | Mô tả | Khi nào gửi |
|------|--------|--------------|
| CREATED | Event mới được tạo | Khi user tạo event (lưu invitedEmails) |
| REMINDER | Nhắc nhở trước event | 5-30 phút trước event |
| UPDATED | Event được cập nhật | Khi user update event |
| CANCELLED | Event bị hủy | Khi user xóa event |

---

## 7. API Endpoints

### 7.1 Task Endpoints (MỞ RỘNG - Task-Service)

**Lưu ý:** Không có API riêng cho Event. Tất cả operation liên quan đến Event đều mở rộng từ API Task hiện tại.

#### Tạo Task/Event

```
POST /api/tasks

Request Body:
{
    "title": "Họp team tháng 4",
    "description": "Tổng kết công việc tháng 3 và kế hoạch tháng 4",
    "deadline": "2026-04-29T14:00:00",
    "priority": "HIGH",
    "userId": 1,
    "isEvent": true,                    // ← THÊM: nếu true thì tạo event
    "eventDescription": "Cuộc họp định kỳ hàng tháng của team",
    "linkEvent": "https://zoom.us/j/123456789",
    "location": "Phòng A, Tầng 5",
    "isOnline": true,
    "reminderMinutesBefore": 30,
    "invitedEmails": [                 // ← THÊM: danh sách người được mời
        "an@example.com",
        "binh@example.com",
        "cuong@example.com"
    ]
}

Response (nếu isEvent=true):
{
    "taskId": 123,
    "eventId": 456,
    "title": "Họp team tháng 4",
    "deadline": "2026-04-29T14:00:00",
    "isEvent": true,
    "eventDescription": "Cuộc họp định kỳ hàng tháng của team",
    "linkEvent": "https://zoom.us/j/123456789",
    "location": "Phòng A, Tầng 5",
    "message": "Task with event created successfully"
}

Response (nếu isEvent=false hoặc không có):
{
    "taskId": 123,
    "title": "Tác vụ thường",
    "message": "Task created successfully"
}

Xử lý phía backend:
1. Tạo Task (title, description, deadline, priority, userId)
2. Nếu isEvent=true:
   - Tạo Event (taskId, eventDescription, linkEvent, location, isOnline)
   - Lưu InvitorEmails vào task_service database
   - Gửi message CREATED + invitedEmails → Email-Service (Email-Service lưu vào email_service database)
```

#### Lấy Task (có thể có Event)

```
GET /api/tasks/{taskId}

Response (nếu task có event):
{
    "taskId": 123,
    "title": "Họp team tháng 4",
    "description": "Tổng kết...",
    "deadline": "2026-04-29T14:00:00",
    "status": "PENDING",
    "userId": 1,
    "isEvent": true,
    "eventId": 456,
    "eventDescription": "Cuộc họp định kỳ...",
    "linkEvent": "https://zoom.us/j/123456789",
    "location": "Phòng A",
    "isOnline": true,
    "invitedEmails": ["an@example.com", "binh@example.com"]
}

Response (nếu task không có event):
{
    "taskId": 123,
    "title": "Tác vụ thường",
    ...
    "isEvent": false
}
```

#### Cập nhật Task/Event

```
PUT /api/tasks/{taskId}

Request Body (chỉ cập nhật Task):
{
    "title": "Tiêu đề mới",
    "description": "Mô tả mới",
    "deadline": "2026-04-30T15:00:00"
}
→ Không gửi message

Request Body (cập nhật Event - có eventId):
{
    "title": "Tiêu đề mới",
    "eventId": 456,                    // ← THÊM: bắt buộc nếu muốn update event
    "eventDescription": "Mô tả event mới",
    "linkEvent": "https://zoom.us/j/newlink",
    "location": "Phòng B"
}
→ Gửi message UPDATED + invitedEmails (delay 15 phút)

Request Body (thêm Event vào Task đã có):
{
    "title": "Tiêu đề mới",
    "addEvent": true,                   // ← THÊM: tạo event mới cho task
    "eventDescription": "Mô tả event",
    "linkEvent": "https://zoom.us/j/123",
    "location": "Phòng C",
    "invitedEmails": ["a@example.com"]
}
→ Tạo Event + gửi message CREATED

Response:
{
    "taskId": 123,
    "eventId": 456,                     // có nếu có event
    "message": "Task updated successfully"
}
```

#### Xóa Task/Event

```
DELETE /api/tasks/{taskId}
→ Xóa Task (không xóa event)

DELETE /api/tasks/{taskId}?eventId=456
→ Xóa Task + Event + InvitorEmails (ở cả task_service và gửi message để email_service xóa)
→ Gửi message CANCELLED (delay 15 phút)

Response:
{
    "message": "Task deleted successfully"
    // hoặc "message": "Task and event deleted, notification will be sent in 15 minutes"
}
```

---

## 8. Implementation Plan

### 8.1 Phase 1: Tạo Database & Entity (Hoàn thành)

- [x] Tạo bảng `events` trong `task_service` database
- [x] Tạo bảng `event_invitations` trong `email_service` database
- [x] Tạo entity `Event` ở task-service
- [x] Tạo entity `EventInvitation` ở email-service

### 8.2 Phase 2: Tạo/Update Event (Hoàn thành)

- [x] Thêm API tạo event trong TaskController
- [x] Thêm API update event trong TaskController
- [x] Thêm RabbitMQ queue `event-creation-queue`
- [x] Tạo EventCreationMessage DTO
- [x] Tạo EventCreationListener ở email-service
- [x] Lưu invitedEmails vào email_service DB khi tạo event
- [x] Gửi message CREATED khi tạo event
- [x] Thêm RabbitMQ queue `event-update-queue`
- [x] Tạo EventUpdateMessage DTO
- [x] Tạo EventUpdateListener ở email-service
- [x] Gửi message UPDATED khi update event

### 8.3 Phase 3: Xóa Event (Hoàn thành)

- [x] Thêm RabbitMQ queue `event-delete-queue`
- [x] Tạo EventDeleteMessage DTO (ở cả task-service và email-service)
- [x] Tạo EventDeleteListener ở email-service
- [x] Thêm method `deleteInvitations` trong EventInvitationService
- [x] Gửi message CANCELLED khi xóa event
- [x] Cập nhật TaskServiceImpl.deleteTask gửi eventId

### 8.4 Phase 4: Nhắc nhở Event (TBD)

- [x] Thiết kế reminder message structure
- [ ] Tạo RabbitMQ queue `event-reminder-queue`
- [ ] Tạo EventReminderMessage DTO
- [ ] Tạo scheduler job ở task-service để trigger reminder
- [ ] Tạo EventReminderListener ở email-service
- [ ] Gửi message REMINDER khi đến giờ nhắc nhở

---

## 9. Chi tiết Message Queue thực tế

### 9.1 Exchange & Queue Configuration

#### task-service (Producer)

| Queue | Exchange | Routing Key | Type | Chức năng |
|-------|----------|-------------|------|-----------|
| event-creation-queue | event-exchange | event.creation | direct | Tạo event mới |
| event-update-queue | event-exchange | event.update | direct | Cập nhật event |
| event-delete-queue | event-exchange | event.delete | direct | Xóa event |

#### email-service (Consumer)

| Queue | Exchange | Routing Key | Listener | Chức năng |
|-------|----------|-------------|----------|-----------|
| event-creation-queue | event-exchange | event.creation | EventCreationListener | Nhận tạo event |
| event-update-queue | event-exchange | event.update | EventUpdateListener | Nhận cập nhật event |
| event-delete-queue | event-exchange | event.delete | EventDeleteListener | Nhận xóa event |

### 9.2 Message DTOs

#### EventCreationMessage (task-service → email-service)

```java
@Data
@Builder
public class EventCreationMessage implements Serializable {
    private Long eventId;
    private Long taskId;
    private String subject;              // Tiêu đề email
    private String content;              // Nội dung email
    private List<String> invitedEmails;  // Danh sách người được mời
    private String messageType;          // "CREATED"
}
```

#### EventUpdateMessage (task-service → email-service)

```java
@Data
@Builder
public class EventUpdateMessage implements Serializable {
    private Long eventId;
    private String subject;               // Tiêu đề email mới
    private String content;               // Nội dung email mới
    private List<String> invitedEmails;   // Danh sách người được mời MỚI
    private String messageType;           // "UPDATED"
}
```

#### EventDeleteMessage (task-service → email-service)

```java
@Data
@Builder
public class EventDeleteMessage implements Serializable {
    private Long eventId;                 // eventId cần xóa
}
```

### 9.3 Message Type Summary

| Type | Trigger | Redis ZSET | InvitedEmails | Action ở email-service |
|------|---------|------------|---------------|------------------------|
| CREATED | Tạo event mới | Thêm reminder | Có | Lưu invitedEmails vào DB |
| UPDATED | Update event | Xóa cũ → Thêm mới | Có (danh sách mới) | Xóa cũ, lưu mới |
| CANCELLED | Xóa event | Xóa reminder | Không | Xóa invitedEmails theo eventId |

---

## 10. Complete Flow Diagrams

### 10.1 Flow Tạo Event (CREATED)

```
User          TaskController        TaskServiceImpl       EventRepository      RabbitMQ          Redis           EmailService
 │                   │                      │                    │                   │                │               │
 │  POST /api/tasks  │                      │                    │                   │                │               │
 │  (isEvent=true,   │                      │                    │                   │                │               │
 │   invitedEmails)  │                      │                    │                   │                │               │
 │──────────────────▶│                      │                    │                   │                │               │
 │                   │                      │                    │                   │                │               │
 │                   │ createTask(request)  │                    │                   │                │               │
 │                   │─────────────────────▶│                    │                   │                │               │
 │                   │                      │                    │                   │                │               │
 │                   │                      │ 1. Tạo Task       │                    │                │               │
 │                   │                      │───────────────────▶│                   │                │               │
 │                   │                      │◀───────────────────│ OK                │                │               │
 │                   │                      │                    │                   │                │               │
 │                   │                      │ 2. Tạo Event      │                    │                │               │
 │                   │                      │───────────────────▶│                   │                │               │
 │                   │                      │◀───────────────────│ OK                │                │               │
 │                   │                      │                    │                   │                │               │
 │                   │                      │ 3. Gửi message    │                    │                │               │
 │                   │                      │    CREATED        │                    │                │               │
 │                   │                      │──────────────────────────────────────────▶│               │
 │                   │                      │                    │                   │                │               │
 │                   │                      │ 4. Tính reminderTime = deadline - reminderMinutesBefore│             │
 │                   │                      │ 5. Lưu vào Redis ZSET         │                │               │
 │                   │                      │    ZADD event:reminders <epoch> <eventId>│              │
 │                   │                      │                    │                   │◀──────────────│               │
 │                   │                      │                    │                   │                │               │
 │                   │ Response (Task+Event) │                    │                   │                │               │
 │◀──────────────────│                      │                    │                   │                │               │


EmailService
 │
 │  6. EventCreationListener nhận message
 │  7. Lưu invitedEmails vào email_service.event_invitations
 │  8. Log: "Event created, invitations saved"
```

### 10.2 Flow Update Event (UPDATED)

```
User          TaskController        TaskServiceImpl       EventRepository      Redis         RabbitMQ          EmailService
 │                   │                      │                    │                │               │               │
 │  PUT /api/tasks/{taskId}  │              │                    │                │               │                 │
 │  (eventId, updated info,  │              │                    │                │               │                 │
 │   invitedEmails mới)     │              │                    │                │               │                 │
 │─────────────────────────▶│               │                    │                │               │                 │
 │                   │                      │                    │                │               │                 │
 │                   │ updateTask(...)     │                    │                │               │                 │
 │                   │─────────────────────▶│                    │                │               │                 │
 │                   │                      │                    │                │               │                 │
 │                   │                      │ 1. Update Task     │                │               │                 │
 │                   │                      │ 2. Update Event     │                │               │                 │
 │                   │                      │                    │                │               │                 │
 │                   │                      │ 3. Xóa reminder CŨ khỏi Redis ZSET│            │                 │
 │                   │                      │    ZREM event:reminders <eventId>  │               │                 │
 │                   │                      │─────────────────────▶                │               │                 │
 │                   │                      │                    │                │               │                 │
 │                   │                      │ 4. Tính reminderTime MỚI           │               │                 │
 │                   │                      │    = deadline - reminderMinutesBefore│               │                 │
 │                   │                      │ 5. Lưu reminder MỚI vào Redis ZSET │               │                 │
 │                   │                      │    ZADD event:reminders <new_epoch> <eventId>│              │                 │
 │                   │                      │                    │                │               │                 │
 │                   │                      │ 6. Gửi message    │                │               │                 │
 │                   │                      │    UPDATED        │                │               │                 │
 │                   │                      │─────────────────────────────────────────▶│               │                 │
 │                   │                      │                    │                │               │                 │
 │                   │ Response             │                    │                │               │                 │
 │◀─────────────────────────────────────────│                    │                │               │                 │
 │                   │                      │                 │               │                 │


EmailService
 │
 │  4. EventUpdateListener nhận message
 │  5. updateInvitations(message):
 │     - Xóa tất cả invitation cũ của eventId
 │     - Lưu danh sách invitedEmails MỚI vào DB
 │  6. Log: "Invitations updated for eventId"
```

### 10.3 Flow Xóa Event (CANCELLED)

```
User          TaskController        TaskServiceImpl       EventRepository      RabbitMQ          EmailService
 │                   │                      │                    │                 │               │
 │  DELETE /api/tasks/{taskId}             │                    │                 │               │
 │  ?eventId=456     │                      │                    │                 │               │
 │──────────────────▶│                      │                    │                 │               │
 │                   │                      │                    │                 │               │
 │                   │ deleteTask(taskId,   │                    │                 │               │
 │                   │              eventId,│                    │                 │               │
 │                   │              userId) │                    │                 │               │
 │                   │─────────────────────▶│                    │                 │               │
 │                   │                      │                    │                 │               │
 │                   │                      │ 1. Tìm Task        │                    │               │
 │                   │                      │ 2. Tìm Event      │                    │               │
 │                   │                      │ 3. Xóa Event reminder khỏi Redis│              │
 │                   │                      │ 4. Xóa Event DB   │                    │               │
 │                   │                      │ 5. Xóa Task       │                    │               │
 │                   │                      │                    │                 │               │
 │                   │                      │ 6. Gửi message    │                    │               │
 │                   │                      │    CANCELLED      │                    │               │
 │                   │                      │    (chỉ có eventId)│                   │               │
 │                   │                      │────────────────────────────────────────▶│
 │                   │                      │                    │                 │               │
 │                   │ Response             │                    │                 │               │
 │◀─────────────────────────────────────────│                    │                 │               │
 │                   │                      │                    │                 │               │


EmailService
 │
 │  7. EventDeleteListener nhận message
 │  8. deleteInvitations(message):
 │     - Tìm tất cả invitation theo eventId
 │     - Xóa khỏi DB
 │  9. Log: "Deleted N invitations for eventId"
```

---

## 11. Các File đã triển khai

### 11.1 task-service

| File | Mô tả |
|------|-------|
| `entity/Event.java` | Event entity |
| `repository/EventRepository.java` | Event repository |
| `dto/request/EventCreationRequest.java` | Request DTO khi tạo event |
| `dto/request/EventUpdateRequest.java` | Request DTO khi update event |
| `dto/message/EventCreationMessage.java` | Message gửi khi tạo event |
| `dto/message/EventUpdateMessage.java` | Message gửi khi update event |
| `dto/message/EventDeleteMessage.java` | Message gửi khi xóa event |
| `messaging/EventEmailProducer.java` | Producer gửi message |
| `service/impl/EventServiceImpl.java` | Logic xử lý event |
| `config/RabbitMQConfig.java` | Cấu hình queue (thêm event-*) |
| `service/impl/TaskServiceImpl.java` | deleteTask gửi message CANCELLED |

### 11.2 email-service

| File | Mô tả |
|------|-------|
| `entity/EventInvitation.java` | Invitation entity |
| `repository/EventInvitationRepository.java` | Invitation repository |
| `dto/message/EventCreationMessage.java` | Message nhận khi tạo event |
| `dto/message/EventUpdateMessage.java` | Message nhận khi update event |
| `dto/message/EventDeleteMessage.java` | Message nhận khi xóa event |
| `messaging/EventCreationListener.java` | Consumer nhận CREATED |
| `messaging/EventUpdateListener.java` | Consumer nhận UPDATED |
| `messaging/EventDeleteListener.java` | Consumer nhận CANCELLED |
| `service/EventInvitationService.java` | Logic xử lý invitations |
| `config/RabbitMQConfig.java` | Cấu hình queue (thêm event-*) |