package cn.tolim.pudans.core.storage.driver;

import cn.tolim.pudans.core.webhook.OutboundRequestValidator;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import okhttp3.Dns;

// 在 OkHttp 解析结果上再次校验，阻止存储域名在连接期指向内网。
public final class ValidatingOkHttpDns implements Dns {

    // 保留系统解析结果，但拒绝任何不可路由地址。
    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        var addresses = Dns.SYSTEM.lookup(hostname);
        for (var address : addresses) {
            if (OutboundRequestValidator.isBlocked(address)) {
                throw new UnknownHostException("存储服务地址不可路由：" + hostname);
            }
        }
        return addresses;
    }
}
