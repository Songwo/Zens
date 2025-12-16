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
import { Loader2, Lock, User, GraduationCap } from "lucide-react"
import toast, { Toaster } from "react-hot-toast"
import api from "@/lib/api"
import React from "react" // Added React import for useEffect

export default function LoginPage() {
    const router = useRouter()
    const [loading, setLoading] = useState(false)

    // 图形验证码相关
    const [captchaUrl, setCaptchaUrl] = useState("")
    const [uuid, setUuid] = useState("")

    const [formData, setFormData] = useState({
        username: "",
        password: "",
        code: "" // 图形验证码
    })

    // 初始化或刷新图形验证码
    const refreshCaptcha = () => {
        // 生成一个随机UUID
        const newUuid = crypto.randomUUID();
        setUuid(newUuid);
        setCaptchaUrl(`/api/auth/captcha?uuid=${newUuid}&t=${Date.now()}`);
        setFormData(prev => ({ ...prev, code: "" }));
    }

    // 组件挂载时加载验证码
    React.useEffect(() => {
        refreshCaptcha();
    }, []);

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        setFormData({ ...formData, [e.target.name]: e.target.value })
    }

    const handleLogin = async (e: React.FormEvent) => {
        e.preventDefault()

        if (!formData.code) {
            toast.error("请输入图形验证码")
            return
        }

        try {
            setLoading(true)
            // 提交登录，包含 uuid 和 code
            const res: any = await api.post("/auth/login", {
                ...formData,
                uuid: uuid
            })

            if (res.code === 2000) {
                toast.success("欢迎回来！")
                localStorage.setItem("access_token", res.data.accessToken)
                localStorage.setItem("refresh_token", res.data.refreshToken)
                setTimeout(() => {
                    router.push("/")
                }, 1000)
            }

        } catch (error: any) {
            // 登录失败（包括验证码错误、账号锁定等），刷新验证码
            refreshCaptcha();
            // 这里不需要额外处理错误提示，api.ts 拦截器已经弹出了 toast（从后端 LoginException 消息中获取）
            // 如果是账号锁定，后端会返回 "账号已被锁定..."
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
                            <GraduationCap className="h-6 w-6 text-primary" />
                        </div>
                    </div>
                    <CardTitle className="text-2xl font-bold text-center tracking-tight">欢迎回来</CardTitle>
                    <CardDescription className="text-center">
                        登录校园脉搏，连接你我
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleLogin} className="space-y-4">
                        {/* 账号 */}
                        <div className="space-y-2">
                            <Label htmlFor="username">学号 / 邮箱</Label>
                            <div className="relative">
                                <User className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                                <Input
                                    id="username"
                                    name="username"
                                    placeholder="请输入学号或绑定邮箱"
                                    className="pl-9"
                                    value={formData.username}
                                    onChange={handleChange}
                                    required
                                />
                            </div>
                        </div>

                        {/* 密码 */}
                        <div className="space-y-2">
                            <div className="flex items-center justify-between">
                                <Label htmlFor="password">密码</Label>
                                <Link href="/auth/forgot-password" className="text-sm text-primary hover:underline">
                                    忘记密码?
                                </Link>
                            </div>
                            <div className="relative">
                                <Lock className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                                <Input
                                    id="password"
                                    name="password"
                                    type="password"
                                    placeholder="请输入密码"
                                    className="pl-9"
                                    value={formData.password}
                                    onChange={handleChange}
                                    required
                                />
                            </div>
                        </div>

                        {/* 图形验证码 */}
                        <div className="space-y-2">
                            <Label htmlFor="code">验证码</Label>
                            <div className="flex space-x-2">
                                <Input
                                    id="code"
                                    name="code"
                                    placeholder="请输入右侧字符"
                                    value={formData.code}
                                    onChange={handleChange}
                                    className="flex-1"
                                    required
                                />
                                <div
                                    className="w-28 h-9 bg-gray-100 rounded border cursor-pointer overflow-hidden relative"
                                    onClick={refreshCaptcha}
                                    title="点击刷新验证码"
                                >
                                    {captchaUrl ? (
                                        // eslint-disable-next-line @next/next/no-img-element
                                        <img
                                            src={captchaUrl}
                                            alt="验证码"
                                            className="w-full h-full object-fill"
                                        />
                                    ) : (
                                        <div className="w-full h-full flex items-center justify-center text-xs text-muted-foreground">
                                            加载中...
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>

                        <Button className="w-full" type="submit" disabled={loading}>
                            {loading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                            登录
                        </Button>
                    </form>
                </CardContent>
                <CardFooter className="flex justify-center border-t p-6">
                    <p className="text-sm text-muted-foreground">
                        还没有账号?{" "}
                        <Link href="/auth/register" className="font-medium text-primary hover:underline underline-offset-4">
                            立即注册
                        </Link>
                    </p>
                </CardFooter>
            </Card>
        </div>
    )
}
