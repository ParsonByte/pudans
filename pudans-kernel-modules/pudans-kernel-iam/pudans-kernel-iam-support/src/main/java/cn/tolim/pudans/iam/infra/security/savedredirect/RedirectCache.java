package cn.tolim.pudans.iam.infra.security.savedredirect;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface RedirectCache {

    String PUDANS_SECURITY_SAVED_REDIRECT = "PUDANS_SECURITY_SAVED_REDIRECT";

    SavedRedirect getRedirect(HttpServletRequest request, HttpServletResponse response);

    void removeRedirect(HttpServletRequest request, HttpServletResponse response);

    void saveRedirect(HttpServletRequest request, HttpServletResponse response,
                      RedirectType redirectType);

    enum RedirectType {
        PARAMETER,
        REQUEST
    }
}
