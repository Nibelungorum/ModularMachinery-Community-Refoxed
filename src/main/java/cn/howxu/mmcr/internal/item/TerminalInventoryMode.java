package cn.howxu.mmcr.internal.item;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Inventory sources available to the terminal.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum TerminalInventoryMode {
    INVENTORY("inventory"),
    CONTAINER("container");

    public static final Codec<TerminalInventoryMode> CODEC = Codec.STRING.xmap(
            TerminalInventoryMode::bySerializedName, TerminalInventoryMode::serializedName);
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalInventoryMode> STREAM_CODEC = StreamCodec.of(
            (buf, mode) -> ByteBufCodecs.STRING_UTF8.encode(buf, mode.serializedName),
            buf -> bySerializedName(ByteBufCodecs.STRING_UTF8.decode(buf)));

    private final String serializedName;

    TerminalInventoryMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public static TerminalInventoryMode bySerializedName(String name) {
        for (TerminalInventoryMode mode : values()) {
            if (mode.serializedName.equals(name)) return mode;
        }
        return INVENTORY;
    }

    public String serializedName() {
        return serializedName;
    }

    public Component component() {
        return Component.translatable("tooltip.mmcr.terminal.inventory_mode." + serializedName);
    }
}
