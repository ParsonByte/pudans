package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;


public class SingleValueBuilder<E extends Extension, K extends Comparable<K>>
        extends AbstractValueIndexSpecBuilder<E, K, SingleValueIndexSpecBuilder<E, K>>
        implements SingleValueIndexSpecBuilder<E, K> {

    private Function<E, @Nullable K> indexFunc;

    // 校验索引名称与键类型。
    public SingleValueBuilder(String name, Class<K> keyType) {
        super(name, keyType);
    }

    // 设置提取单值键的函数。
    @Override
    public SingleValueBuilder<E, K> indexFunc(Function<E, @Nullable K> indexFunc) {
        this.indexFunc = indexFunc;
        return this;
    }

    // 校验配置并生成单值索引描述。
    @Override
    public ValueIndexSpec<E, K> build() {
        Assert.hasText(name, "索引名称不能为空");
        Assert.notNull(keyType, "键类型不能为 null");
        Assert.notNull(indexFunc, "索引函数不能为 null");

        return new SingleValueIndexSpec<>() {
            // 调用索引函数提取单个键。
            @Override
            @Nullable
            public K getValue(E extension) {
                return indexFunc.apply(extension);
            }

            // 返回构建时配置的索引名称。
            @Override
            public String getName() {
                return name;
            }

            // 返回构建时配置的唯一性。
            @Override
            public boolean isUnique() {
                return unique;
            }

            // 返回构建时配置的空值策略。
            @Override
            public boolean isNullable() {
                return nullable;
            }

            // 返回构建时配置的键类型。
            @Override
            public Class<K> getKeyType() {
                return keyType;
            }
        };
    }
}
