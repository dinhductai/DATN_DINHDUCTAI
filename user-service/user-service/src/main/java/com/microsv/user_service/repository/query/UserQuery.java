package com.microsv.user_service.repository.query;

public class UserQuery {
    public static final String COUNT_ALL_NEW_USERS_THIS_WEEK =
            "SELECT COUNT(*) FROM users " +
                    "WHERE (DATE_TRUNC('day', created_at AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM created_at AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date " +
                    "= (DATE_TRUNC('day', CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') - INTERVAL '1 day' * ((EXTRACT(DOW FROM CURRENT_DATE AT TIME ZONE 'Asia/Bangkok') + 6) % 7))::date";

    public static final String SEARCH_USER_BY_NAME_OR_EMAIL =
            "SELECT " +
                    "    u.user_id as userId, " +
                    "    u.user_name as userName, " +
                    "    u.password as password, " +
                    "    u.email as email, " +
                    "    u.profile as profile, " +
                    "    u.created_at as createdAt, " +
                    "    CASE " +
                    "        WHEN search_vector IS NOT NULL THEN TS_RANK(search_vector, plainto_tsquery('simple', :keyword)) " +
                    "        ELSE 0 " +
                    "    END as rank " +
                    "FROM users u " +
                    "WHERE " +
                    "    (search_vector IS NOT NULL AND search_vector @@ plainto_tsquery('simple', :keyword)) " +
                    "    OR LOWER(u.user_name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                    "    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
                    "ORDER BY rank DESC, u.created_at DESC " +
                    "LIMIT 50";
}
