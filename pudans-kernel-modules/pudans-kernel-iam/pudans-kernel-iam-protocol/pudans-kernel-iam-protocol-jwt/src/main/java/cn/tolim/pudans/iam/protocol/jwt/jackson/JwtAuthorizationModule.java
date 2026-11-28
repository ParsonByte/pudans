
package cn.tolim.pudans.iam.protocol.jwt.jackson;

import cn.tolim.pudans.iam.protocol.jwt.authentication.JwtAuthenticationToken;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.security.jackson2.SecurityJackson2Modules;


public class JwtAuthorizationModule extends SimpleModule {

    public JwtAuthorizationModule() {
        super(JwtAuthorizationModule.class.getName(), new Version(1, 0, 0, null, null, null));
    }

    @Override
    public void setupModule(SetupContext context) {
        SecurityJackson2Modules.enableDefaultTyping(context.getOwner());
        context.setMixInAnnotations(JwtAuthenticationToken.class,
                JwtAuthenticationTokenMixin.class);
    }

}
