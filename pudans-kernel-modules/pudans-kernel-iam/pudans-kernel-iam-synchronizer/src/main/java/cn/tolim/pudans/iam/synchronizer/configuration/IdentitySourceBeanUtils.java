package cn.tolim.pudans.iam.synchronizer.configuration;

import org.apache.commons.codec.digest.DigestUtils;


public class IdentitySourceBeanUtils {

    // Get bean name
    public static String getSourceBeanName(String id) {
        return "identitySourceBean_" + DigestUtils.md5Hex(id);
    }
}
