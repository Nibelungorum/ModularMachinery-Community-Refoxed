package cn.howxu.mmcr.util;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

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
