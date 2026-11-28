package cn.tolim.pudans.gateway.filter;

public record GatewayAuthOptions(
        String sessionCookieName,
        String loginPageUrl,
        boolean cookieSecure,
        String cookieSameSite,
        String cookieDomain,
        boolean redirectPageRequests) {

    public static GatewayAuthOptions defaults() {
        return new GatewayAuthOptions("PUDANS_SID", "/login", false, "Lax", "", true);
    }
}