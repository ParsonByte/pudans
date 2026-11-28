package cn.tolim.pudans.iam.communal.entity.app.query;

import cn.tolim.pudans.iam.communal.enums.app.AppGroupType;
import lombok.Data;

import java.io.Serializable;


@Data
public class AppGroupQueryParam implements Serializable {

    // Group name
    private String name;

    // Application name
    private String appName;

    // Group code
    private String code;

    // Group type
    private AppGroupType type;

}
