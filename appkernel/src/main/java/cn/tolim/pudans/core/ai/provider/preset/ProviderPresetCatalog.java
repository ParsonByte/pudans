package cn.tolim.pudans.core.ai.provider.preset;

import cn.tolim.pudans.contract.ai.AiProvider;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

// 预置目录只包含平台约定的三家国外供应商与三家国内供应商。
@Component
public class ProviderPresetCatalog {

    private static final List<ProviderPreset> PRESETS = List.of(
            preset(
                    "openai",
                    "OpenAI",
                    "OpenAI 官方 API",
                    "international",
                    "vendor",
                    AiProvider.Vendor.OPENAI,
                    List.of(region(
                            "global",
                            "国际",
                            "https://platform.openai.com",
                            "https://platform.openai.com/api-keys",
                            "https://api.openai.com")),
                    "https://platform.openai.com",
                    "https://platform.openai.com/api-keys"),
            preset(
                    "anthropic",
                    "Anthropic",
                    "Claude 官方 API",
                    "international",
                    "vendor",
                    AiProvider.Vendor.ANTHROPIC,
                    List.of(region(
                            "global",
                            "国际",
                            "https://console.anthropic.com",
                            "https://console.anthropic.com/settings/keys",
                            "https://api.anthropic.com")),
                    "https://console.anthropic.com",
                    "https://console.anthropic.com/settings/keys"),
            preset(
                    "gemini",
                    "Google Gemini",
                    "Gemini Developer API",
                    "international",
                    "vendor",
                    AiProvider.Vendor.GEMINI,
                    List.of(region(
                            "global",
                            "国际",
                            "https://aistudio.google.com",
                            "https://aistudio.google.com/apikey",
                            "https://generativelanguage.googleapis.com")),
                    "https://aistudio.google.com",
                    "https://aistudio.google.com/apikey"),
            preset(
                    "deepseek",
                    "DeepSeek",
                    "DeepSeek 官方 API",
                    "domestic",
                    "vendor",
                    AiProvider.Vendor.DEEPSEEK,
                    List.of(region(
                            "global",
                            "国际",
                            "https://platform.deepseek.com",
                            "https://platform.deepseek.com/api_keys",
                            "https://api.deepseek.com")),
                    "https://platform.deepseek.com",
                    "https://platform.deepseek.com/api_keys"),
            preset(
                    "minimax",
                    "MiniMax",
                    "MiniMax 官方 API",
                    "domestic",
                    "vendor",
                    AiProvider.Vendor.MINIMAX,
                    List.of(
                            region(
                                    "global",
                                    "国际",
                                    "https://platform.minimax.io",
                                    "https://platform.minimax.io/user-center/basic-information/interface-key",
                                    "https://api.minimax.io"),
                            region(
                                    "cn",
                                    "中国",
                                    "https://platform.minimaxi.com",
                                    "https://platform.minimaxi.com/user-center/basic-information/interface-key",
                                    "https://api.minimaxi.com")),
                    "https://platform.minimax.io",
                    "https://platform.minimax.io/user-center/basic-information/interface-key"),
            preset(
                    "qianwen",
                    "千问",
                    "阿里云百炼 · DashScope",
                    "domestic",
                    "vendor",
                    AiProvider.Vendor.QIANWEN,
                    List.of(
                            region(
                                    "global",
                                    "国际",
                                    "https://modelstudio.console.alibabacloud.com",
                                    "https://modelstudio.console.alibabacloud.com/?tab=playground#/api-key",
                                    "https://dashscope-intl.aliyuncs.com"),
                            region(
                                    "cn",
                                    "中国",
                                    "https://bailian.console.aliyun.com",
                                    "https://bailian.console.aliyun.com/?tab=model#/api-key",
                                    "https://dashscope.aliyuncs.com")),
                    "https://bailian.console.aliyun.com",
                    "https://bailian.console.aliyun.com/?tab=model#/api-key"));

    public Catalog catalog() {
        return new Catalog(List.copyOf(PRESETS), List.of());
    }

    private static ProviderPreset preset(
            String id,
            String displayName,
            String description,
            String group,
            String kind,
            AiProvider.Vendor vendor,
            List<Region> regions,
            String website,
            String keysUrl) {
        return new ProviderPreset(
                id,
                displayName,
                description,
                group,
                kind,
                true,
                null,
                vendor,
                regions,
                website,
                keysUrl,
                List.of(),
                null);
    }

    private static Region region(
            String id, String label, String website, String keysUrl, String baseUrl) {
        return new Region(id, label, website, keysUrl, baseUrl, List.of());
    }

    public record Catalog(List<ProviderPreset> supported, List<ProviderPreset> unsupported) {}

    public record ProviderPreset(
            String id,
            String displayName,
            String description,
            String group,
            String kind,
            boolean supported,
            @Nullable String unsupportedReason,
            AiProvider.Vendor vendor,
            List<Region> regions,
            String website,
            String keysUrl,
            List<String> models,
            @Nullable String note) {}

    public record Region(
            String id,
            String label,
            String website,
            String keysUrl,
            String baseUrl,
            List<String> models) {}
}