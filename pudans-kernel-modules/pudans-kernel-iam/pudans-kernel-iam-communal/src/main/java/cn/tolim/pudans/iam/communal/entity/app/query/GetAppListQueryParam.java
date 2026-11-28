package cn.tolim.pudans.iam.communal.entity.app.query;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
public class GetAppListQueryParam implements Serializable {

    @Serial
    private static final long serialVersionUID = -4981513177967939516L;

    // Application name
    private String name;

    // Application group ID
    private String groupId;

}
