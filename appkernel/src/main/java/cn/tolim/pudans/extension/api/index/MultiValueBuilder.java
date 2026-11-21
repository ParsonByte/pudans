package cn.tolim.pudans.extension.api.index;

import cn.tolim.pudans.extension.api.Extension;
import java.util.Set;
import java.util.function.Function;
import org.springframework.util.Assert;


public class MultiValueBuilder<E extends Extension, K extends Comparable<K>>
        extends AbstractValueIndexSpecBuilder<E, K, MultiValueIndexSpecBuilder<E, K>>
        implements MultiValueIndexSpecBuilder<E, K> {

    private Function<E, Set<K>> indexFunc;

    public MultiValueBuilder(String name, Class<K> keyType) {
        super(name, keyType);
    }

    @Override
    public MultiValueIndexSpecBuilder<E, K> indexFunc(Function<E, Set<K>> indexFunc) {
        this.indexFunc = indexFunc;
        return this;
    }

    @Override
    public ValueIndexSpec<E, K> build() {
        Assert.hasText(name, "索引名称不能为空");
        Assert.notNull(keyType, "键类型不能为 null");
        Assert.notNull(indexFunc, "索引函数不能为 null");

        return new MultiValueIndexSpec<>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public boolean isUnique() {
                return unique;
            }

            @Override
            public boolean isNullable() {
                return nullable;
            }

            @Override
            public Class<K> getKeyType() {
                return keyType;
            }

            @Override
            public Set<K> getValues(E extension) {
                return indexFunc.apply(extension);
            }
        };
    }
}
