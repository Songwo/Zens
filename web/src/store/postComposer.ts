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
        coverImage: '',
        status: 1,
        auditStatus: '',
        postType: 'NORMAL',
        commentDeadline: '',
        commentOncePerUser: true
    })

    const open = (ctx?: { editId?: string, title?: string, content?: string, sectionId?: number, tags?: string, coverImage?: string, status?: number, auditStatus?: string, postType?: string, commentDeadline?: string | null, commentOncePerUser?: boolean | number }) => {
        console.log('Pinia: Opening composer')
        if (ctx) {
            context.editId = ctx.editId || ''
            context.title = ctx.title || ''
            context.content = ctx.content || ''
            context.sectionId = ctx.sectionId || 0
            context.tags = ctx.tags || ''
            context.coverImage = ctx.coverImage || ''
            context.status = ctx.status ?? 1
            context.auditStatus = ctx.auditStatus || ''
            context.postType = ctx.postType === 'LOTTERY' ? 'LOTTERY' : 'NORMAL'
            context.commentDeadline = ctx.commentDeadline || ''
            context.commentOncePerUser = ctx.commentOncePerUser !== false && ctx.commentOncePerUser !== 0
        } else {
            // Song：说明
            context.editId = ''
            context.title = ''
            context.content = ''
            context.sectionId = 0
            context.tags = ''
            context.coverImage = ''
            context.status = 1
            context.auditStatus = ''
            context.postType = 'NORMAL'
            context.commentDeadline = ''
            context.commentOncePerUser = true
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
