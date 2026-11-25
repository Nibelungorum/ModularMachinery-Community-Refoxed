package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.render.DynamicAtlasAllocator;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicOverlayItemModelTest {
    @AfterEach
    void clearAppearanceCache() {
        MachineAppearanceCache.replaceSnapshot(Map.of());
    }

    @Test
    void controller_items_display_the_idle_state_overlay() {
        var machineId = MMCR.id("stateful_controller");
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId, new MachineAppearanceSpec(
                MMCR.id("basic_casing"), null, null,
                MMCR.id("block/custom_idle"), MMCR.id("block/custom_active"))));

        var description = DynamicOverlayItemModel.Description.controller(machineId);

        assertThat(description.stateOverlayTexture()).isEqualTo(MMCR.id("block/custom_idle"));
    }

    @ParameterizedTest
    @CsvSource({"false, false, false", "true, false, false", "false, true, false", "false, false, true"})
    void gui_cache_refreshes_when_any_item_layer_is_animated(boolean animatedBase, boolean animatedOverlay,
                                                           boolean animatedStateOverlay) throws Exception {
        TestBootstrap.bootstrap();
        var machineId = MMCR.id("test_cube");
        var baseTexture = MMCR.id("block/test_item_base");
        var defaults = MachineAppearanceSpec.defaults();
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId, new MachineAppearanceSpec(
                defaults.machineBasicBlock(), baseTexture, null)));
        var description = DynamicOverlayItemModel.Description.controller(machineId);

        try (var staticSprite = sprite(false); var animatedSprite = sprite(true)) {
            var model = new DynamicOverlayItemModel(Map.of(description.baseModel(),
                    new ModelRenderProperties(true, new Material.Baked(staticSprite, false), ItemTransforms.NO_TRANSFORMS)),
                    texture -> {
                        boolean animated = texture.equals(baseTexture) ? animatedBase
                                : texture.equals(description.stateOverlayTexture()) ? animatedStateOverlay
                                : animatedOverlay;
                        return new Material.Baked(animated ? animatedSprite : staticSprite, false);
                    }, new Matrix4f());
            var renderState = new ItemStackRenderState();
            model.update(renderState, new ItemStack(ModBlocks.controllerFor(machineId).get().asItem()),
                    null, ItemDisplayContext.GUI, null, null, 0);

            boolean animated = animatedBase || animatedOverlay || animatedStateOverlay;
            assertThat(renderState.isAnimated()).isEqualTo(animated);
            var allocator = new DynamicAtlasAllocator<Object>(1, 1);
            allocator.getOrAllocate(description, renderState.isAnimated());
            allocator.endFrame();
            assertThat(allocator.getOrAllocate(description, renderState.isAnimated()).state())
                    .isEqualTo(animated ? DynamicAtlasAllocator.SlotState.STALE : DynamicAtlasAllocator.SlotState.READY);
        }
    }

    @ParameterizedTest
    @EnumSource(value = ItemDisplayContext.class, names = {"GUI", "FIRST_PERSON_RIGHT_HAND", "THIRD_PERSON_LEFT_HAND"})
    void runtime_item_quads_follow_the_reloaded_atlas(ItemDisplayContext context) throws Exception {
        TestBootstrap.bootstrap();
        var machineId = MMCR.id("test_cube");
        var base = MMCR.id("block/test_runtime_item_base");
        var idle = MMCR.id("block/custom_idle");
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId,
                new MachineAppearanceSpec(MMCR.id("basic_casing"), base, null, idle, null)));
        var description = DynamicOverlayItemModel.Description.controller(machineId);
        Map<Identifier, TextureAtlasSprite> sprites = new HashMap<>();
        try (var particle = sprite(false)) {
            var model = new DynamicOverlayItemModel(Map.of(description.baseModel(),
                    new ModelRenderProperties(true, new Material.Baked(particle, false), ItemTransforms.NO_TRANSFORMS)),
                    texture -> new Material.Baked(sprites.computeIfAbsent(texture, id -> sprite(false)), false), new Matrix4f());
            var stack = new ItemStack(ModBlocks.controllerFor(machineId).get().asItem());
            var renderState = new ItemStackRenderState();
            model.update(renderState, stack, null, context, null, null, 0);
            var originalSprites = List.copyOf(sprites.values());
            assertThat(renderState.isAnimated()).isFalse();

            sprites.replaceAll((texture, oldSprite) -> {
                oldSprite.close();
                return sprite(true);
            });
            renderState.clear();
            model.update(renderState, stack, null, context, null, null, 0);

            var quads = submittedQuads(renderState);
            for (var face : Direction.values()) {
                var faceQuads = quads.stream().filter(q -> q.direction() == face).toList();
                var expected = face == Direction.NORTH
                        ? List.of(sprites.get(base), sprites.get(description.overlayTextures().getFirst()), sprites.get(idle))
                        : List.of(sprites.get(base));
                assertThat(faceQuads).extracting(q -> q.materialInfo().sprite())
                        .containsExactlyElementsOf(expected).doesNotContainAnyElementsOf(originalSprites);
                for (var quad : faceQuads) {
                    for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                        assertThat(quad.position(i)).isEqualTo(faceQuads.getFirst().position(i));
                    }
                }
            }
            assertThat(renderState.usesBlockLight()).isTrue();
            assertThat(renderState.pickParticleMaterial(RandomSource.create(0L)).sprite()).isSameAs(particle);
            assertThat(renderState.isAnimated()).isTrue();
        } finally {
            sprites.values().forEach(TextureAtlasSprite::close);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void port_item_faces_match_world_geometry_and_translucent_layer_order(boolean forceTranslucent) throws Exception {
        TestBootstrap.bootstrap();
        var block = ModBlocks.BLOCKS.get("item_input_bus_ludicrous").get();
        var stack = new ItemStack(block.asItem());
        var description = DynamicOverlayItemModel.describe(stack);
        var base = DynamicOverlayBakedModel.resolveBase(description.baseTextureSource()).forFace(Direction.NORTH);

        try (var casing = sprite(0, 0xffffffff); var panel = sprite(2, 0);
             var arrow = sprite(4, forceTranslucent ? 0 : 0x80ffffff);
             var icon = sprite(6, 0); var tier = sprite(8, 0)) {
            var baseMaterial = new Material.Baked(casing, false);
            var materials = Map.of(base, baseMaterial,
                    MMCR.id("block/overlay/base/item"), new Material.Baked(panel, false),
                    MMCR.id("block/overlay/direction/input"), new Material.Baked(arrow, forceTranslucent),
                    MMCR.id("block/overlay/type/item"), new Material.Baked(icon, false),
                    MMCR.id("block/overlay/tier/ludicrous"), new Material.Baked(tier, false));
            var world = new DynamicOverlayModelLoader(DynamicOverlayBakedModel.Kind.PORT, baseMaterial, materials::get);
            var parts = new ArrayList<BlockStateModelPart>();
            world.collectParts(BlockAndTintGetter.EMPTY, BlockPos.ZERO, block.defaultBlockState(), RandomSource.create(0L), parts);

            var model = new DynamicOverlayItemModel(Map.of(description.baseModel(),
                    new ModelRenderProperties(true, baseMaterial, ItemTransforms.NO_TRANSFORMS)), materials::get, new Matrix4f());
            var state = new ItemStackRenderState();
            model.update(state, stack, null, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, null, null, 0);
            var quads = submittedQuads(state);
            for (var face : Direction.values()) {
                var itemFace = quads.stream().filter(q -> q.direction() == face).toList();
                var worldFace = parts.getFirst().getQuads(face);
                assertThat(quads).as("contiguous layers for %s", face).containsSequence(itemFace);
                assertThat(itemFace).hasSameSizeAs(worldFace).extracting(q -> q.materialInfo().sprite())
                        .containsExactly(casing, panel, arrow, icon, tier);
                assertThat(itemFace).extracting(q -> q.materialInfo().itemRenderType().hasBlending())
                        .containsExactly(false, false, true, true, true);
                for (int j = 0; j < itemFace.size(); j++) {
                    var itemQuad = itemFace.get(j);
                    var worldQuad = worldFace.get(j);
                    assertThat(itemQuad.materialInfo()).isEqualTo(worldQuad.materialInfo());
                    for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                        assertThat(itemQuad.position(i)).isEqualTo(itemFace.getFirst().position(i));
                        assertThat(itemQuad.position(i)).isEqualTo(worldQuad.position(i));
                        assertThat(itemQuad.packedUV(i)).isEqualTo(worldQuad.packedUV(i));
                    }
                }
            }
        }
    }

    private static List<BakedQuad> submittedQuads(ItemStackRenderState state) {
        var nodes = new SubmitNodeStorage();
        state.submit(new PoseStack(), nodes, 0, 0, 0);
        var submits = nodes.order(0).getItemSubmits();
        assertThat(submits).hasSize(1);
        return List.copyOf(submits.getFirst().quads());
    }

    private static TextureAtlasSprite sprite(int offset, int color) {
        var image = new NativeImage(2, 1, false);
        image.setPixel(0, 0, 0xffffffff);
        image.setPixel(1, 0, color);
        var contents = new SpriteContents(MMCR.id("block/test_face_" + offset), new FrameSize(2, 1), image);
        return new TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, contents, 16, 16, offset, offset, 0) {};
    }

    private static TextureAtlasSprite sprite(boolean animated) {
        NativeImage image = new NativeImage(1, animated ? 2 : 1, true);
        var animation = new AnimationMetadataSection(Optional.empty(), Optional.empty(), Optional.empty(), 1, false);
        var contents = new SpriteContents(MMCR.id(animated ? "block/test_animated" : "block/test_static"),
                new FrameSize(1, 1), image, animated ? Optional.of(animation) : Optional.empty(), List.of(), Optional.empty());
        return new TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, contents, 1, 1, 0, 0, 0) {};
    }
}
