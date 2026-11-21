package cn.tolim.pudans.core.security.authorization;

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

    private String combineResourceName(String name, String subName) {
        if (StringUtils.isBlank(name)) {
            return subName;
        }
        if (StringUtils.isBlank(subName)) {
            return name;
        }
        return name + "/" + subName;
    }

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
