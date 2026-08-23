package cn.howxu.mmcr.api.publicapi.machine;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

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
    private final boolean automaticController;
    private final Block block;
    private final BlockState state;
    private final Supplier<? extends Block> blockSupplier;
    private final TagKey<Block> tag;
    private final List<BlockPredicate> alternatives;

    private BlockPredicate(boolean machineCoupler, boolean automaticController, Block block, BlockState state,
            Supplier<? extends Block> blockSupplier,
            TagKey<Block> tag, List<BlockPredicate> alternatives) {
        this.machineCoupler = machineCoupler;
        this.automaticController = automaticController;
        this.block = block;
        this.state = state;
        this.blockSupplier = blockSupplier;
        this.tag = tag;
        this.alternatives = alternatives;
    }

    public static BlockPredicate block(Block block) {
        return new BlockPredicate(false, false, Objects.requireNonNull(block, "block"), null, null, null, List.of());
    }

    public static BlockPredicate block(String id) {
        return block(Identifier.parse(id));
    }

    public static BlockPredicate block(Identifier id) {
        Objects.requireNonNull(id, "id");
        return deferredBlock(() -> BuiltInRegistries.BLOCK.getValue(id));
    }

    public static BlockPredicate state(String id) {
        Objects.requireNonNull(id, "id");
        int propertiesStart = id.indexOf('[');
        if (propertiesStart < 0) return state(Identifier.parse(id));
        if (!id.endsWith("]")) throw new IllegalArgumentException("Invalid block state: " + id);
        String blockId = id.substring(0, propertiesStart);
        String properties = id.substring(propertiesStart + 1, id.length() - 1);
        if (properties.isEmpty()) throw new IllegalArgumentException("Invalid block state: " + id);
        BlockState state = BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId)).defaultBlockState();
        for (String assignment : properties.split(",", -1)) {
            String[] pair = assignment.split("=", -1);
            if (pair.length != 2 || pair[0].isEmpty() || pair[1].isEmpty()) {
                throw new IllegalArgumentException("Invalid block state property: " + assignment);
            }
            Property<?> property = state.getBlock().getStateDefinition().getProperty(pair[0]);
            if (property == null) throw new IllegalArgumentException("Unknown block state property: " + pair[0]);
            state = setProperty(state, property, pair[1]);
        }
        return blockState(state);
    }

    public static BlockPredicate state(Identifier id) {
        Objects.requireNonNull(id, "id");
        return blockState(BuiltInRegistries.BLOCK.getValue(id).defaultBlockState());
    }

    private static <T extends Comparable<T>> BlockState setProperty(
            BlockState state, Property<T> property, String value) {
        T parsed = property.getValue(value)
                .orElseThrow(() -> new IllegalArgumentException("Invalid value " + value
                        + " for block state property " + property.getName()));
        return state.setValue(property, parsed);
    }

    public static BlockPredicate blockState(BlockState state) {
        return new BlockPredicate(false, false, null, Objects.requireNonNull(state, "state"), null, null, List.of());
    }

    public static BlockPredicate deferredBlock(Supplier<? extends Block> blockSupplier) {
        return new BlockPredicate(false, false, null, null, Objects.requireNonNull(blockSupplier, "blockSupplier"), null, List.of());
    }

    public static BlockPredicate tag(TagKey<Block> tag) {
        return new BlockPredicate(false, false, null, null, null, Objects.requireNonNull(tag, "tag"), List.of());
    }

    public static BlockPredicate machineCoupler() {
        return new BlockPredicate(true, false, null, null, null, null, List.of());
    }

    public static BlockPredicate coupler() {
        return machineCoupler();
    }

    static BlockPredicate automaticController() {
        return new BlockPredicate(false, true, null, null, null, null, List.of());
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
        return new BlockPredicate(false, false, null, null, null, null, List.copyOf(predicates));
    }

    public boolean isMachineCoupler() {
        return machineCoupler;
    }

    boolean isAutomaticController() {
        return automaticController;
    }

    public Optional<Block> block() {
        return Optional.ofNullable(block);
    }

    public Optional<BlockState> blockState() {
        return Optional.ofNullable(state);
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
        return machineCoupler == that.machineCoupler && automaticController == that.automaticController
                && Objects.equals(block, that.block)
                && Objects.equals(state, that.state)
                && Objects.equals(tag, that.tag)
                && alternatives.equals(that.alternatives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(machineCoupler, automaticController, block, state, tag, alternatives);
    }
}
