"use client"

import { useState, useEffect } from "react"
import api from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Heart, Trash2, Reply, Loader2 } from "lucide-react"
import toast from "react-hot-toast"
import { motion } from "framer-motion"
import { formatDistanceToNow } from "date-fns"
import { zhCN } from "date-fns/locale"

interface Comment {
    id: string
    userId: string
    content: string
    parentId: string
    replyUserId?: string
    isAnonymous: number
    likeCount: number
    createTime: string
    children?: Comment[]
}

interface CommentSectionProps {
    postId: string
}

export default function CommentSection({ postId }: CommentSectionProps) {
    const [comments, setComments] = useState<Comment[]>([])
    const [loading, setLoading] = useState(true)
    const [content, setContent] = useState("")
    const [submitting, setSubmitting] = useState(false)
    const [replyTo, setReplyTo] = useState<{ id: string, userId: string, isAnonymous: number } | null>(null)
    const [isAnonymous, setIsAnonymous] = useState(false)

    // Pagination
    const [page, setPage] = useState(1)
    const [hasMore, setHasMore] = useState(false)
    const [loadingMore, setLoadingMore] = useState(false)

    const currentUserId = typeof window !== 'undefined' ? localStorage.getItem("user_id") : null
    const isLoggedIn = !!currentUserId

    useEffect(() => {
        if (!isLoggedIn) {
            setIsAnonymous(true)
        }
    }, [isLoggedIn])

    useEffect(() => {
        loadComments(1, true)
    }, [postId])

    const loadComments = async (pageNum: number, isReset = false) => {
        try {
            if (isReset) {
                setLoading(true)
            } else {
                setLoadingMore(true)
            }

            const res: any = await api.get(`/sys-comment/post/${postId}?page=${pageNum}&size=10`)

            if (res.code === 2000) {
                const records = res.data.records || []
                const current = res.data.current || 1
                const pages = res.data.pages || 1

                if (isReset) {
                    setComments(records)
                } else {
                    setComments(prev => [...prev, ...records])
                }
                setPage(current)
                setHasMore(current < pages)
            }
        } catch (error) {
            console.error("加载评论失败", error)
        } finally {
            setLoading(false)
            setLoadingMore(false)
        }
    }

    const handleSubmit = async () => {
        if (!content.trim()) return

        try {
            setSubmitting(true)
            const payload = {
                postId,
                content,
                parentId: replyTo ? replyTo.id : "0",
                replyUserId: replyTo ? replyTo.userId : undefined,
                isAnonymous: isAnonymous ? 1 : 0
            }

            const res: any = await api.post("/sys-comment/create", payload)
            if (res.code === 2000) {
                toast.success("回复成功")
                setContent("")
                setReplyTo(null)
                loadComments(1, true)
            } else {
                toast.error(res.message || "评论失败")
            }
        } catch (error) {
            toast.error("网络异常")
        } finally {
            setSubmitting(false)
        }
    }

    const handleLike = async (commentId: string) => {
        try {
            const res: any = await api.post(`/sys-comment/${commentId}/like`)
            if (res.code === 2000) {
                setComments(prev => updateCommentLike(prev, commentId))
            }
        } catch (error) { }
    }

    const updateCommentLike = (list: Comment[], targetId: string): Comment[] => {
        return list.map(c => {
            if (c.id === targetId) return { ...c, likeCount: c.likeCount + 1 }
            if (c.children) return { ...c, children: updateCommentLike(c.children, targetId) }
            return c
        })
    }

    const handleDelete = async (commentId: string) => {
        if (!confirm("确定删除？")) return
        try {
            const res: any = await api.delete(`/sys-comment/${commentId}`)
            if (res.code === 2000) {
                loadComments(1, true)
            }
        } catch (error) { }
    }

    const renderComment = (item: Comment, depth = 0) => {
        return (
            <div key={item.id} className={`group ${depth > 0 ? "mt-4 ml-10 border-l-2 border-gray-100 dark:border-gray-800 pl-4" : "border-b border-gray-100 dark:border-gray-800 py-6"}`}>
                <div className="flex gap-3">
                    <div className="flex-shrink-0 w-8 h-8 rounded bg-gray-200 dark:bg-gray-700 flex items-center justify-center text-xs font-bold text-gray-500 dark:text-gray-400">
                        {item.isAnonymous ? "A" : "U"}
                    </div>
                    <div className="flex-1">
                        <div className="flex items-center justify-between mb-1">
                            <div className="flex items-center gap-2">
                                <span className="font-bold text-sm text-gray-900 dark:text-gray-200">
                                    {item.isAnonymous ? "匿名用户" : `用户 ${item.userId.substring(0, 6)}`}
                                </span>
                                {item.replyUserId && <span className="text-xs text-gray-400">回复</span>}
                                <span className="text-xs text-gray-400">
                                    {formatDate(item.createTime)}
                                </span>
                            </div>
                            <div className="text-xs text-gray-400">
                                #{item.id.substring(0, 4)}
                            </div>
                        </div>

                        <div className="text-sm text-gray-800 dark:text-gray-300 leading-relaxed mb-2">
                            {item.content}
                        </div>

                        <div className="flex items-center gap-4 text-xs">
                            <button onClick={() => handleLike(item.id)} className="flex items-center gap-1 text-gray-500 hover:text-pink-500">
                                <Heart className="w-3 h-3" /> {item.likeCount || 0}
                            </button>
                            <button onClick={() => setReplyTo({ id: item.id, userId: item.userId, isAnonymous: item.isAnonymous })} className="flex items-center gap-1 text-gray-500 hover:text-blue-500">
                                <Reply className="w-3 h-3" /> 回复
                            </button>
                            {currentUserId === item.userId && (
                                <button onClick={() => handleDelete(item.id)} className="text-gray-500 hover:text-red-500 ml-auto">
                                    <Trash2 className="w-3 h-3" />
                                </button>
                            )}
                        </div>
                    </div>
                </div>

                {item.children?.map(child => renderComment(child, depth + 1))}
            </div>
        )
    }

    const formatDate = (dateStr: string) => {
        try {
            return formatDistanceToNow(new Date(dateStr), { addSuffix: true, locale: zhCN })
        } catch (e) {
            return ""
        }
    }

    return (
        <div className="bg-transparent">
            {/* Simple Editor */}
            <div className="mb-8 bg-gray-50 dark:bg-gray-800/50 p-4 rounded-lg border border-gray-100 dark:border-gray-800">
                {replyTo && (
                    <div className="flex items-center justify-between text-xs text-blue-500 mb-2">
                        <span>回复 {replyTo.isAnonymous ? "匿名" : "用户"}</span>
                        <button onClick={() => setReplyTo(null)}>取消</button>
                    </div>
                )}
                <Textarea
                    value={content}
                    onChange={(e) => setContent(e.target.value)}
                    placeholder="分享你的观点..."
                    className="bg-white dark:bg-gray-900 border-gray-200 dark:border-gray-700 min-h-[100px] mb-2"
                />
                <div className="flex justify-between items-center">
                    <label className="flex items-center gap-2 text-xs text-gray-500 cursor-pointer">
                        <input type="checkbox" checked={isAnonymous} onChange={e => setIsAnonymous(e.target.checked)} disabled={!isLoggedIn} />
                        <span>匿名回复</span>
                    </label>
                    <Button size="sm" onClick={handleSubmit} disabled={submitting || !content.trim()}>
                        {submitting ? "提交中..." : "发表回复"}
                    </Button>
                </div>
            </div>

            <div className="space-y-1">
                {loading ? (
                    <div className="py-10 text-center"><Loader2 className="w-6 h-6 animate-spin mx-auto text-gray-400" /></div>
                ) : comments.length > 0 ? (
                    <>
                        {comments.map(c => renderComment(c))}
                        {hasMore && (
                            <div className="py-4 text-center">
                                <Button variant="ghost" size="sm" onClick={() => loadComments(page + 1)} disabled={loadingMore}>
                                    {loadingMore ? "加载中..." : "加载更多评论"}
                                </Button>
                            </div>
                        )}
                    </>
                ) : (
                    <div className="py-10 text-center text-gray-400 text-sm">暂无评论，快来占楼吧</div>
                )}
            </div>
        </div>
    )
}
