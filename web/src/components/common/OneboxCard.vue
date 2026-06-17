<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { oneboxApi, type OneboxPreview } from '@/api/onebox'

const props = defineProps<{
  url: string
}>()

const loading = ref(true)
const data = ref<OneboxPreview | null>(null)
const failed = ref(false)

const hostname = computed(() => {
  try {
    return new URL(props.url).hostname.replace(/^www\./, '')
  } catch {
    return props.url
  }
})

const isYoutube = computed(() => data.value?.provider === 'youtube' && data.value?.embeddable)
const isBilibili = computed(() => data.value?.provider === 'bilibili' && data.value?.embeddable)

const youtubeEmbedUrl = computed(() => {
  if (!isYoutube.value || !data.value?.embedId) return null
  return `https://www.youtube.com/embed/${data.value.embedId}`
})

const bilibiliEmbedUrl = computed(() => {
  if (!isBilibili.value || !data.value?.embedId) return null
  return `https://player.bilibili.com/player.html?bvid=${data.value.embedId}&high_quality=1&autoplay=0`
})

onMounted(async () => {
  try {
    const res = await oneboxApi.preview(props.url)
    data.value = res.data
  } catch (e) {
    failed.value = true
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="onebox-wrapper">
    <!-- 加载中 -->
    <a v-if="loading" :href="url" target="_blank" rel="noopener noreferrer nofollow" class="onebox-card onebox-loading">
      <span class="onebox-icon">🔗</span>
      <span class="onebox-info">
        <span class="onebox-title">{{ hostname }}</span>
        <span class="onebox-host">加载中...</span>
      </span>
    </a>

    <!-- YouTube 嵌入 -->
    <div v-else-if="isYoutube && youtubeEmbedUrl" class="onebox-embed onebox-yt">
      <iframe
        :src="youtubeEmbedUrl"
        frameborder="0"
        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
        allowfullscreen
        loading="lazy"
      ></iframe>
    </div>

    <!-- Bilibili 嵌入 -->
    <div v-else-if="isBilibili && bilibiliEmbedUrl" class="onebox-embed onebox-bilibili">
      <iframe
        :src="bilibiliEmbedUrl"
        frameborder="0"
        allowfullscreen
        scrolling="no"
        loading="lazy"
      ></iframe>
    </div>

    <!-- OG 卡片（有图/标题/描述） -->
    <a
      v-else-if="data?.title"
      :href="url"
      target="_blank"
      rel="noopener noreferrer nofollow"
      class="onebox-card"
    >
      <img
        v-if="data.image"
        :src="data.image"
        :alt="data.title"
        class="onebox-thumb"
        loading="lazy"
        referrerpolicy="no-referrer"
        @error="($event.target as HTMLImageElement).style.display = 'none'"
      />
      <span class="onebox-info">
        <span class="onebox-title">{{ data.title }}</span>
        <span v-if="data.description" class="onebox-desc">{{ data.description }}</span>
        <span class="onebox-host">{{ data.siteName || hostname }}</span>
      </span>
      <span class="onebox-arrow">↗</span>
    </a>

    <!-- 兜底：仅显示 hostname -->
    <a
      v-else
      :href="url"
      target="_blank"
      rel="noopener noreferrer nofollow"
      class="onebox-card onebox-fallback"
    >
      <span class="onebox-icon">🔗</span>
      <span class="onebox-info">
        <span class="onebox-title">{{ hostname }}</span>
      </span>
      <span class="onebox-arrow">↗</span>
    </a>
  </div>
</template>

<style scoped>
.onebox-wrapper {
  margin: 12px 0;
}

.onebox-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  text-decoration: none;
  color: inherit;
  transition: border-color 0.2s, box-shadow 0.2s;
  background: var(--el-fill-color-blank);
}

.onebox-card:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.onebox-loading,
.onebox-fallback {
  opacity: 0.85;
}

.onebox-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.onebox-thumb {
  width: 80px;
  height: 60px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
  background: var(--el-fill-color-light);
}

.onebox-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.onebox-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.onebox-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}

.onebox-host {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

.onebox-arrow {
  flex-shrink: 0;
  color: var(--el-text-color-placeholder);
  font-size: 14px;
}

.onebox-embed {
  position: relative;
  width: 100%;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  background: #000;
}

.onebox-yt {
  aspect-ratio: 16 / 9;
}

.onebox-bilibili {
  aspect-ratio: 16 / 9;
}

.onebox-embed iframe {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border: 0;
}

:deep(html.dark) .onebox-card {
  background: var(--el-fill-color-light);
}
</style>
