-- 🧪 TEST SCRIPT: Verify Free Hours Logic
-- Run này trong postgres task_db

-- ========================================
-- SETUP: Tạo test data
-- ========================================

-- Xóa test data cũ (nếu có)
DELETE FROM tasks WHERE user_id = 999 AND title LIKE 'TEST%';

-- Tạo user test (nếu chưa có trong user_service)
-- INSERT INTO users (user_id, username, email) VALUES (999, 'test_user', 'test@example.com');

-- Tạo tasks test với các status khác nhau
INSERT INTO tasks (title, description, deadline, status, priority, created_at, completed_at, user_id)
VALUES 
    -- Task DONE: 2 giờ busy (Monday 9AM → 11AM)
    ('TEST_DONE_2H', 
     'Should count 2h busy', 
     DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '1 day',
     'DONE', 
     'HIGH', 
     DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '9 hours',      -- Monday 9AM
     DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '11 hours',     -- Monday 11AM
     999),
    
    -- Task IN_PROGRESS: 4 giờ busy (Tuesday 10AM → now)
    -- Chỉ test nếu hiện tại là Tuesday afternoon
    ('TEST_IN_PROGRESS_4H', 
     'Should count 4h busy if current time is Tuesday 2PM', 
     DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '2 days',
     'IN_PROGRESS', 
     'MEDIUM', 
     DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '1 day 10 hours', -- Tuesday 10AM
     NULL,
     999),
    
    -- Task TODO: 0 giờ busy
    ('TEST_TODO_0H', 
     'Should count 0h busy (not started)', 
     DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '3 days',
     'TODO', 
     'LOW', 
     DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '2 days',
     NULL,
     999),
    
    -- Task DONE ngoài tuần này: không tính
    ('TEST_DONE_LAST_WEEK', 
     'Should NOT count (outside this week)', 
     DATE_TRUNC('week', CURRENT_DATE) - INTERVAL '7 days',
     'DONE', 
     'HIGH', 
     DATE_TRUNC('week', CURRENT_DATE) - INTERVAL '7 days',
     DATE_TRUNC('week', CURRENT_DATE) - INTERVAL '6 days',
     999);

-- ========================================
-- TEST 1: Verify inserted data
-- ========================================
SELECT 
    title,
    status,
    created_at,
    completed_at,
    EXTRACT(EPOCH FROM (COALESCE(completed_at, CURRENT_TIMESTAMP) - created_at)) / 3600.0 AS duration_hours
FROM tasks
WHERE user_id = 999
ORDER BY created_at;

-- Expected:
-- TEST_DONE_2H:           ~2h
-- TEST_IN_PROGRESS_4H:    ~4h (depends on current time)
-- TEST_TODO_0H:           NULL or future
-- TEST_DONE_LAST_WEEK:    ~24h (but outside this week)

-- ========================================
-- TEST 2: Run OLD query (để so sánh)
-- ========================================
WITH bounds AS (
    SELECT 
        DATE_TRUNC('week', CURRENT_DATE) AS week_start,
        DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days' AS week_end
), 
busy_seconds AS (
    SELECT COALESCE(SUM(
        EXTRACT(EPOCH FROM (
            LEAST(COALESCE(t.completed_at, CURRENT_TIMESTAMP), (SELECT week_end FROM bounds)) - 
            GREATEST(t.created_at, (SELECT week_start FROM bounds))
        ))
    ), 0) AS total_busy
    FROM tasks t
    WHERE t.user_id = 999
        AND t.created_at < (SELECT week_end FROM bounds)
        AND COALESCE(t.completed_at, CURRENT_TIMESTAMP) > (SELECT week_start FROM bounds)
)
SELECT 
    total_busy / 3600.0 AS busy_hours_old_query,
    (7 * 24 * 3600 - total_busy) / 3600.0 AS free_hours_old_query
FROM busy_seconds;

-- Expected (OLD - SAI):
-- busy_hours: ~6h (tính cả TODO đến hiện tại - SAI!)
-- free_hours: ~162h (168 - 6)

