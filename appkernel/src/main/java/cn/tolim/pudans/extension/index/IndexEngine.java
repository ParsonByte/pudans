package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ListResult;
import cn.tolim.pudans.extension.contract.PageRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;


public interface IndexEngine {


    // 批量写入扩展索引。
    <E extends Extension> void insert(Iterable<E> extensions);


    // 批量更新扩展索引。
    <E extends Extension> void update(Iterable<E> extension);


    // 批量删除扩展索引。
    <E extends Extension> void delete(Iterable<E> extensions);


    // 按条件分页返回主键。
    <E extends Extension> ListResult<String> retrieve(Class<E> type, @Nullable ListOptions options, PageRequest page);


    // 按条件与排序返回全部主键。
    <E extends Extension> Iterable<String> retrieveAll(
            Class<E> type, @Nullable ListOptions options, @Nullable Sort sort);


    // 按条件与排序返回前 N 个主键。
    <E extends Extension> Iterable<String> retrieveTopN(
            Class<E> type, @Nullable ListOptions options, @Nullable Sort sort, int topN);


    // 统计满足条件的扩展数量。
    <E extends Extension> long count(Class<E> type, ListOptions options);


    // 返回底层索引管理器。
    IndicesManager getIndicesManager();
}
