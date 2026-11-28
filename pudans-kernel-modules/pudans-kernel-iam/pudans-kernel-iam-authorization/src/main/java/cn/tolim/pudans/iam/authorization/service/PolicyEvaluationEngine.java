package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.constant.AuthorizationConstants;
import cn.tolim.pudans.iam.authorization.dto.PolicyDocument;
import cn.tolim.pudans.iam.authorization.enums.PolicyDecision;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
public class PolicyEvaluationEngine {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    // Evaluates a set of candidate policies against an authorization request
    // Parameter policies: List of candidate policies
    // Parameter appCode: String application code
    // Parameter action: String requested action
    // Parameter resource: String requested resource
    // Parameter orgId: String request organization ID
    // Parameter context: Map<String, Object> request context
    // Returns: Result evaluation result, explicit deny wins and default is deny
    public Result evaluate(List<CandidatePolicy> policies, String appCode, String action,
                           String resource, String orgId, Map<String, Object> context) {
        List<Match> allowMatches = new ArrayList<>();
        List<Match> denyMatches = new ArrayList<>();
        if (policies != null) {
            for (CandidatePolicy candidate : policies) {
                if (candidate == null || candidate.document() == null
                        || candidate.document().getStatement() == null) {
                    continue;
                }
                for (PolicyDocument.Statement statement : candidate.document().getStatement()) {
                    if (statement == null) {
                        continue;
                    }
                    String effect = normalizeEffect(statement.getEffect());
                    if (effect == null) {
                        continue;
                    }
                    if (!actionMatches(statement.getAction(), action)) {
                        continue;
                    }
                    if (!resourceMatches(statement.getResource(), resource, orgId, context)) {
                        continue;
                    }
                    if (!ConditionMatcher.matches(statement.getCondition(), orgId, context)) {
                        continue;
                    }
                    Match match = new Match(candidate.code(), statement.getSid(), effect);
                    if (AuthorizationConstants.EFFECT_DENY.equals(effect)) {
                        denyMatches.add(match);
                    } else {
                        allowMatches.add(match);
                    }
                }
            }
        }
        List<Match> allMatches = new ArrayList<>(denyMatches);
        allMatches.addAll(allowMatches);
        List<String> matchedPolicies = distinctPolicyCodes(allMatches);
        if (!denyMatches.isEmpty()) {
            Match firstDeny = denyMatches.get(0);
            String reason = "Explicit deny by policy '" + firstDeny.policyCode() + "' statement '"
                    + statementLabel(firstDeny) + "'";
            return new Result(PolicyDecision.DENY, reason, matchedPolicies, allMatches);
        }
        if (!allowMatches.isEmpty()) {
            String reason = "Allowed by " + allowMatches.size() + " matching statement(s)";
            return new Result(PolicyDecision.ALLOW, reason, matchedPolicies, allMatches);
        }
        return new Result(PolicyDecision.DENY, "Default deny: no matching allow statement",
                List.of(), List.of());
    }

    // Checks whether any action pattern of a statement matches the requested action
    // Parameter patterns: List<String> statement actions
    // Parameter action: String requested action
    // Returns: boolean
    public boolean actionMatches(List<String> patterns, String action) {
        if (patterns == null || patterns.isEmpty() || action == null || action.isBlank()) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            String trimmed = pattern.trim();
            if (AuthorizationConstants.WILDCARD.equals(trimmed) || trimmed.equals(action)) {
                return true;
            }
            if (GlobMatcher.isWildcard(trimmed) && GlobMatcher.matches(trimmed, action)) {
                return true;
            }
        }
        return false;
    }

    // Checks whether any resource pattern of a statement matches the requested resource
    // Parameter patterns: List<String> statement resources, empty means all resources
    // Parameter resource: String requested resource
    // Parameter orgId: String request organization ID
    // Parameter context: Map<String, Object> request context
    // Returns: boolean
    public boolean resourceMatches(List<String> patterns, String resource, String orgId,
                                   Map<String, Object> context) {
        if (patterns == null || patterns.isEmpty()) {
            return true;
        }
        for (String pattern : patterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            String trimmed = pattern.trim();
            if (AuthorizationConstants.WILDCARD.equals(trimmed)) {
                return true;
            }
            if (resource == null || resource.isBlank()) {
                continue;
            }
            String resolved = resolveVariables(trimmed, orgId, context);
            if (resolved == null) {
                continue;
            }
            if (resolved.equals(resource)) {
                return true;
            }
            if (GlobMatcher.matches(resolved, resource)) {
                return true;
            }
        }
        return false;
    }

    // Resolves ${name} variables of a policy pattern from the organization ID and context
    // Parameter pattern: String
    // Parameter orgId: String
    // Parameter context: Map<String, Object>
    // Returns: String null when a variable cannot be resolved
    public String resolveVariables(String pattern, String orgId, Map<String, Object> context) {
        if (pattern == null || pattern.indexOf("${") < 0) {
            return pattern;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(pattern);
        StringBuilder resolved = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            Object value;
            if ("orgId".equals(name) || "organizationId".equals(name)) {
                value = orgId != null ? orgId : (context == null ? null : context.get(name));
            } else {
                value = context == null ? null : context.get(name);
            }
            if (value == null) {
                return null;
            }
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static String normalizeEffect(String effect) {
        if (effect == null) {
            return null;
        }
        if (AuthorizationConstants.EFFECT_ALLOW.equalsIgnoreCase(effect.trim())) {
            return AuthorizationConstants.EFFECT_ALLOW;
        }
        if (AuthorizationConstants.EFFECT_DENY.equalsIgnoreCase(effect.trim())) {
            return AuthorizationConstants.EFFECT_DENY;
        }
        return null;
    }

    private static List<String> distinctPolicyCodes(List<Match> matches) {
        Set<String> codes = new LinkedHashSet<>();
        matches.forEach(match -> {
            if (match.policyCode() != null) {
                codes.add(match.policyCode());
            }
        });
        return new ArrayList<>(codes);
    }

    private static String statementLabel(Match match) {
        return match.sid() == null || match.sid().isBlank() ? "<unnamed>" : match.sid();
    }

    // Candidate policy passed to the evaluator
    public record CandidatePolicy(String code, PolicyDocument document) {
    }

    // Statement that matched an evaluation request
    public record Match(String policyCode, String sid, String effect) {
    }

    // Evaluation result of the engine
    public record Result(PolicyDecision decision, String reason, List<String> matchedPolicies,
                         List<Match> matches) {

        // Whether the result allows the request
        // Returns: boolean
        public boolean allowed() {
            return PolicyDecision.ALLOW == this.decision;
        }
    }
}