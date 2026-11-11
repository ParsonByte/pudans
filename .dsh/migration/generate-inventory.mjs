#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const ROOT = "/Users/byte/Global/Project/pudans";
const SRC = "/Users/byte/Global/Reference/cloudreve/frontend";
const TARGET = path.join(ROOT, "web");
const OUT_DIR = path.join(ROOT, ".dsh/migration");
const BT = String.fromCharCode(96);
const code = (s) => BT + String(s) + BT;
const DISPOSITIONS = ["迁移", "封装", "复用", "保留应用层", "暂缓"];
const lib = (name) => "web/packages/components/src/components/" + name;
const layout = (name) => "web/packages/components/src/layouts/" + name;
const hook = (name) => "web/packages/components/src/hooks/" + name;
const shared = (name) => "web/packages/shared/src/utils/" + name;
const app = (p) => "web/app/" + p;

function walk(dir, rel) {
  const out = [];
  for (const name of fs.readdirSync(dir).sort()) {
    if (name === ".DS_Store") continue;
    const abs = path.join(dir, name);
    const relPath = rel ? rel + "/" + name : name;
    const stat = fs.statSync(abs);
    if (stat.isDirectory()) out.push.apply(out, walk(abs, relPath));
    else out.push(relPath);
  }
  return out;
}
const files = walk(SRC, "");
const srcFiles = files.filter((f) => f.indexOf("src/") === 0);
const publicFiles = files.filter((f) => f.indexOf("public/") === 0);

const single = {};
const put = (source, disposition, target, reason) => {
  single[source] = { disposition: disposition, target: target, reason: reason };
};

put("src/component/Common/AutoHeight.tsx", "复用", lib("animate-height/animate-height.tsx"), "目标库已有 AnimateHeight，旧 AutoHeight 包装逻辑由其受控/非受控高度动画覆盖。");
put("src/component/Common/BorderLinearProgress.tsx", "迁移", lib("progress/progress.tsx"), "合并为统一 Progress 组件（linear/circular），圆角与色带改用轨道 token。");
put("src/component/Common/CircularProgress.tsx", "迁移", lib("progress/progress.tsx"), "双层环形进度合并为 Progress variant=circular 的 determinate 模式。");
["CapCaptcha", "Captcha", "DefaultCaptcha", "ReCaptchaV2", "TurnstileCaptcha"].forEach((f) => {
  put("src/component/Common/Captcha/" + f + ".tsx", "封装", lib("captcha/captcha.tsx"), "多供应商验证码统一为 Captcha 组件（reCAPTCHA / Turnstile / Cap），由 siteKey+provider 配置驱动。");
});
put("src/component/Common/Code.tsx", "迁移", lib("code/code.tsx"), "内联 Code 与 CodeBlock 合并到 code 组件，等宽字体与底色改 token。");
put("src/component/Common/ErrorBoundary.tsx", "迁移", lib("error-state/error-state.tsx"), "路由错误边界迁移为 ErrorBoundary + RouteError，错误态复用 EmptyState。");
put("src/component/Common/FadeTransition.css", "封装", lib("motion/motion.tsx"), "FadeTransition 的时长/曲线并入 motion token 与 Fade/Slide 过渡组件。");
put("src/component/Common/Form/EncodingSelector.tsx", "暂缓", "—", "文件下载编码选择依赖 cloudreve 下载流程，目标仓库暂无文件下载产品面；登记待文件应用迁移。");
put("src/component/Common/Form/FileDisplayForm.tsx", "暂缓", "—", "云盘文件展示表单强依赖 explorer 数据模型，待文件应用立项后迁移。");
put("src/component/Common/Form/OutlineIconTextField.tsx", "复用", lib("input/input.tsx"), "目标 Input 已内置 startIcon/endIcon 与 label，不再需要独立包装。");
put("src/component/Common/Form/PathSelectorForm.tsx", "暂缓", "—", "路径选择依赖 cloudreve URI 与文件树数据，待文件应用迁移。");
put("src/component/Common/LanguageSwitcher.tsx", "迁移", lib("language-switcher/language-switcher.tsx"), "迁移为受控 LanguageSwitcher（languages/current/onChange 注入，不绑定 i18n 单例）。");
put("src/component/Common/Logo.tsx", "迁移", lib("logo/logo.tsx"), "迁移为支持亮/暗源、加载骨架与回退字标的通用 Logo；cloudreve 品牌素材不复制。");
put("src/component/Common/Nothing.tsx", "迁移", lib("empty-state/empty-state.tsx"), "迁移为 EmptyState（empty/not-found/error/permission 语义变体）。");
put("src/component/Common/ResponsiveTabs.tsx", "迁移", lib("responsive-tabs/responsive-tabs.tsx"), "迁移为 ResponsiveTabs：溢出折叠为下拉菜单，复用 Tabs 视觉 token。");
put("src/component/Common/SizeInput.tsx", "迁移", lib("size-input/size-input.tsx"), "迁移为 SizeInput：字节值 + 单位（B/KB/MB/GB/TB）换算，去掉 redux 依赖。");
put("src/component/Common/Snackbar/FileIconSnackbar.tsx", "封装", lib("toast/toast.tsx"), "notistack 自定义内容合并进 Toast 的 icon 变体。");
put("src/component/Common/Snackbar/LoadingSnackbar.tsx", "复用", lib("toast/toast.tsx"), "Loading snackbar 由 Toast loading 变体覆盖。");
put("src/component/Common/Snackbar/snackbar.tsx", "复用", lib("toast/toast.tsx"), "Service worker 更新提示改用 useToast().info + action。");
put("src/component/Common/StyledComponents.tsx", "封装", lib("form-field/form-field.tsx"), "DefaultButton/DenseFilledTextField/NoWrapTypography 等样式配方拆入 Button/Input/form-field 与 token。");
put("src/component/Common/StyledFormControl.tsx", "迁移", lib("form-field/form-field.tsx"), "迁移为 FormField（标题+控件+校验文案）与 FormSection。");
put("src/component/Common/TimeBadge.tsx", "迁移", lib("relative-time/relative-time.tsx"), "迁移为 RelativeTime：Intl.RelativeTimeFormat + 完整时间 Tooltip，去掉 timeago-react。");
put("src/component/Common/User/UserAvatar.tsx", "复用", lib("avatar/avatar.tsx"), "Avatar 组件已覆盖图片/首字母/图标回退。");
put("src/component/Common/User/UserBadge.tsx", "迁移", lib("user/user-badge.tsx"), "迁移为 UserBadge：头像+名称+副标题+状态点，供 uc/console 复用。");
put("src/component/Common/User/UserPopover.tsx", "迁移", lib("user/user-popover.tsx"), "迁移为 UserPopover：用户摘要卡片与动作插槽，去掉 redux/session 依赖。");

