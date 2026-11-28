package cn.tolim.pudans.mq.compensation;

public interface MessageCompensationHandler {

    // Whether this handler covers the given topic
    boolean supports(String topic);

    // Runs the compensating action, throwing signals a failed compensation
    void compensate(CompensationContext context) throws Exception;
}