package com.campus.trend.campus_pulse.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.campus.trend.campus_pulse.config.properties.InternalServiceProperties;
import com.campus.trend.campus_pulse.filter.InternalServiceFilter;
import com.campus.trend.campus_pulse.monitor.EcosystemMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalServiceFilterReplayTest {
  private static final String SECRET = "unit-test-secret-at-least-16";
  private StringRedisTemplate redis;
  private ValueOperations<String, String> values;
  private InternalServiceFilter filter;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redis = mock(StringRedisTemplate.class);
    values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    InternalServiceProperties properties = new InternalServiceProperties();
    InternalServiceProperties.Client client = new InternalServiceProperties.Client();
    client.setId("zens-ops");
    client.setSecret(SECRET);
    properties.setClients(java.util.List.of(client));
    filter =
        new InternalServiceFilter(
            redis, new ObjectMapper(), mock(EcosystemMetrics.class), properties);
  }

  @Test
  void rejectsReplayBeforeController() throws Exception {
    when(values.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS)))
        .thenReturn(false);
    MockHttpServletRequest request = signedRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(request, response, chain);
    assertEquals(409, response.getStatus());
    verifyNoInteractions(chain);
  }

  @Test
  void rejectsInvalidSignature() throws Exception {
    MockHttpServletRequest request = signedRequest();
    request.removeHeader("X-Service-Signature");
    request.addHeader("X-Service-Signature", "00".repeat(32));
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(request, response, chain);
    assertEquals(401, response.getStatus());
    verify(values, never()).setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    verifyNoInteractions(chain);
  }

  private MockHttpServletRequest signedRequest() throws Exception {
    String path = "/api/internal/ops/status";
    String timestamp = String.valueOf(System.currentTimeMillis());
    String nonce = "abcdefghijklmnop12345678";
    String payload = "GET\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + sha256("");
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.addHeader("X-Service-Id", "zens-ops");
    request.addHeader("X-Service-Timestamp", timestamp);
    request.addHeader("X-Service-Nonce", nonce);
    request.addHeader(
        "X-Service-Signature",
        HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))));
    return request;
  }

  private String sha256(String value) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }
}