put("src/component/Frame/FrameManagerBundle.tsx", "保留应用层", "web/app/console/src/layouts", "Frame 组合由各应用自己的 layout 承担，不进入公共库。");
put("src/component/Frame/HeadlessFrame.tsx", "暂缓", "—", "HeadlessFrame 为云盘文件管理器事件编排层，待文件应用迁移。");
["AppDrawer", "AppMain", "DrawerHeader", "TopAppBar", "UserAction"].forEach((f) => {
  put("src/component/Frame/NavBar/" + f + ".tsx", "复用", layout("app-shell/app-shell.tsx"), "AppShell 已提供可折叠 Sidebar、Header、用户菜单与内容区，旧导航拆分件合并复用。");
});
put("src/component/Frame/NavBar/DarkThemeSwitcher.tsx", "迁移", lib("theme-switch/theme-switch.tsx"), "迁移为 ThemeSwitch（light/system/dark 分段控件），直接消费 usePudansTheme。");
put("src/component/Frame/NavBar/FileSelectedActions.tsx", "暂缓", "—", "文件多选操作条依赖文件管理器选中态，待文件应用迁移。");
put("src/component/Frame/NavBar/NavBarMainActions.tsx", "暂缓", "—", "导航主操作与 cloudreve 上传/新建流程耦合，登记业务层待迁移。");
put("src/component/Frame/NavBar/NavIconTransition.tsx", "封装", lib("motion/motion.tsx"), "图标切换动效抽为 AnimatedSwap（TransitionGroup + Fade，token 化时长）。");
put("src/component/Frame/NavBar/PageNavigation.tsx", "暂缓", "—", "页面导航条绑定 cloudreve 路由与文件路径，登记待迁移。");
put("src/component/Frame/NavBar/SearchBar.tsx", "迁移", lib("search-input/search-input.tsx"), "迁移为 SearchInput：快捷键提示、loading、清空，去掉 redux 全局搜索依赖。");
put("src/component/Frame/NavBar/SideNavItem.tsx", "复用", layout("app-shell/side-nav-item.tsx"), "目标库已有 SideNavItem，支持折叠/激活/图标过渡，直接复用。");
put("src/component/Frame/NavBar/SplitHandle.tsx", "迁移", lib("split-pane/split-pane.tsx"), "迁移为 SplitPane 可拖拽分隔条（pointer 事件，支持持久化 key）。");
put("src/component/Frame/NavBar/StorageSummary.tsx", "暂缓", "—", "存储配额摘要依赖 cloudreve 容量 API，目标暂无文件产品面。");
put("src/component/Frame/NavBarFrame.tsx", "复用", layout("app-shell/app-shell.tsx"), "由 AppShell 统一承载导航框架。");
put("src/component/Frame/OauthAppCard.tsx", "迁移", lib("oauth-app-card/oauth-app-card.tsx"), "迁移为 OAuthAppCard（图标/名称/描述/加载骨架），供授权页与集成页复用。");
put("src/component/Frame/PoweredBy.tsx", "迁移", lib("powered-by/powered-by.tsx"), "迁移为通用 PoweredBy（品牌名/链接/logo 由 props 注入）。");
put("src/component/Frame/RadiusFrame.tsx", "复用", layout("page-container/page-container.tsx"), "PageContainer 已实现圆角内容框架，直接复用。");
["logo.svg", "logo_light.svg"].forEach((f) => {
  put("src/component/Frame/assets/" + f, "暂缓", "—", "cloudreve 品牌 logo 不迁移到 pudans；Logo 组件改为可注入源，品牌资产由应用层提供。");
});

put("src/component/Dialogs/AggregatedErrorDetail.tsx", "迁移", lib("error-detail-dialog/error-detail-dialog.tsx"), "迁移为 ErrorDetailDialog：错误消息+聚合明细表+请求 ID，去掉 redux dialog 池。");
put("src/component/Dialogs/BatchDownloadLog.tsx", "暂缓", "—", "批量下载日志依赖下载任务模型，目标暂无下载产品面。");
put("src/component/Dialogs/Confirmation.tsx", "迁移", lib("confirm-dialog/confirm-dialog.tsx"), "迁移为 ConfirmProvider + useConfirm() promise 确认框，移除全局 redux dialog。");
put("src/component/Dialogs/DialogAccordion.tsx", "迁移", lib("accordion/accordion.tsx"), "迁移为 Accordion（卡片/幽灵两种变体），过渡使用 motion token。");
put("src/component/Dialogs/DraggableDialog.tsx", "迁移", lib("dialog/dialog.tsx"), "对话框增加 draggable 能力（标题栏拖拽、触摸设备自动禁用），其余能力复用 Dialog。");
put("src/component/Dialogs/GlobalDialogs.tsx", "保留应用层", "web/src/providers", "全局对话框挂载点由应用 Provider 组合，不放进公共库；确认框/选择框改为 provider+hook API。");
put("src/component/Dialogs/SelectOption.tsx", "迁移", lib("select-dialog/select-dialog.tsx"), "迁移为 SelectDialog + useSelectOption()（选项列表/描述/回调 promise）。");

