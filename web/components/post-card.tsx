"use client"

import { motion } from "framer-motion"
import { Eye, MessageSquare, ThumbsUp } from "lucide-react"
import Link from "next/link"

// Post Interface
interface Post {
    id: string
    title: string
    content: string
    userId: string
    authorName: string
    authorAvatar?: string
    isAnonymous: number
    createTime: string
    viewCount: number
    likeCount: number
    commentCount: number
    collectCount?: number
    tags?: string
    categoryId?: string
    categoryName?: string
    heatScore?: number
    sentimentScore?: number
    sentimentLabel?: string // positive/neutral/negative
    trendLevel?: string     // hot/trending/normal
}

const formatDate = (dateStr: string) => {
    const date = new Date(dateStr)
    // Return relative time or short date
    const now = new Date()
    const diff = now.getTime() - date.getTime()

    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
    return date.toLocaleDateString()
}

export default function PostCard({ post }: { post: Post }) {
    const tags = post.tags ? post.tags.split(/[#\s]+/).filter(t => t) : []

    return (
        <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="group block"
        >
            <Link href={`/post/${post.id}`}>
                <div className="flex items-center gap-4 py-4 px-4 bg-white dark:bg-gray-900 border-b border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">

                    {/* Author Avatar (Left) */}
                    <div className="flex-shrink-0">
                        <div className="w-10 h-10 rounded-full bg-gray-100 dark:bg-gray-800 flex items-center justify-center overflow-hidden border border-gray-200 dark:border-gray-700">
                            {post.authorAvatar ? (
                                // eslint-disable-next-line @next/next/no-img-element
                                <img src={post.authorAvatar} alt={post.authorName} className="w-full h-full object-cover" />
                            ) : (
                                <span className="text-sm font-bold text-gray-500">
                                    {(post.authorName || "U").charAt(0).toUpperCase()}
                                </span>
                            )}
                        </div>
                    </div>

                    {/* Main Content */}
                    <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                            <h3 className="text-[17px] font-medium text-gray-900 dark:text-gray-100 truncate group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                                {post.title}
                            </h3>
                            {post.trendLevel === 'hot' && (
                                <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400">
                                    HOT
                                </span>
                            )}
                            {post.categoryName && (
                                <span className="flex-shrink-0 px-2 py-0.5 rounded bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400 text-xs text-nowrap">
                                    {post.categoryName}
                                </span>
                            )}
                        </div>

                        <div className="flex items-center gap-x-4 text-xs text-gray-500 dark:text-gray-400">
                            {/* Tags */}
                            {tags.length > 0 && (
                                <div className="flex gap-2">
                                    {tags.slice(0, 3).map((tag, i) => (
                                        <span key={i} className="px-1.5 py-0.5 rounded bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400">
                                            {tag}
                                        </span>
                                    ))}
                                </div>
                            )}
                            {/* <span className="hidden sm:inline">{post.authorName}</span> */}
                            <span className="flex items-center gap-1">
                                <ClockIcon className="w-3 h-3" />
                                {formatDate(post.createTime)}
                            </span>
                        </div>
                    </div>

                    {/* Stats (Right) */}
                    <div className="flex items-center gap-6 text-sm text-gray-500 dark:text-gray-400 flex-shrink-0">
                        <div className="flex flex-col items-center min-w-[3rem]">
                            <span className="font-semibold text-gray-900 dark:text-gray-200">{post.viewCount}</span>
                            <span className="text-xs text-gray-400">浏览</span>
                        </div>
                        <div className="flex flex-col items-center min-w-[3rem]">
                            <span className="font-semibold text-gray-900 dark:text-gray-200">{post.commentCount}</span>
                            <span className="text-xs text-gray-400">回复</span>
                        </div>
                        {/* Last Reply Avatar Stack (Mockup) */}
                        <div className="hidden md:flex -space-x-2">
                            {[1, 2].map((_, i) => (
                                <div key={i} className="w-6 h-6 rounded-full bg-gray-200 dark:bg-gray-700 border-2 border-white dark:border-gray-900 overflow-hidden" />
                            ))}
                        </div>
                    </div>

                </div>
            </Link>
        </motion.div>
    )
}

function ClockIcon({ className }: { className?: string }) {
    return (
        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
            <circle cx="12" cy="12" r="10" />
            <polyline points="12 6 12 12 16 14" />
        </svg>
    )
}
