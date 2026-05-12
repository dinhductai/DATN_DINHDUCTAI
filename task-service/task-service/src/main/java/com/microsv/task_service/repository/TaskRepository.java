package com.microsv.task_service.repository;

import com.microsv.task_service.dto.response.DailyCompletedTasksResponse;
import com.microsv.task_service.entity.Task;
import com.microsv.task_service.enumeration.PriorityLevel;
import com.microsv.task_service.enumeration.TaskStatus;
import com.microsv.task_service.repository.query.TaskQuery;
import jakarta.persistence.Tuple;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.microsv.task_service.repository.query.TaskQuery.*;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findAllByDeadlineBetweenAndStatus(
            OffsetDateTime startDeadline,
            OffsetDateTime endDeadline,
            TaskStatus status
    );
    Optional<Task>  findByTaskIdAndUserId(Long taskId, long userId);

    List<Task> findAllByUserId(Long userId);

    List<Task> findAllByUserIdAndStatus(Long userId, TaskStatus status);

    Long countByUserIdAndStatus(Long userId, TaskStatus status);

    @Query(value = TaskQuery.GET_TASKS_IN_TODAY,nativeQuery = true)
    List<Tuple> getAllTaskToday(@Param("userId") Long userId);

    @Query(value = TaskQuery.GET_OVERDUE_TASK_TODAY, nativeQuery = true)
    List<Tuple> getOverdueTasksToday(@Param("userId") Long userId);

    @Query(value = TaskQuery.GET_COMPLETED_TASK_TODAY,nativeQuery = true)
    List<Tuple> getCompletedTasksToday(@Param("userId") Long userId);

    @Query(value = TaskQuery.GET_COMPLETION_RATE_THIS_WEEK,nativeQuery = true)
    Double getCompletionRateThisWeekByUser(@Param("userId") Long userId);

    @Query(value = TaskQuery.GET_WEEKLY_TASK_STATUS_RATE,nativeQuery = true)
    Tuple getWeeklyTaskRates(@Param("userId") Long userId);

    @Query(value = TaskQuery.GET_WEEKLY_TASK_DISTRIBUTION, nativeQuery = true)
    List<Tuple> getWeeklyTaskDistribution(@Param("userId") Long userId);

    @Query(value = TaskQuery.GET_TASK_CREATION_TIMELINE, nativeQuery = true)
    List<Tuple> getTaskCreationTimeline(@Param("userId") Long userId);

    @Query(value = COUNT_ACTIVE_USERS_THIS_WEEK, nativeQuery = true)
    Long countActiveUsersThisWeek();

    @Query(value = COUNT_TASKS_CREATED_THIS_WEEK, nativeQuery = true)
    Long countTasksCreatedThisWeek();

    @Query(value = GET_COMPLETED_TASKS_BY_DAY_THIS_WEEK, nativeQuery = true)
    List<Tuple> getCompletedTasksByDayThisWeek();


    @Query(value = COUNT_TASKS_BY_PRIORITY, nativeQuery = true)
    List<Tuple> countTasksByPriority();

    @Query(value = TaskQuery.COUNT_TASKS_THIS_MONTH, nativeQuery = true)
    Long countTasksThisMonth();

    @Query(value = TaskQuery.COUNT_TASKS_LAST_MONTH, nativeQuery = true)
    Long countTasksLastMonth();

    List<Task> findByTitle(String title);

    @Query(value = """
        SELECT * FROM tasks t
        WHERE t.user_id = :userId
        AND (CAST(:status AS VARCHAR) IS NULL OR t.status = CAST(:status AS VARCHAR))
        AND (CAST(:priority AS VARCHAR) IS NULL OR t.priority = CAST(:priority AS VARCHAR))
        AND (:fromDate IS NULL OR t.deadline >= :fromDate)
        AND (:toDate IS NULL OR t.deadline <= :toDate)
        ORDER BY
            CASE WHEN t.status = 'TODO' THEN 0 ELSE 1 END,
            CASE t.priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END,
            t.deadline ASC
        """, nativeQuery = true)
    List<Task> findFilteredTasks(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable
    );

    @Query("""
        SELECT t FROM Task t
        WHERE t.startTime BETWEEN :startTime AND :endTime
        AND t.status IN :statuses
        ORDER BY t.startTime ASC
        """)
    List<Task> findTasksStartingNow(
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("statuses") Collection<TaskStatus> statuses
    );

    @Query("""
        SELECT t FROM Task t
        WHERE t.userId = :userId
        AND t.startTime >= :since
        AND t.startTime <= :now
        ORDER BY t.startTime DESC
        """)
    List<Task> findRecentTasksByUserId(
            @Param("userId") Long userId,
            @Param("since") OffsetDateTime since,
            @Param("now") OffsetDateTime now
    );

    @Query(value = """
        SELECT t.* FROM tasks t
        WHERE t.user_id = :userId
        AND t.deadline IS NOT NULL
        AND (t.deadline AT TIME ZONE 'Asia/Ho_Chi_Minh')::date
            BETWEEN (NOW() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date - INTERVAL '14 days'
            AND     (NOW() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '14 days'
        ORDER BY t.deadline ASC
        """, nativeQuery = true)
    List<Task> findTasksForAiSync(@Param("userId") Long userId);
}
