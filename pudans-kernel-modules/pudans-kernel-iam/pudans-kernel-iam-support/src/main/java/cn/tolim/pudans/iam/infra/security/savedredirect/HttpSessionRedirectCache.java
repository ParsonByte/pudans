package cn.tolim.pudans.iam.infra.security.savedredirect;

import cn.tolim.pudans.iam.infra.context.ServletContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.UrlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpSessionRedirectCache implements RedirectCache {

    private static final Logger logger = LoggerFactory
            .getLogger(HttpSessionRedirectCache.class);

    public static List<SavedRedirect.Parameter> getParameters(Map<String, String> parameterMap) {
        List<SavedRedirect.Parameter> parameters = new ArrayList<>();
        parameterMap.forEach(
                (key, value) -> parameters.add(new SavedRedirect.Parameter(key, value)));
        return parameters;
    }

    @Override
    public SavedRedirect getRedirect(HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) == null) {
            return null;
        }
        return (SavedRedirect) request.getSession().getAttribute(PUDANS_SECURITY_SAVED_REDIRECT);
    }

    @Override
    public void removeRedirect(HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.getSession().removeAttribute(PUDANS_SECURITY_SAVED_REDIRECT);
        }
        logger.debug("已从会话中移除保存的重定向");
    }

    @Override
    public void saveRedirect(HttpServletRequest request, HttpServletResponse response,
                             RedirectType redirectType) {
        if (redirectType == RedirectType.PARAMETER) {
            saveParameterRedirect(request);
        } else if (redirectType == RedirectType.REQUEST) {
            saveRequestRedirect(request);
        }
    }

    private void saveParameterRedirect(HttpServletRequest request) {
        String redirectUri = request.getParameter("redirect_uri");
        if (StringUtils.isBlank(redirectUri)) {
            return;
        }
        SavedRedirect redirect = new SavedRedirect();
        int queryIndex = redirectUri.indexOf('?');
        redirect.setAction(queryIndex > -1 ? redirectUri.substring(0, queryIndex) : redirectUri);
        redirect.setParameters(getParameters(ServletContextService
                .getParameterForUrl(redirectUri)));
        redirect.setMethod(HttpMethod.GET.name());
        request.getSession().setAttribute(PUDANS_SECURITY_SAVED_REDIRECT, redirect);
        logger.debug("已将会话中的参数重定向保存");
    }

    private void saveRequestRedirect(HttpServletRequest request) {
        SavedRedirect redirect = new SavedRedirect();
        redirect.setParameters(getParametersByArray(request.getParameterMap()));
        redirect.setMethod(request.getMethod());
        redirect.setAction(UrlUtils.buildFullRequestUrl(request.getScheme(),
                request.getServerName(), request.getServerPort(), request.getRequestURI(), null));
        request.getSession().setAttribute(PUDANS_SECURITY_SAVED_REDIRECT, redirect);
        logger.debug("已将会话中的请求重定向保存");
    }

    public List<SavedRedirect.Parameter> getParametersByArray(
            Map<String, String[]> parameterMap) {
        List<SavedRedirect.Parameter> parameters = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String[] values = entry.getValue();
            if (values == null || values.length == 0 || StringUtils.isEmpty(values[0])) {
                continue;
            }
            parameters.add(new SavedRedirect.Parameter(entry.getKey(), values[0]));
        }
        return parameters;
    }
}
