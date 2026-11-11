# 迁移组件目录（Component Catalog）

> 源项目：/Users/byte/Global/Reference/cloudreve/frontend。目标：web/packages/components。
> 每个条目包含源路径、目标路径、关键 Props、token/样式依赖、使用示例与验证方式。
> 示例中省略 import；组件均从 @pudans/components 统一导出。

## 1. 新增公共组件

### 1.1 EmptyState / ErrorState / ErrorBoundary

| 项 | 内容 |
| --- | --- |
| 源 | src/component/Common/Nothing.tsx、src/component/FileManager/Explorer/EmptyFileList.tsx、src/component/Pages/NoMatch.tsx、src/component/Common/ErrorBoundary.tsx |
| 目标 | components/empty-state/empty-state.tsx、components/error-state/error-state.tsx |
| 导出 | EmptyState, EmptyStateProps, EmptyStateVariant, EmptyStateSize, ErrorState, ErrorBoundary, resolveErrorMessage |
| Props | title, description, icon (node 或 icon name), variant: empty/not-found/error/permission/success/file-drop, size: sm/md/lg, action, secondaryAction, bordered, compact |
| token | --pd-bg-subtle, --pd-text-*, --pd-state-*-soft, --pd-radius-lg/full, --pd-space-* |
| 验证 | tests/rendering.test.mjs（SSR 渲染 + role=status）；empty-state 视觉见应用页 |

    <EmptyState
      title="No files"
      description="Drop files here or use the upload button."
      variant="file-drop"
      action={<Button>Upload</Button>}
    />

### 1.2 ErrorBoundary

- 类组件边界，onError 上报；fallback 可传 ReactNode 或 (error, reset) => ReactNode。
- 默认渲染 ErrorState + Retry（token 化）。
- 用法：在应用根/路由分支包一层。

### 1.3 Progress / LinearProgress / CircularProgress

| 项 | 内容 |
| --- | --- |
| 源 | src/component/Common/BorderLinearProgress.tsx、CircularProgress.tsx |
| 目标 | components/progress/progress.tsx |
| Props | value（0-100，缺省 indeterminate）, variant: linear/circular, tone: brand/success/warning/danger/neutral, size: sm/md/lg, label, showValue, ariaLabel |
| token | --pd-bg-muted, --pd-brand-primary, --pd-state-*, --pd-radius-full, --pd-duration-base, --pd-duration-skeleton |
| 验证 | tests/rendering.test.mjs（aria-valuenow=42） |

    <Progress value={42} showValue label="Uploading" tone="brand" />

### 1.4 ProgressSteps

- 源：src/component/Pages/Tasks/StepProgressBar.tsx。
- Props：steps: {key,label,description,status}[]、current、orientation: horizontal/vertical、size。
- 语义：ol/li + aria-current="step"；完成/错误使用 cr:Checkmark、cr:Warning。
- token：--pd-brand-primary、--pd-state-*、--pd-border-default、--pd-radius-full。

### 1.5 Code / CodeBlock

- 源：src/component/Common/Code.tsx。
- Code：内联 code，等宽字体 + 弱底色。CodeBlock：语言标签、复制按钮（clipboard + 兼容回退）、
  可选行号、wrap、maxHeight。
- Props：code/children、language、showCopy、copyLabel/copiedLabel、wrap、maxHeight、showLineNumbers。
- token：--pd-font-mono、--pd-bg-subtle、--pd-border-default、--pd-radius-lg、--pd-duration-fast。

### 1.6 Breadcrumbs

| 项 | 内容 |
| --- | --- |
| 源 | src/component/FileManager/TopBar/Breadcrumb.tsx、BreadcrumbButton.tsx、BreadcrumbHiddenItem.tsx |
| 目标 | components/breadcrumbs/breadcrumbs.tsx |
| Props | items: BreadcrumbEntry[]（label/icon/href/onClick）, maxItems（中部折叠）, separator, ariaLabel |
| 行为 | 中间项收进 DropdownMenu；当前项 aria-current=page；link/button/static 三种渲染 |
| token | --pd-text-secondary/tertiary, --pd-bg-hover, --pd-shadow-focus, --pd-radius-sm |

    <Breadcrumbs items={[{ label: "Console", href: "/" }, { label: "Users" }]} maxItems={4} />

