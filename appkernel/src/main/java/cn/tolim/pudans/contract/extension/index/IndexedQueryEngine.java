package cn.tolim.pudans.contract.extension.index;

import cn.tolim.pudans.contract.extension.GroupVersionKind;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ListResult;
import cn.tolim.pudans.contract.extension.PageRequest;
import java.util.List;
import org.springframework.data.domain.Sort;


public interface IndexedQueryEngine {


    ListResult<String> retrieve(GroupVersionKind type, ListOptions options, PageRequest page);


    List<String> retrieveAll(GroupVersionKind type, ListOptions options, Sort sort);
}
