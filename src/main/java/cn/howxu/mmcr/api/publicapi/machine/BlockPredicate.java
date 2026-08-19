package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Public block matching predicate value.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BlockPredicate {
    private final Block block;
    private final TagKey<Block> tag;
    private final List<BlockPredicate> alternatives;

    private BlockPredicate(Block block, TagKey<Block> tag, List<BlockPredicate> alternatives) {
        this.block = block;
        this.tag = tag;
        this.alternatives = alternatives;
    }

    public static BlockPredicate block(Block block) {
        return new BlockPredicate(Objects.requireNonNull(block, "block"), null, List.of());
    }

    public static BlockPredicate tag(TagKey<Block> tag) {
        return new BlockPredicate(null, Objects.requireNonNull(tag, "tag"), List.of());
    }

    public static BlockPredicate any(BlockPredicate... predicates) {
        Objects.requireNonNull(predicates, "predicates");
        return anyOf(Arrays.asList(predicates));
    }

    public static BlockPredicate anyOf(Collection<BlockPredicate> predicates) {
        Objects.requireNonNull(predicates, "predicates");
        if (predicates.isEmpty()) {
            throw new IllegalArgumentException("At least one alternative predicate is required");
        }
        for (BlockPredicate predicate : predicates) {
            Objects.requireNonNull(predicate, "predicate");
        }
        return new BlockPredicate(null, null, List.copyOf(predicates));
    }

    public Optional<Block> block() {
        return Optional.ofNullable(block);
    }

    public Optional<TagKey<Block>> tag() {
        return Optional.ofNullable(tag);
    }

    public List<BlockPredicate> alternatives() {
        return alternatives;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof BlockPredicate that)) return false;
        return Objects.equals(block, that.block)
                && Objects.equals(tag, that.tag)
                && alternatives.equals(that.alternatives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(block, tag, alternatives);
    }
}
