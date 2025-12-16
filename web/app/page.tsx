"use client"

import { useState, useEffect } from "react"
import { useSearchParams, useRouter } from "next/navigation"
import Navbar from "@/components/navbar"
import PostCard from "@/components/post-card"
import LeftSidebar from "@/components/left-sidebar"
import { Button } from "@/components/ui/button"
import api from "@/lib/api"
import { Loader2, Flame, Layers, LayoutList, ChevronDown, Filter, X } from "lucide-react"
import { motion, AnimatePresence } from "framer-motion"

export default function Home() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const categoryID = searchParams.get("categoryID")

  const [activeTab, setActiveTab] = useState<"new" | "hot">("new")
  const [posts, setPosts] = useState<any[]>([])
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(true)

  // Filter States
  const [isTagsOpen, setIsTagsOpen] = useState(false)
  const [selectedTag, setSelectedTag] = useState<string | null>(null)
  const [popularTags, setPopularTags] = useState<any[]>([])
  const [filteredCategoryName, setFilteredCategoryName] = useState<string>("")

  // Fetch Category Info if filtered
  useEffect(() => {
    if (categoryID) {
      const fetchCat = async () => {
        try {
          const res: any = await api.get(`/sys-category/${categoryID}`)
          if (res.code === 2000 && res.data) {
            setFilteredCategoryName(res.data.name)
          }
        } catch (e) { }
      }
      fetchCat()
    } else {
      setFilteredCategoryName("")
    }
  }, [categoryID])

  // Fetch Popular Tags for Dropdown
  useEffect(() => {
    const fetchTags = async () => {
      try {
        const res: any = await api.get("/tag/hot?limit=20") // Assuming endpoint exists or similar
        if (res.code === 2000) {
          setPopularTags(res.data || [])
        }
      } catch (e) { }
    }
    fetchTags()
  }, [])

  // Refetch posts when criteria changes
  useEffect(() => {
    setPage(1)
    setPosts([])
    setHasMore(true)
    fetchPosts(activeTab, 1, true)
  }, [activeTab, categoryID, selectedTag])

  // Loading Logic
  const fetchPosts = async (type: "new" | "hot", pageNum: number, isRefresh = false) => {
    try {
      if (isRefresh) {
        setLoading(true)
      } else {
        setLoadingMore(true)
      }

      const res: any = await api.post("/sys-post/search-lists", {
        page: pageNum,
        pageSize: 15,
        orderBy: type,
        status: 1,
        categoryID: categoryID || undefined, // Pass category filter
        tag: selectedTag || undefined        // Pass tag filter
      })

      if (res.code === 2000) {
        const records = res.data.records || []
        const current = res.data.current || pageNum
        const pages = res.data.pages || 1

        if (isRefresh) {
          setPosts(records)
        } else {
          setPosts(prev => [...prev, ...records])
        }

        setHasMore(current < pages)
      }
    } catch (error) {
      console.error("加载帖子失败", error)
    } finally {
      setLoading(false)
      setLoadingMore(false)
    }
  }

  const handleLoadMore = () => {
    if (!loadingMore && hasMore) {
      const nextPage = page + 1
      setPage(nextPage)
      fetchPosts(activeTab, nextPage, false)
    }
  }

  return (
    <div className="min-h-screen bg-white dark:bg-gray-900">
      <Navbar />

      {/* Left Sidebar */}
      <LeftSidebar />

      <main className="pt-20 xl:pl-64 min-h-screen">
        <div className="max-w-[1200px] mx-auto px-4 sm:px-6 lg:px-8 py-6">

          {/* Top Info Banner */}
          <div className="bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-900/20 dark:to-indigo-900/20 text-blue-800 dark:text-blue-200 px-6 py-4 rounded-lg mb-8 border border-blue-100 dark:border-blue-800/50 flex items-center justify-between">
            <div>
              {categoryID ? (
                <>
                  <h2 className="font-bold text-lg mb-1 flex items-center gap-2">
                    <Layers className="w-5 h-5" /> {filteredCategoryName || "分类浏览"}
                  </h2>
                  <p className="text-sm opacity-80">正在浏览该分类下的所有内容。</p>
                </>
              ) : (
                <>
                  <h2 className="font-bold text-lg mb-1">👋 欢迎来到 Campus Pulse</h2>
                  <p className="text-sm opacity-80">真诚、友善、团结、专业，共建你我引以为荣的校园社区。</p>
                </>
              )}
            </div>
            {categoryID && (
              <Button variant="ghost" size="sm" onClick={() => router.push('/')}>查看全部</Button>
            )}
          </div>

          {/* Filter / Breadcrumb Bar */}
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4 bg-white dark:bg-gray-900 sticky top-16 z-10 py-2">
            <div className="flex items-center gap-2">
              {/* Current Scope Badge */}
              <div className="hidden md:flex items-center px-3 py-1.5 bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400 text-sm font-medium rounded-md border border-blue-100 dark:border-blue-800">
                {filteredCategoryName || "全部内容"}
              </div>

              <span className="text-gray-300 hidden md:inline">|</span>

              {/* Tags Filter Dropdown (Custom implementation for specific look) */}
              <div className="relative">
                <button
                  onClick={() => setIsTagsOpen(!isTagsOpen)}
                  className={`flex items-center gap-1 px-3 py-1.5 rounded-md text-sm font-medium transition-colors border ${selectedTag ? 'bg-blue-50 border-blue-200 text-blue-600' : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-300'}`}
                >
                  {selectedTag ? `标签: ${selectedTag}` : "标签"}
                  <ChevronDown className="w-4 h-4 opacity-50" />
                </button>

                <AnimatePresence>
                  {isTagsOpen && (
                    <>
                      <div className="fixed inset-0 z-10" onClick={() => setIsTagsOpen(false)}></div>
                      <motion.div
                        initial={{ opacity: 0, y: 10 }}
                        animate={{ opacity: 1, y: 0 }}
                        exit={{ opacity: 0, y: 10 }}
                        className="absolute top-full left-0 mt-2 w-64 bg-white dark:bg-gray-800 rounded-lg shadow-xl border border-gray-200 dark:border-gray-700 z-20 p-2 max-h-80 overflow-y-auto"
                      >
                        <div className="px-2 py-2 mb-2">
                          <input
                            type="text"
                            placeholder="搜索..."
                            className="w-full bg-gray-100 dark:bg-gray-900 border-none rounded px-3 py-1.5 text-sm focus:ring-1 focus:ring-blue-500"
                            onClick={(e) => e.stopPropagation()}
                          />
                        </div>
                        <div className="grid grid-cols-1 gap-1">
                          <button
                            onClick={() => { setSelectedTag(null); setIsTagsOpen(false); }}
                            className="text-left px-3 py-2 text-sm rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500 font-bold"
                          >
                            无标签
                          </button>
                          {popularTags.map((tag: any) => (
                            <button
                              key={tag.id}
                              onClick={() => { setSelectedTag(tag.name); setIsTagsOpen(false); }}
                              className={`text-left px-3 py-2 text-sm rounded hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center justify-between ${selectedTag === tag.name ? 'text-blue-600 bg-blue-50 dark:bg-blue-900/10' : 'text-gray-700 dark:text-gray-300'}`}
                            >
                              <span className="flex items-center gap-2">
                                <span className="w-1.5 h-1.5 rounded-full bg-gray-400"></span>
                                {tag.name}
                              </span>
                              <span className="text-xs text-gray-400 bg-gray-100 dark:bg-gray-800 px-1.5 rounded">{tag.postCount || 0}</span>
                            </button>
                          ))}
                        </div>
                      </motion.div>
                    </>
                  )}
                </AnimatePresence>
              </div>

              <button
                onClick={() => setActiveTab("new")}
                className={`px-3 py-1.5 rounded-md text-sm font-medium transition-all ${activeTab === "new" ? "text-gray-900 dark:text-white font-bold border-b-2 border-blue-500 rounded-none" : "text-gray-500 hover:text-gray-700 dark:text-gray-400"}`}
              >
                最新
              </button>
              <button
                onClick={() => setActiveTab("hot")}
                className={`px-3 py-1.5 rounded-md text-sm font-medium transition-all ${activeTab === "hot" ? "text-gray-900 dark:text-white font-bold border-b-2 border-orange-500 rounded-none" : "text-gray-500 hover:text-gray-700 dark:text-gray-400"}`}
              >
                热门
              </button>
            </div>

            <div className="flex items-center gap-2">
              {/* Optional right side filters or stats */}
            </div>
          </div>

          {/* List Header */}
          <div className="hidden md:flex px-4 py-2 text-xs font-semibold text-gray-500 border-b border-gray-200 dark:border-gray-800 mb-2 bg-gray-50/50 dark:bg-gray-800/50 rounded-t-lg">
            <div className="flex-1 pl-2">话题</div>
            <div className="w-[200px] flex justify-between px-6">
              <span>浏览</span>
              <span>回复</span>
              <span>活动</span>
            </div>
          </div>

          {/* Post List */}
          <motion.div layout className="bg-white dark:bg-gray-900 shadow-sm rounded-b-lg border border-gray-200 dark:border-gray-800 border-t-0 overflow-hidden min-h-[500px]">
            {posts.length > 0 ? (
              <div className="divide-y divide-gray-100 dark:divide-gray-800">
                {posts.map((post) => (
                  <PostCard key={post.id} post={post} />
                ))}
              </div>
            ) : (
              !loading && (
                <div className="text-center py-32 text-gray-400">
                  <LayoutList className="w-12 h-12 mx-auto mb-4 opacity-20" />
                  <p>暂无相关内容</p>
                  {selectedTag && (
                    <Button variant="link" onClick={() => setSelectedTag(null)}>清除标签筛选</Button>
                  )}
                </div>
              )
            )}
          </motion.div>

          {/* Loading State */}
          {posts.length > 0 && hasMore && (
            <div className="flex justify-center pt-8 pb-12">
              <Button variant="ghost" onClick={handleLoadMore} disabled={loadingMore} className="text-gray-500">
                {loadingMore ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : "加载更多..."}
              </Button>
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
