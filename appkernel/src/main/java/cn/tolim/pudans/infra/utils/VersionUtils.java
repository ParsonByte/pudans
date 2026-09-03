package cn.tolim.pudans.infra.utils;

import com.github.zafarkhaja.semver.Version;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.server.ServerWebInputException;

@UtilityClass
public class VersionUtils {


    public static boolean satisfiesRequires(String version, String requires) {
        String requiresVersion = StringUtils.trim(requires);


        if (requiresVersion.matches("^\\d+\\.\\d+\\.\\d+$")) {

            requiresVersion = ">=" + requiresVersion;
        }
        return version.equals("0.0.0") || checkVersionConstraint(version, requiresVersion);
    }


    public static boolean checkVersionConstraint(String version, String constraint) {
        try {
            return StringUtils.isBlank(constraint)
                    || "*".equals(constraint)
                    || Version.parse(version).satisfies(constraint);
        } catch (Exception e) {
            throw new ServerWebInputException("Illegal requires version expression.", null, e);
        }
    }
}
