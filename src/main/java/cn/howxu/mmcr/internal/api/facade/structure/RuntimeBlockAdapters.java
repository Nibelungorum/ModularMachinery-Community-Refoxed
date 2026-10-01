package cn.howxu.mmcr.internal.api.facade.structure;

import cn.howxu.mmcr.api.machine.BlockPredicate.Air;
import cn.howxu.mmcr.api.machine.BlockPredicate.Any;
import cn.howxu.mmcr.api.machine.BlockPredicate.AnyOf;
import cn.howxu.mmcr.api.machine.BlockPredicate.DeferredBlock;
import cn.howxu.mmcr.api.machine.BlockPredicate.MachineCoupler;
import cn.howxu.mmcr.api.machine.BlockPredicate.OfBlock;
import cn.howxu.mmcr.api.machine.BlockPredicate.OfBlockState;
import cn.howxu.mmcr.api.machine.BlockPredicate.OfTag;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.publicapi.structure.BlockCondition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Read-only runtime predicate boundary; declaration conversion must be lossless.
 * @author howxu <dev@howxu.cn>
 */
final class RuntimeBlockAdapters {
    private RuntimeBlockAdapters() {}

    static BlockCondition wrap(cn.howxu.mmcr.api.machine.BlockPredicate value) {
        return new RuntimeConditionView(Objects.requireNonNull(value));
    }

    static cn.howxu.mmcr.api.machine.BlockPredicate unwrap(BlockCondition value) {
        if (value instanceof RuntimeConditionView view) return view.value;
        throw new IllegalArgumentException("Condition is not runtime-backed");
    }

    static BlockPredicate toDeclaration(cn.howxu.mmcr.api.machine.BlockPredicate value) {
        return switch (value) {
            case OfBlock predicate -> BlockPredicate.block(predicate.block());
            case OfBlockState predicate -> BlockPredicate.blockState(predicate.state());
            case OfTag predicate -> BlockPredicate.tag(predicate.tag());
            case MachineCoupler ignored -> BlockPredicate.coupler();
            case DeferredBlock predicate -> {
                if (predicate.networkInterface()) throw notRepresentable("network interface");
                yield BlockPredicate.deferredBlock(predicate.supplier());
            }
            case AnyOf predicate -> {
                if (predicate.children().isEmpty()) throw notRepresentable("empty alternatives");
                yield BlockPredicate.anyOf(predicate.children().stream().map(RuntimeBlockAdapters::toDeclaration).toList());
            }
            case Any ignored -> throw notRepresentable("any state");
            case Air ignored -> throw notRepresentable("all air states");
        };
    }

    private static UnsupportedOperationException notRepresentable(String kind) {
        return new UnsupportedOperationException("Runtime predicate has no lossless declaration: " + kind
                + "; use StructureAdapters.unwrapRuntime for runtime level registration");
    }

    /** Holds the actual runtime predicate without resolving deferred blocks.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RuntimeConditionView implements BlockCondition {
        private final cn.howxu.mmcr.api.machine.BlockPredicate value;
        private RuntimeConditionView(cn.howxu.mmcr.api.machine.BlockPredicate value) { this.value = value; }
        public boolean isMachineCoupler() { return value instanceof MachineCoupler; }
        public boolean isAny() { return value instanceof Any; }
        public boolean isAir() { return value instanceof Air; }
        public boolean isNetworkInterface() { return value instanceof DeferredBlock predicate && predicate.networkInterface(); }
        public Optional<Block> block() { return value instanceof OfBlock predicate ? Optional.of(predicate.block()) : Optional.empty(); }
        public Optional<BlockState> blockState() {
            return value instanceof OfBlockState predicate ? Optional.of(predicate.state()) : Optional.empty();
        }
        public Optional<Supplier<? extends Block>> blockSupplier() {
            return value instanceof DeferredBlock predicate ? Optional.of(predicate.supplier()) : Optional.empty();
        }
        public Optional<TagKey<Block>> tag() { return value instanceof OfTag predicate ? Optional.of(predicate.tag()) : Optional.empty(); }
        public List<BlockCondition> alternatives() {
            return value.children().stream().map(RuntimeBlockAdapters::wrap).toList();
        }
        @Override public boolean equals(Object other) { return other instanceof RuntimeConditionView view && value.equals(view.value); }
        @Override public int hashCode() { return value.hashCode(); }
    }
}
