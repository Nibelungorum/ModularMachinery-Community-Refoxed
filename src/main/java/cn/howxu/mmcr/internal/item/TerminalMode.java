package cn.howxu.mmcr.internal.item;

import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;

/**
 * Terminal operating modes.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum TerminalMode {
    BUILD("build", ChatFormatting.GREEN),
    DEMOLISH("demolish", ChatFormatting.RED);

    public static final Codec<TerminalMode> CODEC = Codec.STRING.xmap(TerminalMode::bySerializedName, TerminalMode::serializedName);
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalMode> STREAM_CODEC = StreamCodec.of(
            (buf, mode) -> ByteBufCodecs.STRING_UTF8.encode(buf, mode.serializedName()),
            buf -> bySerializedName(ByteBufCodecs.STRING_UTF8.decode(buf)));

    private final String serializedName;
    private final ChatFormatting color;

    TerminalMode(String serializedName, ChatFormatting color) {
        this.serializedName = serializedName;
        this.color = color;
    }

    public static TerminalMode defaultMode() {
        return BUILD;
    }

    public static TerminalMode bySerializedName(String name) {
        for (TerminalMode mode : values()) {
            if (mode.serializedName.equals(name)) return mode;
        }
        return defaultMode();
    }

    public TerminalMode next() {
        return this == BUILD ? DEMOLISH : BUILD;
    }

    public String serializedName() {
        return serializedName;
    }

    public ChatFormatting color() {
        return color;
    }

    public Component modeComponent() {
        return Component.translatable("tooltip.mmcr.terminal.mode." + serializedName).withStyle(color);
    }

    public Component tooltipComponent() {
        return Component.translatable("tooltip.mmcr.terminal.mode", modeComponent());
    }

    @Override
    public String toString() {
        return serializedName.toUpperCase(Locale.ROOT);
    }
}
