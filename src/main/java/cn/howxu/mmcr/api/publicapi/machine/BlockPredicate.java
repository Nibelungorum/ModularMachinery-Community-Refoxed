package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Public block matching predicate value.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class BlockPredicate {
    private final boolean machineCoupler;
    private final Block block;
    private final Supplier<? extends Block> blockSupplier;
    private final TagKey<Block> tag;
    private final List<BlockPredicate> alternatives;

    private BlockPredicate(boolean machineCoupler, Block block, Supplier<? extends Block> blockSupplier,
            TagKey<Block> tag, List<BlockPredicate> alternatives) {
        this.machineCoupler = machineCoupler;
        this.block = block;
        this.blockSupplier = blockSupplier;
        this.tag = tag;
        this.alternatives = alternatives;
    }

    public static BlockPredicate block(Block block) {
        return new BlockPredicate(false, Objects.requireNonNull(block, "block"), null, null, List.of());
    }

    public static BlockPredicate deferredBlock(Supplier<? extends Block> blockSupplier) {
        return new BlockPredicate(false, null, Objects.requireNonNull(blockSupplier, "blockSupplier"), null, List.of());
    }

    public static BlockPredicate tag(TagKey<Block> tag) {
        return new BlockPredicate(false, null, null, Objects.requireNonNull(tag, "tag"), List.of());
    }

    public static BlockPredicate machineCoupler() {
        return new BlockPredicate(true, null, null, null, List.of());
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
        return new BlockPredicate(false, null, null, null, List.copyOf(predicates));
    }

    public boolean isMachineCoupler() {
        return machineCoupler;
    }

    public Optional<Block> block() {
        return Optional.ofNullable(block);
    }

    public Optional<Supplier<? extends Block>> blockSupplier() {
        return Optional.ofNullable(blockSupplier);
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
        return machineCoupler == that.machineCoupler
                && Objects.equals(block, that.block)
                && Objects.equals(tag, that.tag)
                && alternatives.equals(that.alternatives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(machineCoupler, block, tag, alternatives);
    }
}
