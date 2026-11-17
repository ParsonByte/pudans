package cn.tolim.pudans.contract.extension.index;

import cn.tolim.pudans.contract.extension.Extension;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;


public class SingleValueBuilder<E extends Extension, K extends Comparable<K>>
        extends AbstractValueIndexSpecBuilder<E, K, SingleValueIndexSpecBuilder<E, K>>
        implements SingleValueIndexSpecBuilder<E, K> {

    private Function<E, @Nullable K> indexFunc;

    public SingleValueBuilder(String name, Class<K> keyType) {
        super(name, keyType);
    }

    @Override
    public SingleValueBuilder<E, K> indexFunc(Function<E, @Nullable K> indexFunc) {
        this.indexFunc = indexFunc;
        return this;
    }

    @Override
    public ValueIndexSpec<E, K> build() {
        Assert.hasText(name, "索引名称不能为空");
        Assert.notNull(keyType, "键类型不能为 null");
        Assert.notNull(indexFunc, "索引函数不能为 null");

        return new SingleValueIndexSpec<>() {
            @Override
            @Nullable
            public K getValue(E extension) {
                return indexFunc.apply(extension);
            }

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
        };
    }
}
