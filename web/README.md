# Pudans Web

Next.js 控制台，面向 Pudans 企业身份与权限管理平台。

## 环境

- Node.js 20.9 及以上
- npm

## 命令

```bash
npm install          # 安装依赖
npm run dev          # 本地开发，默认 http://localhost:3000
npm run build        # 生产构建
npm run start        # 启动生产构建
npm run check        # lint、类型检查、knip 与格式检查
npm run build:api-client                      # 编译 API 客户端包
npm run gen --workspace @pudans-dev/api-client # 重新生成 API 客户端
```

## 目录

```text
public/                      静态资源（登录背景视频、品牌图标）
packages/api-client/         OpenAPI 生成的 API 客户端（src 生成、entry 手写）
src/app/                     Next.js 页面入口（(auth)、(setup)、(workspace) 分组）
src/app/(auth)/              认证页（layout + sign-in/page）
src/app/(setup)/             初始化页（layout + page）
src/app/(workspace)/         登录后的工作区路由组
src/app/(workspace)/[workspace]/ 工作区动态入口（对外地址 `/workspace/<workspace>`）
src/components/              通用组件
src/components/layouts/      公共布局（AppShell、AppContainer）
src/components/layouts/app-shell.tsx 公共 TDesign 外壳（单层 HeadMenu + 平铺侧栏）
src/components/layouts/app-container.tsx 公共页面容器
src/components/layouts/index.ts AppShell 出口
src/components/ui/           基础 UI 组件
src/components/ui/index.ts   基础 UI 出口
src/components/ui/echart.tsx 共享 ECharts 容器
src/components/ui/global-dialog.tsx 屏幕居中的全局弹窗
src/components/ui/menu-icon.tsx 动态菜单图标映射
src/components/ui/navigation-progress.tsx 导航进度条
src/components/ui/theme-menu.tsx 主题切换菜单
src/context/                 React Context 与 Provider
src/composables/useDynamicMenu.ts 工作区、分组、菜单与默认选中项解析
src/hooks/                   通用 Hooks
src/hooks/ui/use-message-prompt.ts 全局消息提示入口
src/middleware.ts            页面渲染前的会话认证与初始化检查
src/modules/                 页面模块
src/modules/_auth/           认证模块
src/modules/_auth/components/ 共享认证组件
src/modules/_auth/composables/ 登录、恢复与 WebAuthn 逻辑
src/modules/_auth/modules/   sign-in、two-factor、legal、forgot-password
src/modules/_auth/styles/    认证样式（_auth-layout.css）
src/modules/_auth/index.tsx  认证模块出口
src/modules/_setup/          初始化模块
src/modules/_setup/composables/ 初始化提交与初始化状态守卫
src/modules/_setup/modules/setup/ 初始化视图与表单
src/modules/_workspace/      登录后的工作区模块
src/modules/_workspace/_console/ 管理后台
src/modules/_workspace/layout.tsx 通用工作区布局
src/modules/_workspace/_console/components/ 控制台表格、结果标签与 JSON 查看
src/modules/_workspace/_console/composables/ 表格状态、格式化、错误与存储扩展 API
src/modules/_workspace/_console/modules/overview/ 概览模块（dashboard）
src/modules/_workspace/_console/modules/identity/ 身份与权限模块（users、roles、user-groups、permissions、auth-providers）
src/modules/_workspace/_console/modules/security/ 安全与审计模块（risk、audit、traces、replay、secrets）
src/modules/_workspace/_console/modules/storage/ 存储模块（storage-policies、storage-files）
src/modules/_workspace/_console/modules/system/ 系统设置模块（settings、webhooks、config-maps、annotations）
src/routes/                  路由入口组件（(_auth)、(_setup)、(_workspace)、_authenticated）
src/routes/(_auth)/route.tsx 唯一认证路由入口
src/routes/(_setup)/route.tsx 初始化路由入口
src/routes/(_workspace)/index.ts 工作区视图注册表
src/routes/(_workspace)/(_console)/<group>/<menu>/route.tsx 控制台各菜单路由入口
src/routes/_authenticated/route.tsx 会话认证检查
src/stores/                  客户端状态仓库
src/stores/_auth/            认证会话与两步验证状态
src/stores/_setup/           初始化完成状态
src/stores/_workspace/ 工作区侧栏折叠状态
src/stores/create-store.ts  通用状态仓库实现
src/stores/use-store.ts     订阅仓库状态的 Hook
src/styles/theme.css         全局样式、TDesign 主题与布局变量
```

