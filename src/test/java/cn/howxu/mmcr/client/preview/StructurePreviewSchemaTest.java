package cn.howxu.mmcr.client.preview;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies immutable structure-preview schema snapshots.
 *
 * @author howxu <dev@howxu.cn>
 */
class StructurePreviewSchemaTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void schema_copies_positions_calculates_bounds_and_sorts_layers() {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        BlockPos source = new BlockPos(2, 3, -1);
        states.put(source, Blocks.IRON_BLOCK.defaultBlockState());
        states.put(new BlockPos(-2, 1, 4), Blocks.GOLD_BLOCK.defaultBlockState());

        StructurePreviewSchema schema = new StructurePreviewSchema(MMCR.id("preview_test"), states, Map.of());
        states.clear();

        assertThat(schema.states()).hasSize(2);
        assertThat(schema.stateAt(source)).isEqualTo(Blocks.IRON_BLOCK.defaultBlockState());
        assertThat(schema.min()).isEqualTo(new BlockPos(-2, 1, -1));
        assertThat(schema.max()).isEqualTo(new BlockPos(2, 3, 4));
        assertThat(schema.layers()).containsExactly(1, 3);
        assertThat(schema.center()).containsExactly(0.5F, 2.0F, 2.0F);
        assertThatThrownBy(() -> schema.states().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void schema_rejects_level_slots_outside_its_states() {
        BlockPos statePosition = BlockPos.ZERO;
        BlockPos levelSlotPosition = new BlockPos(1, 0, 0);

        assertThatThrownBy(() -> new StructurePreviewSchema(MMCR.id("preview_test"),
                Map.of(statePosition, Blocks.IRON_BLOCK.defaultBlockState()),
                Map.of(levelSlotPosition, Identifier.fromNamespaceAndPath("mmcr", "coil"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("level slot position is not in preview schema");
    }

    @Test
    void schema_copies_mutable_positions_and_level_slots() {
        BlockPos.MutableBlockPos mutablePosition = new BlockPos.MutableBlockPos(2, 3, 4);
        BlockState state = Blocks.IRON_BLOCK.defaultBlockState();
        Identifier slot = MMCR.id("coil");
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        Map<BlockPos, Identifier> levelSlots = new LinkedHashMap<>();
        states.put(mutablePosition, state);
        levelSlots.put(mutablePosition, slot);

        StructurePreviewSchema schema = new StructurePreviewSchema(MMCR.id("mutable_preview"), states, levelSlots);
        mutablePosition.set(10, 11, 12);
        levelSlots.clear();

        assertThat(schema.states()).containsOnlyKeys(new BlockPos(2, 3, 4));
        assertThat(schema.stateAt(new BlockPos(2, 3, 4))).isEqualTo(state);
        assertThat(schema.levelSlots()).containsEntry(new BlockPos(2, 3, 4), slot);
        assertThat(schema.levelSlotAt(new BlockPos(2, 3, 4))).isEqualTo(slot);
        assertThat(schema.min()).isEqualTo(new BlockPos(2, 3, 4));
        assertThat(schema.max()).isEqualTo(new BlockPos(2, 3, 4));
        assertThatThrownBy(() -> schema.levelSlots().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void empty_schema_uses_default_bounds_center_and_layers() {
        StructurePreviewSchema schema = new StructurePreviewSchema(MMCR.id("empty_preview"), Map.of(), Map.of());

        assertThat(schema.min()).isEqualTo(BlockPos.ZERO);
        assertThat(schema.max()).isEqualTo(BlockPos.ZERO);
        assertThat(schema.center()).containsExactly(0.5F, 0.5F, 0.5F);
        assertThat(schema.layers()).isEmpty();
    }

    @Test
    void schema_resolves_candidates_only_for_the_requested_position() {
        AtomicInteger resolutions = new AtomicInteger();
        BlockPos first = BlockPos.ZERO;
        BlockPos second = new BlockPos(1, 0, 0);
        StructurePreviewSchema schema = StructurePreviewSchema.withCandidateResolver(MMCR.id("lazy_candidates"),
                Map.of(first, Blocks.IRON_BLOCK.defaultBlockState(), second, Blocks.GOLD_BLOCK.defaultBlockState()), Map.of(),
                position -> {
                    resolutions.incrementAndGet();
                    return List.of(new StructurePreviewSchema.Candidate(new ItemStack(
                            position.equals(first) ? Blocks.IRON_BLOCK : Blocks.GOLD_BLOCK), false));
                });

        assertThat(resolutions).hasValue(0);
        assertThat(schema.candidatesAt(first)).extracting(ItemStack::getItem).containsExactly(Blocks.IRON_BLOCK.asItem());
        assertThat(schema.candidatesAt(first)).extracting(ItemStack::getItem).containsExactly(Blocks.IRON_BLOCK.asItem());
        assertThat(schema.candidatesAt(second)).extracting(ItemStack::getItem).containsExactly(Blocks.GOLD_BLOCK.asItem());
        assertThat(resolutions).hasValue(2);
    }
}