-- ========================================
-- TEST 3: Run NEW query (đã fix)
-- ========================================
WITH bounds AS (
    SELECT 
        DATE_TRUNC('week', CURRENT_DATE) AS week_start,
        DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days' AS week_end
), 
task_intervals AS (
    SELECT 
        GREATEST(t.created_at, (SELECT week_start FROM bounds)) AS start_time,
        LEAST(t.completed_at, (SELECT week_end FROM bounds)) AS end_time
    FROM tasks t
    WHERE t.user_id = 999
        AND t.status = 'DONE'
        AND t.completed_at IS NOT NULL
        AND t.created_at < (SELECT week_end FROM bounds)
        AND t.completed_at > (SELECT week_start FROM bounds)
    
    UNION ALL
    
    SELECT 
        GREATEST(t.created_at, (SELECT week_start FROM bounds)) AS start_time,
        LEAST(CURRENT_TIMESTAMP, (SELECT week_end FROM bounds)) AS end_time
    FROM tasks t
    WHERE t.user_id = 999
        AND t.status = 'IN_PROGRESS'
        AND t.created_at < (SELECT week_end FROM bounds)
        AND CURRENT_TIMESTAMP > (SELECT week_start FROM bounds)
),
busy_seconds AS (
    SELECT COALESCE(SUM(
        EXTRACT(EPOCH FROM (end_time - start_time))
    ), 0) AS total_busy
    FROM task_intervals
    WHERE end_time > start_time
),
work_hours AS (
    SELECT 70 * 3600 AS total_work_seconds
)
SELECT 
    total_busy / 3600.0 AS busy_hours_new_query,
    GREATEST(0, (total_work_seconds - total_busy) / 3600.0) AS free_hours_new_query
FROM busy_seconds, work_hours;

-- Expected (NEW - ĐÚNG):
-- busy_hours: ~2h (chỉ DONE, không tính TODO)
-- free_hours: ~68h (70 - 2)

-- ========================================
-- TEST 4: Breakdown by status
-- ========================================
WITH bounds AS (
    SELECT 
        DATE_TRUNC('week', CURRENT_DATE) AS week_start,
        DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days' AS week_end
)
SELECT 
    t.status,
    COUNT(*) AS task_count,
    SUM(
        EXTRACT(EPOCH FROM (
            LEAST(COALESCE(t.completed_at, CURRENT_TIMESTAMP), (SELECT week_end FROM bounds)) - 
            GREATEST(t.created_at, (SELECT week_start FROM bounds))
        ))
    ) / 3600.0 AS total_hours
FROM tasks t
WHERE t.user_id = 999
    AND t.created_at < (SELECT week_end FROM bounds)
    AND COALESCE(t.completed_at, CURRENT_TIMESTAMP) > (SELECT week_start FROM bounds)
GROUP BY t.status
ORDER BY t.status;

-- Expected:
-- DONE:         1 task,  ~2h
-- IN_PROGRESS:  1 task,  ~4h
-- TODO:         1 task,  ~Xh (SAI - không nên tính!)

-- ========================================
-- TEST 5: Compare OLD vs NEW
-- ========================================
SELECT 
    'OLD Query (SAI)' AS query_type,
    total_busy / 3600.0 AS busy_hours,
    (7 * 24 * 3600 - total_busy) / 3600.0 AS free_hours
FROM (
    SELECT COALESCE(SUM(
        EXTRACT(EPOCH FROM (
            LEAST(COALESCE(t.completed_at, CURRENT_TIMESTAMP), DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days') - 
            GREATEST(t.created_at, DATE_TRUNC('week', CURRENT_DATE))
        ))
    ), 0) AS total_busy
    FROM tasks t
    WHERE t.user_id = 999
        AND t.created_at < DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days'
        AND COALESCE(t.completed_at, CURRENT_TIMESTAMP) > DATE_TRUNC('week', CURRENT_DATE)
) old_query

UNION ALL

SELECT 
    'NEW Query (ĐÚNG)' AS query_type,
    total_busy / 3600.0 AS busy_hours,
    GREATEST(0, (70 * 3600 - total_busy) / 3600.0) AS free_hours
FROM (
    SELECT COALESCE(SUM(
        EXTRACT(EPOCH FROM (end_time - start_time))
    ), 0) AS total_busy
    FROM (
        SELECT 
            GREATEST(t.created_at, DATE_TRUNC('week', CURRENT_DATE)) AS start_time,
            LEAST(t.completed_at, DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days') AS end_time
        FROM tasks t
        WHERE t.user_id = 999
            AND t.status = 'DONE'
            AND t.completed_at IS NOT NULL
            AND t.created_at < DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days'
            AND t.completed_at > DATE_TRUNC('week', CURRENT_DATE)
        
        UNION ALL
        
        SELECT 
            GREATEST(t.created_at, DATE_TRUNC('week', CURRENT_DATE)) AS start_time,
            LEAST(CURRENT_TIMESTAMP, DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days') AS end_time
        FROM tasks t
        WHERE t.user_id = 999
            AND t.status = 'IN_PROGRESS'
            AND t.created_at < DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days'
            AND CURRENT_TIMESTAMP > DATE_TRUNC('week', CURRENT_DATE)
    ) intervals
    WHERE end_time > start_time
) new_query;

-- ========================================
-- CLEANUP: Xóa test data
-- ========================================
-- DELETE FROM tasks WHERE user_id = 999 AND title LIKE 'TEST%';
