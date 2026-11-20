package cn.tolim.pudans.core.ai.library.command;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.core.extension.ai.AiSlashCommand;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SlashCommandService {

    public static final String CODE_COMMAND_NOT_FOUND = "ai-command-not-found";

    private static final Pattern NAME_PATTERN = Pattern.compile(AiSlashCommand.NAME_PATTERN);

    private static final Pattern POSITIONAL_ARGUMENT = Pattern.compile("\\$([1-9])(?!\\d)");

    private static final String ARGUMENTS_PLACEHOLDER = "$ARGUMENTS";

    private static final int MAX_POSITIONAL_ARGUMENTS = 9;

    private final ReactiveExtensionClient client;

    public Flux<AiSlashCommand> byAgent(String agentName) {
        requireAgentName(agentName);
        var options = ListOptions.builder()
                .andQuery(equal("spec.enabled", true))
                .andQuery(equal("spec.agents", agentName))
                .build();
        return client.listAll(AiSlashCommand.class, options, defaultSort());
    }

    public Mono<ResolvedSlashCommand> resolve(String agentName, String text) {
        var parsed = parse(text);
        return byAgent(agentName)
                .filter(command -> parsed.name().equals(command.getSpec().getName()))
                .next()
                .switchIfEmpty(Mono.error(commandNotFound(parsed.name())))
                .map(command -> new ResolvedSlashCommand(
                        parsed.name(), expand(command.getSpec().getPrompt(), parsed.arguments())));
    }

    public Mono<AiSlashCommand> create(AiSlashCommand command) {
        validate(command);
        return client.create(command);
    }

    public Mono<AiSlashCommand> update(AiSlashCommand command) {
        validate(command);
        return client.update(command);
    }

    public Mono<AiSlashCommand> delete(String name) {
        return client.get(AiSlashCommand.class, name).flatMap(client::delete);
    }

    public void validate(AiSlashCommand command) {
        var spec = command == null ? null : command.getSpec();
        if (spec == null) {
            throw new ServerWebInputException("斜杠命令配置不能为空。");
        }
        if (!StringUtils.hasText(spec.getName()) || !NAME_PATTERN.matcher(spec.getName()).matches()) {
            throw new ServerWebInputException("斜杠命令名称必须匹配 " + AiSlashCommand.NAME_PATTERN + "。");
        }
        if (!StringUtils.hasText(spec.getPrompt())) {
            throw new ServerWebInputException("斜杠命令提示词不能为空。");
        }
    }

    static String expand(String prompt, String arguments) {
        if (prompt == null) {
            return "";
        }
        var values = splitArguments(arguments);
        var matcher = POSITIONAL_ARGUMENT.matcher(prompt);
        var expanded = new StringBuilder();
        while (matcher.find()) {
            var index = Integer.parseInt(matcher.group(1)) - 1;
            var replacement = index < values.size() ? values.get(index) : "";
            matcher.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(expanded);
        return expanded.toString().replace(ARGUMENTS_PLACEHOLDER, arguments == null ? "" : arguments);
    }

    private static ParsedCommand parse(String text) {
        if (!StringUtils.hasText(text)) {
            throw new ServerWebInputException("text 不能为空。");
        }
        var trimmed = text.strip();
        if (!trimmed.startsWith("/")) {
            throw commandNotFound(trimmed);
        }
        var body = trimmed.substring(1);
        var separator = firstWhitespace(body);
        var name = separator < 0 ? body : body.substring(0, separator);
        if (!StringUtils.hasText(name)) {
            throw commandNotFound(trimmed);
        }
        var arguments = separator < 0 ? "" : body.substring(separator).strip();
        return new ParsedCommand(name, arguments);
    }

    private static int firstWhitespace(String value) {
        for (var index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static List<String> splitArguments(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return List.of();
        }
        var values = new ArrayList<String>();
        for (var value : arguments.strip().split("\\s+")) {
            if (values.size() == MAX_POSITIONAL_ARGUMENTS) {
                break;
            }
            values.add(value);
        }
        return values;
    }

    private static ApiException commandNotFound(String name) {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_COMMAND_NOT_FOUND, "未知斜杠命令：" + name);
    }

    private static void requireAgentName(String agentName) {
        if (!StringUtils.hasText(agentName)) {
            throw new ServerWebInputException("agent 不能为空。");
        }
    }

    private record ParsedCommand(String name, String arguments) {}

    public record ResolvedSlashCommand(String name, String expandedPrompt) {}
}