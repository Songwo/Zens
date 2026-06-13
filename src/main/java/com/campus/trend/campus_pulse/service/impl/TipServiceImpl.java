package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.entity.*;
import com.campus.trend.campus_pulse.mapper.*;
import com.campus.trend.campus_pulse.service.NotificationService;
import com.campus.trend.campus_pulse.service.TipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 打赏服务实现
 */
@Slf4j
@Service
public class TipServiceImpl extends ServiceImpl<TipRecordMapper, TipRecord> implements TipService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void tip(String targetType, String targetId, Integer amount, String message, String userId) {
        // 1. 验证打赏金额
        if (amount == null || amount <= 0) {
            throw new BusinessException("打赏金额必须大于0");
        }
        if (amount > 100) {
            throw new BusinessException("单次打赏不能超过100积分");
        }

        // 2. 验证打赏者积分足够
        User tipper = userMapper.selectById(userId);
        if (tipper == null) {
            throw new BusinessException("用户不存在");
        }
        if (tipper.getPoints() < amount) {
            throw new BusinessException("积分不足，当前积分: " + tipper.getPoints());
        }

        // 3. 获取被打赏者ID
        String targetAuthorId = null;
        String targetTitle = "";

        if ("post".equals(targetType)) {
            Post post = postMapper.selectById(targetId);
            if (post == null) {
                throw new BusinessException("帖子不存在");
            }
            targetAuthorId = post.getUserId();
            targetTitle = post.getTitle();
        } else if ("comment".equals(targetType)) {
            Comment comment = commentMapper.selectById(targetId);
            if (comment == null) {
                throw new BusinessException("评论不存在");
            }
            targetAuthorId = comment.getUserId();
            Post post = postMapper.selectById(comment.getPostId());
            targetTitle = post != null ? post.getTitle() : "评论";
        } else {
            throw new BusinessException("不支持的打赏类型");
        }

        // 4. 不能打赏自己
        if (targetAuthorId.equals(userId)) {
            throw new BusinessException("不能打赏自己");
        }

        // 5. 扣除打赏者积分
        tipper.setPoints(tipper.getPoints() - amount);
        userMapper.updateById(tipper);

        // 6. 增加被打赏者积分
        User author = userMapper.selectById(targetAuthorId);
        if (author != null) {
            author.setPoints(author.getPoints() + amount);
            userMapper.updateById(author);
        }

        // 7. 创建打赏记录
        TipRecord record = new TipRecord()
                .setTipperId(userId)
                .setTargetType(targetType)
                .setTargetId(targetId)
                .setTargetAuthorId(targetAuthorId)
                .setAmount(amount)
                .setMessage(message)
                .setCreatedAt(LocalDateTime.now());
        this.save(record);

        // 8. 发送通知
        try {
            String notifyContent = tipper.getNickname() + " 打赏了你 " + amount + " 积分";
            if (message != null && !message.isEmpty()) {
                notifyContent += "，留言：" + message;
            }

            notificationService.createNotification(
                    targetAuthorId,
                    "tip",
                    "收到打赏",
                    notifyContent,
                    targetId,
                    userId
            );
        } catch (Exception e) {
            log.error("发送打赏通知失败", e);
        }

        log.info("打赏成功: targetType={}, targetId={}, amount={}, from={}, to={}",
                targetType, targetId, amount, userId, targetAuthorId);
    }

    @Override
    public List<TipRecord> getReceivedTips(String userId) {
        LambdaQueryWrapper<TipRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TipRecord::getTargetAuthorId, userId)
                .orderByDesc(TipRecord::getCreatedAt);
        return this.list(wrapper);
    }

    @Override
    public List<TipRecord> getSentTips(String userId) {
        LambdaQueryWrapper<TipRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TipRecord::getTipperId, userId)
                .orderByDesc(TipRecord::getCreatedAt);
        return this.list(wrapper);
    }

    @Override
    public Integer getTipSum(String targetType, String targetId) {
        LambdaQueryWrapper<TipRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TipRecord::getTargetType, targetType)
                .eq(TipRecord::getTargetId, targetId);

        List<TipRecord> records = this.list(wrapper);
        return records.stream()
                .mapToInt(TipRecord::getAmount)
                .sum();
    }
}
