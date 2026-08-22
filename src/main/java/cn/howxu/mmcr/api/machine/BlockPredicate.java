package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.block.ModuleCouplerBlock;
import net.minecraft.core.registries.BuiltInRegistries;
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

    default List<BlockPredicate> children() { return List.of(); }

    static MachineCoupler machineCoupler() {
        return MachineCoupler.INSTANCE;
    }

    boolean matches(BlockState state);

    /**
     * Returns the preferred concrete block state for this predicate, when one is directly representable.
     */
    default Optional<BlockState> preferredState() {
        List<BlockState> candidates = candidateStates(this).stream()
                .sorted(Comparator.comparingInt(Candidate::exactState).reversed()
                        .thenComparing(Comparator.comparingInt((Candidate candidate) -> levelPriority(candidate.state())).reversed()))
                .map(Candidate::state)
                .toList();
        if (!candidates.isEmpty()) return Optional.of(candidates.getFirst());
        return Optional.empty();
    }

    private static List<Candidate> candidateStates(BlockPredicate predicate) {
        List<Candidate> states = new ArrayList<>();
        collectCandidateStates(predicate, states);
        return states;
    }

    private static void collectCandidateStates(BlockPredicate predicate, List<Candidate> states) {
        switch (predicate) {
            case OfBlockState ofState -> states.add(new Candidate(ofState.state(), 1));
            case OfBlock ofBlock -> states.add(new Candidate(ofBlock.block().defaultBlockState(), 0));
            case DeferredBlock deferredBlock -> states.add(new Candidate(deferredBlock.supplier().get().defaultBlockState(), 0));
            case OfTag ofTag -> blocksInTag(ofTag.tag()).stream()
                    .map(Block::defaultBlockState)
                    .map(state -> new Candidate(state, 0))
                    .forEach(states::add);
            case AnyOf anyOf -> anyOf.children().forEach(child -> collectCandidateStates(child, states));
            default -> {}
        }
    }

    public static List<Block> blocksInTag(TagKey<Block> tag) {
        List<Block> blocks = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.builtInRegistryHolder().is(tag)) blocks.add(block);
        }
        return List.copyOf(blocks);
    }

    private static int levelPriority(BlockState state) {
        return MachineLevelRegistry.findLevel(state).map(level -> level.priority()).orElse(Integer.MIN_VALUE);
    }


    record Candidate(BlockState state, int exactState) {}

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

    record DeferredBlock(java.util.function.Supplier<? extends Block> supplier) implements BlockPredicate {
        @Override public boolean matches(BlockState state) { return state.getBlock() == supplier.get(); }
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
