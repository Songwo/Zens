<script setup lang="ts">
import { ref } from 'vue'
import { uploadApi } from '@/api/upload'
import { ImagePlus, X, Loader2 } from 'lucide-vue-next'
import { toast } from 'vue-sonner'

const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const triggerUpload = () => {
  fileInput.value?.click()
}

const handleFileChange = async (e: Event) => {
  const files = (e.target as HTMLInputElement).files
  if (!files || files.length === 0) return

  const file = files[0]
  if (!file.type.startsWith('image/')) {
    toast.error('请选择图片文件')
    return
  }
  
  if (file.size > 5 * 1024 * 1024) {
    toast.error('图片大小不能超过 5MB')
    return
  }

  uploading.value = true
  try {
    const res = await uploadApi.uploadImage(file)
    emit('update:modelValue', res.data)
    toast.success('上传成功')
  } catch (error) {
    toast.error('上传失败')
  } finally {
    uploading.value = false
    // Clear input so same file can be selected again if needed
    if (fileInput.value) fileInput.value.value = ''
  }
}

const removeImage = (e: Event) => {
  e.stopPropagation()
  emit('update:modelValue', '')
}
</script>

<template>
  <div class="w-full">
    <input 
      ref="fileInput"
      type="file" 
      accept="image/*" 
      class="hidden" 
      @change="handleFileChange"
    />

    <!-- Image Preview -->
    <div 
      v-if="modelValue" 
      class="relative w-full aspect-[21/9] rounded-2xl overflow-hidden group border border-slate-200"
    >
      <img :src="modelValue" class="w-full h-full object-cover" />
      <div class="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
        <button 
          @click="removeImage"
          class="p-2 bg-white/20 hover:bg-white/40 backdrop-blur-md rounded-full text-white transition-all transform hover:scale-110"
        >
          <X class="w-5 h-5" />
        </button>
      </div>
    </div>

    <!-- Upload Placeholder -->
    <button 
      v-else
      @click="triggerUpload"
      :disabled="uploading"
      class="w-full aspect-[21/9] rounded-2xl border-2 border-dashed border-slate-200 hover:border-slate-400 hover:bg-slate-50 transition-all flex flex-col items-center justify-center gap-3 group disabled:opacity-50 disabled:cursor-not-allowed"
    >
      <div class="w-12 h-12 bg-slate-100 rounded-full flex items-center justify-center group-hover:scale-110 transition-transform">
        <Loader2 v-if="uploading" class="w-6 h-6 text-slate-400 animate-spin" />
        <ImagePlus v-else class="w-6 h-6 text-slate-400 group-hover:text-slate-600 transition-colors" />
      </div>
      <div class="text-center">
        <p class="text-sm font-bold text-slate-600">点击上传封面图</p>
        <p class="text-[10px] font-medium text-slate-400 mt-1 uppercase tracking-widest">支持 JPG, PNG · 最大 5MB</p>
      </div>
    </button>
  </div>
</template>
