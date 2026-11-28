package cn.tolim.pudans.audit.entity;

import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Actor implements Serializable {

    @Serial
    private static final long serialVersionUID = -1144169992714000310L;

    private String id;

    private UserType type;

    private String authType;
}