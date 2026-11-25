import type { NextConfig } from "next";

import { networkInterfaces } from "node:os";

const apiOrigin = process.env.PUDANS_API_ORIGIN ?? "http://localhost:8090";

// 开发期也允许用本机局域网地址打开控制台，否则 Next 会拦截 dev 资源
const localDevOrigins = Object.values(networkInterfaces())
    .flatMap((entries) => entries ?? [])
    .filter((entry) => entry.family === "IPv4" && !entry.internal)
    .map((entry) => entry.address);

const nextConfig: NextConfig = {
    allowedDevOrigins: ["127.0.0.1", "localhost", ...localDevOrigins],
    transpilePackages: ["@pudans-dev/api-client"],
    async rewrites() {
        return [
            { source: "/apis/:path*", destination: `${apiOrigin}/apis/:path*` },
            { source: "/login", destination: `${apiOrigin}/login` },
            { source: "/logout", destination: `${apiOrigin}/logout` },
            { source: "/workspace/:workspace", destination: "/:workspace" },
            { source: "/workspace/:workspace/:path*", destination: "/:workspace/:path*" },
            { source: "/setup", destination: "/" },
        ];
    },
};

export default nextConfig;
