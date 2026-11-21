package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;
import org.springframework.util.Assert;


public abstract class AbstractValueIndexSpecBuilder<
        E extends Extension, K extends Comparable<K>, B extends IndexSpecBuilder<E, K, B>>
        implements IndexSpecBuilder<E, K, B> {

    protected final String name;

    protected final Class<K> keyType;

    protected boolean unique = false;

    protected boolean nullable = true;

    // 校验索引名称与键类型后保存配置。
    protected AbstractValueIndexSpecBuilder(String name, Class<K> keyType) {
        Assert.hasText(name, "索引名称不能为空");
        Assert.notNull(keyType, "键类型不能为 null");
        this.name = name;
        this.keyType = keyType;
    }

    // 设置唯一性约束，返回构建器本身。
    public B unique(boolean unique) {
        this.unique = unique;
        return (B) this;
    }

    // 设置是否允许空值，返回构建器本身。
    public B nullable(boolean nullable) {
        this.nullable = nullable;
        return (B) this;
    }
}
