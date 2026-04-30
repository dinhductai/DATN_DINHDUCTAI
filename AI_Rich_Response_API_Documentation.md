# AI Chat Rich Response API

## Mục lục
1. [Tổng quan](#tổng-quan)
2. [Endpoint](#endpoint)
3. [Request](#request)
4. [Response](#response)
5. [Mô tả chi tiết từng trường](#mô-tả-chi-tiết-từng-trường)
6. [Ví dụ Response thực tế](#ví-dụ-response-thực-tế)
7. [Hướng dẫn render UI](#hướng-dẫn-render-ui)

---

## Tổng quan

API `/api/ai/rich` trả về phản hồi AI dưới dạng **JSON có cấu trúc (structured)**, giúp Frontend dễ dàng parse và render thành UI đẹp thay vì hiển thị một khối text thuần.

**Điểm khác biệt so với `/api/ai`:**

| `/api/ai` | `/api/ai/rich` |
|---|---|
| Trả về text markdown (chuỗi string) | Trả về JSON có cấu trúc |
| Frontend phải parse markdown thủ công | Frontend parse JSON trực tiếp |
| Khó render UI nhất quán | Dễ dàng bind vào component |

---

## Endpoint

```
POST /api/ai/rich
```

> **Authentication:** Bearer JWT Token (Required)

---

## Request

### Headers

| Header | Value | Required |
|---|---|---|
| `Authorization` | `Bearer <JWT_TOKEN>` | ✅ Yes |
| `Content-Type` | `application/x-www-form-urlencoded` | ✅ Yes |

### Body (Form URL Encoded)

| Parameter | Type | Required | Description |
|---|---|---|---|
| `message` | String | Yes* | Câu hỏi của user. Nếu empty/null → tự động trả về lời chào |
| `conversationId` | String | No | ID cuộc trò chuyện. Nếu không truyền → server tự tạo UUID mới. **Nên lưu lại từ response để gửi tiếp ở các lần sau** |

### Ví dụ Request

```javascript
const response = await fetch('http://localhost:8080/api/ai/rich', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer eyJhbGciOiJIUzM4NCIs...',
    'Content-Type': 'application/x-www-form-urlencoded'
  },
  body: new URLSearchParams({
    message: 'Tôi nên làm gì hôm nay?',
    conversationId: 'a36d2b27-d469-4c7f-b095-6d4ea1080902' // optional, nên truyền để duy trì context
  })
});

const data = await response.json();
```

---

## Response

### Success (200 OK)

```json
{
  "structured": true,
  "message": "Dựa trên danh sách task của bạn hôm nay, đây là lịch trình đề xuất:",
  "summary": {
    "totalTasks": 7,
    "pendingTasks": 7,
    "overdueTasks": 0,
    "completedToday": 0,
    "completionRate": 0.0
  },
  "tasks": [
    {
      "emoji": "🔴",
      "taskId": 6,
      "title": "test2",
      "description": "desc test 2",
      "deadline": "28/04/2026 10:00",
      "priority": "HIGH",
      "status": "TODO",
      "reason": "Deadline hôm nay + Priority HIGH"
    }
  ],
  "recommendations": [
    {
      "taskId": 6,
      "taskTitle": "test2",
      "reason": "Task quan trọng nhất cần hoàn thành hôm nay.",
      "order": 1
    }
  ],
  "motivation": "Cố lên! Bạn sẽ làm được!",
  "followUp": "Bạn cần tôi nhắc deadline trước bao lâu?",
  "conversationId": "a36d2b27-d469-4c7f-b095-6d4ea1080902"
}
```

### Fallback (structured: false) — Khi AI không trả về JSON đúng format

```json
{
  "structured": false,
  "message": "Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại."
}
```

---

## Mô tả chi tiết từng trường

### Root Level

| Trường | Kiểu | Mô tả |
|---|---|---|
| `structured` | `boolean` | `true` = AI trả JSON đúng format. `false` = parse lỗi, nên hiển thị `message` như text thuần |
| `message` | `String` | Câu chào hoặc nhận xét ngắn gọn bằng tiếng Việt (1-2 câu) |
| `conversationId` | `String` | UUID của cuộc trò chuyện. **Lưu lại để gửi ở request tiếp theo** |

### summary

Thống kê tổng quan về tasks của user.

| Trường | Kiểu | Mô tả |
|---|---|---|
| `totalTasks` | `int` | Tổng số task |
| `pendingTasks` | `int` | Số task chưa hoàn thành |
| `overdueTasks` | `int` | Số task quá hạn |
| `completedToday` | `int` | Số task đã hoàn thành hôm nay |
| `completionRate` | `double` | Tỷ lệ hoàn thành (0.0 - 100.0) |

### tasks

Danh sách chi tiết các task liên quan đến câu hỏi của user (tối đa 10 task).

| Trường | Kiểu | Mô tả |
|---|---|---|
| `emoji` | `String` | 🔴 = HIGH, 🟡 = MEDIUM, 🟢 = LOW |
| `taskId` | `Long / null` | ID của task. `null` nếu AI không xác định được |
| `title` | `String` | Tiêu đề task |
| `description` | `String / null` | Mô tả ngắn. `null` nếu không có |
| `deadline` | `String` | Format: `dd/MM/yyyy HH:mm` (VD: `28/04/2026 10:00`) |
| `priority` | `String` | `HIGH` \| `MEDIUM` \| `LOW` |
| `status` | `String` | `TODO` \| `IN_PROGRESS` \| `DONE` |
| `reason` | `String / null` | Giải thích ngắn gọn tại sao task này được liệt kê |

### recommendations

Danh sách task quan trọng nhất cần ưu tiên (tối đa 3 task), sắp xếp theo thứ tự ưu tiên.

| Trường | Kiểu | Mô tả |
|---|---|---|
| `taskId` | `Long / null` | ID của task |
| `taskTitle` | `String` | Tiêu đề task được recommend |
| `reason` | `String / null` | Lý do nên làm trước (1 câu) |
| `order` | `int` | Thứ tự ưu tiên: 1, 2, 3... |

### motivation

`String / null` — Câu động viên ngắn 1 dòng bằng tiếng Việt. VD: `"Cố lên! Bạn sẽ làm được!"`

### followUp

`String / null` — Câu hỏi tiếp theo gợi ý cho user. VD: `"Bạn cần tôi nhắc deadline trước bao lâu?"`

---

## Ví dụ Response thực tế

> **Lưu ý quan trọng về ngày tháng:** Backend truyền ngày hiện tại theo múi giờ **Asia/Ho_Chi_Minh (UTC+7)** vào prompt cho AI. Ví dụ: `"Hôm nay là thứ Tư, ngày 29 tháng 04 năm 2026"`. AI sẽ so sánh deadline với ngày này, không phải UTC date.

### Test 1: "Tôi nên làm gì hôm nay?" (29/04/2026)

```json
{
  "structured": true,
  "message": "Dựa trên danh sách task của bạn hôm nay, đây là lịch trình đề xuất:",
  "summary": {
    "totalTasks": 7,
    "pendingTasks": 7,
    "overdueTasks": 0,
    "completedToday": 0,
    "completionRate": 0.0
  },
  "tasks": [
    {
      "emoji": "🔴",
      "taskId": 10,
      "title": "test6",
      "description": "",
      "deadline": "29/04/2026 10:00",
      "priority": "HIGH",
      "status": "TODO",
      "reason": "Deadline trong hôm nay + Priority HIGH"
    },
    {
      "emoji": "🟢",
      "taskId": 9,
      "title": "test4",
      "description": "",
      "deadline": "29/04/2026 08:00",
      "priority": "LOW",
      "status": "TODO",
      "reason": "Deadline trong hôm nay"
    },
    {
      "emoji": "🟡",
      "taskId": 11,
      "title": "test7",
      "description": "",
      "deadline": "29/04/2026 00:00",
      "priority": "MEDIUM",
      "status": "TODO",
      "reason": "Deadline trong hôm nay + Priority MEDIUM"
    },
    {
      "emoji": "🟡",
      "taskId": 12,
      "title": "test8",
      "description": "",
      "deadline": "29/04/2026 13:00",
      "priority": "MEDIUM",
      "status": "TODO",
      "reason": "Deadline trong hôm nay + Priority MEDIUM"
    }
  ],
  "recommendations": [
    {
      "taskId": 10,
      "taskTitle": "test6",
      "reason": "Cần hoàn thành task quan trọng trước khi hết thời gian.",
      "order": 1
    },
    {
      "taskId": 9,
      "taskTitle": "test4",
      "reason": "Tuy có priority thấp nhưng deadline trước.",
      "order": 2
    },
    {
      "taskId": 11,
      "taskTitle": "test7",
      "reason": "Có deadline trong hôm nay và priority trung bình.",
      "order": 3
    }
  ],
  "motivation": "Cố lên! Bạn sẽ làm được!",
  "followUp": "Bạn cần tôi nhắc deadline trước bao lâu?",
  "conversationId": "43afa317-784f-4f29-ad84-dded5753de2c"
}
```

### Test 2: "Có task nào quá hạn không?"

### Test 3: Liệt kê tất cả task

```json
{
  "structured": true,
  "message": "Đây là danh sách tất cả các task của bạn cùng với deadline:",
  "summary": {
    "totalTasks": 7,
    "pendingTasks": 7,
    "overdueTasks": 0,
    "completedToday": 0,
    "completionRate": 0.0
  },
  "tasks": [
    { "emoji": "🔴", "taskId": 5, "title": "test 1", "deadline": "28/04/2026 09:00", "priority": "MEDIUM", "reason": "Hôm qua - quá hạn" },
    { "emoji": "🔴", "taskId": 6, "title": "test2", "deadline": "28/04/2026 10:00", "priority": "HIGH", "reason": "Hôm qua - quá hạn" },
    { "emoji": "🟢", "taskId": 9, "title": "test4", "deadline": "29/04/2026 08:00", "priority": "LOW", "reason": "Deadline hôm nay" },
    { "emoji": "🔴", "taskId": 10, "title": "test6", "deadline": "29/04/2026 10:00", "priority": "HIGH", "reason": "Deadline hôm nay + Priority HIGH" },
    { "emoji": "🟡", "taskId": 11, "title": "test7", "deadline": "29/04/2026 00:00", "priority": "MEDIUM", "reason": "Deadline hôm nay + Priority MEDIUM" },
    { "emoji": "🟡", "taskId": 12, "title": "test8", "deadline": "29/04/2026 13:00", "priority": "MEDIUM", "reason": "Deadline hôm nay + Priority MEDIUM" },
    { "emoji": "🟢", "taskId": 8, "title": "test3", "deadline": "30/04/2026 09:00", "priority": "LOW", "reason": "Deadline ngày mai" }
  ],
  "recommendations": [],
  "motivation": "Bạn đang có 7 task, hãy cố gắng hoàn thành chúng nhé!",
  "followUp": null,
  "conversationId": "43afa317-784f-4f29-ad84-dded5753de2c"
}
```

---

## Hướng dẫn render UI

### 1. Kiểm tra structured flag

```typescript
if (data.structured === true) {
  // Render UI từ structured data
} else {
  // Fallback: hiển thị message như text thuần
  renderPlainText(data.message);
}
```

### 2. Render Tasks List

```typescript
// tasks là array, có thể empty
data.tasks?.forEach((task) => {
  const priorityColor = {
    HIGH: 'red',    // 🔴
    MEDIUM: 'yellow', // 🟡
    LOW: 'green'    // 🟢
  }[task.priority];

  // Render task card với:
  // - emoji: 🔴 🟡 🟢
  // - title: task.title
  // - deadline: task.deadline (format dd/MM/yyyy HH:mm)
  // - priority: task.priority
  // - reason: task.reason (hiển thị nhỏ phía dưới)
  // - onClick: navigateToTask(task.taskId) // nếu có taskId
});
```

### 3. Render Recommendations

```typescript
// Sắp xếp theo order và hiển thị thứ tự ưu tiên
data.recommendations
  ?.sort((a, b) => a.order - b.order)
  .forEach((rec) => {
    // Render:
    // 1. test2 → Cần hoàn thành trước do deadline gần.
    // 2. test 1 → Cũng cần hoàn thành trong hôm nay.
    const priority = data.tasks?.find(t => t.taskId === rec.taskId)?.priority || 'MEDIUM';
    renderRecommendation(rec.order, rec.taskTitle, rec.reason, priority);
  });
```

### 4. Render Summary Stats

```typescript
// Hiển thị badge/stats bar
<div>
  <span>Tổng: {data.summary.totalTasks}</span>
  <span>Đang làm: {data.summary.pendingTasks}</span>
  <span>Quá hạn: {data.summary.overdueTasks}</span>
  <span>Hoàn thành hôm nay: {data.summary.completedToday}</span>
  <span>Tỷ lệ: {data.summary.completionRate}%</span>
</div>
```

### 5. Render Motivation & FollowUp

```typescript
// Motivation: hiển thị như banner động viên
{data.motivation && (
  <div className="motivation-banner">
    {data.motivation}
  </div>
)}

// FollowUp: hiển thị như gợi ý câu hỏi tiếp theo
{data.followUp && (
  <button onClick={() => sendMessage(data.followUp)}>
    {data.followUp}
  </button>
)}
```

### 6. Lưu ConversationId

```typescript
// Quan trọng: lưu conversationId từ response để gửi ở request tiếp theo
localStorage.setItem('conversationId', data.conversationId);

// Ở request tiếp theo:
const conversationId = localStorage.getItem('conversationId');
```

---

## Lưu ý quan trọng

1. **`conversationId`**: Luôn lưu lại từ response để duy trì context cuộc trò chuyện
2. **Empty arrays**: Khi không có data phù hợp, `tasks` và `recommendations` trả về `[]`, không phải `null`
3. **Fallback**: Nếu `structured: false`, hiển thị `message` như text thuần
4. **Encoding**: Response trả về UTF-8, đảm bảo frontend xử lý đúng encoding cho tiếng Việt
5. **TaskId có thể null**: AI có thể không xác định được taskId, cần handle trường hợp này
