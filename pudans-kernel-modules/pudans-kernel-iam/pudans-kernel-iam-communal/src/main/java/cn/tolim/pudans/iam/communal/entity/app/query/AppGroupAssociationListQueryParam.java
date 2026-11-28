package cn.tolim.pudans.iam.communal.entity.app.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
public class AppGroupAssociationListQueryParam implements Serializable {
    @Serial
    private static final long serialVersionUID = -7110595216804896858L;
    // Group ID
    private String id;

    // Application name
    private String appName;
}
