package cn.howxu.mmcr.api.machine;

import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.internal.block.FactorySchedulerBlock;
import cn.howxu.mmcr.internal.block.IOPortBlock;
import cn.howxu.mmcr.internal.block.ModuleCouplerBlock;
import cn.howxu.mmcr.internal.block.ParallelControllerBlock;
import cn.howxu.mmcr.internal.block.SmartInterfaceBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
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

    default boolean matches(BlockState state, boolean stateSensitive) {
        if (stateSensitive) return matches(state);
        return switch (this) {
            case OfBlockState ofState -> ofState.state().getBlock() == state.getBlock();
            case AnyOf anyOf -> anyOf.children().stream().anyMatch(child -> child.matches(state, false));
            default -> matches(state);
        };
    }

    /**
     * Returns the preferred concrete block state for this predicate, when one is directly representable.
     */
    default Optional<BlockState> preferredState() {
        List<BlockState> candidates = candidateStates(this).stream()
                .sorted(BlockPredicate::compareCandidates)
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
            case DeferredBlock deferredBlock -> {
                if (!deferredBlock.networkInterface()) {
                    states.add(new Candidate(deferredBlock.supplier().get().defaultBlockState(), 0));
                }
            }
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

    private static int blockPriority(Block block) {
        if (block instanceof FactorySchedulerBlock) return 4;
        if (block instanceof ParallelControllerBlock) return 3;
        if (block instanceof SmartInterfaceBlock) return 2;
        if (block instanceof IOPortBlock) return 1;
        return 0;
    }

    private static int compareCandidates(Candidate left, Candidate right) {
        int priority = Integer.compare(blockPriority(left.state().getBlock()), blockPriority(right.state().getBlock()));
        if (priority != 0) return priority;
        int exactState = Integer.compare(right.exactState(), left.exactState());
        if (exactState != 0) return exactState;
        return Integer.compare(levelPriority(right.state()), levelPriority(left.state()));
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

    record DeferredBlock(java.util.function.Supplier<? extends Block> supplier, boolean networkInterface)
            implements BlockPredicate {
        public DeferredBlock(java.util.function.Supplier<? extends Block> supplier) {
            this(supplier, false);
        }

        @Override public boolean matches(BlockState state) {
            if (networkInterface) {
                return state.getBlock().getClass().getName()
                        .equals("cn.howxu.mmcr.internal.block.NetworkInterfaceBlock");
            }
            return state.getBlock() == supplier.get();
        }
    }

    record OfBlockState(BlockState state) implements BlockPredicate {
        @Override public boolean matches(BlockState s) {
            return state.getBlock() == s.getBlock() && state.getValues().toList().equals(s.getValues().toList());
        }
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
