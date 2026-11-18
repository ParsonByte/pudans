package cn.tolim.pudans.core.rbac;

import cn.tolim.pudans.contract.core.extension.rbac.Role.PolicyRule;
import java.util.Arrays;
import java.util.Collection;

public final class PolicyRuleCoverage {

    private static final String ALL = "*";

    private PolicyRuleCoverage() {}

    public static boolean covers(Collection<PolicyRule> granted, Collection<PolicyRule> requested) {
        return requested.stream().allMatch(rule -> covers(granted, rule));
    }

    public static boolean covers(Collection<PolicyRule> granted, PolicyRule requested) {
        var hasResourceGrant = requested.getResources().length > 0;
        var hasNonResourceGrant = requested.getNonResourceURLs().length > 0;
        if (!hasResourceGrant && !hasNonResourceGrant) {
            return true;
        }
        if (hasResourceGrant && !resourceAccessCovered(granted, requested)) {
            return false;
        }
        return !hasNonResourceGrant || nonResourceAccessCovered(granted, requested);
    }

    private static boolean resourceAccessCovered(Collection<PolicyRule> granted, PolicyRule requested) {
        for (var group : requested.getApiGroups()) {
            for (var resource : requested.getResources()) {
                for (var verb : requested.getVerbs()) {
                    var names = requested.getResourceNames();
                    if (names.length == 0) {
                        if (!anyResourceGrant(granted, group, resource, verb, null)) {
                            return false;
                        }
                        continue;
                    }
                    for (var name : names) {
                        if (!anyResourceGrant(granted, group, resource, verb, name)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean anyResourceGrant(
            Collection<PolicyRule> granted, String group, String resource, String verb, String name) {
        for (var rule : granted) {
            if (rule.getResources().length == 0
                    || !valueMatches(rule.getApiGroups(), group)
                    || !valueMatches(rule.getResources(), resource)
                    || !valueMatches(rule.getVerbs(), verb)
                    || !resourceNamesCover(rule.getResourceNames(), name)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean nonResourceAccessCovered(Collection<PolicyRule> granted, PolicyRule requested) {
        for (var url : requested.getNonResourceURLs()) {
            for (var verb : requested.getVerbs()) {
                if (!anyNonResourceGrant(granted, url, verb)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean anyNonResourceGrant(Collection<PolicyRule> granted, String url, String verb) {
        for (var rule : granted) {
            if (rule.getNonResourceURLs().length == 0
                    || !valueMatches(rule.getVerbs(), verb)
                    || !nonResourceUrlCovers(rule.getNonResourceURLs(), url)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean valueMatches(String[] grantedValues, String requestedValue) {
        for (var grantedValue : grantedValues) {
            if (ALL.equals(grantedValue) || grantedValue.equals(requestedValue)) {
                return true;
            }
        }
        return false;
    }

    private static boolean resourceNamesCover(String[] grantedNames, String requestedName) {
        if (grantedNames.length == 0) {
            return true;
        }
        if (requestedName == null) {
            return Arrays.stream(grantedNames).anyMatch(ALL::equals);
        }
        for (var grantedName : grantedNames) {
            if (ALL.equals(grantedName) || grantedName.equals(requestedName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nonResourceUrlCovers(String[] grantedUrls, String requestedUrl) {
        for (var grantedUrl : grantedUrls) {
            if (ALL.equals(grantedUrl) || grantedUrl.equals(requestedUrl)) {
                return true;
            }
            if (grantedUrl.endsWith(ALL)) {
                var grantedPrefix = grantedUrl.substring(0, grantedUrl.length() - 1);
                var requestedPrefix = requestedUrl.endsWith(ALL)
                        ? requestedUrl.substring(0, requestedUrl.length() - 1)
                        : requestedUrl;
                if (requestedPrefix.startsWith(grantedPrefix)) {
                    return true;
                }
            }
        }
        return false;
    }
}
