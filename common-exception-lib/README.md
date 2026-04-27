# 🧱 Common Exception Library

A reusable **Spring Boot exception handling library** for Microservices.  
This library provides a **unified structure** for API error responses and global exception handling across all services.

---

## 🚀 Features

- Centralized error handling using `@RestControllerAdvice`
- Custom exception classes with standardized error codes
- Reusable `ErrorCode` enumeration with HTTP status mapping
- Unified API response wrapper (`ApiResponse`)
- Easy integration with any Spring Boot service

---

## 📦 Installation

### 1️⃣ Build and install to local Maven repository
```bash
mvn clean install
```

If successful, you should see output like:
```
BUILD SUCCESS
Installing ... to ~/.m2/repository/com/microsv/common/common-exception-lib/1.0.0
```

---

### 2️⃣ Add dependency to your microservice

In your service’s `pom.xml` (for example: `user-service`):

```xml
<dependency>
    <groupId>com.microsv.common</groupId>
    <artifactId>common-exception-lib</artifactId>
    <version>1.0.0</version>
</dependency>
```

Then reload your Maven project.

---

## 🧩 Package Structure

```
com.microsv.common
 ┣ enumeration/
 ┃ ┗ ErrorCode.java          → Defines custom error codes & messages
 ┣ exception/
 ┃ ┣ custom/
 ┃ ┃ ┣ ApiException.java     → For general API errors
 ┃ ┃ ┣ BaseException.java    → Base class for all custom exceptions
 ┃ ┃ ┗ UserNotFoundException.java
 ┃ ┗ GlobalExceptionHandler.java  → Handles all exceptions globally
 ┗ response/
   ┗ ApiResponse.java        → Standard response wrapper
```

---

## 🧠 Usage Example

### Throwing custom exception
```java
import com.microsv.common.exception.custom.BaseException;
import com.microsv.common.enumeration.ErrorCode;

if (user == null) {
    throw new BaseException(ErrorCode.USER_NOT_FOUND);
}
```

### Global exception response example
**Response:**
```json
{
  "code": 1001,
  "message": "User not found",
  "status": "NOT_FOUND",
  "timestamp": "2025-10-10T10:30:15"
}
```

---

## ⚙️ Technologies Used

- **Java 21**
- **Spring Boot 3.3.5**
- **Lombok**
- **Maven**

---

## 🧰 How to Extend

To add your own exception:
1. Create a new class extending `BaseException`.
2. Add a new constant in `ErrorCode.java`.
3. Throw your custom exception anywhere in your service.

Example:
```java
public class EmailAlreadyExistsException extends BaseException {
    public EmailAlreadyExistsException() {
        super(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
}
```

---

## 🧾 License

This project is part of the **Smart Schedule Microservices Project**  
© 2025 – Developed by **Tài**

---

## 💡 Notes

- Make sure to install this library before building dependent microservices.
- Works seamlessly with Spring Cloud and any microservice architecture.
