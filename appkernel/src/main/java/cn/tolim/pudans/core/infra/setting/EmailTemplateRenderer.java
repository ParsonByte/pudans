package cn.tolim.pudans.core.infra.setting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class EmailTemplateRenderer {

    private static final String OPEN = "{{";

    private static final String CLOSE = "}}";

    public String render(String template, Map<String, ?> variables) {
        if (template == null) {
            return "";
        }
        var index = new int[] {0};
        var nodes = parse(template, index, null).nodes();
        var contexts = new ArrayDeque<Map<String, ?>>();
        contexts.push(variables == null ? Map.of() : variables);
        var output = new StringBuilder();
        renderNodes(nodes, contexts, output);
        return output.toString();
    }

    private ParseResult parse(String template, int[] index, String stopTag) {
        var nodes = new ArrayList<Node>();
        while (index[0] < template.length()) {
            var open = template.indexOf(OPEN, index[0]);
            if (open < 0) {
                nodes.add(new TextNode(template.substring(index[0])));
                index[0] = template.length();
                if (stopTag != null) {
                    throw new TemplateRenderException("块 " + stopTag + " 缺少结束标签");
                }
                return new ParseResult(nodes, null);
            }
            if (open > index[0]) {
                nodes.add(new TextNode(template.substring(index[0], open)));
            }
            var close = template.indexOf(CLOSE, open + OPEN.length());
            if (close < 0) {
                throw new TemplateRenderException("位置 " + open + " 处的模板标签未闭合");
            }
            var tag = template.substring(open + OPEN.length(), close).trim();
            index[0] = close + CLOSE.length();
            if (tag.equals("else") || tag.startsWith("/")) {
                if (stopTag == null) {
                    throw new TemplateRenderException("意外的模板标签：" + tag);
                }
                if (tag.equals("else") || tag.equals("/" + stopTag)) {
                    return new ParseResult(nodes, tag);
                }
                throw new TemplateRenderException("意外的模板标签：" + tag);
            }
            if (tag.startsWith("#if ")) {
                var condition = tag.substring(4).trim();
                if (condition.isEmpty()) {
                    throw new TemplateRenderException("if 块必须提供条件");
                }
                var thenResult = parse(template, index, "if");
                var elseNodes = new ArrayList<Node>();
                if ("else".equals(thenResult.stopTag())) {
                    var elseResult = parse(template, index, "if");
                    if (!"/if".equals(elseResult.stopTag())) {
                        throw new TemplateRenderException("if 块缺少结束标签");
                    }
                    elseNodes.addAll(elseResult.nodes());
                } else if (!"/if".equals(thenResult.stopTag())) {
                    throw new TemplateRenderException("if 块缺少结束标签");
                }
                nodes.add(new IfNode(condition, thenResult.nodes(), elseNodes));
                continue;
            }
            if (tag.startsWith("#each ")) {
                var key = tag.substring(6).trim();
                if (key.isEmpty()) {
                    throw new TemplateRenderException("each 块必须提供集合名称");
                }
                var bodyResult = parse(template, index, "each");
                if (!"/each".equals(bodyResult.stopTag())) {
                    throw new TemplateRenderException("each 块缺少结束标签");
                }
                nodes.add(new EachNode(key, bodyResult.nodes()));
                continue;
            }
            if (tag.isEmpty()) {
                throw new TemplateRenderException("不允许使用空的模板标签");
            }
            nodes.add(new VariableNode(tag));
        }
        if (stopTag != null) {
            throw new TemplateRenderException("块 " + stopTag + " 缺少结束标签");
        }
        return new ParseResult(nodes, null);
    }

    private void renderNodes(List<Node> nodes, Deque<Map<String, ?>> contexts, StringBuilder output) {
        for (var node : nodes) {
            switch (node) {
                case TextNode text -> output.append(text.value());
                case VariableNode variable -> output.append(renderVariable(variable.expression(), contexts));
                case IfNode condition -> {
                    if (truthy(lookup(condition.condition(), contexts))) {
                        renderNodes(condition.thenNodes(), contexts, output);
                    } else {
                        renderNodes(condition.elseNodes(), contexts, output);
                    }
                }
                case EachNode each -> renderEach(each, contexts, output);
            }
        }
    }

    private void renderEach(EachNode each, Deque<Map<String, ?>> contexts, StringBuilder output) {
        var value = lookup(each.collection(), contexts);
        if (!(value instanceof Collection<?> collection)) {
            throw new TemplateRenderException("模板值 [" + each.collection() + "] 不是集合");
        }
        for (var item : collection) {
            if (item instanceof Map<?, ?> map) {
                contexts.push((Map<String, ?>) map);
            } else {
                contexts.push(Map.of("this", item == null ? "" : item));
            }
            try {
                renderNodes(each.body(), contexts, output);
            } finally {
                contexts.pop();
            }
        }
    }

    private String renderVariable(String expression, Deque<Map<String, ?>> contexts) {
        var parts = expression.split("\\|");
        var key = parts[0].trim();
        var value = lookup(key, contexts);
        var rendered = value == null ? "" : String.valueOf(value);
        for (int i = 1; i < parts.length; i++) {
            rendered = applyFilter(parts[i].trim(), rendered);
        }
        return rendered;
    }

    private static String applyFilter(String filter, String value) {
        return switch (filter.toLowerCase(Locale.ROOT)) {
            case "escape" -> escape(value);
            case "upper" -> value.toUpperCase(Locale.ROOT);
            case "lower" -> value.toLowerCase(Locale.ROOT);
            case "trim" -> value.trim();
            default -> throw new TemplateRenderException("未知的模板过滤器：" + filter);
        };
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private Object lookup(String key, Deque<Map<String, ?>> contexts) {
        var value = lookupOptional(key, contexts);
        if (value == null && contexts.stream().noneMatch(context -> context.containsKey(key))) {
            throw new TemplateRenderException("缺少模板变量：" + key);
        }
        return value;
    }

    private static Object lookupOptional(String key, Deque<Map<String, ?>> contexts) {
        for (var context : contexts) {
            if (context.containsKey(key)) {
                return context.get(key);
            }
        }
        return null;
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return !Objects.equals(value, Boolean.FALSE);
    }

    private record ParseResult(List<Node> nodes, String stopTag) {}

    private sealed interface Node permits TextNode, VariableNode, IfNode, EachNode {}

    private record TextNode(String value) implements Node {}

    private record VariableNode(String expression) implements Node {}

    private record IfNode(String condition, List<Node> thenNodes, List<Node> elseNodes) implements Node {}

    private record EachNode(String collection, List<Node> body) implements Node {}
}
