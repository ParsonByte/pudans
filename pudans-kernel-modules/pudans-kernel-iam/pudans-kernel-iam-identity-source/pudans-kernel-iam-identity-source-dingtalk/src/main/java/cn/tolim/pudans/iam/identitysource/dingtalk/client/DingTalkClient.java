package cn.tolim.pudans.iam.identitysource.dingtalk.client;

import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.domain.Dept;
import cn.tolim.pudans.iam.identitysource.core.domain.User;
import cn.tolim.pudans.iam.identitysource.core.domain.UserDetail;
import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.dingtalk.DingTalkConfig;
import cn.tolim.pudans.iam.identitysource.dingtalk.DingTalkConstants;
import com.alibaba.fastjson2.JSON;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.request.OapiV2DepartmentGetRequest;
import com.dingtalk.api.request.OapiV2DepartmentListsubRequest;
import com.dingtalk.api.request.OapiV2UserGetRequest;
import com.dingtalk.api.request.OapiV2UserListRequest;
import com.dingtalk.api.response.OapiV2DepartmentGetResponse;
import com.dingtalk.api.response.OapiV2DepartmentListsubResponse;
import com.dingtalk.api.response.OapiV2UserGetResponse;
import com.dingtalk.api.response.OapiV2UserListResponse;
import com.taobao.api.ApiException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static cn.tolim.pudans.iam.identitysource.dingtalk.DingTalkConstants.LOGGER_NAME;


@Slf4j(topic = LOGGER_NAME)
public class DingTalkClient extends AbstractDingTalkClient implements IdentitySourceClient {

    public DingTalkClient(DingTalkConfig config) {
        super(config);
    }

    // Returns department data for the given department ID.
    @Override
    public Dept getDept(String id) {
        try {
            com.dingtalk.api.DingTalkClient client = new DefaultDingTalkClient(
                    DingTalkConstants.DEPARTMENT_GET);
            OapiV2DepartmentGetRequest req = new OapiV2DepartmentGetRequest();
            req.setDeptId(Long.valueOf(id));
            req.setLanguage(DingTalkConstants.ZH_CN);
            log.info("获取钉钉部门信息入参: {}", JSON.toJSONString(req));
            OapiV2DepartmentGetResponse execute = client.execute(req, getAccessToken());
            log.info("获取钉钉部门信息返回: {}", JSON.toJSONString(execute));
            // Process data successfully
            if (execute.isSuccess()) {
                OapiV2DepartmentGetResponse.DeptGetResponse result = execute.getResult();
                Dept dept = new Dept();
                dept.setDeptId(String.valueOf(result.getDeptId()));
                dept.setParentId(String.valueOf(result.getParentId()));
                dept.setName(result.getName());
                dept.setOrder(result.getOrder());
                return dept;
            }
            log.error("获取钉钉部门信息失败: {}", JSON.toJSONString(execute));
            throw new ApiCallException(execute.getErrmsg());
        } catch (ApiException e) {
            log.error("获取钉钉部门信息失败: {}", e.getErrMsg(), e);
            throw new ApiCallException(e.getErrMsg());
        }
    }

    // Returns all descendant departments of the given department.
    @Override
    public List<Dept> getDeptList(String id) {
        return getSubDeptList(id);
    }

    // Returns all departments.
    @Override
    public List<Dept> getDeptList() {
        Dept dept = getDept(getRootId());
        List<Dept> deptList = getDeptList(getRootId());
        deptList.add(dept);
        return deptList;
    }

    // Returns the child department tree of the given department.
    private List<Dept> getSubDeptList(String deptId) {
        List<Dept> list = new ArrayList<>();
        try {
            // Get child departments
            com.dingtalk.api.DingTalkClient client = new DefaultDingTalkClient(
                    DingTalkConstants.DEPARTMENT_LIST);
            OapiV2DepartmentListsubRequest req = new OapiV2DepartmentListsubRequest();
            req.setDeptId(Long.valueOf(deptId));
            req.setLanguage(DingTalkConstants.ZH_CN);
            log.info("获取钉钉子部门列表入参: {}", JSON.toJSONString(req));
            OapiV2DepartmentListsubResponse execute = client.execute(req, getAccessToken());
            log.info("获取钉钉子部门列表返回: {}", JSON.toJSONString(execute));
            if (execute.isSuccess()) {
                for (OapiV2DepartmentListsubResponse.DeptBaseResponse response : execute
                        .getResult()) {
                    Dept dept = getDept(String.valueOf(response.getDeptId()));
                    // Recursively query child nodes
                    String childDeptId = String.valueOf(response.getDeptId());
                    dept.setChildren(getSubDeptList(childDeptId));
                    list.add(dept);
                }
                return list;
            }
            log.error("获取钉钉子部门列表失败: {}", JSON.toJSONString(execute));
            throw new ApiCallException(execute.getErrmsg());
        } catch (ApiException e) {
            log.error("获取钉钉部门列表发生异常: {}", e.getErrMsg(), e);
            throw new ApiCallException(e.getErrMsg());
        }
    }