### 1.7 DescriptionList

- 源：src/component/FileManager/Sidebar/InfoRow.tsx、Details.tsx、Data.tsx。
- Props：items: DescriptionItem[]（label/value/span）、columns: 1/2/3、dense、bordered。
- 响应式：<900px 自动单列。
- token：--pd-text-tertiary/primary、--pd-radius-md、--pd-border-subtle、--pd-space-*。

### 1.8 RelativeTime

| 项 | 内容 |
| --- | --- |
| 源 | src/component/Common/TimeBadge.tsx |
| 目标 | components/relative-time/relative-time.tsx |
| Props | datetime, base, locale, timeZone, thresholdMinutes（默认 7 天）, tooltip |
| 实现 | @pudans/shared formatRelativeTime / formatDateTime（Intl），不再依赖 timeago-react |
| 可访问性 | time[datetime] + title 完整时间 |

    <RelativeTime datetime={record.timestamp} />

### 1.9 FileTypeIcon

- 源：src/component/FileManager/Explorer/FileTypeIcon.tsx、FileIcon.tsx、FileSmallIcon.tsx。
- Props：name（文件名或扩展名）、isFolder、size: sm/md/lg、icon（覆盖）、tone、label。
- 导出 resolveFileTypeIcon(name, isFolder) 纯函数（60+ 扩展名映射）。
- 图标来源：cr: 离线集合（Image/Video/MusicNote1/DocumentPDF/FileWordBox/FileExclBox/
  FilePowerPointBox/FolderZip/LanguageJS/Python/Go/Rust/C/CPP/PHP/Markdown/Book/Notebook 等）。
- tone token：--pd-state-*-soft / --pd-brand-soft + --pd-text-*。

### 1.10 ConfirmDialog / ConfirmProvider / useConfirm

| 项 | 内容 |
| --- | --- |
| 源 | src/component/Dialogs/Confirmation.tsx、DraggableDialog.tsx（actions 部分） |
| 目标 | components/confirm-dialog/confirm-dialog.tsx |
| API | ConfirmProvider 挂载一次；const confirm = useConfirm(); await confirm({title,description,confirmLabel,cancelLabel,tone}) => boolean |
| 受控版 | ConfirmDialog({open,onConfirm,onCancel,...}) 可单独使用 |
| 变更 | 移除 redux promise pool；改为 React context + promise 队列；danger tone 使用 Button danger 变体 |
| token | Dialog 全部 token；danger 使用 --pd-state-danger |

### 1.11 SelectDialog / SelectProvider / useSelectOption

- 源：src/component/Dialogs/SelectOption.tsx。
- API：useSelectOption()(options, config) => Promise<T | null>；SelectDialog 受控版。
- option：value/label/description/icon/disabled；支持键盘与移动端。
- token：--pd-bg-hover、--pd-shadow-focus、--pd-radius-md。

### 1.12 ErrorDetailDialog

- 源：src/component/Dialogs/AggregatedErrorDetail.tsx。
- Props：open/onClose/title/message/errors[{key,label,message}]/requestId/footer。
- 用 CodeBlock 展示 requestId；错误行使用 monospace + danger token。

### 1.13 Accordion / DialogAccordion

| 项 | 内容 |
| --- | --- |
| 源 | src/component/Dialogs/DialogAccordion.tsx |
| 目标 | components/accordion/accordion.tsx |
| Props | title, children, defaultExpanded, expanded（受控）, onExpandedChange, variant: card/ghost, icon, disabled |
| 动画 | grid-template-rows 0fr->1fr + visibility 延迟，250ms token；aria-expanded/aria-controls |
| 别名 | DialogAccordion = Accordion（迁移兼容） |

### 1.14 ThemeSwitch

