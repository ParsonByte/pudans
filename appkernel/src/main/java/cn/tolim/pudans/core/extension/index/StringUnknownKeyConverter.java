package cn.tolim.pudans.core.extension.index;

import cn.tolim.pudans.contract.extension.index.UnknownKey;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;


@Component
class StringUnknownKeyConverter implements Converter<String, UnknownKey> {

    @Override
    public UnknownKey convert(String source) {
        return new UnknownKey(source);
    }
}
