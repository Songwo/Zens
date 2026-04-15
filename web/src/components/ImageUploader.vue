<script setup lang="ts">
import { ref } from 'vue'
import { uploadApi } from '@/api/upload'
import { UPLOAD_IMAGE_MAX_SIZE_BYTES, UPLOAD_IMAGE_MAX_SIZE_MB } from '@/constants/upload'
import { Plus, Delete, Loading } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const uploading = ref(false)

const uploadFile = async (file: File) => {
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return false
  }

  if (file.size > UPLOAD_IMAGE_MAX_SIZE_BYTES) {
    ElMessage.error(`图片大小不能超过 ${UPLOAD_IMAGE_MAX_SIZE_MB}MB`)
    return false
  }

  uploading.value = true
  try {
    const url = await uploadApi.uploadImage(file, 'post-cover')
    emit('update:modelValue', url)
    ElMessage.success('上传成功')
  } catch (error) {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

const handleUpload = async (options: any) => {
  const { file } = options
  return uploadFile(file as File)
}

const handlePaste = async (event: ClipboardEvent) => {
  const clipboard = event.clipboardData
  if (!clipboard?.items?.length || uploading.value) return

  const imageItem = Array.from(clipboard.items).find(item =>
    item.kind === 'file' && item.type.startsWith('image/')
  )

  if (!imageItem) return

  const file = imageItem.getAsFile()
  if (!file) return

  event.preventDefault()
  await uploadFile(file)
}

const removeImage = () => {
  emit('update:modelValue', '')
}
</script>

<template>
  <div class="image-uploader" tabindex="0" @paste="handlePaste">
    <el-upload
      class="uploader-box"
      :show-file-list="false"
      :http-request="handleUpload"
      accept="image/*"
      :disabled="uploading"
    >
      <div v-if="modelValue" class="preview-container">
        <el-image 
          :src="modelValue" 
          fit="cover" 
          class="preview-image"
        />
        <div class="preview-overlay">
          <el-icon class="remove-icon" @click.stop="removeImage"><Delete /></el-icon>
        </div>
      </div>
      <div v-else class="upload-placeholder">
        <el-icon v-if="uploading" class="is-loading"><Loading /></el-icon>
        <el-icon v-else class="plus-icon"><Plus /></el-icon>
        <div class="upload-text">
          <span>点击上传封面图</span>
          <span class="upload-tip">支持常见图片格式，最大 {{ UPLOAD_IMAGE_MAX_SIZE_MB }}MB，可粘贴图片</span>
        </div>
      </div>
    </el-upload>
  </div>
</template>

<style scoped>
.image-uploader {
  width: 100%;
  outline: none;
}

.uploader-box {
  width: 100%;
}

.uploader-box :deep(.el-upload) {
  width: 100%;
  display: block;
}

.preview-container {
  position: relative;
  width: 100%;
  height: 140px;
  border-radius: var(--el-border-radius-base);
  overflow: hidden;
  border: 1px solid var(--el-border-color);
}

.preview-image {
  width: 100%;
  height: 100%;
}

.preview-overlay {
  position: absolute;
  inset: 0;
  background-color: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s;
}

.preview-container:hover .preview-overlay {
  opacity: 1;
}

.remove-icon {
  font-size: 24px;
  color: #fff;
  cursor: pointer;
  transition: transform 0.2s;
}

.remove-icon:hover {
  transform: scale(1.2);
  color: var(--el-color-danger);
}

.upload-placeholder {
  width: 100%;
  height: 140px;
  border: 2px dashed var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background-color: var(--el-fill-color-blank);
  transition: all 0.2s;
}

.upload-placeholder:hover {
  border-color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
}

.plus-icon, .is-loading {
  font-size: 28px;
  color: var(--el-text-color-placeholder);
}

.upload-text {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.upload-tip {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 4px;
}
</style>
