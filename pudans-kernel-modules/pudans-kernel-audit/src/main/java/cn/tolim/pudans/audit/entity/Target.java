package cn.tolim.pudans.audit.entity;

import cn.tolim.pudans.audit.enums.TargetType;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
public class Target implements Serializable {

    @Serial
    private static final long serialVersionUID = -1144169992714000310L;

    private String id;

    private String name;

    private TargetType type;

    @JsonIgnore
    public String getTypeName() {
        return type == null ? null : type.getDesc();
    }
}