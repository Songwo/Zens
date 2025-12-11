package com.campus.trend.campus_pulse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
public class VerificationCodeService {

    private final StringRedisTemplate redisTemplate;
    private final MailService mailService;

    private static final String CODE_PREFIX = "verify:code:";
    private static final long EXPIRE_MINUTES = 5;

    /**
     * 发送验证码到指定邮箱
     */
    public void sendCode(String email) {
        // 1. 生成6位随机验证码
        String code = generateCode();

        // 2. 存入 Redis
        String key = CODE_PREFIX + email;
        redisTemplate.opsForValue().set(key, code, Duration.ofMinutes(EXPIRE_MINUTES));

        // 3. 发送邮件
        mailService.sendVerificationCode(email, code);

        log.info("验证码 {} 已发送至 {}", code, email);
    }

    /**
     * 校验验证码
     */
    public boolean verifyCode(String email, String code) {
        if (email == null || code == null) {
            return false;
        }
        String key = CODE_PREFIX + email;
        String storedCode = redisTemplate.opsForValue().get(key);

        // 验证通过后是否删除？这里选择删除防止重复使用
        if (code.equals(storedCode)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }

    private String generateCode() {
        Random random = new Random();
        int number = random.nextInt(900000) + 100000; // 100000-999999
        return String.valueOf(number);
    }
}
