/**
 * 信任等级 TL0-TL4 工具（与后端 TrustLevelServiceImpl / campus.trust-level 配置对齐）
 *
 * 设计：经验值等级(level, Lv1-10) 反映资历；信任等级(trustLevel, TL0-4) 反映行为质量。
 * TL0-TL3 由行为指标自动晋升，TL3 不活跃会降级，TL4 仅管理员手动。
 */
export interface TrustLevelSpec {
    /** 信任等级 0-4 */
    level: number
    label: string
    description: string
    privileges: string[]
    /** 徽章颜色 */
    color: string
}

export const TRUST_LEVELS: TrustLevelSpec[] = [
    {
        level: 0,
        label: '新人',
        description: '新注册用户，部分功能受限以防止垃圾内容',
        privileges: ['浏览', '评论', '点赞'],
        color: '#909399',
    },
    {
        level: 1,
        label: '基础',
        description: '已通过基础门槛，可发布外链',
        privileges: ['发布外链', '搜索无限制'],
        color: '#67c23a',
    },
    {
        level: 2,
        label: '成员',
        description: '活跃社区成员，可上传附件、使用私信',
        privileges: ['上传附件', '私信', '创建标签'],
        color: '#409eff',
    },
    {
        level: 3,
        label: '常客',
        description: '社区常客，举报权重更高，可参与自治',
        privileges: ['举报权重 5x', '标记问题内容', '可被选为版主'],
        color: '#e6a23c',
    },
    {
        level: 4,
        label: '领袖',
        description: '社区领袖，由管理员授予，拥有高级治理权限',
        privileges: ['置顶帖子', '合并/分割帖子', '编辑他人帖子'],
        color: '#f56c6c',
    },
]

/** 信任等级标签 */
export function trustLevelLabel(tl?: number): string {
    const level = Math.max(0, Math.min(4, tl ?? 0))
    return TRUST_LEVELS[level]!.label
}

/** 信任等级颜色 */
export function trustLevelColor(tl?: number): string {
    const level = Math.max(0, Math.min(4, tl ?? 0))
    return TRUST_LEVELS[level]!.color
}

/** 能否发布外链（TL >= 1） */
export function canPostLinks(tl?: number): boolean {
    return (tl ?? 0) >= 1
}

/** 能否上传附件（TL >= 2） */
export function canUploadAttachments(tl?: number): boolean {
    return (tl ?? 0) >= 2
}

/** TL3 及以上用户的举报权重 */
export function flagWeight(tl?: number): number {
    const level = tl ?? 0
    if (level >= 3) return 5
    if (level >= 2) return 3
    return 1
}

/** 内容中是否包含外链（用于 TL0 拦截判定） */
export function containsExternalLink(content?: string): boolean {
    if (!content) return false
    return /\bhttps?:\/\/|www\.|\[.+?\]\(.+?\)/i.test(content)
}
