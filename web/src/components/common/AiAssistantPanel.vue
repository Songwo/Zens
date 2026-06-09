<template>
    <div class="ai-assistant-panel">
        <!-- 内容质量评分 -->
        <div v-if="showQualityScore && qualityEvaluation" class="quality-score">
            <div class="score-header">
                <el-icon :class="getScoreIconClass()">
                    <component :is="getScoreIcon()" />
                </el-icon>
                <div>
                    <div class="score-value">{{ qualityEvaluation.score }}</div>
                    <div class="score-level">{{ qualityEvaluation.level }}</div>
                </div>
            </div>

            <div v-if="qualityEvaluation.suggestions.length > 0" class="suggestions">
                <div
                    v-for="(suggestion, index) in qualityEvaluation.suggestions"
                    :key="index"
                    class="suggestion-item"
                >
                    <el-icon><InfoFilled /></el-icon>
                    <span>{{ suggestion }}</span>
                </div>
            </div>
        </div>

        <!-- 相似问题推荐 -->
        <div v-if="showSimilarPosts && similarPosts.length > 0" class="similar-posts">
            <div class="section-title">
                <el-icon><QuestionFilled /></el-icon>
                <span>可能相关的问题</span>
            </div>

            <div class="post-list">
                <div
                    v-for="post in similarPosts"
                    :key="post.postId"
                    class="post-item"
                    @click="$router.push(`/post/${post.postId}`)"
                >
                    <div class="post-title">
                        {{ post.title }}
                        <el-tag v-if="post.hasAdoptedAnswer" type="success" size="small">
                            已解决
                        </el-tag>
                    </div>
                    <div class="post-meta">
                        <span>{{ post.likeCount }} 赞</span>
                        <span>{{ post.commentCount }} 评论</span>
                        <span class="similarity">相似度 {{ (post.similarity * 100).toFixed(0) }}%</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- 智能标签推荐 -->
        <div v-if="showTagSuggestions && suggestedTags.length > 0" class="tag-suggestions">
            <div class="section-title">
                <el-icon><PriceTag /></el-icon>
                <span>推荐标签</span>
            </div>

            <div class="tag-list">
                <el-tag
                    v-for="tag in suggestedTags"
                    :key="tag"
                    @click="$emit('tag-selected', tag)"
                    style="cursor: pointer; margin-right: 8px;"
                >
                    {{ tag }}
                </el-tag>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { SuccessFilled, WarningFilled, CircleCloseFilled, InfoFilled, QuestionFilled, PriceTag } from '@element-plus/icons-vue'
import { aiAssistantApi, type SimilarPost, type QualityEvaluation } from '@/api/aiEnhancement'
import { debounce } from 'lodash-es'

const props = defineProps<{
    title: string
    content: string
    showQualityScore?: boolean
    showSimilarPosts?: boolean
    showTagSuggestions?: boolean
}>()

const emit = defineEmits<{
    'tag-selected': [tag: string]
}>()

const qualityEvaluation = ref<QualityEvaluation | null>(null)
const similarPosts = ref<SimilarPost[]>([])
const suggestedTags = ref<string[]>([])

const evaluateQuality = debounce(async () => {
    if (!props.title && !props.content) {
        qualityEvaluation.value = null
        return
    }

    try {
        const res = await aiAssistantApi.evaluateQuality(props.title, props.content)
        if (res.code === 200) {
            qualityEvaluation.value = res.data
        }
    } catch (error) {
        console.error('评估质量失败', error)
    }
}, 1000)

const findSimilarPosts = debounce(async () => {
    if (!props.title && !props.content) {
        similarPosts.value = []
        return
    }

    try {
        const res = await aiAssistantApi.findSimilarPosts(props.title, props.content)
        if (res.code === 200) {
            similarPosts.value = res.data.slice(0, 5)
        }
    } catch (error) {
        console.error('查找相似问题失败', error)
    }
}, 1500)

const getSuggestedTags = debounce(async () => {
    if (!props.title && !props.content) {
        suggestedTags.value = []
        return
    }

    try {
        const res = await aiAssistantApi.suggestTags(props.title, props.content)
        if (res.code === 200) {
            suggestedTags.value = res.data
        }
    } catch (error) {
        console.error('获取标签建议失败', error)
    }
}, 1500)

const getScoreIcon = () => {
    if (!qualityEvaluation.value) return InfoFilled
    const score = qualityEvaluation.value.score
    if (score >= 80) return SuccessFilled
    if (score >= 40) return WarningFilled
    return CircleCloseFilled
}

const getScoreIconClass = () => {
    if (!qualityEvaluation.value) return ''
    const score = qualityEvaluation.value.score
    if (score >= 80) return 'success'
    if (score >= 40) return 'warning'
    return 'danger'
}

watch(() => [props.title, props.content], () => {
    if (props.showQualityScore) {
        evaluateQuality()
    }
    if (props.showSimilarPosts) {
        findSimilarPosts()
    }
    if (props.showTagSuggestions) {
        getSuggestedTags()
    }
}, { immediate: true })
</script>

<style scoped lang="scss">
.ai-assistant-panel {
    display: flex;
    flex-direction: column;
    gap: 20px;

    .quality-score {
        padding: 16px;
        background: var(--el-fill-color-lighter);
        border-radius: 8px;

        .score-header {
            display: flex;
            align-items: center;
            gap: 12px;
            margin-bottom: 12px;

            .el-icon {
                font-size: 32px;

                &.success {
                    color: var(--el-color-success);
                }

                &.warning {
                    color: var(--el-color-warning);
                }

                &.danger {
                    color: var(--el-color-danger);
                }
            }

            .score-value {
                font-size: 24px;
                font-weight: 700;
                line-height: 1;
            }

            .score-level {
                font-size: 12px;
                color: var(--el-text-color-secondary);
                margin-top: 4px;
            }
        }

        .suggestions {
            display: flex;
            flex-direction: column;
            gap: 8px;

            .suggestion-item {
                display: flex;
                align-items: flex-start;
                gap: 6px;
                font-size: 13px;
                line-height: 1.5;

                .el-icon {
                    margin-top: 2px;
                    color: var(--el-color-primary);
                }
            }
        }
    }

    .similar-posts,
    .tag-suggestions {
        .section-title {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 14px;
            font-weight: 600;
            margin-bottom: 12px;
            color: var(--el-text-color-primary);
        }
    }

    .similar-posts {
        .post-list {
            display: flex;
            flex-direction: column;
            gap: 8px;

            .post-item {
                padding: 12px;
                background: var(--el-fill-color-light);
                border-radius: 6px;
                cursor: pointer;
                transition: all 0.2s;

                &:hover {
                    background: var(--el-fill-color);
                    transform: translateX(4px);
                }

                .post-title {
                    display: flex;
                    align-items: center;
                    gap: 8px;
                    font-size: 14px;
                    font-weight: 500;
                    margin-bottom: 6px;
                }

                .post-meta {
                    display: flex;
                    gap: 12px;
                    font-size: 12px;
                    color: var(--el-text-color-secondary);

                    .similarity {
                        margin-left: auto;
                        color: var(--el-color-primary);
                    }
                }
            }
        }
    }

    .tag-suggestions {
        .tag-list {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
        }
    }
}
</style>
