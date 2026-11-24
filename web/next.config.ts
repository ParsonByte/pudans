import type { NextConfig } from "next";

const apiOrigin = process.env.PUDANS_API_ORIGIN ?? "http://localhost:8090";

const nextConfig: NextConfig = {
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
