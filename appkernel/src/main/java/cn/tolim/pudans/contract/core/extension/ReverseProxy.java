package cn.tolim.pudans.contract.core.extension;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
@GVK(
        group = "plugin.pudans.tolim.cn",
        kind = "ReverseProxy",
        version = "v1alpha1",
        plural = "reverseproxies",
        singular = "reverseproxy")
public class ReverseProxy extends AbstractExtension {

    private List<ReverseProxyRule> rules;


    public record ReverseProxyRule(String path, FileReverseProxyProvider file) {}


    public record FileReverseProxyProvider(String directory, String filename) {}
}
