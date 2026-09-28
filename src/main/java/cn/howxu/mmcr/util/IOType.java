package cn.howxu.mmcr.util;

import net.minecraft.util.StringRepresentable;
import org.checkerframework.checker.nullness.qual.NonNull;

public enum IOType implements StringRepresentable {
    INPUT, OUTPUT;

    public IOType opposite() {
        return this == INPUT ? OUTPUT : INPUT;
    }

    @Override
    public @NonNull String getSerializedName() {
        return name().toLowerCase();
    }
}
