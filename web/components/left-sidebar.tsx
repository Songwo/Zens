"use client"

import { useState, useEffect } from "react"
import Link from "next/link"
import { usePathname, useSearchParams } from "next/navigation"
import {
    Home, Hash, Layers,
    MessageCircle, BarChart2, Radio,
    BookOpen, Sparkles, Clock, Grid, Code, Box, Cpu, Coffee, Activity,
    Zap, Monitor, Database, Globe
} from "lucide-react"
import api from "@/lib/api"

// Icon mapping helper
const getIcon = (iconName: string) => {
    const icons: any = {
        "Code": Code,
        "Box": Box,
        "MessageSquare": MessageCircle,
        "Cpu": Cpu,
        "Coffee": Coffee,
        "Activity": Activity,
        "Zap": Zap,
        "Monitor": Monitor,
        "Database": Database,
        "Globe": Globe,
        "BookOpen": BookOpen
    }
    return icons[iconName] || Hash // Default icon
}

export default function LeftSidebar() {
    const pathname = usePathname()
    const searchParams = useSearchParams()
    const currentCategoryId = searchParams.get("categoryID")
    const [categories, setCategories] = useState<any[]>([])

    useEffect(() => {
        const fetchCategories = async () => {
            try {
                const res: any = await api.get("/sys-category/list")
                if (res.code === 2000) {
                    setCategories(res.data || [])
                }
            } catch (error) {
                console.error("Failed to load categories")
            }
        }
        fetchCategories()
    }, [])

    const menuGroups = [
        {
            label: "资源",
            items: [
                { icon: Home, label: "全部话题", href: "/" }, // Reset category
                { icon: Clock, label: "近期活动", href: "/recent" },
                { icon: Activity, label: "趋势分析", href: "/trends" }
            ]
        },
        {
            label: "类别",
            items: categories.map(cat => ({
                icon: getIcon(cat.icon),
                label: cat.name,
                href: `/?categoryID=${cat.id}`, // Filter by category
                id: cat.id
            }))
        }
    ]

    return (
        <aside className="w-64 hidden xl:block fixed left-0 top-16 bottom-0 overflow-y-auto px-4 py-6 border-r border-gray-100 dark:border-gray-800 bg-white/50 dark:bg-gray-900/50 backdrop-blur-sm custom-scrollbar z-20">
            <nav className="space-y-8">
                {menuGroups.map((group, groupIndex) => (
                    <div key={groupIndex}>
                        <h3 className="px-3 text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
                            {group.label}
                        </h3>
                        <div className="space-y-1">
                            {group.items.map((item, itemIndex) => {
                                // Determine active state:
                                // If item.href is exactly "/", active if pathname is "/" and no categoryID.
                                // If item has id (category), active if categoryID matches.
                                let isActive = false
                                if (item.href === "/") {
                                    isActive = pathname === "/" && !currentCategoryId
                                } else if (item.id) {
                                    isActive = currentCategoryId === item.id
                                } else {
                                    isActive = pathname === item.href
                                }

                                const Icon = item.icon
                                return (
                                    <Link
                                        key={itemIndex}
                                        href={item.href}
                                        className={`
                                            flex items-center gap-3 px-3 py-2 text-sm font-medium rounded-md transition-colors
                                            ${isActive
                                                ? "bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100"
                                                : "text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-800/50 hover:text-gray-900 dark:hover:text-gray-200"
                                            }
                                        `}
                                    >
                                        <Icon className={`w-4 h-4`} />
                                        {item.label}
                                    </Link>
                                )
                            })}
                        </div>
                    </div>
                ))}
            </nav>
        </aside>
    )
}
