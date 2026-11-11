# 第二轮重构交付报告：组件库边界 + 旧项目 1:1 视觉重建

> 目标仓库：/Users/byte/Global/Project/pudans。旧项目：/Users/byte/Global/Reference/cloudreve/frontend。
> 生成时间：2026-11-16。上一轮基线提交 fd45a85。

## 1. 组件库边界改造结果

### 1.1 移出的内容与新位置

| 原位置 | 内容 | 新位置 | 新包 |
| --- | --- | --- | --- |
| packages/components/src/hooks | use-delayed-hover / use-overflow / use-media-session | packages/hooks/src | @pudans/hooks |
| packages/components/src/utils | cn | packages/utils/src/utils（并入 classnames 的 cn） | @pudans/utils |
| packages/components/src/layouts | app-shell / side-nav-item / sidebar-layout / page-layout / page-container | packages/layouts/src | @pudans/layouts |
| packages/components/src/styles | tokens.ts / variables.css / create-theme.ts / theme-provider.tsx / global.css / index.css | packages/theme/src | @pudans/theme |
| packages/components/src/components/icon（数据部分） | builtin-icons / cloudreve-icons(251) / icon-registry | packages/icons/src | @pudans/icons |
| components/settings-panel/use-dirty-state.ts | 组件私有 hook 文件 | 内联进 settings-panel.tsx | — |
| 原 @pudans/shared | constants/i18n helper/types/utils | packages/utils（整包改名） | @pudans/utils |

