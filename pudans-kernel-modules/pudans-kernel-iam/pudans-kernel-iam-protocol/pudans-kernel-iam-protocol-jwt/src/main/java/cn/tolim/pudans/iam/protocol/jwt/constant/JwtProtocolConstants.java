
package cn.tolim.pudans.iam.protocol.jwt.constant;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;
import static cn.tolim.pudans.iam.protocol.core.constant.ProtocolConstants.PROTOCOL_CACHE_PREFIX;


public class JwtProtocolConstants {


    public static final String JWT_PROTOCOL_CACHE_PREFIX = PROTOCOL_CACHE_PREFIX + "jwt" + COLON;

    public static final String TARGET_URL = "target_url";
    public static final String ID_TOKEN = "id_token";
    public static final String NONCE = "nonce";

    public static final String URL = "url";
    public static final String BINDING_TYPE = "binding_type";

    public static final String JWT_ERROR_URI = "https://iam.pudans.cn";

    public static final String POST_LOGOUT_REDIRECT_URI = "post_logout_redirect_uri";

}
