package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.config.properties.SupporterVoucherProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupporterVoucherCryptoTest {
    @Test
    void shouldEncryptWithRandomIvDecryptAndProduceStableKeyedHash() {
        SupporterVoucherProperties properties = properties("0123456789abcdef0123456789abcdef");
        SupporterVoucherCrypto crypto = new SupporterVoucherCrypto(properties);

        String first = crypto.encrypt("REAL-CODE-30-001");
        String second = crypto.encrypt("REAL-CODE-30-001");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(crypto.decrypt(first)).isEqualTo("REAL-CODE-30-001");
        assertThat(crypto.decrypt(second)).isEqualTo("REAL-CODE-30-001");
        assertThat(crypto.hash("REAL-CODE-30-001"))
                .hasSize(64).isEqualTo(crypto.hash("REAL-CODE-30-001"));
    }

    @Test
    void shouldFailClosedWhenKeyIsMissing() {
        SupporterVoucherCrypto crypto = new SupporterVoucherCrypto(new SupporterVoucherProperties());

        assertThatThrownBy(() -> crypto.encrypt("code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SUPPORTER_VOUCHER_ENCRYPTION_KEY");
    }

    private SupporterVoucherProperties properties(String rawKey) {
        SupporterVoucherProperties properties = new SupporterVoucherProperties();
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(rawKey.getBytes(StandardCharsets.UTF_8)));
        return properties;
    }
}
