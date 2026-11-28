package cn.tolim.pudans.iam.communal.entity.account.po;

import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;


@EqualsAndHashCode(callSuper = true)
@Data
public class UserPO extends UserEntity {

    @Serial
    private static final long serialVersionUID = 2330202241971348786L;

    // Organization display path
    private String orgDisplayPath;

    public UserPO(UserEntity user, String orgDisplayPath) {
        super.setId(user.getId());
        super.setUsername(user.getUsername());
        super.setPassword(user.getPassword());
        super.setEmail(user.getEmail());
        super.setPhone(user.getPhone());
        super.setPhoneAreaCode(user.getPhoneAreaCode());
        super.setFullName(user.getFullName());
        super.setNickName(user.getNickName());
        super.setAvatar(user.getAvatar());
        super.setStatus(user.getStatus());
        super.setDataOrigin(user.getDataOrigin());
        super.setEmailVerified(user.getEmailVerified());
        super.setPhoneVerified(user.getPhoneVerified());
        super.setAuthTotal(user.getAuthTotal());
        super.setLastAuthIp(user.getLastAuthIp());
        super.setLastAuthTime(user.getLastAuthTime());
        super.setExpand(user.getExpand());
        super.setExternalId(user.getExternalId());
        super.setExpireDate(user.getExpireDate());
        super.setCreateBy(user.getCreateBy());
        super.setCreateTime(user.getCreateTime());
        super.setUpdateBy(user.getUpdateBy());
        super.setUpdateTime(user.getUpdateTime());
        super.setRemark(user.getRemark());
        setOrgDisplayPath(orgDisplayPath);
    }
}
