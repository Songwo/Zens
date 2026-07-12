-- 支持者权益文案升级：仅提供身份展示、个人资料装饰与共建反馈，不影响推荐、审核和基础发帖权。
UPDATE `supporter_plan`
SET `description` = '支持社区持续维护与基础设施成本，获得清晰可见的支持者身份与个人资料装饰。',
    `benefits_json` = '["30 天支持者身份与到期时间展示","个人资料支持者徽章与专属强调色","本人近 30 天创作数据简报"]'
WHERE `code` = 'supporter_30';

UPDATE `supporter_plan`
SET `description` = '在支持者权益之上参与长期共建，获得独立的共建身份和结构化反馈通道。',
    `benefits_json` = '["包含 Zens 支持者全部权益","个人资料共建支持者专属徽章","产品共建反馈专属通道","反馈处理状态与官方回复查看"]'
WHERE `code` = 'supporter_plus_30';
