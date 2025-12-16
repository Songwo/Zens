"use client"

import { useState, useEffect } from "react"
import Navbar from "@/components/navbar"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import api from "@/lib/api"
import { Sparkles, TrendingUp, Activity, PieChart as PieIcon, BarChart3, Zap, Hash, MessageSquare } from "lucide-react"
import { motion } from "framer-motion"
import {
    AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
    PieChart, Pie, Cell, Legend
} from 'recharts'

export default function TrendDashboard() {
    const [keywordCloud, setKeywordCloud] = useState<any>(null)
    const [categoryPie, setCategoryPie] = useState<any>(null)
    const [heatRank, setHeatRank] = useState<any[]>([])
    const [postTrend, setPostTrend] = useState<any[]>([])
    const [loading, setLoading] = useState(true)

    // Chart Colors
    const COLORS = ['#3b82f6', '#8b5cf6', '#ec4899', '#f97316', '#10b981', '#6366f1']
    const DARK_THEME = {
        background: 'transparent',
        text: '#9ca3af',
        grid: '#374151',
        tooltip: {
            backgroundColor: '#1f2937',
            borderColor: '#374151',
            color: '#f3f4f6',
            borderRadius: '8px',
            boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)'
        }
    }

    useEffect(() => {
        loadTrendData()
    }, [])

    const loadTrendData = async () => {
        try {
            setLoading(true)
            // Parallel fetch
            const [cloudRes, pieRes, rankRes, trendRes]: any[] = await Promise.all([
                api.get("/sys-trend-stat/keyword-cloud"),
                api.get("/sys-trend-stat/category-pie"),
                api.get("/sys-trend-stat/heat-rank"),
                api.get("/sys-trend-stat/post-trend")
            ])

            if (cloudRes.code === 2000) setKeywordCloud(cloudRes.data)
            if (pieRes.code === 2000) setCategoryPie(pieRes.data)
            if (rankRes.code === 2000) setHeatRank(rankRes.data || [])
            if (trendRes && trendRes.code === 2000) setPostTrend(trendRes.data || [])

        } catch (error) {
            console.error("Failed to load trend data", error)
        } finally {
            setLoading(false)
        }
    }

    // Helper to calculate total interactions
    const totalInteractions = heatRank.reduce((acc, curr) => acc + (curr.viewCount || 0), 0)

    return (
        <div className="min-h-screen bg-gray-50/50 dark:bg-gray-900">
            <Navbar />

            <main className="container mx-auto px-4 pt-24 pb-12 max-w-7xl space-y-8">
                {/* Header Section */}
                <motion.div
                    initial={{ opacity: 0, y: -20 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 border-b border-gray-200 dark:border-gray-800 pb-6"
                >
                    <div>
                        <h1 className="text-3xl font-bold tracking-tight text-gray-900 dark:text-gray-100 flex items-center gap-3">
                            <Sparkles className="w-8 h-8 text-orange-500" />
                            趋势洞察
                            <span className="text-sm font-normal text-gray-500 dark:text-gray-400 bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded-md border border-gray-200 dark:border-gray-700">
                                BETA
                            </span>
                        </h1>
                        <p className="text-gray-600 dark:text-gray-400 mt-2 text-sm max-w-xl">
                            基于AI生成的实时校园内容趋势分析，助你发现热门话题与潜在机遇。
                        </p>
                    </div>
                    <div className="flex gap-2">
                        <button onClick={loadTrendData} className="px-4 py-2 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-300 hover:text-gray-900 dark:hover:text-white rounded-lg text-sm transition-colors flex items-center gap-2">
                            <Activity className="w-4 h-4" /> 刷新数据
                        </button>
                    </div>
                </motion.div>

                {loading ? (
                    <div className="flex justify-center items-center h-96">
                        <div className="relative w-16 h-16">
                            <div className="absolute top-0 left-0 w-full h-full border-4 border-gray-200 dark:border-gray-800 rounded-full"></div>
                            <div className="absolute top-0 left-0 w-full h-full border-4 border-t-orange-500 rounded-full animate-spin"></div>
                        </div>
                    </div>
                ) : (
                    <>
                        {/* KPI Cards */}
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                            <motion.div
                                initial={{ opacity: 0, y: 20 }}
                                animate={{ opacity: 1, y: 0 }}
                                transition={{ delay: 0.1 }}
                            >
                                <Card className="bg-white dark:bg-gray-800/50 border-gray-200 dark:border-gray-700">
                                    <CardContent className="p-6">
                                        <div className="flex justify-between items-start">
                                            <div>
                                                <p className="text-sm font-medium text-gray-500 dark:text-gray-400">实时热度指数</p>
                                                <h3 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                                                    {(totalInteractions / 100).toFixed(1)}k
                                                </h3>
                                            </div>
                                            <div className="p-2 bg-orange-500/10 rounded-lg">
                                                <Zap className="w-5 h-5 text-orange-500" />
                                            </div>
                                        </div>
                                        <div className="mt-4 flex items-center text-xs text-green-500">
                                            <TrendingUp className="w-3 h-3 mr-1" />
                                            +12.5% <span className="text-gray-400 dark:text-gray-600 ml-1">较昨日</span>
                                        </div>
                                    </CardContent>
                                </Card>
                            </motion.div>

                            <motion.div
                                initial={{ opacity: 0, y: 20 }}
                                animate={{ opacity: 1, y: 0 }}
                                transition={{ delay: 0.2 }}
                            >
                                <Card className="bg-white dark:bg-gray-800/50 border-gray-200 dark:border-gray-700">
                                    <CardContent className="p-6">
                                        <div className="flex justify-between items-start">
                                            <div>
                                                <p className="text-sm font-medium text-gray-500 dark:text-gray-400">活跃话题数</p>
                                                <h3 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                                                    {heatRank.length}
                                                </h3>
                                            </div>
                                            <div className="p-2 bg-blue-500/10 rounded-lg">
                                                <Hash className="w-5 h-5 text-blue-500" />
                                            </div>
                                        </div>
                                        <div className="mt-4 flex items-center text-xs text-gray-500">
                                            <span className="text-gray-400 dark:text-gray-600">当前最热: </span>
                                            <span className="ml-1 text-gray-700 dark:text-gray-300 truncate max-w-[120px]">{heatRank[0]?.title}</span>
                                        </div>
                                    </CardContent>
                                </Card>
                            </motion.div>

                            <motion.div
                                initial={{ opacity: 0, y: 20 }}
                                animate={{ opacity: 1, y: 0 }}
                                transition={{ delay: 0.3 }}
                            >
                                <Card className="bg-white dark:bg-gray-800/50 border-gray-200 dark:border-gray-700">
                                    <CardContent className="p-6">
                                        <div className="flex justify-between items-start">
                                            <div>
                                                <p className="text-sm font-medium text-gray-500 dark:text-gray-400">内容分类覆盖</p>
                                                <h3 className="text-2xl font-bold text-gray-900 dark:text-gray-100 mt-2">
                                                    {categoryPie?.categories?.length || 0}
                                                </h3>
                                            </div>
                                            <div className="p-2 bg-purple-500/10 rounded-lg">
                                                <PieIcon className="w-5 h-5 text-purple-500" />
                                            </div>
                                        </div>
                                        <div className="mt-4 flex items-center text-xs text-gray-500">
                                            <span className="text-gray-400 dark:text-gray-600">主导分类: </span>
                                            <span className="ml-1 text-gray-700 dark:text-gray-300">{categoryPie?.categories?.[0]?.name}</span>
                                        </div>
                                    </CardContent>
                                </Card>
                            </motion.div>
                        </div>

                        {/* Main Charts Area */}
                        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">

                            {/* Trend Line Chart */}
                            <motion.div
                                className="lg:col-span-2"
                                initial={{ opacity: 0, scale: 0.95 }}
                                animate={{ opacity: 1, scale: 1 }}
                                transition={{ delay: 0.4 }}
                            >
                                <Card className="bg-white dark:bg-gray-800/50 border-gray-200 dark:border-gray-700 h-[400px]">
                                    <CardHeader>
                                        <CardTitle className="text-lg text-gray-900 dark:text-gray-100 flex items-center gap-2">
                                            <Activity className="w-5 h-5 text-orange-500" />
                                            内容发布趋势 (7天)
                                        </CardTitle>
                                        <CardDescription className="text-gray-500 dark:text-gray-400">
                                            每日新增内容数量统计
                                        </CardDescription>
                                    </CardHeader>
                                    <CardContent className="h-[300px] w-full">
                                        <ResponsiveContainer width="100%" height="100%">
                                            <AreaChart data={postTrend}>
                                                <defs>
                                                    <linearGradient id="colorCount" x1="0" y1="0" x2="0" y2="1">
                                                        <stop offset="5%" stopColor="#f97316" stopOpacity={0.3} />
                                                        <stop offset="95%" stopColor="#f97316" stopOpacity={0} />
                                                    </linearGradient>
                                                </defs>
                                                <CartesianGrid strokeDasharray="3 3" stroke={DARK_THEME.grid} vertical={false} />
                                                <XAxis
                                                    dataKey="date"
                                                    stroke={DARK_THEME.text}
                                                    fontSize={12}
                                                    tickLine={false}
                                                    axisLine={false}
                                                />
                                                <YAxis
                                                    stroke={DARK_THEME.text}
                                                    fontSize={12}
                                                    tickLine={false}
                                                    axisLine={false}
                                                />
                                                <Tooltip
                                                    contentStyle={DARK_THEME.tooltip}
                                                    itemStyle={{ color: '#fff' }}
                                                    cursor={{ stroke: '#52525b', strokeWidth: 1 }}
                                                />
                                                <Area
                                                    type="monotone"
                                                    dataKey="count"
                                                    stroke="#f97316"
                                                    strokeWidth={3}
                                                    fillOpacity={1}
                                                    fill="url(#colorCount)"
                                                />
                                            </AreaChart>
                                        </ResponsiveContainer>
                                    </CardContent>
                                </Card>
                            </motion.div>

                            {/* Category Pie Chart */}
                            <motion.div
                                initial={{ opacity: 0, scale: 0.95 }}
                                animate={{ opacity: 1, scale: 1 }}
                                transition={{ delay: 0.5 }}
                            >
                                <Card className="bg-white dark:bg-gray-800/50 border-gray-200 dark:border-gray-700 h-[400px]">
                                    <CardHeader>
                                        <CardTitle className="text-lg text-gray-900 dark:text-gray-100 flex items-center gap-2">
                                            <PieIcon className="w-5 h-5 text-purple-500" />
                                            内容构成
                                        </CardTitle>
                                    </CardHeader>
                                    <CardContent className="h-[320px] w-full flex flex-col justify-center items-center">
                                        <ResponsiveContainer width="100%" height="100%">
                                            <PieChart>
                                                <Pie
                                                    data={categoryPie?.categories || []}
                                                    cx="50%"
                                                    cy="50%"
                                                    innerRadius={60}
                                                    outerRadius={90} // 略微减小半径以为图例留出空间
                                                    fill="#8884d8"
                                                    paddingAngle={5}
                                                    dataKey="count"
                                                >
                                                    {categoryPie?.categories?.map((entry: any, index: number) => (
                                                        <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                                    ))}
                                                </Pie>
                                                <Tooltip
                                                    contentStyle={DARK_THEME.tooltip}
                                                    itemStyle={{ color: '#fff' }}
                                                />
                                                <Legend
                                                    layout="horizontal"
                                                    verticalAlign="bottom"
                                                    align="center"
                                                    wrapperStyle={{ paddingTop: "20px", fontSize: "12px" }}
                                                    formatter={(value) => <span style={{ color: '#9ca3af', display: 'inline-block', marginRight: '10px' }}>{value}</span>}
                                                />
                                            </PieChart>
                                        </ResponsiveContainer>
                                    </CardContent>
                                </Card>
                            </motion.div>

                            {/* Heat Rank List */}
                            <motion.div
                                className="lg:col-span-1"
                                initial={{ opacity: 0, y: 20 }}
                                animate={{ opacity: 1, y: 0 }}
                                transition={{ delay: 0.6 }}
                            >
                                <Card className="bg-white dark:bg-gray-800/50 border-gray-200 dark:border-gray-700 h-full max-h-[500px] overflow-hidden flex flex-col">
                                    <CardHeader className="pb-3 border-b border-gray-200 dark:border-gray-700">
                                        <CardTitle className="text-lg text-gray-900 dark:text-gray-100 flex items-center gap-2">
                                            <BarChart3 className="w-5 h-5 text-red-500" />
                                            热门话题榜
                                        </CardTitle>
                                    </CardHeader>
                                    <CardContent className="flex-1 overflow-y-auto pr-2 custom-scrollbar p-0">
                                        <div className="divide-y divide-zinc-800/50">
                                            {heatRank.slice(0, 10).map((item: any, index: number) => (
                                                <div
                                                    key={item.postId}
                                                    className="flex items-center gap-4 p-4 hover:bg-zinc-800/50 transition-colors group"
                                                >
                                                    <div className={`
                                                        w-8 h-8 rounded flex items-center justify-center text-sm font-bold flex-shrink-0
                                                        ${index === 0 ? "bg-orange-500 text-white" :
                                                            index === 1 ? "bg-zinc-400 text-zinc-900" :
                                                                index === 2 ? "bg-zinc-600 text-zinc-100" :
                                                                    "bg-zinc-800 text-zinc-400"}
                                                    `}>
                                                        {index + 1}
                                                    </div>
                                                    <div className="flex-1 min-w-0">
                                                        <h4 className="text-sm font-medium text-zinc-200 group-hover:text-white truncate transition-colors">
                                                            {item.title}
                                                        </h4>
                                                        <div className="flex items-center gap-3 mt-1">
                                                            <div className="flex items-center text-xs text-zinc-500">
                                                                <Zap className="w-3 h-3 mr-1 text-orange-400" />
                                                                {item.heatScore.toFixed(0)}
                                                            </div>
                                                            <div className="flex items-center text-xs text-zinc-500">
                                                                <MessageSquare className="w-3 h-3 mr-1 text-blue-400" />
                                                                {item.viewCount}
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    </CardContent>
                                </Card>
                            </motion.div>

                            {/* Keyword Cloud (Styled visually) */}
                            <motion.div
                                className="lg:col-span-2"
                                initial={{ opacity: 0, y: 20 }}
                                animate={{ opacity: 1, y: 0 }}
                                transition={{ delay: 0.7 }}
                            >
                                <Card className="bg-white dark:bg-gray-800/50 border-gray-200 dark:border-gray-700">
                                    <CardHeader>
                                        <CardTitle className="text-lg text-gray-900 dark:text-gray-100 flex items-center gap-2">
                                            <Hash className="w-5 h-5 text-blue-500" />
                                            热门关键词
                                        </CardTitle>
                                    </CardHeader>
                                    <CardContent className="p-8">
                                        <div className="flex flex-wrap items-center justify-center gap-x-8 gap-y-4">
                                            {keywordCloud?.keywords?.map((kw: any, index: number) => {
                                                // 调整缩放逻辑，防止文字过大
                                                // 原逻辑可能导致 rem 值过大，现限制在 0.8rem - 2.0rem 之间
                                                const scale = Math.max(0, Math.min(1.2, (kw.count || 0) / 10));
                                                return (
                                                    <span
                                                        key={index}
                                                        className={`
                                                            inline-block transition-all duration-300 cursor-default hover:text-orange-500
                                                            ${index % 3 === 0 ? 'text-gray-400 dark:text-gray-600' : index % 3 === 1 ? 'text-gray-500' : 'text-gray-600 dark:text-gray-400'}
                                                            hover:scale-110 font-bold tracking-tight
                                                        `}
                                                        style={{
                                                            fontSize: `${0.875 + scale}rem`,
                                                            opacity: 0.6 + (scale * 0.2)
                                                        }}
                                                    >
                                                        {kw.keyword}
                                                    </span>
                                                )
                                            })}
                                        </div>
                                    </CardContent>
                                </Card>
                            </motion.div>
                        </div>
                    </>
                )}
            </main>
        </div>
    )
}
