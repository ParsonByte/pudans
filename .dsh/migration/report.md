# 前端重构与迁移最终报告

> 任务：把旧项目前端迁移到当前仓库，重构 web/packages/components，统一设计规范并完成全仓组件化。
> 生成时间：2026-11-16。目标仓库：/Users/byte/Global/Project/pudans。

## 0. 路径与基线（只读盘点结论）

| 项 | 结论 |
| --- | --- |
| 用户给出的工作目录 | /Users/byte/Global/Reference/cloudreve（其中 frontend/ 为旧项目前端源码） |
| 实际目标仓库 | /Users/byte/Global/Project/pudans（当前会话 cwd；唯一包含 web/packages/components 的仓库） |
| 旧项目源 | /Users/byte/Global/Reference/cloudreve/frontend（Cloudreve 4.0.0-next，821 个 src 文件） |
| 包管理器 | pnpm 12.21.0（web/package.json packageManager + pnpm-workspace.yaml catalog） |
| workspace | web/packages/*：api-client、components、shared、ui-plugin-bundler-kit |
| 构建 | Vite 5 MPA（setup/auth/console/uc）+ 组件库 lib 模式 |
| 迁移前 HEAD | 7cd0112（会话期间另一自动化流程将既有未提交改动整理为 2f9c652..c9fedcc 六个提交，本迁移提交叠加在其上） |
| 迁移提交 | a59133c feat(web): migrate Cloudreve design system and component library |
| 本报告/文档提交 | 见 git log 最新提交（second commit，紧随 a59133c） |

## 1. 迁移清单覆盖率与组件总数

| 指标 | 数值 |
| --- | --- |
| 清单条目（源文件，不含 .DS_Store/node_modules） | 1,290 |
| 其中 src/ 源码 | 816 |
| 其中 public/ 静态资源 | 462 |
| 其中根配置 | 12 |
| 已迁移/已封装（目标文件已存在） | 309 |
| 已复用目标库既有能力 | 68 |
| 已保留应用层 | 7 |
| 已登记（暂缓，附原因与计划） | 906 |
| 未登记 / 静默遗漏 | 0 |
| 清单覆盖率 | 100.00% |
| src/ 完成率（迁移+封装+复用+保留） | 374 / 816 = 45.8% |
| 源图标迁移 | 251 / 251 = 100%（Iconify cr: 离线集合） |
| 组件库组件目录 | 55（本批新增 28 个目录/30+ 组件/API） |
| 组件库命名导出 | 225（check-exports 通过） |
| 单元/一致性测试 | 21 个，全部通过 |

处理方式分布：迁移 292、封装 17、复用 68、保留应用层 7、暂缓 906。
完整逐条清单：inventory.md / inventory.json；图标映射：icon-map.md。

### 1.1 暂缓项的分布与阻断原因（均已登记，不是遗漏）

| 源目录 | 文件数 | 已处理 | 暂缓 | 阻断原因摘要 |
| --- | --- | --- | --- | --- |
| src/component/Icons | 251 | 251 | 0 | 全部迁移为 cr: 图标集合 |
| src/component/Common | 29 | 26 | 3 | 文件编码/文件展示/路径选择依赖云盘下载流程 |
| src/component/Frame | 22 | 15 | 7 | 文件多选、存储配额、云盘导航等业务编排 |
| src/component/Dialogs | 7 | 6 | 1 | 批量下载日志依赖下载任务模型 |
| src/component/Pages | 57 | 30 | 27 | 分享/任务/设备/存储配额等目标产品面未开放 |
| src/component/Admin | 165 | 15 | 150 | cloudreve 实体/文件系统/节点/存储策略/分享/任务等管理域在目标后端无对应领域 |
| src/component/FileManager | 129 | 15 | 114 | 目标尚无文件管理应用；通用原子已抽出（图标/面包屑/空状态/描述列表/分页/右键菜单/确认框） |
| src/component/Viewers | 75 | 0 | 75 | 无文件预览应用；依赖 monaco/pdfjs/artplayer/leaflet 等，需应用立项后按批迁移 |
| src/component/Uploader | 34 | 1 | 33 | 上传协议/分片/多云存储与 cloudreve 后端签名强绑定 |
| src/api | 9 | 0 | 9 | 用 @pudans/api-client 生成客户端替代 |
| src/redux | 14 | 0 | 14 | 云盘域切片；目标 store 已有 app/auth，文件域待文件应用 |
| public/{assets,locales,static} | 459 | 0 | 459 | pdfjs 运行时资源 365、cloudreve 品牌/占位图 29、14 语言资源 65（目标 i18n 已重写 5 个 namespace） |
| 其余（hooks/util/session/router/root） | 35 | 24 | 11 | 云盘协议/会话/路由副作用等 |

## 2. web/packages/components 重构后的目录树与导出

    web/packages/components/src/
    ├── index.ts                     # 导入 style.css 并导出 components/hooks/layouts/styles/utils
    ├── components/                  # 55 个组件目录（每个含 index.ts + *.tsx + *.module.css）
    │   ├── accordion/               # 旧 DialogAccordion
    │   ├── alert/  avatar/ badge/ button/ card/ chart/ checkbox/
    │   ├── breadcrumbs/             # 旧 Breadcrumb 三件套
    │   ├── captcha/                 # 旧 Captcha 五合一
    │   ├── code/                    # 旧 Common/Code
    │   ├── confirm-dialog/          # 旧 Confirmation + redux promise pool
    │   ├── date-picker/ description-list/ dialog/ dropdown-menu/
    │   ├── empty-state/ error-detail-dialog/ error-state/
    │   ├── file-drop-zone/ file-type-icon/ form-field/ graph/ highlight-text/
    │   ├── icon/                    # Icon + builtin-icons + cloudreve-icons(251 cr:)
    │   ├── image-cropper/ input/ language-switcher/ logo/ motion/
    │   ├── oauth-app-card/ otp-input/ pagination/ powered-by/ progress/ progress-steps/
    │   ├── relative-time/ responsive-tabs/ search-input/ select/ select-dialog/
    │   ├── settings-panel/ size-input/ skeleton/ split-pane/ stat-card/ switch/
    │   ├── table/ tabs/ theme-switch/ toast/ tooltip/ tree-view/ user/ ...
    ├── hooks/                       # use-delayed-hover / use-overflow / use-media-session
    ├── layouts/                     # app-shell / side-nav-item / page-container / page-layout / sidebar-layout
    ├── styles/
    │   ├── tokens.ts               # 设计 token 唯一来源（颜色/字号/间距/圆角/阴影/动效/z-index/状态）
    │   ├── variables.css           # CSS 变量镜像（亮/暗/reduced-motion）
    │   ├── create-theme.ts         # 由 token 推导 MUI 主题（palette/typography/shape/transitions/zIndex）
    │   ├── theme-provider.tsx      # 亮暗主题 + accent 注入 + 持久化
    │   ├── global.css              # reset/字体/滚动条/focus-visible/动效工具类
    │   └── index.css               # fontsource Roboto + variables + global
    └── utils/                      # cn / index

导出策略：root index 全量导出；每个组件目录单一 index.ts，避免深层路径导入；命名唯一
（225 个），无循环依赖（230 模块检查通过）。类型完整（tsc strict + declaration 产出）。

## 3. 主题、字体、动效、过渡、状态规范

见 design-system.md。摘要：

- 颜色：品牌 50–900、中性 0–1000、语义 bg/text/brand/border/state/chart、媒体/玻璃/遮罩 token；
  亮暗双套；accent 运行时注入并同步 MUI + CSS 变量。
- 字体：Roboto 300/400/500/700（@fontsource 打包）+ mono；12/13/14/15/16/20/24/30 字号，
  18–40 行高，400/500/600/700 字重。
- 间距 4px 网格；圆角 4/6/8/12/16/20/9999；阴影 5 级 + 品牌色 focus ring。
- 动效：150/200/250/300/375ms；进入 225ms / 离开 195ms；标准/进出/锐利/强调曲线；
  --pd-transition-colors/transform/opacity/shadow/all；prefers-reduced-motion 全局降级。
- 状态：hover/active/focus-visible/disabled/loading/selected/error/skeleton 八类统一；
  Button/Dialog/in/Tabs/AppShell/图标切换全部接入 motion token。
- 层级：--pd-z-raised/shell/overlay/modal/popover/toast/tooltip。
- 旧值映射：MUI radius 12→radius-lg、菜单 8→radius-md、hover rgba(.04/.08)→--pd-bg-hover、
  focus rgba(25,118,210,.35)→--pd-shadow-focus 等，详见 design-system.md 第 8 节。

## 4. 旧项目全仓组件化清单

逐文件清单在 inventory.md（1,290 行条目，每项含源路径/目标路径/处理方式/状态/理由）。
组件级目录在 component-catalog.md（源路径、Props、token 依赖、使用示例、验证方式）。
关键多对一合并：

| 旧实现 | 新组件 |
| --- | --- |
| Common/Nothing + EmptyFileList + Pages/NoMatch | EmptyState |
| Common/ErrorBoundary | ErrorState + ErrorBoundary |
| BorderLinearProgress + CircularProgress | Progress（linear/circular） |
| Common/Captcha 5 个文件 | Captcha（turnstile/recaptcha） |
| Dialogs/Confirmation + redux dialog pool | ConfirmProvider + useConfirm + ConfirmDialog |
| Dialogs/SelectOption | SelectProvider + useSelectOption + SelectDialog |
| Dialogs/DraggableDialog | Dialog draggable + footer |
| Dialogs/DialogAccordion | Accordion |
| Dialogs/AggregatedErrorDetail | ErrorDetailDialog |
| FileManager Breadcrumbs 三件套 | Breadcrumbs |
| FileManager InfoRow/Details/Data | DescriptionList |
| FileManager FileTypeIcon/FileIcon/FileSmallIcon | FileTypeIcon |
| Frame NavIconTransition + FadeTransition.css | AnimatedSwap + Fade/Grow/Slide |
| Frame DarkThemeSwitcher | ThemeSwitch |
| Common/Logo / Frame/PoweredBy / Common/LanguageSwitcher | Logo / PoweredBy / LanguageSwitcher |
| Common/User 三件套 | Avatar + UserBadge + UserPopover |
| Common/StyledFormControl + SettingListItem | FormField + FormSection + SettingItem |
| NavBar SearchBar / SplitHandle | SearchInput / SplitPane |
| Common/ResponsiveTabs / SizeInput | ResponsiveTabs / SizeInput |
| Uploader/DropFile | FileDropZone |
| Common/TimeBadge | RelativeTime（Intl，去 timeago-react） |
| hooks delayedHover/useOverflow/useMediaSession | @pudans/components/hooks 同名迁移 |
| util base64/boolset/lru/filesystem/datetime | @pudans/shared/utils 迁移 |

## 5. 页面替换列表与 API 变更

### 5.1 页面/应用替换

| 应用 | 文件 | 变更 |
| --- | --- | --- |
| auth | modules/login/components/auth-topbar.tsx | 删除本地 theme cycle + 手写语言菜单，改用 ThemeSwitch + LanguageSwitcher（保留品牌/帮助） |
| auth | layouts/auth-layout.module.css、auth-card.module.css、login/recovery/legal css | 局部 --login-* 平行主题层全部映射到全局语义 token；字体/圆角/动效 token 化 |
| uc | layouts/uc-layout.tsx | 同上，header actions 收敛为 ThemeSwitch + LanguageSwitcher |
| uc | modules/profile/profile-view.tsx、security/security-view.tsx、authorizations/authorizations-view.tsx | TimeAgo → RelativeTime；二维码区使用 --pd-qr-surface（修复暗色白底 bug）；MUI 组件逐步换公共库 |
| console | layouts/console-layout.tsx | header actions 收敛为 ThemeSwitch + LanguageSwitcher；删除三份重复 theme/language 实现 |
| console | modules/users、roles、audit | TimeAgo → RelativeTime；Table 开启 loading 骨架；空态走 EmptyState |
| console | modules/settings/components/appearance-settings.tsx | accent 色预设改为 accentPresets token；monospace 改 --pd-font-mono |
| console | modules/settings/components/*.css | 圆角/字号/动效/ z-index token 化 |
| setup | modules/setup/setup-view.module.css | 字号/圆角/z-index token 化 |
| src | providers/index.tsx | 挂载 ConfirmProvider + SelectProvider |
| src | i18n/index.ts | 移除 HttpBackend 链（所有 namespace 已内置）；修复 nonExplicitSupportedLngs 导致 en-US 被判 unsupported、t() 返回 key 的 i18next 27 行为 |
| src | store/app-slice.ts | 默认 accent 改 accentPresets[0] |
| src | theme/index.tsx | 保持与 PudansThemeProvider 桥接 |

### 5.2 API 变更（旧 → 新）

| 旧 API | 新 API | 说明 |
| --- | --- | --- |
| Nothing(primary, secondary, top, size) | EmptyState({title,description,icon,action,variant}) | 字段改名 + 语义 variant |
| BorderLinearProgress / CircularProgress | Progress({variant,tone,size,value}) | 统一 indeterminate/determinate |
| Confirmation（redux promise pool） | useConfirm() / ConfirmProvider | 移除全局 redux dialog |
| SelectOption（redux） | useSelectOption() / SelectProvider | 返回 Promise<T | null> |
| DraggableDialog(dialogProps, showActions) | Dialog({draggable, footer}) | 动作区由 footer 传入 |
| TimeBadge(datetime, timeAgoThreshold) | RelativeTime({datetime, thresholdMinutes, tooltip}) | Intl.RelativeTimeFormat |
| DarkThemeSwitcher（redux bool） | ThemeSwitch（light/system/dark） | 消费 usePudansTheme |
| ResponsiveTabs(tabs,value,onChange) | ResponsiveTabs(items,value,onChange) | 溢出行为内置 |
| SizeInput（redux） | SizeInput（纯受控 + SIZE_UNITS/convertSize） | 去 redux/i18n 耦合 |
| SearchBar（全局搜索） | SearchInput | 受控 + 快捷键/hotkey |
| SplitHandle（redux drawer） | SplitPane | 受控尺寸 + storageKey |
| Breadcrumb 三件套 | Breadcrumbs({items,maxItems,separator}) | 中部折叠菜单 |
| InfoRow | DescriptionList({items,columns,dense,bordered}) | 响应式列 |
| FileTypeIcon（redux 站点配置） | FileTypeIcon({name,isFolder,size,tone}) + resolveFileTypeIcon | 纯函数映射 |
| 旧 Snackbar 三件套 | ToastProvider/useToast | notistack 统一 |
| 旧图标组件（251 个） | Icon name="cr:Xxx" | 单集合离线注册 |
| 旧 theme cycle（3 处重复） | ThemeSwitch | 单一实现 |
| 旧语言菜单（3 处重复） | LanguageSwitcher | 单一实现 |

## 6. 验证命令与结果

在 /Users/byte/Global/Project/pudans/web 下执行：

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| 应用类型检查 | npx tsc -p tsconfig.json --noEmit | PASS（无输出） |
| 包类型检查 | npx pnpm -r typecheck | PASS（api-client/components/shared/ui-plugin-bundler-kit 全部 Done） |
| Lint | npx eslint . | 0 errors / 24 warnings（全部为既有 react-refresh only-export-components 提示） |
| 单元/一致性测试 | node --test tests/ | 21 pass / 0 fail |
| 循环依赖 | node scripts/check-cycles.mjs | PASS：230 模块、4 个 root，无环 |
| 导出完整性 | node scripts/check-exports.mjs | PASS：55 组件目录、225 唯一命名导出 |
| 硬编码 token 审计 | node scripts/audit-design-tokens.mjs --strict | PASS：0 findings（颜色/字号/圆角/阴影/动效/字体族/z-index） |
| 包构建 | npx pnpm -r build | PASS（components/shared/api-client/kit 全部产 dist） |
| 应用构建 | npx pnpm build | PASS（setup/auth/console/uc 四个入口，✓ built） |
| 主题切换 | tests/theme.test.ts + tokens.test.ts | PASS：亮暗主题由 token 推导、accent 注入、CSS 变量逐项对账、reduced-motion 覆写 |
| 字体检查 | tokens.test.ts + 构建产物 | PASS：Roboto woff/woff2 四档打入 dist，字号/行高 token 一致 |
| 动画/过渡检查 | tokens.test.ts + audit | PASS：时长/曲线 token 对账，无字面量；reduced-motion 生效 |
| 视觉回归 | Chrome headless 1440x900 截图 | screenshots/auth-light.png、auth-dark.png；亮暗主题、翻译、卡片/输入/按钮/主题切换控件均正确 |
| 键盘可访问性 | 组件实现 + rendering 测试 | focus-visible 统一、EmptyState role=status/alert、ThemeSwitch radiogroup、Table 行 Enter/Space、FileDropZone Enter/Space、SplitPane 方向键/Home/End、Dialog Esc/焦点陷阱由 MUI 提供 |
| 响应式 | 组件断点 + 截图 | PageLayout/DescriptionList/SplitPane 在 599/899px 断点降级；1440x900 截图验证 |

命令一键执行：npx pnpm verify（typecheck + cycles + exports + test:packages + test + lint + build）。

## 7. 未完成项及阻断原因

没有未登记的遗漏项（清单覆盖 1,290/1,290）。以下为明确登记、需要产品/后端决策才能继续的暂缓项：

1. 文件管理应用（FileManager 114 + Uploader 33 + 目标路由）：目标产品尚无文件管理页面/后端协议，
   但公共原子（FileTypeIcon/Breadcrumbs/EmptyState/DescriptionList/Pagination/Confirm/ContextMenu/
   FileDropZone）已迁移，可随时组装。
2. 文件预览器（Viewers 75）：依赖 monaco/pdfjs/artplayer/leaflet/mapbox 等运行时，需文件/媒体应用
   立项后按依赖批次迁移；本次已登记每个文件与依赖。
3. cloudreve 管理端业务域（Admin 150）：Entity/FileSystem/Node/StoragePolicy/Share/Task/Group 等
   与 cloudreve 后端领域强绑定，目标后端无对应概念；console 已实现的 settings/users/roles/audit
   均已复用与 token 化。
4. 旧 i18n 语言资源（65 个 JSON）：目标 i18n 已重写 common/setup/auth/console/uc 五个 namespace
   （中英双语，resources.ts），旧 14 语言资源保留为翻译参考，待统一 key 后导入。
5. pdfjs 静态资源（365）、cloudreve 品牌/占位图（29）：不复制第三方/品牌资产；目标构建按需从
   node_modules/pdfjs-dist 复制。
6. Captcha CapProvider：旧 CapCaptcha 使用自定义 SDK，本次统一 turnstile/recaptcha；Cap 登记待接入。
7. 文件/下载/分享/任务/设备等页面（Pages 27）：目标产品未开放对应路由。
8. 自动化视觉回归：仓库未引入 Playwright/Storybook；本次用 Chrome headless 截图作为基线，
   后续可接入 CI 截图对比。
9. 并发说明：会话期间另一自动化流程将既有未提交改动整理为 2f9c652..c9fedcc 六个提交；本迁移
   提交 a59133c 叠加其上。若需要只回滚本次迁移，使用第 8 节命令。

## 8. 回滚方式与关键提交记录

| 提交/标签 | 内容 |
| --- | --- |
| a59133c | 本次迁移代码 + 清单 + 设计规范 + 测试/脚本（tag: migration-2026-11-16） |
| c9fedcc | 迁移前检查点（既有工作整理后的 HEAD；该提交已包含会话开始时的未提交改动） |
| 7cd0112 | 会话开始前的原始 HEAD（更早基线） |

回滚方式（三选一）：

    # 1) 只撤销本次迁移，保留此前工作
    git reset --hard c9fedcc

    # 2) 完整回到会话开始前
    git reset --hard 7cd0112

    # 3) 保留历史、生成反向提交
    git revert --no-edit a59133c

注意：a59133c 包含清单、截图与文档；回滚后 web/packages/components 将恢复到迁移前状态。
本次迁移未修改任何后端代码与构建入口（web/build.gradle 未变），所有产物均在 web/ 内。

## 9. 复现与后续批次入口

    cd /Users/byte/Global/Project/pudans
    node .dsh/migration/generate-inventory.mjs
    cd web
    node scripts/check-cycles.mjs
    node scripts/check-exports.mjs
    node scripts/audit-design-tokens.mjs --strict
    npx pnpm typecheck && npx pnpm -r typecheck
    npx pnpm test:packages && node --test tests/
    npx pnpm build

后续批次建议顺序（已在清单登记）：
1) 设备管理页（uc）→ 2) 文件管理 app（FileManager/Uploader 原子已就绪）→ 3) Viewers 按依赖分批 →
4) cloudreve 管理端领域（若目标后端补齐）→ 5) 旧语言资源导入与翻译统一。
