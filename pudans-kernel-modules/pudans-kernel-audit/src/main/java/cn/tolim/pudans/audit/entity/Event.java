package cn.tolim.pudans.audit.entity;

import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.event.type.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event implements Serializable {

    @Serial
    private static final long serialVersionUID = -1144169992714000310L;

    private EventType type;

    private String param;

    private String content;

    private String result;

    private LocalDateTime time;

    private EventStatus status;
}