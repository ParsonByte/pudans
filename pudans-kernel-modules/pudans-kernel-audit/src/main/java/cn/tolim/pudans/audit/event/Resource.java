package cn.tolim.pudans.audit.event;

import java.io.Serializable;

public abstract class Resource implements Serializable {

    private final String code;

    private final String name;

    protected Resource(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("[%s](%s)", name, code);
    }
}