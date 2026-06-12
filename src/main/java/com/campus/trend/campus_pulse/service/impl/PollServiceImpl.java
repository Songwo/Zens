package com.campus.trend.campus_pulse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.trend.campus_pulse.common.exception.BusinessException;
import com.campus.trend.campus_pulse.dto.request.PollCreateReq;
import com.campus.trend.campus_pulse.dto.request.PollVoteReq;
import com.campus.trend.campus_pulse.dto.response.PollOptionResp;
import com.campus.trend.campus_pulse.dto.response.PollResp;
import com.campus.trend.campus_pulse.entity.Poll;
import com.campus.trend.campus_pulse.entity.PollOption;
import com.campus.trend.campus_pulse.entity.PollVote;
import com.campus.trend.campus_pulse.entity.User;
import com.campus.trend.campus_pulse.mapper.PollMapper;
import com.campus.trend.campus_pulse.mapper.PollOptionMapper;
import com.campus.trend.campus_pulse.mapper.PollVoteMapper;
import com.campus.trend.campus_pulse.mapper.UserMapper;
import com.campus.trend.campus_pulse.service.PollService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 帖子投票服务实现。
 * 计票以 poll_vote 明细为准（唯一约束兜底重复/并发），写后重算冗余计数，避免漂移。
 */
@Slf4j
@Service
public class PollServiceImpl implements PollService {

    private static final int MIN_OPTIONS = 2;
    private static final int MAX_OPTIONS = 10;

    @Autowired
    private PollMapper pollMapper;

    @Autowired
    private PollOptionMapper pollOptionMapper;

    @Autowired
    private PollVoteMapper pollVoteMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createForPost(String postId, PollCreateReq req, String userId) {
        if (req == null) {
            return;
        }

        // 1. 清洗选项：去首尾空白、剔除空项、去重（保持输入顺序）
        List<String> options = new ArrayList<>();
        if (req.getOptions() != null) {
            Set<String> seen = new LinkedHashSet<>();
            for (String raw : req.getOptions()) {
                if (raw == null) continue;
                String text = raw.trim();
                if (text.isEmpty()) continue;
                if (text.length() > 200) {
                    text = text.substring(0, 200);
                }
                seen.add(text);
            }
            options.addAll(seen);
        }
        if (options.size() < MIN_OPTIONS || options.size() > MAX_OPTIONS) {
            throw new BusinessException("投票选项去重后需为 " + MIN_OPTIONS + "~" + MAX_OPTIONS + " 个");
        }

        // 2. 单选/多选与可选上限
        int multi = (req.getMultiChoice() != null && req.getMultiChoice() == 1) ? 1 : 0;
        int maxChoices;
        if (multi == 1) {
            // 多选：0=不限；否则 clamp 到 [1, 选项数]
            Integer reqMax = req.getMaxChoices();
            if (reqMax == null || reqMax <= 0) {
                maxChoices = 0;
            } else {
                maxChoices = Math.min(reqMax, options.size());
            }
        } else {
            maxChoices = 1;
        }

        // 3. 截止时间（若给定必须晚于当前）
        LocalDateTime deadline = req.getDeadline();
        if (deadline != null && !deadline.isAfter(LocalDateTime.now())) {
            throw new BusinessException("投票截止时间必须晚于当前时间");
        }

        // 4. 落库 poll
        String title = StringUtils.hasText(req.getTitle()) ? req.getTitle().trim() : null;
        if (title != null && title.length() > 200) {
            title = title.substring(0, 200);
        }
        Poll poll = new Poll()
                .setPostId(postId)
                .setTitle(title)
                .setMultiChoice(multi)
                .setMaxChoices(maxChoices)
                .setDeadline(deadline)
                .setStatus(1)
                .setVoterCount(0)
                .setCreatedBy(userId)
                .setCreatedAt(LocalDateTime.now());
        pollMapper.insert(poll);

        // 5. 落库选项（按输入顺序）
        int order = 0;
        for (String text : options) {
            PollOption option = new PollOption()
                    .setPollId(poll.getId())
                    .setOptionText(text)
                    .setOptionOrder(order++)
                    .setVoteCount(0);
            pollOptionMapper.insert(option);
        }

        log.info("投票创建成功: postId={}, pollId={}, options={}", postId, poll.getId(), options.size());
    }

