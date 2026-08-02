package cn.howxu.mmcr.api.machine;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public sealed interface BlockPredicate {

    boolean matches(BlockState state);

    record Air() implements BlockPredicate {
        @Override public boolean matches(BlockState state) { return state.isAir(); }
    }

    record Any() implements BlockPredicate {
        @Override public boolean matches(BlockState state) { return true; }
    }

    record OfBlock(Block block) implements BlockPredicate {
        @Override public boolean matches(BlockState state) { return state.is(block); }
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
