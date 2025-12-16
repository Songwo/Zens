"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { usePathname } from "next/navigation"
import { Button } from "@/components/ui/button"
import {
    Bell,
    Search,
    LogOut,
    User,
    Settings,
    PenSquare,
    Menu,
    Plus
} from "lucide-react"
import { useState, useEffect } from "react"
import { motion, AnimatePresence } from "framer-motion"

export default function Navbar() {
    const router = useRouter()
    const pathname = usePathname()
    const [isScrolled, setIsScrolled] = useState(false)
    const [isLoggedIn, setIsLoggedIn] = useState(false)
    const [showUserMenu, setShowUserMenu] = useState(false)

    // 监听滚动以实现磨砂效果切换
    useEffect(() => {
        const handleScroll = () => {
            setIsScrolled(window.scrollY > 10)
        }
        window.addEventListener("scroll", handleScroll)
        // 检查登录状态
        setIsLoggedIn(!!localStorage.getItem("access_token"))
        return () => window.removeEventListener("scroll", handleScroll)
    }, [])

    const handleLogout = () => {
        localStorage.removeItem("access_token")
        localStorage.removeItem("refresh_token")
        setIsLoggedIn(false)
        router.push("/auth/login")
    }

    return (
        <header
            className={`fixed top-0 left-0 right-0 z-50 transition-all duration-300 ${isScrolled
                ? "bg-white/80 dark:bg-gray-900/80 backdrop-blur-md shadow-sm border-b border-gray-100 dark:border-gray-800"
                : "bg-transparent border-b border-transparent"
                }`}
        >
            <div className="container mx-auto px-4 h-16 flex items-center justify-between">
                {/* Logo */}
                <Link href="/" className="flex items-center space-x-2 group">
                    <div className="w-8 h-8 bg-primary rounded-lg flex items-center justify-center transform transition-transform group-hover:rotate-12">
                        <span className="text-white font-bold text-xl">C</span>
                    </div>
                    <span className="font-bold text-xl tracking-tight text-gray-900 dark:text-white transition-colors">
                        Campus Pulse
                    </span>
                </Link>

                <nav className="hidden md:flex items-center space-x-1">
                    {[
                        { name: "首页", path: "/" },
                        { name: "趋势分析", path: "/trends" },
                        { name: "话题", path: "/topics" },
                    ].map((item) => (
                        <Link
                            key={item.path}
                            href={item.path}
                            className={`px-4 py-2 rounded-md text-sm font-medium transition-colors ${pathname === item.path
                                ? "text-primary bg-primary/5"
                                : "text-gray-600 hover:text-primary hover:bg-gray-50"
                                }`}
                        >
                            {item.name}
                        </Link>
                    ))}
                </nav>

                {/* Right Actions */}
                <div className="flex items-center space-x-3">
                    {/* Search Trigger */}
                    <Button variant="ghost" size="icon" className="text-gray-500 hover:text-primary">
                        <Search className="w-5 h-5" />
                    </Button>

                    {isLoggedIn ? (
                        <>
                            <Button variant="ghost" size="icon" className="text-gray-500 hover:text-primary relative">
                                <Bell className="w-5 h-5" />
                                {/* Red dot for notification */}
                                <span className="absolute top-2 right-2 w-2 h-2 bg-red-500 rounded-full border border-white"></span>
                            </Button>

                            <Button
                                size="sm"
                                className="hidden md:flex rounded-full bg-primary hover:bg-primary/90 text-white shadow-sm shadow-primary/20 transition-all hover:scale-105 active:scale-95"
                                onClick={() => router.push('/post/create')}
                            >
                                <Plus className="w-4 h-4 mr-1.5" />
                                发帖
                            </Button>

                            {/* User Menu */}
                            <div className="relative">
                                <Button
                                    variant="ghost"
                                    size="icon"
                                    className="rounded-full overflow-hidden border border-gray-200"
                                    onClick={() => setShowUserMenu(!showUserMenu)}
                                >
                                    <User className="w-5 h-5 text-gray-600" />
                                </Button>

                                <AnimatePresence>
                                    {showUserMenu && (
                                        <motion.div
                                            initial={{ opacity: 0, y: 10, scale: 0.95 }}
                                            animate={{ opacity: 1, y: 0, scale: 1 }}
                                            exit={{ opacity: 0, y: 10, scale: 0.95 }}
                                            transition={{ duration: 0.2 }}
                                            className="absolute right-0 mt-2 w-48 bg-white rounded-lg shadow-xl border border-gray-100 py-1"
                                        >
                                            <Link href="/profile" className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                                                <User className="w-4 h-4 mr-2" /> 个人中心
                                            </Link>
                                            <Link href="/settings" className="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-50">
                                                <Settings className="w-4 h-4 mr-2" /> 设置
                                            </Link>
                                            <div className="border-t my-1"></div>
                                            <button
                                                onClick={handleLogout}
                                                className="w-full flex items-center px-4 py-2 text-sm text-red-600 hover:bg-red-50"
                                            >
                                                <LogOut className="w-4 h-4 mr-2" /> 退出登录
                                            </button>
                                        </motion.div>
                                    )}
                                </AnimatePresence>
                            </div>
                        </>
                    ) : (
                        <div className="flex items-center space-x-2">
                            <Link href="/auth/login">
                                <Button variant="ghost" size="sm">登录</Button>
                            </Link>
                            <Link href="/auth/register">
                                <Button size="sm">注册</Button>
                            </Link>
                        </div>
                    )}
                </div>
            </div>
        </header>
    )
}
