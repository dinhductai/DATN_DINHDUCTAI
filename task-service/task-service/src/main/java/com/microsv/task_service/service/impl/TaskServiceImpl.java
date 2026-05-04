package com.microsv.task_service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsv.common.enumeration.ErrorCode;
import com.microsv.common.exception.BaseException;
import com.microsv.task_service.dto.message.EventCreationMessage;
import com.microsv.task_service.dto.message.EventReminderMessage;
import com.microsv.task_service.dto.message.EventUpdateMessage;
import com.microsv.task_service.dto.request.EventCreationRequest;
import com.microsv.task_service.dto.request.EventUpdateRequest;
import com.microsv.task_service.dto.request.TaskCreationRequest;
import com.microsv.task_service.dto.request.TaskUpdateRequest;
import com.microsv.task_service.dto.response.*;
import com.microsv.task_service.dto.EventReminderData;
import com.microsv.task_service.entity.Event;
import com.microsv.task_service.entity.Task;
import com.microsv.task_service.enumeration.PriorityLevel;
import com.microsv.task_service.enumeration.TaskStatus;
import com.microsv.task_service.mapper.EventMapper;
import com.microsv.task_service.mapper.TaskMapper;
import com.microsv.task_service.messaging.EventEmailProducer;
import com.microsv.task_service.repository.EventRepository;
import com.microsv.task_service.repository.TaskRepository;
import com.microsv.task_service.service.TaskService;
import com.microsv.task_service.service.ClaudeTaskConvertService;
import com.microsv.task_service.service.TaskCacheService;
import com.microsv.task_service.service.EventReminderRedisService;
import com.microsv.task_service.util.DateUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
@Slf4j
public class TaskServiceImpl implements TaskService {
    TaskMapper taskMapper;
    TaskRepository taskRepository;
    EventRepository eventRepository;
    EventMapper eventMapper;
    EventEmailProducer eventEmailProducer;
    TaskCacheService taskCacheService;
    ClaudeTaskConvertService claudeTaskConvertService;
    EventReminderRedisService eventReminderRedisService;
    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Transactional
    public TaskResponse createTask(TaskCreationRequest request, Long userId) {
        try {
//            DateUtil.ValidateDeadline(request.getDeadline());
            Task savedTask = taskRepository.save(taskMapper.taskCreationRequestToTask(request, userId));
            
            if (Boolean.TRUE.equals(request.getIsEvent()) && request.getEventCreationRequest() != null) {
                Event savedEvent = eventRepository.save(eventMapper.toEvent(request.getEventCreationRequest(), savedTask.getTaskId()));
                
                // Lưu event reminder vào Redis để scheduler check
                saveEventReminderToRedis(savedEvent, savedTask, request.getEventCreationRequest());
                
                // Gửi message để EmailService lưu invitedEmails
                sendEventEmails(savedEvent, request.getEventCreationRequest());
                
                log.info("Event created successfully with eventId: {}, taskId: {}", savedEvent.getEventId(), savedTask.getTaskId());
            }
            
            TaskResponse response = taskMapper.toTaskResponse(savedTask);
            syncTasksToRedis(userId);
            return response;
        }catch (Exception e){
            throw new BaseException(ErrorCode.DATABASE_QUERY_ERROR);
        }
    }
    
    private void saveEventReminderToRedis(Event event, Task task, EventCreationRequest request) {
        OffsetDateTime startTime = request.getStartTime();
        Integer reminderMinutesBefore = event.getReminderMinutesBefore();
        
        EventReminderData reminderData = EventReminderData.builder()
                .eventId(event.getEventId())
                .taskId(task.getTaskId())
                .eventDescription(event.getEventDescription())
                .linkEvent(event.getLinkEvent())
                .location(event.getLocation())
                .isOnline(event.getIsOnline())
                .reminderMinutesBefore(reminderMinutesBefore)
                .startTime(startTime)
                .build();
        eventReminderRedisService.saveEventReminder(reminderData);
    }
    
