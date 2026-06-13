export interface CoverConfig {
  fit: 'cover' | 'contain'
  x: number
  y: number
  height: number
}

export const DEFAULT_COVER_CONFIG: CoverConfig = { fit: 'cover', x: 50, y: 50, height: 320 }

const clamp = (n: number, min: number, max: number, fallback: number) =>
  Number.isFinite(n) ? Math.max(min, Math.min(max, n)) : fallback

export function parseCoverConfig(raw?: string | null): CoverConfig {
  if (!raw) return { ...DEFAULT_COVER_CONFIG }
  try {
    const o = JSON.parse(raw) as Partial<CoverConfig>
    return {
      fit: o.fit === 'contain' ? 'contain' : 'cover',
      x: clamp(Number(o.x), 0, 100, 50),
      y: clamp(Number(o.y), 0, 100, 50),
      height: clamp(Number(o.height), 120, 600, 320),
    }
  } catch {
    return { ...DEFAULT_COVER_CONFIG }
  }
}
