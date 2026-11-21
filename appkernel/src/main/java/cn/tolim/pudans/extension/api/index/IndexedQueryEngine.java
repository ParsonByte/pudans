package cn.tolim.pudans.extension.api.index;

import cn.tolim.pudans.extension.api.GroupVersionKind;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ListResult;
import cn.tolim.pudans.extension.api.PageRequest;
import java.util.List;
import org.springframework.data.domain.Sort;


public interface IndexedQueryEngine {


    ListResult<String> retrieve(GroupVersionKind type, ListOptions options, PageRequest page);


    List<String> retrieveAll(GroupVersionKind type, ListOptions options, Sort sort);
}
