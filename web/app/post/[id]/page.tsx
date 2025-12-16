"use client"

import { useState, useEffect } from "react"
import { useParams, useRouter } from "next/navigation"
import Navbar from "@/components/navbar"
import CommentSection from "@/components/comment-section"
import api from "@/lib/api"
import toast from "react-hot-toast"
import { Loader2, Heart, Star, Share2, MessageCircle, MoreHorizontal, User, Clock, MapPin, Eye } from "lucide-react"
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { motion } from "framer-motion"
import { Button } from "@/components/ui/button"
import Link from "next/link"

interface PostDetail {
    id: string
    title: string
    content: string
    userId: string
    categoryId: string
    categoryName?: string    // 分类名称
    tags: string
    isAnonymous: number
    locationName?: string
    viewCount: number
    likeCount: number
    collectCount: number
    commentCount: number
    createTime: string
    heatScore: number
    isLiked?: boolean
    isCollected?: boolean
    // 新增作者信息字段
    authorName: string       // 后端已填充
    authorAvatar?: string    // 头像URL
    sentimentScore?: number
    sentimentLabel?: string  // positive/neutral/negative
    trendLevel?: string      // hot/trending/normal
}

// 简单生成颜色
const getUserColor = (id: string) => {
    const colors = ["bg-red-500", "bg-orange-500", "bg-amber-500", "bg-green-500", "bg-emerald-500", "bg-teal-500", "bg-cyan-500", "bg-blue-500", "bg-indigo-500", "bg-violet-500", "bg-purple-500", "bg-fuchsia-500", "bg-pink-500", "bg-rose-500"]
    let hash = 0
    for (let i = 0; i < id.length; i++) {
        hash = id.charCodeAt(i) + ((hash << 5) - hash)
    }
    return colors[Math.abs(hash) % colors.length]
}

