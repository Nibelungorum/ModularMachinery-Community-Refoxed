package cn.howxu.mmcr.compat.fluxnetworks.loaded;

import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworkOverlay;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksIds;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.util.FastColor;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import sonar.fluxnetworks.client.FluxColorHandler;

/** Native block and item network colours for the Flux core overlay only.
 * @author howxu <dev@howxu.cn>
 */
public final class FluxNetworksClient {
    private FluxNetworksClient() {
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> tintIndex == FluxNetworkOverlay.NETWORK_TINT_INDEX
                        ? FluxColorHandler.INSTANCE.getColor(state, level, pos, 1) : -1,
                ModBlocks.BLOCKS.get(FluxNetworksIds.INPUT).get(),
                ModBlocks.BLOCKS.get(FluxNetworksIds.OUTPUT).get());
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == FluxNetworkOverlay.NETWORK_TINT_INDEX
                        ? FastColor.ARGB32.opaque(FluxColorHandler.INSTANCE.getColor(stack, 1)) : -1,
                ModBlocks.BLOCKS.get(FluxNetworksIds.INPUT).get(),
                ModBlocks.BLOCKS.get(FluxNetworksIds.OUTPUT).get());
    }
}
