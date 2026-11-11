# 组件库边界改造（architecture）

## 改造前

```
web/packages/components/
  src/components/...        # 55 个组件
  src/components/icon/      # Icon 组件 + builtin-icons + cloudreve-icons + icon-registry（图标数据）
  src/hooks/                # 3 个通用 hooks
  src/layouts/              # 4 个布局
  src/styles/               # tokens / variables / create-theme / theme-provider / global / index
  src/utils/                # cn
  src/index.ts              # 导出 components + hooks + layouts + styles + utils
  package.json              # files: dist + src/styles/tokens.ts, 依赖 fontsource/shared 等
```

## 改造后

| 包 | 路径 | 内容 | 依赖 |
| --- | --- | --- | --- |
| @pudans/components | web/packages/components | 55 个纯组件（tsx + 模块 CSS + barrel），Icon 渲染组件保留 | theme/icons/utils + react/MUI/Emotion |
| @pudans/theme | web/packages/theme | tokens.ts、variables.css、create-theme.ts、theme-provider.tsx、global.css、index.css/exports | react/MUI/Emotion/fontsource |
| @pudans/icons | web/packages/icons | builtin-icons、cloudreve-icons(251)、icon-registry | @iconify/react |
| @pudans/hooks | web/packages/hooks | use-delayed-hover、use-overflow、use-media-session | react |
| @pudans/utils | web/packages/utils | 原 @pudans/shared（constants/i18n helper/types/utils） | dayjs/i18next |
| @pudans/layouts | web/packages/layouts | app-shell、sidebar-layout、page-layout、page-container | components/theme/utils + react/MUI |

## 调用方修复

- 全仓 import 由 `@pudans/shared` → `@pudans/utils`。
- 布局符号（AppShell/PageLayout/PageContainer/SideNavItem/布局类型）→ `@pudans/layouts`。
- 主题符号（PudansThemeProvider/usePudansTheme/tokens/accentPresets/createPudansTheme）→ `@pudans/theme`。
- 图标集合/注册表 → `@pudans/icons`；Icon 组件仍在 `@pudans/components`。
- 各 app main 改为：`import "@pudans/theme/style.css"`（字体/CSS 变量/全局样式）；组件样式随源码 alias 自动注入。
- 根 vite alias / tsconfig paths / workspace 依赖同步更新。

## 边界门禁

- `node scripts/check-component-boundary.mjs`：组件包目录/文件白名单、禁止 app/路由/store/API/i18n 依赖、禁止跨包深层导入。
- `node scripts/check-exports.mjs`：组件 barrel 只允许 `./components`，禁止 hooks/layouts/styles/utils 目录与导出；主题/布局/hooks/icons/utils 入口存在性检查。
- `node scripts/check-cycles.mjs`、`node scripts/audit-design-tokens.mjs --strict` 继续作为质量门禁。
