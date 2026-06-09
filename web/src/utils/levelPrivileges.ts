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
    { level: 1, exp: 0, name: '初来乍到', privileges: ['发帖、评论、点赞、收藏'] },
    { level: 2, exp: 100, name: '崭露头角', privileges: ['发布外链与图片', '关注其他用户'] },
    { level: 3, exp: 300, name: '渐入佳境', privileges: ['发送私信', '参与打赏'] },
    { level: 4, exp: 600, name: '社区常客', privileges: ['创建帖子系列', '自定义个人主页封面'] },
    { level: 5, exp: 1000, name: '中坚力量', privileges: ['被采纳答案获额外声望', '评论加速展示'] },
    { level: 6, exp: 1500, name: '资深成员', privileges: ['申请成为板块版主'] },
    { level: 7, exp: 2100, name: '社区元老', privileges: ['协助标记优质内容'] },
    { level: 8, exp: 2800, name: '意见领袖', privileges: ['编辑社区 Wiki 类帖子'] },
    { level: 9, exp: 3600, name: '社区栋梁', privileges: ['参与社区治理投票'] },
    { level: 10, exp: 4500, name: '传奇人物', privileges: ['专属传奇徽章与昵称特效'] },
]

/** 获取等级称号 */
export function levelTitle(level: number): string {
    return LEVEL_PRIVILEGES.find(l => l.level === level)?.name || `Lv.${level}`
}