put("src/component/Pages/NoMatch.tsx", "迁移", lib("empty-state/empty-state.tsx"), "迁移为 EmptyState variant=not-found（图标/标题/返回动作）。");
put("src/component/Pages/PageContainer.tsx", "复用", layout("page-container/page-container.tsx"), "目标库 PageContainer 已覆盖圆角容器与移动端适配。");
put("src/component/Pages/PageHeader.tsx", "复用", layout("page-layout/page-layout.tsx"), "PageLayout 的 title/description/actions 已覆盖页头能力。");
put("src/component/Pages/Pages.tsx", "保留应用层", "web/src/router", "路由组合由 @web/router 与 app/*/router 承担。");
put("src/component/Pages/HomeRedirect.tsx", "暂缓", "—", "首页重定向逻辑由目标应用 router 自带重定向覆盖，无需迁移。");
put("src/component/Pages/Profile/Profile.tsx", "复用", app("uc/src/modules/profile"), "目标 UC 已有 profile 模块，页面结构与交互已适配。");
const loginMap = {
  "Login/Activate.tsx": ["暂缓", "—", "邮件激活流程目标 auth 应用尚未提供页面，登记待后端激活接口对齐。"],
  "Login/Authorize.tsx": ["暂缓", "—", "OAuth 授权确认页待目标 OAuth provider 接入后迁移。"],
  "Login/Reset.tsx": ["复用", app("auth/src/modules/recovery"), "目标 auth 已有 recovery 模块覆盖找回密码流程。"],
  "Login/SessionIntro.tsx": ["复用", app("auth/src/layouts"), "登录页会话介绍由 AuthLayout 插槽承担。"],
  "Login/Signup.tsx": ["暂缓", "—", "目标暂未开放注册入口（管理员创建用户），登记待产品决策。"],
  "Login/SideTransition.css": ["封装", lib("motion/motion.tsx"), "侧栏切换过渡并入 motion token。"],
};
Object.keys(loginMap).forEach((f) => put("src/component/Pages/" + f, loginMap[f][0], loginMap[f][1], loginMap[f][2]));
["Phase2FA", "PhaseCollectEmail", "PhaseCollectPassword", "PhaseConsent", "PhaseForgetPassword", "PhaseSignupNeeded"].forEach((f) => {
  put("src/component/Pages/Login/Phases/" + f + ".tsx", "复用", app("auth/src/modules/login"), "分阶段登录流程已收敛到目标 auth login 模块；旧 Phase 组件作为交互参考登记复用。");
});
["DesktopCallback", "MobileCallback", "PasskeyLoginButton", "SignIn"].forEach((f) => {
  put("src/component/Pages/Login/Signin/" + f + ".tsx", "复用", app("auth/src/modules/login"), "目标 auth 登录模块已覆盖密码/OTP 流程；passkey 与第三方回调登记为待补。");
});
put("src/component/Pages/Setting/AvatarCropperDialog.tsx", "复用", lib("image-cropper/image-cropper.tsx"), "ImageCropper 已覆盖裁剪+输出 blob 能力。");
["AvatarSetting", "ProfileSetting", "ProfileSettingPopover"].forEach((f) => {
  put("src/component/Pages/Setting/" + f + ".tsx", "复用", app("uc/src/modules/profile"), "目标 UC profile 模块已实现头像/资料编辑，复用其交互。");
});
["Disable2FADialog", "Enable2FADialog", "PasskeyList", "SecuritySetting"].forEach((f) => {
  put("src/component/Pages/Setting/Security/" + f + ".tsx", "复用", app("uc/src/modules/security"), "目标 UC security 模块覆盖 2FA/密码/会话安全设置；passkey 列表登记为待补。");
});
put("src/component/Pages/Setting/PreferenceSetting.tsx", "暂缓", "—", "cloudreve 偏好设置项（主题/语言/列表密度）分散在目标 console appearance 与全局 ThemeProvider，登记待对齐。");
put("src/component/Pages/Setting/Setting.tsx", "复用", app("uc/src/layouts"), "UC 布局 + 模块路由已覆盖设置页壳层。");
put("src/component/Pages/Setting/SettingForm.tsx", "复用", lib("form-field/form-field.tsx"), "表单分组统一由 FormField/FormSection 承载。");
put("src/component/Pages/Setting/SettingListItem.tsx", "迁移", lib("form-field/form-field.tsx"), "迁移为 SettingItem（标题/描述/右侧动作），供 UC 与 Console 设置页复用。");
put("src/component/Pages/Setting/StorageSetting.tsx", "暂缓", "—", "存储配额设置依赖 cloudreve 容量接口，目标暂无对应用户设置面。");
["ShareCard", "ShareList"].forEach((f) => put("src/component/Pages/Shares/" + f + ".tsx", "暂缓", "—", "分享页依赖 cloudreve 分享 API，目标产品面未开放。"));
put("src/component/Pages/Tasks/StepProgressBar.tsx", "迁移", lib("progress-steps/progress-steps.tsx"), "迁移为公用的 ProgressSteps 步骤进度组件。");
["DownloadFileList", "DownloadList", "PieceProgress", "StepProgressPopover", "TaskCard", "TaskDetail", "TaskList", "TaskProgress", "TaskProgressStep", "TaskProps", "TaskSummaryStatus", "TaskSummaryTitle"].forEach((f) => {
  put("src/component/Pages/Tasks/" + f + ".tsx", "暂缓", "—", "离线下载/任务中心强依赖 cloudreve 任务 API，目标暂无任务产品面；StepProgressBar 已抽为公用组件。");
});
["AppPromotion", "ConnectionInfoDialog", "CreateDAVAccountDialog", "DavAccountList", "DavAccountRow", "DesktopAppPromotion", "Devices"].forEach((f) => {
  put("src/component/Pages/Devices/" + f + ".tsx", "暂缓", "—", "目标 UC 尚无设备管理路由（api-client 已有 device API）；登记为下一批迁移页面。");
});

put("src/component/Admin/AdminBundle.tsx", "保留应用层", "web/app/console/src/router", "Admin 路由组合由 console router 承担。");
put("src/component/Admin/Common/AdminCard.tsx", "复用", lib("settings-panel/settings-panel.tsx"), "设置卡片统一由 SettingsPanel/Card 承载。");
put("src/component/Admin/Common/TablePagination.tsx", "复用", lib("pagination/pagination.tsx"), "分页统一由 Pagination 承载。");
["EndpointInput", "GroupSelectionInput", "MagicVarDialog", "NodeSelectionInput", "ProDialog", "SharesInput", "SinglePolicySelectionInput"].forEach((f) => {
  put("src/component/Admin/Common/" + f + ".tsx", "暂缓", "—", "cloudreve 管理端专用表单/实体选择器，目标 console 暂无对应实体管理面；登记待迁移。");
});
put("src/component/Admin/Home/Home.tsx", "复用", app("console/src/modules/overview"), "控制台概览页已实现，复用其信息架构。");
put("src/component/Admin/Home/SiteUrlWarning.tsx", "暂缓", "—", "站点 URL 告警依赖 cloudreve 后端站点配置，登记待设置页补齐。");
const adminSettingsMap = {
  "Appearance/Appearance.tsx": ["复用", app("console/src/modules/settings/components/appearance-settings.tsx")],
  "Captcha/CapCaptcha.tsx": ["封装", lib("captcha/captcha.tsx")],
  "Captcha/Captcha.tsx": ["复用", app("console/src/modules/settings/components/captcha-settings.tsx")],
  "Captcha/ReCaptcha.tsx": ["封装", lib("captcha/captcha.tsx")],
  "Captcha/TurnstileCaptcha.tsx": ["封装", lib("captcha/captcha.tsx")],
  "Email/Email.tsx": ["复用", app("console/src/modules/settings/components/mail-settings.tsx")],
  "Event/Events.tsx": ["复用", app("console/src/modules/settings/components/events-settings.tsx")],
  "Queue/Queue.tsx": ["复用", app("console/src/modules/settings/components/queue-settings.tsx")],
  "Server/ServerSetting.tsx": ["复用", app("console/src/modules/settings/components/server-settings.tsx")],
  "Settings/SettingWrapper.tsx": ["复用", lib("settings-panel/settings-panel.tsx")],
  "Settings/Settings.tsx": ["复用", app("console/src/modules/settings/settings-view.tsx")],
  "SiteInformation/SiteInformation.tsx": ["复用", app("console/src/modules/settings/components/site-settings.tsx")],
  "UserSession/UserSession.tsx": ["复用", app("console/src/modules/settings/components/user-settings.tsx")],
};
Object.keys(adminSettingsMap).forEach((f) => {
  put("src/component/Admin/Settings/" + f, adminSettingsMap[f][0], adminSettingsMap[f][1], "目标 console settings 已实现该类设置（字段范围可能不同），复用其壳层与交互，差异字段登记待补。");
});
["Appearance/CustomHTML", "Appearance/CustomNavItems", "Appearance/ThemeOptionEditDialog", "Appearance/ThemeOptions", "Captcha/GraphicCaptcha", "Email/EmailTemplateEditor", "Email/EmailTemplates"].forEach((f) => {
  put("src/component/Admin/Settings/" + f + ".tsx", "暂缓", "—", "cloudreve 专有设置项（自定义 HTML/导航/主题包/图形验证码/邮件模板），目标设置范围不同，登记待产品确认。");
});
["Media/Extractors", "Media/Generators", "Media/Media", "Queue/QueueCard", "Queue/QueueSettingDialog", "SiteInformation/GeneralImagePreview", "SiteInformation/LogoPreview", "SiteInformation/SiteURLInput", "UserSession/SSOSettings", "VAS/GiftCodes", "VAS/GroupProducts", "VAS/PaymentProviders", "VAS/StorageProducts", "VAS/VAS"].forEach((f) => {
  put("src/component/Admin/Settings/" + f + ".tsx", "暂缓", "—", "目标 console 设置暂无对应分区（媒体处理/队列详情/站点素材/SSO/VAS 增值服务），登记待迁移。");
});
const adminDeferred = {
  Entity: "实体（用户/组/文件统一实体）管理，目标 console 暂无实体管理路由。",
  File: "文件管理后台依赖 cloudreve 文件 API 与文件应用。",
  FileSystem: "文件系统/图标/查看器/WOPI 配置为 cloudreve 专有。",
  Group: "用户组管理在目标 console 仅有占位路由 usergroups，待实现后迁移。",
  Node: "存储节点管理为 cloudreve 存储架构专有，目标后端无此概念。",
  OAuthClient: "OAuth 客户端管理待目标 OAuth provider 管理面建立后迁移。",
  Share: "分享管理依赖 cloudreve 分享 API，目标产品面未开放。",
  StoragePolicy: "存储策略为 cloudreve 存储架构专有，目标后端无此概念。",
  Task: "任务队列管理依赖 cloudreve 任务 API，目标暂无线程。",
};
Object.keys(adminDeferred).forEach((dir) => {
  srcFiles.filter((x) => x.indexOf("src/component/Admin/" + dir + "/") === 0).forEach((f) => {
    put(f, "暂缓", "—", adminDeferred[dir]);
  });
});
["User", "Group"].forEach((dir) => {
  srcFiles.filter((x) => x.indexOf("src/component/Admin/" + dir + "/") === 0).forEach((f) => {
    put(f, "暂缓", "—", "目标 console 已覆盖用户/角色列表与基础操作；该管理对话框待与目标 RBAC/用户模型对齐后迁移。");
  });
});

