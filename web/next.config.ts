import type { NextConfig } from "next";

const apiOrigin = process.env.PUDANS_API_ORIGIN ?? "http://localhost:8090";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [
      { source: "/apis/:path*", destination: `${apiOrigin}/apis/:path*` },
      { source: "/login", destination: `${apiOrigin}/login` },
      { source: "/logout", destination: `${apiOrigin}/logout` },
    ];
  },
};

export default nextConfig;
