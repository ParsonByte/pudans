package cn.tolim.pudans.infra.contract;

import java.net.URI;
import java.net.URL;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;


public interface ExternalUrlSupplier extends Supplier<URI> {


    // 返回当前解析出的外部访问地址。
    @Override
    URI get();


    // 结合当前请求上下文推导外部访问地址。
    URL getURL(HttpRequest request);


    // 返回未解析的原始地址，可能为空。
    @Nullable
    URL getRaw();
}
