package cn.tolim.pudans.iam.communal.enums.identitysource;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum IdentitySourceProvider implements BaseEnum {
    // DingTalk identity source
    DINGTALK("dingtalk", "钉钉",
            "钉钉（Ding Talk）是阿里巴巴集团打造的企业级智能移动办公平台，是数字经济时代的企业组织协同办公和应用开发平台。"),

    // Feishu
    FEISHU("feishu", "飞书",
            "飞书是字节跳动于2016年自研的新一代一站式协作平台，是保障字节跳动全球数万人高效协作的办公工具。飞书将即时沟通、日历、云文档、云盘和工作台深度整合，通过开放兼容的平台，让成员在一处即可实现高效的沟通和流畅的协作，全方位提升企业效率。");

    @JsonValue
    private final String code;
    private final String name;
    private final String desc;

    IdentitySourceProvider(String code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

    @EnumConvert
    public static IdentitySourceProvider getType(String code) {
        IdentitySourceProvider[] values = values();
        for (IdentitySourceProvider value : values) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

    @Override
    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
