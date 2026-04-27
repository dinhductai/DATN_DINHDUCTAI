# Smart Schedule — AI Chat (Spring AI + OpenAI) — Source Dump

Tài liệu này **gom toàn bộ code** liên quan tới chức năng chat AI trong `ai-service` có dùng **Spring AI** (`ChatClient`, `ChatMemory`, `MessageChatMemoryAdvisor`).

> Lưu ý bảo mật: mọi secret/key trong config (nếu có) đã được **redact** thành biến môi trường.

## 1) Điểm vào API

- `POST /api/ai` (multipart/form-data): chat với AI, có `message`, `file` (chưa xử lý), `conversationId`
- `GET /api/ai/id`: lấy conversationId gần nhất của user
- `GET /api/ai?page=&size=`: xem lịch sử hội thoại (paging)

---

## 2) Dependency Spring AI

### File: `ai-service/ai-service/pom.xml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
		 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">

	<modelVersion>4.0.0</modelVersion>

	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>3.3.5</version>
		<relativePath/>
	</parent>
	<groupId>com.microsv</groupId>
	<artifactId>ai-service</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>ai-service</name>
	<description>ai service</description>

	<properties>
		<java.version>21</java.version>
		<spring-cloud.version>2023.0.3</spring-cloud.version>
		<spring-ai.version>1.0.0</spring-ai.version>
	</properties>

	<dependencyManagement>
		<dependencies>
			<dependency>
				<groupId>org.springframework.ai</groupId>
				<artifactId>spring-ai-bom</artifactId>
				<version>${spring-ai.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
			<dependency>
				<groupId>org.springframework.cloud</groupId>
				<artifactId>spring-cloud-dependencies</artifactId>
				<version>${spring-cloud.version}</version>
				<type>pom</type>
				<scope>import</scope>
			</dependency>
		</dependencies>
	</dependencyManagement>

	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>

		<dependency>
			<groupId>org.springframework.ai</groupId>
			<artifactId>spring-ai-starter-model-openai</artifactId>
		</dependency>

		<dependency>
			<groupId>org.springframework.cloud</groupId>
			<artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
		</dependency>
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>org.springframework.cloud</groupId>
			<artifactId>spring-cloud-starter-openfeign</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>

</project>
```

---

## 3) Spring AI/OpenAI configuration

### File: `ai-service/ai-service/src/main/resources/application.yml`
```yaml
spring:
  application:
    name: ai-service
  datasource:
    url: jdbc:postgresql://postgres-ai-db:5432/ai_db
    username: postgres
    password: password123
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
    show-sql: true
  ai:
    openai:
      api-key: ${OPENAI_API_KEY} # REDACTED
      chat:
        options:
          model: gpt-3.5-turbo

server:
  port: 9006

eureka:
  client:
    service-url:
      defaultZone: http://discovery-server:8761/eureka/
  instance:
    prefer-ip-address: true

jwt:
  secret: ${JWT_SECRET} # REDACTED
```

> Ghi chú: `docker-compose.yml` ở root đang set biến `SPRING_AI_OPENAI_API_KEY` (theo convention của Spring AI). Còn `application.yml` đang dùng `${OPENAI_API_KEY}`. Nếu chạy bằng compose mà chưa override, bạn cần đồng bộ lại biến env.

---

## 4) Controller (Chat endpoints)

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/controller/ConversationController.java`
```java
package com.microsv.ai_service.controller;

import com.microsv.ai_service.dto.response.ChatAIConversationResponse;
import com.microsv.ai_service.dto.response.ChatAIResponse;
import com.microsv.ai_service.entity.ConversationMemory;
import com.microsv.ai_service.service.impl.ChatAIServiceImpl;
import com.microsv.ai_service.service.impl.ChatMemoryServiceImpl;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/ai")
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class ConversationController {
    ChatMemoryServiceImpl chatMemoryService;
    ChatAIServiceImpl chatAIService;

    @PostMapping
    public ResponseEntity<ChatAIConversationResponse> chat(
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "conversationId", required = false) String conversationId,
            @AuthenticationPrincipal Jwt jwt) {
        Long userId  = Long.parseLong(jwt.getSubject());
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }
        String chatAIResponses = chatAIService.chat(message, file,conversationId,userId);
        ChatAIConversationResponse response = new ChatAIConversationResponse(conversationId,chatAIResponses);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/id")
    public ResponseEntity<String> getConversationId(@AuthenticationPrincipal Jwt jwt){
        Long userId  = Long.parseLong(jwt.getSubject());
        String conversationId = chatMemoryService.getConversationId(userId);
        return ResponseEntity.ok(conversationId);
    }

    @GetMapping
    public ResponseEntity<Page<ConversationMemory>> getConversationMemory(@AuthenticationPrincipal Jwt jwt,
                                                                          @RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "10") int size){
        Long userId  = Long.parseLong(jwt.getSubject());
        Page<ConversationMemory> conversation = chatMemoryService.getConversationMemory(userId,page,size);
        return ResponseEntity.ok(conversation);
    }}
```

---

## 5) Spring AI Chat Service (ChatClient)

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/service/ChatAIService.java`
```java
package com.microsv.ai_service.service;

import org.springframework.web.multipart.MultipartFile;

public interface ChatAIService {
    String chat(String message, MultipartFile file, String conversationId, Long userId);
}
```

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/service/impl/ChatAIServiceImpl.java`
```java
package com.microsv.ai_service.service.impl;

import com.microsv.ai_service.service.ChatAIService;
import com.microsv.ai_service.util.PromptUtil;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatAIServiceImpl implements ChatAIService {
    ChatClient chatClient;

    public ChatAIServiceImpl(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultSystem(PromptUtil.SYSTEM_PROMPT) //prompt để train AI
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    //tạm thời trả ra string, đang bị lỗi convert response object
    @Override
    public String chat(String message, MultipartFile file, String conversationId, Long userId) {

        //chưa xử lý multipart file

        if (message == null || message.trim().isEmpty()) {
            message = "Xin chào"; //message mặc định
        }

        String finalMessage = message;
        return chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(promptUserSpec -> {
                    if (finalMessage != null && !finalMessage.isEmpty()) {
                        promptUserSpec.text(finalMessage);
                    }
                })

                //đang thiếu dạng file

                .call()
                .content().trim();

    }


}
```

---

## 6) Chat Memory (Spring AI ChatMemory + DB persistence + task context)

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/service/ConversationMemoryService.java`
```java
package com.microsv.ai_service.service;

import com.microsv.ai_service.entity.ConversationMemory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ConversationMemoryService {
    String getConversationId(Long userId);
    Page<ConversationMemory> getConversationMemory(Long userId,int page,int size);
}
```

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/service/impl/ChatMemoryServiceImpl.java`
```java
package com.microsv.ai_service.service.impl;

import com.microsv.ai_service.client.TaskClient;
import com.microsv.ai_service.dto.response.TaskResponse;
import com.microsv.ai_service.entity.ConversationMemory;
import com.microsv.ai_service.repository.ConversationMemoryRepository;
import com.microsv.ai_service.service.ConversationMemoryService;
import com.microsv.ai_service.util.NullUtil;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMemoryServiceImpl implements ChatMemory , ConversationMemoryService {
    private final ConversationMemoryRepository conversationMemoryRepository;
    private final TaskClient taskClient;

    @Override
    public void add(String conversationId, List<Message> messages) {
        Long currentUserId = getCurrentUserId();
        NullUtil.checkUserNullByUserId(currentUserId);

        for (Message message : messages) {
            ConversationMemory cvMemory = ConversationMemory.builder()
                    .conversationId(conversationId)
                    .role(message.getMessageType().getValue().toUpperCase())
                    .content(message.getText())
                    .userId(currentUserId)
                    .build();
            conversationMemoryRepository.save(cvMemory);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        NullUtil.checkUserNullByUserId(getCurrentUserId());
        List<ConversationMemory> cvMemory = conversationMemoryRepository.findByConversationId(conversationId);
        List<TaskResponse> tasks = taskClient.getUserTasks(getCurrentUserId());
        Message taskContext = createTaskContext(tasks);
        List<Message> messages = new ArrayList<>();
        if(!tasks.isEmpty()){
            messages.add(taskContext);
        }
        messages.addAll(cvMemory.stream().map(this::convertToMessage).collect(Collectors.toList()));
        return messages;
    }

    @Override
    public void clear(String conversationId) {
        Long currentUserId = getCurrentUserId();
        NullUtil.checkUserNullByUserId(currentUserId);
        conversationMemoryRepository.deleteByConversationIdAndUserId(conversationId, currentUserId);
    }

    private Message createTaskContext(List<TaskResponse> taskResponses) {
        StringBuilder context = new StringBuilder();
        context.append("USER'S CURRENT TASKS , DEADLINE AND SCHEDULE: \n");
        if (taskResponses.isEmpty()) {
            context.append(" NO TASKS FOUND\n");
        }
        else{
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for(TaskResponse taskResponse : taskResponses){
                context.append(String.format(
                        "- title: %s | description: %s | deadline: %s | status: %s | priority: %s | createdAt: %s | completedAt: %s \n",
                        taskResponse.getTitle(),
                        taskResponse.getDescription(),
                        taskResponse.getDeadline() != null ? taskResponse.getDeadline().format(formatter) : "No deadline",
                        taskResponse.getStatus(),
                        taskResponse.getPriority(),
                        taskResponse.getCreatedAt().format(formatter),
                        taskResponse.getCompletedAt() != null ? taskResponse.getCompletedAt().format(formatter) : "Still working"
                ));
            }
        }
        context.append("\nUse this task information to provide relevant responses about scheduling, " +
                "task management, and deadlines while maintaining conversation context.");
        return new SystemMessage(context.toString());
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            return Long.parseLong(jwt.getSubject());
        }
        return null;
    }

    private Message convertToMessage(ConversationMemory memory){
        if("USER".equalsIgnoreCase(memory.getRole())){
            return new UserMessage(memory.getContent());
        }
        else {
            return new AssistantMessage(memory.getContent());
        }
    }

    @Override
    public String getConversationId(Long userId) {
        ConversationMemory conversationMemory = conversationMemoryRepository.findFirstByUserId(userId).orElseThrow();
        return conversationMemory.getConversationId();
    }

    @Override
    public Page<ConversationMemory> getConversationMemory(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page,size);
        return conversationMemoryRepository.findAllByUserId(userId,pageable);
    }
}
```

---

## 7) Prompt (system prompt)

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/util/PromptUtil.java`
```java
package com.microsv.ai_service.util;

import org.springframework.ai.content.Media;

import java.util.List;

public class PromptUtil {

    //prompt train tạm thời
    public static final String SYSTEM_PROMPT = """
            Bạn là một trợ lý AI chuyên về quản lý thời gian và sắp xếp lịch trình. Tên của bạn là 'Schedule Assistant'.
            
            VAI TRÒ CHÍNH:
            - Hỗ trợ người dùng quản lý, sắp xếp và tối ưu hóa lịch trình công việc
            - Phân tích và đưa ra đề xuất cho các task dựa trên mức độ ưu tiên, deadline
            - Giúp người dùng lập kế hoạch làm việc hiệu quả
            
            DỮ LIỆU BẠN CÓ:
            - Danh sách các task hiện tại của người dùng (bao gồm: tiêu đề, mô tả, deadline, trạng thái, mức độ ưu tiên, thời gian mà task đã hoàn thành (nếu đã hoàn thành))
            - Lịch sử trò chuyện trước đó với người dùng
            
            NHIỆM VỤ CỤ THỂ:
            1. PHÂN TÍCH & TƯ VẤN SẮP XẾP TASK:
               - Đánh giá task nào nên làm trước dựa trên: deadline gần, mức độ ưu tiên cao
               - Gợi ý thứ tự thực hiện task hợp lý
               - Cảnh báo các task sắp hết hạn
            
            2. QUẢN LÝ THỜI GIAN:
               - Ước lượng thời gian cần thiết cho các task
               - Đề xuất phân bổ thời gian trong ngày/tuần
               - Nhắc nhở về các khung giờ làm việc hiệu quả
            
            3. TỐI ƯU HÓA LỊCH TRÌNH:
               - Gợi ý nhóm các task có liên quan
               - Đề xuất breaks giữa các task để tránh kiệt sức
               - Tối ưu hóa đường đi nếu có task cần di chuyển
            
            4. HỖ TRỢ RA QUYẾT ĐỊNH:
               - Giúp người dùng quyết định nên tập trung vào task nào
               - Đề xuất delegate hoặc loại bỏ task không cần thiết
               - Cân bằng giữa công việc và nghỉ ngơi
            
            QUY TẮC ỨNG XỬ:
            - CHỈ trả lời các câu hỏi liên quan đến quản lý thời gian, sắp xếp lịch trình, task management
            - Từ chối lịch sự các câu hỏi ngoài phạm vi bằng cách: "Xin lỗi, tôi chỉ có thể hỗ trợ bạn về việc quản lý thời gian và sắp xếp lịch trình thôi ạ! 😊"
            - Luôn tham chiếu đến dữ liệu task thực tế của người dùng khi đưa ra đề xuất
            - Sử dụng ngôn ngữ thân thiện, chuyên nghiệp, có thể dùng emoji phù hợp
            - Luôn đề xuất cụ thể và có thể hành động được
            
            ĐỊNH DẠNG PHẢN HỒI:
            - Bắt đầu bằng việc tóm tắt tình hình hiện tại
            - Đưa ra đề xuất rõ ràng, có giải thích lý do
            - Sử dụng bullet points cho các đề xuất cụ thể
            - Kết thúc bằng lời động viên hoặc câu hỏi tiếp theo
            
            VÍ DỤ PHẢN HỒI:
            "Dựa trên task list hiện tại, tôi thấy bạn có 3 task cần hoàn thành:
            • [Task A] - Deadline sắp đến, ưu tiên cao, hoặc ảnh hưởng nghiêm trọng tới sức khỏe
            • [Task B] - Quan trọng nhưng còn thời gian, hoặc ảnh hưởng tới sức khỏe
            • [Task C] - Có thể làm sau
            
            Đề xuất: Nên tập trung làm Task A trước vì..."
            """;

    public static void checkMessageAndMediaIsNull(String message, List<Media> mediaList) {
        if ((message == null || message.isBlank()) && mediaList.isEmpty()) {
            throw new RuntimeException("Please provide a message or an image to start the chat.");
        }
    }
}
```

---

## 8) DB layer for memory

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/entity/ConversationMemory.java`
```java
package com.microsv.ai_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Timestamp;

@Entity
@Table(name = "conversation_memory")
@Getter


@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ConversationMemory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_id")
    private Integer chatId;

    @Column(name = "conversation_id",nullable = false)
    private String conversationId;

    @Column(name = "role",nullable = false)
    private String role;

    @Column(name = "content",length = 3000) //tăng kích thước lưu trữ câu tl của AI
    private String content;

    @Column(name = "create_at")
    private Timestamp createAt = new Timestamp(System.currentTimeMillis());

    @Column(name = "user_id",nullable = false)
    private Long userId;
}
```

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/repository/ConversationMemoryRepository.java`
```java
package com.microsv.ai_service.repository;

import com.microsv.ai_service.dto.response.ChatAIResponse;
import com.microsv.ai_service.entity.ConversationMemory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationMemoryRepository extends JpaRepository<ConversationMemory, Long> {
    List<ConversationMemory> findByConversationId(String conversationId);
    void deleteByConversationIdAndUserId(String conversationId, Long userId);
    Optional<ConversationMemory> findFirstByUserId(Long userId);
    Page<ConversationMemory> findAllByUserId(Long userId, Pageable pageable);
}
```

---

## 9) Feign call sang task-service (để nhét task context vào chat memory)

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/client/TaskClient.java`
```java
package com.microsv.ai_service.client;

import com.microsv.ai_service.config.FeignConfig;
import com.microsv.ai_service.dto.response.TaskResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "task-service")
public interface TaskClient {

    //lấy tất cả task thuộc user
    @GetMapping(value = "/internal/tasks")
    List<TaskResponse> getUserTasks(@RequestHeader("userId") Long userId);
}
```

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/config/FeignConfig.java`
```java
package com.microsv.ai_service.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            // Lấy authentication từ security context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) authentication.getPrincipal();
                // Truyền JWT token sang service khác
                String tokenValue = jwt.getTokenValue();
                template.header("Authorization", "Bearer " + tokenValue);
            }
        };
    }
}
```

### File (dependency endpoint): `task-service/task-service/src/main/java/com/microsv/task_service/controller/internal/TaskInternalController.java`
```java
package com.microsv.task_service.controller.internal;

