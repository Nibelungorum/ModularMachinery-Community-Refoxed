package cn.howxu.mmcr.internal.assembly;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Returns removed structure blocks to a player inventory, dropping remainders at the player's feet.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PlayerInventoryStructureItemSink implements StructureItemSink {
    private final ServerPlayer player;

    public PlayerInventoryStructureItemSink(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public void accept(ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack remainder = stack.copy();
        boolean inserted = player.getInventory().add(remainder);
        if (!inserted || !remainder.isEmpty()) {
            player.drop(remainder, false);
        }
    }
}