    // Returns user information for the given user ID.
    @Override
    public User getUser(String id) {
        try {
            OapiV2UserGetRequest req = new OapiV2UserGetRequest();
            req.setUserid(id);
            log.info("获取钉钉用户信息入参: {}", JSON.toJSONString(req));
            com.dingtalk.api.DingTalkClient client = new DefaultDingTalkClient(
                    DingTalkConstants.GET_USER);
            OapiV2UserGetResponse execute = client.execute(req, getAccessToken());
            log.info("获取钉钉用户信息返回: {}", JSON.toJSONString(execute));
            if (execute.isSuccess()) {
                OapiV2UserGetResponse.UserGetResponse result = execute.getResult();
                return buildUser(result.getUserid(), result.getActive(), result.getName(),
                        result.getDeptIdList(), result.getAvatar(), result.getEmail(),
                        result.getOrgEmail(), result.getMobile(), result.getStateCode());
            }
            log.error("获取钉钉用户信息失败: {}", JSON.toJSONString(execute));
            throw new ApiCallException(execute.getErrmsg());
        } catch (ApiException e) {
            log.error("获取钉钉用户信息异常: {}", e.getErrMsg(), e);
            throw new ApiCallException(e.getErrMsg());
        }
    }

    // Returns users in the given department.
    @Override
    public List<User> getUserList(String deptId) {
        OapiV2UserListRequest req = new OapiV2UserListRequest();
        req.setDeptId(Long.valueOf(deptId));
        req.setCursor(DingTalkConstants.CURSOR);
        req.setSize(DingTalkConstants.SIZE);
        req.setContainAccessLimit(false);
        req.setLanguage(DingTalkConstants.ZH_CN);
        return getUserList(req);
    }

    // Returns all users.
    @Override
    public List<User> getUserList() {
        // Get all departments
        List<Dept> list = getDeptList();
        List<User> users = new ArrayList<>();
        // Get user information by department
        for (Dept dept : list) {
            users.addAll(getUserList(dept));
        }
        return users;
    }

    // Returns all users by following pagination cursors.
    private List<User> getUserList(OapiV2UserListRequest request) {
        List<User> list = new ArrayList<>();
        try {
            log.info("分页获取用户列表入参：{}", JSON.toJSONString(request));
            com.dingtalk.api.DingTalkClient client = new DefaultDingTalkClient(
                    DingTalkConstants.USER_LIST);
            OapiV2UserListResponse execute = client.execute(request, getAccessToken());
            log.info("分页获取用户列表返回：{}", JSON.toJSONString(execute));
            if (execute.isSuccess()) {
                OapiV2UserListResponse.PageResult result = execute.getResult();
                List<OapiV2UserListResponse.ListUserResponse> userList = result.getList();
                for (OapiV2UserListResponse.ListUserResponse response : userList) {
                    User user = buildUser(response.getUserid(), response.getActive(),
                            response.getName(), response.getDeptIdList(), response.getAvatar(),
                            response.getEmail(), response.getOrgEmail(), response.getMobile(),
                            response.getStateCode());
                    list.add(user);
                }
                // Pagination
                if (Boolean.TRUE.equals(result.getHasMore())) {
                    request.setCursor(result.getNextCursor());
                    list.addAll(getUserList(request));
                }
                return list;
            }
            log.error("获取钉钉用户列表失败：{}", JSON.toJSONString(execute));
            throw new ApiCallException(execute.getErrmsg());
        } catch (ApiException e) {
            log.error("获取钉钉用户列表异常：{}", e.getErrMsg(), e);
            throw new ApiCallException(e.getErrMsg());
        }
    }

    // Builds a user from DingTalk API fields.
    private User buildUser(String userid, Boolean active, String name, List<Long> deptIdList,
                           String avatar, String email, String orgEmail, String mobile,
                           String stateCode) {
        User user = new User();
        user.setUserId(userid);
        user.setActive(active);
        user.setDeptIdList(deptIdList.stream().map(String::valueOf).toList());
        user.setAvatar(avatar);
        user.setEmail(email);
        user.setOrgEmail(orgEmail);
        user.setPhone(mobile);
        user.setPhoneAreaCode(stateCode);
        UserDetail userDetail = new UserDetail();
        userDetail.setName(name);
        userDetail.setNickName(name);
        user.setUserDetail(userDetail);
        return user;
    }

    private String getRootId() {
        return "1";
    }
}