import com.microsv.task_service.dto.response.TaskResponse;
import com.microsv.task_service.service.TaskService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/tasks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaskInternalController {
    TaskService taskService;

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasks(@RequestHeader("userId") Long userId) {
        List<TaskResponse> responses = taskService.getAllTasksByUser(userId);
        return ResponseEntity.ok(responses);
    }

}
```

---

## 10) DTOs/utilities used by chat feature

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/dto/response/ChatAIConversationResponse.java`
```java
package com.microsv.ai_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAIConversationResponse {
    String conversationId;
    String chatAIResponses;
}

```

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/dto/response/ChatAIResponse.java`
```java
package com.microsv.ai_service.dto.response;

public record ChatAIResponse(String message) {
}
```

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/dto/response/TaskResponse.java`
```java
package com.microsv.ai_service.dto.response;


import com.microsv.ai_service.enumeration.PriorityLevel;
import com.microsv.ai_service.enumeration.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {
    private Long taskId;
    private String title;
    private String description;
    private LocalDateTime deadline;
    private TaskStatus status;
    private PriorityLevel priority;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Long userId;
}
```

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/enumeration/TaskStatus.java`
```java
package com.microsv.ai_service.enumeration;

public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE
}
```

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/enumeration/PriorityLevel.java`
```java
package com.microsv.ai_service.enumeration;

public enum PriorityLevel {
    HIGH,
    MEDIUM,
    LOW
}
```

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/util/NullUtil.java`
```java
package com.microsv.ai_service.util;

