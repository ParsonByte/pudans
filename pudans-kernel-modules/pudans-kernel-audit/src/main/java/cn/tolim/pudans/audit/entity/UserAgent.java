package cn.tolim.pudans.audit.entity;

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
public class UserAgent implements Serializable {

    @Serial
    private static final long serialVersionUID = -1144169992714000310L;

    private String deviceType;

    private String platform;

    private String platformVersion;

    private String browser;

    private String browserType;

    private String browserMajorVersion;
}