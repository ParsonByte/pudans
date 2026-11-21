package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.GroupVersionKind;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ListResult;
import cn.tolim.pudans.extension.contract.PageRequest;
import java.util.List;
import org.springframework.data.domain.Sort;


public interface IndexedQueryEngine {


    ListResult<String> retrieve(GroupVersionKind type, ListOptions options, PageRequest page);


    List<String> retrieveAll(GroupVersionKind type, ListOptions options, Sort sort);
}
