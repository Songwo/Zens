# Zens Nav

Zens 开发者社区导航子站，使用 React + TypeScript + Tailwind CSS + lucide-react 实现。

## 启动

```powershell
npm install
npm run dev
```

默认 Vite 地址：

```text
http://localhost:5173
```

## 构建

```powershell
npm run build
```

## 修改入口

导航数据集中在 `src/data/navItems.ts` 的 `navItems` 数组里。字段结构：

```ts
type NavItem = {
  id: string
  title: string
  description: string
  href: string
  category: string
  icon: string
  status?: "normal" | "maintenance" | "coming-soon" | "beta"
  tag?: string
  external?: boolean
}
```

`icon` 使用 lucide-react 图标名称，映射维护在 `src/lib/icons.ts`。
