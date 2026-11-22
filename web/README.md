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
```

## 目录

```text
public/                      静态资源（登录背景视频、品牌图标）
src/app/                     Next.js 页面入口（(auth) 分组）
src/app/(auth)/              认证页（layout + page）
src/components/              通用组件
src/components/layouts/      布局组件（BentoGrid，预留）
src/components/ui/           基础 UI 组件
src/context/                 React Context 与 Provider
src/hooks/                   通用 Hooks
src/middleware.ts            页面渲染前的会话认证检查
src/modules/                 页面模块
src/modules/_auth/           认证模块
src/modules/_auth/components/ 共享认证组件
src/modules/_auth/composables/ 登录、恢复与 WebAuthn 逻辑
src/modules/_auth/modules/   sign-in、legal、forgot-password
src/modules/_auth/styles/    认证样式（_auth-layout.css）
src/modules/_auth/index.tsx  认证模块出口
src/routes/                  路由入口组件（(_auth)、_authenticated）
src/routes/(_auth)/route.tsx 唯一认证路由入口
src/routes/_authenticated/route.tsx 会话认证检查
src/stores/                  客户端状态仓库
src/styles/                  全局样式与主题变量（_global.css）
src/styles/layouts/          布局样式（_bento-layout.css）
```

## 路由

- `/sign-in`：认证页，默认密码登录 / 通行密钥 / 外部登录；`?routes=forgot-password` 切换找回密码，`?routes=legal&tab=terms|privacy|confidentiality` 打开法律协议弹层。

`src/app/(auth)` 只有 `layout.tsx` 和 `page.tsx`；`src/routes/(_auth)/route.tsx` 是唯一认证路由入口，根据 `routes` 查询参数切换登录、找回密码和法律弹层。页面内容放在 `src/modules/_auth`。`/sign-in` 通过 Next rewrite 渲染 `(auth)/page`。

## 认证

[src/routes/_authenticated/route.tsx](src/routes/_authenticated/route.tsx) 提供会话检查，[src/middleware.ts](src/middleware.ts) 在页面渲染前调用它请求 `/apis/api.pudans.tolim.cn/v1alpha1/login/session`：

- 未登录或会话请求失败：重定向到 `/sign-in?redirect=<原路径>`。
- `/sign-in`、认证页 `/`、静态资源、`/apis` 与 `/login` 不经过认证检查。
- 后端不可达时按未登录处理。

## 布局

- 登录区域由 `(auth)` 分组布局使用 `AuthLayout`：固定背景视频、渐变遮罩、品牌栏、主题切换、视频暂停控制与法律协议入口。
- `BentoGrid` 布局组件保留给后续控制台页面：卡片贴同一组行列线，尺寸跨行跨列错位。

## 后端

后端 `appkernel` 监听 `8090` 端口，接口前缀为 `/apis/api.pudans.tolim.cn/v1alpha1/`。开发时 Next.js 通过 rewrites 将 `/apis/*` 与 `/login` 代理到 `PUDANS_API_ORIGIN`（默认 `http://localhost:8090`）。
