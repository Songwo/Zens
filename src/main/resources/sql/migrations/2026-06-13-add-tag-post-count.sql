-- 标签可见帖子数冗余列：消除标签搜索时的 FIND_IN_SET N+1 全表扫描
-- (TagServiceImpl.searchTags / UserTagRelationServiceImpl 此前对每个标签
--  实时 COUNT，每次搜索最多触发 20 次 sys_post 全表扫描)
-- 上线顺序：先执行本迁移(含回填)，再部署读取 post_count 的代码。

use campus_pulse;

ALTER TABLE `sys_tag`
    ADD COLUMN `post_count` INT NOT NULL DEFAULT 0 COMMENT '可见帖子数(冗余计数,定时校准)' AFTER `heat`;

-- 一次性回填，口径与原 PostMapper.countByTagName 完全一致
UPDATE `sys_tag` t
SET t.`post_count` = (
    SELECT COUNT(*)
    FROM `sys_post` p
    WHERE p.`status` = 1
      AND (p.`audit_status` IS NULL OR p.`audit_status` = '' OR p.`audit_status` = 'APPROVED')
      AND (FIND_IN_SET(t.`name`, p.`tags`) > 0
           OR FIND_IN_SET(CONCAT(' ', t.`name`), p.`tags`) > 0
           OR p.`tags` = t.`name`)
);
