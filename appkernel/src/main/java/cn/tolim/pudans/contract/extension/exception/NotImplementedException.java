package cn.tolim.pudans.contract.extension.exception;


public class NotImplementedException extends UnsupportedOperationException {

    public NotImplementedException() {
        this("Not implemented");
    }

    public NotImplementedException(String message) {
        super(message);
    }
}
