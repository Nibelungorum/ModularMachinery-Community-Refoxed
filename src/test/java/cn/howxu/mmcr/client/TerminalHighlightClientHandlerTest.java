package cn.howxu.mmcr.client;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import net.minecraft.world.item.ItemStack;
import cn.howxu.mmcr.client.preview.PreviewHighlights;
import cn.howxu.mmcr.client.preview.StructurePreviewSchemaFactory;
import cn.howxu.mmcr.internal.item.TerminalData;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies stage-derived projection without inspecting the world's blocks.
 *
 * @author howxu <dev@howxu.cn>
 */
class TerminalHighlightClientHandlerTest {
    private static final Identifier ID = Identifier.parse("test:terminal_world_highlights");
    private static final BlockPos ORIGIN = new BlockPos(20, 64, 30);
    private static Machine machine;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        BlockPos raw = new BlockPos(1, 2, 3);
        BlockArray first = new BlockArray(Map.of(raw, new BlockPredicate.OfBlock(Blocks.STONE)),
                Map.of(), Map.of(raw, 'M'));
        MachineStructureStage stageOne = new MachineStructureStage(1, first, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                        .modifier('M', new SingleBlockModifierReplacement("terminal_highlight",
                                new BlockPredicate.OfBlock(ModBlocks.SMART_INTERFACE.get()), List.of(), ItemStack.EMPTY))
                        .build(first));
        BlockArray second = new BlockArray(Map.of(new BlockPos(0, -1, 2),
                new BlockPredicate.OfBlock(ModBlocks.SMART_INTERFACE.get())));
        MachineStructureStage stageTwo = new MachineStructureStage(2, second, PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);
        machine = new Machine() {
            @Override public Identifier registryName() { return ID; }
            @Override public BlockArray pattern() { return first; }
            @Override public MachineControllerSpec controller() { return MachineControllerSpec.defaultsFor(ID); }
            @Override public List<MachineStructureStage> structureStages() { return List.of(stageOne, stageTwo); }
        };
        MachineRegistry.register(machine);
    }

    @AfterEach
    void clear() {
        TerminalHighlightClientHandler.clear();
    }

    @Test
    void projects_rotated_schema_colors_even_with_ghost_preview_disabled() {
        for (Direction facing : List.of(Direction.SOUTH, Direction.EAST, Direction.UP)) {
            TerminalHighlightClientHandler.applyState(data(), ID, facing);
            Map<BlockPos, Integer> expected = new LinkedHashMap<>();
            new StructurePreviewSchemaFactory().create(machine.structureStages().getFirst(), ID, facing)
                    .highlights().forEach((pos, mask) -> expected.put(ORIGIN.offset(pos), PreviewHighlights.color(mask)));

            assertThat(visible()).containsExactlyInAnyOrderEntriesOf(expected);
            assertThat(visible().values()).containsOnly(0x44FF00FF);
        }
    }

    @Test
    void stage_and_layer_changes_replace_positions_instead_of_leaving_stale_highlights() {
        TerminalHighlightClientHandler.applyState(data(), ID, Direction.SOUTH);
        assertThat(visible()).containsKey(ORIGIN.offset(1, 2, 3));
        TerminalHighlightClientHandler.applyState(data().withStage(2), ID, Direction.SOUTH);
        assertThat(visible()).containsOnly(Map.entry(ORIGIN.offset(0, -1, 2), 0x44FF0000));
        TerminalHighlightClientHandler.applyState(data().withStage(2).withPreview(false, 2), ID, Direction.SOUTH);
        assertThat(visible()).isEmpty();
        TerminalHighlightClientHandler.applyState(data().withStage(2).withPreview(false, -1), ID, Direction.SOUTH);
        assertThat(visible()).containsKey(ORIGIN.offset(0, -1, 2));
    }

    @Test
    void disabled_unavailable_and_invalid_stage_states_clear_projection() {
        for (TerminalData state : List.of(data().withHighlight(false), data().clear(), data().withStage(99))) {
            TerminalHighlightClientHandler.applyState(data(), ID, Direction.SOUTH);
            TerminalHighlightClientHandler.applyState(state, ID, Direction.SOUTH);
            assertThat(visible()).isEmpty();
        }
        TerminalHighlightClientHandler.applyState(data(), ID, Direction.SOUTH);
        TerminalHighlightClientHandler.applyState(data(), null, Direction.SOUTH);
        assertThat(visible()).isEmpty();
    }

    @Test
    void radius_filter_and_world_unload_clear_do_not_leave_positions() {
        TerminalHighlightClientHandler.applyState(data(), ID, Direction.SOUTH);
        Map<BlockPos, Integer> visible = new LinkedHashMap<>();
        TerminalHighlightClientHandler.forEachVisibleHighlight(Vec3.ZERO, 1, visible::put);
        assertThat(visible).isEmpty();
        TerminalHighlightClientHandler.clear();
        assertThat(visible()).isEmpty();
    }

    private static TerminalData data() {
        return TerminalData.DEFAULT.withController(GlobalPos.of(Level.OVERWORLD, ORIGIN)).withHighlight(true);
    }

    private static Map<BlockPos, Integer> visible() {
        Map<BlockPos, Integer> result = new LinkedHashMap<>();
        TerminalHighlightClientHandler.forEachVisibleHighlight(ORIGIN.getCenter(), 128, result::put);
        return result;
    }
}
