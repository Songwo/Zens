"use client"

import { useState, useEffect } from "react"
import { useRouter } from "next/navigation"
import Navbar from "@/components/navbar"
import { Button } from "@/components/ui/button"
import api from "@/lib/api"
import toast from "react-hot-toast"
import { Loader2, ImagePlus, X, Tag as TagIcon } from "lucide-react"
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import TextareaAutosize from 'react-textarea-autosize'
import { motion } from "framer-motion"

interface Category {
    id: string
    name: string
    code: string
}

export default function CreatePostPage() {
    const router = useRouter()

    // 表单状态
    const [title, setTitle] = useState("")
    const [content, setContent] = useState("")
    const [categoryId, setCategoryId] = useState("")
    const [tags, setTags] = useState<string[]>([])
    const [tagInput, setTagInput] = useState("")
    const [isAnonymous, setIsAnonymous] = useState(false)

    // UI状态
    const [categories, setCategories] = useState<Category[]>([])
    const [isPreview, setIsPreview] = useState(false)
    const [submitting, setSubmitting] = useState(false)

    // 检查登录状态
    useEffect(() => {
        const token = localStorage.getItem("access_token")
        if (!token) {
            toast.error("请先登录后再发帖")
            router.push("/auth/login")
            return
        }
    }, [])

    // 加载分类
    useEffect(() => {
        api.get("/sys-category/list").then((res: any) => {
            if (res.code === 2000) {
                setCategories(res.data)
                if (res.data.length > 0) {
                    setCategoryId(res.data[0].id)
                }
            }
        })
    }, [])

    // 处理标签输入
    const handleTagKeyDown = (e: React.KeyboardEvent) => {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault()
            const newTag = tagInput.trim().replace(/^#/, '')
            if (newTag && !tags.includes(newTag)) {
                if (tags.length >= 5) {
                    toast.error("最多只能添加5个标签")
                    return
                }
                setTags([...tags, newTag])
            }
            setTagInput("")
        } else if (e.key === 'Backspace' && !tagInput && tags.length > 0) {
            setTags(tags.slice(0, -1))
        }
    }

    const removeTag = (tagToRemove: string) => {
        setTags(tags.filter(t => t !== tagToRemove))
    }

    // 提交发布
    const handleSubmit = async () => {
        if (title.length < 5) {
            toast.error("标题至少需要5个字符")
            return
        }
        if (content.length < 30) {
            toast.error("正文内容至少需要30个字符")
            return
        }
        if (!categoryId) {
            toast.error("请选择分类")
            return
        }

        try {
            setSubmitting(true)
            // 构造标签字符串 "#Tag1 #Tag2"
            const tagsStr = tags.length > 0 ? tags.map(t => `#${t}`).join(' ') : ""

            if (!tagsStr) {
                toast.error("请至少添加一个标签")
                setSubmitting(false)
                return
            }

            const res: any = await api.post("/sys-post/create-post", {
                title,
                content,
                categoryID: categoryId, // 注意后端字段是 categoryID (大写ID)
                tags: tagsStr,
                isAnonymous: isAnonymous ? 1 : 0,
            })

            if (res.code === 2000) {
                toast.success("发布成功！审核通过后展示")
                router.push("/")
            } else {
                toast.error(res.message || "发布失败")
            }
        } catch (error) {
            console.error(error)
            toast.error("发布异常")
        } finally {
            setSubmitting(false)
        }
    }

    return (
        <div className="min-h-screen bg-gray-50/50 dark:bg-gray-900">
            <Navbar />

            <main className="container mx-auto px-4 pt-24 pb-12 max-w-4xl">
                <motion.div
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="bg-white dark:bg-gray-800 rounded-2xl shadow-sm border border-gray-100 dark:border-gray-800 overflow-hidden"
                >
                    {/* 顶部工具栏 */}
                    <div className="px-6 py-4 border-b border-gray-100 dark:border-gray-800 bg-gray-50/30 flex justify-between items-center">
                        <div className="flex items-center space-x-4">
                            <select
                                value={categoryId}
                                onChange={(e) => setCategoryId(e.target.value)}
                                className="px-3 py-1.5 rounded-lg border border-gray-200 bg-white text-sm focus:outline-none focus:ring-2 focus:ring-primary/20"
                            >
                                <option value="" disabled>选择分区</option>
                                {categories.map(c => (
                                    <option key={c.id} value={c.id}>{c.name}</option>
                                ))}
                            </select>

                            <div className="flex items-center space-x-2">
                                <label className="flex items-center space-x-2 text-sm text-gray-600 cursor-pointer select-none">
                                    <input
                                        type="checkbox"
                                        checked={isAnonymous}
                                        onChange={(e) => setIsAnonymous(e.target.checked)}
                                        className="rounded border-gray-300 text-primary focus:ring-primary/20 transition-all"
                                    />
                                    <span>匿名发布</span>
                                </label>
                            </div>
                        </div>

                        <div className="flex space-x-2">
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => setIsPreview(!isPreview)}
                                className={isPreview ? "bg-primary/10 text-primary" : "text-gray-500"}
                            >
                                {isPreview ? "编辑模式" : "预览效果"}
                            </Button>
                        </div>
                    </div>

                    {/* 编辑区域 */}
                    <div className="p-6 space-y-4">
                        {/* 标题 */}
                        <TextareaAutosize
                            placeholder="请输入标题..."
                            value={title}
                            onChange={(e) => setTitle(e.target.value)}
                            maxRows={2}
                            className="w-full text-2xl font-bold placeholder:text-gray-300 border-none resize-none focus:ring-0 bg-transparent p-0 leading-tight"
                        />

                        <div className="w-full h-px bg-gray-100 dark:bg-gray-800" />

                        {/* 正文 */}
                        <div className="min-h-[400px]">
                            {isPreview ? (
                                <div className="prose prose-sm dark:prose-invert max-w-none">
                                    <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                        {content || "*这里空空如也...*"}
                                    </ReactMarkdown>
                                </div>
                            ) : (
                                <TextareaAutosize
                                    placeholder="分享你的新鲜事... (支持 Markdown 语法)"
                                    value={content}
                                    onChange={(e) => setContent(e.target.value)}
                                    minRows={15}
                                    className="w-full text-base placeholder:text-gray-300 border-none resize-none focus:ring-0 bg-transparent p-0"
                                />
                            )}
                        </div>
                    </div>

                    {/* 底部 标签与提交 */}
                    <div className="px-6 py-4 bg-gray-50/50 border-t border-gray-100 dark:border-gray-800">
                        <div className="flex flex-col md:flex-row gap-4 justify-between items-start md:items-center">
                            {/* 标签输入 */}
                            <div className="flex-1 w-full md:w-auto">
                                <div className="flex flex-wrap items-center gap-2 p-2 bg-white border border-gray-200 rounded-lg focus-within:ring-2 focus-within:ring-primary/20 focus-within:border-primary/50 transition-all">
                                    <TagIcon className="w-4 h-4 text-gray-400 ml-1" />
                                    {tags.map(tag => (
                                        <span key={tag} className="flex items-center text-xs font-medium bg-blue-50 text-blue-600 px-2 py-0.5 rounded cursor-default animate-in fade-in zoom-in duration-200">
                                            #{tag}
                                            <button onClick={() => removeTag(tag)} className="ml-1 hover:text-blue-800"><X className="w-3 h-3" /></button>
                                        </span>
                                    ))}
                                    <input
                                        type="text"
                                        value={tagInput}
                                        onChange={(e) => setTagInput(e.target.value)}
                                        onKeyDown={handleTagKeyDown}
                                        placeholder={tags.length === 0 ? "输入标签，回车添加 (如: 考研 复习)..." : ""}
                                        className="flex-1 min-w-[120px] text-sm bg-transparent border-none focus:ring-0 outline-none placeholder:text-gray-400"
                                    />
                                </div>
                            </div>

                            <div className="flex items-center space-x-3 w-full md:w-auto justify-end">
                                <Button variant="outline" onClick={() => router.back()}>取消</Button>
                                <Button onClick={handleSubmit} disabled={submitting} className="min-w-[100px]">
                                    {submitting ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : null}
                                    发布
                                </Button>
                            </div>
                        </div>
                    </div>
                </motion.div>
            </main>
        </div>
    )
}
