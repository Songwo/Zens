"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
    Card,
    CardContent,
    CardDescription,
    CardFooter,
    CardHeader,
    CardTitle,
} from "@/components/ui/card"
import { Loader2, Mail, Lock, User, GraduationCap } from "lucide-react"
import toast, { Toaster } from "react-hot-toast"
import api from "@/lib/api"

export default function RegisterPage() {
    const router = useRouter()
    const [loading, setLoading] = useState(false)
    const [sendingCode, setSendingCode] = useState(false)
    const [countdown, setCountdown] = useState(0)

    const [formData, setFormData] = useState({
        username: "",
        password: "",
        email: "",
        code: "",
        nickname: "",
        school: "默认大学", // 暂时硬编码或留空
        major: "计算机科学与技术",
        role: 1, // 默认为学生
        gender: 1, // 默认为男
        grade: 2023
    })

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setFormData({ ...formData, [e.target.name]: e.target.value })
    }

    // 发送验证码
    const handleSendCode = async () => {
        if (!formData.email) {
            toast.error("请输入邮箱地址")
            return
        }

        // 简单的邮箱格式验证
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
            toast.error("邮箱格式不正确")
            return
        }

        try {
            setSendingCode(true)
            const res: any = await api.post("/auth/send-code", { email: formData.email })
            if (res.code === 2000) {
                toast.success("验证码已发送至您的邮箱")

                // 开始倒计时 60s
                setCountdown(60)
                const timer = setInterval(() => {
                    setCountdown((prev) => {
                        if (prev <= 1) {
                            clearInterval(timer)
                            return 0
                        }
                        return prev - 1
                    })
                }, 1000)
            } else {
                toast.error(res.message || "发送验证码失败")
            }

        } catch (error: any) {
            // 错误已由 api 拦截器处理或自行处理
            console.error(error)
        } finally {
            setSendingCode(false)
        }
    }

    // 提交注册
    const handleRegister = async (e: React.FormEvent) => {
        e.preventDefault()

        if (!formData.code) {
            toast.error("请输入验证码")
            return
        }

        try {
            setLoading(true)
            const res: any = await api.post("/auth/register", formData)

            if (res.code === 2000) {
                toast.success("注册成功，请登录")
                // 延迟跳转
                setTimeout(() => {
                    router.push("/auth/login")
                }, 1500)
            } else {
                toast.error(res.message || "注册失败")
            }
        } catch (error: any) {
            console.error(error)
            // 错误提示已由 API 拦截器处理
        } finally {
            setLoading(false)
        }
    }

    return (
        <div className="flex min-h-screen items-center justify-center bg-gray-50/50 px-4 py-12 sm:px-6 lg:px-8">
            <Toaster position="top-center" />
            <Card className="w-full max-w-md shadow-lg border-0 sm:border">
                <CardHeader className="space-y-1">
                    <div className="flex justify-center mb-4">
                        <div className="rounded-full bg-primary/10 p-3">
                            {/* Logo placeholder */}
                            <GraduationCap className="h-6 w-6 text-primary" />
                        </div>
                    </div>
                    <CardTitle className="text-2xl font-bold text-center tracking-tight">创建账号</CardTitle>
                    <CardDescription className="text-center">
                        加入校园脉搏，发现校园精彩生活
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleRegister} className="space-y-4">
                        {/* 学号/用户名 */}
                        <div className="space-y-2">
                            <Label htmlFor="username">学号</Label>
                            <div className="relative">
                                <User className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                                <Input
                                    id="username"
                                    name="username"
                                    placeholder="请输入学号"
                                    className="pl-9"
                                    value={formData.username}
                                    onChange={handleChange}
                                    required
                                />
                            </div>
                        </div>

                        {/* 昵称 */}
                        <div className="space-y-2">
                            <Label htmlFor="nickname">昵称</Label>
                            <Input
                                id="nickname"
                                name="nickname"
                                placeholder="怎么称呼您？"
                                value={formData.nickname}
                                onChange={handleChange}
                                required
                            />
                        </div>

                        {/* 邮箱 */}
                        <div className="space-y-2">
                            <Label htmlFor="email">邮箱</Label>
                            <div className="flex space-x-2">
                                <div className="relative flex-1">
                                    <Mail className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                                    <Input
                                        id="email"
                                        name="email"
                                        type="email"
                                        placeholder="name@example.com"
                                        className="pl-9"
                                        value={formData.email}
                                        onChange={handleChange}
                                        required
                                    />
                                </div>
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={handleSendCode}
                                    disabled={sendingCode || countdown > 0}
                                    className="w-32"
                                >
                                    {sendingCode ? (
                                        <Loader2 className="h-4 w-4 animate-spin" />
                                    ) : countdown > 0 ? (
                                        `${countdown}s`
                                    ) : (
                                        "发送验证码"
                                    )}
                                </Button>
                            </div>
                        </div>

                        {/* 验证码 */}
                        <div className="space-y-2">
                            <Label htmlFor="code">验证码</Label>
                            <Input
                                id="code"
                                name="code"
                                placeholder="请输入6位验证码"
                                value={formData.code}
                                onChange={handleChange}
                                required
                            />
                        </div>

                        {/* 密码 */}
                        <div className="space-y-2">
                            <Label htmlFor="password">密码</Label>
                            <div className="relative">
                                <Lock className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                                <Input
                                    id="password"
                                    name="password"
                                    type="password"
                                    placeholder="设置登录密码"
                                    className="pl-9"
                                    value={formData.password}
                                    onChange={handleChange}
                                    required
                                />
                            </div>
                        </div>

                        <Button className="w-full" type="submit" disabled={loading}>
                            {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                            立即注册
                        </Button>
                    </form>
                </CardContent>
                <CardFooter className="flex justify-center border-t p-6">
                    <p className="text-sm text-muted-foreground">
                        已有账号?{" "}
                        <Link href="/auth/login" className="font-medium text-primary hover:underline underline-offset-4">
                            直接登录
                        </Link>
                    </p>
                </CardFooter>
            </Card>
        </div>
    )
}
