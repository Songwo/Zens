package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.CommunityQaAskReq;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

public interface AgentGatewayService {

    boolean isEnabled();

    Map<String, Object> health();

    Map<String, Object> diagnostics();

    Map<String, Object> ask(CommunityQaAskReq request);

    Map<String, Object> search(CommunityQaAskReq request);

    StreamingResponseBody askStream(CommunityQaAskReq request);
}
