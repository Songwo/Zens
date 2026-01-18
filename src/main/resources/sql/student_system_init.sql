use campus_pulse;
-- 学生详细信息扩展表
CREATE TABLE IF NOT EXISTS `sys_student_profile` (
    `user_id` VARCHAR(64) NOT NULL COMMENT '关联用户ID',
    `student_no` VARCHAR(32) NOT NULL COMMENT '学号',
    `real_name` VARCHAR(64) NOT NULL COMMENT '真实姓名',
    `id_card` VARCHAR(20) DEFAULT NULL COMMENT '身份证号',
    `college` VARCHAR(128) DEFAULT NULL COMMENT '学院',
    `class_name` VARCHAR(64) DEFAULT NULL COMMENT '班级',
    `enrollment_date` DATE DEFAULT NULL COMMENT '入学时间',
    `graduation_status` TINYINT DEFAULT 0 COMMENT '毕业状态 0:在读 1:毕业 2:延毕',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系方式',
    `campus` VARCHAR(64) DEFAULT NULL COMMENT '校区',
    `dorm_building` VARCHAR(64) DEFAULT NULL COMMENT '宿舍楼',
    `dorm_room` VARCHAR(32) DEFAULT NULL COMMENT '房间号',
    `dorm_bed` VARCHAR(16) DEFAULT NULL COMMENT '床位号',
    `total_credits` DECIMAL(5,2) DEFAULT 0.00 COMMENT '已修总学分',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_student_no` (`student_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生信息表';

-- 课程表
CREATE TABLE IF NOT EXISTS `sys_course` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `course_code` VARCHAR(32) NOT NULL COMMENT '课程编号',
    `name` VARCHAR(128) NOT NULL COMMENT '课程名称',
    `teacher_name` VARCHAR(64) DEFAULT NULL COMMENT '任课教师',
    `credits` DECIMAL(3,1) DEFAULT 0.0 COMMENT '学分',
    `max_capacity` INT DEFAULT 50 COMMENT '最大选课人数',
    `current_capacity` INT DEFAULT 0 COMMENT '当前已选人数',
    `class_time` VARCHAR(256) DEFAULT NULL COMMENT '上课时间 (例: 周一 1-2节)',
    `location` VARCHAR(256) DEFAULT NULL COMMENT '上课地点',
    `semester` VARCHAR(32) DEFAULT NULL COMMENT '学期',
    `status` TINYINT DEFAULT 1 COMMENT '状态 1:正常 0:停开',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_code` (`course_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程信息表';

-- 选课记录表
CREATE TABLE IF NOT EXISTS `sys_course_selection` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` VARCHAR(64) NOT NULL,
    `course_id` BIGINT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_course` (`user_id`, `course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选课记录表';

-- 成绩表
CREATE TABLE IF NOT EXISTS `sys_grade` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` VARCHAR(64) NOT NULL,
    `course_id` BIGINT NOT NULL,
    `score` DECIMAL(5,2) DEFAULT NULL COMMENT '分数',
    `semester` VARCHAR(32) DEFAULT NULL COMMENT '学期',
    `is_passed` TINYINT DEFAULT 0 COMMENT '是否及格 1:是 0:否',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成绩表';

-- 请假申请表
CREATE TABLE IF NOT EXISTS `sys_leave_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` VARCHAR(64) NOT NULL,
    `type` TINYINT NOT NULL COMMENT '请假类型 1:事假 2:病假',
    `start_time` DATETIME NOT NULL,
    `end_time` DATETIME NOT NULL,
    `reason` TEXT COMMENT '原因',
    `status` TINYINT DEFAULT 0 COMMENT '审批状态 0:待审批 1:已通过 2:已驳回',
    `approver_id` VARCHAR(64) DEFAULT NULL COMMENT '审批人ID',
    `approve_time` DATETIME DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假申请表';

-- 系统配置/公告 (用于弹窗)
CREATE TABLE IF NOT EXISTS `sys_announcement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `title` VARCHAR(128) DEFAULT NULL,
    `content` TEXT,
    `type` VARCHAR(32) DEFAULT 'WELCOME' COMMENT 'WELCOME/NOTICE',
    `is_active` TINYINT DEFAULT 1,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告表';

-- 用户弹窗记录
CREATE TABLE IF NOT EXISTS `sys_user_popup_log` (
    `user_id` VARCHAR(64) NOT NULL,
    `announcement_id` BIGINT NOT NULL,
    `show_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`, `announcement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户弹窗记录表';
