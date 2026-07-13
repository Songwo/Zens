package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.config.properties.SupporterVoucherProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class SupporterVoucherCrypto {
    private static final String VERSION = "v1";
    private static final byte[] AAD = "zens-supporter-voucher-v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SupporterVoucherProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public String encrypt(String plainCode) {
        SecretKeySpec key = aesKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] encrypted = cipher.doFinal(plainCode.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return VERSION + ":" + Base64.getEncoder().encodeToString(payload);
        } catch (Exception error) {
            throw new IllegalStateException("兑换码加密失败", error);
        }
    }

    public String decrypt(String ciphertext) {
        SecretKeySpec key = aesKey();
        try {
            String[] parts = ciphertext.split(":", 2);
            if (parts.length != 2 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("不支持的兑换码密文版本");
            }
            byte[] payload = Base64.getDecoder().decode(parts[1]);
            if (payload.length <= IV_BYTES) throw new IllegalArgumentException("兑换码密文无效");
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("兑换码解密失败，请检查服务器加密密钥", error);
        }
    }

    /** 使用带密钥摘要做唯一指纹，避免数据库泄露后直接枚举低熵兑换码。 */
    public String hash(String plainCode) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(plainCode.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("兑换码摘要计算失败", error);
        }
    }

    private SecretKeySpec aesKey() {
        return new SecretKeySpec(keyBytes(), "AES");
    }

    private byte[] keyBytes() {
        if (!StringUtils.hasText(properties.getEncryptionKey())) {
            throw new IllegalStateException("未配置 SUPPORTER_VOUCHER_ENCRYPTION_KEY，无法导入或读取兑换码");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.getEncryptionKey().trim());
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("SUPPORTER_VOUCHER_ENCRYPTION_KEY 不是有效 Base64", error);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("SUPPORTER_VOUCHER_ENCRYPTION_KEY 解码后必须为 32 字节");
        }
        return decoded;
    }
}
