package cn.tolim.pudans.core.infra.exception;

import java.text.MessageFormat;
import java.util.Map;
import org.jspecify.annotations.Nullable;

// 统一维护 Spring 框架异常
final class ProblemDetailMessages {

    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("problemDetail.title.org.springframework.web.server.ServerWebInputException", "请求参数有误"),
            Map.entry(
                    "problemDetail.title.org.springframework.web.server.UnsupportedMediaTypeStatusException",
                    "不支持的媒体类型"),
            Map.entry(
                    "problemDetail.title.org.springframework.web.server.MissingRequestValueException", "缺少请求参数"),
            Map.entry(
                    "problemDetail.title.org.springframework.web.server.UnsatisfiedRequestParameterException",
                    "请求参数不满足要求"),
            Map.entry(
                    "problemDetail.title.org.springframework.web.bind.support.WebExchangeBindException",
                    "数据绑定或校验失败"),
            Map.entry(
                    "problemDetail.title.org.springframework.web.server.NotAcceptableStatusException",
                    "不可接受的响应类型"),
            Map.entry("problemDetail.title.org.springframework.web.server.ServerErrorException", "服务器错误"),
            Map.entry("problemDetail.title.org.springframework.web.server.MethodNotAllowedException", "请求方法不允许"),
            Map.entry(
                    "problemDetail.title.org.springframework.security.authentication.BadCredentialsException", "无效凭据"));

    private static final Map<String, String> DETAILS = Map.ofEntries(
            Map.entry(
                    "problemDetail.org.springframework.web.server.UnsupportedMediaTypeStatusException",
                    "不支持内容类型 {0}。支持的媒体类型：{1}。"),
            Map.entry(
                    "problemDetail.org.springframework.web.server.UnsupportedMediaTypeStatusException.parseError",
                    "无法解析 Content-Type。"),
            Map.entry(
                    "problemDetail.org.springframework.web.server.MissingRequestValueException",
                    "缺少必需的 {0}“{1}”。"),
            Map.entry(
                    "problemDetail.org.springframework.web.server.UnsatisfiedRequestParameterException",
                    "实际请求参数不满足条件“{0}”。"),
            Map.entry(
                    "problemDetail.org.springframework.web.bind.support.WebExchangeBindException",
                    "请求内容无效。全局错误：{0}。字段错误：{1}。"),
            Map.entry(
                    "problemDetail.org.springframework.web.server.NotAcceptableStatusException", "可接受的响应类型：{0}。"),
            Map.entry(
                    "problemDetail.org.springframework.web.server.NotAcceptableStatusException.parseError",
                    "无法解析 Accept 请求头。"),
            Map.entry("problemDetail.org.springframework.web.server.ServerErrorException", "{0}。"),
            Map.entry(
                    "problemDetail.org.springframework.web.server.MethodNotAllowedException",
                    "不支持请求方法 {0}。支持的方法：{1}。"),
            Map.entry(
                    "problemDetail.org.springframework.security.authentication.BadCredentialsException",
                    "用户名或密码错误。"));

    private ProblemDetailMessages() {}

    static @Nullable String title(String code) {
        return TITLES.get(code);
    }

    static @Nullable String detail(String code, @Nullable Object[] args) {
        var template = DETAILS.get(code);
        if (template == null) {
            return null;
        }
        return args == null || args.length == 0 ? template : MessageFormat.format(template, args);
    }
}
