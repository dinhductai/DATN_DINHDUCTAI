# USER REGISTRATION FLOW — BE to FE Integration Guide

## Kết luận

**Đã phát hiện và fix lỗi 401 Unauthorized trên browser thực.**

Nguyên nhân gốc: Browser gửi **OPTIONS preflight request** (CORS preflight) TRƯỚC khi gửi POST. OPTIONS request không mang JWT → Gateway + user-service cố validate JWT → trả 401.

**Đã fix** bằng cách thêm `.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()` ở cả:
- `api-gateway/.../config/SecurityConfig.java` (dòng 40)
- `user-service/.../config/SecurityConfig.java` (dòng 35)

> ⚠️ **Lưu ý:** Sau khi sửa, cần **restart lại cả 2 services** (`api-gateway` và `user-service`) để config mới có hiệu lực.

---

## Flow đăng ký (Backend)

```
FE gửi request
      │
      ▼
API Gateway (port 8080)
  Route: /api/users/** → lb://user-service
  Security: POST /api/users/register → permitAll()
      │
      ▼
User Service (port 9001)
  Security: POST /api/users/register → permitAll()
      │
      ▼
UserController.registerUser()
  @Valid @RequestBody UserCreationRequest
      │
      ▼
UserServiceImpl.createUser(UserCreationRequest)
  1. Check email đã tồn tại?
  2. Tìm Role USER từ DB
  3. Mã hóa password (BCrypt)
  4. Lưu User vào DB
  5. Map → UserResponse (trả về)
```

---

## Code liên quan

### 1. Controller — Endpoint public

```java
// user-service/.../controller/UserController.java

@PostMapping("/register")
public ResponseEntity<UserResponse> registerUser(
        @Valid @RequestBody UserCreationRequest request) {
    UserResponse response = userService.createUser(request);
    return new ResponseEntity<>(response, HttpStatus.CREATED);
}
```

### 2. Security Gateway — permitAll

```java
// api-gateway/.../config/SecurityConfig.java

.authorizeExchange(exchange -> exchange
    .pathMatchers(HttpMethod.POST, "/api/users/register").permitAll()
    // ... các rule khác ...
    .anyExchange().authenticated()
)
```

### 3. Security User Service — permitAll

```java
// user-service/.../config/SecurityConfig.java

.authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.POST, "/api/users/register").permitAll()
    // ... các rule khác ...
    .anyRequest().authenticated()
)
```

### 4. Request DTO — Validation

```java
// user-service/.../dto/request/UserCreationRequest.java

@Data
public class UserCreationRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, message = "Username must be at least 3 characters")
    private String userName;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Email is required")
    @Email
    private String email;

    @NotBlank(message = "Profile is required")
    private String profile;
}
```

> **Lưu ý quan trọng:** `profile` là `@NotBlank` — FE bắt buộc phải gửi field này. Nếu không gửi, Spring validation trả về **400 Bad Request**, không phải 401.

### 5. Response DTO

```java
// user-service/.../dto/response/UserResponse.java

@Data
@Builder
public class UserResponse {
    private Long userId;
    private String userName;
    private String email;
    private String profile;
    private Set<String> roles;   // ví dụ: ["USER"]
}
```

### 6. Service Implementation

```java
// user-service/.../service/impl/UserServiceImpl.java

@Override
public UserResponse createUser(UserCreationRequest request) {
    if (request == null) {
        throw new BaseException(ErrorCode.INVALID_INPUT);
    }
    if (userRepository.findByEmail(request.getEmail()).isPresent()) {
        throw new BaseException(ErrorCode.EMAIL_ALREADY_IN_USE);
    }
    Role userRole = roleRepository.findByRoleName(RoleName.USER)
            .orElseThrow(() -> new BaseException(ErrorCode.ROLE_NOT_FOUND));
    try {
        User savedUser = userRepository.save(
                userMapper.toCreateUser(request, userRole));
        return userMapper.toUserResponse(savedUser);
    } catch (Exception e) {
        throw new BaseException(ErrorCode.DATABASE_QUERY_ERROR);
    }
}
```

### 7. Entity User

```java
// user-service/.../entity/User.java

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(name = "userName", length = 50)
    private String userName;

    @Column(name = "password", nullable = false)
    private String password;          // BCrypt encoded

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "profile")
    private String profile;           // URL ảnh avatar

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "users_roles", ...)
    private Set<Role> roles;
}
```

### 8. Service config

```yaml
# user-service/.../application.yml
spring:
  datasource:
    url: jdbc:postgresql://postgres-user-db:5432/user_db
  jpa:
    hibernate:
      ddl-auto: update

server:
  port: 9001

eureka:
  client:
    service-url:
      defaultZone: http://discovery-server:8761/eureka/

jwt:
  secret: ${JWT_SECRET}    # PHẢI GIỐNG GATEWAY
```

