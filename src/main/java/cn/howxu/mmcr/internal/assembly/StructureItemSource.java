package cn.howxu.mmcr.internal.assembly;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Source of blocks for structure assembly.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface StructureItemSource {
    List<ItemStack> copyStacks();

    boolean canExtractAll(List<ItemStack> requirements);

    boolean extractAll(List<ItemStack> requirements);

    /** Snapshot of whole source blocks available for placement, not fluid volume. */
    default Map<Fluid, Long> copyFluidSources() {
        return Map.of();
    }

    /** Consumes one source block's fluid only when the placement succeeds. */
    default boolean placeFluidSource(Fluid fluid, BooleanSupplier placement) {
        return false;
    }
}
