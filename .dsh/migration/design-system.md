# pudans web 设计系统规范（Design System）

> 生成于 2026-11-16。目标仓库：/Users/byte/Global/Project/pudans。源项目：
> /Users/byte/Global/Reference/cloudreve/frontend（Cloudreve 4.0.0-next 前端）。

## 1. 单一事实来源

| 层 | 文件 | 职责 |
| --- | --- | --- |
| TS token | web/packages/components/src/styles/tokens.ts | 颜色/字号/间距/圆角/阴影/动效/z-index/状态等唯一来源 |
| CSS 变量 | web/packages/components/src/styles/variables.css | 与 tokens.ts 一一镜像，供组件 CSS Modules 使用 |
| MUI 主题 | web/packages/components/src/styles/create-theme.ts | 由 tokens.ts 推导 palette/typography/shape/transitions/zIndex，禁止另写色板 |
| 全局底座 | web/packages/components/src/styles/global.css | reset、字体、滚动条、focus-visible、动效工具类 |
| 主题运行时 | theme-provider.tsx | html[data-theme] + .dark + accent 注入 + localStorage |

规则：任何组件样式只允许使用 <code>var(--pd-...)</code> 或 MUI theme；直接写颜色、字体族、
字号、圆角、阴影、动画时长、缓动、z-index 视为缺陷。审计命令：

    cd web && node scripts/audit-design-tokens.mjs --strict

当前结果：0 findings（组件库 + 应用层全量扫描）。

## 2. 颜色

### 2.1 品牌与中性色阶

| 组 | token | 值（light） |
| --- | --- | --- |
| Brand | --pd-brand-50..900 | #e3f2fd / #bbdefb / #90caf9 / #64b5f6 / #42a5f5 / #2196f3 / #1976d2 / #1565c0 / #114a9e / #0d47a1 / #0a3a7a |
| Neutral | --pd-neutral-0..1000 | #ffffff → #121212 冷中性阶 |

### 2.2 语义色（light / dark 自动切换）

| 语义 | CSS 变量 | 用途 |
| --- | --- | --- |
| 画布 | --pd-bg-canvas | 页面底色 |
| 表面 | --pd-bg-surface / --pd-bg-elevated | 卡片、弹窗、浮层 |
| 弱表面 | --pd-bg-subtle / --pd-bg-muted | 输入框、分段控件、骨架 |
| 交互 | --pd-bg-hover / --pd-bg-active | hover / active 覆盖层 |
| 文本 | --pd-text-primary / secondary / tertiary / disabled / inverse | 四级文本 |
| 品牌 | --pd-brand-primary / hover / active / contrast / soft | 主操作、选中态、链接 |
| 边框 | --pd-border-subtle / default / strong | 分隔、描边、输入框 |
| 状态 | --pd-state-success / warning / danger / info + soft | 语义反馈 |
| 图表 | --pd-chart-1..6 | Recharts/Graph 系列色，亮暗双套 |

### 2.3 主题切换

- 由 PudansThemeProvider 在 &lt;html&gt; 上写 data-theme="light|dark" 与 .dark，并设置 color-scheme。
- CSS 变量、Tailwind 类、MUI palette 三者同源；第三方 Portal 也在 html 选择器下生效。
- accent 色：console 外观设置可选 6 个 preset（accentPresets）或任意 hex，运行时同时改写
  --pd-brand-primary/hover/active/contrast 与 MUI primary。
- 对比度：品牌色文字使用 brand-contrast；暗色模式使用 brand-200 作为主色以保证对比。

### 2.4 媒体/玻璃/遮罩效果

| token | 值 | 用途 |
| --- | --- | --- |
| --pd-media-text / -secondary / -tertiary | 白 100% / 72% / 50% | 视频/图片之上的文字 |
| --pd-scrim-strong / medium / soft | 黑 50% / 36% / 22% | 视频蒙层、渐变遮罩 |
| --pd-glass-surface / -soft | 白 10% / 8% | 玻璃标签底 |
| --pd-glass-border / -soft | 白 20% / 14% | 玻璃标签描边 |
| --pd-nav-veil | 白 88% | 媒体页顶部导航底 |
| --pd-qr-surface | 固定白 | 二维码扫描区（暗色模式也保持白色） |

