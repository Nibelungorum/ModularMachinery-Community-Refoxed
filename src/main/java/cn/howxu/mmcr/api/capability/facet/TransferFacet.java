package cn.howxu.mmcr.api.capability.facet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes the world anchor and transfer limit needed by automatic IO.
 *
 * @author howxu <dev@howxu.cn>
 */
public interface TransferFacet extends CapabilityFacet {
    @Nullable
    Level level();

    BlockPos position();

    long transferLimit();
}
