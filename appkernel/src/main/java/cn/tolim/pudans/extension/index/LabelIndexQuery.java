package cn.tolim.pudans.extension.index;

import java.util.Collection;
import java.util.Set;


public interface LabelIndexQuery {


    // 查询包含指定标签键的主键。
    Set<String> exists(String labelKey);


    // 查询标签键值相等的主键。
    Set<String> equal(String labelKey, String labelValue);


    // 查询标签键值不等的主键。
    Set<String> notEqual(String labelKey, String labelValue);


    // 查询标签值在集合内的主键。
    Set<String> in(String labelKey, Collection<String> labelValues);


    // 查询标签值不在集合内的主键。
    Set<String> notIn(String labelKey, Collection<String> labelValues);
}
