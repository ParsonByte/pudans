package cn.tolim.pudans.extension.api;

import cn.tolim.pudans.extension.api.index.IndexedQueryEngine;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.data.domain.Sort;


public interface ExtensionClient {


    <E extends Extension> List<E> list(Class<E> type, Predicate<E> predicate, Comparator<E> comparator);


    <E extends Extension> List<E> listAll(Class<E> type, ListOptions options, Sort sort);

    <E extends Extension> List<String> listAllNames(Class<E> type, ListOptions options, Sort sort);

    <E extends Extension> List<String> listTopNames(Class<E> type, ListOptions options, Sort sort, int topN);

    <E extends Extension> ListResult<E> listBy(Class<E> type, ListOptions options, PageRequest page);

    <E extends Extension> ListResult<String> listNamesBy(Class<E> type, ListOptions options, PageRequest page);

    <E extends Extension> long countBy(Class<E> type, ListOptions options);


    <E extends Extension> Optional<E> fetch(Class<E> type, String name);

    Optional<Unstructured> fetch(GroupVersionKind gvk, String name);


    <E extends Extension> void create(E extension);


    <E extends Extension> void update(E extension);


    <E extends Extension> void delete(E extension);

    IndexedQueryEngine indexedQueryEngine();

    void watch(Watcher watcher);
}
