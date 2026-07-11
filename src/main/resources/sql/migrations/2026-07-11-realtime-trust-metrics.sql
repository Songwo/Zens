-- 实时行为画像校准：只补索引并按权威关系表回填冗余计数器，不删除业务数据。

SET @schema_name := DATABASE();

SET @sql := IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = @schema_name
           AND TABLE_NAME = 'sys_post'
           AND INDEX_NAME = 'idx_post_user_visibility'
    ),
    'CREATE INDEX `idx_post_user_visibility` ON `sys_post` (`user_id`, `status`, `audit_status`, `create_time`)',
    'SELECT ''[skip] idx_post_user_visibility exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = @schema_name
           AND TABLE_NAME = 'sys_comment'
           AND INDEX_NAME = 'idx_comment_user_audit'
    ),
    'CREATE INDEX `idx_comment_user_audit` ON `sys_comment` (`user_id`, `audit_status`, `create_time`)',
    'SELECT ''[skip] idx_comment_user_audit exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sys_user u
LEFT JOIN (
    SELECT user_id, COUNT(1) AS cnt
      FROM sys_post_like
     GROUP BY user_id
) pl_given ON pl_given.user_id = u.id
LEFT JOIN (
    SELECT user_id, COUNT(1) AS cnt
      FROM comment_likes
     GROUP BY user_id
) cl_given ON cl_given.user_id = u.id
LEFT JOIN (
    SELECT user_id,
           COUNT(DISTINCT DATE(create_time)) AS days_visited,
           FLOOR(COALESCE(SUM(CASE WHEN duration_ms > 0 THEN duration_ms ELSE 0 END), 0) / 1000) AS read_time_sec
      FROM sys_view_log
     GROUP BY user_id
) views ON views.user_id = u.id
LEFT JOIN (
    SELECT user_id, COUNT(1) AS cnt
      FROM sys_post
     WHERE status = 1
       AND (audit_status IS NULL OR audit_status = '' OR audit_status = 'APPROVED')
     GROUP BY user_id
) posts ON posts.user_id = u.id
LEFT JOIN (
    SELECT owner_id, SUM(cnt) AS cnt
      FROM (
          SELECT p.user_id AS owner_id, COUNT(1) AS cnt
            FROM sys_post_like pl
            JOIN sys_post p ON p.id = pl.post_id
           WHERE p.status = 1
             AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED')
           GROUP BY p.user_id
          UNION ALL
          SELECT c.user_id AS owner_id, COUNT(1) AS cnt
            FROM comment_likes cl
            JOIN sys_comment c ON c.id = cl.comment_id
            JOIN sys_post p ON p.id = c.post_id
           WHERE p.status = 1
             AND (p.audit_status IS NULL OR p.audit_status = '' OR p.audit_status = 'APPROVED')
             AND (c.audit_status IS NULL OR c.audit_status = '' OR c.audit_status = 'APPROVED')
           GROUP BY c.user_id
      ) received_union
     GROUP BY owner_id
) received ON received.owner_id = u.id
SET u.likes_given = COALESCE(pl_given.cnt, 0) + COALESCE(cl_given.cnt, 0),
    u.days_visited = COALESCE(views.days_visited, 0),
    u.read_time_sec = COALESCE(views.read_time_sec, 0),
    u.total_posts = COALESCE(posts.cnt, 0),
    u.total_likes_received = COALESCE(received.cnt, 0);
