package cn.howxu.mmcr.internal.assembly;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import java.util.function.BooleanSupplier;

/**
 * Destination for blocks removed from structures.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface StructureItemSink {
    /**
     * Accepts a complete item stack without mutating the destination on failure.
     */
    boolean accept(ItemStack stack);

    default boolean supportsFluidSources() {
        return false;
    }

    /** Recovers one source block only when the complete fluid amount fits and removal succeeds. */
    default boolean recoverFluidSource(Fluid fluid, BooleanSupplier removal) {
        return false;
    }
}
