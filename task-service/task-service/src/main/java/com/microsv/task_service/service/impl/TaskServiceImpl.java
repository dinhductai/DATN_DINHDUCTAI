package com.microsv.task_service.service.impl;

import com.microsv.common.enumeration.ErrorCode;
import com.microsv.common.exception.BaseException;
import com.microsv.task_service.dto.message.EventCreationMessage;
import com.microsv.task_service.dto.message.EventReminderMessage;
import com.microsv.task_service.dto.request.EventCreationRequest;
import com.microsv.task_service.dto.request.TaskCreationRequest;
import com.microsv.task_service.dto.request.TaskUpdateRequest;
import com.microsv.task_service.dto.response.*;
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

    @Override
    @Transactional
    public TaskResponse createTask(TaskCreationRequest request, Long userId) {
        try {
//            DateUtil.ValidateDeadline(request.getDeadline());
            Task savedTask = taskRepository.save(taskMapper.taskCreationRequestToTask(request, userId));
            
            if (Boolean.TRUE.equals(request.getIsEvent()) && request.getEventCreationRequest() != null) {
                Event savedEvent = eventRepository.save(eventMapper.toEvent(request.getEventCreationRequest(), savedTask.getTaskId()));
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
            return tasks.stream()
                    .map(taskMapper::toTaskResponse)
                    .collect(Collectors.toList());
        }catch (Exception e){
        throw new BaseException(ErrorCode.DATABASE_QUERY_ERROR);
    }
    }

    @Override
    public List<TaskResponse> getTasksByStatus(Long userId, TaskStatus status) {
        try {
            List<Task> tasks = taskRepository.findAllByUserIdAndStatus(userId, status);
            return tasks.stream()
                    .map(taskMapper::toTaskResponse)
                    .collect(Collectors.toList());
        }catch (Exception e){
            throw new BaseException(ErrorCode.DATABASE_QUERY_ERROR);
        }
    }



    @Override
    public TaskResponse updateTask(Long taskId, TaskUpdateRequest request, Long userId) {
        try{
        Task task = taskRepository.findByTaskIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());

//        DateUtil.ValidateDeadline(request.getDeadline());

        task.setDeadline(request.getDeadline());
        task.setPriority(request.getPriority());
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
            if (request.getStatus() == TaskStatus.DONE) {
                task.setCompletedAt(OffsetDateTime.now());
            }
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
    public void deleteTask(Long taskId, Long userId) {
        try {
            Task task = taskRepository.findByTaskIdAndUserId(taskId, userId)
                    .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

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

            return tasks.stream()
                    .map(taskMapper::toTaskResponse)
                    .collect(Collectors.toList());
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
        return tasks.stream().map(taskMapper::toTaskResponse).collect(Collectors.toList());
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

}