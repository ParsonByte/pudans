package cn.tolim.pudans.iam.infra.web.useragent;

import jakarta.servlet.http.HttpServletRequest;

public interface UserAgentParser {
    UserAgent getUserAgent(HttpServletRequest request);
}
