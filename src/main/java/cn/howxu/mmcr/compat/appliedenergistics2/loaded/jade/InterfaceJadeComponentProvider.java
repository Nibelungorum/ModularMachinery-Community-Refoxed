package cn.howxu.mmcr.compat.appliedenergistics2.loaded.jade;

import appeng.me.helpers.IGridConnectedBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.TooltipPosition;

/**
 * Renders the AE2-native grid-node state in Jade.
 *
 * @author howxu <dev@howxu.cn>
 */
public enum InterfaceJadeComponentProvider implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public @NonNull ResourceLocation getUid() {
        return InterfaceJadeDataProvider.UID;
    }

    @Override
    public int getDefaultPriority() {
        return TooltipPosition.TAIL - 9;
    }

    @Override
    public void appendTooltip(@NonNull ITooltip tooltip, BlockAccessor accessor, @NonNull IPluginConfig config) {
        if (!(accessor.getTarget() instanceof IGridConnectedBlockEntity)) return;
        int state = accessor.getServerData().getByteOr(InterfaceJadeDataProvider.STATE, (byte) 0);
        String key = switch (state) {
            case 1 -> "waila.ae2.NetworkBooting";
            case 2 -> "waila.ae2.DeviceMissingChannel";
            case 3 -> "waila.ae2.DeviceOnline";
            default -> "waila.ae2.DeviceOffline";
        };
        // base AE has no color
        ChatFormatting color = switch (state) {
            case 1, 2 -> ChatFormatting.YELLOW;
            case 3 -> ChatFormatting.GREEN;
            default -> ChatFormatting.RED;
        };
        tooltip.add(Component.translatable(key)); // .withStyle(color)
        InterfaceJadeDataProvider.outputs(accessor.getServerData()).forEach(display -> tooltip.add(
                Component.translatable("gui.mmcr.port."
                                + (display.label().equals("energy") ? "energy" : display.label() + "s"))
                        .append(Component.literal(" " + display.value()
                                + (display.unit().equals("item") ? "" : " " + display.unit())))));
    }
}
