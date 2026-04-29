package com.microsv.task_service.service;

import com.microsv.task_service.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class TaskCacheService {

    private static final String TASK_CACHE_PREFIX = "user:tasks:";
    private static final String AI_TASK_JSON_PREFIX = "ai:tasks:json:";
    private static final Duration TASK_CACHE_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, Object> redisTemplate;

    public TaskCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Cache all tasks for a user (used after CRUD operations)
     */
    public void cacheUserTasks(Long userId, List<Task> tasks) {
        String key = TASK_CACHE_PREFIX + userId;
        try {
            redisTemplate.opsForValue().set(key, tasks, TASK_CACHE_TTL);
            log.info("Cached {} tasks for user {}", tasks.size(), userId);
        } catch (Exception e) {
            log.error("Failed to cache tasks for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Cache AI-friendly task JSON string
     */
    public void cacheTaskJson(Long userId, String taskJson) {
        String key = AI_TASK_JSON_PREFIX + userId;
        try {
            redisTemplate.opsForValue().set(key, taskJson, TASK_CACHE_TTL);
            log.info("Cached AI task JSON for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to cache AI task JSON for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Get cached tasks for user
     */
    @SuppressWarnings("unchecked")
    public List<Task> getCachedTasks(Long userId) {
        String key = TASK_CACHE_PREFIX + userId;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Retrieved cached tasks for user {}", userId);
                return (List<Task>) cached;
            }
        } catch (Exception e) {
            log.error("Failed to get cached tasks for user {}: {}", userId, e.getMessage());
        }
        return null;
    }

    /**
     * Get cached AI task JSON for user
     */
    public String getCachedTaskJson(Long userId) {
        String key = AI_TASK_JSON_PREFIX + userId;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Retrieved cached AI task JSON for user {}", userId);
                return cached.toString();
            }
        } catch (Exception e) {
            log.error("Failed to get cached AI task JSON for user {}: {}", userId, e.getMessage());
        }
        return null;
    }

    /**
     * Invalidate cache when tasks change
     */
    public void invalidateUserTaskCache(Long userId) {
        String taskKey = TASK_CACHE_PREFIX + userId;
        String jsonKey = AI_TASK_JSON_PREFIX + userId;
        try {
            redisTemplate.delete(taskKey);
            redisTemplate.delete(jsonKey);
            log.info("Invalidated task cache for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to invalidate cache for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Check if cache exists for user
     */
    public boolean hasCachedData(Long userId) {
        String taskKey = TASK_CACHE_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(taskKey));
    }

    /**
     * Get cached AI task JSON, refresh TTL if exists, return null if not exists
     * Use this when user logs in - if cache exists, refresh TTL to 24h; otherwise return null
     */
    public String getCachedTaskJsonWithRefresh(Long userId) {
        String key = AI_TASK_JSON_PREFIX + userId;
        try {
            // Try to get directly - if exists, refresh TTL
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                // Cache exists - refresh TTL to 24h
                redisTemplate.expire(key, TASK_CACHE_TTL);
                log.info("Cache HIT - Retrieved cached AI task JSON for user {} and refreshed TTL", userId);
                return cached.toString();
            }
            log.info("Cache MISS for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to get cached AI task JSON for user {}: {}", userId, e.getMessage(), e);
        }
        return null;
    }
}
