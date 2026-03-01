package com.campus.trend.campus_pulse.utils;

import cn.hutool.core.codec.Base32;
import cn.hutool.core.util.RandomUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TotpUtil {

    private static final int SECRET_BYTE_LENGTH = 20;
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;

    private TotpUtil() {
    }

    public static String generateSecret() {
        byte[] bytes = RandomUtil.randomBytes(SECRET_BYTE_LENGTH);
        return Base32.encode(bytes).replace("=", "");
    }

    public static String buildOtpAuthUri(String issuer, String account, String secret) {
        String safeIssuer = issuer == null ? "CampusPulse" : issuer;
        String safeAccount = account == null ? "user" : account;
        String label = safeIssuer + ":" + safeAccount;
        return "otpauth://totp/"
                + urlEncode(label)
                + "?secret=" + secret
                + "&issuer=" + urlEncode(safeIssuer)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    public static boolean verifyCode(String secret, String code) {
        if (secret == null || secret.isBlank() || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long currentCounter = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
        for (int offset = -1; offset <= 1; offset++) {
            String expected = generateCode(secret, currentCounter + offset);
            if (code.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private static String generateCode(String secret, long counter) {
        try {
            byte[] key = Base32.decode(secret);
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%06d", otp);
        } catch (Exception e) {
            return "";
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
