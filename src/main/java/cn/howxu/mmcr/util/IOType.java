package cn.howxu.mmcr.util;

public enum IOType {
    INPUT, OUTPUT;

    public IOType opposite() {
        return this == INPUT ? OUTPUT : INPUT;
    }
}
