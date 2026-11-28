package cn.tolim.pudans.mq.exception;

public class MessageStateTransitionException extends RuntimeException {

    public MessageStateTransitionException(String subject, String id, Object from, Object to) {
        super(subject + " " + id + " cannot transition from " + from + " to " + to);
    }
}