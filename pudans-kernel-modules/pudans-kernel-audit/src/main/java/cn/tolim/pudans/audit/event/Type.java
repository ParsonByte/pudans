package cn.tolim.pudans.audit.event;

import java.util.List;

import cn.tolim.pudans.iam.infra.security.userdetails.UserType;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Type {

    private String         code;

    private String         name;

    private Resource       resource;

    private List<UserType> userTypes;

    @Override
    public String toString() {
        return String.format("[%s](%s) %s", name, code, resource);
    }

}
