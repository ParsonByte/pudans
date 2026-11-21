package cn.tolim.pudans.extension.index;

import java.util.Collection;
import java.util.Set;


public interface ValueIndexQuery<K extends Comparable<K>> {


    // 返回索引键类型。
    Class<K> getKeyType();


    // 查询键相等的主键集合。
    Set<String> equal(K key);


    // 查询键不等的主键集合。
    Set<String> notEqual(K key);


    // 返回全部已索引主键。
    Set<String> all();


    // 查询区间内的主键集合。
    Set<String> between(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive);


    // 查询区间外的主键集合。
    Set<String> notBetween(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive);


    // 查询键在集合内的主键。
    Set<String> in(Collection<K> keys);


    // 查询键不在集合内的主键。
    Set<String> notIn(Collection<K> keys);


    // 查询小于边界的键。
    Set<String> lessThan(K key, boolean inclusive);


    // 查询大于边界的键。
    Set<String> greaterThan(K key, boolean inclusive);


    // 查询索引键为空的主键。
    Set<String> isNull();


    // 查询索引键非空的主键。
    Set<String> isNotNull();


    // 查询包含关键字的字符串键。
    Set<String> stringContains(String keyword);


    // 查询不包含关键字的字符串键。
    Set<String> stringNotContains(String keyword);


    // 查询指定前缀的字符串键。
    Set<String> stringStartsWith(String prefix);


    // 查询非指定前缀的字符串键。
    Set<String> stringNotStartsWith(String prefix);


    // 查询指定后缀的字符串键。
    Set<String> stringEndsWith(String suffix);


    // 查询非指定后缀的字符串键。
    Set<String> stringNotEndsWith(String suffix);
}