    private void sendEventEmails(Event event, EventCreationRequest request) {
        if (request.getInvitedEmails() != null && !request.getInvitedEmails().isEmpty()) {
            EventCreationMessage message = EventCreationMessage.builder()
                    .eventId(event.getEventId())
                    .invitedEmails(request.getInvitedEmails())
                    .build();
            eventEmailProducer.sendEventCreation(message);
        }
    }

    @Override
    public TaskResponse getTask(Long taskId, Long userId) {
        try {
            Task task = taskRepository.findByTaskIdAndUserId(taskId, userId)
                    .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
            return taskMapper.toTaskResponse(task);
        }catch (Exception e){
            throw new BaseException(ErrorCode.DATABASE_QUERY_ERROR);
        }
    }

    @Override
    public List<TaskResponse> getAllTasksByUser(Long userId) {
        try {
            List<Task> tasks = taskRepository.findAllByUserId(userId);
            if (tasks.isEmpty()) {
                return List.of();
            }
            List<Long> taskIds = tasks.stream().map(Task::getTaskId).collect(Collectors.toList());
            Map<Long, Event> eventMap = eventRepository.findByTaskIdIn(taskIds).stream()
                    .collect(Collectors.toMap(Event::getTaskId, e -> e));
            return taskMapper.toTaskResponses(tasks, eventMap);
        }catch (Exception e){
        throw new BaseException(ErrorCode.DATABASE_QUERY_ERROR);
    }
    }

    @Override
    public List<TaskResponse> getTasksByStatus(Long userId, TaskStatus status) {
        try {
            List<Task> tasks = taskRepository.findAllByUserIdAndStatus(userId, status);
            if (tasks.isEmpty()) {
                return List.of();
            }
            List<Long> taskIds = tasks.stream().map(Task::getTaskId).collect(Collectors.toList());
            Map<Long, Event> eventMap = eventRepository.findByTaskIdIn(taskIds).stream()
                    .collect(Collectors.toMap(Event::getTaskId, e -> e));
            return taskMapper.toTaskResponses(tasks, eventMap);
        }catch (Exception e){
            throw new BaseException(ErrorCode.DATABASE_QUERY_ERROR);
        }
    }



    @Override
    @Transactional
    public TaskResponse updateTask(Long taskId, TaskUpdateRequest request, Long userId) {
        try{
        Task task = taskRepository.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());

//        DateUtil.ValidateDeadline(request.getDeadline());

        OffsetDateTime oldStartTime = task.getStartTime();
        task.setDeadline(request.getDeadline());
        if (request.getStartTime() != null) {
            task.setStartTime(request.getStartTime());
        }
        if (request.getCompletedAt() != null) {
            task.setCompletedAt(request.getCompletedAt());
        }
        task.setPriority(request.getPriority());
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
            if (request.getStatus() == TaskStatus.DONE) {
                if (request.getCompletedAt() == null) {
                    task.setCompletedAt(OffsetDateTime.now());
                }
            } else {
                task.setCompletedAt(null);
            }
        }
        Task updatedTask = taskRepository.save(task);
        TaskResponse response = taskMapper.toTaskResponse(updatedTask);
        syncTasksToRedis(userId);
        
        // Xử lý update event nếu có
        if (request.getEventId() != null && request.getEventUpdateRequest() != null) {
            handleEventUpdate(task, request.getEventId(), request.getEventUpdateRequest(), oldStartTime);
        }
        
