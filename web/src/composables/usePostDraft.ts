import { reactive, computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

// Song：说明
export interface PostFormData {
    title: string
    content: string
    sectionId: number
    tags: string[]
    coverImage: string
    isAnonymous: number
}

// Song：说明
const getInitialForm = (): PostFormData => ({
    title: '',
    content: '',
    sectionId: 0,
    tags: [],
    coverImage: '',
    isAnonymous: 0
})

export function usePostDraft() {
    const form = reactive<PostFormData>(getInitialForm())

    // Song：说明
    const lastSavedState = ref<string>(JSON.stringify(getInitialForm()))

    // Song：说明
    const isDirty = computed(() => {
        return JSON.stringify(form) !== lastSavedState.value
    })

    // Song：说明
    const hasContent = computed(() => {
        return form.title.trim() !== '' || form.content.trim() !== ''
    })

    const saveDraft = (silent = false) => {
        if (!form.title.trim() && !silent) {
            ElMessage.warning('请输入标题才能保存草稿')
            return false
        }

        try {
            const draft = {
                ...form,
                savedAt: new Date().toISOString()
            }
            localStorage.setItem('post_draft', JSON.stringify(draft))
            lastSavedState.value = JSON.stringify(form)

            if (!silent) {
                ElMessage.success('草稿已保存')
            }
            return true
        } catch (error) {
            if (!silent) {
                ElMessage.error('保存草稿失败')
            }
            return false
        }
    }

    const loadDraft = () => {
        try {
            const draftStr = localStorage.getItem('post_draft')
            if (draftStr) {
                const parsed = JSON.parse(draftStr)
                Object.assign(form, {
                    title: parsed.title || '',
                    content: parsed.content || '',
                    sectionId: parsed.sectionId || 0,
                    tags: parsed.tags || [],
                    coverImage: parsed.coverImage || '',
                    isAnonymous: parsed.isAnonymous || 0
                })
                lastSavedState.value = JSON.stringify(form)
                return true
            }
            return false
        } catch (error) {
            return false
        }
    }

    const loadDraftAction = () => {
        if (loadDraft()) {
            ElMessage.success('草稿已加载')
        } else {
            ElMessage.info('暂无本地草稿')
        }
    }

    const clearDraft = () => {
        Object.assign(form, getInitialForm())
        localStorage.removeItem('post_draft')
        lastSavedState.value = JSON.stringify(getInitialForm())
    }

    // Song：说明
    const resetForm = () => {
        Object.assign(form, getInitialForm())
        lastSavedState.value = JSON.stringify(getInitialForm())
    }

    return {
        form,
        isDirty,
        hasContent,
        saveDraft,
        loadDraft,
        loadDraftAction,
        clearDraft,
        resetForm
    }
}
