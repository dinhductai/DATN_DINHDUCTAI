# FE Integration Guide — Event Feature

> Backend đã mở rộng hệ thống Task CRUD thành **Task + Event**. Tài liệu này mô tả đầy đủ các trường, endpoint, và flow để FE tích hợp tính năng tạo / cập nhật / xóa event.

---

## 1. Tổng quan

Mỗi **Event** gắn liền với một **Task**. Khi tạo task với `isEvent = true`, hệ thống tự động tạo event đi kèm.

```
Task (isEvent=true)  ──1:1──>  Event
                                 │
                                 ├── invitedEmails ──> EventInvitation (email_service)
                                 ├── Redis Scheduler ──> Gửi email reminder trước N phút
```

---

## 2. Enum & Reference Data

### 2.1 PriorityLevel
| Value | Mô tả |
|---|---|
| `HIGH` | Ưu tiên cao |
| `MEDIUM` | Trung bình (default) |
| `LOW` | Thấp |

### 2.2 TaskStatus
| Value | Mô tả |
|---|---|
| `TODO` | Chưa làm (default) |
| `IN_PROGRESS` | Đang làm |
| `DONE` | Hoàn thành |

---

## 3. API Endpoints

Base URL: `http://localhost:8080/api/tasks`

| Method | Endpoint | Mô tả |
|---|---|---|
| `POST` | `/api/tasks` | Tạo task (có thể kèm event) |
| `GET` | `/api/tasks` | Lấy tất cả task của user |
| `GET` | `/api/tasks/{taskId}` | Lấy task theo id |
| `PUT` | `/api/tasks/{taskId}` | Cập nhật task (có thể kèm event) |
| `PATCH` | `/api/tasks/{taskId}/status?status=TODO` | Chỉ cập nhật trạng thái |
| `DELETE` | `/api/tasks/{taskId}` | Xóa task (tự xóa event nếu có) |
| `DELETE` | `/api/tasks/{taskId}?eventId={eventId}` | Xóa task kèm event cụ thể |

**Auth:** Tất cả request cần header `Authorization: Bearer <JWT_TOKEN>`

---

## 4. Request / Response Schemas

### 4.1 Tạo Task thường (không phải Event)

**POST** `/api/tasks`

```json
{
  "title": "Hoàn thành báo cáo tháng",
  "description": "Tổng hợp số liệu và viết báo cáo tổng kết tháng 4",
  "deadline": "2026-05-15T17:00:00+07:00",
  "priority": "HIGH"
}
```

Response `201 Created`:
```json
{
  "taskId": 36,
  "title": "Hoàn thành báo cáo tháng",
  "description": "Tổng hợp số liệu và viết báo cáo tổng kết tháng 4",
  "deadline": "2026-05-15T10:00:00Z",
  "status": "TODO",
  "priority": "HIGH",
  "createdAt": "2026-04-30T07:00:00Z",
  "completedAt": null,
  "userId": 3,
  "isEvent": false,
  "eventId": null
}
```

### 4.2 Tạo Event

**POST** `/api/tasks`

```json
{
  "title": "Họp đầu năm mới 2026 - Chuẩn bị",
  "description": "Chuẩn bị tài liệu, chương trình nghị sự và phòng họp cho cuộc họp tổng kết đầu năm",
  "deadline": "2026-04-30T14:50:00+07:00",
  "priority": "HIGH",
  "isEvent": true,
  "eventCreationRequest": {
    "eventDescription": "Họp đầu năm mới 2026 - Tổng kết năm cũ và triển khai kế hoạch năm mới",
    "linkEvent": "https://meet.google.com/nte-hyir-onm?authuser=0",
    "isOnline": true,
    "reminderMinutesBefore": 30,
    "startTime": "2026-04-30T14:50:00+07:00",
    "invitedEmails": [
      "dinhductai2501@gmail.com",
      "gtaiducdinhm@gmail.com",
      "ai5mmotest@gmail.com",
      "taiduc2k4@gmail.com"
    ]
  }
}
```

Response `201 Created`:
```json
{
  "taskId": 35,
  "title": "Họp đầu năm mới 2026 - Chuẩn bị",
  "description": "Chuẩn bị tài liệu, chương trình nghị sự và phòng họp cho cuộc họp tổng kết đầu năm",
  "deadline": "2026-04-30T07:50:00Z",
  "status": "TODO",
  "priority": "HIGH",
  "createdAt": null,
  "completedAt": null,
  "userId": 3,
  "isEvent": true,
  "eventId": 5
}
```