## 路由

- `/sign-in`：认证页，默认密码登录 / 通行密钥 / 外部登录；开启登录验证码时在提交后弹出图片验证码弹窗，启用两步验证的账号在密码或通行密钥校验后进入动态验证码步骤；`?routes=forgot-password` 切换找回密码，`?routes=legal&tab=terms|privacy|confidentiality` 打开法律协议弹层。
- `/setup`：首次运行初始化页，创建超级管理员并设置站点名称与站点地址；管理员邮箱由飞书同步，初始化表单不填写邮箱。该页面也作为 `/` 在首次运行时展示，`/setup` 通过 rewrite 指向它。
- `/`：首次运行时渲染初始化页；初始化完成后重定向到 `/sign-in`。
- `/workspace/<workspace>?<group>=<menu>`：工作区统一入口，例如 `/workspace/console?overview=dashboard`、`/workspace/index?other=account-profile`。工作区 `slug`、分组 `key`、菜单和顺序都由 `/navigation` 返回，前端不维护工作区或菜单路径；未带有效参数时使用导航首个可访问菜单。

`src/app/(auth)/sign-in/page.tsx` 对应 `/sign-in`，通过 `src/routes/(_auth)/route.tsx` 渲染，页面内容放在 `src/modules/_auth`。`src/app/(setup)/page.tsx` 对应 `/`，`/setup` 通过 Next rewrite 渲染该页面，通过 `src/routes/(_setup)/route.tsx` 进入视图，页面内容放在 `src/modules/_setup`。`src/app/(workspace)/[workspace]/layout.tsx` 使用 `WorkspaceLayout`；布局通过 `useDynamicMenu` 读取 `/navigation`，按 `ui-group` 与 `ui-group-key` 分组，按 `ui-parent` 组装二级菜单，并组合公共 `AppShell`。Next 路由组不产生 URL，`/workspace/:workspace` 由 `next.config.ts` rewrite 到 `/[workspace]`。

工作区页面统一位于 `src/app/(workspace)/[workspace]/page.tsx`，页面从 `useDynamicMenu` 读取当前工作区、分组和菜单，再从 `src/routes/(_workspace)/index.ts` 的视图注册表选择入口。控制台菜单入口位于 `src/routes/(_workspace)/(_console)/<group>/<menu>/route.tsx`，视图放在 `src/modules/_workspace/_console/modules/<group>/<menu>`，每个菜单使用独立的 `index.tsx`。

新增工作区时，在后端 `extensions/workspace/<workspace>/` 声明工作区模板和菜单模板：工作区填写 `ui-slug`，菜单填写 `ui-group-key`；`ui-order` 同时控制分组与菜单顺序。前端无需新增 `app` 目录；实现菜单视图后，在 `WORKSPACE_VIEWS[workspaceSlug][groupKey][menuKey]` 注册对应路由入口即可。

## 控制台页面

