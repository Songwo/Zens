-- Zens 自动运营 MVP。仅新增表，不授予运营进程任何数据库权限。
CREATE TABLE IF NOT EXISTS ops_content_plan (
  id varchar(64) NOT NULL,
  idempotency_key varchar(128) NOT NULL,
  topic varchar(200) NOT NULL,
  title varchar(200) DEFAULT NULL,
  brief text,
  status varchar(32) NOT NULL DEFAULT 'PLANNED',
  scheduled_at datetime DEFAULT NULL,
  metadata_json json DEFAULT NULL,
  created_by varchar(64) NOT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_ops_plan_idem (idempotency_key),
  KEY idx_ops_plan_status_schedule (status, scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ops_draft (
  id varchar(64) NOT NULL,
  idempotency_key varchar(128) NOT NULL,
  plan_id varchar(64) DEFAULT NULL,
  type varchar(20) NOT NULL,
  status varchar(32) NOT NULL DEFAULT 'CREATED',
  title varchar(200) DEFAULT NULL,
  content mediumtext NOT NULL,
  section_id bigint DEFAULT NULL,
  tags varchar(500) DEFAULT NULL,
  cover_image varchar(1000) DEFAULT NULL,
  target_post_id varchar(64) DEFAULT NULL,
  parent_comment_id varchar(64) DEFAULT NULL,
  post_id varchar(64) DEFAULT NULL,
  comment_id varchar(64) DEFAULT NULL,
  metadata_json json DEFAULT NULL,
  source_service varchar(64) NOT NULL,
  approved_by varchar(64) DEFAULT NULL,
  approved_at datetime DEFAULT NULL,
  published_at datetime DEFAULT NULL,
  failure_reason varchar(500) DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_ops_draft_idem (idempotency_key),
  KEY idx_ops_draft_status_time (status, create_time), KEY idx_ops_draft_target (target_post_id), KEY idx_ops_draft_plan (plan_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ops_approval (
  id varchar(64) NOT NULL, draft_id varchar(64) NOT NULL,
  action varchar(20) NOT NULL, operator_id varchar(64) NOT NULL,
  note varchar(500) DEFAULT NULL, create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), KEY idx_ops_approval_draft_time (draft_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ops_job_run (
  id varchar(64) NOT NULL, job_type varchar(50) NOT NULL,
  idempotency_key varchar(128) DEFAULT NULL, draft_id varchar(64) DEFAULT NULL,
  service_id varchar(64) DEFAULT NULL, operator_id varchar(64) DEFAULT NULL,
  status varchar(32) NOT NULL, detail_json json DEFAULT NULL,
  error_message varchar(500) DEFAULT NULL,
  started_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, finished_at datetime DEFAULT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_ops_job_idem (idempotency_key),
  KEY idx_ops_job_type_time (job_type, started_at), KEY idx_ops_job_status_time (status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ops_metric_snapshot (
  id varchar(64) NOT NULL, idempotency_key varchar(128) NOT NULL, period_start datetime NOT NULL, period_end datetime NOT NULL,
  metrics_json json NOT NULL, source_service varchar(64) NOT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_ops_metric_idem (idempotency_key), KEY idx_ops_metric_period (period_start, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
