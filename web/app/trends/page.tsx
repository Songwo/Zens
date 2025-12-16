"use client"

import { useState, useEffect } from "react"
import Navbar from "@/components/navbar"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import api from "@/lib/api"
import { BarChart3, PieChart, TrendingUp, Activity, Sparkles } from "lucide-react"
import { motion } from "framer-motion"

export default function TrendDashboard() {
    const [keywordCloud, setKeywordCloud] = useState<any>(null)
    const [categoryPie, setCategoryPie] = useState<any>(null)
    const [heatRank, setHeatRank] = useState<any[]>([])
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        loadTrendData()
    }, [])

    const loadTrendData = async () => {
        try {
            setLoading(true)
            const [cloudRes, pieRes, rankRes]: any[] = await Promise.all([
                api.get("/sys-trend-stat/keyword-cloud"),
                api.get("/sys-trend-stat/category-pie"),
                api.get("/sys-trend-stat/heat-rank")
            ])

            if (cloudRes.code === 2000) setKeywordCloud(cloudRes.data)
            if (pieRes.code === 2000) setCategoryPie(pieRes.data)
            if (rankRes.code === 2000) setHeatRank(rankRes.data || [])
        } catch (error) {
            console.error("加载趋势数据失败", error)
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="min-h-screen bg-gray-50/50 dark:bg-gray-900">
            <Navbar />

            <main className="container mx-auto px-4 pt-24 pb-12 max-w-7xl">
                {/* 页面标题 */}
                <motion.div
                    initial={{ opacity: 0, y: -20 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="mb-8"
                >
                    <h1 className="text-3xl md:text-4xl font-bold text-gray-900 dark:text-gray-100 mb-2 flex items-center gap-3">
                        <Sparkles className="w-8 h-8 text-primary" />
                        智能内容趋势分析
                    </h1>
                    <p className="text-gray-600 dark:text-gray-400">
                        基于AI的校园内容趋势洞察与推荐系统
                    </p>
                </motion.div>

                {loading ? (
                    <div className="flex justify-center items-center h-64">
                        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                        {/* 关键词词云 */}
                        <motion.div
                            initial={{ opacity: 0, scale: 0.95 }}
                            animate={{ opacity: 1, scale: 1 }}
                            transition={{ delay: 0.1 }}
                            className="lg:col-span-2"
                        >
                            <Card className="border-gray-100 dark:border-gray-800">
                                <CardHeader>
                                    <CardTitle className="flex items-center gap-2">
                                        <Activity className="w-5 h-5 text-blue-500" />
                                        热门关键词云
                                    </CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <div className="flex flex-wrap gap-3 justify-center p-6">
                                        {keywordCloud?.keywords?.map((kw: any, index: number) => {
                                            const size = Math.max(12, Math.min(32, kw.count * 2))
                                            const colors = [
                                                "text-blue-600", "text-purple-600", "text-pink-600",
                                                "text-orange-600", "text-green-600", "text-indigo-600"
                                            ]
                                            return (
                                                <span
                                                    key={index}
                                                    className={`font-bold ${colors[index % colors.length]} hover:scale-110 transition-transform cursor-pointer`}
                                                    style={{ fontSize: `${size}px` }}
                                                >
                                                    {kw.keyword}
                                                </span>
                                            )
                                        })}
                                    </div>
                                </CardContent>
                            </Card>
                        </motion.div>

                        {/* 分类分布 */}
                        <motion.div
                            initial={{ opacity: 0, scale: 0.95 }}
                            animate={{ opacity: 1, scale: 1 }}
                            transition={{ delay: 0.2 }}
                        >
                            <Card className="border-gray-100 dark:border-gray-800">
                                <CardHeader>
                                    <CardTitle className="flex items-center gap-2">
                                        <PieChart className="w-5 h-5 text-purple-500" />
                                        分类分布
                                    </CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <div className="space-y-3">
                                        {categoryPie?.categories?.map((cat: any, index: number) => {
                                            const colors = [
                                                "bg-blue-500", "bg-purple-500", "bg-pink-500",
                                                "bg-orange-500", "bg-green-500"
                                            ]
                                            const percentage = ((cat.count / categoryPie.total) * 100).toFixed(1)
                                            return (
                                                <div key={index} className="space-y-1">
                                                    <div className="flex justify-between text-sm">
                                                        <span className="font-medium text-gray-700">{cat.name}</span>
                                                        <span className="text-gray-500">{percentage}%</span>
                                                    </div>
                                                    <div className="w-full bg-gray-200 rounded-full h-2">
                                                        <div
                                                            className={`${colors[index % colors.length]} h-2 rounded-full transition-all duration-500`}
                                                            style={{ width: `${percentage}%` }}
                                                        ></div>
                                                    </div>
                                                </div>
                                            )
                                        })}
                                    </div>
                                </CardContent>
                            </Card>
                        </motion.div>

                        {/* 热度排行榜 */}
                        <motion.div
                            initial={{ opacity: 0, scale: 0.95 }}
                            animate={{ opacity: 1, scale: 1 }}
                            transition={{ delay: 0.3 }}
                            className="lg:col-span-3"
                        >
                            <Card className="border-gray-100 dark:border-gray-800">
                                <CardHeader>
                                    <CardTitle className="flex items-center gap-2">
                                        <TrendingUp className="w-5 h-5 text-red-500" />
                                        热度排行榜 TOP 10
                                    </CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                        {heatRank.slice(0, 10).map((item: any, index: number) => (
                                            <div
                                                key={item.postId}
                                                className="flex items-center gap-4 p-4 rounded-lg bg-gradient-to-r from-gray-50 to-white hover:from-blue-50 hover:to-indigo-50 transition-all duration-200 border border-gray-100"
                                            >
                                                <div className={`flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center text-lg font-bold ${index === 0 ? "bg-gradient-to-br from-yellow-400 to-orange-500 text-white" :
                                                        index === 1 ? "bg-gradient-to-br from-gray-300 to-gray-400 text-white" :
                                                            index === 2 ? "bg-gradient-to-br from-orange-300 to-orange-400 text-white" :
                                                                "bg-gray-200 text-gray-600"
                                                    }`}>
                                                    {index + 1}
                                                </div>
                                                <div className="flex-1 min-w-0">
                                                    <p className="font-medium text-gray-900 line-clamp-1">{item.title}</p>
                                                    <div className="flex items-center gap-3 mt-1 text-xs text-gray-500">
                                                        <span className="flex items-center gap-1">
                                                            <BarChart3 className="w-3 h-3" />
                                                            热度 {item.heatScore.toFixed(1)}
                                                        </span>
                                                        <span>•</span>
                                                        <span>{item.viewCount} 浏览</span>
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </CardContent>
                            </Card>
                        </motion.div>
                    </div>
                )}
            </main>
        </div>
    )
}
