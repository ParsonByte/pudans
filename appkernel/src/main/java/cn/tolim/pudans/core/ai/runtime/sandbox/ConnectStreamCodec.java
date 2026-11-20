package cn.tolim.pudans.core.ai.runtime.sandbox;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Connect 流式协议信封：1 字节标志位 + 4 字节大端长度 + JSON 负载。
final class ConnectStreamCodec {

    private static final int HEADER_BYTES = 5;

    private static final int FLAG_END_STREAM = 0x02;

    private ConnectStreamCodec() {}

    static byte[] frame(byte[] payload) {
        var body = payload == null ? new byte[0] : payload;
        var framed = new byte[HEADER_BYTES + body.length];
        framed[0] = 0;
        ByteBuffer.wrap(framed, 1, 4).putInt(body.length);
        System.arraycopy(body, 0, framed, HEADER_BYTES, body.length);
        return framed;
    }

    static List<Envelope> unframe(byte[] body) {
        var envelopes = new ArrayList<Envelope>();
        var offset = 0;
        while (offset < body.length) {
            if (body.length - offset < HEADER_BYTES) {
                throw new IllegalArgumentException("Connect 信封头不完整");
            }
            var flags = body[offset] & 0xFF;
            var length = ByteBuffer.wrap(body, offset + 1, 4).getInt();
            if (length < 0 || offset + HEADER_BYTES + length > body.length) {
                throw new IllegalArgumentException("Connect 信封长度非法");
            }
            var payload = Arrays.copyOfRange(body, offset + HEADER_BYTES, offset + HEADER_BYTES + length);
            envelopes.add(new Envelope(flags, payload));
            offset += HEADER_BYTES + length;
        }
        return envelopes;
    }

    record Envelope(int flags, byte[] payload) {

        boolean endStream() {
            return (flags & FLAG_END_STREAM) != 0;
        }
    }
}