| 菜单       | 查询参数（基路径 `/workspace/console`） | 当前实现                                                      |
| ---------- | --------------------------------------- | ------------------------------------------------------------- |
| 面板首页   | `?overview=dashboard`                   | 概览统计、登录趋势、活跃用户、最近操作、角色分布、站点信息    |
| 用户       | `?identity=users`                       | 列表、搜索、状态筛选、新建/编辑、启用/禁用、删除              |
| 角色       | `?identity=roles`                       | 列表、新建/编辑、详情（权限规则表）、删除（内置角色禁编禁删） |
| 用户组     | `?identity=user-groups`                 | 左侧内置/自定义列表，右侧成员与角色绑定管理                   |
| 身份提供商 | `?identity=auth-providers`              | OAuth2、OIDC、飞书卡片，支持添加、编辑与启停                  |
| 参数配置   | `?system=settings`                      | 8 个参数组的结构化表单编辑，敏感字段掩码提示                  |
| 存储       | `?storage=storage-policies`             | 列表、详情、删除                                              |
| 存储文件   | `?storage=storage-files`                | 列表、详情、下载、回收站、恢复、彻底删除                      |
| 风险       | `?security=risk`                        | 实时态势、趋势、热力图、关系图、分布、事件、封禁与规则管理    |
| 审计       | `?security=audit`                       | 实时态势、趋势、热力图、分布、活跃用户与日志详情              |
| 链路       | `?security=traces`                      | 链路态势、趋势、热力图、步骤/耗时分布、链路列表与回放         |
| 回溯       | `?security=replay`                      | 可回溯态势、趋势、热力图、分布、最近链路回放、归档日志与详情  |
| Webhook    | `?system=webhooks`                      | 列表、新建/编辑、删除                                         |
| 配置字典   | `?system=config-maps`                   | 列表、键值新建/编辑、删除                                     |
| 密钥管理   | `?security=secrets`                     | 列表、键值新建、删除（内容不回显）                            |
| 注解设置   | `?system=annotations`                   | 按 `targetRef` 查询、详情                                     |
| 权限目录   | `?identity=permissions`                 | 工作区、菜单与权限资源的只读目录                              |

创建和编辑统一使用 `GlobalDialog`，在屏幕正中弹出；详情使用结构化字段和表格，不直接回显 JSON。操作反馈统一使用 `MessagePlugin`，错误与警告使用右侧 `NotificationPlugin`。

## 认证

[src/modules/_setup/composables/_setup-guard.ts](src/modules/_setup/composables/_setup-guard.ts) 提供初始化状态检查，[src/middleware.ts](src/middleware.ts) 在页面渲染前请求 `/apis/api.pudans.tolim.cn/v1alpha1/login/status`：

- `setupRequired` 为真时，`/sign-in` 重定向到 `/setup`；`/` 与 `/setup` 展示初始化页。
- `setupRequired` 为假时，`/` 与 `/setup` 重定向到 `/sign-in`。
- `/setup`、`/sign-in` 与首次运行时的 `/` 不经过会话认证检查。
- 其余页面请求 `/login/session`：未登录或请求失败时重定向到 `/sign-in?redirect=<原路径>`；后端不可达时按未登录处理。

登录提交时按 `/login/status` 的 `loginCaptchaEnabled` 弹出图片验证码弹窗；开启两步验证的账号在 `/login` 返回 `twoFactorRequired` 后进入 TOTP 验证，验证码提交到 `/challenges/two-factor/totp`。浏览器端接口调用统一通过 `@pudans-dev/api-client`；`/login` 与两步验证挑战由 Spring Security 处理、不在 OpenAPI 文档中，因此使用该包导出的 `axiosInstance`。Edge 中间件在页面渲染前直接使用 `fetch` 请求会话与初始化状态，因为生成的 axios 客户端运行在浏览器或 Node 运行时。

## 布局

