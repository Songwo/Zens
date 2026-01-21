use campus_pulse;
-- =================================================================
-- 9. 系统通知表 (sys_notification)
-- =================================================================
DROP TABLE IF EXISTS `sys_notification`;
CREATE TABLE `sys_notification` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `user_id` varchar(64) NOT NULL COMMENT '接收人ID',
  `sender_id` varchar(64) DEFAULT NULL COMMENT '发送人ID',
  `sender_name` varchar(50) DEFAULT NULL COMMENT '发送人昵称',
  `sender_avatar` varchar(255) DEFAULT NULL COMMENT '发送人头像',
  `title` varchar(100) NOT NULL COMMENT '标题',
  `content` varchar(500) DEFAULT NULL COMMENT '内容',
  `type` int DEFAULT 1 COMMENT '类型 1:系统 2:回复 3:点赞 4:收藏',
  `related_id` varchar(64) DEFAULT NULL COMMENT '关联ID(帖子/评论ID)',
  `status` int DEFAULT 0 COMMENT '状态 0:未读 1:已读',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统通知表';