        return response;
        }catch (Exception e){
            throw new BaseException(ErrorCode.DATABASE_QUERY_ERROR);
        }
    }
    
    private void handleEventUpdate(Task task, Long eventId, EventUpdateRequest eventUpdateRequest, OffsetDateTime oldStartTime) {
        eventRepository.findByEventId(eventId).ifPresent(event -> {
            boolean reminderChanged = false;

            eventMapper.updateEvent(event, eventUpdateRequest);

            // Kiểm tra thay đổi reminder
            if (eventUpdateRequest.getReminderMinutesBefore() != null) {
                reminderChanged = true;
            }
            // Kiểm tra thay đổi startTime
            if (oldStartTime != null && task.getStartTime() != null && !oldStartTime.equals(task.getStartTime())) {
                reminderChanged = true;
            }

            eventRepository.save(event);

            // Nếu có thay đổi về reminder → tính lại ZSET
            if (reminderChanged) {
                EventReminderData reminderData = EventReminderData.builder()
                        .eventId(event.getEventId())
                        .taskId(task.getTaskId())
                        .eventDescription(event.getEventDescription())
                        .linkEvent(event.getLinkEvent())
                        .location(event.getLocation())
                        .isOnline(event.getIsOnline())
                        .reminderMinutesBefore(event.getReminderMinutesBefore())
                        .startTime(task.getStartTime())
                        .build();
                eventReminderRedisService.updateEventReminder(reminderData);
                log.info("Updated event reminder in Redis for eventId: {}", event.getEventId());
            }

            // Nếu có invitedEmails mới → gửi message cập nhật email service
            if (eventUpdateRequest.getInvitedEmails() != null) {
                EventUpdateMessage updateMessage = EventUpdateMessage.builder()
                        .eventId(event.getEventId())
                        .invitedEmails(eventUpdateRequest.getInvitedEmails())
                        .build();
                eventEmailProducer.sendEventUpdate(updateMessage);
                log.info("Sent event update message for eventId: {} with {} invitedEmails",
                        event.getEventId(), eventUpdateRequest.getInvitedEmails().size());
            }
        });
    }

    @Override
    public TaskResponse updateTaskStatus(Long taskId, TaskStatus status, Long userId) {
        try{
        Task task = taskRepository.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        task.setStatus(status);
        if (status == TaskStatus.DONE) {
            task.setCompletedAt(OffsetDateTime.now());
        }

        Task updatedTask = taskRepository.save(task);
        TaskResponse response = taskMapper.toTaskResponse(updatedTask);
        syncTasksToRedis(userId);
        return response;
        }catch (Exception e){
            throw new BaseException(ErrorCode.DATABASE_QUERY_ERROR);
        }
    }

    @Override
    public void deleteTask(Long taskId, Long eventId, Long userId) {
        try {
            Task task = taskRepository.findByTaskIdAndUserId(taskId, userId)
                    .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

            // Nếu có eventId → xóa event cụ thể và gửi message để email service xóa invitedEmails
            if (eventId != null) {
                eventRepository.findById(eventId).ifPresent(event -> {
                    eventReminderRedisService.deleteEventReminder(event.getEventId());
                    eventRepository.delete(event);
                    eventEmailProducer.sendEventDelete(
                            com.microsv.task_service.dto.message.EventDeleteMessage.builder()
                                    .eventId(eventId)
                                    .build()
                    );
                });
            } else {
                // Không có eventId → xóa tất cả event liên quan (cascade)
                eventRepository.findByTaskId(taskId).ifPresent(event -> {
                    eventReminderRedisService.deleteEventReminder(event.getEventId());
                    eventRepository.delete(event);
                    eventEmailProducer.sendEventDelete(
                            com.microsv.task_service.dto.message.EventDeleteMessage.builder()
                                    .eventId(event.getEventId())
                                    .build()
                    );
                });
            }

            taskRepository.delete(task);
            syncTasksToRedis(userId);
        }catch (Exception e){
            throw new BaseException(ErrorCode.DATABASE_QUERY_ERROR);
        }
    }

    private void syncTasksToRedis(Long userId) {
        try {
            List<Task> tasks = taskRepository.findAllByUserId(userId);
            claudeTaskConvertService.syncUserTasks(userId, tasks);
        } catch (Exception e) {
            log.error("Failed to sync tasks to Redis for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public List<TaskResponse> getUpcomingTasks(Long userId, Integer hours) {
        try {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime deadlineLimit = now.plusHours(hours != null ? hours : 24);

            List<Task> tasks = taskRepository.findAllByUserIdAndStatus(userId, TaskStatus.TODO)
                    .stream()
                    .filter(task -> task.getDeadline() != null &&
                            task.getDeadline().isAfter(now) &&
                            task.getDeadline().isBefore(deadlineLimit))
                    .collect(Collectors.toList());

            if (tasks.isEmpty()) {
                return List.of();
            }
            List<Long> taskIds = tasks.stream().map(Task::getTaskId).collect(Collectors.toList());
            Map<Long, Event> eventMap = eventRepository.findByTaskIdIn(taskIds).stream()
                    .collect(Collectors.toMap(Event::getTaskId, e -> e));
            return taskMapper.toTaskResponses(tasks, eventMap);
        }
        catch (Exception e){
            throw new BaseException(ErrorCode.DATABASE_QUERY_ERROR);
        }
    }

    @Override
    public List<TaskResponse> getAllTaskToday(Long userId) {
        return taskRepository.getAllTaskToday(userId).stream()
                .map(taskMapper::tupleToTaskResponse).toList();
    }

    @Override
    public List<TaskResponse> getOverdueTaskToday(Long userId) {
        return taskRepository.getOverdueTasksToday(userId).stream()
                .map(taskMapper::tupleToTaskResponse).toList();    }

    @Override
    public List<TaskResponse> getCompetedTaskToday(Long userId) {
        return taskRepository.getCompletedTasksToday(userId).stream()
                .map(taskMapper::tupleToTaskResponse).toList();    }

    @Override
    public Double getCompletionRateThisWeek(Long userId) {
        return taskRepository.getCompletionRateThisWeekByUser(userId);
    }

    @Override
    public Double getFreeHoursThisWeek(Long userId) {
        return taskRepository.getFreeHoursThisWeek(userId);
    }

    @Override
    public StatusTaskWeekResponse getWeeklyTaskRates(Long userId) {
        return taskMapper.tupleToStatusTaskWeekResponse(taskRepository.getWeeklyTaskRates(userId));
    }

    @Override
    public List<DailyTaskCountResponse> getWeeklyTaskDistribution(Long userId) {
        return taskRepository.getWeeklyTaskDistribution(userId).stream().map(taskMapper::tupleToDailyTaskCountResponse).toList();
    }

    @Override
    public List<TaskTimelineResponse> getTaskCreationTimeline(Long userId) {
        return taskRepository.getTaskCreationTimeline(userId).stream().map(taskMapper::tupleToTaskTimelineResponse).toList();
    }

    @Override
    public Long countActiveUsersThisWeek() {
        return taskRepository.countActiveUsersThisWeek();
    }

    @Override
    public Long countTasksCreatedThisWeek() {
        return taskRepository.countTasksCreatedThisWeek();
    }

    @Override
    public List<DailyCompletedTasksResponse> getCompletedTasksByDayThisWeek() {
        return taskRepository.getCompletedTasksByDayThisWeek().stream()
                .map(taskMapper::tupleToDailyCompletedTasksResponse).toList();
    }

    @Override
    public List<TaskPriorityCountResponse> countTasksByPriority() {
        return taskRepository.countTasksByPriority().stream()
                .map(taskMapper::tupleToTaskPriorityCountResponse).toList();
    }

    @Override
    public List<Task> findTaskByTitle(String title) {
        return taskRepository.findByTitle(title);
    }

    @Override
    public List<TaskResponse> getFilteredTasks(Long userId, TaskStatus status, PriorityLevel priority,
                                              LocalDate fromDate, LocalDate toDate, Integer limit) {
        OffsetDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay().atZone(ZoneId.systemDefault()).toOffsetDateTime() : null;
        OffsetDateTime toDateTime = toDate != null ? toDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toOffsetDateTime() : null;

        // Convert enums to String for native query
        String statusStr = status != null ? status.name() : null;
        String priorityStr = priority != null ? priority.name() : null;

        int pageLimit = limit != null ? limit : 20;
        Pageable pageable = PageRequest.of(0, pageLimit);

        List<Task> tasks = taskRepository.findFilteredTasks(userId, statusStr, priorityStr, fromDateTime, toDateTime, pageable);
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<Long> taskIds = tasks.stream().map(Task::getTaskId).collect(Collectors.toList());
        Map<Long, Event> eventMap = eventRepository.findByTaskIdIn(taskIds).stream()
                .collect(Collectors.toMap(Event::getTaskId, e -> e));
        return taskMapper.toTaskResponses(tasks, eventMap);
    }

    @Override
    public TaskStatisticResponse getTaskStatistics(Long userId) {
        Long totalTasks = taskRepository.countByUserIdAndStatus(userId, null);
        Long todoCount = taskRepository.countByUserIdAndStatus(userId, TaskStatus.TODO);
        Long inProgressCount = taskRepository.countByUserIdAndStatus(userId, TaskStatus.IN_PROGRESS);
        Long doneCount = taskRepository.countByUserIdAndStatus(userId, TaskStatus.DONE);

        return TaskStatisticResponse.builder()
                .totalTasks(totalTasks)
                .todoCount(todoCount)
                .inProgressCount(inProgressCount)
                .doneCount(doneCount)
                .completionRate(totalTasks > 0 ? (double) doneCount / totalTasks * 100 : 0)
                .build();
    }

    @Override
    public void syncTasksToCache(Long userId) {
        try {
            List<Task> tasks = taskRepository.findAllByUserId(userId);
            claudeTaskConvertService.syncUserTasks(userId, tasks);
        } catch (Exception e) {
            log.error("Failed to sync tasks to cache for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public Long countEventsInCurrentYear() {
        Long total = eventRepository.countEventsInCurrentYear();
        return total != null ? total : 0L;
    }

    @Override
    public Long countPersonalEventsInCurrentYear() {
        Long count = eventRepository.countPersonalEventsInCurrentYear();
        return count != null ? count : 0L;
    }

    @Override
    public Long countGroupEventsInCurrentYear() {
        Long count = eventRepository.countGroupEventsInCurrentYear();
        return count != null ? count : 0L;
    }

    @Override
    public List<EventResponse> countEventsByPriorityInCurrentYear() {
        List<Tuple> results = eventRepository.countEventsByPriorityInCurrentYear();
        return results.stream()
                .map(tuple -> EventResponse.builder()
                        .priority(tuple.get("priorityLevel", String.class))
                        .taskId(tuple.get("eventCount", Long.class))
                        .build())
                .toList();
    }

    @Override
    public List<EventResponse> getUpcomingEvents(Long userId, Integer limit) {
        int fetchLimit = limit != null ? limit : 10;
        List<Tuple> results = eventRepository.findUpcomingEventsByUserId(userId, fetchLimit);
        return results.stream()
                .map(this::tupleToEventResponse)
                .toList();
    }

    @Override
    public List<EventResponse> getAllEventsByUser(Long userId) {
        List<Tuple> results = eventRepository.findAllEventsByUserId(userId);
        return results.stream()
                .map(this::tupleToEventResponse)
                .toList();
    }

    @Override
    public TaskResponse deleteEvent(Long taskId, Long eventId, Long userId) {
        deleteTask(taskId, eventId, userId);
        return TaskResponse.builder().taskId(taskId).build();
    }

    private EventResponse tupleToEventResponse(Tuple tuple) {
        return EventResponse.builder()
                .eventId(tuple.get("eventId", Long.class))
                .taskId(tuple.get("taskId", Long.class))
                .title(tuple.get("title", String.class))
                .description(tuple.get("description", String.class))
                .startTime(toOffsetDateTime(tuple.get("startTime")))
                .deadline(toOffsetDateTime(tuple.get("deadline")))
                .status(tuple.get("status", String.class))
                .priority(tuple.get("priority", String.class))
                .eventDescription(tuple.get("eventDescription", String.class))
                .linkEvent(tuple.get("linkEvent", String.class))
                .location(tuple.get("location", String.class))
                .isOnline(tuple.get("isOnline", Boolean.class))
                .reminderMinutesBefore(tuple.get("reminderMinutesBefore", Integer.class))
                .invitedEmails(jsonToList(tuple.get("invitedEmails", String.class)))
                .build();
    }

    private List<String> jsonToList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime) return (OffsetDateTime) value;
        if (value instanceof java.time.Instant) return ((java.time.Instant) value).atOffset(java.time.ZoneOffset.UTC);
        if (value instanceof java.sql.Timestamp) return ((java.sql.Timestamp) value).toInstant().atOffset(java.time.ZoneOffset.UTC);
        if (value instanceof String) {
            try {
                return OffsetDateTime.parse((String) value);
            } catch (Exception ignored) {}
        }
        return null;
    }

}