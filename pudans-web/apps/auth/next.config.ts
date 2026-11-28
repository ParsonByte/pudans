import type { NextConfig } from "next";

const apiOrigin = process.env.PUDANS_API_ORIGIN;

if (!apiOrigin) {
  throw new Error("PUDANS_API_ORIGIN is required");
}

const nextConfig: NextConfig = {
  transpilePackages: ["@pudans-dev/api-client", "@pudans-dev/share-component"],
  turbopack: {
    rules: {
      "*.less": {
        as: "*.css",
        loaders: ["less-loader"],
      },
    },
  },
  async rewrites() {
    return [{ source: "/apis/:path*", destination: `${apiOrigin}/apis/:path*` }];
  },
};

export default nextConfig;