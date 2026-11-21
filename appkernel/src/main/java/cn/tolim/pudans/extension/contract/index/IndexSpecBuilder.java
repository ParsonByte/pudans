package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;


public interface IndexSpecBuilder<E extends Extension, K extends Comparable<K>, B extends IndexSpecBuilder<E, K, B>> {


    // 设置索引是否唯一。
    B unique(boolean unique);


    // 设置索引是否允许空值。
    B nullable(boolean nullable);


    // 校验配置并生成索引描述。
    ValueIndexSpec<E, K> build();
}