---

## Các nguyên nhân có thể gây 401 & cách fix

### Nguyên nhân 1: JWT_SECRET khác nhau (PHỔ BIẾN NHẤT)

Gateway và user-service đều dùng JWT để decode token. Nếu `JWT_SECRET` env var ở hai service khác nhau, Gateway vẫn cho phép request đi qua (vì rule `permitAll()`), nhưng user-service sẽ bị lỗi OAuth2 resource server vì JWT decode thất bại.

**Fix:** Kiểm tra `.env` file đảm bảo:
```
JWT_SECRET=A9mK2pX8vS4rW7tF1yD3bJ6nL0zQ5hG2eR9cU4xT8sV1wM7oP3lY6jN5iC
```
phải giống nhau cho **tất cả services**.

### Nguyên nhân 2: FE gửi kèm Authorization header

Nếu FE lưu token cũ rồi gửi request đăng ký kèm `Authorization: Bearer <old_token>`, token đó có thể đã hết hạn hoặc không hợp lệ → Gateway/gateway xử lý khác.

**Fix:** Đảm bảo `/api/users/register` không gửi header `Authorization`.

### Nguyên nhân 3: UserService chưa đăng ký Eureka

Nếu user-service không up hoặc không đăng ký với Eureka, Gateway không tìm thấy service `lb://user-service` → 500 hoặc 401.

**Fix:**
```bash
docker-compose logs user-service
docker-compose ps
```

### Nguyên nhân 4: CORS bị chặn

CORS config ở Gateway cho phép các origin:
```java
.setAllowedOrigins(Arrays.asList(
    "http://localhost:8000", "http://127.0.0.1:8000",
    "http://localhost:3000", "http://127.0.0.1:3000",
    "file://"
))
```
Nếu FE chạy ở origin khác (ví dụ `http://localhost:5173` — Vite), browser sẽ chặn.

**Fix:** Thêm origin vào danh sách allowed trong `api-gateway/.../config/SecurityConfig.java`.

### Nguyên nhân 5: Request body thiếu field `profile`

Nếu FE không gửi `profile`, validation trả về **400 Bad Request** chứ không phải 401. Nhưng FE có thể nhầm thành 401.

**Fix:** Xem response body thực tế. Nếu là 400, thêm field `profile` vào request.

### Nguyên nhân 6: Container network

Nếu chạy Docker, user-service kết nối DB qua `postgres-user-db:5432` (internal network). Nếu user-service không ở trong network `smart-schedule-network`, nó không kết nối được DB → lỗi.

---

## Cách FE gọi đúng

```javascript
// Ví dụ fetch
const response = await fetch('http://localhost:8080/api/users/register', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    // KHÔNG gửi Authorization header cho register
  },
  body: JSON.stringify({
    userName: 'johndoe',
    email: 'john@example.com',
    password: 'Password123',
    profile: 'https://res.cloudinary.com/xxx/image.jpg'  // BẮT BUỘC
  })
});

const data = await response.json();
console.log(data); // { code: 201, message: "Created", data: { userId, userName, ... } }
```

## Response thành công (201)

```json
{
  "code": 201,
  "message": "Created",
  "data": {
    "userId": 5,
    "userName": "johndoe",
    "email": "john@example.com",
    "profile": "https://res.cloudinary.com/xxx/image.jpg",
    "roles": ["USER"]
  }
}
```

## Các lỗi có thể trả về

| HTTP Status | Code | Nguyên nhân |
|------------|------|-------------|
| 400 | `INVALID_INPUT` | Body null hoặc thiếu field bắt buộc |
| 400 | `EMAIL_ALREADY_IN_USE` | Email đã tồn tại trong DB |
| 400 | `ROLE_NOT_FOUND` | Role USER chưa được seed vào DB |
| 500 | `DATABASE_QUERY_ERROR` | Lỗi kết nối DB hoặc constraint violation |
| 401 | — | JWT_SECRET khác nhau hoặc service down |
| 401 | — | FE gửi token không hợp lệ |

---

## Cách debug nhanh

```bash
# 1. Test trực tiếp bằng curl (không qua FE)
curl -v -X POST http://localhost:8080/api/users/register \
  -H "Content-Type: application/json" \
  -d '{"userName":"testuser","email":"test@example.com","password":"Test123@","profile":"https://example.com/pic.jpg"}'

# 2. Kiểm tra Gateway logs
docker-compose logs -f api-gateway

# 3. Kiểm tra User Service logs
docker-compose logs -f user-service

# 4. Kiểm tra Eureka — user-service đã register?
# Mở http://localhost:8761

# 5. Kiểm tra DB — role USER đã có?
docker-compose exec postgres-user-db psql -U postgres -d user_db -c "SELECT * FROM roles;"
docker-compose exec postgres-user-db psql -U postgres -d user_db -c "SELECT * FROM users;"
```
