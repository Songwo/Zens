"use client"

import { useState, useEffect } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import api from "@/lib/api"
import { BarChart3, TrendingUp } from "lucide-react"

interface HeatRankItem {
    postId: string
    title: string
    heatScore: number
    viewCount: number
}

export default function HeatRankSidebar() {
    const [heatRank, setHeatRank] = useState<HeatRankItem[]>([])
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        loadHeatRank()
    }, [])

    const loadHeatRank = async () => {
        try {
            // 尝试使用实时API
            const res: any = await api.get("/heat-rank/top")
            if (res.code === 2000 && res.data && res.data.length > 0) {
                setHeatRank((res.data || []).slice(0, 5))
            } else {
                // 降级到趋势统计API
                const fallbackRes: any = await api.get("/sys-trend-stat/heat-rank")
                if (fallbackRes.code === 2000) {
                    setHeatRank((fallbackRes.data || []).slice(0, 5))
                }
            }
        } catch (error) {
            console.error("加载热度排行失败", error)
        } finally {
            setLoading(false)
        }
    }

    if (loading) {
        return (
            <Card className="border-gray-100 dark:border-gray-800">
                <CardHeader className="pb-3">
                    <CardTitle className="text-sm font-bold flex items-center gap-2">
                        <TrendingUp className="w-4 h-4 text-red-500" />
                        热度排行
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="animate-pulse space-y-3">
                        {[1, 2, 3].map(i => (
                            <div key={i} className="h-12 bg-gray-200 rounded"></div>
                        ))}
                    </div>
                </CardContent>
            </Card>
        )
    }

    return (
        <Card className="border-gray-100 dark:border-gray-800">
            <CardHeader className="pb-3">
                <CardTitle className="text-sm font-bold flex items-center gap-2">
                    <TrendingUp className="w-4 h-4 text-red-500" />
                    热度排行 TOP 5
                </CardTitle>
            </CardHeader>
            <CardContent>
                <div className="space-y-3">
                    {heatRank.map((item, index) => (
                        <div
                            key={item.postId}
                            className="group flex items-start gap-3 p-2 rounded-lg hover:bg-gray-50 transition-colors cursor-pointer"
                        >
                            <div className={`flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold ${index === 0 ? "bg-gradient-to-br from-yellow-400 to-orange-500 text-white" :
                                index === 1 ? "bg-gradient-to-br from-gray-300 to-gray-400 text-white" :
                                    index === 2 ? "bg-gradient-to-br from-orange-300 to-orange-400 text-white" :
                                        "bg-gray-200 text-gray-600"
                                }`}>
                                {index + 1}
                            </div>
                            <div className="flex-1 min-w-0">
                                <p className="text-sm font-medium text-gray-900 line-clamp-2 group-hover:text-primary transition-colors">
                                    {item.title}
                                </p>
                                <div className="flex items-center gap-2 mt-1 text-xs text-gray-500">
                                    <span className="flex items-center gap-1">
                                        <BarChart3 className="w-3 h-3" />
                                        {item.heatScore.toFixed(1)}
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
    )
}