- 认证页与初始化页布局使用 `AuthLayout`：固定背景视频、渐变遮罩、品牌栏、主题切换与半透明玻璃卡片；认证页保留视频暂停控制与法律协议入口，初始化页隐藏这些入口并居中展示 720px 卡片。
- 认证页与初始化页固定在视口高度，页面不滚动；内容超出时在卡片内部滚动。
- UI 基座统一使用 TDesign；图表统一由 `EChart` 承载 ECharts 配置，构图色板由 `tvision-color` 生成。
- `components/layouts` 提供公共布局：`AppShell` 是对外出口，内部组合 TDesign `HeadMenu`、平铺侧栏 `Menu` 与 `AppContainer`；`AppShell` 不读取业务导航，只接收分组、菜单和选中项等属性。
- 通用工作区布局在 `src/modules/_workspace/layout.tsx`：`useDynamicMenu` 根据 URL 中的工作区 `slug` 查找 `/navigation` 工作区，按 `ui-group-key` 生成顶部单层导航分组，按 `ui-parent` 组装侧栏菜单，并生成 `/workspace/<workspace>?<group>=<menu>` 链接；`src/app/(workspace)/[workspace]/layout.tsx` 负责接线。
- `NavigationProgress` 只在根布局挂载一次，在页面最顶层显示全局路由切换进度：内部链接点击和浏览器前进/后退触发，路由变化后完成。
- `AppContainer` 在内容区提供 Suspense 边界；工作区侧栏折叠由 `stores/_workspace` 驱动 `AppShell` 的受控 `collapsed`。
- 弹窗不使用抽屉或侧滑面板；统一由 `GlobalDialog` 在屏幕中心呈现。

## 状态管理

- 服务端状态统一使用 TanStack Query，查询键按 `["console", <domain>, ...]` 命名，变更后失效对应前缀。
- 列表分页、排序和筛选通过 `useTableUrlState` 同步到 URL，公共封装在 `modules/_workspace/_console/composables/_table.ts`。
- 工作区侧栏折叠状态放在 `src/stores/_workspace`，通过 `AppShell` 的受控 `collapsed` 属性驱动。
- `useDynamicMenu` 中的 `useSearchParams` 由 `WorkspaceLayout` 内部 Suspense 承接，页面中的查询参数读取由 `AppContainer` 的 Suspense 边界承接。

## API 客户端

`packages/api-client` 是 npm workspace 包 `@pudans-dev/api-client`，从 [api-docs/openapi/v3_0/apis_api_v1alpha1.json](../api-docs/openapi/v3_0/apis_api_v1alpha1.json) 自动生成：

- `npm run gen --workspace @pudans-dev/api-client`：规范化临时 OpenAPI 副本后调用 openapi-generator，重新生成 `src/`。
- `npm run build:api-client`：编译到 `dist/`；`predev`、`prebuild`、`pretypecheck` 会自动执行。
- `entry/` 是手写入口，提供 `apiClient`、`createApiClient` 与共享的 `axiosInstance`；`src/` 为生成产物，不要手改。
- 包的 `types` 指向 `entry/index.ts`，编辑器不需要先构建 `dist` 就能解析类型；运行时仍加载 `dist`。

生成前会修正当前 OpenAPI 文档中的重复参数、缺失路径参数和请求体 `oneOf` 包装，不修改仓库中的原始文档。`/login/status` 的 `loginCaptchaEnabled` 尚未写入当前 OpenAPI 文档，前端通过本地扩展类型读取；后端文档补齐后重新生成即可移除。

存储策略和存储文件由扩展资源路由提供（`/storagepolicies`、`/storagefiles`），当前 OpenAPI 文档尚未生成对应客户端，前端在 `modules/_workspace/_console/composables/_storage.ts` 通过共享 `axiosInstance` 访问；后端文档补齐后可切回生成客户端。

## 后端

后端 `appkernel` 监听 `8090` 端口，接口前缀为 `/apis/api.pudans.tolim.cn/v1alpha1/`。开发时 Next.js 通过 rewrites 将 `/apis/*`、`/login` 与 `/setup` 代理到对应目标：`/apis/*` 和 `/login` 指向 `PUDANS_API_ORIGIN`（默认 `http://localhost:8090`），`/setup` 指向初始化页 `/`。
