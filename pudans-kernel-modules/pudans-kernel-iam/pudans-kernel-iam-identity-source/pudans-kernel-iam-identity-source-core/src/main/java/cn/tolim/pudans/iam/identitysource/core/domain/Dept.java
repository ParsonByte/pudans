package cn.tolim.pudans.iam.identitysource.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.io.Serializable;
import java.util.List;


@Data
@NoArgsConstructor
public class Dept implements Serializable {

    // Department ID
    private String deptId;

    // Parent department ID
    private String parentId;

    // Department name
    private String name;

    // Department sort
    private Long order;

    // Child node
    private List<Dept> children;

    public boolean isLeaf() {
        return CollectionUtils.isEmpty(children);
    }
}
