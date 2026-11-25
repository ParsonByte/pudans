package cn.tolim.pudans.core.ai.protocol;

public interface AiProtocolDescriptor {

    AiProtocol protocol();

    String description();

    String requestPath();

    boolean runtimeSupported();
}
