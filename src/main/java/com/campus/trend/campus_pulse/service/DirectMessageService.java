package com.campus.trend.campus_pulse.service;

import com.campus.trend.campus_pulse.dto.request.DirectMessageSendReq;

import java.util.Map;

public interface DirectMessageService {

    void sendMessage(String senderId, DirectMessageSendReq req);

    Map<String, Object> listConversations(String userId, int page, int pageSize);

    Map<String, Object> listMessages(String userId, String peerId, int page, int pageSize);

    void markConversationRead(String userId, String peerId);

    long getUnreadCount(String userId);
}
