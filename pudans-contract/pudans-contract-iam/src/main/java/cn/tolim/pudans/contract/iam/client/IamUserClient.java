package cn.tolim.pudans.contract.iam.client;

import cn.tolim.pudans.contract.iam.IamContractConstants;
import cn.tolim.pudans.contract.iam.IamContractPaths;
import cn.tolim.pudans.contract.iam.dto.IamApiResponse;
import cn.tolim.pudans.contract.iam.dto.authz.RoleSummary;
import cn.tolim.pudans.contract.iam.dto.ticket.UserInfo;
import cn.tolim.pudans.contract.iam.dto.user.UserBatchRequest;
import cn.tolim.pudans.contract.iam.dto.user.UserBatchResponse;
import cn.tolim.pudans.contract.iam.dto.user.UserPageResponse;
import cn.tolim.pudans.contract.iam.dto.user.UserQueryRequest;
import cn.tolim.pudans.contract.iam.dto.user.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

// IAM user contract
@FeignClient(name = IamContractConstants.SERVICE_NAME, contextId = "iamUserClient",
        path = IamContractPaths.BASE)
public interface IamUserClient {

    // Get a user by ID
    // Parameter userId: String
    // Returns: IamApiResponse<UserSummary>
    @GetMapping(IamContractPaths.USER_DETAIL_SUFFIX)
    IamApiResponse<UserSummary> getUser(@PathVariable("userId") String userId);

    // Get a user by username
    // Parameter username: String
    // Returns: IamApiResponse<UserSummary>
    @GetMapping(IamContractPaths.USER_BY_USERNAME_SUFFIX)
    IamApiResponse<UserSummary> getUserByUsername(@RequestParam("username") String username);

    // Query users
    // Parameter query: UserQueryRequest
    // Returns: IamApiResponse<UserPageResponse>
    @GetMapping(IamContractPaths.USER_SUFFIX)
    IamApiResponse<UserPageResponse> getUsers(@SpringQueryMap UserQueryRequest query);

    // Get users by IDs or usernames
    // Parameter request: UserBatchRequest
    // Returns: IamApiResponse<UserBatchResponse>
    @PostMapping(IamContractPaths.USER_BATCH_SUFFIX)
    IamApiResponse<UserBatchResponse> getUsersBatch(@RequestBody UserBatchRequest request);

    // Get roles granted to a user
    // Parameter userId: String
    // Returns: IamApiResponse<List<RoleSummary>>
    @GetMapping(IamContractPaths.USER_ROLES_SUFFIX)
    IamApiResponse<List<RoleSummary>> getUserRoles(@PathVariable("userId") String userId);

    // Get the current user resolved from the global login session
    // Parameter sid: String
    // Returns: IamApiResponse<UserInfo>
    @GetMapping(IamContractPaths.ME_SUFFIX)
    IamApiResponse<UserInfo> getCurrentUser(
            @RequestHeader(value = IamContractConstants.HEADER_SESSION_ID,
                    required = false) String sid);
}