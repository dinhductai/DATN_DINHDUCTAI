package com.microsv.task_service.repository.query;

public class TaskQuery {
    public static final String GET_TASKS_IN_TODAY =
            "SELECT t.task_id AS taskId, t.title AS title, t.description AS description, " +
                    "t.deadline AS deadline, t.status AS status, t.priority AS priority, " +
                    "t.created_at AS createdAt, t.start_time AS startTime, t.completed_at AS completedAt, t.user_id AS userId " +
                    "FROM tasks t " +
                    "WHERE t.user_id = :userId " +
                    "AND t.deadline IS NOT NULL " +
                    "AND DATE_TRUNC('day', t.deadline AT TIME ZONE 'Asia/Bangkok') = DATE_TRUNC('day', NOW() AT TIME ZONE 'Asia/Bangkok') " +
                    "ORDER BY t.deadline ASC";


    public static final String GET_OVERDUE_TASK_TODAY =
            "SELECT t.task_id AS taskId, t.title AS title, t.description AS description, " +
                    "t.deadline AS deadline, t.status AS status, t.priority AS priority, " +
                    "t.created_at AS createdAt, t.start_time AS startTime, t.completed_at AS completedAt, t.user_id AS userId " +
                    "FROM tasks t " +
                    "WHERE t.user_id = :userId " +
                    "AND t.deadline IS NOT NULL " +
                    "AND DATE_TRUNC('day', t.deadline AT TIME ZONE 'Asia/Bangkok') = DATE_TRUNC('day', NOW() AT TIME ZONE 'Asia/Bangkok') " +
                    "AND t.deadline AT TIME ZONE 'Asia/Bangkok' < NOW() AT TIME ZONE 'Asia/Bangkok' " +
                    "AND t.status <> 'DONE' " +
                    "ORDER BY t.deadline ASC";

    public static final String GET_COMPLETED_TASK_TODAY =
            "SELECT t.task_id AS taskId, t.title AS title, t.description AS description, " +
                    "t.deadline AS deadline, t.status AS status, t.priority AS priority, " +
                    "t.created_at AS createdAt, t.start_time AS startTime, t.completed_at AS completedAt, t.user_id AS userId " +
                    "FROM tasks t " +
                    "WHERE t.user_id = :userId " +
                    "AND DATE_TRUNC('day', t.deadline AT TIME ZONE 'Asia/Bangkok') = DATE_TRUNC('day', NOW() AT TIME ZONE 'Asia/Bangkok') " +
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
                    "   AND DATE_TRUNC('day', t.deadline AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM t.deadline AT TIME ZONE 'Asia/Bangkok') + 6) % 7)\n" +
                    "       = DATE_TRUNC('day', NOW() AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM NOW() AT TIME ZONE 'Asia/Bangkok') + 6) % 7)";

    public static final String GET_WEEKLY_TASK_STATUS_RATE =
            "SELECT \n" +
                    "    COUNT(CASE WHEN t.status = 'DONE' THEN 1 END) AS completedRate,\n" +
                    "    COUNT(CASE WHEN t.status = 'IN_PROGRESS' THEN 1 END) AS inProgressRate,\n" +
                    "    COUNT(CASE WHEN t.status = 'TODO' THEN 1 END) AS todoRate\n" +
                    "FROM tasks t\n" +
                    "WHERE \n" +
                    "    t.user_id = :userId\n" +
                    "    AND t.deadline IS NOT NULL\n" +
                    "    AND DATE_TRUNC('day', t.deadline AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM t.deadline AT TIME ZONE 'Asia/Bangkok') + 6) % 7)\n" +
                    "        = DATE_TRUNC('day', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') + 6) % 7)";

    public static final String GET_WEEKLY_TASK_DISTRIBUTION =
            "WITH current_week AS (\n" +
                    "        SELECT \n" +
                    "            (DATE_TRUNC('day', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date AS week_start,\n" +
                    "            (DATE_TRUNC('day', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') + 6) % 7) + INTERVAL '7 days')::date AS week_end\n" +
                    "    ),\n" +
                    "    days AS (\n" +
                    "        SELECT \n" +
                    "            generate_series(\n" +
                    "                (SELECT week_start FROM current_week)::date,\n" +
                    "                (SELECT week_end FROM current_week)::date - INTERVAL '1 day',\n" +
                    "                '1 day'\n" +
                    "            )::date AS day_date\n" +
                    "    )\n" +
                    "    SELECT \n" +
                    "        TO_CHAR(d.day_date, 'Day') AS day_name,\n" +
                    "        COUNT(t.task_id) AS task_count\n" +
                    "    FROM days d\n" +
                    "    LEFT JOIN tasks t \n" +
                    "        ON t.user_id = :userId\n" +
                    "        AND (t.start_time AT TIME ZONE 'Asia/Bangkok')::date = d.day_date\n" +
                    "    GROUP BY d.day_date\n" +
                    "    ORDER BY d.day_date;";

