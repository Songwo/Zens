"use client"

import { useState, useEffect } from "react"
import api from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { MessageCircle, Heart, Trash2, Reply, Loader2 } from "lucide-react"
import toast from "react-hot-toast"
import { motion, AnimatePresence } from "framer-motion"
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

    // 当前登录用户ID (简单从token解析或localStorage获取，这里假设存储了userId)
    // 实际项目中应从全局状态获取
    const currentUserId = typeof window !== 'undefined' ? localStorage.getItem("user_id") : null
    const isLoggedIn = !!currentUserId

    // 如果未登录，强制匿名
    useEffect(() => {
        if (!isLoggedIn) {
            setIsAnonymous(true)
        }
    }, [isLoggedIn])

    useEffect(() => {
        loadComments()
    }, [postId])

    const loadComments = async () => {
        try {
            setLoading(true)
            const res: any = await api.get(`/sys-comment/post/${postId}`)
            if (res.code === 2000) {
                setComments(res.data || [])
            }
        } catch (error) {
            console.error("加载评论失败", error)
        } finally {
            setLoading(false)
        }
    }

    const handleSubmit = async () => {
        if (!content.trim()) {
            toast.error("请输入评论内容")
            return
        }

        try {
            setSubmitting(true)
            const payload = {
                postId,
                content,
                parentId: replyTo ? replyTo.id : "0",
                replyUserId: replyTo ? replyTo.userId : undefined,
                isAnonymous: isAnonymous ? 1 : 0
            }

            // 使用新的 create 接口，支持匿名
            const res: any = await api.post("/sys-comment/create", payload)
            if (res.code === 2000) {
                toast.success("评论成功")
                setContent("")
                setReplyTo(null)
                loadComments() // 重新加载列表
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
                // 简单起见重新加载，优化方案是本地更新状态
                loadComments()
            }
        } catch (error) {
            // ignore
        }
    }

    const handleDelete = async (commentId: string) => {
        if (!confirm("确定删除这条评论吗？")) return

        try {
            const res: any = await api.delete(`/sys-comment/${commentId}`)
            if (res.code === 2000) {
                toast.success("删除成功")
                loadComments()
            }
        } catch (error) {
            toast.error("删除失败")
        }
    }

    // 递归渲染评论项
    const renderComment = (item: Comment, depth = 0) => {
        const isMe = currentUserId === item.userId // 需要后端配合返回当前用户是否是作者，或者前端存userId

        return (
            <motion.div
                key={item.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className={`flex gap-3 mb-4 ${depth > 0 ? "ml-8 md:ml-12 border-l-2 border-gray-100 pl-4" : ""}`}
            >
                <div className={`shrink-0 w-8 h-8 md:w-10 md:h-10 rounded-full flex items-center justify-center font-bold text-white text-sm ${item.isAnonymous ? "bg-gray-400" : "bg-primary"}`}>
                    {item.isAnonymous ? "A" : "U"}
                </div>

                <div className="flex-1 space-y-1">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                            <span className="font-semibold text-sm text-gray-900 dark:text-gray-200">
                                {item.isAnonymous ? "匿名同学" : `用户 ${item.userId.substring(0, 6)}`}
                            </span>
                            {item.replyUserId && (
                                <span className="text-xs text-gray-400 flex items-center">
                                    <Reply className="w-3 h-3 mx-1" />
                                    {/* 这里最好显示被回复人昵称，目前只有ID暂且显示ID或隐式处理 */}
                                </span>
                            )}
                            <span className="text-xs text-gray-400">
                                {formatDistanceToNow(new Date(item.createTime), { addSuffix: true, locale: zhCN })}
                            </span>
                        </div>
                    </div>

                    <div className="text-gray-700 dark:text-gray-300 text-sm leading-relaxed whitespace-pre-wrap">
                        {item.content}
                    </div>

                    <div className="flex items-center gap-4 pt-1">
                        <button
                            onClick={() => handleLike(item.id)}
                            className="flex items-center gap-1 text-xs text-gray-500 hover:text-pink-500 transition-colors"
                        >
                            <Heart className={`w-3.5 h-3.5 ${item.likeCount > 0 ? "fill-pink-500 text-pink-500" : ""}`} />
                            {item.likeCount > 0 ? item.likeCount : "赞"}
                        </button>

                        <button
                            onClick={() => setReplyTo({ id: item.id, userId: item.userId, isAnonymous: item.isAnonymous })}
                            className="flex items-center gap-1 text-xs text-gray-500 hover:text-blue-500 transition-colors"
                        >
                            <Reply className="w-3.5 h-3.5" />
                            回复
                        </button>

                        {/* 模拟删除权限：实际应由后端控制或前端判断ID */}
                        <button
                            onClick={() => handleDelete(item.id)}
                            className="flex items-center gap-1 text-xs text-gray-400 hover:text-red-500 transition-colors opacity-0 hover:opacity-100"
                        >
                            <Trash2 className="w-3.5 h-3.5" />
                            删除
                        </button>
                    </div>

                    {/* 递归渲染子评论 */}
                    {item.children && item.children.map(child => renderComment(child, depth + 1))}
                </div>
            </motion.div>
        )
    }

    return (
        <div className="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-100 dark:border-gray-800 p-6 md:p-8 mt-6">
            <h3 className="text-xl font-bold mb-6 flex items-center gap-2">
                <MessageCircle className="w-5 h-5 text-primary" />
                评论 ({comments.reduce((acc, curr) => acc + 1 + (curr.children?.length || 0), 0)})
            </h3>

            {/* 输入框 */}
            <div className="mb-8 space-y-3">
                {replyTo && (
                    <div className="flex items-center justify-between bg-blue-50 text-blue-600 px-3 py-2 rounded-lg text-sm">
                        <span>回复 @{replyTo.isAnonymous ? "匿名同学" : "用户"} 的评论</span>
                        <button onClick={() => setReplyTo(null)} className="hover:underline">取消</button>
                    </div>
                )}

                <Textarea
                    placeholder={replyTo ? "写下你的回复..." : "发一条友善的评论..."}
                    value={content}
                    onChange={(e: any) => setContent(e.target.value)}
                    className="min-h-[100px] bg-gray-50 border-gray-200 resize-none focus:bg-white transition-colors"
                />

                <div className="flex justify-between items-center">
                    <label className="flex items-center space-x-2 text-sm text-gray-600 cursor-pointer select-none">
                        <input
                            type="checkbox"
                            checked={isAnonymous}
                            onChange={(e) => setIsAnonymous(e.target.checked)}
                            disabled={!isLoggedIn} // 未登录强制匿名，不可取消
                            className="rounded border-gray-300 text-primary focus:ring-primary/20 disabled:opacity-50"
                        />
                        <span className={!isLoggedIn ? "text-gray-400" : ""}>
                            {isLoggedIn ? "匿名发表" : "未登录 (将匿名发表)"}
                        </span>
                    </label>

                    <Button onClick={handleSubmit} disabled={submitting || !content.trim()}>
                        {submitting && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                        {replyTo ? "回复" : "发表评论"}
                    </Button>
                </div>
            </div>

            {/* 列表 */}
            <div className="space-y-6">
                {loading ? (
                    <div className="flex justify-center py-8">
                        <Loader2 className="w-8 h-8 animate-spin text-gray-300" />
                    </div>
                ) : comments.length > 0 ? (
                    comments.map(c => renderComment(c))
                ) : (
                    <div className="text-center py-8 text-gray-400">
                        暂无评论，来抢沙发吧~
                    </div>
                )}
            </div>
        </div>
    )
}
