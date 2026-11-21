package cn.tolim.pudans.infra.contract;

import java.net.URI;
import java.net.URL;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;


public interface ExternalUrlSupplier extends Supplier<URI> {


    @Override
    URI get();


    URL getURL(HttpRequest request);


    @Nullable
    URL getRaw();
}
