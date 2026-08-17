package cn.howxu.mmcr.util;

import net.minecraft.util.StringRepresentable;

public enum IOType implements StringRepresentable {
    INPUT, OUTPUT;

    public IOType opposite() {
        return this == INPUT ? OUTPUT : INPUT;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase();
    }
}
