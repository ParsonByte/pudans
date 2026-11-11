# 组件库审计：组件 vs 非组件（components-audit）

> 生成时间：2026-11-16T03:06:12.999Z。只读盘点产物，生成于任何代码改动之前。
> 范围：web/packages/components（不含 node_modules/dist）。后续动作：保留为组件 / 移出组件库 / 删除 / 重写。

## 摘要

| 指标 | 数值 |
| --- | --- |
| 包内文件总数 | 201 |
| 组件目录数 | 55 |
| 需按旧项目重写的组件 | 55（全部） |
| 需移出组件库的文件 | 30 |
| 需删除/内联的文件 | 1 |
| 需重写的源码/样式/构建配置 | 112 |
| 纯保留文件（组件入口/barrel） | 55 |

按文件类别：构建配置（非组件） 3；组件样式 53；组件源码 55；组件入口 55；图标集合/注册表（非组件） 3；组件（Icon） 3；组件内部 hook（非独立组件） 1；通用 hooks（非组件） 4；包入口 1；布局（非组件） 15；主题/token（非组件） 6；通用 utils（非组件） 2

## 处置规则

- 组件目录内：*.tsx / *.module.css / index.ts 保留在组件库，但视觉/布局全部按旧项目重写。
- hooks → web/packages/hooks（@pudans/hooks）。
- utils → web/packages/utils（@pudans/utils；与现有 @pudans/shared 合并，见第二阶段说明）。
- layouts/ → web/packages/layouts（@pudans/layouts）。
- styles/（tokens/variables/create-theme/theme-provider/global）→ web/packages/theme（@pudans/theme）。
- icon 集合与注册表 → web/packages/icons（@pudans/icons）；Icon 渲染组件保留在组件库。
- settings-panel/use-dirty-state.ts → 内联到组件（组件库不允许独立 hook 文件）。
- 组件库入口 src/index.ts → 只导出 components（不再导出 hooks/layouts/styles/utils）。
- package.json/vite.config.ts/tsconfig.json → 重写为纯组件库（无 style.css 入口、无 token 导出、无 fontsource/图标集合依赖）。

## 组件级清单（55）

