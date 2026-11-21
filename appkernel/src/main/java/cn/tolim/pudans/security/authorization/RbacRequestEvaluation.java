package cn.tolim.pudans.security.authorization;

import java.util.Objects;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import cn.tolim.pudans.contract.rbac.Role;

public class RbacRequestEvaluation {
    interface WildCard {
        String APIGroupAll = "*";
        String ResourceAll = "*";
        String VerbAll = "*";
        String NonResourceAll = "*";
    }

    // 资源型与非资源型请求采用不同规则维度
    protected boolean ruleAllows(Attributes requestAttributes, Role.PolicyRule rule) {
        if (requestAttributes.isResourceRequest()) {
            String combinedResource = requestAttributes.getResource();
            if (StringUtils.isNotBlank(requestAttributes.getSubresource())) {
                combinedResource = requestAttributes.getResource() + "/" + requestAttributes.getSubresource();
            }
            return verbMatches(rule, requestAttributes.getVerb())
                    && apiGroupMatches(rule, requestAttributes.getApiGroup())
                    && resourceMatches(rule, combinedResource, requestAttributes.getSubresource())
                    && resourceNameMatches(
                            rule, combineResourceName(requestAttributes.getName(), requestAttributes.getSubName()));
        }
        return verbMatches(rule, requestAttributes.getVerb())
                && nonResourceURLMatches(rule, requestAttributes.getPath());
    }

    // 把资源名与子名称拼成规则匹配名称
    private String combineResourceName(String name, String subName) {
        if (StringUtils.isBlank(name)) {
            return subName;
        }
        if (StringUtils.isBlank(subName)) {
            return name;
        }
        return name + "/" + subName;
    }

    // 规则动词支持通配符或精确匹配
    protected boolean verbMatches(Role.PolicyRule rule, String requestedVerb) {
        for (String ruleVerb : rule.getVerbs()) {
            if (Objects.equals(ruleVerb, WildCard.VerbAll)) {
                return true;
            }
            if (Objects.equals(ruleVerb, requestedVerb)) {
                return true;
            }
        }
        return false;
    }

    // API 组支持通配符或精确匹配
    protected boolean apiGroupMatches(Role.PolicyRule rule, String requestedGroup) {
        for (String ruleGroup : rule.getApiGroups()) {
            if (Objects.equals(ruleGroup, WildCard.APIGroupAll)) {
                return true;
            }
            if (Objects.equals(ruleGroup, requestedGroup)) {
                return true;
            }
        }
        return false;
    }

    // 资源支持全通配、完整名与子资源通配
    protected boolean resourceMatches(
            Role.PolicyRule rule, String combinedRequestedResource, String requestedSubresource) {
        for (String ruleResource : rule.getResources()) {
            
            if (Objects.equals(ruleResource, WildCard.ResourceAll)) {
                return true;
            }
            
            if (Objects.equals(ruleResource, combinedRequestedResource)) {
                return true;
            }

            
            
            if (StringUtils.isBlank(requestedSubresource)) {
                continue;
            }
            
            if (Objects.equals(ruleResource, "*/" + requestedSubresource)) {
                return true;
            }
        }
        return false;
    }

    // 资源名按分段模式匹配，空模式表示不限
    protected boolean resourceNameMatches(Role.PolicyRule rule, String requestedName) {
        if (ArrayUtils.isEmpty(rule.getResourceNames())) {
            return true;
        }
        String[] requestedNameParts = ArrayUtils.nullToEmpty(StringUtils.split(requestedName, "/"));
        for (String ruleName : rule.getResourceNames()) {
            String[] patternParts = StringUtils.split(ruleName, "/");
            var matched = true;
            for (int i = 0; i < patternParts.length; i++) {
                String patternPart = patternParts[i];
                String textPart = StringUtils.EMPTY;
                if (requestedNameParts.length > i) {
                    textPart = requestedNameParts[i];
                }
                if (!matchPart(patternPart, textPart)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return true;
            }
        }
        return false;
    }

    // 模式段支持前缀、后缀与全通配
    private static boolean matchPart(String patternPart, String textPart) {
        if (patternPart.equals("*")) {
            return true;
        } else if (patternPart.startsWith("*")) {
            return textPart.endsWith(patternPart.substring(1));
        } else if (patternPart.endsWith("*")) {
            return textPart.startsWith(patternPart.substring(0, patternPart.length() - 1));
        } else {
            return patternPart.equals(textPart);
        }
    }

    // 非资源 URL 支持精确与末尾通配匹配
    protected boolean nonResourceURLMatches(Role.PolicyRule rule, String requestedURL) {
        for (String ruleURL : rule.getNonResourceURLs()) {
            if (Objects.equals(ruleURL, WildCard.NonResourceAll)) {
                return true;
            }
            if (Objects.equals(ruleURL, requestedURL)) {
                return true;
            }
            if (StringUtils.endsWith(ruleURL, WildCard.NonResourceAll)
                    && StringUtils.startsWith(requestedURL, StringUtils.stripEnd(ruleURL, WildCard.NonResourceAll))) {
                return true;
            }
        }
        return false;
    }
}
