package cn.tolim.pudans.iam.identitysource.core.client;

import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;

import java.util.ArrayList;
import java.util.List;


public interface IdentitySourceClient {

    // Returns users in the department and all of its descendants.
    default List<User> getUserList(Dept dept) {
        List<User> list = new ArrayList<>(getUserList(dept.getDeptId()));
        List<Dept> children = dept.getChildren();
        if (!dept.isLeaf()) {
            children.forEach(item -> list.addAll(getUserList(item)));
        }
        return list;
    }

    // Returns department data for the given department ID.
    Dept getDept(String id);

    // Returns the department and all of its descendants.
    List<Dept> getDeptList(String id);

    // Returns all departments.
    List<Dept> getDeptList();

    // Returns user information for the given user ID.
    User getUser(String id);

    // Returns users in the given department.
    List<User> getUserList(String deptId);

    // Returns all users.
    List<User> getUserList();
}
