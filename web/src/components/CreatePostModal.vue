<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import { MagicStick, Promotion } from '@element-plus/icons-vue'
import { postApi } from '@/api/post'
import { ElMessage } from 'element-plus'
import ImageUploader from './ImageUploader.vue'
import { ResultCode } from '@/types'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits(['update:modelValue', 'success'])

const loading = ref(false)
const extracting = ref(false)

const form = reactive({
  title: '',
  content: '',
  sectionId: 0,
  coverImage: '',
  tags: [] as string[]
})

const tagInput = ref('')

const isValid = computed(() => {
  return form.title.trim().length > 3 &&
         form.content.trim().length > 30 &&
         form.sectionId
})

const categories = [
  { id: 'c1', name: '校园头条' },
  { id: 'c2', name: '失物招领' },
  { id: 'c3', name: '表白墙' },
  { id: 'c4', name: '学术交流' },
  { id: 'c5', name: '二手市场' },
  { id: 'c6', name: '活动组织' }
]

const close = () => {
  emit('update:modelValue', false)
  // Song：说明
  Object.assign(form, {
    title: '',
    content: '',
    sectionId: 0,
    coverImage: '',
    tags: []
  })
}

const handleAIAnalysis = async () => {
  if (!form.content || extracting.value) return
  
  extracting.value = true
  try {
    const res = await postApi.extractTags({
      title: form.title,
      content: form.content
    })
    if (res.data && res.data.tags && res.data.tags.length > 0) {
      if (Array.isArray(res.data.tags)) {
        form.tags = res.data.tags
      } else if (typeof res.data.tags === 'string') {
        form.tags = (res.data.tags as string).split(',').filter(Boolean)
      }
      ElMessage.success('AI 分析完成')
    } else {
      ElMessage.warning('未能提取出有效标签')
    }
  } catch (error) {
    // Song：说明
  } finally {
    extracting.value = false
  }
}

const handleSubmit = async () => {
  if (!isValid.value || loading.value) return
  if (form.title.trim().length <= 3) {
    ElMessage.warning('标题需超过3个字符')
    return
  }
  if (form.content.trim().length <= 30) {
    ElMessage.warning('正文需超过30个字符')
    return
  }

  loading.value = true
  
  try {
    const res = await postApi.create({
      title: form.title,
      content: form.content,
      sectionId: form.sectionId,
      coverImage: form.coverImage,
      tags: form.tags.join(','),
      status: 1
    })
    
    if (res.code === ResultCode.SUCCESS) {
      ElMessage.success('发布成功')
      emit('success')
      close()
    } else {
      ElMessage.error(res.message || '发布失败')
    }
  } catch (error: any) {
    if (error?.response) {
      return
    }
    const message = typeof error?.message === 'string' ? error.message.trim() : ''
    ElMessage.error(message && message !== 'Network Error' ? message : '发布失败')
  } finally {
    loading.value = false
  }
}

const addTag = () => {
  const val = tagInput.value.trim()
  if (val && !form.tags.includes(val)) {
    form.tags.push(val)
  }
  tagInput.value = ''
}

const removeTag = (tag: string) => {
  form.tags = form.tags.filter(t => t !== tag)
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="发布新动态"
    width="680px"
    destroy-on-close
    class="create-post-dialog"
    @close="close"
  >
    <el-form :model="form" label-position="top" class="post-form">
      <el-form-item label="标题" required>
        <el-input 
          v-model="form.title" 
          placeholder="起一个吸引人的标题" 
          maxlength="100"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="分区" required>
        <el-radio-group v-model="form.sectionId" class="category-group">
          <el-radio-button 
            v-for="cat in categories" 
            :key="cat.id" 
            :label="cat.id"
          >
            {{ cat.name }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item required>
        <template #label>
          <div class="content-header">
            <span>正文内容</span>
            <el-button 
              v-if="form.content.length > 10"
              link 
              type="primary" 
              :icon="MagicStick"
              :loading="extracting"
              @click="handleAIAnalysis"
            >
              AI 辅助标签
            </el-button>
          </div>
        </template>
        <el-input
          v-model="form.content"
          type="textarea"
          :rows="8"
          placeholder="分享你的校园见闻、求助或感悟..."
        />
      </el-form-item>

      <el-form-item label="封面图 (可选)">
        <ImageUploader v-model="form.coverImage" />
      </el-form-item>

      <el-form-item label="标签">
        <div class="tags-container">
          <div class="tags-list">
            <el-tag
              v-for="tag in form.tags"
              :key="tag"
              closable
              class="tag-item"
              @close="removeTag(tag)"
            >
              {{ tag }}
            </el-tag>
          </div>
          <el-input
            v-model="tagInput"
            placeholder="按回车添加标签"
            size="small"
            class="tag-input"
            @keyup.enter="addTag"
            @blur="addTag"
          />
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="close">取消</el-button>
        <el-button 
          type="primary" 
          :loading="loading" 
          :disabled="!isValid"
          :icon="Promotion"
          @click="handleSubmit"
        >
          立即发布
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.create-post-dialog :deep(.el-dialog__body) {
  padding-top: 10px;
}

.category-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.category-group :deep(.el-radio-button__inner) {
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base) !important;
  margin-right: 8px;
  margin-bottom: 8px;
  box-shadow: none !important;
}

.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.tags-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tags-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag-input {
  max-width: 200px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
