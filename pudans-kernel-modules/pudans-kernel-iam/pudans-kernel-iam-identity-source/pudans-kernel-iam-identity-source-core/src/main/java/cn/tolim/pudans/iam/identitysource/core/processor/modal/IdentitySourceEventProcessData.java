package cn.tolim.pudans.iam.identitysource.core.processor.modal;

import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceProvider;
import cn.tolim.pudans.iam.identitysource.core.enums.IdentitySourceEventReceiveType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

// Identity source callback event data
@Data
@AllArgsConstructor
public class IdentitySourceEventProcessData<T> implements Serializable {

    private String id;

    // Data
    private List<T> data;

    // Identity provider
    private IdentitySourceProvider provider;

    // Time event
    private LocalDateTime eventTime;

    // Event type
    private IdentitySourceEventReceiveType eventType;

}
