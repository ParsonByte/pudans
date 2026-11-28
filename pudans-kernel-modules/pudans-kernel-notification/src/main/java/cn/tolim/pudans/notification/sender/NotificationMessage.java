package cn.tolim.pudans.notification.sender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Provider-neutral message handed to a sender implementation.
public final class NotificationMessage {

    private final String recipient;
    private final String subject;
    private final String content;
    private final String providerTemplateCode;
    private final Map<String, String> parameters;
    private final List<String> templateParameters;

    private NotificationMessage(Builder builder) {
        this.recipient = builder.recipient;
        this.subject = builder.subject;
        this.content = builder.content;
        this.providerTemplateCode = builder.providerTemplateCode;
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(builder.parameters));
        this.templateParameters = List.copyOf(builder.templateParameters);
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSubject() {
        return subject;
    }

    public String getContent() {
        return content;
    }

    public String getProviderTemplateCode() {
        return providerTemplateCode;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public List<String> getTemplateParameters() {
        return templateParameters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String recipient;
        private String subject;
        private String content;
        private String providerTemplateCode;
        private Map<String, String> parameters = new LinkedHashMap<>();
        private List<String> templateParameters = new ArrayList<>();

        public Builder recipient(String value) {
            this.recipient = value;
            return this;
        }

        public Builder subject(String value) {
            this.subject = value;
            return this;
        }

        public Builder content(String value) {
            this.content = value;
            return this;
        }

        public Builder providerTemplateCode(String value) {
            this.providerTemplateCode = value;
            return this;
        }

        public Builder parameters(Map<String, String> value) {
            this.parameters = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
            return this;
        }

        public Builder templateParameters(List<String> value) {
            this.templateParameters = value == null ? new ArrayList<>() : new ArrayList<>(value);
            return this;
        }

        public NotificationMessage build() {
            return new NotificationMessage(this);
        }
    }
}