**Lưu ý quan trọng:**
- `deadline` trong request = thời gian bắt đầu event (`startTime` của event)
- `deadline` trong response = `deadline` nhận vào, chuyển thành UTC
- `eventCreationRequest.eventDescription` = mô tả nội dung event (khác với task description)
- `eventCreationRequest.startTime` = thời gian bắt đầu event (nên trùng deadline)
- `invitedEmails` = danh sách email được mời (không cần thêm email người tạo, hệ thống tự xử lý)
- `reminderMinutesBefore` = số phút trước khi event bắt đầu để gửi email reminder (mặc định: 30)

### 4.3 Cập nhật Task / Event

**PUT** `/api/tasks/{taskId}`

```json
{
  "title": "Họp đầu năm mới 2026 - Chuẩn bị (cập nhật)",
  "description": "Mô tả đã được cập nhật",
  "deadline": "2026-05-01T14:50:00+07:00",
  "createdAt": "2026-05-01T14:00:00+07:00",
  "completedAt": null,
  "priority": "MEDIUM",
  "status": "IN_PROGRESS",
  "eventId": 5,
  "eventUpdateRequest": {
    "eventDescription": "Họp đầu năm mới 2026 - Tổng kết năm cũ",
    "linkEvent": "https://meet.google.com/new-link",
    "isOnline": true,
    "reminderMinutesBefore": 15,
    "invitedEmails": [
      "dinhductai2501@gmail.com",
      "gtaiducdinhm@gmail.com",
      "taiduc2k4@gmail.com"
    ]
  }
}
```

**Lưu ý:**
- `createdAt` = thời gian bắt đầu task/event (startTime). Khi thay đổi, hệ thống tự tính lại lịch reminder trong Redis
- `completedAt` = thời gian hoàn thành. Nếu gửi `null` hoặc không gửi → để trống
- Khi `status = DONE` và không gửi `completedAt` → tự động set = thời điểm hiện tại
- Khi `status != DONE` và gửi `completedAt = null` → xóa thời gian hoàn thành
- Khi thay đổi `reminderMinutesBefore` hoặc `createdAt`, hệ thống tự tính lại thời điểm gửi reminder trong Redis
- Khi thay đổi `invitedEmails`, hệ thống gửi message đến email-service để cập nhật danh sách lời mời
- Nếu `eventId` không thuộc về task này → trả về lỗi

### 4.4 Xóa Event (xóa task + event)

**DELETE** `/api/tasks/{taskId}`

Hoặc xóa task kèm event cụ thể:

**DELETE** `/api/tasks/{taskId}?eventId={eventId}`

---

## 5. Chi tiết các trường

### 5.1 TaskCreationRequest

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `title` | String | Có | Tiêu đề task |
| `description` | String | Không | Mô tả task |
| `deadline` | String (ISO 8601 OffsetDateTime) | Có | Thời hạn task. Khi là event, nên = `startTime` của event |
| `priority` | Enum: `HIGH`, `MEDIUM`, `LOW` | Không | Mặc định `MEDIUM` |
| `isEvent` | Boolean | Không | `true` = tạo kèm event |
| `eventCreationRequest` | Object | Có khi `isEvent=true` | Thông tin chi tiết event |

### 5.2 TaskUpdateRequest

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `title` | String | Không | Tiêu đề task |
| `description` | String | Không | Mô tả task |
| `deadline` | String (ISO 8601 OffsetDateTime) | Không | Thời hạn task |
| `createdAt` | String (ISO 8601 OffsetDateTime) | Không | Thời gian bắt đầu task/event (startTime). Thay đổi sẽ cập nhật lại lịch reminder |
| `completedAt` | String (ISO 8601 OffsetDateTime) | Không | Thời gian hoàn thành. `null` = chưa hoàn thành |
| `priority` | Enum: `HIGH`, `MEDIUM`, `LOW` | Không | Độ ưu tiên |
| `status` | Enum: `TODO`, `IN_PROGRESS`, `DONE` | Không | Trạng thái. Khi = `DONE` và `completedAt` không gửi → tự set thời gian hiện tại |
| `eventId` | Long | Không | ID event cần cập nhật (bắt buộc kèm `eventUpdateRequest`) |
| `eventUpdateRequest` | Object | Không | Thông tin event cần cập nhật |

