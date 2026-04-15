package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.trend.campus_pulse.dto.request.DirectMessageSendReq;
import com.campus.trend.campus_pulse.dto.response.DirectConversationResp;
import com.campus.trend.campus_pulse.dto.response.DirectMessageResp;
import com.campus.trend.campus_pulse.entity.DirectMessage;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.DirectMessageMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.common.api.ResultCode;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.service.DirectMessageService;
import com.campus.trend.campus_pulse.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DirectMessageServiceImpl implements DirectMessageService {

    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int CONVERSATION_SCAN_LIMIT = 1000;

    private final DirectMessageMapper directMessageMapper;
    private final UserMapper userMapper;
    private final MailService mailService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendMessage(String senderId, DirectMessageSendReq req) {
        if (!StringUtils.hasText(senderId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "未登录，无法发送私信");
        }
        if (!StringUtils.hasText(req.getReceiverId()) || !StringUtils.hasText(req.getContent())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "接收者与消息内容不能为空");
        }
        if (Objects.equals(senderId, req.getReceiverId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能给自己发送私信");
        }

        User receiver = userMapper.selectById(req.getReceiverId());
        if (receiver == null || (receiver.getStatus() != null && receiver.getStatus() != 1)) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "接收者不存在或不可用");
        }

        String content = req.getContent().trim();
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }

        DirectMessage message = new DirectMessage()
                .setConversationId(buildConversationId(senderId, req.getReceiverId()))
                .setSenderId(senderId)
                .setReceiverId(req.getReceiverId())
                .setContent(content)
                .setIsRead(0)
                .setCreatedAt(LocalDateTime.now());
        directMessageMapper.insert(message);

        try {
            User sender = userMapper.selectById(senderId);
            if (StringUtils.hasText(receiver.getEmail())
                    && (receiver.getEmailNotifyEnabled() == null || receiver.getEmailNotifyEnabled() == 1)) {
                String senderName = sender != null && StringUtils.hasText(sender.getNickname())
                        ? sender.getNickname()
                        : (sender != null ? sender.getUsername() : "社区用户");
                mailService.sendSimpleMail(
                        receiver.getEmail(),
                        "【Zens社区】你收到一条新私信",
                        "你收到了来自 " + senderName + " 的新私信：\n\n"
                                + content
                                + "\n\n请前往站内私信查看完整内容。");
            }
        } catch (Exception e) {
            log.warn("私信邮件同步失败: receiverId={}, err={}", req.getReceiverId(), e.getMessage());
        }

        log.info("私信发送成功: sender={}, receiver={}, msgId={}",
                senderId, req.getReceiverId(), message.getId());
    }

    @Override
    public Map<String, Object> listConversations(String userId, int page, int pageSize) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "未登录，无法查看会话");
        }
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 50);

        List<DirectMessage> allMessages = directMessageMapper.selectList(
                new LambdaQueryWrapper<DirectMessage>()
                        .and(w -> w.eq(DirectMessage::getSenderId, userId)
                                .or()
                                .eq(DirectMessage::getReceiverId, userId))
                        .orderByDesc(DirectMessage::getCreatedAt)
                        .last("LIMIT " + CONVERSATION_SCAN_LIMIT)
        );

        Map<String, DirectMessage> latestByConversation = new LinkedHashMap<>();
        for (DirectMessage msg : allMessages) {
            latestByConversation.putIfAbsent(msg.getConversationId(), msg);
        }

        Map<String, Long> unreadByConversation = directMessageMapper.selectList(
                new LambdaQueryWrapper<DirectMessage>()
                        .eq(DirectMessage::getReceiverId, userId)
                        .eq(DirectMessage::getIsRead, 0))
                .stream()
                .collect(Collectors.groupingBy(DirectMessage::getConversationId, Collectors.counting()));

        Map<String, String> peerByConversation = new HashMap<>();
        List<String> peerIds = new ArrayList<>();
        for (DirectMessage latest : latestByConversation.values()) {
            String peerId = Objects.equals(latest.getSenderId(), userId)
                    ? latest.getReceiverId()
                    : latest.getSenderId();
            peerByConversation.put(latest.getConversationId(), peerId);
            peerIds.add(peerId);
        }
        Map<String, User> userMap = new HashMap<>();
        List<String> distinctPeerIds = peerIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (!distinctPeerIds.isEmpty()) {
            userMap = userMapper.selectBatchIds(distinctPeerIds)
                    .stream()
                    .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        }

        List<DirectConversationResp> conversations = new ArrayList<>();
        for (DirectMessage latest : latestByConversation.values()) {
            String peerId = peerByConversation.get(latest.getConversationId());
            User peer = userMap.get(peerId);
            if (peer == null) {
                continue;
            }

            DirectConversationResp resp = new DirectConversationResp();
            resp.setConversationId(latest.getConversationId());
            resp.setPeerId(peerId);
            resp.setPeerName(StringUtils.hasText(peer.getNickname()) ? peer.getNickname() : peer.getUsername());
            resp.setPeerAvatar(peer.getAvatar());
            resp.setLastMessage(latest.getContent());
            resp.setLastTime(latest.getCreatedAt());
            resp.setUnreadCount(unreadByConversation.getOrDefault(latest.getConversationId(), 0L));
            conversations.add(resp);
        }

        int total = conversations.size();
        int from = (safePage - 1) * safePageSize;
        int to = Math.min(from + safePageSize, total);
        List<DirectConversationResp> records = from >= total ? new ArrayList<>() : conversations.subList(from, to);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("current", safePage);
        result.put("size", safePageSize);
        result.put("pages", total == 0 ? 0 : (long) Math.ceil((double) total / safePageSize));
        log.debug("私信会话列表查询: userId={}, page={}, pageSize={}, total={}",
                userId, safePage, safePageSize, total);
        return result;
    }

    @Override
    public Map<String, Object> listMessages(String userId, String peerId, int page, int pageSize) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(ResultCode.NO_PERMISSION, "未登录，无法查看私信");
        }
        if (!StringUtils.hasText(peerId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "对话对象不能为空");
        }
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);

        Page<DirectMessage> queryPage = new Page<>(safePage, safePageSize);
        Page<DirectMessage> resultPage = directMessageMapper.selectPage(
                queryPage,
                new LambdaQueryWrapper<DirectMessage>()
                        .and(w -> w.and(w1 -> w1.eq(DirectMessage::getSenderId, userId)
                                        .eq(DirectMessage::getReceiverId, peerId))
                                .or(w2 -> w2.eq(DirectMessage::getSenderId, peerId)
                                        .eq(DirectMessage::getReceiverId, userId)))
                        .orderByDesc(DirectMessage::getCreatedAt)
        );

        List<DirectMessageResp> records = resultPage.getRecords().stream().map(item -> {
            DirectMessageResp resp = new DirectMessageResp();
            resp.setId(item.getId());
            resp.setSenderId(item.getSenderId());
            resp.setReceiverId(item.getReceiverId());
            resp.setContent(item.getContent());
            resp.setIsRead(item.getIsRead());
            resp.setCreatedAt(item.getCreatedAt());
            resp.setSelf(Objects.equals(item.getSenderId(), userId));
            return resp;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", resultPage.getTotal());
        result.put("current", resultPage.getCurrent());
        result.put("size", resultPage.getSize());
        result.put("pages", resultPage.getPages());
        log.debug("私信消息列表查询: userId={}, peerId={}, page={}, pageSize={}, total={}",
                userId, peerId, safePage, safePageSize, resultPage.getTotal());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markConversationRead(String userId, String peerId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(peerId)) {
            return;
        }
        directMessageMapper.update(
                null,
                new LambdaUpdateWrapper<DirectMessage>()
                        .eq(DirectMessage::getSenderId, peerId)
                        .eq(DirectMessage::getReceiverId, userId)
                        .eq(DirectMessage::getIsRead, 0)
                        .set(DirectMessage::getIsRead, 1)
        );
        log.debug("会话已读: userId={}, peerId={}", userId, peerId);
    }

    @Override
    public long getUnreadCount(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0L;
        }
        Long count = directMessageMapper.selectCount(
                new LambdaQueryWrapper<DirectMessage>()
                        .eq(DirectMessage::getReceiverId, userId)
                        .eq(DirectMessage::getIsRead, 0));
        return count != null ? count : 0L;
    }

    private String buildConversationId(String a, String b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }
}
