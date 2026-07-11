package com.campus.trend.campus_pulse.config.properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class InternalServicePropertiesTest {
  @Test
  void disabledOpsClientMayHaveEmptySecret() {
    InternalServiceProperties properties = new InternalServiceProperties();
    InternalServiceProperties.Client client = client(false, "");
    properties.setClients(List.of(client));
    assertDoesNotThrow(properties::validate);
  }

  @Test
  void enabledOpsClientRejectsEmptyOrShortSecret() {
    InternalServiceProperties empty = new InternalServiceProperties();
    empty.setClients(List.of(client(true, "")));
    assertThrows(IllegalStateException.class, empty::validate);

    InternalServiceProperties shortSecret = new InternalServiceProperties();
    shortSecret.setClients(List.of(client(true, "short-secret")));
    assertThrows(IllegalStateException.class, shortSecret::validate);
  }

  @Test
  void enabledOpsClientAcceptsStrongSecret() {
    InternalServiceProperties properties = new InternalServiceProperties();
    properties.setClients(List.of(client(true, "0123456789abcdef0123456789abcdef")));
    assertDoesNotThrow(properties::validate);
  }

  private InternalServiceProperties.Client client(boolean enabled, String secret) {
    InternalServiceProperties.Client client = new InternalServiceProperties.Client();
    client.setId("zens-ops");
    client.setEnabled(enabled);
    client.setSecret(secret);
    return client;
  }
}
