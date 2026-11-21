package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;
import java.util.Set;
import java.util.function.Function;
import org.springframework.util.Assert;


public class MultiValueBuilder<E extends Extension, K extends Comparable<K>>
        extends AbstractValueIndexSpecBuilder<E, K, MultiValueIndexSpecBuilder<E, K>>
        implements MultiValueIndexSpecBuilder<E, K> {

    private Function<E, Set<K>> indexFunc;

    // 校验索引名称与键类型。
    public MultiValueBuilder(String name, Class<K> keyType) {
        super(name, keyType);
    }

    // 设置提取多值键的函数。
    @Override
    public MultiValueIndexSpecBuilder<E, K> indexFunc(Function<E, Set<K>> indexFunc) {
        this.indexFunc = indexFunc;
        return this;
    }

    // 校验配置并生成多值索引描述。
    @Override
    public ValueIndexSpec<E, K> build() {
        Assert.hasText(name, "索引名称不能为空");
        Assert.notNull(keyType, "键类型不能为 null");
        Assert.notNull(indexFunc, "索引函数不能为 null");

        return new MultiValueIndexSpec<>() {
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

            // 调用索引函数提取扩展的键集合。
            @Override
            public Set<K> getValues(E extension) {
                return indexFunc.apply(extension);
            }
        };
    }
}
