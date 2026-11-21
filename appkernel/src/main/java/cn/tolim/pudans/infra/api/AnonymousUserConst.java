package cn.tolim.pudans.infra.api;

public interface AnonymousUserConst {
    String PRINCIPAL = "anonymousUser";

    String Role = "anonymous";

    static boolean isAnonymousUser(String principal) {
        return PRINCIPAL.equals(principal);
    }
}
