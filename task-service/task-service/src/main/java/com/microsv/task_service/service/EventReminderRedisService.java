package com.microsv.task_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsv.task_service.dto.EventReminderData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventReminderRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // Key cho ZSET lưu các reminder sắp tới
    private static final String EVENT_REMINDER_ZSET = "event:reminders:zset";
    
    // Key prefix để lưu chi tiết event reminder data
    private static final String EVENT_REMINDER_DATA_PREFIX = "event:reminder:data:";

    /**
     * Lưu event reminder vào Redis khi tạo event
     * - Thêm vào ZSET với score = thời gian reminder đến (startTime - reminderMinutesBefore)
     * - Lưu chi tiết event data vào Hash
     */
    public void saveEventReminder(EventReminderData data) {
        if (data.getStartTime() == null || data.getReminderMinutesBefore() == null) {
            log.warn("Cannot save event reminder: startTime or reminderMinutesBefore is null for eventId: {}", data.getEventId());
            return;
        }

        // Tính thời điểm reminder sẽ được kích hoạt: startTime - reminderMinutesBefore
        OffsetDateTime reminderTime = data.getStartTime().minusMinutes(data.getReminderMinutesBefore());
        
        // Nếu thời điểm reminder đã qua, không lưu
        if (reminderTime.isBefore(OffsetDateTime.now())) {
            log.info("Reminder time already passed for eventId: {}, skipping", data.getEventId());
            return;
        }

        // Key để lưu chi tiết event data
        String dataKey = EVENT_REMINDER_DATA_PREFIX + data.getEventId();

        try {
            // Lưu chi tiết event data vào Redis
            String jsonData = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(dataKey, jsonData, Duration.ofDays(30));
            
            // Thêm vào ZSET với score = thời gian reminder (epoch millis)
            double score = reminderTime.toInstant().toEpochMilli();
            redisTemplate.opsForZSet().add(EVENT_REMINDER_ZSET, data.getEventId().toString(), score);
            
            log.info("Saved event reminder to Redis - eventId: {}, startTime: {}, reminderTime: {}, score: {}", 
                    data.getEventId(), data.getStartTime(), reminderTime, score);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event reminder data for eventId: {}", data.getEventId(), e);
        } catch (Exception e) {
            log.error("Failed to save event reminder to Redis for eventId: {}", data.getEventId(), e);
        }
    }

    /**
     * Lấy tất cả các event cần reminder (có thời gian <= now)
     * Sử dụng ZRANGEBYSCORE để lấy các event có score <= hiện tại
     */
    public Set<Object> getDueReminders() {
        long nowMillis = OffsetDateTime.now().toInstant().toEpochMilli();
        
        // Lấy tất cả event có score <= now (đã đến giờ reminder)
        Set<Object> eventIds = redisTemplate.opsForZSet().rangeByScore(EVENT_REMINDER_ZSET, 0, nowMillis);
        
        if (eventIds != null && !eventIds.isEmpty()) {
            log.info("Found {} due reminders at {}", eventIds.size(), OffsetDateTime.now());
        }
        
        return eventIds;
    }

    /**
     * Lấy chi tiết event reminder data từ Redis
     */
    public EventReminderData getEventReminderData(Long eventId) {
        String dataKey = EVENT_REMINDER_DATA_PREFIX + eventId;
        
        try {
            Object data = redisTemplate.opsForValue().get(dataKey);
            if (data != null) {
                if (data instanceof String) {
                    return objectMapper.readValue((String) data, EventReminderData.class);
                }
                // Handle case where data might be already deserialized
                return objectMapper.convertValue(data, EventReminderData.class);
            }
        } catch (Exception e) {
            log.error("Failed to get event reminder data for eventId: {}", eventId, e);
        }
        
        return null;
    }

    /**
     * Xóa reminder đã được xử lý (atomic operation)
     * - Xóa khỏi ZSET
     * - Xóa chi tiết data
     */
    public void removeEventReminder(Long eventId) {
        String dataKey = EVENT_REMINDER_DATA_PREFIX + eventId;
        
        try {
            // Atomic: xóa cùng lúc ZSET entry và data
            redisTemplate.delete(dataKey);
            redisTemplate.opsForZSet().remove(EVENT_REMINDER_ZSET, eventId.toString());
            
            log.info("Removed event reminder from Redis - eventId: {}", eventId);
        } catch (Exception e) {
            log.error("Failed to remove event reminder from Redis for eventId: {}", eventId, e);
        }
    }

    /**
     * Cập nhật reminder khi event được update
     * Xóa reminder cũ và tạo mới nếu cần
     */
    public void updateEventReminder(EventReminderData data) {
        removeEventReminder(data.getEventId());
        saveEventReminder(data);
    }

    /**
     * Xóa reminder khi event bị xóa
     */
    public void deleteEventReminder(Long eventId) {
        removeEventReminder(eventId);
    }
}