- 源：src/component/Frame/NavBar/DarkThemeSwitcher.tsx。
- Props：labels、showLabels、size、disabled、aria-label。
- 行为：light/system/dark radiogroup，消费 usePudansTheme()；直接驱动 Redux 偏好与 CSS 变量。
- 已用于 auth-topbar / uc-layout / console-layout，替代三处重复的 theme cycle 逻辑。

### 1.15 Logo / PoweredBy / LanguageSwitcher / OAuthAppCard

| 组件 | 源 | 关键 Props | 说明 |
| --- | --- | --- | --- |
| Logo | Common/Logo.tsx | src/darkSrc/name/href/height/invertInDark | 亮暗源切换 + 字标回退；不复制 cloudreve 品牌资产 |
| PoweredBy | Frame/PoweredBy.tsx | label/name/href/logoSrc | 品牌无关的页脚署名 |
| LanguageSwitcher | Common/LanguageSwitcher.tsx | languages/value/onChange/compact/showCurrentLabel | i18n 运行时由 props 注入；DropdownMenu 实现 |
| OAuthAppCard | Frame/OauthAppCard.tsx | name/description/icon/loading/action | 支持 URL 或 iconify 图标 + loading 骨架 |

### 1.16 UserBadge / UserPopover

- 源：src/component/Common/User/UserAvatar.tsx、UserBadge.tsx、UserPopover.tsx。
- UserBadge：name/description/src/icon/status/size/onClick/trailing；组合 Avatar。
- UserPopover：open/anchorEl/user/onClose/actions/footer；受控 Popover，无 redux 依赖。
- token：--pd-bg-hover、--pd-radius-md/lg、--pd-shadow-lg。

### 1.17 FormField / FormSection / SettingItem

- 源：src/component/Common/StyledFormControl.tsx、src/component/Pages/Setting/SettingListItem.tsx。
- FormField：label/description/error/required/htmlFor；label+控件+错误行，错误 role=alert。
- FormSection：title/description/actions/bordered；设置分组卡。
- SettingItem：title/description/control；设置行（右对齐控件）。
- token：--pd-text-*、--pd-space-*、--pd-radius-lg、--pd-border-subtle。

### 1.18 SearchInput

- 源：src/component/Frame/NavBar/SearchBar.tsx。
- Props：value/defaultValue/onChange/onSearch/placeholder/shortcut/loading/disabled/ariaLabel。
- 快捷键：react-hotkeys-hook，mod+k 等；显示平台化快捷键（⌘ / Ctrl）。
- token：--pd-control-md、--pd-radius-full、--pd-bg-subtle、--pd-shadow-focus。

### 1.19 SplitPane

- 源：src/component/Frame/NavBar/SplitHandle.tsx。
- Props：children[2], direction, initialSize, minSize, maxSize, storageKey, ariaLabel。
- 行为：pointer 拖拽 + 键盘方向键/Home/End + aria separator + localStorage 持久化。
- token：--pd-brand-primary、--pd-border-strong、--pd-duration-fast。

### 1.20 ResponsiveTabs

- 源：src/component/Common/ResponsiveTabs.tsx。
- Props：items[{key,label,icon,badge}]、value、onChange、ariaLabel。
- 行为：ResizeObserver 检测溢出，溢出时只显示当前项 + More 下拉；role=tablist/tab。
- token：--pd-brand-soft、--pd-bg-hover、--pd-radius-full。

### 1.21 SizeInput

- 源：src/component/Common/SizeInput.tsx。
- Props：value(bytes)/onChange/label/helperText/error/min/max/allowZero/units/size。
- 导出 SIZE_UNITS、convertSize；纯受控，无 redux/i18n 依赖。
- token：--pd-control-*、--pd-radius-lg、--pd-border-strong、--pd-state-danger。

### 1.22 FileDropZone

- 源：src/component/Uploader/DropFile.tsx。
- Props：onFiles/accept/multiple/disabled/overlay/title/description/children。
- 行为：内嵌虚线区或全屏遮罩；accept 过滤；键盘 Enter/Space 打开文件选择；aria-disabled。
- token：--pd-brand-*、--pd-z-overlay、--pd-backdrop-blur、--pd-shadow-xl。

### 1.23 Captcha

