package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.GroupVersionKind;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ListResult;
import cn.tolim.pudans.extension.contract.PageRequest;
import java.util.List;
import org.springframework.data.domain.Sort;


public interface IndexedQueryEngine {


    // 分页查询满足条件的扩展名称。
    ListResult<String> retrieve(GroupVersionKind type, ListOptions options, PageRequest page);


    // 按排序返回全部匹配的扩展名称。
    List<String> retrieveAll(GroupVersionKind type, ListOptions options, Sort sort);
}
