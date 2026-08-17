package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.block.ModuleCouplerBlock;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Matches valid multiblock states and selects a representable default state when available.
 *
 * @author howxu <dev@howxu.cn>
 */
public sealed interface BlockPredicate {

    static MachineCoupler machineCoupler() {
        return MachineCoupler.INSTANCE;
    }

    boolean matches(BlockState state);

    /**
     * Returns the preferred concrete block state for this predicate, when one is directly representable.
     */
    default Optional<BlockState> preferredState() {
        List<BlockState> candidates = candidateStates(this).stream()
                .sorted(Comparator.comparingInt(BlockPredicate::levelPriority).reversed())
                .toList();
        if (!candidates.isEmpty()) return Optional.of(candidates.getFirst());
        return Optional.empty();
    }

    private static List<BlockState> candidateStates(BlockPredicate predicate) {
        List<BlockState> states = new ArrayList<>();
        collectCandidateStates(predicate, states);
        return states;
    }

    private static void collectCandidateStates(BlockPredicate predicate, List<BlockState> states) {
        switch (predicate) {
            case OfBlockState ofState -> states.add(ofState.state());
            case OfBlock ofBlock -> states.add(ofBlock.block().defaultBlockState());
            case AnyOf anyOf -> anyOf.children().forEach(child -> collectCandidateStates(child, states));
            default -> {}
        }
    }

    private static int levelPriority(BlockState state) {
        return MachineLevelRegistry.findLevel(state).map(level -> level.priority()).orElse(Integer.MIN_VALUE);
    }

    enum MachineCoupler implements BlockPredicate {
        INSTANCE;

        @Override public boolean matches(BlockState state) { return state.getBlock() instanceof ModuleCouplerBlock; }
    }

    record Air() implements BlockPredicate {
        @Override public boolean matches(BlockState state) { return state.isAir(); }
    }

    record Any() implements BlockPredicate {
        @Override public boolean matches(BlockState state) { return true; }
    }

    record OfBlock(Block block) implements BlockPredicate {
        @Override public boolean matches(BlockState state) { return state.getBlock() == block; }
    }

    record OfBlockState(BlockState state) implements BlockPredicate {
        @Override public boolean matches(BlockState s) { return this.state == s; }
    }

    record OfTag(TagKey<Block> tag) implements BlockPredicate {
        @Override public boolean matches(BlockState state) { return state.is(tag); }
    }

    record AnyOf(List<BlockPredicate> children) implements BlockPredicate {
        @Override public boolean matches(BlockState state) {
            for (var child : children) if (child.matches(state)) return true;
            return false;
        }
    }
}
