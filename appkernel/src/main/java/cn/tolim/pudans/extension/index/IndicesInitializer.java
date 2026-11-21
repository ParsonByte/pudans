package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.extension.contract.Scheme;

public interface IndicesInitializer {

    // 为注册的 Scheme 初始化索引。
    void initialize(Scheme scheme);
}
