package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.extension.api.Extension;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ListResult;
import cn.tolim.pudans.extension.api.PageRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;


public interface IndexEngine {


    <E extends Extension> void insert(Iterable<E> extensions);


    <E extends Extension> void update(Iterable<E> extension);


    <E extends Extension> void delete(Iterable<E> extensions);


    <E extends Extension> ListResult<String> retrieve(Class<E> type, @Nullable ListOptions options, PageRequest page);


    <E extends Extension> Iterable<String> retrieveAll(
            Class<E> type, @Nullable ListOptions options, @Nullable Sort sort);


    <E extends Extension> Iterable<String> retrieveTopN(
            Class<E> type, @Nullable ListOptions options, @Nullable Sort sort, int topN);


    <E extends Extension> long count(Class<E> type, ListOptions options);


    IndicesManager getIndicesManager();
}
