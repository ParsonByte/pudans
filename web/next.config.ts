import type { NextConfig } from "next";

const apiOrigin = process.env.PUDANS_API_ORIGIN ?? "http://localhost:8090";

const nextConfig: NextConfig = {
  output: "standalone",
  transpilePackages: ["@pudans-dev/api-client"],
  async rewrites() {
    return [
      { source: "/apis/:path*", destination: `${apiOrigin}/apis/:path*` },
      { source: "/sudo", destination: `${apiOrigin}/sudo` },
      { source: "/sudo/:path*", destination: `${apiOrigin}/sudo/:path*` },
      { source: "/login", destination: `${apiOrigin}/login` },
      { source: "/challenges/:path*", destination: `${apiOrigin}/challenges/:path*` },
      { source: "/logout", destination: `${apiOrigin}/logout` },
    ];
  },
};

export default nextConfig;
