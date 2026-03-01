package com.campus.trend.campus_pulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationCodeService {

    private final StringRedisTemplate stringRedisTemplate;
    private final MailService mailService;
    private final Random random = new Random();

    private static final String REGISTER_CODE_PREFIX = "verify:register:";
    private static final String LOGIN_CODE_PREFIX = "verify:login:";
    private static final String CAPTCHA_PREFIX = "auth:captcha:"; // New constant
    private static final String LOCK_PREFIX = "auth:lock:"; // New constant
    private static final String LOGIN_FAIL_PREFIX = "login:fail:"; // Kept for other methods
    private static final long EXPIRE_MINUTES = 5;
    private static final int MAX_LOGIN_ATTEMPTS = 3; // 最大登录失败次数
    private static final long LOCK_TIME_MINUTES = 5; // New constant

    /**
     * 发送注册验证码
     */
    public void sendCode(String email) {
        String code = generateCode();
        String key = REGISTER_CODE_PREFIX + email;
        stringRedisTemplate.opsForValue().set(key, code, Duration.ofMinutes(EXPIRE_MINUTES));
        log.info("验证码已生成并存储到Redis: {}", email);

        // 异步发送邮件，不阻塞主线程
        mailService.sendVerificationCode(email, code);
        log.info("验证码邮件发送任务已提交: {}", email);
    }

    /**
     * 静默生成验证码（不发邮件），用于 verify-code 后恢复码给后续 login/register 使用
     */
    public void sendCodeSilent(String email) {
        String code = generateCode();
        String key = REGISTER_CODE_PREFIX + email;
        stringRedisTemplate.opsForValue().set(key, code, Duration.ofMinutes(EXPIRE_MINUTES));
        log.info("已为 {} 静默生成新验证码（供后续登录/注册使用）", email);
    }

    /**
     * 发送登录验证码
     */
    public void sendLoginCode(String username) {
        String code = generateCode();
        String key = LOGIN_CODE_PREFIX + username;
        stringRedisTemplate.opsForValue().set(key, code, Duration.ofMinutes(EXPIRE_MINUTES));
        // 这里只是为了演示，实际应该发邮件
        log.info("登录验证码已发送至 {}", username);
    }

    /**
     * 校验注册验证码
     */
    public boolean verifyCode(String email, String code) {
        if (email == null) {
            return false;
        }
        String key = REGISTER_CODE_PREFIX + email;
        String storedCode = stringRedisTemplate.opsForValue().get(key);

        if (code.equals(storedCode)) {
            stringRedisTemplate.delete(key);
            return true;
        }
        return false;
    }

    /**
     * 仅校验验证码，不删除（供 /auth/verify-code 使用，保留码给后续 login/register 消费）
     */
    public boolean checkCodeWithoutConsuming(String email, String code) {
        if (email == null || code == null) {
            return false;
        }
        String key = REGISTER_CODE_PREFIX + email;
        String storedCode = stringRedisTemplate.opsForValue().get(key);
        return code.equals(storedCode);
    }

    /**
     * 校验登录验证码
     */
    public boolean verifyLoginCode(String username, String code) {
        if (username == null)
            return false;
        String key = LOGIN_CODE_PREFIX + username;
        String storedCode = stringRedisTemplate.opsForValue().get(key);
        return code != null && code.equals(storedCode);
    }

    /**
     * 生成图形验证码并存储
     * 
     * @param uuid 用于标识验证码的唯一ID
     * @return 验证码图片对象
     */
    public cn.hutool.captcha.ICaptcha getCaptcha(String uuid) {
        // 定义图形验证码的长、宽、位数、干扰线条数
        cn.hutool.captcha.LineCaptcha captcha = cn.hutool.captcha.CaptchaUtil.createLineCaptcha(200, 100, 4, 150);
        // 存入Redis，5分钟有效
        stringRedisTemplate.opsForValue().set(CAPTCHA_PREFIX + uuid, captcha.getCode(), 5, TimeUnit.MINUTES);
        return captcha;
    }

    /**
     * 校验图形验证码
     */
    public boolean verifyCaptcha(String uuid, String code) {
        if (uuid == null || code == null)
            return false;
        String key = CAPTCHA_PREFIX + uuid;
        String realCode = stringRedisTemplate.opsForValue().get(key);
        if (realCode == null)
            return false;

        // 验证一次即删除（防止重放）
        stringRedisTemplate.delete(key);
        return realCode.equalsIgnoreCase(code);
    }

    /**
     * 记录登录失败，返回当前失败次数
     * 如果达到阈值，则锁定账号
     */
    public int recordLoginFailure(String username) {
        String key = LOGIN_FAIL_PREFIX + username;
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, 30, TimeUnit.MINUTES);
        }

        if (count != null && count >= MAX_LOGIN_ATTEMPTS) {
            // 锁定账号
            stringRedisTemplate.opsForValue().set(LOCK_PREFIX + username, "LOCKED", LOCK_TIME_MINUTES,
                    TimeUnit.MINUTES);
            // 清除计数，重新开始（或者保留计数也可以，这里选择锁定后清空计数，锁定结束后重试）
            stringRedisTemplate.delete(key);
            return MAX_LOGIN_ATTEMPTS;
        }
        return count != null ? count.intValue() : 0;
    }

    /**
     * 清除登录失败记录
     */
    public void clearLoginFailure(String username) {
        stringRedisTemplate.delete(LOGIN_FAIL_PREFIX + username);
        stringRedisTemplate.delete(LOCK_PREFIX + username);
    }

    /**
     * 检查是否需要验证码（失败次数超过阈值）
     */
    public boolean needVerificationCode(String username) {
        // 因需求变更，现在始终需要图形验证码，不再根据失败次数决定是否需要邮箱验证码
        // 但此方法可能被前端调用用来检查状态，我们可以复用它来返回是否被锁定？
        // 或者直接返回 false，因为前端总是显示图形验证码
        return false;
    }

    public boolean isLocked(String username) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(LOCK_PREFIX + username));
    }

    public long getLockTimeLeft(String username) {
        Long expire = stringRedisTemplate.getExpire(LOCK_PREFIX + username, TimeUnit.SECONDS);
        return expire != null && expire > 0 ? expire : 0;
    }

    /**
     * 获取当前登录失败次数
     */
    public int getLoginFailCount(String username) {
        String key = LOGIN_FAIL_PREFIX + username;
        String count = stringRedisTemplate.opsForValue().get(key);
        return count == null ? 0 : Integer.parseInt(count);
    }

    private String generateCode() {
        int number = random.nextInt(900000) + 100000;
        return String.valueOf(number);
    }
}