- 源：src/component/Common/Captcha/*.tsx（CapCaptcha/Captcha/DefaultCaptcha/ReCaptchaV2/TurnstileCaptcha 五合一）。
- Props：provider: turnstile/recaptcha、siteKey、onVerify(token|null)、onError、theme、language。
- 实现：按需加载第三方脚本 + explicit render；组件卸载清理 widget；失败展示可访问错误。
- 说明：Cap provider 未纳入（旧项目自定义 SDK），登记为后续项。

### 1.24 Motion（Fade / Grow / Slide / AnimatedSwap）

- 源：src/component/Common/FadeTransition.css、src/component/Frame/NavBar/NavIconTransition.tsx。
- 导出：Fade、Grow、Slide、AnimatedSwap、fadeTimeout、growTimeout + re-export motionDurations/motionEasing/transitions。
- AnimatedSwap：双槽绝对定位交叉淡入，150ms token，aria-hidden 切换。
- token：--pd-duration-entering/leaving/fast、--pd-ease-standard。

### 1.25 hooks：useDelayedHover / useOverflow / useMediaSession

| hook | 源 | API |
| --- | --- | --- |
| useDelayedHover | src/hooks/delayedHover.tsx | (delayMs, onHoverChange) => {hovered,onMouseEnter,onMouseLeave,onFocus,onBlur} |
| useOverflow | src/hooks/useOverflow.tsx | (ref, onChange) => boolean，ResizeObserver |
| useMediaSession | src/hooks/useMediaSession.ts | ({metadata,playing,duration,position,handlers})，泛化 Media Session API |

### 1.26 shared utils（@pudans/shared）

| 模块 | 源 | 导出 |
| --- | --- | --- |
| utils/base64.ts | src/util/base64.ts | base64Encode / base64Decode / base64DecodeToString |
| utils/boolset.ts | src/util/boolset.ts | Boolset（enabled/and/or/set/sets/toUint8Array/toString） |
| utils/lru.ts | src/util/lru.ts | LRUCache（onEvict 回收 blob URL） |
| utils/filesystem.ts | src/util/filesystem.ts | getFileSystemDirectoryPaths / createFileSystemDirectory / saveFileToFileSystemDirectory / streamFileToFileSystemDirectory / verifyFileSystemRWPermission |
| utils/datetime.ts | src/util/datetime.ts | formatLocalTime / formatDurationHuman |

## 2. 既有组件增强

| 组件 | 变更 |
| --- | --- |
| Dialog | 新增 draggable（标题栏拖拽，按钮/表单控件不触发，触摸屏禁用）；translate 独立于 Grow transform |
| Button | 新增 danger 变体；loading spinner 使用 --pd-duration-spin |
| Table | 新增 loading（骨架行）、loadingRows、emptyState（默认 EmptyState）；aria-busy |
| Icon | 注册 cr: 251 图标离线集合；云盘图标兜底离线渲染；registerCloudreveIcons 幂等 |
| Tabs | 硬编码 rgba 指示条/焦点环改为 token（品牌色/accent 生效）；9999px→radius-full |
| Avatar/Badge/Checkbox/TreeView/StatCard/PageContainer/AppShell | 字号/圆角/z-index/动画时长全部 token 化 |

## 3. 图标集合（251 → cr:）

- 目标：components/icon/cloudreve-icons.ts（IconifyJSON + registerCloudreveIcons）。
- 清单：.dsh/migration/icon-map.md、data/cloudreve-icons.json。
- 用法：

    <Icon name="cr:Search" size={24} />

- 验证：tests/icons.test.ts（251 唯一、离线注册、body 保留 currentColor、SSR SVG 输出）。
- 许可：源图标来自 Cloudreve 前端（GPL-3.0），文件头保留来源与许可说明。

## 4. 导出与质量门禁

- web/packages/components/src/index.ts：components + hooks + layouts + styles + utils 全量导出。
- 组件目录 55 个，命名导出 225 个；node scripts/check-exports.mjs 通过。
- 循环依赖：node scripts/check-cycles.mjs 检查 230 个模块，无环。
- 单元测试：node --test tests/（21 个测试全绿）。
