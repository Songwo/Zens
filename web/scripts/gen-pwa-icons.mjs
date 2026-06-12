// 从 public/logo.png 生成 PWA 图标集。需要本机装有 sharp（devDependency）。
// 运行：node scripts/gen-pwa-icons.mjs
import sharp from 'sharp'
import { mkdir } from 'node:fs/promises'

const src = 'public/logo.png'
const outDir = 'public/icons'
await mkdir(outDir, { recursive: true })

// 普通图标：等比缩放，透明背景
const plain = [
  { size: 192, name: 'pwa-192x192.png' },
  { size: 512, name: 'pwa-512x512.png' },
]
for (const { size, name } of plain) {
  await sharp(src)
    .resize(size, size, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toFile(`${outDir}/${name}`)
  console.log('wrote', name)
}

// maskable：logo 缩到 ~70%，居中放在品牌色底板上，留足安全区（避免被系统圆角裁掉）
const maskSize = 512
const inner = Math.round(maskSize * 0.7)
const logo = await sharp(src)
  .resize(inner, inner, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
  .png()
  .toBuffer()
const pad = Math.round((maskSize - inner) / 2)
await sharp({ create: { width: maskSize, height: maskSize, channels: 4, background: { r: 244, g: 180, b: 0, alpha: 1 } } })
  .composite([{ input: logo, top: pad, left: pad }])
  .png()
  .toFile(`${outDir}/pwa-maskable-512x512.png`)
console.log('wrote pwa-maskable-512x512.png')

// apple-touch-icon：iOS 不支持透明，用白底 180
const appleSize = 180
const appleInner = Math.round(appleSize * 0.82)
const appleLogo = await sharp(src)
  .resize(appleInner, appleInner, { fit: 'contain', background: { r: 255, g: 255, b: 255, alpha: 1 } })
  .png()
  .toBuffer()
const applePad = Math.round((appleSize - appleInner) / 2)
await sharp({ create: { width: appleSize, height: appleSize, channels: 4, background: { r: 255, g: 255, b: 255, alpha: 1 } } })
  .composite([{ input: appleLogo, top: applePad, left: applePad }])
  .png()
  .toFile(`${outDir}/apple-touch-icon.png`)
console.log('wrote apple-touch-icon.png')
