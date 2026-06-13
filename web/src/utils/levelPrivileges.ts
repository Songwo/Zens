/**
 * 信任等级路线图（与后端 LevelController.LEVEL_THRESHOLDS 对齐）。
 * 说明：本期“等级特权”仅作展示与激励路线图，不在后端强制权限。
 * 经验阈值：0 / 100 / 300 / 600 / 1000 / 1500 / 2100 / 2800 / 3600 / 4500
 */
export interface LevelPrivilege {
    /** 等级 (1-10) */
    level: number
    /** 升至该级所需累计经验 */
    exp: number
    /** 等级称号 */
    name: string
    /** 该级解锁/代表的权益（展示文案） */
    privileges: string[]
}

export const LEVEL_PRIVILEGES: LevelPrivilege[] = [
    { level: 1, exp: 0, name: '初来乍到', privileges: ['发帖、评论、点赞、收藏', '每日签到', '基础个人主页'] },
    { level: 2, exp: 100, name: '崭露头角', privileges: ['发布外链与图片', '关注其他用户', '帖子草稿箱', '参与投票'] },
    { level: 3, exp: 300, name: '渐入佳境', privileges: ['发送私信', '参与打赏', '评论收藏', '内容表情回应'] },
    { level: 4, exp: 600, name: '社区常客', privileges: ['创建帖子系列', '自定义个人主页封面', '追踪主题更新', 'Markdown 高级编辑'] },
    { level: 5, exp: 1000, name: '中坚力量', privileges: ['被采纳答案获额外声望', '评论加速展示', '个人徽章样式', '更高上传额度'] },
    { level: 6, exp: 1500, name: '资深成员', privileges: ['申请成为板块版主', '优先进入内容推荐池', '创建长图文指南', '更多主页模块'] },
    { level: 7, exp: 2100, name: '社区元老', privileges: ['协助标记优质内容', '参与内容巡检', '主题合集推荐', '专属资料卡标识'] },
    { level: 8, exp: 2800, name: '意见领袖', privileges: ['编辑社区 Wiki 类帖子', '发起社区共创主题', '优先体验新功能', '高级搜索筛选'] },
    { level: 9, exp: 3600, name: '社区栋梁', privileges: ['参与社区治理投票', '协助维护精选内容', '专属动态效果', '年度贡献展示'] },
    { level: 10, exp: 4500, name: '传奇人物', privileges: ['专属传奇徽章与昵称特效', '社区荣誉墙展示', '优先产品反馈通道', '纪念身份标识'] },
]

/** 获取等级称号 */
export function levelTitle(level: number): string {
    return LEVEL_PRIVILEGES.find(l => l.level === level)?.name || `Lv.${level}`
}
