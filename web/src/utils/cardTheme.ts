export interface CardThemeItem {
  key: string
  label: string
  background: string
  borderColor: string
}

export const CARD_THEME_OPTIONS: CardThemeItem[] = [
  {
    key: 'sunset',
    label: '暖阳',
    background: 'linear-gradient(135deg, #fff2db 0%, #ffe3b2 48%, #ffd39a 100%)',
    borderColor: '#f4c98d',
  },
  {
    key: 'ocean',
    label: '海蓝',
    background: 'linear-gradient(135deg, #e6f4ff 0%, #d5ecff 50%, #c3e3ff 100%)',
    borderColor: '#9fd0f7',
  },
  {
    key: 'forest',
    label: '森林',
    background: 'linear-gradient(135deg, #eaf8ef 0%, #d7f2e3 48%, #c4eccc 100%)',
    borderColor: '#9ed3af',
  },
  {
    key: 'aurora',
    label: '极光',
    background: 'linear-gradient(135deg, #eef0ff 0%, #e5ddff 52%, #d7cdf9 100%)',
    borderColor: '#beb3ef',
  },
  {
    key: 'graphite',
    label: '石墨',
    background: 'linear-gradient(135deg, #f2f4f7 0%, #e9edf2 52%, #dde3eb 100%)',
    borderColor: '#c6d0dd',
  },
  {
    key: 'peach',
    label: '蜜桃',
    background: 'linear-gradient(135deg, #fff1ee 0%, #ffe4df 48%, #ffd6ce 100%)',
    borderColor: '#f5beb4',
  },
  {
    key: 'violet',
    label: '紫雾',
    background: 'linear-gradient(135deg, #f4ecff 0%, #ebddff 48%, #dfcdfb 100%)',
    borderColor: '#cbb0ef',
  },
]

const themeMap = CARD_THEME_OPTIONS.reduce<Record<string, CardThemeItem>>((acc, item) => {
  acc[item.key] = item
  return acc
}, {})

const DEFAULT_CARD_THEME: CardThemeItem = {
  key: 'sunset',
  label: '暖阳',
  background: 'linear-gradient(135deg, #fff2db 0%, #ffe3b2 48%, #ffd39a 100%)',
  borderColor: '#f4c98d',
}

export function getCardThemePalette(themeKey?: string, fallback: string = 'sunset'): CardThemeItem {
  const normalized = String(themeKey || '').trim().toLowerCase()
  return themeMap[normalized] || themeMap[fallback] || DEFAULT_CARD_THEME
}
