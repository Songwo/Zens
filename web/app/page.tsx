"use client"

import { useState, useEffect } from "react"
import Navbar from "@/components/navbar"
import PostCard from "@/components/post-card"
import { Button } from "@/components/ui/button"
import api from "@/lib/api"
import { Loader2, Flame, Clock } from "lucide-react"
import { motion } from "framer-motion"
import RecommendedTagsSidebar from "@/components/recommended-tags"
import HeatRankSidebar from "@/components/heat-rank"

export default function Home() {
  const [activeTab, setActiveTab] = useState<"new" | "hot">("new")
  const [posts, setPosts] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  // 加载帖子列表
  const fetchPosts = async (type: "new" | "hot", pageNum: number, isRefresh = false) => {
    try {
      setLoading(true)
      const res: any = await api.post("/sys-post/search-lists", {
        page: pageNum,
        pageSize: 10,
        orderBy: type, // 使用后端新增的 sorting 字段
        status: 1
      })

      if (res.code === 2000) {
        // 这里假设后端返回的是 Mybatis-Plus IPage 结构: { records: [], total, ... }
        // 或者 Result.success(pageData) 直接返回
        const records = res.data.records || []

        if (isRefresh) {
          setPosts(records)
        } else {
          setPosts(prev => [...prev, ...records])
        }

        // 简单判断是否还有更多 (如果当前页返回条数 < pageSize，说明没了)
        if (records.length < 10) {
          setHasMore(false)
        }
      }
    } catch (error) {
      console.error("加载帖子失败", error)
    } finally {
      setLoading(false)
    }
  }

  // 初始加载和Tab切换
  useEffect(() => {
    setPage(1)
    setPosts([])
    setHasMore(true)
    fetchPosts(activeTab, 1, true)
  }, [activeTab])

  // 加载更多
  const handleLoadMore = () => {
    const nextPage = page + 1
    setPage(nextPage)
    fetchPosts(activeTab, nextPage, false)
  }

  return (
    <div className="min-h-screen bg-gray-50/50 dark:bg-gray-900">
      <Navbar />

      <main className="container mx-auto px-4 pt-24 pb-12 max-w-5xl">
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
          {/* 左侧主要内容区 */}
          <div className="lg:col-span-3 space-y-6">

            {/* 视图切换 Tabs */}
            <div className="flex items-center space-x-4 mb-6">
              <button
                onClick={() => setActiveTab("new")}
                className={`flex items-center space-x-2 px-4 py-2 rounded-full font-medium transition-all duration-300 ${activeTab === "new"
                  ? "bg-white shadow text-primary"
                  : "text-gray-500 hover:text-gray-900"
                  }`}
              >
                <Clock className="w-4 h-4" />
                <span>最新发布</span>
              </button>
              <button
                onClick={() => setActiveTab("hot")}
                className={`flex items-center space-x-2 px-4 py-2 rounded-full font-medium transition-all duration-300 ${activeTab === "hot"
                  ? "bg-white shadow text-red-500"
                  : "text-gray-500 hover:text-gray-900"
                  }`}
              >
                <Flame className="w-4 h-4" />
                <span>热门趋势</span>
              </button>
            </div>

            {/* 帖子列表容器 */}
            <motion.div
              layout
              className="space-y-4"
            >
              {posts.length > 0 ? (
                posts.map((post) => (
                  <PostCard key={post.id} post={post} />
                ))
              ) : (
                !loading && (
                  <div className="text-center py-20 text-muted-foreground">
                    还没有帖子内容，快去发布第一条吧！
                  </div>
                )
              )}
            </motion.div>

            {/* 加载更多 */}
            {posts.length > 0 && hasMore && (
              <div className="flex justify-center pt-8">
                <Button
                  variant="outline"
                  onClick={handleLoadMore}
                  disabled={loading}
                  className="rounded-full px-8"
                >
                  {loading ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : null}
                  {loading ? "加载中..." : "加载更多"}
                </Button>
              </div>
            )}
          </div>

          {/* 右侧边栏 (Sidebar) - 智能推荐 */}
          <div className="hidden lg:block space-y-6">
            {/* 推荐标签 */}
            <RecommendedTagsSidebar />

            {/* 热度排行 */}
            <HeatRankSidebar />

            {/* 公告栏 */}
            <div className="bg-gradient-to-br from-primary/5 to-blue-50/50 rounded-xl p-5 border border-primary/10">
              <h3 className="font-bold text-primary mb-2">📢 校园公告</h3>
              <p className="text-sm text-gray-600 leading-relaxed">
                欢迎来到 Campus Pulse！这里是自由开放的校园社区。请遵守社区规范，文明发言。
              </p>
            </div>
          </div>
        </div>
      </main>
    </div>
  )
}