组件库入口 src/index.ts 现在只 export * from ./components；src/components/index.ts 只聚合 55 个组件目录。组件库仅依赖 @pudans/theme、@pudans/icons、@pudans/utils 与 react/MUI/Emotion peer，不含任何 @web/*、react-router、业务 store/API、i18next、hooks/layouts 依赖（check-component-boundary 自动扫描）。

### 1.2 组件库现在包含

    web/packages/components/
      README.md                 # 边界说明
      package.json / tsconfig.json / vite.config.ts
      src/index.ts              # 只导出 components
      src/components/index.ts   # 55 个组件 barrel
      src/components/<55 dirs>/ # *.tsx + 组件专属 *.module.css（多数已改为 MUI sx）+ index.ts

依赖检查：无循环依赖（194 模块）、无跨包 deep import、无非组件导出（check-exports 强制 root barrel 只允许 ./components）。

### 1.3 调用方修复

- 全仓 @pudans/shared → @pudans/utils。
- 布局符号（AppShell/PageLayout/PageContainer/SideNavItem/布局类型）→ @pudans/layouts。
- 主题符号（PudansThemeProvider/usePudansTheme/tokens/accentPresets/createPudansTheme）→ @pudans/theme。
- 图标集合/注册 API → @pudans/icons；Icon 组件仍在 @pudans/components。
- 四个 app main 改为 import "@pudans/theme/style.css"；组件 CSS 随源码 alias 注入。根 vite alias、tsconfig paths、workspace 依赖、ui-plugin-bundler-kit externals 全部同步。
- 新增 web/app/lab 组件画廊（仅内部验收用，MPA 独立入口）。

## 2. 已有组件重写清单（55/55）

全部 55 个组件按旧项目源码重写；逐组件布局/间距/字体/颜色/圆角/阴影/动效/状态对照与剩余差异见：
- .dsh/refactor/rewrite-reports/group-forms.md（button/input/select/checkbox/switch/form-field/size-input/otp-input/date-picker/file-drop-zone）
- .dsh/refactor/rewrite-reports/group-overlays.md（dialog/confirm/select/error-detail/dropdown/tooltip/toast/accordion/tabs/responsive-tabs）
- .dsh/refactor/rewrite-reports/group-data.md（table/pagination/card/stat-card/badge/alert/avatar/skeleton/progress/progress-steps/chart/graph）
- .dsh/refactor/rewrite-reports/group-content.md（breadcrumbs/description-list/empty-state/error-state/code/highlight-text/relative-time/icon/motion/animate-height/split-pane/search-input/settings-panel）
- .dsh/refactor/rewrite-reports/group-brand.md（logo/powered-by/language-switcher/theme-switch/user/oauth-app-card/captcha/image-cropper/file-type-icon）
- .dsh/refactor/rewrite-reports/group-layouts.md（app-shell/sidebar-layout/page-layout/page-container）

关键视觉基线（旧项目 = MUI 6.4.7 默认 + App.tsx overrides）：
- Button：MUI Button（padding 6/16、radius 12、disableElevation、textTransform none、MUI hover/ripple/focus）。
- Input/Select：MUI TextField/Select（DenseFilled 变体、float label、radius 12）。
- Dialog：MUI Dialog（Grow 进入/离开、radius 12、DialogContent paddingTop 0、可拖拽、loading 禁关）。
- Menu/Dropdown：radius 8、elevation 3、item radius 8 margin 4/padding 8。
- Table：MUI Table size=small + Paper 1px divider/radius 12/无阴影/action.hover 行 hover。
- Alert/Tooltip/Toast：MUI Alert 默认、Tooltip 无箭头 enterDelay 500、notistack 左下角/radius 12/palette 底色。
- EmptyState：复刻 Common/Nothing（100px 图标、50*size auto margin、h5 24px、action.disabled 颜色）。
- Tabs/ResponsiveTabs：复刻 StyledTabs（36 高、gap 24、无 pill/indicator 逻辑）。
- 进度条：8px/radius 5/grey 轨道 + 40px 双环；ProgressSteps 改为 MUI Stepper。
- Chart：recharts 旧默认（300px、3-3 divider 网格、主题轴/Tooltip）；Graph 旧项目无对应，按旧主题语言重建并保留能力。
- 所有组件删除自研视觉 CSS 或仅保留结构样式；颜色/间距/字号/圆角/阴影/动效来自 @pudans/theme（MUI theme 或 CSS 变量），硬编码审计 0 findings。

## 3. 布局一致性验收

- 逐组件结论已回填 .dsh/refactor/layout-diff.md（状态列：已完成；附报告与截图位置）。
- 布局包：app-shell 复刻 NavBarFrame（MUI AppBar+Drawer+Toolbar、抽屉 240、header 64、抽屉/主列 margin 动效 225/195）；page-container 复刻 RadiusFrame（radius 12/1px divider/py 32/移动端直角）；page-layout 复刻 PageHeader（h4 34/600、mb 32、刷新按钮）；sidebar-layout 为无顶栏轻量变体。
- 页面级：auth 已用新组件库并截图复核（screenshots/auth-light.png、auth-dark.png，1440x900）；uc/console/setup 通过类型检查与构建，消费包入口；页面级像素对照受限于旧应用无法启动（见第 5 节）。
- 组件级视觉基线：screenshots/lab-light.png、lab-dark.png（1440x2800 全量画廊，覆盖 50+ 组件状态）。

## 4. 质量门禁命令与结果

在 /Users/byte/Global/Project/pudans/web 执行：

| 门禁 | 命令 | 结果 |
| --- | --- | --- |
| 应用类型检查 | npx tsc -p tsconfig.json --noEmit | PASS（exit 0） |
| 全部 workspace 包类型检查 | npx pnpm -r typecheck | PASS（8/8 Done） |
| Lint | npx eslint . | 0 errors（仅既有 react-refresh warning） |
| 单元/一致性测试 | node --test tests/ | 22/22 pass |
| 组件库边界 | node scripts/check-component-boundary.mjs | PASS（纯组件包） |
| 导出检查 | node scripts/check-exports.mjs | PASS（55 目录 / 222 命名导出 / 纯 components barrel） |
| 循环依赖 | node scripts/check-cycles.mjs | PASS（194 模块，0 环） |
| 硬编码审计 | node scripts/audit-design-tokens.mjs --strict | 0 findings |
| 包构建 | npx pnpm -r build | PASS（9 个包 Done） |
| 应用构建 | npx pnpm build | PASS（setup/auth/console/uc/lab） |
| 视觉/布局 | lab 画廊 + auth 截图；逐组件报告 | 见第 2/3 节与布局清单 |
| 一键复验 | npx pnpm verify（typecheck + cycles + exports + boundary + test:packages + test + lint + build） | 已写入 package.json |

## 5. 未完成项与阻断原因

无静默遗漏；以下为明确记录的限制/差异：

1. 旧项目无法直接启动（无 node_modules、无后端服务），无法做旧/新双跑像素 diff。1:1 基准取旧源码显式数值（MUI 6.4.7 源码 + App.tsx overrides + theme_options），结构化提取见 .dsh/refactor/legacy-specs/；新视觉基线用 lab/auth 截图记录。
2. 页面级截图仅 auth 可在无后端情况下渲染；uc/console/setup 需要登录/配置 API，未能本地截图，以组件级画廊 + 类型/构建门禁验收。后续接入后端后可补页面截图回归。
3. 组件库“纯组件”边界下未纳入的旧项目强业务 UI（已在组报告注明）：级联子菜单、错误详情里的文件徽标、loading/file 自定义 snackbar 变体、ImageEditor 全屏编辑、cloudreve 上传协议组件。它们留在应用层或后续业务包，不作为公共组件。
4. graph 旧项目无对应组件（组报告与 layout-diff 已标注），按旧主题视觉语言重建。
5. 旧项目动态主题（后端 siteConfig.themes JSON）可覆盖 palette/typography；本实现以默认值 + accent 注入为准，运行时自定义主题能力保留在 @pudans/theme。

## 6. 回滚方式与关键提交

| 提交 | 内容 |
| --- | --- |
| fd45a85 | 上一轮迁移基线（工作树干净） |
| 本轮提交 | 组件库边界改造 + 55 组件重写 + 主题复刻 + 文档/截图（见 git log 最新提交，tag: refactor-2026-11-16） |

回滚：

    cd /Users/byte/Global/Project/pudans
    git reset --hard fd45a85     # 回到本轮改造前
    git revert <本轮提交hash>     # 保留历史的反向提交

本轮未修改后端与 Gradle 构建入口；所有改动在 web/ 与 .dsh/。