## 3. 字体与排版

- 字体族：--pd-font-sans = "Roboto", Helvetica, Arial, sans-serif（@fontsource/roboto 300/400/500/700 随库打包）。
- 等宽：--pd-font-mono = Menlo, Monaco, Consolas, ... JetBrains Mono, Fira Code。
- 字号/行高（--pd-text-* / --pd-leading-*）：12/18、13/20、14/22、15/24、16/26、20/30、24/34、30/40。
- 字重（--pd-font-weight-*）：400 regular / 500 medium / 600 semibold / 700 bold。
- MUI 映射：h1=30/40 bold，h2=24/34 semibold，h3=20/30，h4=16/26，h5=15/24，h6=14/22，
  subtitle1=14/22 medium，body1=14/22，body2=13/20，caption=12/18。
- 迁移策略：旧项目 9–11.5px 的微文案统一收敛到 12/18（可读性下限），12.5–13.5 → 13/20，
  17–19 → 16/26，22 → 24/34，26–30 → 30/40。

## 4. 间距、圆角、阴影、边框

| 组 | token | 值 |
| --- | --- | --- |
| 间距 | --pd-space-1..16 | 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64px（4px 网格） |
| 布局 | --pd-sidebar-width / -collapsed / --pd-header-height / --pd-container-max | 248 / 72 / 56 / 1440px |
| 圆角 | --pd-radius-xs..2xl / full | 4 / 6 / 8 / 12 / 16 / 20 / 9999px |
| 阴影 | --pd-shadow-xs..xl / focus | 5 级高度 + focus ring（品牌色 35% alpha） |
| 边框 | --pd-border-width-thin/thick | 1px / 2px |
| 控件高 | --pd-control-xs..xl | 28 / 32 / 36 / 40 / 44px |
| 图标 | --pd-icon-xs..xl | 14 / 16 / 20 / 24 / 28px |

旧项目映射：MUI shape 12 → radius-lg；菜单 8 → radius-md；按钮/输入 12 → radius-lg；
对话弹层 12 → radius-lg；卡片 1px border + 无阴影 → card 变体。

## 5. 动效

### 5.1 时长与曲线

| token | 值 | 场景 |
| --- | --- | --- |
| --pd-duration-fast | 150ms | hover/focus/颜色/位移 |
| --pd-duration-shorter | 200ms | 淡入淡出 |
| --pd-duration-base | 250ms | 展开/收起/指示条 |
| --pd-duration-slow | 300ms | 面板/布局尺寸变化 |
| --pd-duration-complex | 375ms | 复杂编排 |
| --pd-duration-entering | 225ms | 进入 |
| --pd-duration-leaving | 195ms | 离开 |
| --pd-duration-skeleton | 1400ms | 骨架波 |
| --pd-duration-spin | 900ms | 加载旋转 |
| --pd-duration-autofill | 9999s | 浏览器自动填充背景 hack |
| --pd-ease-standard | cubic-bezier(0.4, 0, 0.2, 1) | 默认 |
| --pd-ease-in / out / sharp / emphasized | 标准 Material 曲线组 | 进入/离开/锐利/强调 |

组合过渡：--pd-transition-colors / transform / opacity / shadow / all，全部引用上面的时长与曲线，
因此 prefers-reduced-motion 只需覆写时长即可全局生效。

### 5.2 动效工具类（global.css）

- .pd-transition-colors / -transform / -opacity / -shadow / -all
- .pd-hover-lift（hover 上浮 1px + shadow-md）
- .pd-press（active scale 0.985）
- .pd-focus-ring（focus-visible 使用 shadow-focus）
- .pd-animate-fade-in / scale-in / slide-up / pulse / spin
- 所有动画在 prefers-reduced-motion: reduce 下禁用或降为 1ms。

