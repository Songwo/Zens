"use client"

import { useState, useEffect } from "react"
import { useParams, useRouter } from "next/navigation"
import Navbar from "@/components/navbar"
import CommentSection from "@/components/comment-section"
import LeftSidebar from "@/components/left-sidebar"
import api from "@/lib/api"
import toast from "react-hot-toast"
import { Loader2, Heart, Star, Share2, MoreHorizontal, Clock, MapPin, Eye, ThumbsUp, MessageSquare } from "lucide-react"
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { motion } from "framer-motion"
import { Button } from "@/components/ui/button"

interface PostDetail {
    id: string
    title: string
    content: string
    userId: string
    categoryId: string
    categoryName?: string
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
    authorName: string
    authorAvatar?: string
}

export default function PostDetailPage() {
    const params = useParams()
    const router = useRouter()
    const id = params?.id as string

    const [post, setPost] = useState<PostDetail | null>(null)
    const [loading, setLoading] = useState(true)
    const [likeCount, setLikeCount] = useState(0)
    const [collectCount, setCollectCount] = useState(0)
    const [isLiked, setIsLiked] = useState(false)
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
                setIsLiked(!isLiked)
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

    const formatDate = (dateStr: string) => {
        const date = new Date(dateStr)
        return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`
    }

    if (loading) {
        return (
            <div className="min-h-screen bg-white dark:bg-gray-900">
                <Navbar />
                <LeftSidebar />
                <div className="pl-0 xl:pl-64 pt-20 flex justify-center items-center h-[calc(100vh-80px)]">
                    <Loader2 className="w-8 h-8 animate-spin text-primary" />
                </div>
            </div>
        )
    }

    if (!post) {
        return (
            <div className="min-h-screen bg-white dark:bg-gray-900">
                <Navbar />
                <LeftSidebar />
                <div className="pl-0 xl:pl-64 pt-32 flex flex-col items-center justify-center space-y-4">
                    <h1 className="text-2xl font-bold text-gray-800 dark:text-gray-200">帖子不存在或已被删除</h1>
                    <Button onClick={() => router.push('/')}>返回首页</Button>
                </div>
            </div>
        )
    }

    const tags = post.tags ? post.tags.split(/[#\s]+/).filter(t => t) : []

    return (
        <div className="min-h-screen bg-white dark:bg-gray-900">
            <Navbar />

            {/* Left Sidebar */}
            <LeftSidebar />

            <main className="pl-0 xl:pl-64 pt-16">
                <div className="max-w-[1000px] mx-auto px-4 sm:px-6 lg:px-8 py-8">

                    {/* Header Section */}
                    <div className="mb-8 border-b border-gray-100 dark:border-gray-800 pb-8">
                        <h1 className="text-3xl font-extrabold text-gray-900 dark:text-gray-100 mb-4 leading-tight">
                            {post.title}
                        </h1>

                        <div className="flex flex-wrap items-center gap-4 text-sm">
                            {/* Category Label */}
                            {post.categoryName && (
                                <span className="bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 px-2 py-1 rounded font-medium flex items-center gap-1">
                                    <span className="w-2 h-2 rounded-full bg-blue-500"></span>
                                    {post.categoryName}
                                </span>
                            )}

                            {/* Tags */}
                            {tags.length > 0 && (
                                <div className="flex gap-2">
                                    {tags.map((tag, i) => (
                                        <span key={i} className="text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-gray-800/50 px-2 py-1 rounded">
                                            {tag}
                                        </span>
                                    ))}
                                </div>
                            )}

                            <span className="ml-auto text-gray-400 text-xs">
                                {formatDate(post.createTime)}
                            </span>
                        </div>
                    </div>


                    {/* Main Post Content with Author Sidebar (Simulated Layout) */}
                    <div className="flex gap-6">

                        {/* Author Info (Desktop view - mimicing forum style left avatar) */}
                        <div className="hidden md:block w-16 flex-shrink-0">
                            <div className="sticky top-24">
                                <div className="w-12 h-12 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden mb-2 border border-gray-200 dark:border-gray-700">
                                    {post.authorAvatar ? (
                                        <img src={post.authorAvatar} alt={post.authorName} className="w-full h-full object-cover" />
                                    ) : (
                                        <div className="w-full h-full flex items-center justify-center text-gray-500 font-bold">
                                            {post.authorName.charAt(0).toUpperCase()}
                                        </div>
                                    )}
                                </div>
                                <div className="text-xs text-center font-bold text-gray-700 dark:text-gray-300 truncate w-16">
                                    {post.authorName}
                                </div>
                                <div className="mt-4 flex flex-col items-center gap-2 text-gray-400">
                                    {/* Simple stats or badges could go here */}
                                </div>
                            </div>
                        </div>

                        {/* Content Body */}
                        <div className="flex-1 min-w-0">
                            {/* Mobile Author Header */}
                            <div className="md:hidden flex items-center gap-3 mb-6">
                                <div className="w-10 h-10 rounded-full bg-gray-100 dark:bg-gray-800 overflow-hidden">
                                    {post.authorAvatar ? (
                                        <img src={post.authorAvatar} alt={post.authorName} className="w-full h-full object-cover" />
                                    ) : (
                                        <div className="w-full h-full flex items-center justify-center text-gray-500 font-bold">
                                            {post.authorName.charAt(0).toUpperCase()}
                                        </div>
                                    )}
                                </div>
                                <div>
                                    <div className="font-bold text-gray-900 dark:text-gray-100">{post.authorName}</div>
                                    <div className="text-xs text-gray-500">{formatDate(post.createTime)}</div>
                                </div>
                            </div>

                            <article className="prose prose-lg dark:prose-invert max-w-none prose-p:leading-relaxed prose-headings:font-bold prose-a:text-blue-600 dark:prose-a:text-blue-400 prose-img:rounded-lg">
                                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                    {post.content}
                                </ReactMarkdown>
                            </article>

                            {/* Interaction Bar */}
                            <div className="flex items-center gap-4 mt-12 py-4 border-t border-b border-gray-100 dark:border-gray-800">
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={handleLike}
                                    className={`flex gap-2 ${isLiked ? 'text-pink-600 bg-pink-50 dark:bg-pink-900/20' : 'text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800'}`}
                                >
                                    <Heart className={`w-4 h-4 ${isLiked ? 'fill-current' : ''}`} />
                                    <span>{likeCount} 点赞</span>
                                </Button>

                                <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={handleCollect}
                                    className={`flex gap-2 ${isCollected ? 'text-yellow-600 bg-yellow-50 dark:bg-yellow-900/20' : 'text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-800'}`}
                                >
                                    <Star className={`w-4 h-4 ${isCollected ? 'fill-current' : ''}`} />
                                    <span>{collectCount} 收藏</span>
                                </Button>

                                <div className="ml-auto flex items-center gap-4 text-sm text-gray-400">
                                    <span className="flex items-center gap-1">
                                        <Eye className="w-4 h-4" /> {post.viewCount} 浏览
                                    </span>
                                </div>
                            </div>

                            {/* Comments Section */}
                            <div className="mt-8">
                                <h3 className="text-lg font-bold text-gray-900 dark:text-gray-100 mb-6 border-l-4 border-blue-500 pl-3">
                                    {post.commentCount} 条回复
                                </h3>
                                <CommentSection postId={id} />
                            </div>

                        </div>
                    </div>
                </div>
            </main>
        </div>
    )
}
