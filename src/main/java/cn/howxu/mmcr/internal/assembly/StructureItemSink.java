package cn.howxu.mmcr.internal.assembly;

import net.minecraft.world.item.ItemStack;

/**
 * Destination for blocks removed from structures.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface StructureItemSink {
    void accept(ItemStack stack);
}
