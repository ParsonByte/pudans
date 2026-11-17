package cn.tolim.pudans.contract.extension.index;

import cn.tolim.pudans.contract.extension.Extension;
import org.springframework.util.Assert;


public abstract class AbstractValueIndexSpecBuilder<
        E extends Extension, K extends Comparable<K>, B extends IndexSpecBuilder<E, K, B>>
        implements IndexSpecBuilder<E, K, B> {

    protected final String name;

    protected final Class<K> keyType;

    protected boolean unique = false;

    protected boolean nullable = true;

    protected AbstractValueIndexSpecBuilder(String name, Class<K> keyType) {
        Assert.hasText(name, "索引名称不能为空");
        Assert.notNull(keyType, "键类型不能为 null");
        this.name = name;
        this.keyType = keyType;
    }

    public B unique(boolean unique) {
        this.unique = unique;
        return (B) this;
    }

    public B nullable(boolean nullable) {
        this.nullable = nullable;
        return (B) this;
    }
}
