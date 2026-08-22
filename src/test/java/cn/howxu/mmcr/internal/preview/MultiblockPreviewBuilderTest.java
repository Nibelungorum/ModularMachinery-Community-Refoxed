package cn.howxu.mmcr.internal.preview;

import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.BlockArrayCache;
import cn.howxu.mmcr.api.machine.BlockRotator;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

class MultiblockPreviewBuilderTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void preview_state_uses_explicit_block_state() {
        var state = Blocks.COPPER_BLOCK.defaultBlockState();

        var result = MultiblockPreviewBuilder.previewState(new BlockPredicate.OfBlockState(state));

        assertEquals(state, result.orElseThrow());
    }

    @Test
    void build_uses_directional_state_from_the_rotated_compiled_pattern() {
        BlockPos rawPosition = new BlockPos(1, 0, 0);
        BlockState southState = Blocks.OAK_LOG.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        BlockArray pattern = new BlockArray(Map.of(rawPosition, new BlockPredicate.OfBlockState(southState)));
        BlockArray rotated = BlockArrayCache.get(pattern, Direction.EAST);
        BlockPos rotatedPosition = BlockRotator.rotateSouthTo(rawPosition, Direction.EAST);

        var snapshot = MultiblockPreviewBuilder.build(LevelStub.create(Map.of()), BlockPos.ZERO, rotated, 16);

        assertEquals(southState.rotate(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90),
                entriesByPosition(snapshot).get(rotatedPosition));
    }

    @Test
    void preview_state_uses_default_state_for_block_predicate() {
        var result = MultiblockPreviewBuilder.previewState(new BlockPredicate.OfBlock(Blocks.IRON_BLOCK));

        assertEquals(Blocks.IRON_BLOCK.defaultBlockState(), result.orElseThrow());
    }

    @Test
    void preview_state_resolves_deferred_port_predicate() {
        var result = MultiblockPreviewBuilder.previewState(new BlockPredicate.DeferredBlock(
                () -> ModBlocks.BLOCKS.get("item_input_bus").get()));

        assertEquals(ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState(), result.orElseThrow());
    }

    @Test
    void preview_state_uses_first_supported_any_of_child() {
        var result = MultiblockPreviewBuilder.previewState(new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK))));

        assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), result.orElseThrow());
    }

    @Test
    void preview_state_prefers_exact_state_over_plain_block_in_any_of() {
        var exactState = Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);

        var result = MultiblockPreviewBuilder.previewState(new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.OAK_LOG),
                new BlockPredicate.OfBlockState(exactState))));

        assertEquals(exactState, result.orElseThrow());
    }

    @Test
    void build_uses_original_base_predicate_when_modifier_alternatives_are_supported_by_matching() {
        var level = LevelStub.create(Map.of());
        var controller = new BlockPos(0, 64, 0);
        var basePosition = new BlockPos(1, 0, 0);
        var modifierAlternatives = List.of(
                modifierReplacement(Blocks.DIAMOND_BLOCK),
                modifierReplacement(Blocks.GOLD_BLOCK));
        var effectiveMatchingPredicate = new BlockPredicate.AnyOf(List.of(
                modifierAlternatives.get(0).getReplacement(),
                modifierAlternatives.get(1).getReplacement(),
                new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE)));
        var pattern = new BlockArray(Map.of(basePosition, new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE)));

        var snapshot = MultiblockPreviewBuilder.build(level, controller, pattern, 8192);

        assertEquals(Blocks.DIAMOND_BLOCK.defaultBlockState(),
                MultiblockPreviewBuilder.previewState(effectiveMatchingPredicate).orElseThrow());
        assertEquals(Blocks.BLAST_FURNACE.defaultBlockState(), entriesByPosition(snapshot).get(basePosition));
    }

    @Test
    void preview_state_prefers_normal_blocks_before_interfaces_in_any_of() {
        var result = MultiblockPreviewBuilder.previewState(new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()),
                new BlockPredicate.OfBlock(ModBlocks.SMART_INTERFACE.get()),
                new BlockPredicate.OfBlock(Blocks.PURPUR_PILLAR))));

        assertEquals(Blocks.PURPUR_PILLAR.defaultBlockState(), result.orElseThrow());
    }

    @Test
    void preview_state_prefers_io_interfaces_before_smart_interfaces_in_any_of() {
        var result = MultiblockPreviewBuilder.previewState(new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(ModBlocks.SMART_INTERFACE.get()),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))));

        assertEquals(ModBlocks.BLOCKS.get("item_input_bus").get().defaultBlockState(), result.orElseThrow());
    }

    @Test
    void preview_state_prefers_normal_port_parallel_and_factory_in_that_order() {
        var predicate = new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("factory_controller").get()),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("parallel_controller_normal").get()),
                new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()),
                new BlockPredicate.OfBlock(Blocks.PURPUR_PILLAR)));

        assertEquals(Blocks.PURPUR_PILLAR.defaultBlockState(), MultiblockPreviewBuilder.previewState(predicate).orElseThrow());
        var preferred = predicate.preferredState().orElseThrow();
        assertThat(preferred.getBlock()).as("preferred block").isSameAs(Blocks.PURPUR_PILLAR);
    }

    @Test
    void preview_state_returns_one_block_for_a_tag_predicate() throws Exception {
        TagKey<Block> tag = TagKey.create(BuiltInRegistries.BLOCK.key(), Identifier.fromNamespaceAndPath("mmcr", "preview_test"));
        var bindTags = Class.forName("net.minecraft.core.Holder$Reference").getDeclaredMethod("bindTags", java.util.Collection.class);
        bindTags.setAccessible(true);
        bindTags.invoke(Blocks.OAK_LOG.builtInRegistryHolder(), java.util.Set.of(tag));
        BlockPredicate unsupported = new BlockPredicate.OfTag(tag);

        assertTrue(MultiblockPreviewBuilder.previewState(unsupported).isPresent());
    }

    @Test
    void build_skips_positions_that_are_not_air() {
        var controller = new BlockPos(10, 64, 10);
        var occupiedRelative = new BlockPos(1, 0, 0);
        var level = LevelStub.create(Map.of(controller.offset(occupiedRelative), Blocks.STONE));
        var pattern = new BlockArray(Map.of(
                occupiedRelative, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK)));

        var snapshot = MultiblockPreviewBuilder.build(level, controller, pattern, 8192);

        assertEquals(1, snapshot.entries().size());
        assertEquals(new BlockPos(2, 0, 0), snapshot.entries().getFirst().relativePos());
    }

    @Test
    void build_truncates_to_max_entries() {
        var level = LevelStub.create(Map.of());
        var controller = new BlockPos(0, 64, 0);
        var pattern = new BlockArray(Map.of(
                new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                new BlockPos(2, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK)));

        var snapshot = MultiblockPreviewBuilder.build(level, controller, pattern, 1);

        assertEquals(1, snapshot.entries().size());
    }

    @Test
    void build_keeps_each_machine_own_coupler_without_rendering_the_other_structure() {
        var level = LevelStub.create(Map.of());
        var controller = new BlockPos(0, 64, 0);
        var hostCoupler = new BlockPos(1, 0, 0);
        var moduleCoupler = new BlockPos(-1, 0, 0);
        var hostOnly = new BlockPos(2, 0, 0);
        var moduleOnly = new BlockPos(-2, 0, 0);
        var hostPattern = new BlockArray(Map.of(
                hostCoupler, BlockPredicate.machineCoupler(),
                hostOnly, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)));
        var modulePattern = new BlockArray(Map.of(
                moduleCoupler, BlockPredicate.machineCoupler(),
                moduleOnly, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK)));

        var hostSnapshot = MultiblockPreviewBuilder.build(level, controller, hostPattern, 8192);
        var moduleSnapshot = MultiblockPreviewBuilder.build(level, controller, modulePattern, 8192);

        assertEquals(Map.of(
                hostCoupler, ModBlocks.MODULE_BRIDGE.get().defaultBlockState(),
                hostOnly, Blocks.IRON_BLOCK.defaultBlockState()), entriesByPosition(hostSnapshot));
        assertEquals(Map.of(
                moduleCoupler, ModBlocks.MODULE_BRIDGE.get().defaultBlockState(),
                moduleOnly, Blocks.GOLD_BLOCK.defaultBlockState()), entriesByPosition(moduleSnapshot));
    }

    private static Map<BlockPos, BlockState> entriesByPosition(MultiblockPreviewSnapshot snapshot) {
        return snapshot.entries().stream().collect(Collectors.toMap(
                MultiblockPreviewSnapshot.Entry::relativePos, MultiblockPreviewSnapshot.Entry::state));
    }

    private static SingleBlockModifierReplacement modifierReplacement(Block block) {
        return new SingleBlockModifierReplacement(block.getDescriptionId(), new BlockPredicate.OfBlock(block),
                List.of(), new ItemStack(block));
    }
}
