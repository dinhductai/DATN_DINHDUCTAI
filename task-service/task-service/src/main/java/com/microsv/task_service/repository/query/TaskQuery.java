package com.microsv.task_service.repository.query;

public class TaskQuery {
    public static final String GET_TASKS_IN_TODAY =
            "SELECT t.task_id AS taskId, t.title AS title, t.description AS description, " +
                    "t.deadline AS deadline, t.status AS status, t.priority AS priority, " +
                    "t.start_time AS startTime, t.completed_at AS completedAt, t.user_id AS userId " +
                    "FROM tasks t " +
                    "WHERE t.user_id = :userId " +
                    "AND t.deadline IS NOT NULL " +
                    "AND DATE_TRUNC('day', t.deadline AT TIME ZONE 'Asia/Bangkok') = DATE_TRUNC('day', NOW() AT TIME ZONE 'Asia/Bangkok') " +
                    "ORDER BY t.deadline ASC";


    public static final String GET_OVERDUE_TASK_TODAY =
            "SELECT t.task_id AS taskId, t.title AS title, t.description AS description, " +
                    "t.deadline AS deadline, t.status AS status, t.priority AS priority, " +
                    "t.start_time AS startTime, t.completed_at AS completedAt, t.user_id AS userId " +
                    "FROM tasks t " +
                    "WHERE t.user_id = :userId " +
                    "AND t.deadline IS NOT NULL " +
                    "AND DATE_TRUNC('day', t.deadline AT TIME ZONE 'Asia/Bangkok') = DATE_TRUNC('day', NOW() AT TIME ZONE 'Asia/Bangkok') " +
                    "AND t.deadline < NOW() " +
                    "AND t.status <> 'DONE' " +
                    "ORDER BY t.deadline ASC";

    public static final String GET_COMPLETED_TASK_TODAY =
            "SELECT t.task_id AS taskId, t.title AS title, t.description AS description, " +
                    "t.deadline AS deadline, t.status AS status, t.priority AS priority, " +
                    "t.start_time AS startTime, t.completed_at AS completedAt, t.user_id AS userId " +
                    "FROM tasks t " +
                    "WHERE t.user_id = :userId " +
                    "AND t.completed_at IS NOT NULL " +
                    "AND DATE_TRUNC('day', t.completed_at AT TIME ZONE 'Asia/Bangkok') = DATE_TRUNC('day', NOW() AT TIME ZONE 'Asia/Bangkok') " +
                    "AND t.status = 'DONE' " +
                    "ORDER BY t.completed_at ASC";


    public static final String GET_COMPLETION_RATE_THIS_WEEK =
            "SELECT \n" +
                    "   COALESCE(\n" +
                    "       (COUNT(CASE WHEN t.status = 'DONE' AND t.completed_at IS NOT NULL AND t.completed_at <= t.deadline THEN 1 END) * 100.0) / \n" +
                    "       NULLIF(COUNT(*), 0), \n" +
                    "       0\n" +
                    "   ) AS completion_rate\n" +
                    "FROM tasks t\n" +
                    "WHERE \n" +
                    "   t.user_id = :userId\n" +
                    "   AND t.deadline IS NOT NULL\n" +
                    "   AND DATE_TRUNC('week', t.deadline AT TIME ZONE 'Asia/Bangkok') = DATE_TRUNC('week', NOW() AT TIME ZONE 'Asia/Bangkok')";

    public static final String GET_FREE_HOURS_THIS_WEEK =
            "WITH week_bounds AS (\n" +
                    "    SELECT \n" +
                    "        DATE_TRUNC('week', CURRENT_DATE) AS week_start,\n" +
                    "        DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days' AS week_end\n" +
                    "),\n" +
                    "busy_hours AS (\n" +
                    "    SELECT COALESCE(SUM(\n" +
                    "        EXTRACT(EPOCH FROM (COALESCE(t.completed_at, t.deadline) - t.start_time)) / 3600.0\n" +
                    "    ), 0) AS used_hours\n" +
                    "    FROM tasks t\n" +
                    "    CROSS JOIN week_bounds w\n" +
                    "    WHERE \n" +
                    "        t.user_id = :userId\n" +
                    "        AND t.deadline >= w.week_start\n" +
                    "        AND t.deadline < w.week_end\n" +
                    ")\n" +
                    "SELECT \n" +
                    "    GREATEST(0, ROUND(168.0 - (SELECT used_hours FROM busy_hours), 2)) AS free_hours;";

    public static final String GET_WEEKLY_TASK_STATUS_RATE =
            "SELECT \n" +
                    "    COUNT(CASE WHEN t.status = 'DONE' THEN 1 END) AS completedRate,\n" +
                    "    COUNT(CASE WHEN t.status = 'IN_PROGRESS' THEN 1 END) AS inProgressRate,\n" +
                    "    COUNT(CASE WHEN t.status = 'TODO' THEN 1 END) AS todoRate\n" +
                    "FROM tasks t\n" +
                    "WHERE \n" +
                    "    t.user_id = :userId\n" +
                    "    AND t.deadline IS NOT NULL\n" +
                    "    AND DATE_TRUNC('week', t.deadline) = DATE_TRUNC('week', CURRENT_DATE)";

