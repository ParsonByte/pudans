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
src/app/                     Next.js 页面入口（(auth)、(setup) 分组）
src/app/(auth)/              认证页（layout + page）
src/app/(setup)/             初始化页（layout + setup/page）
src/components/              通用组件
src/components/layouts/      布局组件（BentoGrid，预留）
src/components/ui/           基础 UI 组件
src/context/                 React Context 与 Provider
src/hooks/                   通用 Hooks
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
src/routes/                  路由入口组件（(_auth)、(_setup)、_authenticated）
src/routes/(_auth)/route.tsx 唯一认证路由入口
src/routes/(_setup)/route.tsx 初始化路由入口
src/routes/_authenticated/route.tsx 会话认证检查
src/stores/                  客户端状态仓库
src/styles/                  全局样式与主题变量（_global.css）
src/styles/layouts/          布局样式（_bento-layout.css）
```

## 路由

- `/sign-in`：认证页，默认密码登录 / 通行密钥 / 外部登录；开启登录验证码时在提交后弹出图片验证码弹窗，启用两步验证的账号在密码或通行密钥校验后进入动态验证码步骤；`?routes=forgot-password` 切换找回密码，`?routes=legal&tab=terms|privacy|confidentiality` 打开法律协议弹层。
- `/setup`：首次运行初始化页，创建超级管理员并设置站点名称与站点地址；管理员邮箱由飞书同步，初始化表单不填写邮箱。

`src/app/(auth)` 只有 `layout.tsx` 和 `page.tsx`；`src/routes/(_auth)/route.tsx` 是唯一认证路由入口，根据 `routes` 查询参数切换登录、找回密码和法律弹层。页面内容放在 `src/modules/_auth`。`/sign-in` 通过 Next rewrite 渲染 `(auth)/page`。`src/app/(setup)/setup/page.tsx` 通过 `src/routes/(_setup)/route.tsx` 渲染 `/setup`，页面内容放在 `src/modules/_setup`。

## 认证

[src/modules/_setup/composables/_setup-guard.ts](src/modules/_setup/composables/_setup-guard.ts) 提供初始化状态检查，[src/middleware.ts](src/middleware.ts) 在页面渲染前请求 `/apis/api.pudans.tolim.cn/v1alpha1/login/status`：

- `setupRequired` 为真时，`/` 与 `/sign-in` 重定向到 `/setup`。
- `setupRequired` 为假时，`/setup` 重定向到 `/sign-in`。
- `/setup`、`/sign-in` 与认证页 `/` 不经过会话认证检查。
- 其余页面请求 `/login/session`：未登录或请求失败时重定向到 `/sign-in?redirect=<原路径>`；后端不可达时按未登录处理。

登录提交时按 `/login/status` 的 `loginCaptchaEnabled` 弹出图片验证码弹窗；开启两步验证的账号在 `/login` 返回 `twoFactorRequired` 后进入 TOTP 验证，验证码提交到 `/challenges/two-factor/totp`。浏览器端接口调用统一通过 `@pudans-dev/api-client`；`/login` 与两步验证挑战由 Spring Security 处理、不在 OpenAPI 文档中，因此使用该包导出的 `axiosInstance`。Edge 中间件在页面渲染前直接使用 `fetch` 请求会话与初始化状态，因为生成的 axios 客户端运行在浏览器或 Node 运行时。

## 布局

- 登录区域由 `(auth)` 与 `(setup)` 分组布局使用 `AuthLayout`：固定背景视频、渐变遮罩、品牌栏、主题切换与半透明玻璃卡片；认证页保留视频暂停控制与法律协议入口，初始化页隐藏这些入口并居中展示 720px 卡片。
- 认证页与初始化页固定在视口高度，页面不滚动；内容超出时在卡片内部滚动。
- `BentoGrid` 布局组件保留给后续控制台页面：卡片贴同一组行列线，尺寸跨行跨列错位。

## API 客户端

`packages/api-client` 是 npm workspace 包 `@pudans-dev/api-client`，从 [api-docs/openapi/v3_0/apis_api_v1alpha1.json](../api-docs/openapi/v3_0/apis_api_v1alpha1.json) 自动生成：

- `npm run gen --workspace @pudans-dev/api-client`：规范化临时 OpenAPI 副本后调用 openapi-generator，重新生成 `src/`。
- `npm run build:api-client`：编译到 `dist/`；`predev`、`prebuild`、`pretypecheck` 会自动执行。
- `entry/` 是手写入口，提供 `apiClient`、`createApiClient` 与共享的 `axiosInstance`；`src/` 为生成产物，不要手改。

生成前会修正当前 OpenAPI 文档中的重复参数、缺失路径参数和请求体 `oneOf` 包装，不修改仓库中的原始文档。`/login/status` 的 `loginCaptchaEnabled` 尚未写入当前 OpenAPI 文档，前端通过本地扩展类型读取；后端文档补齐后重新生成即可移除。

## 后端

后端 `appkernel` 监听 `8090` 端口，接口前缀为 `/apis/api.pudans.tolim.cn/v1alpha1/`。开发时 Next.js 通过 rewrites 将 `/apis/*` 与 `/login` 代理到 `PUDANS_API_ORIGIN`（默认 `http://localhost:8090`）。
