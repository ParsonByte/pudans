package cn.tolim.pudans.contract.extension.index;

import cn.tolim.pudans.contract.extension.Extension;


public interface IndexSpecBuilder<E extends Extension, K extends Comparable<K>, B extends IndexSpecBuilder<E, K, B>> {


    B unique(boolean unique);


    B nullable(boolean nullable);


    ValueIndexSpec<E, K> build();
}
