package cn.tolim.pudans.communal.redis.serializer;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSerializersTests {

    @Test
    void utf8SerializerRoundTrips() {
        RedisSerializer<String> serializer = RedisSerializers.utf8();

        byte[] bytes = serializer.serialize("hello");

        assertThat(serializer.deserialize(bytes)).isEqualTo("hello");
    }

    @Test
    void jsonSerializerRoundTripsStrings() {
        RedisSerializer<Object> serializer = RedisSerializers.json(JsonMapper.builder().build());

        byte[] bytes = serializer.serialize("hello");

        assertThat(bytes).isNotEmpty();
        assertThat(serializer.deserialize(bytes)).isEqualTo("hello");
    }

    @Test
    void redissonJsonCodecIsProvided() {
        assertThat(RedissonCodecs.json()).isNotNull();
        assertThat(RedissonCodecs.json(JsonMapper.builder().build())).isNotNull();
    }
}