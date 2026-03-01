# STYLE_CLEANUP.md

## Removed Global / High-Risk CSS Rules

### 1. `src/styles/global.css`
**Added Protection:**
- Added `width: 1em !important; height: 1em !important;` to `.el-icon` and `.el-icon svg`.
- Added `max-width: 100%; max-height: 100%;` to `svg`.
**Reason:** 
Without these, SVGs (like Element Plus icons) can inherit surrounding widths or default to their raw viewBox sizes, resulting in giant icons throughout the interface, completely breaking the layout. 

### 2. `src/main.ts`
**Fix:** 
- Added `import 'element-plus/dist/index.css'`.
**Reason:**
Without the core Element Plus structural CSS, components (like menus, drop-downs, rows, columns, buttons) have no base sizing or structure, which makes everything misaligned and text-based layouts collapse into giant unstyled flows.

### 3. Layout Rewrite (`src/layouts/MainLayout.vue`)
**Fix:**
- Stripped ambiguous flex resets and removed leftover Tailwind class-like structure mappings that didn't work.
- Introduced strict CSS Grid constraints (`grid-template-columns: 240px minmax(0, 1fr) 320px;`) under a main `max-width: 1280px` centered shell.
**Reason:** 
The previous setup relied on flex layouts that broke because Element Plus columns components weren't loaded correctly, or the wrapper lacked hard restraints, causing the "three columns" to collapse vertically or blow past the 100vw limit. Now it's a stable, mathematically guaranteed grid layout.

## Next Steps
We are also auditing unscoped `<style>` blocks in components like `App.vue` or pages like `ComposePage.vue` that might be leaking sizing rules globally, and converting them to `<style scoped>`.