### 5.3 组件动效约定

- Button：颜色/位移 150ms；loading spinner 900ms；danger hover 透明度过渡。
- Dialog：Grow 进入 225ms / 离开 195ms；可选标题栏拖拽（触摸设备禁用）。
- DropdownMenu / Select / DatePicker：Popover 250ms + 8px 圆角。
- Tabs：指示条与面板 250ms 标准曲线；ResponsiveTabs 溢出折叠 250ms。
- AppShell：Sidebar 宽度与透明度 250ms；图标切换 AnimatedSwap 150ms 交叉淡入。
- 动效时长/曲线均取自 tokens.ts motionDurations / motionEasing，不接受字面量。

## 6. 交互状态

| 状态 | 规范 |
| --- | --- |
| hover | 背景 --pd-bg-hover；可点击卡片/导航可上浮 1px |
| active | --pd-bg-active；按钮 scale 0.985 |
| focus-visible | outline 2px 品牌色 + offset 2px，或 shadow-focus（3px 品牌 35%） |
| disabled | opacity --pd-opacity-disabled（0.45）+ cursor not-allowed + 不可聚焦行为 |
| loading | aria-busy + spinner + 文案替换；按钮保留宽度避免跳动 |
| selected | 品牌 soft 底 + brand 文本（导航/Tabs/TreeView 一致） |
| error | danger 边框/文字 + role=alert + FormField 错误文案 |
| skeleton | 波次动画，暗色模式使用中性透明度 |

## 7. 层级（z-index）

| token | 值 | 用途 |
| --- | --- | --- |
| --pd-z-raised | 1 | 局部抬升（sticky header 内） |
| --pd-z-shell / shell-raised | 40 / 50 | AppShell sidebar / header |
| --pd-z-overlay | 1300 | 全屏拖拽遮罩 |
| --pd-z-modal | 1400 | Dialog |
| --pd-z-popover | 1500 | Popover/Menu |
| --pd-z-toast | 1700 | notistack |
| --pd-z-tooltip | 1800 | Tooltip |

组件内 < 50 的局部堆叠数字属于实现细节，不纳入 token 审计；>= 50 的全局层级必须使用 token。

## 8. 迁移前后对照（Cloudreve → pudans）

| 旧值/实现 | 新 token/实现 |
| --- | --- |
| MUI palette primary #1976d2 / #90caf9 | --pd-brand-primary（light/dark） |
| shape.borderRadius = 12 | --pd-radius-lg |
| Menu borderRadius 8 | --pd-radius-md |
| MUI default typography | Roboto + 12–30 type scale |
| FadeTransition CSS、MUI transitions 默认值 | motionDurations / motionEasing + --pd-transition-* |
| 旧 auth 局部 --login-* 变量 | 语义 token + media/scrim/glass token |
| 旧 hover rgba(0,0,0,.04) / dark .08 | --pd-bg-hover |
| 旧禁用 opacity 0.45 | --pd-opacity-disabled |
| 旧 focus ring rgba(25,118,210,.35) | --pd-shadow-focus（随品牌色变化） |
| 旧滚动条 8px 圆角 + hover 品牌色 | global.css 滚动条规则（token） |

## 9. 验证

- tokens.test.ts：TS ↔ CSS 变量逐项对账（色阶/字号/行高/圆角/时长/曲线/字重/控件高），
  校验所有 var(--pd-*) 均有定义，校验 reduced-motion 覆写。
- theme.test.ts：亮暗 MUI 主题由 tokens 推导、accent 注入、非法色忽略。
- audit-design-tokens.mjs：全仓硬编码扫描，--strict 下 0 findings。
- 视觉：.dsh/migration/screenshots/auth-light.png 与 auth-dark.png（Chrome headless 1440x900）。
