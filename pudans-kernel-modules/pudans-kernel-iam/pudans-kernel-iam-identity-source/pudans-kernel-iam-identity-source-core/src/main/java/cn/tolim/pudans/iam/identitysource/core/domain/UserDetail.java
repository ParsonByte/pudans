package cn.tolim.pudans.iam.identitysource.core.domain;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


@Data
@NoArgsConstructor
public class UserDetail implements Serializable {

    // Full name
    private String name;

    // Nickname
    private String nickName;
}