    @Override
    public PollResp getByPostId(String postId, String currentUserId) {
        Poll poll = pollMapper.selectOne(
                new LambdaQueryWrapper<Poll>().eq(Poll::getPostId, postId));
        if (poll == null) {
            return null;
        }
        return buildResp(poll, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PollResp vote(PollVoteReq req, String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException("请先登录后再投票");
        }

        Poll poll = pollMapper.selectById(req.getPollId());
        if (poll == null) {
            throw new BusinessException("投票不存在");
        }
        if (isClosed(poll)) {
            throw new BusinessException("投票已截止或已关闭");
        }

        // 已投过则拒绝（不支持改票）
        Long existing = pollVoteMapper.selectCount(new LambdaQueryWrapper<PollVote>()
                .eq(PollVote::getPollId, poll.getId())
                .eq(PollVote::getUserId, userId));
        if (existing != null && existing > 0) {
            throw new BusinessException("你已经投过票了");
        }

        // 选项去重
        List<Long> optionIds = req.getOptionIds() == null ? List.of()
                : req.getOptionIds().stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        if (optionIds.isEmpty()) {
            throw new BusinessException("请至少选择一个选项");
        }

        // 数量校验
        if (poll.getMultiChoice() == null || poll.getMultiChoice() != 1) {
            if (optionIds.size() != 1) {
                throw new BusinessException("该投票为单选，只能选择一个选项");
            }
        } else {
            int max = poll.getMaxChoices() == null ? 0 : poll.getMaxChoices();
            if (max > 0 && optionIds.size() > max) {
                throw new BusinessException("最多只能选择 " + max + " 项");
            }
        }

        // 选项归属校验：所选 optionId 必须都属于该投票
        List<PollOption> pollOptions = pollOptionMapper.selectList(new LambdaQueryWrapper<PollOption>()
                .eq(PollOption::getPollId, poll.getId()));
        Set<Long> validIds = pollOptions.stream().map(PollOption::getId).collect(Collectors.toSet());
        for (Long oid : optionIds) {
            if (!validIds.contains(oid)) {
                throw new BusinessException("选项不属于该投票");
            }
        }

        // 写入投票明细（唯一约束兜底并发重复提交）
        try {
            for (Long oid : optionIds) {
                PollVote v = new PollVote()
                        .setPollId(poll.getId())
                        .setOptionId(oid)
                        .setUserId(userId)
                        .setCreatedAt(LocalDateTime.now());
                pollVoteMapper.insert(v);
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException("你已经投过票了");
        }

        // 重算冗余计数（以明细为准，避免漂移）
        recountPoll(poll);

        log.info("投票成功: pollId={}, userId={}, options={}", poll.getId(), userId, optionIds.size());
        return buildResp(pollMapper.selectById(poll.getId()), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PollResp close(Long pollId, String userId) {
        Poll poll = pollMapper.selectById(pollId);
        if (poll == null) {
            throw new BusinessException("投票不存在");
        }

        boolean isAuthor = poll.getCreatedBy() != null && poll.getCreatedBy().equals(userId);
        boolean isPrivileged = false;
        if (!isAuthor) {
            User user = userMapper.selectById(userId);
            String role = user != null && user.getRole() != null ? user.getRole() : "";
            isPrivileged = role.contains("ADMIN") || role.contains("MODERATOR");
        }
        if (!isAuthor && !isPrivileged) {
            throw new BusinessException("只有投票发起者、版主或管理员可以关闭投票");
        }

        if (poll.getStatus() != null && poll.getStatus() == 0) {
            // 幂等：已关闭直接返回
            return buildResp(poll, userId);
        }

        poll.setStatus(0);
        pollMapper.updateById(poll);
        log.info("投票已关闭: pollId={}, userId={}", pollId, userId);
        return buildResp(pollMapper.selectById(pollId), userId);
    }

    // ============================ 内部方法 ============================

    /**
     * 重算并写回该投票的冗余计数：各选项票数 + 去重参与人数。
     */
    private void recountPoll(Poll poll) {
        List<PollVote> votes = pollVoteMapper.selectList(new LambdaQueryWrapper<PollVote>()
                .eq(PollVote::getPollId, poll.getId()));

        // 各选项票数
        List<PollOption> options = pollOptionMapper.selectList(new LambdaQueryWrapper<PollOption>()
                .eq(PollOption::getPollId, poll.getId()));
        for (PollOption option : options) {
            int count = (int) votes.stream()
                    .filter(v -> option.getId().equals(v.getOptionId()))
                    .count();
            if (option.getVoteCount() == null || option.getVoteCount() != count) {
                option.setVoteCount(count);
                pollOptionMapper.updateById(option);
            }
        }

        // 去重参与人数
        int voterCount = (int) votes.stream().map(PollVote::getUserId).distinct().count();
        if (poll.getVoterCount() == null || poll.getVoterCount() != voterCount) {
            poll.setVoterCount(voterCount);
            pollMapper.updateById(poll);
        }
    }

    /**
     * 是否已截止：状态关闭 或 截止时间已过。
     */
    private boolean isClosed(Poll poll) {
        if (poll.getStatus() != null && poll.getStatus() == 0) {
            return true;
        }
        return poll.getDeadline() != null && poll.getDeadline().isBefore(LocalDateTime.now());
    }

    /**
     * 组装投票全貌 + 当前用户视角状态。
     */
    private PollResp buildResp(Poll poll, String currentUserId) {
        List<PollOption> options = pollOptionMapper.selectList(new LambdaQueryWrapper<PollOption>()
                .eq(PollOption::getPollId, poll.getId())
                .orderByAsc(PollOption::getOptionOrder));

        // 当前用户选了哪些选项
        Set<Long> myOptionIds;
        if (StringUtils.hasText(currentUserId)) {
            myOptionIds = pollVoteMapper.selectList(new LambdaQueryWrapper<PollVote>()
                            .eq(PollVote::getPollId, poll.getId())
                            .eq(PollVote::getUserId, currentUserId))
                    .stream().map(PollVote::getOptionId).collect(Collectors.toSet());
        } else {
            myOptionIds = Set.of();
        }

        int totalVotes = options.stream()
                .mapToInt(o -> o.getVoteCount() == null ? 0 : o.getVoteCount())
                .sum();

        List<PollOptionResp> optionResps = options.stream().map(o -> {
            int count = o.getVoteCount() == null ? 0 : o.getVoteCount();
            double percent = totalVotes > 0 ? Math.round(count * 1000.0 / totalVotes) / 10.0 : 0.0;
            return new PollOptionResp()
                    .setId(o.getId())
                    .setOptionText(o.getOptionText())
                    .setOptionOrder(o.getOptionOrder())
                    .setVoteCount(count)
                    .setPercent(percent)
                    .setVotedByMe(myOptionIds.contains(o.getId()));
        }).collect(Collectors.toList());

        boolean closed = isClosed(poll);
        boolean votedByMe = !myOptionIds.isEmpty();
        boolean isAuthor = StringUtils.hasText(currentUserId) && currentUserId.equals(poll.getCreatedBy());
        boolean canVote = StringUtils.hasText(currentUserId) && !closed && !votedByMe;
        boolean showResult = votedByMe || closed || isAuthor;

        return new PollResp()
                .setId(poll.getId())
                .setPostId(poll.getPostId())
                .setTitle(poll.getTitle())
                .setMultiChoice(poll.getMultiChoice())
                .setMaxChoices(poll.getMaxChoices())
                .setDeadline(poll.getDeadline())
                .setStatus(poll.getStatus())
                .setVoterCount(poll.getVoterCount() == null ? 0 : poll.getVoterCount())
                .setTotalVotes(totalVotes)
                .setCreatedBy(poll.getCreatedBy())
                .setCreatedAt(poll.getCreatedAt())
                .setOptions(optionResps)
                .setClosed(closed)
                .setVotedByMe(votedByMe)
                .setCanVote(canVote)
                .setShowResult(showResult);
    }
}
