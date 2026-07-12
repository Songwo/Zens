package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.entity.SupporterEntitlement;
import com.campus.trend.campus_pulse.mapper.SupporterEntitlementMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupporterEntitlementQueryServiceTest {
    private final SupporterEntitlementMapper mapper = mock(SupporterEntitlementMapper.class);
    private final SupporterEntitlementQueryService service = new SupporterEntitlementQueryService(mapper);

    @Test
    void shouldMapCurrentPlusEntitlementToPublicIdentity() {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(20);
        when(mapper.selectOne(any())).thenReturn(SupporterEntitlement.builder()
                .planCode("supporter_plus_30").expiresAt(expiresAt).build());

        var identity = service.findActive("user-1");

        assertThat(identity.active()).isTrue();
        assertThat(identity.tier()).isEqualTo("PLUS");
        assertThat(identity.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void shouldReturnInactiveWhenNoCurrentEntitlementExists() {
        when(mapper.selectOne(any())).thenReturn(null);
        assertThat(service.findActive("user-1").active()).isFalse();
    }
}
