package cn.tolim.pudans.iam.authentication.core;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.google.common.collect.Lists;

import java.util.List;


public enum IdentityProviderCategory implements BaseEnum {
    // Social
    social("social", "社交", Lists.newArrayList(
            IdentityProviderType.QQ_OAUTH,
            IdentityProviderType.WECHAT,
            IdentityProviderType.GITEE_OAUTH,
            IdentityProviderType.GITHUB_OAUTH,
            IdentityProviderType.ALIPAY_OAUTH)),
    // Enterprise
    enterprise("enterprise", "企业", Lists
            .newArrayList(
                    IdentityProviderType.WECHAT_WORK_OAUTH,
                    IdentityProviderType.DINGTALK_OAUTH,
                    IdentityProviderType.FEISHU_OAUTH));

    private final String code;

    private final String desc;

    private final List<IdentityProviderType> providers;

    IdentityProviderCategory(String code, String desc, List<IdentityProviderType> providers) {
        this.code = code;
        this.desc = desc;
        this.providers = providers;
    }

    // Get type
    @EnumConvert
    public static IdentityProviderCategory getType(String code) {
        IdentityProviderCategory[] values = values();
        for (IdentityProviderCategory status : values) {
            if (String.valueOf(status.getCode()).equals(code)) {
                return status;
            }
        }
        throw new NullPointerException("未获取到对应平台");
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    public List<IdentityProviderType> getProviders() {
        return providers;
    }
}
