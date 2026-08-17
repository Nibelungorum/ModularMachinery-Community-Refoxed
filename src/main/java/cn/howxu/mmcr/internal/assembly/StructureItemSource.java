package cn.howxu.mmcr.internal.assembly;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Source of blocks for structure assembly.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface StructureItemSource {
    List<ItemStack> copyStacks();

    boolean canExtractAll(List<ItemStack> requirements);

    boolean extractAll(List<ItemStack> requirements);
}
