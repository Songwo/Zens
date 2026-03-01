import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'

export const usePostComposerStore = defineStore('postComposer', () => {
    const isOpen = ref(false)
    const context = reactive({
        editId: '',
        title: '',
        content: '',
        sectionId: 0,
        tags: '',
        coverImage: ''
    })

    const open = (ctx?: { editId?: string, title?: string, content?: string, sectionId?: number, tags?: string, coverImage?: string }) => {
        console.log('Pinia: Opening composer')
        if (ctx) {
            context.editId = ctx.editId || ''
            context.title = ctx.title || ''
            context.content = ctx.content || ''
            context.sectionId = ctx.sectionId || 0
            context.tags = ctx.tags || ''
            context.coverImage = ctx.coverImage || ''
        } else {
            // Song：说明
            context.editId = ''
            context.title = ''
            context.content = ''
            context.sectionId = 0
            context.tags = ''
            context.coverImage = ''
        }
        isOpen.value = true
        document.body.style.overflow = 'hidden'
    }

    const close = () => {
        isOpen.value = false
        document.body.style.overflow = ''
    }

    const toggle = () => {
        isOpen.value ? close() : open()
    }

    return {
        isOpen,
        context,
        open,
        close,
        toggle
    }
})