export default function PostDetailPage() {
    const params = useParams()
    const router = useRouter()
    const id = params?.id as string

    const [post, setPost] = useState<PostDetail | null>(null)
    const [loading, setLoading] = useState(true)
    const [likeCount, setLikeCount] = useState(0)
    const [collectCount, setCollectCount] = useState(0)
    const [isLiked, setIsLiked] = useState(false) // 暂无后端状态，仅前端模拟交互
    const [isCollected, setIsCollected] = useState(false)

    useEffect(() => {
        if (id) {
            loadPost()
        }
    }, [id])

    const loadPost = async () => {
        try {
            setLoading(true)
            const res: any = await api.get(`/sys-post/${id}`)
            if (res.code === 2000) {
                setPost(res.data)
                setLikeCount(res.data.likeCount)
                setCollectCount(res.data.collectCount)
                // 初始化点赞/收藏状态
                setIsLiked(!!res.data.isLiked)
                setIsCollected(!!res.data.isCollected)
            } else {
                toast.error(res.message || "加载失败")
            }
        } catch (error) {
            console.error(error)
            toast.error("网络异常")
        } finally {
            setLoading(false)
        }
    }

    const handleLike = async () => {
        try {
            const res: any = await api.post(`/sys-post/${id}/like`)
            if (res.code === 2000) {
                setIsLiked(!isLiked) // 乐观更新
                setLikeCount(prev => isLiked ? prev - 1 : prev + 1)
                toast.success(isLiked ? "取消点赞" : "点赞成功")
            }
        } catch (error) {
            toast.error("操作失败")
        }
    }

    const handleCollect = async () => {
        try {
            const res: any = await api.post(`/sys-post/${id}/collect`)
            if (res.code === 2000) {
                setIsCollected(!isCollected)
                setCollectCount(prev => isCollected ? prev - 1 : prev + 1)
                toast.success(isCollected ? "取消收藏" : "收藏成功")
            }
        } catch (error) {
            toast.error("操作失败")
        }
    }

    if (loading) {
        return (
            <div className="min-h-screen bg-white dark:bg-gray-900">
                <Navbar />
                <div className="flex justify-center items-center h-screen pt-16">
                    <Loader2 className="w-8 h-8 animate-spin text-primary" />
                </div>
            </div>
        )
    }

    if (!post) {
        return (
            <div className="min-h-screen bg-white dark:bg-gray-900">
                <Navbar />
                <div className="flex flex-col justify-center items-center h-screen pt-16 space-y-4">
                    <h1 className="text-2xl font-bold text-gray-800">帖子不存在或已被删除</h1>
                    <Button onClick={() => router.push('/')}>返回首页</Button>
                </div>
            </div>
        )
    }

    const tags = post.tags ? post.tags.split(/[#\s]+/).filter(t => t) : []
    const userColor = getUserColor(post.userId)

    return (
        <div className="min-h-screen bg-gray-50/50 dark:bg-gray-900">
            <Navbar />

            <main className="container mx-auto px-4 pt-24 pb-12 max-w-4xl">
                <motion.div
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-100 dark:border-gray-800 overflow-hidden"
                >
                    {/* 头部信息 */}
                    <div className="p-8 pb-4">
                        {/* 标签 */}
                        {tags.length > 0 && (
                            <div className="flex flex-wrap gap-2 mb-4">
                                {tags.map((tag, i) => (
                                    <span key={i} className="text-sm font-medium text-blue-600 bg-blue-50 px-2.5 py-0.5 rounded-full">
                                        #{tag}
                                    </span>
                                ))}
                            </div>
                        )}

                        <h1 className="text-3xl md:text-4xl font-bold text-gray-900 dark:text-gray-100 mb-6 leading-tight">
                            {post.title}
                        </h1>

                        <div className="flex items-center justify-between border-b border-gray-100 dark:border-gray-800 pb-6">
                            <div className="flex items-center space-x-3">
                                {/* 作者头像 */}
                                <div className={`w-10 h-10 rounded-full ${post.authorAvatar ? '' : userColor} flex items-center justify-center text-white font-bold text-lg overflow-hidden`}>
                                    {post.authorAvatar ? (
                                        <img src={post.authorAvatar} alt="avatar" className="w-full h-full object-cover" />
                                    ) : (
                                        post.authorName.charAt(0).toUpperCase()
                                    )}
                                </div>
                                <div>
                                    <div className="font-semibold text-gray-900 dark:text-gray-200">
                                        {post.authorName}
                                    </div>
                                    <div className="text-xs text-gray-500 flex items-center space-x-2 mt-0.5">
                                        <span className="flex items-center">
                                            <Clock className="w-3 h-3 mr-1" />
                                            {new Date(post.createTime).toLocaleString()}
                                        </span>
                                        {post.locationName && (
                                            <span className="flex items-center">
                                                <MapPin className="w-3 h-3 mr-1" />
                                                {post.locationName}
                                            </span>
                                        )}
                                        <span className="flex items-center">
                                            <Eye className="w-3 h-3 mr-1" />
                                            {post.viewCount} 浏览
                                        </span>
                                    </div>
                                </div>
                            </div>

                            <Button variant="ghost" size="icon">
                                <MoreHorizontal className="w-5 h-5 text-gray-500" />
                            </Button>
                        </div>
                    </div>

                    {/* 正文内容 */}
                    <div className="px-8 py-2">
                        <article className="prose prose-lg dark:prose-invert max-w-none prose-headings:font-bold prose-a:text-primary hover:prose-a:underline prose-img:rounded-xl prose-img:shadow-md">
                            <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                {post.content}
                            </ReactMarkdown>
                        </article>
                    </div>

                    {/* 底部互动栏 */}
                    <div className="px-8 py-8 mt-4 border-t border-gray-100 dark:border-gray-800 bg-gray-50/30">
                        <div className="flex justify-center space-x-8">
                            <motion.button
                                whileTap={{ scale: 0.9 }}
                                onClick={handleLike}
                                className={`flex flex-col items-center space-y-1 group ${isLiked ? "text-pink-500" : "text-gray-500"}`}
                            >
                                <div className={`p-3 rounded-full transition-colors ${isLiked ? "bg-pink-100 text-pink-500" : "bg-white group-hover:bg-gray-200"}`}>
                                    <Heart className={`w-6 h-6 ${isLiked ? "fill-current" : ""}`} />
                                </div>
                                <span className="text-sm font-medium">{likeCount}</span>
                            </motion.button>

                            <motion.button
                                whileTap={{ scale: 0.9 }}
                                onClick={handleCollect}
                                className={`flex flex-col items-center space-y-1 group ${isCollected ? "text-yellow-500" : "text-gray-500"}`}
                            >
                                <div className={`p-3 rounded-full transition-colors ${isCollected ? "bg-yellow-100 text-yellow-500" : "bg-white group-hover:bg-gray-200"}`}>
                                    <Star className={`w-6 h-6 ${isCollected ? "fill-current" : ""}`} />
                                </div>
                                <span className="text-sm font-medium">{collectCount}</span>
                            </motion.button>

                            <motion.button
                                whileTap={{ scale: 0.9 }}
                                className="flex flex-col items-center space-y-1 group text-gray-500"
                            >
                                <div className="p-3 rounded-full bg-white group-hover:bg-gray-200 transition-colors">
                                    <Share2 className="w-6 h-6" />
                                </div>
                                <span className="text-sm font-medium">分享</span>
                            </motion.button>
                        </div>
                    </div>
                </motion.div>

                {/* 评论区 */}
                <CommentSection postId={id} />

            </main>
        </div>
    )
}