    public static final String GET_TASK_CREATION_TIMELINE =
            "WITH monday_week AS (\n" +
                    "        SELECT (DATE_TRUNC('day', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') + 6) % 7)) AS week_monday\n" +
                    "    ),\n" +
                    "    week_series AS (\n" +
                    "        SELECT \n" +
                    "            generate_series(\n" +
                    "                (SELECT week_monday - INTERVAL '5 weeks' FROM monday_week),\n" +
                    "                (SELECT week_monday FROM monday_week),\n" +
                    "                '1 week'::interval\n" +
                    "            ) AS week_start\n" +
                    "    ),\n" +
                    "    task_weeks AS (\n" +
                    "        SELECT \n" +
                    "            (DATE_TRUNC('day', start_time AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM start_time AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date AS week_start,\n" +
                    "            COUNT(*) AS task_count\n" +
                    "        FROM tasks\n" +
                    "        WHERE user_id = :userId\n" +
                    "        GROUP BY 1\n" +
                    ")\n" +
                    "SELECT \n" +
                    "    TO_CHAR(ws.week_start, 'dd') || '-' ||\n" +
                    "    TO_CHAR(ws.week_start + INTERVAL '6 days',\n" +
                    "        CASE\n" +
                    "            WHEN TO_CHAR(ws.week_start, 'mm') = TO_CHAR(ws.week_start + INTERVAL '6 days', 'mm')\n" +
                    "            THEN 'dd' ELSE 'dd/mm'\n" +
                    "        END\n" +
                    "    ) AS week_label,\n" +
                    "    COALESCE(tw.task_count, 0) AS task_count\n" +
                    "FROM week_series ws\n" +
                    "LEFT JOIN task_weeks tw ON ws.week_start = tw.week_start\n" +
                    "ORDER BY ws.week_start;";

    public static final String COUNT_ACTIVE_USERS_THIS_WEEK =
            "SELECT COUNT(DISTINCT t.user_id) FROM tasks t " +
                    "WHERE (DATE_TRUNC('day', t.start_time AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM t.start_time AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date " +
                    "= (DATE_TRUNC('day', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date";

    public static final String COUNT_TASKS_CREATED_THIS_WEEK =
            "SELECT COUNT(*) FROM tasks t " +
                    "WHERE (DATE_TRUNC('day', t.start_time AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM t.start_time AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date " +
                    "= (DATE_TRUNC('day', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date";

    public static final String GET_COMPLETED_TASKS_BY_DAY_THIS_WEEK =
            "WITH monday_week AS (\n" +
                    "        SELECT (DATE_TRUNC('day', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date AS week_monday\n" +
                    "    ),\n" +
                    "    days AS (\n" +
                    "        SELECT \n" +
                    "            generate_series(\n" +
                    "                (SELECT week_monday FROM monday_week),\n" +
                    "                (SELECT week_monday FROM monday_week) + INTERVAL '6 days',\n" +
                    "                '1 day'::interval\n" +
                    "            )::date AS day_date\n" +
                    "    )\n" +
                    "SELECT \n" +
                    "    TO_CHAR(d.day_date, 'Dy') AS day_name,\n" +
                    "    COUNT(t.task_id) AS completed_count\n" +
                    "FROM days d\n" +
                    "LEFT JOIN tasks t \n" +
                    "    ON (t.completed_at AT TIME ZONE 'Asia/Bangkok')::date = d.day_date\n" +
                    "    AND t.status = 'DONE'\n" +
                    "    AND (DATE_TRUNC('day', t.completed_at AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM t.completed_at AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date\n" +
                    "        = (DATE_TRUNC('day', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date\n" +
                    "GROUP BY d.day_date\n" +
                    "ORDER BY d.day_date;";

    public static final String COUNT_TASKS_BY_PRIORITY =
            "SELECT \n" +
                    "    t.priority AS priority_level,\n" +
                    "    COUNT(*) AS task_count\n" +
                    "FROM tasks t\n" +
                    "WHERE (DATE_TRUNC('day', t.start_time AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM t.start_time AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date " +
                    "= (DATE_TRUNC('day', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date\n" +
                    "GROUP BY t.priority\n" +
                    "ORDER BY \n" +
                    "    CASE t.priority\n" +
                    "        WHEN 'HIGH' THEN 1\n" +
                    "        WHEN 'MEDIUM' THEN 2\n" +
                    "        WHEN 'LOW' THEN 3\n" +
                    "    END;";

    public static final String COUNT_TASKS_THIS_MONTH =
            "SELECT COUNT(*)\n" +
                    "FROM tasks t\n" +
                    "WHERE t.start_time >= DATE_TRUNC('month', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok')\n" +
                    "AND t.start_time < DATE_TRUNC('month', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') + INTERVAL '1 month'";

    public static final String COUNT_TASKS_LAST_MONTH =
            "SELECT COUNT(*)\n" +
                    "FROM tasks t\n" +
                    "WHERE t.start_time >= DATE_TRUNC('month', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 month'\n" +
                    "AND t.start_time < DATE_TRUNC('month', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok')";
}
