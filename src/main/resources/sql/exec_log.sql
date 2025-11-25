use campus_pulse;

show tables ;

select * from campus_pulse.sys_user;
select * from campus_pulse.sys_post;
select * from campus_pulse.sys_category;
select * from campus_pulse.sys_trend_stat;
select * from campus_pulse.sys_interaction;

select * from campus_pulse.sys_post sp left join sys_category sc on
    sp.category_id = sc.id;

-- 系统操作日志表
CREATE TABLE admin_trigger_log (
                                   id INT AUTO_INCREMENT PRIMARY KEY,
                                   exec_user VARCHAR(100),
                                   action VARCHAR(100),
                                   detail TEXT,
                                   exec_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DELIMITER $$

CREATE TRIGGER admin_log_update
    AFTER UPDATE ON sys_post
    FOR EACH ROW
BEGIN
    INSERT INTO admin_trigger_log(exec_user, action, detail)
    VALUES(
                      CURRENT_USER(),                                      -- 当前执行 SQL 的用户
                      'UPDATE sys_post',                                   -- 执行动作
                      CONCAT(
                              'Old Title: ', OLD.title,
                              ' -> New Title: ', NEW.title,
                              '; Old Content: ', OLD.content,
                              ' -> New Content: ', NEW.content
                      )
          );
    END$$

    DELIMITER ;

DELIMITER $$

    CREATE TRIGGER admin_log_insert
        AFTER INSERT ON sys_post
        FOR EACH ROW
    BEGIN
        INSERT INTO admin_trigger_log(exec_user, action, detail)
        VALUES(
                          CURRENT_USER(),
                          'INSERT sys_post',
                          CONCAT(
                                  'New Title: ', NEW.title,
                                  ', New Content: ', NEW.content
                          )
              );
        END$$

        DELIMITER ;

DELIMITER $$

        CREATE TRIGGER admin_log_delete
            AFTER DELETE ON sys_post
            FOR EACH ROW
        BEGIN
            INSERT INTO admin_trigger_log(exec_user, action, detail)
            VALUES(
                              CURRENT_USER(),
                              'DELETE sys_post',
                              CONCAT(
                                      'Deleted Title: ', OLD.title,
                                      ', Deleted Content: ', OLD.content
                              )
                  );
            END$$

            DELIMITER ;