### 5.3 EventCreationRequest

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `eventDescription` | String | **Có** | Mô tả / nội dung sự kiện |
| `linkEvent` | String | Không | Đường link cuộc họp online (Google Meet, Zoom,...) |
| `location` | String | Không | Địa điểm nếu là event offline |
| `isOnline` | Boolean | Không | `true` = event online, `false` = offline. Mặc định `false` |
| `reminderMinutesBefore` | Integer | Không | Số phút trước khi gửi email reminder. Mặc định `30` |
| `invitedEmails` | List\<String\> | Không | Danh sách email được mời tham gia |
| `startTime` | String (ISO 8601 OffsetDateTime) | Không | Thời gian bắt đầu event |

### 5.4 EventUpdateRequest

| Trường | Kiểu | Bắt buộc | Mô tả |
|---|---|---|---|
| `eventDescription` | String | Không | Mô tả / nội dung sự kiện |
| `linkEvent` | String | Không | Đường link cuộc họp online |
| `location` | String | Không | Địa điểm nếu là event offline |
| `isOnline` | Boolean | Không | `true` = online, `false` = offline |
| `reminderMinutesBefore` | Integer | Không | Số phút trước khi gửi email reminder. Thay đổi sẽ cập nhật lại lịch Redis |
| `invitedEmails` | List\<String\> | Không | Danh sách email mới. Hệ thống sẽ replace toàn bộ danh sách cũ |

### 5.5 TaskResponse

| Trường | Kiểu | Mô tả |
|---|---|---|
| `taskId` | Long | ID của task |
| `title` | String | Tiêu đề task |
| `description` | String | Mô tả task |
| `deadline` | String | Thời hạn (UTC) |
| `status` | Enum | Trạng thái: `TODO`, `IN_PROGRESS`, `DONE` |
| `priority` | Enum | Độ ưu tiên: `HIGH`, `MEDIUM`, `LOW` |
| `createdAt` | String | Thời gian tạo (UTC) |
| `completedAt` | String | Thời gian hoàn thành (UTC, null nếu chưa done) |
| `userId` | Long | ID người tạo |
| `isEvent` | Boolean | `true` = task này là event |
| `eventId` | Long | ID event (null nếu không phải event) |

---

## 6. Flow email reminder

```
Tạo event ──> Email Service lưu invitedEmails ──> Redis lưu reminder schedule
                                                              │
                                                              v
                                        Scheduler chạy mỗi phút
                                                              │
                                                              v
                              Khi đến giờ reminder ──> Lấy invitedEmails từ DB
                                                              │
                                                              v
                                              Gửi email đến từng invited
```

**Ví dụ:** Event bắt đầu lúc **14:50 UTC+7**, `reminderMinutesBefore = 30`
→ Email reminder được gửi lúc **14:20 UTC+7** đến tất cả `invitedEmails`

---

## 7. Các lỗi thường gặp & cách xử lý

### 7.1 Validation

- `eventDescription` bị trống khi `isEvent = true` → HTTP 400
- Email không đúng format → vẫn được lưu (BE không validate format email)

### 7.2 Múi giờ

- BE lưu tất cả thời gian dưới dạng **UTC**
- FE nên gửi kèm timezone offset (ví dụ `+07:00`)
- BE trả về UTC (không có offset), FE tự convert về local

### 7.3 Gửi nhiều lần cùng email

- Nếu `invitedEmails` chứa email trùng lặp → email-service vẫn lưu từng bản ghi riêng
- Khuyến nghị: FE nên loại bỏ email trùng trước khi gửi

---

## 8. Checklist cho FE

- [ ] Form tạo task có checkbox / toggle "Tạo sự kiện" để bật `isEvent`
- [ ] Khi `isEvent = true`, hiển thị thêm các trường event
- [ ] Validate `eventDescription` bắt buộc khi tạo event
- [ ] Gửi `deadline` = thời gian bắt đầu event (ISO 8601 với offset)
- [ ] Hiển thị trường `isEvent` và `eventId` trong TaskResponse
- [ ] Trang chi tiết task/event: gọi `GET /api/tasks/{taskId}` để lấy thông tin đầy đủ
- [ ] Trang danh sách: `GET /api/tasks` trả về `isEvent` để phân biệt task/event
- [ ] Form cập nhật: hỗ trợ đầy đủ `createdAt` (startTime), `completedAt`, `description`, `deadline`, `status`
- [ ] Form cập nhật: hỗ trợ cập nhật event fields kèm `eventId`
- [ ] Nút xóa: gọi `DELETE /api/tasks/{taskId}?eventId={eventId}` nếu là event