public class NullUtil {
    public static void checkUserNullByUserId(Long userId){
        if(userId == null) {
            throw new IllegalArgumentException("User not authenticated, cannot save chat memory");
        }
    }
}
```

---

## 11) Security + App bootstrap

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/config/SecurityConfig.java`
```java
package com.microsv.ai_service.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Bật annotation @PreAuthorize nếu bạn muốn dùng sau này
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String secretKey;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                                .requestMatchers("/internal/**").permitAll()

                                .requestMatchers(HttpMethod.GET, "/api/ai").authenticated()
                                .requestMatchers(HttpMethod.GET, "/api/ai/**").authenticated()
                                .requestMatchers(HttpMethod.POST, "/api/ai").authenticated()
                                .requestMatchers(HttpMethod.PUT, "/api/ai/**").authenticated()
                                .requestMatchers(HttpMethod.DELETE, "/api/ai/**").authenticated()

                                .requestMatchers(HttpMethod.GET, "/api/ai/admin").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/ai/**").hasAnyRole("ADMIN")
                                .anyRequest().authenticated() //mặc định tất cả APIs khác cần auth
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    //bean này sẽ đọc claim "scope" và chuyển nó thành các quyền (authorities)
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        jwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("scope"); // Đọc từ claim "scope"
        jwtGrantedAuthoritiesConverter.setAuthorityPrefix(""); // Bỏ tiền tố "SCOPE_"

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }

    //bean này chịu trách nhiệm giải mã và xác thực chữ ký của JWT
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HS384");
        return NimbusJwtDecoder
                .withSecretKey(secretKeySpec)
                .macAlgorithm(MacAlgorithm.HS384)
                .build();
    }
}
```

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/AiServiceApplication.java`
```java
package com.microsv.ai_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class AiServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiServiceApplication.class, args);
	}

}

```

---

## 12) Exception handler placeholder

### File: `ai-service/ai-service/src/main/java/com/microsv/ai_service/exception/GlobalExceptionHandler.java`
```java
package com.microsv.ai_service.exception;

public class GlobalExceptionHandler {
    //tiến hành xử lý sau khi config service hoàn thanh
}
```
