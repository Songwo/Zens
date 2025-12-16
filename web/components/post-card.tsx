"use client"

import { motion } from "framer-motion"
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { MessageSquare, Heart, Eye } from "lucide-react"
import Link from "next/link"

// 帖子接口定义 - 匹配后端 PostResponse
interface Post {
    id: string
    title: string
    content: string
    userId: string
    authorName: string      // 后端已填充，匿名时返回"匿名同学"
    authorAvatar?: string   // 头像URL，匿名时为null
    isAnonymous: number     // 1=匿名, 0=非匿名
    createTime: string
    viewCount: number
    likeCount: number
    commentCount: number
    collectCount?: number
    tags?: string
    categoryId?: string
    categoryName?: string   // 分类名称
    heatScore?: number
    sentimentScore?: number
    sentimentLabel?: string // positive/neutral/negative
    trendLevel?: string     // hot/trending/normal
}

// 简单的 Badge 组件 (如果项目中没有)
function SimpleBadge({ children, className }: { children: React.ReactNode, className?: string }) {
    return (
        <span className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80 ${className}`}>
            {children}
        </span>
    )
}

export default function PostCard({ post }: { post: Post }) {

    // 格式化时间 (简单处理)
    const formatDate = (dateStr: string) => {
        const date = new Date(dateStr);
        return date.toLocaleDateString() + " " + date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    // 解析标签
    const tags = post.tags ? post.tags.split(/[#\s]+/).filter(t => t) : []

    return (
        <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            whileHover={{ y: -4, transition: { duration: 0.2 } }}
            className="w-full"
        >
            <Link href={`/post/${post.id}`}>
                <Card className="h-full cursor-pointer hover:shadow-lg transition-shadow duration-300 border-gray-100 dark:border-gray-800">
                    <CardHeader className="p-4 pb-2">
                        <div className="flex justify-between items-start gap-4">
                            <div className="flex-1">
                                <div className="flex items-center gap-2 mb-1 flex-wrap">
                                    {/* 趋势标签 */}
                                    {post.trendLevel === 'hot' && (
                                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-red-100 text-red-600">
                                            🔥 热门
                                        </span>
                                    )}
                                    {post.trendLevel === 'trending' && (
                                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-orange-100 text-orange-600">
                                            📈 上升
                                        </span>
                                    )}
                                    {/* 情感标签 */}
                                    {post.sentimentLabel === 'positive' && (
                                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-700 border border-green-200">
                                            😊 积极
                                        </span>
                                    )}
                                    {post.sentimentLabel === 'negative' && (
                                        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-orange-100 text-orange-700 border border-orange-200">
                                            😔 消极
                                        </span>
                                    )}
                                    {/* 分类名称 */}
                                    {post.categoryName && (
                                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-600">
                                            {post.categoryName}
                                        </span>
                                    )}
                                </div>
                                <h3 className="text-lg font-bold leading-tight text-gray-900 dark:text-gray-100 line-clamp-2 hover:text-primary transition-colors">
                                    {post.title}
                                </h3>
                                <div className="flex flex-wrap gap-2 mt-2">
                                    {tags.map((tag, i) => (
                                        <SimpleBadge key={i} className="text-xs bg-blue-50 text-blue-600 border-blue-100 hover:bg-blue-100">
                                            {tag}
                                        </SimpleBadge>
                                    ))}
                                </div>
                            </div>
                        </div>
                    </CardHeader>

                    <CardContent className="p-4 pt-0 pb-2">
                        <p className="text-sm text-gray-500 dark:text-gray-400 line-clamp-3 mb-4">
                            {post.content.replace(/<[^>]+>/g, '')}
                        </p>
                    </CardContent>

                    <CardFooter className="p-4 pt-2 border-t border-gray-50 dark:border-gray-800/50 flex items-center justify-between text-xs text-muted-foreground bg-gray-50/30">
                        <div className="flex items-center space-x-2">
                            {/* 作者头像与名称 - 若无头像显示首字母 */}
                            <div className="w-6 h-6 rounded-full bg-gray-200 flex items-center justify-center overflow-hidden">
                                {post.authorAvatar ? (
                                    // eslint-disable-next-line @next/next/no-img-element
                                    <img src={post.authorAvatar} alt="avatar" className="w-full h-full object-cover" />
                                ) : (
                                    <span className="text-[10px] font-bold text-gray-500">
                                        {(post.authorName || "U").charAt(0).toUpperCase()}
                                    </span>
                                )}
                            </div>
                            <span className="font-medium text-gray-700">{post.authorName || "匿名用户"}</span>
                            <span className="text-gray-300">|</span>
                            <span>{formatDate(post.createTime)}</span>
                        </div>

                        <div className="flex items-center space-x-4">
                            <div className="flex items-center space-x-1" title="浏览量">
                                <Eye className="w-3.5 h-3.5" />
                                <span>{post.viewCount || 0}</span>
                            </div>
                            <div className="flex items-center space-x-1" title="点赞">
                                <Heart className="w-3.5 h-3.5" />
                                <span>{post.likeCount || 0}</span>
                            </div>
                            <div className="flex items-center space-x-1" title="评论">
                                <MessageSquare className="w-3.5 h-3.5" />
                                <span>{post.commentCount || 0}</span>
                            </div>
                        </div>
                    </CardFooter>
                </Card>
            </Link>
        </motion.div>
    )
}