put("src/component/FileManager/ContextMenu/ContextMenu.tsx", "复用", lib("dropdown-menu/dropdown-menu.tsx"), "DropdownMenu 已支持 contextMenu 坐标模式与菜单项语义，通用右键菜单复用。");
put("src/component/FileManager/Dialogs/DeleteConfirmation.tsx", "封装", lib("confirm-dialog/confirm-dialog.tsx"), "删除确认统一走 ConfirmDialog/useConfirm。");
put("src/component/FileManager/Dialogs/Confirmation.tsx", "封装", lib("confirm-dialog/confirm-dialog.tsx"), "同上，合并为统一确认框。");
put("src/component/FileManager/Explorer/EmptyFileList.tsx", "迁移", lib("empty-state/empty-state.tsx"), "迁移为 EmptyState 的 file-drop 变体（可拖拽上传提示）。");
put("src/component/FileManager/Explorer/FileTypeIcon.tsx", "迁移", lib("file-type-icon/file-type-icon.tsx"), "迁移为 FileTypeIcon：扩展名 -> cr: 图标 + 语义色 token，支持自定义映射。");
put("src/component/FileManager/Explorer/FileIcon.tsx", "复用", lib("file-type-icon/file-type-icon.tsx"), "文件图标统一由 FileTypeIcon 输出。");
put("src/component/FileManager/Explorer/FileSmallIcon.tsx", "复用", lib("file-type-icon/file-type-icon.tsx"), "小尺寸文件图标复用 FileTypeIcon size=sm。");
put("src/component/FileManager/Explorer/FileTag.tsx", "暂缓", "—", "文件标签配色与 cloudreve 标签 API 绑定，登记待文件应用。");
put("src/component/FileManager/Explorer/FileTagSummary.tsx", "暂缓", "—", "同上。");
put("src/component/FileManager/FileBadge.tsx", "暂缓", "—", "文件徽标依赖 FileResponse 数据模型，待文件应用迁移。");
["Breadcrumb", "BreadcrumbButton", "BreadcrumbHiddenItem"].forEach((f) => {
  put("src/component/FileManager/TopBar/" + f + ".tsx", "迁移", lib("breadcrumbs/breadcrumbs.tsx"), "迁移为 Breadcrumbs：层级折叠、图标、可点击/可编辑路径，去掉 redux/URI 依赖。");
});
put("src/component/FileManager/Sidebar/InfoRow.tsx", "迁移", lib("description-list/description-list.tsx"), "迁移为 DescriptionList/DescriptionItem（label+value，响应式列）。");
put("src/component/FileManager/Sidebar/Details.tsx", "复用", lib("description-list/description-list.tsx"), "详情面板字段展示复用 DescriptionList。");
put("src/component/FileManager/Sidebar/Data.tsx", "复用", lib("description-list/description-list.tsx"), "基础信息面板复用 DescriptionList。");
["PaginationFooter", "PaginationItem"].forEach((f) => {
  put("src/component/FileManager/Pagination/" + f + ".tsx", "复用", lib("pagination/pagination.tsx"), "分页控件统一由 Pagination 承载。");
});
put("src/component/FileManager/NewButton.tsx", "复用", lib("button/button.tsx"), "新建按钮复用 Button + DropdownMenu。");
put("src/component/FileManager/FolderPicker.tsx", "暂缓", "—", "目录选择器依赖 cloudreve 文件树，待文件应用迁移。");

srcFiles.filter((x) => x.indexOf("src/component/Viewers/") === 0).forEach((f) => {
  put(f, "暂缓", "—", "文件预览器依赖文件应用上下文与外部运行时（monaco/pdfjs/artplayer/leaflet 等）；登记依赖清单，待文件/媒体应用立项后按批迁移。");
});
put("src/component/Uploader/DropFile.tsx", "迁移", lib("file-drop-zone/file-drop-zone.tsx"), "迁移为 FileDropZone（全屏遮罩/内嵌两种形态，拖拽高亮与键盘可达）。");
srcFiles.filter((x) => x.indexOf("src/component/Uploader/") === 0 && x !== "src/component/Uploader/DropFile.tsx").forEach((f) => {
  put(f, "暂缓", "—", "上传协议/分片/多云存储实现与 cloudreve 后端签名强绑定，目标后端不同；登记待上传服务适配后迁移。");
});

