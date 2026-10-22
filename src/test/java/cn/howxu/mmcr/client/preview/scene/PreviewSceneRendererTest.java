package cn.howxu.mmcr.client.preview.scene;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.MachineStructureStage;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.client.preview.PreviewCamera;
import cn.howxu.mmcr.client.preview.PreviewLevel;
import cn.howxu.mmcr.client.preview.PreviewVisibility;
import cn.howxu.mmcr.client.preview.StructurePreviewSchema;
import cn.howxu.mmcr.client.preview.StructurePreviewSchemaFactory;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies screen-coordinate picking against the preview level.
 *
 * @author howxu <dev@howxu.cn>
 */
class PreviewSceneRendererTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void highlights_emit_one_final_color_per_marked_non_air_position_only_when_enabled() {
        PreviewSceneRenderer renderer = highlightedRenderer();
        try {
            assertThat(highlights(renderer)).isEmpty();

            renderer.setPositionHighlightsEnabled(true);

            assertThat(highlights(renderer)).containsExactlyInAnyOrder(
                    Map.entry(BlockPos.ZERO, 0x44FF0000),
                    Map.entry(new BlockPos(1, 0, 0), 0x440000FF),
                    Map.entry(new BlockPos(0, 1, 0), 0x4400FF00),
                    Map.entry(new BlockPos(1, 1, 0), 0x44FF00FF),
                    Map.entry(new BlockPos(2, 1, 0), 0x44FFFFFF),
                    Map.entry(new BlockPos(3, 1, 0), 0x4400FFFF));

            renderer.setPositionHighlightsEnabled(false);
            assertThat(highlights(renderer)).isEmpty();
        } finally {
            renderer.dispose();
        }
    }

    @Test
    void highlight_switches_are_independent_for_scenes_using_the_same_schema() {
        StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(new MachineStructureStage(1,
                new BlockArray(Map.of(BlockPos.ZERO,
                        new BlockPredicate.OfBlock(ModBlocks.BLOCKS.get("item_input_bus").get()))),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY),
                MMCR.id("scene_highlight_independence"));
        var first = new PreviewSceneRenderer(PreviewLevel.create(schema, () -> PreviewVisibility.ALL), schema);
        var second = new PreviewSceneRenderer(PreviewLevel.create(schema, () -> PreviewVisibility.ALL), schema);
        try {
            first.setPositionHighlightsEnabled(true);
            assertThat(highlights(first)).containsExactly(Map.entry(BlockPos.ZERO, 0x44FF0000));
            assertThat(highlights(second)).isEmpty();

            second.setPositionHighlightsEnabled(true);
            first.setPositionHighlightsEnabled(false);
            assertThat(highlights(first)).isEmpty();
            assertThat(highlights(second)).containsExactly(Map.entry(BlockPos.ZERO, 0x44FF0000));
        } finally {
            first.dispose();
            second.dispose();
        }
    }

    @Test
    void highlights_follow_single_layer_visibility_without_toggling_changing_picking() {
        PreviewSceneRenderer renderer = highlightedRenderer();
        try {
            PreviewCamera camera = new PreviewCamera();
            camera.reset(new Vector3f(0.5F, 0.5F, 0.5F), 8.0F, 0.0F, 0.0F);
            PreviewSceneCamera sceneCamera = PreviewSceneCamera.from(camera, 160, 100);
            renderer.setVisibility(PreviewVisibility.singleLayer(0));
            BlockHitResult before = renderer.rayTrace(sceneCamera, 80.0D, 50.0D, 160, 100);
            assertThat(before).isNotNull();
            assertThat(before.getBlockPos()).isEqualTo(BlockPos.ZERO);

            renderer.setPositionHighlightsEnabled(true);
            assertThat(highlights(renderer)).containsExactlyInAnyOrder(
                    Map.entry(BlockPos.ZERO, 0x44FF0000),
                    Map.entry(new BlockPos(1, 0, 0), 0x440000FF));
            assertThat(renderer.rayTrace(sceneCamera, 80.0D, 50.0D, 160, 100).getBlockPos())
                    .isEqualTo(before.getBlockPos());

            renderer.setPositionHighlightsEnabled(false);
            assertThat(highlights(renderer)).isEmpty();
            assertThat(renderer.rayTrace(sceneCamera, 80.0D, 50.0D, 160, 100).getBlockPos())
                    .isEqualTo(before.getBlockPos());

            renderer.setVisibility(PreviewVisibility.singleLayer(1));
            renderer.setPositionHighlightsEnabled(true);
            assertThat(highlights(renderer)).containsExactlyInAnyOrder(
                    Map.entry(new BlockPos(0, 1, 0), 0x4400FF00),
                    Map.entry(new BlockPos(1, 1, 0), 0x44FF00FF),
                    Map.entry(new BlockPos(2, 1, 0), 0x44FFFFFF),
                    Map.entry(new BlockPos(3, 1, 0), 0x4400FFFF));
        } finally {
            renderer.dispose();
        }
    }

    @Test
    void screen_center_ray_hits_the_block_seen_by_the_preview_camera() {
        PreviewSceneRenderer renderer = renderer();

        try {
            BlockHitResult hit = renderer.rayTrace(camera(), 80.0D, 50.0D, 160, 100);

            assertThat(hit).isNotNull();
            assertThat(hit.getBlockPos()).isEqualTo(BlockPos.ZERO);
        } finally {
            renderer.dispose();
        }
    }

    @Test
    void screen_ray_rejects_pointer_outside_the_preview_viewport() {
        PreviewSceneRenderer renderer = renderer();

        try {
            assertThat(renderer.rayTrace(camera(), -1.0D, 50.0D, 160, 100)).isNull();
            assertThat(renderer.rayTrace(camera(), 160.0D, 50.0D, 160, 100)).isNull();
        } finally {
            renderer.dispose();
        }
    }

    @Test
    void screen_ray_ignores_a_block_hidden_by_preview_visibility() {
        PreviewSceneRenderer renderer = renderer();

        try {
            renderer.setVisibility(PreviewVisibility.singleLayer(1));

            assertThat(renderer.rayTrace(camera(), 80.0D, 50.0D, 160, 100)).isNull();
        } finally {
            renderer.dispose();
        }
    }

    private static PreviewSceneRenderer renderer() {
        StructurePreviewSchema schema = new StructurePreviewSchema(
                MMCR.id("preview_ray_test"),
                Map.of(BlockPos.ZERO, Blocks.IRON_BLOCK.defaultBlockState()),
                Map.of());
        PreviewLevel level = PreviewLevel.create(schema, () -> PreviewVisibility.ALL);
        return new PreviewSceneRenderer(level, schema);
    }

    private static List<Map.Entry<BlockPos, Integer>> highlights(PreviewSceneRenderer renderer) {
        List<Map.Entry<BlockPos, Integer>> highlights = new ArrayList<>();
        renderer.forEachPositionHighlight((position, color) -> highlights.add(Map.entry(position, color)));
        return highlights;
    }

    private static PreviewSceneRenderer highlightedRenderer() {
        List<LevelType> previousTypes = MachineLevelRegistry.types();
        List<MachineLevel> previousLevels = previousTypes.stream()
                .flatMap(type -> MachineLevelRegistry.levelsForType(type.id()).stream()).toList();
        ResourceLocation ironType = MMCR.id("scene_highlight_iron");
        ResourceLocation portType = MMCR.id("scene_highlight_port");
        var port = ModBlocks.BLOCKS.get("item_input_bus").get();
        try {
            MachineLevelRegistry.installSnapshot(List.of(
                    new LevelType(ironType, Component.literal("Iron")),
                    new LevelType(portType, Component.literal("Port"))), List.of(
                    new MachineLevel(MMCR.id("scene_highlight_iron_level"), ironType, 0,
                            new BlockPredicate.OfBlockState(Blocks.IRON_BLOCK.defaultBlockState()),
                            ItemStack.EMPTY, ModifierDefinition.EMPTY),
                    new MachineLevel(MMCR.id("scene_highlight_port_level"), portType, 0,
                            new BlockPredicate.OfBlockState(port.defaultBlockState()),
                            ItemStack.EMPTY, ModifierDefinition.EMPTY)));
            BlockArray pattern = new BlockArray(Map.of(
                    BlockPos.ZERO, new BlockPredicate.OfBlock(port),
                    new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                    new BlockPos(0, 1, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                    new BlockPos(1, 1, 0), new BlockPredicate.OfBlock(port),
                    new BlockPos(2, 1, 0), new BlockPredicate.OfBlock(port),
                    new BlockPos(3, 1, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK),
                    new BlockPos(4, 0, 0), new BlockPredicate.OfBlock(Blocks.AIR),
                    new BlockPos(5, 0, 0), new BlockPredicate.Any(),
                    new BlockPos(6, 0, 0), new BlockPredicate.OfBlock(Blocks.IRON_BLOCK)), Map.of(), Map.of(
                    BlockPos.ZERO, 'I', new BlockPos(1, 0, 0), 'M',
                    new BlockPos(0, 1, 0), 'L', new BlockPos(1, 1, 0), 'D',
                    new BlockPos(2, 1, 0), 'T', new BlockPos(3, 1, 0), 'C',
                    new BlockPos(4, 0, 0), 'A', new BlockPos(5, 0, 0), 'U'));
            var replacement = new SingleBlockModifierReplacement(MMCR.id("scene_highlight_modifier"),
                    new BlockPredicate.OfBlock(Blocks.DIAMOND_BLOCK));
            MachineStructureStage stage = new MachineStructureStage(1, pattern, PortRequirementSpec.none(),
                    PortTierRequirementSpec.none(), List.of(), MachineStructureRequirements.builder()
                    .levelSlot('L', ironType).levelSlot('T', portType).levelSlot('C', ironType)
                    .modifier('M', replacement).modifier('D', replacement).modifier('T', replacement)
                    .modifier('C', replacement).modifier('A', replacement).modifier('U', replacement)
                    .build(pattern));
            StructurePreviewSchema schema = new StructurePreviewSchemaFactory().create(stage,
                    MMCR.id("scene_highlight_test"));
            return new PreviewSceneRenderer(PreviewLevel.create(schema, () -> PreviewVisibility.ALL), schema);
        } finally {
            MachineLevelRegistry.installSnapshot(previousTypes, previousLevels);
        }
    }

    private static PreviewSceneCamera camera() {
        PreviewCamera camera = new PreviewCamera();
        camera.reset(new Vector3f(0.5F, 0.5F, 0.5F), 8.0F);
        return PreviewSceneCamera.from(camera, 160, 100);
    }
}