| # | 组件目录 | 文件数 | 旧项目参照 | 动作 |
| --- | --- | --- | --- | --- |
| 1 | accordion | 3 | src/component/Dialogs/DialogAccordion.tsx | 重写（按旧项目 1:1 重建） |
| 2 | alert | 3 | MUI Alert + App.tsx MuiAlert override | 重写（按旧项目 1:1 重建） |
| 3 | animate-height | 3 | src/component/Common/AutoHeight.tsx | 重写（按旧项目 1:1 重建） |
| 4 | avatar | 3 | src/component/Common/User/UserAvatar.tsx + MUI Avatar | 重写（按旧项目 1:1 重建） |
| 5 | badge | 3 | MUI Chip / FileBadge 使用形态 + 主题 | 重写（按旧项目 1:1 重建） |
| 6 | breadcrumbs | 3 | src/component/FileManager/TopBar/Breadcrumb.tsx / BreadcrumbButton.tsx / BreadcrumbHiddenItem.tsx | 重写（按旧项目 1:1 重建） |
| 7 | button | 3 | src/component/Common/StyledComponents.tsx DefaultButton + App.tsx MuiButton | 重写（按旧项目 1:1 重建） |
| 8 | captcha | 3 | src/component/Common/Captcha/*.tsx | 重写（按旧项目 1:1 重建） |
| 9 | card | 3 | src/component/Admin/Common/AdminCard.tsx + MUI Paper/Card 主题 | 重写（按旧项目 1:1 重建） |
| 10 | chart | 3 | src/component/Admin/Home/Home.tsx (recharts 卡片) + 主题图表色 | 重写（按旧项目 1:1 重建） |
| 11 | checkbox | 3 | App.tsx MuiCheckbox + MUI Checkbox | 重写（按旧项目 1:1 重建） |
| 12 | code | 3 | src/component/Common/Code.tsx | 重写（按旧项目 1:1 重建） |
| 13 | confirm-dialog | 3 | src/component/Dialogs/Confirmation.tsx + DraggableDialog.tsx | 重写（按旧项目 1:1 重建） |
| 14 | date-picker | 3 | MUI X DatePicker 主题（旧项目默认） | 重写（按旧项目 1:1 重建） |
| 15 | description-list | 3 | src/component/FileManager/Sidebar/InfoRow.tsx / BasicInfo.tsx / Details.tsx | 重写（按旧项目 1:1 重建） |
| 16 | dialog | 3 | src/component/Dialogs/DraggableDialog.tsx + App.tsx MuiDialog | 重写（按旧项目 1:1 重建） |
| 17 | dropdown-menu | 3 | src/component/FileManager/ContextMenu/* + App.tsx MuiMenu/MuiMenuItem | 重写（按旧项目 1:1 重建） |
| 18 | empty-state | 3 | src/component/Common/Nothing.tsx + Explorer/EmptyFileList.tsx | 重写（按旧项目 1:1 重建） |
| 19 | error-detail-dialog | 3 | src/component/Dialogs/AggregatedErrorDetail.tsx | 重写（按旧项目 1:1 重建） |
| 20 | error-state | 2 | src/component/Common/ErrorBoundary.tsx | 重写（按旧项目 1:1 重建） |
| 21 | file-drop-zone | 3 | src/component/Uploader/DropFile.tsx | 重写（按旧项目 1:1 重建） |
| 22 | file-type-icon | 3 | src/component/FileManager/Explorer/FileTypeIcon.tsx | 重写（按旧项目 1:1 重建） |
| 23 | form-field | 3 | src/component/Common/StyledFormControl.tsx | 重写（按旧项目 1:1 重建） |
| 24 | graph | 3 | 旧项目无对应组件（目标自定义 RBAC 图）；以旧项目 MUI/主题规范为基准 | 重写（按旧项目 1:1 重建） |
| 25 | highlight-text | 3 | App.tsx .highlight-marker 样式 + react-highlight-words 用法 | 重写（按旧项目 1:1 重建） |
| 26 | icon | 6 | src/component/Icons/*.tsx（251 个 SvgIcon） | 重写（按旧项目 1:1 重建） |
| 27 | image-cropper | 3 | src/component/Pages/Setting/AvatarCropperDialog.tsx + Viewers/ImageViewer/ImageEditor.tsx | 重写（按旧项目 1:1 重建） |
| 28 | input | 3 | src/component/Common/StyledComponents.tsx FilledTextField / DenseFilledTextField + App.tsx MuiFilledInput | 重写（按旧项目 1:1 重建） |
| 29 | language-switcher | 3 | src/component/Common/LanguageSwitcher.tsx | 重写（按旧项目 1:1 重建） |
| 30 | logo | 3 | src/component/Common/Logo.tsx | 重写（按旧项目 1:1 重建） |
| 31 | motion | 3 | src/component/Frame/NavBar/NavIconTransition.tsx + Common/FadeTransition.css | 重写（按旧项目 1:1 重建） |
| 32 | oauth-app-card | 3 | src/component/Frame/OauthAppCard.tsx | 重写（按旧项目 1:1 重建） |
| 33 | otp-input | 3 | src/component/Pages/Login/Phases/Phase2FA.tsx (mui-one-time-password-input) | 重写（按旧项目 1:1 重建） |
| 34 | pagination | 3 | src/component/FileManager/Pagination/* + Admin/Common/TablePagination.tsx + App.tsx MuiPaginationItem | 重写（按旧项目 1:1 重建） |
| 35 | powered-by | 3 | src/component/Frame/PoweredBy.tsx | 重写（按旧项目 1:1 重建） |
| 36 | progress | 3 | src/component/Common/BorderLinearProgress.tsx + CircularProgress.tsx | 重写（按旧项目 1:1 重建） |
| 37 | progress-steps | 3 | src/component/Pages/Tasks/StepProgressBar.tsx | 重写（按旧项目 1:1 重建） |
| 38 | relative-time | 3 | src/component/Common/TimeBadge.tsx | 重写（按旧项目 1:1 重建） |
| 39 | responsive-tabs | 3 | src/component/Common/ResponsiveTabs.tsx + StyledComponents StyledTabs | 重写（按旧项目 1:1 重建） |
| 40 | search-input | 3 | src/component/Frame/NavBar/SearchBar.tsx | 重写（按旧项目 1:1 重建） |
| 41 | select | 3 | MUI Select + App.tsx MuiFilledInput/OutlinedInput 主题 | 重写（按旧项目 1:1 重建） |
| 42 | select-dialog | 3 | src/component/Dialogs/SelectOption.tsx | 重写（按旧项目 1:1 重建） |
| 43 | settings-panel | 4 | src/component/Admin/Settings/SettingWrapper.tsx | 重写（按旧项目 1:1 重建） |
| 44 | size-input | 3 | src/component/Common/SizeInput.tsx | 重写（按旧项目 1:1 重建） |
| 45 | skeleton | 3 | App.tsx MuiSkeleton (wave) + MUI Skeleton | 重写（按旧项目 1:1 重建） |
| 46 | split-pane | 3 | src/component/Frame/NavBar/SplitHandle.tsx | 重写（按旧项目 1:1 重建） |
| 47 | stat-card | 3 | src/component/Admin/Common/AdminCard.tsx + Admin/Home/Home.tsx 统计数据卡 | 重写（按旧项目 1:1 重建） |
| 48 | switch | 3 | MUI Switch（旧主题 color=primary） | 重写（按旧项目 1:1 重建） |
| 49 | table | 3 | MUI Table + StyledTableContainerPaper + 主题 MuiTableCell | 重写（按旧项目 1:1 重建） |
| 50 | tabs | 3 | src/component/Common/StyledComponents.tsx StyledTabs/StyledTab + App.tsx MuiTab | 重写（按旧项目 1:1 重建） |
| 51 | theme-switch | 3 | src/component/Frame/NavBar/DarkThemeSwitcher.tsx | 重写（按旧项目 1:1 重建） |
| 52 | toast | 3 | src/component/Common/Snackbar/*.tsx + App.tsx SnackbarProvider | 重写（按旧项目 1:1 重建） |
| 53 | tooltip | 3 | App.tsx MuiTooltip (enterDelay 500) + MUI Tooltip | 重写（按旧项目 1:1 重建） |
| 54 | tree-view | 3 | src/component/FileManager/TreeView/* | 重写（按旧项目 1:1 重建） |
| 55 | user | 4 | src/component/Common/User/UserBadge.tsx / UserPopover.tsx / UserAvatar.tsx | 重写（按旧项目 1:1 重建） |

## 文件级清单（全部 201 个文件）

| # | 路径 | 分类 | 动作 | 新位置 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 1 | package.json | 构建配置（非组件） | 重写 | components 包内保留（仅构建组件） | 移除 style.css 入口、去掉 token 导出与 fontsource/图标依赖 |
| 2 | src/components/accordion/accordion.module.css | 组件样式 | 重写 | src/components/accordion/accordion.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 3 | src/components/accordion/accordion.tsx | 组件源码 | 重写 | src/components/accordion/accordion.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 4 | src/components/accordion/index.ts | 组件入口 | 保留 | src/components/accordion/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 5 | src/components/alert/alert.module.css | 组件样式 | 重写 | src/components/alert/alert.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 6 | src/components/alert/alert.tsx | 组件源码 | 重写 | src/components/alert/alert.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 7 | src/components/alert/index.ts | 组件入口 | 保留 | src/components/alert/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 8 | src/components/animate-height/animate-height.module.css | 组件样式 | 重写 | src/components/animate-height/animate-height.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 9 | src/components/animate-height/animate-height.tsx | 组件源码 | 重写 | src/components/animate-height/animate-height.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 10 | src/components/animate-height/index.ts | 组件入口 | 保留 | src/components/animate-height/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 11 | src/components/avatar/avatar.module.css | 组件样式 | 重写 | src/components/avatar/avatar.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 12 | src/components/avatar/avatar.tsx | 组件源码 | 重写 | src/components/avatar/avatar.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 13 | src/components/avatar/index.ts | 组件入口 | 保留 | src/components/avatar/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 14 | src/components/badge/badge.module.css | 组件样式 | 重写 | src/components/badge/badge.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 15 | src/components/badge/badge.tsx | 组件源码 | 重写 | src/components/badge/badge.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 16 | src/components/badge/index.ts | 组件入口 | 保留 | src/components/badge/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 17 | src/components/breadcrumbs/breadcrumbs.module.css | 组件样式 | 重写 | src/components/breadcrumbs/breadcrumbs.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 18 | src/components/breadcrumbs/breadcrumbs.tsx | 组件源码 | 重写 | src/components/breadcrumbs/breadcrumbs.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 19 | src/components/breadcrumbs/index.ts | 组件入口 | 保留 | src/components/breadcrumbs/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 20 | src/components/button/button.module.css | 组件样式 | 重写 | src/components/button/button.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 21 | src/components/button/button.tsx | 组件源码 | 重写 | src/components/button/button.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 22 | src/components/button/index.ts | 组件入口 | 保留 | src/components/button/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 23 | src/components/captcha/captcha.module.css | 组件样式 | 重写 | src/components/captcha/captcha.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 24 | src/components/captcha/captcha.tsx | 组件源码 | 重写 | src/components/captcha/captcha.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 25 | src/components/captcha/index.ts | 组件入口 | 保留 | src/components/captcha/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 26 | src/components/card/card.module.css | 组件样式 | 重写 | src/components/card/card.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 27 | src/components/card/card.tsx | 组件源码 | 重写 | src/components/card/card.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 28 | src/components/card/index.ts | 组件入口 | 保留 | src/components/card/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 29 | src/components/chart/chart.module.css | 组件样式 | 重写 | src/components/chart/chart.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 30 | src/components/chart/chart.tsx | 组件源码 | 重写 | src/components/chart/chart.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 31 | src/components/chart/index.ts | 组件入口 | 保留 | src/components/chart/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 32 | src/components/checkbox/checkbox.module.css | 组件样式 | 重写 | src/components/checkbox/checkbox.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 33 | src/components/checkbox/checkbox.tsx | 组件源码 | 重写 | src/components/checkbox/checkbox.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 34 | src/components/checkbox/index.ts | 组件入口 | 保留 | src/components/checkbox/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 35 | src/components/code/code.module.css | 组件样式 | 重写 | src/components/code/code.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 36 | src/components/code/code.tsx | 组件源码 | 重写 | src/components/code/code.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 37 | src/components/code/index.ts | 组件入口 | 保留 | src/components/code/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 38 | src/components/confirm-dialog/confirm-dialog.module.css | 组件样式 | 重写 | src/components/confirm-dialog/confirm-dialog.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 39 | src/components/confirm-dialog/confirm-dialog.tsx | 组件源码 | 重写 | src/components/confirm-dialog/confirm-dialog.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 40 | src/components/confirm-dialog/index.ts | 组件入口 | 保留 | src/components/confirm-dialog/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 41 | src/components/date-picker/date-picker.module.css | 组件样式 | 重写 | src/components/date-picker/date-picker.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 42 | src/components/date-picker/date-picker.tsx | 组件源码 | 重写 | src/components/date-picker/date-picker.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 43 | src/components/date-picker/index.ts | 组件入口 | 保留 | src/components/date-picker/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 44 | src/components/description-list/description-list.module.css | 组件样式 | 重写 | src/components/description-list/description-list.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 45 | src/components/description-list/description-list.tsx | 组件源码 | 重写 | src/components/description-list/description-list.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 46 | src/components/description-list/index.ts | 组件入口 | 保留 | src/components/description-list/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 47 | src/components/dialog/dialog.module.css | 组件样式 | 重写 | src/components/dialog/dialog.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 48 | src/components/dialog/dialog.tsx | 组件源码 | 重写 | src/components/dialog/dialog.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 49 | src/components/dialog/index.ts | 组件入口 | 保留 | src/components/dialog/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 50 | src/components/dropdown-menu/dropdown-menu.module.css | 组件样式 | 重写 | src/components/dropdown-menu/dropdown-menu.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 51 | src/components/dropdown-menu/dropdown-menu.tsx | 组件源码 | 重写 | src/components/dropdown-menu/dropdown-menu.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 52 | src/components/dropdown-menu/index.ts | 组件入口 | 保留 | src/components/dropdown-menu/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 53 | src/components/empty-state/empty-state.module.css | 组件样式 | 重写 | src/components/empty-state/empty-state.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 54 | src/components/empty-state/empty-state.tsx | 组件源码 | 重写 | src/components/empty-state/empty-state.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 55 | src/components/empty-state/index.ts | 组件入口 | 保留 | src/components/empty-state/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 56 | src/components/error-detail-dialog/error-detail-dialog.module.css | 组件样式 | 重写 | src/components/error-detail-dialog/error-detail-dialog.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 57 | src/components/error-detail-dialog/error-detail-dialog.tsx | 组件源码 | 重写 | src/components/error-detail-dialog/error-detail-dialog.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 58 | src/components/error-detail-dialog/index.ts | 组件入口 | 保留 | src/components/error-detail-dialog/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 59 | src/components/error-state/error-state.tsx | 组件源码 | 重写 | src/components/error-state/error-state.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 60 | src/components/error-state/index.ts | 组件入口 | 保留 | src/components/error-state/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 61 | src/components/file-drop-zone/file-drop-zone.module.css | 组件样式 | 重写 | src/components/file-drop-zone/file-drop-zone.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 62 | src/components/file-drop-zone/file-drop-zone.tsx | 组件源码 | 重写 | src/components/file-drop-zone/file-drop-zone.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 63 | src/components/file-drop-zone/index.ts | 组件入口 | 保留 | src/components/file-drop-zone/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 64 | src/components/file-type-icon/file-type-icon.module.css | 组件样式 | 重写 | src/components/file-type-icon/file-type-icon.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 65 | src/components/file-type-icon/file-type-icon.tsx | 组件源码 | 重写 | src/components/file-type-icon/file-type-icon.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 66 | src/components/file-type-icon/index.ts | 组件入口 | 保留 | src/components/file-type-icon/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 67 | src/components/form-field/form-field.module.css | 组件样式 | 重写 | src/components/form-field/form-field.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 68 | src/components/form-field/form-field.tsx | 组件源码 | 重写 | src/components/form-field/form-field.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 69 | src/components/form-field/index.ts | 组件入口 | 保留 | src/components/form-field/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 70 | src/components/graph/graph.module.css | 组件样式 | 重写 | src/components/graph/graph.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 71 | src/components/graph/graph.tsx | 组件源码 | 重写 | src/components/graph/graph.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 72 | src/components/graph/index.ts | 组件入口 | 保留 | src/components/graph/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 73 | src/components/highlight-text/highlight-text.module.css | 组件样式 | 重写 | src/components/highlight-text/highlight-text.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 74 | src/components/highlight-text/highlight-text.tsx | 组件源码 | 重写 | src/components/highlight-text/highlight-text.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 75 | src/components/highlight-text/index.ts | 组件入口 | 保留 | src/components/highlight-text/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 76 | src/components/icon/builtin-icons.ts | 图标集合/注册表（非组件） | 移出组件库 | web/packages/icons/src/builtin-icons.ts | 新建 @pudans/icons；Icon 组件依赖其注册表 |
| 77 | src/components/icon/cloudreve-icons.ts | 图标集合/注册表（非组件） | 移出组件库 | web/packages/icons/src/cloudreve-icons.ts | 新建 @pudans/icons；Icon 组件依赖其注册表 |
| 78 | src/components/icon/icon-registry.ts | 图标集合/注册表（非组件） | 移出组件库 | web/packages/icons/src/icon-registry.ts | 新建 @pudans/icons；Icon 组件依赖其注册表 |
| 79 | src/components/icon/icon.module.css | 组件（Icon） | 重写 | src/components/icon/icon.module.css | 图标渲染组件保留；集合数据移出 |
| 80 | src/components/icon/icon.tsx | 组件（Icon） | 重写 | src/components/icon/icon.tsx | 图标渲染组件保留；集合数据移出 |
| 81 | src/components/icon/index.ts | 组件（Icon） | 重写 | src/components/icon/index.ts | 图标渲染组件保留；集合数据移出 |
| 82 | src/components/image-cropper/image-cropper.module.css | 组件样式 | 重写 | src/components/image-cropper/image-cropper.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 83 | src/components/image-cropper/image-cropper.tsx | 组件源码 | 重写 | src/components/image-cropper/image-cropper.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 84 | src/components/image-cropper/index.ts | 组件入口 | 保留 | src/components/image-cropper/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 85 | src/components/index.ts | 组件入口 | 保留 | src/components/index.ts | 仅聚合 55 个组件目录 |
| 86 | src/components/input/index.ts | 组件入口 | 保留 | src/components/input/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 87 | src/components/input/input.module.css | 组件样式 | 重写 | src/components/input/input.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 88 | src/components/input/input.tsx | 组件源码 | 重写 | src/components/input/input.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 89 | src/components/language-switcher/index.ts | 组件入口 | 保留 | src/components/language-switcher/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 90 | src/components/language-switcher/language-switcher.module.css | 组件样式 | 重写 | src/components/language-switcher/language-switcher.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 91 | src/components/language-switcher/language-switcher.tsx | 组件源码 | 重写 | src/components/language-switcher/language-switcher.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 92 | src/components/logo/index.ts | 组件入口 | 保留 | src/components/logo/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 93 | src/components/logo/logo.module.css | 组件样式 | 重写 | src/components/logo/logo.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 94 | src/components/logo/logo.tsx | 组件源码 | 重写 | src/components/logo/logo.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 95 | src/components/motion/index.ts | 组件入口 | 保留 | src/components/motion/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 96 | src/components/motion/motion.module.css | 组件样式 | 重写 | src/components/motion/motion.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 97 | src/components/motion/motion.tsx | 组件源码 | 重写 | src/components/motion/motion.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 98 | src/components/oauth-app-card/index.ts | 组件入口 | 保留 | src/components/oauth-app-card/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 99 | src/components/oauth-app-card/oauth-app-card.module.css | 组件样式 | 重写 | src/components/oauth-app-card/oauth-app-card.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 100 | src/components/oauth-app-card/oauth-app-card.tsx | 组件源码 | 重写 | src/components/oauth-app-card/oauth-app-card.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 101 | src/components/otp-input/index.ts | 组件入口 | 保留 | src/components/otp-input/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 102 | src/components/otp-input/otp-input.module.css | 组件样式 | 重写 | src/components/otp-input/otp-input.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 103 | src/components/otp-input/otp-input.tsx | 组件源码 | 重写 | src/components/otp-input/otp-input.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 104 | src/components/pagination/index.ts | 组件入口 | 保留 | src/components/pagination/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 105 | src/components/pagination/pagination.module.css | 组件样式 | 重写 | src/components/pagination/pagination.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 106 | src/components/pagination/pagination.tsx | 组件源码 | 重写 | src/components/pagination/pagination.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 107 | src/components/powered-by/index.ts | 组件入口 | 保留 | src/components/powered-by/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 108 | src/components/powered-by/powered-by.module.css | 组件样式 | 重写 | src/components/powered-by/powered-by.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 109 | src/components/powered-by/powered-by.tsx | 组件源码 | 重写 | src/components/powered-by/powered-by.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 110 | src/components/progress/index.ts | 组件入口 | 保留 | src/components/progress/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 111 | src/components/progress/progress.module.css | 组件样式 | 重写 | src/components/progress/progress.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 112 | src/components/progress/progress.tsx | 组件源码 | 重写 | src/components/progress/progress.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 113 | src/components/progress-steps/index.ts | 组件入口 | 保留 | src/components/progress-steps/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 114 | src/components/progress-steps/progress-steps.module.css | 组件样式 | 重写 | src/components/progress-steps/progress-steps.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 115 | src/components/progress-steps/progress-steps.tsx | 组件源码 | 重写 | src/components/progress-steps/progress-steps.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 116 | src/components/relative-time/index.ts | 组件入口 | 保留 | src/components/relative-time/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 117 | src/components/relative-time/relative-time.module.css | 组件样式 | 重写 | src/components/relative-time/relative-time.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 118 | src/components/relative-time/relative-time.tsx | 组件源码 | 重写 | src/components/relative-time/relative-time.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 119 | src/components/responsive-tabs/index.ts | 组件入口 | 保留 | src/components/responsive-tabs/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 120 | src/components/responsive-tabs/responsive-tabs.module.css | 组件样式 | 重写 | src/components/responsive-tabs/responsive-tabs.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 121 | src/components/responsive-tabs/responsive-tabs.tsx | 组件源码 | 重写 | src/components/responsive-tabs/responsive-tabs.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 122 | src/components/search-input/index.ts | 组件入口 | 保留 | src/components/search-input/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 123 | src/components/search-input/search-input.module.css | 组件样式 | 重写 | src/components/search-input/search-input.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 124 | src/components/search-input/search-input.tsx | 组件源码 | 重写 | src/components/search-input/search-input.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 125 | src/components/select/index.ts | 组件入口 | 保留 | src/components/select/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 126 | src/components/select/select.module.css | 组件样式 | 重写 | src/components/select/select.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 127 | src/components/select/select.tsx | 组件源码 | 重写 | src/components/select/select.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 128 | src/components/select-dialog/index.ts | 组件入口 | 保留 | src/components/select-dialog/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 129 | src/components/select-dialog/select-dialog.module.css | 组件样式 | 重写 | src/components/select-dialog/select-dialog.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 130 | src/components/select-dialog/select-dialog.tsx | 组件源码 | 重写 | src/components/select-dialog/select-dialog.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 131 | src/components/settings-panel/index.ts | 组件入口 | 保留 | src/components/settings-panel/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 132 | src/components/settings-panel/settings-panel.module.css | 组件样式 | 重写 | src/components/settings-panel/settings-panel.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 133 | src/components/settings-panel/settings-panel.tsx | 组件源码 | 重写 | src/components/settings-panel/settings-panel.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 134 | src/components/settings-panel/use-dirty-state.ts | 组件内部 hook（非独立组件） | 删除 | 内联进 settings-panel.tsx | 组件库不允许独立 hooks 文件；逻辑内联为组件私有实现 |
| 135 | src/components/size-input/index.ts | 组件入口 | 保留 | src/components/size-input/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 136 | src/components/size-input/size-input.module.css | 组件样式 | 重写 | src/components/size-input/size-input.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 137 | src/components/size-input/size-input.tsx | 组件源码 | 重写 | src/components/size-input/size-input.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 138 | src/components/skeleton/index.ts | 组件入口 | 保留 | src/components/skeleton/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 139 | src/components/skeleton/skeleton.module.css | 组件样式 | 重写 | src/components/skeleton/skeleton.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 140 | src/components/skeleton/skeleton.tsx | 组件源码 | 重写 | src/components/skeleton/skeleton.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 141 | src/components/split-pane/index.ts | 组件入口 | 保留 | src/components/split-pane/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 142 | src/components/split-pane/split-pane.module.css | 组件样式 | 重写 | src/components/split-pane/split-pane.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 143 | src/components/split-pane/split-pane.tsx | 组件源码 | 重写 | src/components/split-pane/split-pane.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 144 | src/components/stat-card/index.ts | 组件入口 | 保留 | src/components/stat-card/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 145 | src/components/stat-card/stat-card.module.css | 组件样式 | 重写 | src/components/stat-card/stat-card.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 146 | src/components/stat-card/stat-card.tsx | 组件源码 | 重写 | src/components/stat-card/stat-card.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 147 | src/components/switch/index.ts | 组件入口 | 保留 | src/components/switch/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 148 | src/components/switch/switch.module.css | 组件样式 | 重写 | src/components/switch/switch.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 149 | src/components/switch/switch.tsx | 组件源码 | 重写 | src/components/switch/switch.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 150 | src/components/table/index.ts | 组件入口 | 保留 | src/components/table/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 151 | src/components/table/table.module.css | 组件样式 | 重写 | src/components/table/table.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 152 | src/components/table/table.tsx | 组件源码 | 重写 | src/components/table/table.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 153 | src/components/tabs/index.ts | 组件入口 | 保留 | src/components/tabs/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 154 | src/components/tabs/tabs.module.css | 组件样式 | 重写 | src/components/tabs/tabs.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 155 | src/components/tabs/tabs.tsx | 组件源码 | 重写 | src/components/tabs/tabs.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 156 | src/components/theme-switch/index.ts | 组件入口 | 保留 | src/components/theme-switch/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 157 | src/components/theme-switch/theme-switch.module.css | 组件样式 | 重写 | src/components/theme-switch/theme-switch.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 158 | src/components/theme-switch/theme-switch.tsx | 组件源码 | 重写 | src/components/theme-switch/theme-switch.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 159 | src/components/toast/index.ts | 组件入口 | 保留 | src/components/toast/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 160 | src/components/toast/toast.module.css | 组件样式 | 重写 | src/components/toast/toast.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 161 | src/components/toast/toast.tsx | 组件源码 | 重写 | src/components/toast/toast.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 162 | src/components/tooltip/index.ts | 组件入口 | 保留 | src/components/tooltip/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 163 | src/components/tooltip/tooltip.module.css | 组件样式 | 重写 | src/components/tooltip/tooltip.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 164 | src/components/tooltip/tooltip.tsx | 组件源码 | 重写 | src/components/tooltip/tooltip.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 165 | src/components/tree-view/index.ts | 组件入口 | 保留 | src/components/tree-view/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 166 | src/components/tree-view/tree-view.module.css | 组件样式 | 重写 | src/components/tree-view/tree-view.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 167 | src/components/tree-view/tree-view.tsx | 组件源码 | 重写 | src/components/tree-view/tree-view.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 168 | src/components/user/index.ts | 组件入口 | 保留 | src/components/user/index.ts | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 169 | src/components/user/user-badge.tsx | 组件源码 | 重写 | src/components/user/user-badge.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 170 | src/components/user/user-popover.tsx | 组件源码 | 重写 | src/components/user/user-popover.tsx | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 171 | src/components/user/user.module.css | 组件样式 | 重写 | src/components/user/user.module.css | 按 cloudreve/frontend 原实现重建布局/间距/字体/动效/状态 |
| 172 | src/hooks/index.ts | 通用 hooks（非组件） | 移出组件库 | web/packages/hooks/src/index.ts | 新建 @pudans/hooks |
| 173 | src/hooks/use-delayed-hover.ts | 通用 hooks（非组件） | 移出组件库 | web/packages/hooks/src/use-delayed-hover.ts | 新建 @pudans/hooks |
| 174 | src/hooks/use-media-session.ts | 通用 hooks（非组件） | 移出组件库 | web/packages/hooks/src/use-media-session.ts | 新建 @pudans/hooks |
| 175 | src/hooks/use-overflow.ts | 通用 hooks（非组件） | 移出组件库 | web/packages/hooks/src/use-overflow.ts | 新建 @pudans/hooks |
| 176 | src/index.ts | 包入口 | 重写 | components/src/index.ts | 只导出组件与组件相关类型；不再导出 hooks/layouts/theme/utils/icons |
| 177 | src/layouts/app-shell/app-shell.module.css | 布局（非组件） | 移出组件库 | web/packages/layouts/src/app-shell/app-shell.module.css | 新建 @pudans/layouts，依赖 components + theme |
| 178 | src/layouts/app-shell/app-shell.tsx | 布局（非组件） | 移出组件库 | web/packages/layouts/src/app-shell/app-shell.tsx | 新建 @pudans/layouts，依赖 components + theme |
| 179 | src/layouts/app-shell/index.ts | 布局（非组件） | 移出组件库 | web/packages/layouts/src/app-shell/index.ts | 新建 @pudans/layouts，依赖 components + theme |
| 180 | src/layouts/app-shell/side-nav-item.module.css | 布局（非组件） | 移出组件库 | web/packages/layouts/src/app-shell/side-nav-item.module.css | 新建 @pudans/layouts，依赖 components + theme |
| 181 | src/layouts/app-shell/side-nav-item.tsx | 布局（非组件） | 移出组件库 | web/packages/layouts/src/app-shell/side-nav-item.tsx | 新建 @pudans/layouts，依赖 components + theme |
| 182 | src/layouts/index.ts | 布局（非组件） | 移出组件库 | web/packages/layouts/src/index.ts | 新建 @pudans/layouts，依赖 components + theme |
| 183 | src/layouts/page-container/index.ts | 布局（非组件） | 移出组件库 | web/packages/layouts/src/page-container/index.ts | 新建 @pudans/layouts，依赖 components + theme |
| 184 | src/layouts/page-container/page-container.module.css | 布局（非组件） | 移出组件库 | web/packages/layouts/src/page-container/page-container.module.css | 新建 @pudans/layouts，依赖 components + theme |
| 185 | src/layouts/page-container/page-container.tsx | 布局（非组件） | 移出组件库 | web/packages/layouts/src/page-container/page-container.tsx | 新建 @pudans/layouts，依赖 components + theme |
| 186 | src/layouts/page-layout/index.ts | 布局（非组件） | 移出组件库 | web/packages/layouts/src/page-layout/index.ts | 新建 @pudans/layouts，依赖 components + theme |
| 187 | src/layouts/page-layout/page-layout.module.css | 布局（非组件） | 移出组件库 | web/packages/layouts/src/page-layout/page-layout.module.css | 新建 @pudans/layouts，依赖 components + theme |
| 188 | src/layouts/page-layout/page-layout.tsx | 布局（非组件） | 移出组件库 | web/packages/layouts/src/page-layout/page-layout.tsx | 新建 @pudans/layouts，依赖 components + theme |
| 189 | src/layouts/sidebar-layout/index.ts | 布局（非组件） | 移出组件库 | web/packages/layouts/src/sidebar-layout/index.ts | 新建 @pudans/layouts，依赖 components + theme |
| 190 | src/layouts/sidebar-layout/sidebar-layout.module.css | 布局（非组件） | 移出组件库 | web/packages/layouts/src/sidebar-layout/sidebar-layout.module.css | 新建 @pudans/layouts，依赖 components + theme |
| 191 | src/layouts/sidebar-layout/sidebar-layout.tsx | 布局（非组件） | 移出组件库 | web/packages/layouts/src/sidebar-layout/sidebar-layout.tsx | 新建 @pudans/layouts，依赖 components + theme |
| 192 | src/styles/create-theme.ts | 主题/token（非组件） | 移出组件库 | web/packages/theme/src/create-theme.ts | 新建 @pudans/theme；组件库以 peer/依赖方式消费 |
| 193 | src/styles/global.css | 主题/token（非组件） | 移出组件库 | web/packages/theme/src/global.css | 新建 @pudans/theme；组件库以 peer/依赖方式消费 |
| 194 | src/styles/index.css | 主题/token（非组件） | 移出组件库 | web/packages/theme/src/index.css | 新建 @pudans/theme；组件库以 peer/依赖方式消费 |
| 195 | src/styles/theme-provider.tsx | 主题/token（非组件） | 移出组件库 | web/packages/theme/src/theme-provider.tsx | 新建 @pudans/theme；组件库以 peer/依赖方式消费 |
| 196 | src/styles/tokens.ts | 主题/token（非组件） | 移出组件库 | web/packages/theme/src/tokens.ts | 新建 @pudans/theme；组件库以 peer/依赖方式消费 |
| 197 | src/styles/variables.css | 主题/token（非组件） | 移出组件库 | web/packages/theme/src/variables.css | 新建 @pudans/theme；组件库以 peer/依赖方式消费 |
| 198 | src/utils/cn.ts | 通用 utils（非组件） | 移出组件库 | web/packages/utils/src/cn.ts | 并入 @pudans/utils（由 @pudans/shared 合并而来） |
| 199 | src/utils/index.ts | 通用 utils（非组件） | 移出组件库 | web/packages/utils/src/index.ts | 并入 @pudans/utils（由 @pudans/shared 合并而来） |
| 200 | tsconfig.json | 构建配置（非组件） | 重写 | components 包内保留（仅构建组件） | 移除 style.css 入口、去掉 token 导出与 fontsource/图标依赖 |
| 201 | vite.config.ts | 构建配置（非组件） | 重写 | components 包内保留（仅构建组件） | 移除 style.css 入口、去掉 token 导出与 fontsource/图标依赖 |

## 阻断项

1. 旧项目 cloudreve/frontend 未安装 node_modules 且无后端服务，无法直接启动旧应用做像素级截图；本次以旧源码的显式数值（theme.spacing、typography variant、palette、shape、App.tsx 覆盖）作为 1:1 基准，并在新组件库中生成截图作为回归基线。
2. 旧项目部分能力依赖 cloudreve redux/api（例如 FileTypeIcon 的站点图标配置、Breadcrumb 的云盘路径）；组件库重做时以 props/context 注入等价的静态数据，默认行为保持旧视觉。


## 执行结果（2026-11-16）

| 处置 | 数量 | 结果 |
| --- | --- | --- |
| 移出组件库 | 30 | hooks→packages/hooks（4）、utils→packages/utils（2）、layouts→packages/layouts（12）、styles/theme→packages/theme（6）、图标集合/注册表→packages/icons（3）、其它入口 3 |
| 删除/内联 | 1 | settings-panel/use-dirty-state.ts 已内联进组件（组件库不允许独立 hook 文件） |
| 重写 | 112 | 55 个组件的 tsx/CSS/barrel 全部按旧项目重写（index.ts barrel 保留 55） |
| 保留为组件入口 | 55 | 组件目录 index.ts 保留 |
| 边界门禁 | — | node scripts/check-component-boundary.mjs 通过；check-exports.mjs 通过（55 目录 / 222 命名导出 / 纯 components barrel） |

组件库现在只包含：

    web/packages/components/src/
      index.ts                # 只导出 components
      components/<55 dirs>/    # tsx + module.css + index.ts
      components/index.ts

依赖：@pudans/theme、@pudans/icons、@pudans/utils（+ react/MUI/Emotion peer）。无 @web/*、react-router、业务 store/API、i18next、hooks/layouts 依赖。
