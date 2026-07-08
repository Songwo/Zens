CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 帖子全文检索 + 过滤
CREATE INDEX IF NOT EXISTS idx_agent_post_search_tsv
ON sys_post
USING gin (
  to_tsvector(
    'simple',
    coalesce(title, '') || ' ' ||
    coalesce(summary, '') || ' ' ||
    coalesce(tags, '') || ' ' ||
    coalesce(content, '')
  )
);

CREATE INDEX IF NOT EXISTS idx_agent_post_title_trgm
ON sys_post
USING gin (title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_agent_post_summary_trgm
ON sys_post
USING gin (summary gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_agent_post_tags_trgm
ON sys_post
USING gin (tags gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_agent_post_filters
ON sys_post (status, audit_status, section_id, create_time DESC);

-- 评论辅助检索 / 聚合
CREATE INDEX IF NOT EXISTS idx_agent_comment_post_audit_time
ON sys_comment (post_id, audit_status, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_agent_comment_content_trgm
ON sys_comment
USING gin (content gin_trgm_ops);

-- 用户 / 板块维度补全
CREATE INDEX IF NOT EXISTS idx_agent_user_status
ON sys_user (status, id);

CREATE INDEX IF NOT EXISTS idx_agent_section_status
ON sections (status, id);
