package cn.howxu.mmcr.internal.assembly;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Compatibility adapter for a player inventory backed structure storage.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PlayerInventoryStructureItemSink implements StructureItemSink {
    private final StructureItemSink sink;

    public PlayerInventoryStructureItemSink(ServerPlayer player) {
        this.sink = new PlayerInventoryStructureItemStorage(player).sink();
    }

    @Override
    public boolean accept(ItemStack stack) {
        return sink.accept(stack);
    }
}