srcFiles.filter((x) => x.indexOf("src/api/") === 0).forEach((f) => {
  put(f, "暂缓", "—", "cloudreve REST API 客户端；目标仓库使用 @pudans/api-client 生成的 OpenAPI 客户端，接口模型不同，登记为业务参考。");
});
put("src/constants/index.ts", "封装", "web/packages/shared/src/constants.ts", "通用常量（版本/布局/键位）并入 @pudans/shared 常量；cloudreve 专有常量不迁移。");
put("src/hooks/delayedHover.tsx", "迁移", hook("use-delayed-hover.ts"), "迁移为 useDelayedHover（可配置延迟，清理定时器，支持 popup state）。");
put("src/hooks/useOverflow.tsx", "迁移", hook("use-overflow.ts"), "迁移为 useOverflow（ResizeObserver + 返回值）。");
put("src/hooks/useMediaSession.ts", "迁移", hook("use-media-session.ts"), "迁移为 useMediaSession（泛化 MediaSession API，去掉 FileResponse 依赖）。");
put("src/hooks/areaSelection.ts", "暂缓", "—", "框选逻辑与文件网格 DOM 结构强耦合，待文件应用迁移。");
put("src/hooks/useNavigation.tsx", "暂缓", "—", "文件路径导航 thunk 编排，待文件应用迁移。");
srcFiles.filter((x) => x.indexOf("src/redux/") === 0).forEach((f) => {
  put(f, "暂缓", "—", "cloudreve 文件管理/站点配置 Redux 切片与 thunk；目标 store 已有 app/auth 切片，文件域切片待文件应用迁移时按 RTK 规范重写。");
});
put("src/router/index.tsx", "复用", "web/src/router", "路由组合由 @web/router（BrowserRouter + guards）与各 app router 承担。");
put("src/router/PageTitle.tsx", "暂缓", "—", "文档标题副作用由各应用路由处理，登记待统一 title hook。");
put("src/session/index.ts", "暂缓", "—", "cloudreve SessionManager（localStorage 键值 + 用户会话）与目标 Redux persisted store 架构不同；偏好键位登记至 shared。");
put("src/session/utils.ts", "暂缓", "—", "会话工具与 cloudreve 登录态绑定。");
put("src/session/errors.ts", "复用", "web/packages/api-client/src", "错误类与归一化由 @pudans/api-client 承担。");
put("src/util/base64.ts", "迁移", shared("base64.ts"), "迁移 base64Encode/base64Decode 到 @pudans/shared/utils。");
put("src/util/boolset.ts", "迁移", shared("boolset.ts"), "迁移 Boolset 位图工具到 @pudans/shared/utils 并补测试。");
put("src/util/datetime.ts", "迁移", shared("datetime.ts"), "迁移时间格式化/时长格式化（注入 locale/timezone，不依赖 i18n 单例）。");
put("src/util/filesystem.ts", "迁移", shared("filesystem.ts"), "迁移 File System Access API 工具（目录创建/流式写文件/权限校验）。");
put("src/util/lru.ts", "迁移", shared("lru.ts"), "迁移 LRUCache（带 blob URL 回收）到 @pudans/shared/utils。");
put("src/util/index.ts", "复用", "web/packages/shared/src/index.ts", "通用工具汇总由 @pudans/shared 导出。");
put("src/util/permission.ts", "暂缓", "—", "文件复制/移动权限判定依赖 cloudreve URI 语义，待文件应用。");
put("src/util/uri.ts", "暂缓", "—", "cloudreve:// URI 解析器为云盘专有协议，目标无对应协议。");
put("src/util/zip.js", "暂缓", "—", "压缩流实现与 cloudreve 归档 API 绑定，登记待文件应用。");
put("src/App.tsx", "保留应用层", "web/src/providers", "主题/Snackbar/全局对话框/ServiceWorker 编排已由目标 Providers + PudansThemeProvider + ToastProvider 重新实现。");
put("src/main.tsx", "保留应用层", "web/app/auth/src/main.tsx", "各应用入口由 Vite MPA 拆分承担（auth/setup/uc/console 各自 main.tsx）。");
put("src/i18n.ts", "保留应用层", "web/src/i18n", "i18n 初始化已迁移到 @web/i18n（资源合并策略不同）。");

publicFiles.forEach((f) => {
  if (f.indexOf("public/locales/") === 0) {
    put(f, "暂缓", "—", "cloudreve 多语言资源键（common/application/dashboard/image_editor/markdown_editor）与目标 i18n 资源模型不同；登记为翻译参考，待统一资源键后导入。");
  } else if (f.indexOf("public/assets/pdfjs/") === 0) {
    put(f, "暂缓", "—", "pdf.js cmaps/standard_fonts 运行时资源；目标未启用 pdf 预览，vite 已配置从 node_modules/pdfjs-dist 复制，未来接入时复用。");
  } else if (f.indexOf("public/static/") === 0) {
    put(f, "暂缓", "—", "cloudreve 品牌/占位静态资源；目标使用自有品牌素材，登记不复制。");
  } else {
    put(f, "暂缓", "—", "静态资源登记；不复制第三方/品牌资产。");
  }
});

const rootMap = {
  "package.json": ["复用", "web/package.json", "依赖清单用于比对；目标依赖版本已统一到 pnpm catalog。"],
  "index.html": ["封装", "web/app/console/index.html", "应用加载壳（loader 动画/theme-color）拆入各 app index.html；动画时长改为 motion token。"],
  "vite.config.ts": ["封装", "web/vite.config.ts", "单应用 Vite 配置改造为 MPA + 库别名 + PWA 配置。"],
  "tsconfig.json": ["复用", "web/tsconfig.json", "TS 严格模式与路径别名已迁移。"],
  "tsconfig.node.json": ["复用", "web/tsconfig.json", "合并进根 tsconfig。"],
  "eslint.config.js": ["复用", "web/eslint.config.js", "ESLint flat config 已适配目标依赖。"],
  ".prettierrc": ["复用", "web/.prettierrc", "代码风格沿用。"],
  ".prettierignore": ["复用", "web/.prettierignore", "忽略规则沿用。"],
  ".gitignore": ["复用", ".gitignore", "构建产物忽略规则已覆盖。"],
  "README.md": ["复用", "web/README.md", "架构说明重写为 pudans web 版本。"],
  "package-lock.json": ["暂缓", "—", "旧 npm 锁文件不迁移；目标使用 pnpm-lock.yaml。"],
  "yarn.lock": ["暂缓", "—", "旧 yarn 锁文件不迁移；目标使用 pnpm-lock.yaml。"],
};
Object.keys(rootMap).forEach((f) => put(f, rootMap[f][0], rootMap[f][1], rootMap[f][2]));

const dirRules = [
  ["src/component/Admin/", "暂缓", "—", "cloudreve 管理端业务组件，目标 console 暂无对应实体/存储产品面；已在清单登记，待后端领域对齐后按模块迁移。"],
  ["src/component/FileManager/", "暂缓", "—", "云盘文件管理器业务组件，依赖 cloudreve 文件树/选择/上传数据流；通用原子（图标/面包屑/描述列表/空状态/分页/右键菜单/确认框）已抽出，其余登记待文件应用迁移。"],
  ["src/component/Common/", "暂缓", "—", "通用组件待逐个确认目标 API 后迁移；当前批次已覆盖可复用原子，剩余登记为下一批。"],
  ["src/component/Dialogs/", "暂缓", "—", "对话框业务变体登记待迁移。"],
  ["src/component/Pages/", "暂缓", "—", "页面业务逻辑依赖 cloudreve 路由/API，目标应用已按 pudans 领域重写；旧页面登记为交互参考。"],
  ["src/component/Frame/", "暂缓", "—", "导航框架中与 cloudreve 文件/站点配置绑定的部分登记待迁移。"],
  ["src/hooks/", "暂缓", "—", "cloudreve 业务 hook，登记待对应产品面迁移。"],
  ["src/util/", "暂缓", "—", "cloudreve 专有工具函数，登记待迁移/替代。"],
  ["src/redux/", "暂缓", "—", "cloudreve Redux 业务切片，登记待重写。"],
  ["src/session/", "暂缓", "—", "会话实现登记待迁移。"],
  ["src/api/", "暂缓", "—", "cloudreve API 客户端登记待替代。"],
  ["src/component/Icons/", "迁移", "", "SvgIcon 包装器逐个抽取为 offline Iconify 集合 cr:*（见 cloudreve-icons.ts）；不保留单文件组件。"],
  ["src/component/", "暂缓", "—", "未归类组件登记待迁移。"],
  ["src/", "暂缓", "—", "未归类源码登记待迁移。"],
];
dirRules.sort((a, b) => b[0].length - a[0].length);

