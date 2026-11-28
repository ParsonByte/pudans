package cn.tolim.pudans.iam.communal.entity.account.po;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;


@Data
public class UserIdpBindPO implements Serializable {
    // unionId
    public String unionId;
    // id
    private String id;
    // User ID
    private String userId;
    // User name
    private String userName;
    // Nickname
    private String nickName;
    // Personal email
    private String email;
    // Country code for the phone number
    private String stateCode;
    // Phone number
    private String mobile;
    // Avatar URL
    private String avatarUrl;
    // OpenId
    private String openId;
    // Identity provider ID
    private String idpId;

    // Identity provider name
    private String idpName;

    // Identity provider type
    private String idpType;

    // Binding time
    private LocalDateTime bindTime;

    public UserIdpBindPO(String id, String userId, String userName, String nickName, String email,
                         String mobile, String stateCode, String avatarUrl, String openId,
                         String unionId, String idpId, String idpName, String idpType,
                         LocalDateTime bindTime) {
        this.id = String.valueOf(id);
        this.userId = String.valueOf(userId);
        this.userName = userName;
        this.nickName = nickName;
        this.email = email;
        this.mobile = mobile;
        this.stateCode = stateCode;
        this.avatarUrl = avatarUrl;
        this.openId = openId;
        this.unionId = unionId;
        this.idpId = String.valueOf(idpId);
        this.idpName = idpName;
        this.idpType = idpType;
        this.bindTime = bindTime;
    }
}
