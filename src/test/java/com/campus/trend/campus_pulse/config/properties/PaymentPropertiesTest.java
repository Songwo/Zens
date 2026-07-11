package com.campus.trend.campus_pulse.config.properties;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentPropertiesTest {
    @Test
    void defaultsToDisabledWithoutRequiringCredentials() {
        PaymentProperties properties = new PaymentProperties(new MockEnvironment());
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void refusesMockProviderInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        PaymentProperties properties = new PaymentProperties(environment);
        properties.setEnabled(true);
        properties.setProvider("mock");
        properties.setAllowMock(true);
        properties.setMockWebhookSecret("local-test-secret-at-least-32-characters");
        assertThrows(IllegalStateException.class, properties::validate);
    }
}