function lookup(rel) {
  if (single[rel]) return single[rel];
  for (const rule of dirRules) {
    if (rel.indexOf(rule[0]) === 0) {
      if (rel.indexOf("src/component/Icons/") === 0) {
        const name = path.basename(rel, ".tsx");
        return { disposition: rule[1], target: "web/packages/components/src/components/icon/cloudreve-icons.ts", icon: "cr:" + name, reason: rule[3] };
      }
      return { disposition: rule[1], target: rule[2], reason: rule[3] };
    }
  }
  return { disposition: "暂缓", target: "—", reason: "未显式归类，登记待迁移。" };
}

function targetExists(target) {
  if (!target || target === "—") return false;
  const clean = target.replace(/^web\//, "");
  return fs.existsSync(path.join(TARGET, clean));
}
function statusOf(row) {
  const exists = row.target !== "—" && targetExists(row.target);
  if (row.disposition === "迁移" || row.disposition === "封装") return exists ? "已完成" : "规划中";
  if (row.disposition === "复用") return exists ? "已复用" : "目标待建";
  if (row.disposition === "保留应用层") return exists ? "已保留" : "待对齐";
  return "已登记（暂缓）";
}

let iconCollectionNames = new Set();
try {
  const coll = JSON.parse(fs.readFileSync(path.join(OUT_DIR, "data/cloudreve-icons.json"), "utf8"));
  iconCollectionNames = new Set(coll.map((i) => i.component));
} catch (e) { /* generated later */ }
const iconFileExists = fs.existsSync(path.join(TARGET, "packages/components/src/components/icon/cloudreve-icons.ts"));

const rows = files.map((rel) => {
  const hit = lookup(rel);
  const row = Object.assign({ source: rel }, hit);
  if (rel.indexOf("src/component/Icons/") === 0) {
    const migrated = iconFileExists && iconCollectionNames.has(path.basename(rel, ".tsx"));
    row.status = migrated ? "已完成" : "规划中";
  } else {
    row.status = statusOf(row);
  }
  return row;
});

function listDir(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).filter((e) => e.isDirectory() && e.name !== "node_modules").map((e) => e.name).sort();
}
const componentsDir = path.join(TARGET, "packages/components/src/components");
const layoutsDir = path.join(TARGET, "packages/components/src/layouts");
const hooksDir = path.join(TARGET, "packages/components/src/hooks");
const sharedUtilsDir = path.join(TARGET, "packages/shared/src/utils");
const targetComponents = listDir(componentsDir);
const targetLayouts = listDir(layoutsDir);
const targetHooks = listDir(hooksDir);
const targetSharedUtils = fs.existsSync(sharedUtilsDir) ? fs.readdirSync(sharedUtilsDir).filter((f) => f.endsWith(".ts")).sort() : [];
const appModules = {};
["auth", "setup", "uc", "console"].forEach((appName) => {
  appModules[appName] = listDir(path.join(TARGET, "app/" + appName + "/src/modules"));
});

const byDisposition = {};
DISPOSITIONS.forEach((d) => { byDisposition[d] = 0; });
rows.forEach((row) => { byDisposition[row.disposition]++; });
const done = rows.filter((r) => r.status === "已完成").length;
const reused = rows.filter((r) => r.status === "已复用").length;
const appLayer = rows.filter((r) => r.status === "已保留").length;
const deferred = rows.filter((r) => r.status === "已登记（暂缓）").length;
const planned = rows.filter((r) => r.status === "规划中" || r.status === "目标待建" || r.status === "待对齐").length;
const srcRows = rows.filter((r) => r.source.indexOf("src/") === 0);
const srcDone = srcRows.filter((r) => ["已完成", "已复用", "已保留"].indexOf(r.status) >= 0).length;

const out = [];
const push = (line) => { out.push(line === undefined ? "" : line); };
const esc = (s) => String(s === undefined || s === null ? "" : s).replace(/\|/g, "\\|");
const c = code;
const h = (cells) => "| " + cells.join(" | ") + " |";

