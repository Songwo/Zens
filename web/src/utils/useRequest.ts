/**
 * useRequest: 防重复提交 + 请求去重 composable
 *
 * 用法:
 *   const { loading, run } = useRequest(apiFn)
 *   <el-button :loading="loading" @click="run(args)">提交</el-button>
 *
 * 特性:
 *   - loading 期间重复调用直接忽略（防重复点击）
 *   - 可选 debounce 防抖
 *   - 可选 onError 统一错误处理
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

interface UseRequestOptions<T> {
  onSuccess?: (data: T) => void
  onError?: (e: any) => void
  showError?: boolean        // 默认 true，自动 ElMessage.error
  debounceMs?: number        // 防抖毫秒数，默认 0
}

export function useRequest<Args extends any[], T>(
  fn: (...args: Args) => Promise<T>,
  options: UseRequestOptions<T> = {}
) {
  const loading = ref(false)
  const { showError = true, debounceMs = 0, onSuccess, onError } = options
  let debounceTimer: ReturnType<typeof setTimeout> | null = null

  const run = (...args: Args): Promise<T | undefined> => {
    if (loading.value) return Promise.resolve(undefined)

    if (debounceMs > 0) {
      if (debounceTimer) clearTimeout(debounceTimer)
      return new Promise((resolve) => {
        debounceTimer = setTimeout(() => {
          debounceTimer = null
          execute(args).then(resolve)
        }, debounceMs)
      })
    }

    return execute(args)
  }

  const execute = async (args: Args): Promise<T | undefined> => {
    loading.value = true
    try {
      const result = await fn(...args)
      onSuccess?.(result)
      return result
    } catch (e: any) {
      if (showError) {
        ElMessage.error(e?.message || '操作失败，请稍后重试')
      }
      onError?.(e)
      return undefined
    } finally {
      loading.value = false
    }
  }

  return { loading, run }
}

/**
 * 在模板里直接用于一次性操作的简单防重 wrapper
 * 返回包装后的函数，执行期间重复调用无效
 */
export function withLoading<Args extends any[], T>(
  fn: (...args: Args) => Promise<T>
): [(...args: Args) => Promise<T | undefined>, ReturnType<typeof ref<boolean>>] {
  const loading = ref(false)
  const wrapped = async (...args: Args): Promise<T | undefined> => {
    if (loading.value) return undefined
    loading.value = true
    try {
      return await fn(...args)
    } finally {
      loading.value = false
    }
  }
  return [wrapped, loading]
}
