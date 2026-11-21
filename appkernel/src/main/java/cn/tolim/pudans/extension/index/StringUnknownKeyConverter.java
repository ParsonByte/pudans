package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.extension.contract.index.UnknownKey;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;


@Component
class StringUnknownKeyConverter implements Converter<String, UnknownKey> {

    // 把字符串包装为未知键类型。
    @Override
    public UnknownKey convert(String source) {
        return new UnknownKey(source);
    }
}
