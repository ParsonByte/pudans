package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.dto.PolicyDocument;
import cn.tolim.pudans.iam.authorization.dto.PolicyValidationResult;
import cn.tolim.pudans.iam.authorization.exception.AuthorizationErrors;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import cn.tolim.pudans.iam.authorization.repository.ServiceActionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyDocumentService {

    private final ObjectMapper objectMapper;
    private final AppRepository appRepository;
    private final ServiceActionRepository serviceActionRepository;

    // Parses a policy document JSON string
    // Parameter document: String policy document JSON
    // Returns: PolicyDocument
    public PolicyDocument parse(String document) {
        if (document == null || document.isBlank()) {
            throw AuthorizationErrors.badRequest("Policy document must not be blank");
        }
        try {
            return objectMapper.readValue(document, PolicyDocument.class);
        } catch (JsonProcessingException e) {
            throw AuthorizationErrors.badRequest("Invalid policy document JSON: " + e.getOriginalMessage());
        }
    }

    // Parses a policy document JSON string without throwing, used when loading stored policies
    // Parameter document: String policy document JSON
    // Returns: PolicyDocument null when the document cannot be parsed
    public PolicyDocument parseOrNull(String document) {
        try {
            return parse(document);
        } catch (RuntimeException e) {
            log.warn("Skipping invalid authorization policy document: {}", e.getMessage());
            return null;
        }
    }

    // Serializes a policy document to JSON
    // Parameter document: PolicyDocument
    // Returns: String
    public String serialize(PolicyDocument document) {
        if (document == null) {
            throw AuthorizationErrors.badRequest("Policy document must not be null");
        }
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw AuthorizationErrors.badRequest("Unable to serialize policy document: " + e.getMessage());
        }
    }

    // Validates the structure of a policy document
    // Parameter document: PolicyDocument
    // Returns: PolicyValidationResult
    public PolicyValidationResult validate(PolicyDocument document) {
        PolicyValidationResult result = new PolicyValidationResult();
        if (document == null) {
            result.addError("document must not be null");
            return result;
        }
        List<PolicyDocument.Statement> statements = document.getStatement();
        if (statements == null || statements.isEmpty()) {
            result.addError("document.statement must contain at least one statement");
            return result;
        }
        result.setStatementCount(statements.size());
        Set<String> sids = new HashSet<>();
        for (int index = 0; index < statements.size(); index++) {
            PolicyDocument.Statement statement = statements.get(index);
            String prefix = "statement[" + index + "]";
            if (statement == null) {
                result.addError(prefix + " must not be null");
                continue;
            }
            if (statement.getSid() != null && !statement.getSid().isBlank()
                    && !sids.add(statement.getSid())) {
                result.addError(prefix + ".sid '" + statement.getSid() + "' is duplicated");
            }
            String effect = statement.getEffect();
            if (effect == null || (!"Allow".equalsIgnoreCase(effect.trim())
                    && !"Deny".equalsIgnoreCase(effect.trim()))) {
                result.addError(prefix + ".effect must be Allow or Deny");
            }
            List<String> actions = statement.getAction();
            if (actions == null || actions.isEmpty()) {
                result.addError(prefix + ".action must not be empty");
            } else {
                for (String action : actions) {
                    if (action == null || action.isBlank()) {
                        result.addError(prefix + ".action contains a blank entry");
                    } else {
                        result.getActions().add(action.trim());
                    }
                }
            }
            List<String> resources = statement.getResource();
            if (resources != null) {
                for (String resource : resources) {
                    if (resource == null || resource.isBlank()) {
                        result.addError(prefix + ".resource contains a blank entry");
                    } else {
                        result.getResources().add(resource.trim());
                    }
                }
            }
        }
        return result;
    }

    // Validates a policy document and throws when it is invalid
    // Parameter document: PolicyDocument
    public void validateOrThrow(PolicyDocument document) {
        PolicyValidationResult result = validate(document);
        if (!Boolean.TRUE.equals(result.getValid())) {
            throw AuthorizationErrors.badRequest("Invalid policy document: "
                    + String.join("; ", result.getErrors()));
        }
    }

    // Verifies that every concrete action referenced by the document is registered by its application
    // Parameter document: PolicyDocument
    public void validateActionsRegistered(PolicyDocument document) {
        validateOrThrow(document);
        for (PolicyDocument.Statement statement : document.getStatement()) {
            List<String> actions = statement.getAction();
            if (actions == null) {
                continue;
            }
            for (String action : actions) {
                String trimmed = action == null ? null : action.trim();
                if (trimmed == null || trimmed.isBlank()
                        || GlobMatcher.isWildcard(trimmed) || trimmed.contains("${")) {
                    continue;
                }
                int separator = trimmed.indexOf(':');
                if (separator <= 0 || separator == trimmed.length() - 1) {
                    throw AuthorizationErrors.badRequest("Action '" + trimmed
                            + "' must be in {appCode}:{action} format");
                }
                String appCode = trimmed.substring(0, separator);
                AppEntity application = appRepository.findByCode(appCode)
                        .orElseThrow(() -> AuthorizationErrors.badRequest("Application '" + appCode
                                + "' referenced by action '" + trimmed + "' is not registered"));
                if (!serviceActionRepository.existsByAppIdAndAction(application.getId(), trimmed)) {
                    throw AuthorizationErrors.badRequest("Action '" + trimmed
                            + "' is not registered by application '" + appCode + "'");
                }
            }
        }
    }
}