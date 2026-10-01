package cn.howxu.mmcr.publicapi.structure;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.ApiStatus;

/** Read-only condition produced by BlockConditions; not a matching SPI.
 * @author howxu <dev@howxu.cn>
 */
@ApiStatus.NonExtendable
public interface BlockCondition {
    boolean isMachineCoupler();
    /** Whether this is the runtime any-state predicate. */
    boolean isAny();
    /** Whether this is the runtime air predicate, which accepts all air states. */
    boolean isAir();
    /** Whether this runtime predicate uses the special network-interface matcher. */
    boolean isNetworkInterface();
    Optional<Block> block();
    Optional<BlockState> blockState();
    Optional<Supplier<? extends Block>> blockSupplier();
    Optional<TagKey<Block>> tag();
    List<BlockCondition> alternatives();
}
