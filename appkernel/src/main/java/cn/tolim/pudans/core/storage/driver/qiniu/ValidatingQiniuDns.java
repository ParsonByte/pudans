package cn.tolim.pudans.core.storage.driver.qiniu;

import cn.tolim.pudans.core.webhook.OutboundRequestValidator;
import com.qiniu.http.Dns;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

// 在七牛 SDK 解析域名时复用出站地址校验。
final class ValidatingQiniuDns implements Dns {

    // 解析后拒绝回环、内网和云元数据地址。
    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        var addresses = List.of(InetAddress.getAllByName(hostname));
        for (var address : addresses) {
            if (OutboundRequestValidator.isBlocked(address)) {
                throw new UnknownHostException("存储服务地址不可路由：" + hostname);
            }
        }
        return addresses;
    }
}