    public static final String GET_WEEKLY_TASK_DISTRIBUTION =
            "WITH current_week AS (\n" +
                    "        SELECT \n" +
                    "            DATE_TRUNC('week', CURRENT_DATE) AS week_start,\n" +
                    "            DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '7 days' AS week_end\n" +
                    "    ),\n" +
                    "    days AS (\n" +
                    "        SELECT \n" +
                    "            generate_series(\n" +
                    "                (SELECT week_start FROM current_week),\n" +
                    "                (SELECT week_end FROM current_week) - INTERVAL '1 day',\n" +
                    "                '1 day'\n" +
                    "            )::date AS day_date\n" +
                    "    )\n" +
                    "    SELECT \n" +
                    "        TO_CHAR(d.day_date, 'Day') AS day_name,\n" +
                    "        COUNT(t.task_id) AS task_count\n" +
                    "    FROM days d\n" +
                    "    LEFT JOIN tasks t \n" +
                    "        ON t.user_id = :userId\n" +
                    "        AND t.start_time::date = d.day_date\n" +
                    "    GROUP BY d.day_date\n" +
                    "    ORDER BY d.day_date;";

    public static final String GET_TASK_CREATION_TIMELINE =
            "WITH week_series AS (\n" +
                    "    SELECT \n" +
                    "        generate_series(\n" +
                    "            DATE_TRUNC('week', CURRENT_DATE) - INTERVAL '5 weeks',\n" +
                    "            DATE_TRUNC('week', CURRENT_DATE),\n" +
                    "            '1 week'::interval\n" +
                    "        ) AS week_start\n" +
                    "), \n" +
                    "task_weeks AS (\n" +
                    "    SELECT \n" +
                    "        DATE_TRUNC('week', start_time) AS week_start,\n" +
                    "        COUNT(*) AS task_count\n" +
                    "    FROM tasks\n" +
                    "    WHERE user_id = :userId\n" +
                    "    GROUP BY DATE_TRUNC('week', start_time)\n" +
                    ")\n" +
                    "SELECT \n" +
                    "    TO_CHAR(ws.week_start, 'YYYY-\"W\"IW') AS week_label,\n" +
                    "    COALESCE(tw.task_count, 0) AS task_count\n" +
                    "FROM week_series ws\n" +
                    "LEFT JOIN task_weeks tw ON ws.week_start = tw.week_start\n" +
                    "ORDER BY ws.week_start;";

    public static final String COUNT_ACTIVE_USERS_THIS_WEEK =
            "SELECT COUNT(DISTINCT t.user_id) FROM tasks t " +
                    "WHERE DATE_TRUNC('week', t.start_time) = DATE_TRUNC('week', CURRENT_DATE)";

    public static final String COUNT_TASKS_CREATED_THIS_WEEK =
            "SELECT COUNT(*) FROM tasks t " +
                    "WHERE DATE_TRUNC('week', t.start_time) = DATE_TRUNC('week', CURRENT_DATE)";

    public static final String GET_COMPLETED_TASKS_BY_DAY_THIS_WEEK =
            "WITH days AS (\n" +
                    "    SELECT generate_series(\n" +
                    "        DATE_TRUNC('week', CURRENT_DATE),\n" +
                    "        DATE_TRUNC('week', CURRENT_DATE) + INTERVAL '6 days',\n" +
                    "        '1 day'::interval\n" +
                    "    )::date AS day_date\n" +
                    ")\n" +
                    "SELECT \n" +
                    "    TO_CHAR(d.day_date, 'Dy') AS day_name,\n" +
                    "    COUNT(t.task_id) AS completed_count\n" +
                    "FROM days d\n" +
                    "LEFT JOIN tasks t \n" +
                    "    ON DATE(t.completed_at) = d.day_date\n" +
                    "    AND t.status = 'DONE'\n" +
                    "    AND DATE_TRUNC('week', t.completed_at) = DATE_TRUNC('week', CURRENT_DATE)\n" +
                    "GROUP BY d.day_date\n" +
                    "ORDER BY d.day_date;";

    public static final String COUNT_TASKS_BY_PRIORITY =
            "SELECT \n" +
                    "    t.priority AS priority_level,\n" +
                    "    COUNT(*) AS task_count\n" +
                    "FROM tasks t\n" +
                    "GROUP BY t.priority\n" +
                    "ORDER BY \n" +
                    "    CASE t.priority\n" +
                    "        WHEN 'HIGH' THEN 1\n" +
                    "        WHEN 'MEDIUM' THEN 2\n" +
                    "        WHEN 'LOW' THEN 3\n" +
                    "    END;";
}
