package cn.tolim.pudans.iam.communal.entity.app.po;

import cn.tolim.pudans.iam.communal.entity.app.AppGroupEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppGroupType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;


@Data
@EqualsAndHashCode(callSuper = true)
public class AppGroupPO extends AppGroupEntity {

    // Application count
    private String appCount;

    public AppGroupPO(String id, String name, String code, AppGroupType type,
                      LocalDateTime createTime, String remark, Long appCount) {
        super.setId(id);
        super.setName(name);
        super.setCode(code);
        super.setType(type);
        super.setCreateTime(createTime);
        super.setRemark(remark);
        this.appCount = String.valueOf(appCount);
    }

}
