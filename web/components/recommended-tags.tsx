"use client"

import { useState, useEffect } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import api from "@/lib/api"
import { TrendingUp, Tag, Flame } from "lucide-react"

interface RecommendedTag {
    id: string
    name: string
    heat: number
}

export default function RecommendedTagsSidebar() {
    const [tags, setTags] = useState<RecommendedTag[]>([])
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        loadRecommendedTags()
    }, [])

    const loadRecommendedTags = async () => {
        try {
            const res: any = await api.get("/recommend/tags?limit=8")
            if (res.code === 2000) {
                setTags(res.data || [])
            }
        } catch (error) {
            console.error("加载推荐标签失败", error)
        } finally {
            setLoading(false)
        }
    }

    if (loading) {
        return (
            <Card className="border-gray-100 dark:border-gray-800">
                <CardHeader className="pb-3">
                    <CardTitle className="text-sm font-bold flex items-center gap-2">
                        <Tag className="w-4 h-4 text-blue-500" />
                        推荐标签
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="animate-pulse space-y-2">
                        {[1, 2, 3, 4].map(i => (
                            <div key={i} className="h-6 bg-gray-200 rounded"></div>
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
                    <Tag className="w-4 h-4 text-blue-500" />
                    推荐标签
                </CardTitle>
            </CardHeader>
            <CardContent>
                <div className="flex flex-wrap gap-2">
                    {tags.map((tag) => (
                        <button
                            key={tag.id}
                            className="group relative inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium bg-gradient-to-r from-blue-50 to-indigo-50 text-blue-700 hover:from-blue-100 hover:to-indigo-100 transition-all duration-200 border border-blue-100"
                        >
                            <span>#{tag.name}</span>
                            {tag.heat > 50 && (
                                <Flame className="w-3 h-3 text-orange-500" />
                            )}
                        </button>
                    ))}
                </div>
            </CardContent>
        </Card>
    )
}
