<template>
    <div class="user-badges">
        <div
            v-for="badge in badges"
            :key="badge.id"
            class="badge-item"
            :style="{ borderColor: badge.badgeColor || '#409eff' }"
        >
            <el-tooltip :content="badge.badgeDesc || badge.grantReason" placement="top">
                <div class="badge-content">
                    <el-icon v-if="badge.badgeIcon" class="badge-icon">
                        <component :is="getBadgeIcon(badge.badgeType)" />
                    </el-icon>
                    <span class="badge-name">{{ badge.badgeName }}</span>
                </div>
            </el-tooltip>
        </div>

        <el-empty v-if="badges.length === 0" description="暂无徽章" :image-size="60" />
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Trophy, Star, Medal, VideoCamera, Stamp } from '@element-plus/icons-vue'
import { badgeApi, type UserBadge } from '@/api/aiEnhancement'

const props = defineProps<{
    userId: string
}>()

const badges = ref<UserBadge[]>([])

const getBadgeIcon = (badgeType: string) => {
    const iconMap: Record<string, any> = {
        expert: Trophy,
        contributor: Star,
        moderator: Medal,
        early_bird: VideoCamera,
        quality_answer: Stamp
    }
    return iconMap[badgeType] || Star
}

const loadBadges = async () => {
    try {
        const res = await badgeApi.getUserBadges(props.userId)
        if (res.code === 200) {
            badges.value = res.data
        }
    } catch (error) {
        console.error('加载徽章失败', error)
    }
}

onMounted(() => {
    loadBadges()
})
</script>

<style scoped lang="scss">
.user-badges {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;

    .badge-item {
        display: inline-flex;
        align-items: center;
        padding: 6px 12px;
        background: var(--el-fill-color-light);
        border: 2px solid;
        border-radius: 20px;
        cursor: default;
        transition: all 0.2s;

        &:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        }

        .badge-content {
            display: flex;
            align-items: center;
            gap: 6px;

            .badge-icon {
                font-size: 16px;
            }

            .badge-name {
                font-size: 13px;
                font-weight: 600;
            }
        }
    }
}
</style>