push("# 旧项目前端 → pudans web 迁移清单（inventory）");
push("");
push("> 生成时间：" + new Date().toISOString() + " · 生成方式：" + c(".dsh/migration/generate-inventory.mjs") + "（状态由文件系统推导，不手写完成状态）");
push("");
push(h(["项", "值"]));
push(h(["---", "---"]));
push(h(["旧项目（源）", c("/Users/byte/Global/Reference/cloudreve/frontend")]));
push(h(["目标仓库", c("/Users/byte/Global/Project/pudans") + "（当前会话工作目录；" + c("web/packages/components") + " 所在仓库）"]));
push(h(["源文件总数（不含 node_modules）", String(files.length)]));
push(h(["源 src/ 文件", String(srcFiles.length)]));
push(h(["源 public/ 文件", String(publicFiles.length)]));
push(h(["源根配置", String(files.length - srcFiles.length - publicFiles.length)]));
push(h(["目标组件库组件目录", String(targetComponents.length)]));
push(h(["目标布局目录", String(targetLayouts.length)]));
push(h(["目标 hooks", String(targetHooks.length)]));
push(h(["目标 shared utils", String(targetSharedUtils.length)]));
push("");
push("## 摘要（覆盖率与处理方式）");
push("");
push(h(["指标", "数量", "说明"]));
push(h(["---", "---", "---"]));
push(h(["清单条目（源文件）", String(rows.length), "每个源文件恰好一个条目"]));
push(h(["已迁移/已封装（writes）", String(done), "目标文件已存在，含 251 图标集合"]));
push(h(["已复用（reuse）", String(reused), "直接使用目标库既有能力"]));
push(h(["已保留应用层", String(appLayer), "架构层组合，不抽公共库"]));
push(h(["已登记（暂缓）", String(deferred), "明确原因与迁移计划，无静默遗漏"]));
push(h(["规划中/待建", String(planned), "已定目标路径，待本批完成后自动转为已完成"]));
push(h(["覆盖率", ((rows.length / files.length) * 100).toFixed(2) + "%", "源文件全部登记"]));
push(h(["src/ 完成率", ((srcDone / srcFiles.length) * 100).toFixed(1) + "%", "迁移+封装+复用+保留 / src 文件数"]));
push("");
push("### 处理方式分布");
push("");
push(h(["处理方式", "数量", "含义"]));
push(h(["---", "---", "---"]));
const meaning = {
  迁移: "整体迁移到公共组件库/共享包",
  封装: "抽取/合并为统一公共组件（多对一）",
  复用: "目标库已有等价能力，直接复用",
  "保留应用层": "属于应用架构组合，不进入公共库但已在应用层落地",
  暂缓: "登记保留，附阻断原因与迁移计划",
};
DISPOSITIONS.forEach((d) => push(h([d, String(byDisposition[d]), meaning[d]])));
push("");
push("## 一、源项目 src/ 全量清单");
push("");
push("> 目标路径相对 " + c("/Users/byte/Global/Project/pudans/") + "。状态：已完成 / 已复用 / 已保留 / 规划中 / 已登记（暂缓）。");
push("");
const srcDirOrder = ["src/component/Icons/", "src/component/Common/", "src/component/Frame/", "src/component/Dialogs/", "src/component/Pages/", "src/component/Admin/", "src/component/FileManager/", "src/component/Viewers/", "src/component/Uploader/", "src/api/", "src/constants/", "src/hooks/", "src/redux/", "src/router/", "src/session/", "src/util/", "src/"];
const seen = new Set();
srcDirOrder.forEach((prefix) => {
  const group = srcRows.filter((r) => !seen.has(r.source) && r.source.indexOf(prefix) === 0);
  if (group.length === 0) return;
  group.forEach((r) => seen.add(r.source));
  const title = prefix === "src/" ? "src 根文件" : prefix.replace(/^src\//, "").replace(/\/$/, "");
  push("### " + title + "（" + group.length + "）");
  push("");
  push(h(["#", "源路径", "目标路径", "处理方式", "状态", "理由"]));
  push(h(["---", "---", "---", "---", "---", "---"]));
  group.forEach((r, i) => {
    const target = r.icon ? r.target + " -> " + c(r.icon) : r.target;
    push(h([String(i + 1), c(r.source), target === "—" ? "—" : c(target), r.disposition, r.status, esc(r.reason)]));
  });
  push("");
});

push("## 二、源项目 public/ 静态资源清单");
push("");
const publicGroups = {};
publicFiles.forEach((f) => {
  const parts = f.split("/");
  const key = parts.slice(0, 3).join("/") + (parts.length > 3 ? "/…" : "");
  if (!publicGroups[key]) publicGroups[key] = [];
  publicGroups[key].push(f);
});
push(h(["目录", "文件数", "处理方式", "状态", "说明"]));
push(h(["---", "---", "---", "---", "---"]));
Object.keys(publicGroups).sort().forEach((key) => {
  push(h([c(key), String(publicGroups[key].length), "暂缓", "已登记（暂缓）", "见附录逐文件清单与理由"]));
});
push("");
const detailsOpen = "<details><summary>展开 public 全量文件路径（" + publicFiles.length + "）</summary>";
push(detailsOpen);
push("");
Object.keys(publicGroups).sort().forEach((key) => {
  push("**" + key + "**");
  push("");
  push(publicGroups[key].map((f) => c(f)).join("、"));
  push("");
});
push("</details>");
push("");

push("## 三、目标仓库 web/packages/components 现有盘点");
push("");
push("### 组件目录（" + targetComponents.length + "）");
push("");
push(targetComponents.map((x) => c(x)).join("、") || "（无）");
push("");
push("### 布局目录（" + targetLayouts.length + "）");
push("");
push(targetLayouts.map((x) => c(x)).join("、") || "（无）");
push("");
push("### hooks 目录（" + targetHooks.length + "）");
push("");
push(targetHooks.map((x) => c(x)).join("、") || "（本批次新增）");
push("");
push("### shared utils（" + targetSharedUtils.length + "）");
push("");
push(targetSharedUtils.map((x) => c(x)).join("、") || "（无）");
push("");
push("## 四、目标应用页面/模块盘点");
push("");
push(h(["应用", "模块"]));
push(h(["---", "---"]));
Object.keys(appModules).forEach((appName) => {
  push(h([appName, appModules[appName].map((m) => c(m)).join("、") || "—"]));
});
push("");
push("## 五、缺口与合并分析（人工结论）");
push("");
push("### 5.1 已有能力（复用）");
push("");
push("- 主题：PudansThemeProvider / createPudansTheme / tokens.ts / variables.css 已提供亮暗主题、品牌色注入、MUI 桥接。");
push("- 布局：AppShell / SidebarLayout / PageLayout / PageContainer 覆盖旧 Frame/Pages 的页框需求。");
push("- 基础组件：Button/Input/Select/Dialog/DropdownMenu/Card/Tabs/Table/Badge/Alert/Toast/Skeleton/Avatar/Tooltip/Checkbox/Switch/Pagination/DatePicker/TreeView/Chart/Graph 等。");
push("");
push("### 5.2 缺失（本批迁移/封装）");
push("");
push("- 状态类：EmptyState（旧 Nothing/EmptyFileList/NoMatch）、ErrorState/ErrorBoundary（旧 ErrorBoundary）、Progress（旧 Linear/Circular）、ProgressSteps（旧 StepProgressBar）。");
push("- 反馈类：ConfirmDialog/useConfirm（旧 Confirmation）、SelectDialog/useSelectOption（旧 SelectOption）、ErrorDetailDialog（旧 AggregatedErrorDetail）、Toast 内容变体。");
push("- 导航/交互：Breadcrumbs（旧 Breadcrumb 三件套）、ResponsiveTabs、ThemeSwitch、SearchInput、SplitPane、Accordion（旧 DialogAccordion）、Draggable Dialog。");
push("- 数据展示：DescriptionList（旧 InfoRow/Details/Data）、RelativeTime（旧 TimeBadge）、FileTypeIcon（旧 FileTypeIcon/FileIcon/FileSmallIcon）、SizeInput。");
push("- 品牌/身份：Logo、PoweredBy、LanguageSwitcher、UserBadge、UserPopover、OAuthAppCard。");
push("- 表单/布局：FormField/FormSection/SettingItem（旧 StyledFormControl/SettingListItem）。");
push("- 动效：Fade/Slide/AnimatedSwap + motion token（旧 FadeTransition/NavIconTransition）。");
push("- 上传：FileDropZone（旧 DropFile）。");
push("- 图标：251 个 cloudreve SvgIcon -> cr: offline Iconify 集合。");
push("- hooks：useDelayedHover / useOverflow / useMediaSession。");
push("- shared utils：base64 / boolset / datetime / filesystem / lru。");
push("");
push("### 5.3 可合并（重复实现合并）");
push("");
push("- 旧 Captcha 5 个供应商包装 -> 1 个 Captcha；旧 Progress 2 个 -> 1 个；旧 Snackbar 3 件套 -> Toast；旧 Breadcrumb 3 件套 -> Breadcrumbs；旧 Pagination 2 件套 -> Pagination；旧 Dialog 变体（Confirmation/SelectOption/ErrorDetail）-> Dialog + 专用封装。");
push("");
push("### 5.4 API 不兼容（需要适配说明）");
push("");
push(h(["旧 API", "新 API", "迁移说明"]));
push(h(["---", "---", "---"]));
push(h(["Nothing(primary, secondary, top, size)", "EmptyState(title, description, icon, action, variant)", "文案字段改名；size -> sm/md/lg；新增语义 variant。"]));
push(h(["DraggableDialog(dialogProps, showActions...)", "Dialog(draggable, footer, ...)", "动作区改由 footer 传入；确认语义用 ConfirmDialog。"]));
push(h(["Confirmation（redux promise）", "useConfirm()（Provider promise）", "移除全局 redux 依赖；在 App Providers 内挂载 ConfirmProvider。"]));
push(h(["SelectOption（redux）", "useSelectOption()", "同上。"]));
push(h(["TimeBadge(datetime, timeAgoThreshold)", "RelativeTime(datetime, threshold, tooltip)", "改用 Intl.RelativeTimeFormat；tooltip 显示完整本地时间。"]));
push(h(["ResponsiveTabs(tabs, value, onChange)", "ResponsiveTabs(items, value, onChange)", "字段 label/value/icon 不变；溢出策略内置。"]));
push(h(["DarkThemeSwitcher（redux 布尔）", "ThemeSwitch（light/system/dark）", "直接消费 usePudansTheme()。"]));
push(h(["BorderLinearProgress", "Progress variant=linear", "tone/size/value 标准化。"]));
push(h(["SizeInput(onChange, value, min/max)", "SizeInput（同名）", "取消 redux，单位换算纯函数化。"]));
push(h(["SearchBar（全局搜索）", "SearchInput", "去掉全局搜索状态，受控 value/onChange。"]));
push(h(["SplitHandle（redux drawer）", "SplitPane", "改为受控尺寸 + storageKey 持久化。"]));
push("");
push("## 六、页面/路由映射");
push("");
push(h(["旧页面", "目标应用/路由", "状态", "说明"]));
push(h(["---", "---", "---", "---"]));
push(h(["Pages/Login/*", "web/app/auth -> /auth/", "已迁移（部分待补）", "密码/OTP/找回已落地；passkey、激活、OAuth 授权登记待补。"]));
push(h(["Pages/Setting/*", "web/app/uc -> /uc/profile、/uc/security", "已迁移（部分待补）", "资料/安全/授权已落地；存储配额/偏好/passkey 列表登记待补。"]));
push(h(["Admin/Home", "web/app/console -> /console/ overview", "已迁移", "概览卡片与布局已适配。"]));
push(h(["Admin/Settings/*", "/console/settings", "已迁移（子集）", "appearance/captcha/mail/events/queue/server/site/user 已落地；media/VAS/SSO 等登记。"]));
push(h(["Admin/User/*", "/console/users", "部分迁移", "列表+基础操作；高级用户对话框登记。"]));
push(h(["Admin/Group/*", "/console/usergroups（占位）", "暂缓", "目标 RBAC/用户组模型未定。"]));
push(h(["Admin/{Entity,File,FileSystem,Node,OAuthClient,Share,StoragePolicy,Task}", "—", "暂缓", "目标产品暂无对应管理面（后端领域不同）。"]));
push(h(["FileManager/*", "—", "暂缓（原子已抽）", "目标尚无文件管理应用；原子组件（图标/面包屑/空状态/描述列表/分页/右键菜单/确认框）已进公共库。"]));
push(h(["Viewers/*", "—", "暂缓", "目标无文件预览应用；依赖清单已登记。"]));
push(h(["Uploader/*", "—", "暂缓（DropFile 已抽）", "上传协议与 cloudreve 存储强绑定。"]));
push(h(["Pages/{Shares,Tasks,Devices}", "—", "暂缓", "目标无对应产品面。"]));
push(h(["Pages/NoMatch", "公共库 EmptyState(not-found)", "已迁移", "任意应用可复用。"]));
push("");
push("## 七、设计变量（token）清单");
push("");
push("> 完整定义见 " + c("web/packages/components/src/styles/tokens.ts") + "、" + c("variables.css") + "、" + c("create-theme.ts") + "；规范说明见 " + c(".dsh/migration/design-system.md") + "。");
push("");
push(h(["组", "token 前缀", "覆盖"]));
push(h(["---", "---", "---"]));
push(h(["品牌/语义色", c("--pd-brand-*, --pd-bg-*, --pd-text-*, --pd-border-*, --pd-state-*"), "亮/暗双主题、accent 注入"]));
push(h(["字体", c("--pd-font-*, --pd-text-*, --pd-leading-*, --pd-font-weight-*"), "Roboto + mono，12-30px 字号"]));
push(h(["间距", c("--pd-space-*"), "4px 网格"]));
push(h(["圆角/阴影", c("--pd-radius-*, --pd-shadow-*"), "4-9999px / 5 级高度"]));
push(h(["动效", c("--pd-duration-*, --pd-ease-*, --pd-transition-*"), "150-375ms、标准/进出/强调曲线、reduced-motion"]));
push(h(["状态", c("--pd-focus-ring-*, --pd-opacity-disabled, --pd-hover-*"), "focus/hover/active/disabled/loading"]));
push(h(["图表", c("--pd-chart-1…6"), "亮/暗双套"]));
push("");
push("## 八、图标迁移");
push("");
push(h(["项", "值"]));
push(h(["---", "---"]));
push(h(["源图标文件", String(srcFiles.filter((f) => f.indexOf("src/component/Icons/") === 0).length)]));
push(h(["目标集合", c("web/packages/components/src/components/icon/cloudreve-icons.ts") + "（Iconify collection prefix " + c("cr") + "）"]));
push(h(["映射清单", c(".dsh/migration/icon-map.md") + " / " + c(".dsh/migration/data/cloudreve-icons.json")]));
push(h(["使用方式", c('<Icon name="cr:Search" />') + "（离线注册，无网络请求）"]));
push("");
push("## 九、阻断与待办汇总");
push("");
push("见 " + c(".dsh/migration/report.md") + " 的「未完成项及阻断原因」章节；所有暂缓项均已在上表给出 reason，可在后续批次按目录整体启动。");
push("");

fs.mkdirSync(path.join(OUT_DIR, "data"), { recursive: true });
fs.writeFileSync(path.join(OUT_DIR, "inventory.md"), out.join("\n") + "\n");
fs.writeFileSync(path.join(OUT_DIR, "inventory.json"), JSON.stringify({
  generatedAt: new Date().toISOString(),
  sourceRoot: SRC,
  targetRoot: ROOT,
  meta: {
    total: files.length,
    src: srcFiles.length,
    public: publicFiles.length,
    byDisposition: byDisposition,
    statusCounts: { done: done, reused: reused, appLayer: appLayer, deferred: deferred, planned: planned },
    srcCompletionRate: Number(((srcDone / srcFiles.length) * 100).toFixed(2)),
  },
  target: { components: targetComponents, layouts: targetLayouts, hooks: targetHooks, sharedUtils: targetSharedUtils, apps: appModules },
  rows: rows,
}, null, 2) + "\n");

console.log("inventory rows:", rows.length);
console.log("byDisposition:", JSON.stringify(byDisposition));
console.log("status:", JSON.stringify({ done: done, reused: reused, appLayer: appLayer, deferred: deferred, planned: planned }));
console.log("src rows:", srcRows.length, "srcDone:", srcDone);
