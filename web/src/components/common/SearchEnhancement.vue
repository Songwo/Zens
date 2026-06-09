<template>
    <div class="search-enhancement">
        <!-- 搜索建议 -->
        <el-autocomplete
            v-model="searchKeyword"
            :fetch-suggestions="fetchSuggestions"
            placeholder="搜索帖子..."
            :trigger-on-focus="false"
            @select="handleSelect"
            @keyup.enter="handleSearch"
            clearable
            class="search-input"
        >
            <template #prefix>
                <el-icon><Search /></el-icon>
            </template>

            <template #default="{ item }">
                <div class="suggestion-item">
                    <el-icon><Clock /></el-icon>
                    <span>{{ item.value }}</span>
                </div>
            </template>
        </el-autocomplete>

        <!-- 热门搜索 -->
        <div v-if="showHotKeywords && hotKeywords.length > 0" class="hot-keywords">
            <span class="label">热门：</span>
            <el-tag
                v-for="keyword in hotKeywords"
                :key="keyword.keyword"
                size="small"
                @click="searchKeyword = keyword.keyword; handleSearch()"
                style="cursor: pointer; margin-right: 8px;"
            >
                {{ keyword.keyword }}
            </el-tag>
        </div>

        <!-- 搜索历史 -->
        <el-popover
            v-if="searchHistory.length > 0"
            placement="bottom-start"
            :width="300"
            trigger="click"
        >
            <template #reference>
                <el-button size="small" text>
                    <el-icon><Clock /></el-icon>
                    搜索历史
                </el-button>
            </template>

            <div class="search-history">
                <div class="history-header">
                    <span>最近搜索</span>
                    <el-button size="small" text @click="clearHistory">
                        清空
                    </el-button>
                </div>

                <div class="history-list">
                    <div
                        v-for="item in searchHistory"
                        :key="item.id"
                        class="history-item"
                        @click="searchKeyword = item.keyword; handleSearch()"
                    >
                        <el-icon><Search /></el-icon>
                        <span>{{ item.keyword }}</span>
                        <span class="result-count">{{ item.resultCount }} 条结果</span>
                    </div>
                </div>
            </div>
        </el-popover>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Clock } from '@element-plus/icons-vue'
import { searchApi, type HotKeyword, type SearchHistory } from '@/api/aiEnhancement'

const props = defineProps<{
    showHotKeywords?: boolean
}>()

const emit = defineEmits<{
    search: [keyword: string]
}>()

const searchKeyword = ref('')
const hotKeywords = ref<HotKeyword[]>([])
const searchHistory = ref<SearchHistory[]>([])

const fetchSuggestions = async (queryString: string, cb: any) => {
    if (!queryString) {
        cb([])
        return
    }

    try {
        const res = await searchApi.getSuggestions(queryString, 10)
        if (res.code === 200) {
            const suggestions = res.data.map(keyword => ({ value: keyword }))
            cb(suggestions)
        } else {
            cb([])
        }
    } catch (error) {
        console.error('获取搜索建议失败', error)
        cb([])
    }
}

const handleSelect = (item: any) => {
    searchKeyword.value = item.value
    handleSearch()
}

const handleSearch = () => {
    if (!searchKeyword.value.trim()) {
        return
    }

    emit('search', searchKeyword.value.trim())
    loadSearchHistory()
}

const loadHotKeywords = async () => {
    try {
        const res = await searchApi.getHotKeywords(8)
        if (res.code === 200) {
            hotKeywords.value = res.data
        }
    } catch (error) {
        console.error('加载热门搜索失败', error)
    }
}

const loadSearchHistory = async () => {
    try {
        const res = await searchApi.getHistory(10)
        if (res.code === 200) {
            searchHistory.value = res.data
        }
    } catch (error) {
        console.error('加载搜索历史失败', error)
    }
}

const clearHistory = async () => {
    try {
        const res = await searchApi.clearHistory()
        if (res.code === 200) {
            searchHistory.value = []
            ElMessage.success('已清空搜索历史')
        }
    } catch (error: any) {
        ElMessage.error(error.message || '清空失败')
    }
}

onMounted(() => {
    if (props.showHotKeywords) {
        loadHotKeywords()
    }
    loadSearchHistory()
})

defineExpose({
    searchKeyword,
    handleSearch
})
</script>

<style scoped lang="scss">
.search-enhancement {
    .search-input {
        width: 100%;
    }

    .suggestion-item {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 8px 0;
    }

    .hot-keywords {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-top: 12px;
        flex-wrap: wrap;

        .label {
            font-size: 13px;
            color: var(--el-text-color-secondary);
        }
    }
}

.search-history {
    .history-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding-bottom: 12px;
        border-bottom: 1px solid var(--el-border-color-lighter);
        font-size: 14px;
        font-weight: 600;
    }

    .history-list {
        margin-top: 8px;

        .history-item {
            display: flex;
            align-items: center;
            gap: 8px;
            padding: 8px 12px;
            cursor: pointer;
            border-radius: 4px;
            transition: background 0.2s;

            &:hover {
                background: var(--el-fill-color-light);
            }

            span {
                flex: 1;
                font-size: 13px;
            }

            .result-count {
                flex: none;
                color: var(--el-text-color-secondary);
                font-size: 12px;
            }
        }
    }
}
</style>
