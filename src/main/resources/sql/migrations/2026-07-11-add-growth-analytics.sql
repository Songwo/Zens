-- Privacy-conscious first-party growth analytics. No IP, user-agent, content or credentials are stored.
CREATE TABLE IF NOT EXISTS growth_event (
  id bigint NOT NULL AUTO_INCREMENT,
  event_name varchar(40) NOT NULL,
  user_id varchar(64) DEFAULT NULL,
  anonymous_id varchar(64) NOT NULL,
  session_id varchar(64) NOT NULL,
  route varchar(255) DEFAULT NULL,
  source varchar(40) NOT NULL DEFAULT 'direct',
  properties_json json DEFAULT NULL,
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_growth_event_time_name (create_time, event_name),
  KEY idx_growth_event_user_time (user_id, create_time),
  KEY idx_growth_event_anon_time (anonymous_id, create_time),
  KEY idx_growth_event_session_time (session_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='First-party privacy-conscious product analytics';
