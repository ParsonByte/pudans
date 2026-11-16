package cn.tolim.pudans.contract.extension.exception;


public class NotImplementedException extends UnsupportedOperationException {

    public NotImplementedException() {
        this("尚未实现");
    }

    public NotImplementedException(String message) {
        super(message);
    }
}
