package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.compat.create.loaded.StressInterfaceKind;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworkOverlay;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInterfaceKind;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.joml.Vector3f;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DynamicOverlayModelLoaderTest {
    private static final BlockPos POS = BlockPos.ZERO;
    private final Map<ResourceLocation, TextureAtlasSprite> sprites = new HashMap<>();

    @AfterEach
    void closeSprites() {
        sprites.values().forEach(sprite -> sprite.contents().close());
        MachineAppearanceCache.replaceSnapshot(Map.of());
        ControllerSpecCache.replaceSnapshot(Map.of());
    }

    private BakedModel bake(DynamicOverlayBakedModel.Kind kind, ResourceLocation itemBlockId) {
        var description = itemBlockId == null ? null
                : DynamicOverlayItemModel.describeBlock(BuiltInRegistries.BLOCK.get(itemBlockId));
        return new DynamicOverlayModelLoader.DynamicModel(kind, description,
                sprites.computeIfAbsent(MMCR.id("block/ctm/basic_casing/particle"), TestSprite::new),
                texture -> sprites.computeIfAbsent(texture, TestSprite::new),
                ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY, null);
    }

    @ParameterizedTest
    @CsvSource({
            "UP, NORTH, 0, 0, 1, 0, 0, 1",
            "UP, SOUTH, 1, 1, 0, 1, 1, 0",
            "UP, EAST, 1, 0, 1, 1, 0, 0",
            "UP, WEST, 0, 1, 0, 0, 1, 1",
            "DOWN, NORTH, 1, 0, 0, 0, 1, 1",
            "DOWN, SOUTH, 0, 1, 1, 1, 0, 0",
            "DOWN, EAST, 1, 1, 1, 0, 0, 1",
            "DOWN, WEST, 0, 0, 0, 1, 1, 0"
    })
    void vertical_overlay_uses_roll_as_up_without_mirroring(Direction face, Direction up,
                                                          float leftX, float leftZ, float rightX, float rightZ,
                                                          float bottomX, float bottomZ) {
        assertThat(DynamicOverlayModelLoader.uv(face, up, new Vector3f(leftX, 0, leftZ))).containsExactly(0, 0);
        assertThat(DynamicOverlayModelLoader.uv(face, up, new Vector3f(rightX, 0, rightZ))).containsExactly(1, 0);
        assertThat(DynamicOverlayModelLoader.uv(face, up, new Vector3f(bottomX, 0, bottomZ))).containsExactly(0, 1);
    }

    @ParameterizedTest
    @EnumSource(DynamicOverlayBakedModel.Kind.class)
    void destruction_particle_material_loads_the_bundled_ctm_texture(DynamicOverlayBakedModel.Kind kind) {
        IGeometryBakingContext context = (IGeometryBakingContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IGeometryBakingContext.class},
                (proxy, method, args) -> method.getName().equals("getTransforms") ? ItemTransforms.NO_TRANSFORMS : null);
        BakedModel model = new DynamicOverlayModelLoader.Unbaked(kind, null).bake(context, null, material -> {
            ResourceLocation id = material.texture();
            assertThat(id).isEqualTo(MMCR.id("block/ctm/basic_casing/particle"));
            try (var resource = getClass().getResourceAsStream(
                    "/assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png")) {
                assertThat(resource).as("Bundled particle texture %s", id).isNotNull();
                NativeImage image = NativeImage.read(resource);
                var contents = new SpriteContents(id, new FrameSize(image.getWidth(), image.getHeight()),
                        image, ResourceMetadata.EMPTY);
                var sprite = new TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, contents,
                        image.getWidth(), image.getHeight(), 0, 0) {};
                sprites.put(id, sprite);
                return sprite;
            } catch (IOException exception) {
                throw new AssertionError("Unable to load particle texture " + id, exception);
            }
        }, null, ItemOverrides.EMPTY);

        assertThat(model.getParticleIcon()).isSameAs(sprites.get(MMCR.id("block/ctm/basic_casing/particle")));
    }

    @Test
    void port_base_is_solid_and_binary_alpha_overlays_are_cutout() {
        BakedModel model = bake(DynamicOverlayBakedModel.Kind.PORT, null);
        var state = Blocks.IRON_BLOCK.defaultBlockState();
        var base = MMCR.id("block/formed_base");
        ModelData data = ModelData.of(MachineModelDataKeys.PORT_TEXTURE_SOURCE,
                new MachineAppearanceSpec.TextureSource(MMCR.id("basic_casing"), base));
        RandomSource random = RandomSource.create(0L);

        assertThat(model.getRenderTypes(state, random, data).asList())
                .containsExactly(RenderType.solid(), RenderType.cutout(), RenderType.translucent());
        for (var direction : Direction.values()) {
            assertThat(model.getQuads(state, direction, random, data, RenderType.solid()))
                    .extracting(quad -> quad.getSprite().contents().name()).containsExactly(base);
            assertThat(model.getQuads(state, direction, random, data, RenderType.cutout()))
                    .extracting(quad -> quad.getSprite().contents().name()).containsExactly(DynamicOverlayBakedModel.defaultPortOverlayTexture());
            assertThat(model.getQuads(state, direction, random, data, RenderType.translucent())).isEmpty();
        }
        assertThat(model.getQuads(state, null, random, data, null)).isEmpty();
    }

    @Test
    void only_controller_front_changes_when_active_and_vertical_roll_is_retained() {
        var machineId = MMCR.id("test_cube");
        var base = MMCR.id("block/controller_base");
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId,
                new MachineAppearanceSpec(MMCR.id("basic_casing"), base, null)));
        MachineControllerBlock block = (MachineControllerBlock) ModBlocks.controllerFor(machineId).get();
        var state = block.defaultBlockState().setValue(MachineControllerBlock.FACING, Direction.UP)
                .setValue(MachineControllerBlock.ROLL_FACING, Direction.EAST).setValue(MachineControllerBlock.FORMED, true);
        BakedModel model = bake(DynamicOverlayBakedModel.Kind.CONTROLLER, null);
        var random = RandomSource.create(0L);
        for (boolean running : List.of(false, true)) {
            var current = state.setValue(MachineControllerBlock.ACTIVE, running);
            var data = model.getModelData(null, POS, current, ModelData.EMPTY);
            assertThat(model.getQuads(current, null, random, data, null)).isEmpty();
            for (var face : Direction.values()) {
                var quads = model.getQuads(current, face, random, data, null);
                if (face != Direction.UP) {
                    assertThat(quads).extracting(q -> q.getSprite().contents().name()).containsExactly(base);
                } else {
                    assertThat(quads).extracting(q -> q.getSprite().contents().name()).containsExactly(base,
                            ControllerSpecCache.specFor(machineId).frontTexture(), MMCR.id(running ? "block/overlay_basic_active" : "block/overlay_basic_idle"));
                    for (var quad : quads) for (int i = 0; i < 4; i++) {
                        var point = position(quad, i);
                        assertThat(point).isEqualTo(position(quads.getFirst(), i));
                        float u = quad == quads.getFirst() ? point.x : point.z;
                        float v = quad == quads.getFirst() ? point.z : 1F - point.x;
                        assertUv(quad, i, u, v);
                    }
                }
            }
        }
    }

    @Test
    void runtime_block_and_item_quads_follow_the_reloaded_atlas() {
        var machineId = MMCR.id("test_cube");
        var base = ResourceLocation.parse("mekanism:block/sps_casing");
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId,
                new MachineAppearanceSpec(MMCR.id("basic_casing"), base, null)));
        MachineControllerBlock block = (MachineControllerBlock) ModBlocks.controllerFor(machineId).get();
        var state = block.defaultBlockState();
        var model = bake(DynamicOverlayBakedModel.Kind.CONTROLLER, BuiltInRegistries.BLOCK.getKey(block));
        var random = RandomSource.create(0L);
        var originalBase = model.getQuads(state, Direction.DOWN, random, ModelData.EMPTY, RenderType.solid())
                .getFirst().getSprite();
        var front = state.getValue(MachineControllerBlock.FACING);
        var originalOverlays = model.getQuads(state, front, random, ModelData.EMPTY, RenderType.cutout())
                .stream().map(BakedQuad::getSprite).toList();
        assertThat(originalOverlays).hasSize(2);
        var passes = model.getRenderPasses(ItemStack.EMPTY, false);
        passes.forEach(pass -> pass.getQuads(null, null, random));

        // Atlas upload closes the old sprites and installs replacements, even for the same texture IDs.
        sprites.replaceAll((texture, sprite) -> {
            sprite.contents().close();
            return new TestSprite(texture, 128);
        });

        assertThat(model.getQuads(state, Direction.DOWN, random, ModelData.EMPTY, RenderType.solid()))
                .singleElement().satisfies(quad -> assertThat(quad.getSprite())
                        .isSameAs(sprites.get(base)).isNotSameAs(originalBase));
        assertThat(model.getQuads(state, front, random, ModelData.EMPTY, RenderType.translucent()))
                .extracting(BakedQuad::getSprite)
                .containsExactly(sprites.get(ControllerSpecCache.specFor(machineId).frontTexture()),
                        sprites.get(MMCR.id("block/overlay_basic_idle")))
                 .doesNotContainAnyElementsOf(originalOverlays);
        assertThat(model.getQuads(state, front, random, ModelData.EMPTY, RenderType.cutout())).isEmpty();
        assertThat(passes.get(1).getQuads(null, null, random)).isEmpty();
        assertThat(passes.get(2).getQuads(null, null, random)).hasSize(2);
        for (var pass : passes) {
            assertThat(pass.getQuads(null, null, random))
                    .allSatisfy(quad -> assertThat(quad.getSprite())
                            .isSameAs(sprites.get(quad.getSprite().contents().name())));
        }
    }

    @Test
    void port_layers_keep_native_animations_and_pass_order_across_atlas_replacement() {
        var base = MMCR.id("block/runtime_base");
        var panel = MMCR.id("block/overlay/base/item");
        var arrows = MMCR.id("block/overlay/direction/input");
        var icon = MMCR.id("block/overlay/type/item");
        var tier = MMCR.id("block/overlay/tier/ludicrous");
        var state = ModBlocks.BLOCKS.get("item_input_bus_ludicrous").get().defaultBlockState();
        var model = bake(DynamicOverlayBakedModel.Kind.PORT, null);
        var input = ModelData.of(MachineModelDataKeys.PORT_TEXTURE_SOURCE, new MachineAppearanceSpec.TextureSource(MMCR.id("basic_casing"), base));
        var random = RandomSource.create(1);
        sprites.put(tier, animated(tier, 3, false, 0));
        for (boolean reload : List.of(false, true)) {
            var previous = sprites.put(arrows, animated(arrows, reload ? 5 : 2, !reload, reload ? 0 : 128));
            if (previous != null) previous.contents().close();
            var data = model.getModelData(null, POS, state, input);
            assertThat(model.getQuads(state, null, random, data, null)).isEmpty();
            for (var face : Direction.values()) {
                var quads = model.getQuads(state, face, random, data, null);
                assertThat(quads).extracting(BakedQuad::getSprite)
                        .containsExactly(sprites.get(base), sprites.get(panel), sprites.get(arrows), sprites.get(icon), sprites.get(tier));
                assertThat(model.getQuads(state, face, random, data, RenderType.cutout())).extracting(BakedQuad::getSprite)
                        .containsExactlyElementsOf(reload ? List.of(sprites.get(panel), sprites.get(arrows), sprites.get(icon), sprites.get(tier))
                                : List.of(sprites.get(panel)));
                assertThat(model.getQuads(state, face, random, data, RenderType.translucent())).extracting(BakedQuad::getSprite)
                        .containsExactlyElementsOf(reload ? List.of() : List.of(sprites.get(arrows), sprites.get(icon), sprites.get(tier)));
                for (var quad : quads) for (int i = 0; i < 4; i++) {
                    var point = position(quad, i);
                    assertThat(point).isEqualTo(position(quads.getFirst(), i));
                    float[] uv = DynamicOverlayModelLoader.uv(face, Direction.NORTH, point);
                    assertUv(quad, i, uv[0], uv[1]);
                }
            }
            assertThat(sprites.get(arrows).contents().getUniqueFrames().toArray()).containsExactly(0, 1);
        }
    }

    private static TextureAtlasSprite animated(ResourceLocation id, int time, boolean interpolate, int nextAlpha) {
        var image = new NativeImage(1, 2, false);
        image.setPixelRGBA(0, 0, -1);
        image.setPixelRGBA(0, 1, nextAlpha << 24 | 0x00ffffff);
        var metadata = new ResourceMetadata.Builder().put(AnimationMetadataSection.SERIALIZER,
                new AnimationMetadataSection(List.of(), 1, 1, time, interpolate)).build();
        var contents = new SpriteContents(id, new FrameSize(1, 1), image, metadata);
        return new TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, contents, 32, 32, time, time) {};
    }

    @Test
    void item_passes_separate_cube_base_from_overlays_and_keep_uv_inside_sprites() {
        var machineId = MMCR.id("test_cube");
        var blockId = BuiltInRegistries.BLOCK.getKey(ModBlocks.controllerFor(machineId).get());
        var model = bake(DynamicOverlayBakedModel.Kind.CONTROLLER, blockId);
        var passes = model.getRenderPasses(ItemStack.EMPTY, false);
        var random = RandomSource.create(0L);

        assertThat(passes).hasSize(3);
        assertThat(passes.get(0).getRenderTypes(ItemStack.EMPTY, false))
                .containsExactly(NeoForgeRenderTypes.ITEM_LAYERED_SOLID.get());
        assertThat(passes.get(1).getRenderTypes(ItemStack.EMPTY, false))
                .containsExactly(NeoForgeRenderTypes.ITEM_LAYERED_CUTOUT.get());
        assertThat(passes.get(2).getRenderTypes(ItemStack.EMPTY, false))
                .containsExactly(NeoForgeRenderTypes.ITEM_UNSORTED_TRANSLUCENT.get());
        assertThat(passes.get(0).getQuads(null, null, random)).hasSize(6)
                .allSatisfy(quad -> assertThat(quad.getSprite().contents().name()).isEqualTo(MMCR.id("block/ctm/basic_casing/particle")));
        assertThat(passes.get(1).getQuads(null, null, random)).hasSize(2)
                .allSatisfy(DynamicOverlayModelLoaderTest::assertUvInsideSprite);
        assertThat(passes.get(2).getQuads(null, null, random)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"255, solid", "0, cutout", "128, translucent"})
    void block_and_item_overlays_select_the_layer_from_sprite_alpha(int alpha, String layerName) {
        var machineId = MMCR.id("test_cube");
        var front = ControllerSpecCache.specFor(machineId).frontTexture();
        var idle = MMCR.id("block/overlay_basic_idle");
        sprites.put(front, new TestSprite(front, alpha));
        sprites.put(idle, new TestSprite(idle, alpha));
        var block = ModBlocks.controllerFor(machineId).get();
        var state = block.defaultBlockState();
        var model = bake(DynamicOverlayBakedModel.Kind.CONTROLLER, BuiltInRegistries.BLOCK.getKey(block));
        var random = RandomSource.create(0L);
        var expectedLayer = switch (layerName) {
            case "solid" -> RenderType.solid();
            case "cutout" -> RenderType.cutout();
            default -> RenderType.translucent();
        };
        var layers = List.of(RenderType.solid(), RenderType.cutout(), RenderType.translucent());
        var passes = model.getRenderPasses(ItemStack.EMPTY, false);
        for (int index = 0; index < layers.size(); index++) {
            var layer = layers.get(index);
            var blockQuads = model.getQuads(state, state.getValue(MachineControllerBlock.FACING), random, ModelData.EMPTY, layer)
                    .stream().filter(q -> q.getSprite() == sprites.get(front) || q.getSprite() == sprites.get(idle)).toList();
            var itemQuads = passes.get(index).getQuads(null, null, random);
            if (layer == expectedLayer) {
                assertThat(blockQuads).extracting(quad -> quad.getSprite().contents().name())
                        .containsExactly(front, idle);
                assertThat(itemQuads).extracting(quad -> quad.getSprite().contents().name())
                        .contains(front, idle);
            } else {
                assertThat(blockQuads).isEmpty();
                assertThat(itemQuads).extracting(quad -> quad.getSprite().contents().name())
                        .doesNotContain(front, idle);
            }
        }
    }

    @Test
    void ctm_proxy_keeps_weighted_base_selection_and_filters_other_layers() {
        var state = Blocks.IRON_BLOCK.defaultBlockState();
        BakedModel model = bake(DynamicOverlayBakedModel.Kind.PORT, null);
        BakedQuad first = model.getQuads(state, Direction.NORTH, RandomSource.create(0L),
                ModelData.of(MachineModelDataKeys.PORT_TEXTURE_SOURCE,
                        new MachineAppearanceSpec.TextureSource(MMCR.id("basic_casing"), MMCR.id("block/first"))),
                RenderType.solid()).getFirst();
        BakedQuad second = model.getQuads(state, Direction.NORTH, RandomSource.create(0L),
                ModelData.of(MachineModelDataKeys.PORT_TEXTURE_SOURCE,
                        new MachineAppearanceSpec.TextureSource(MMCR.id("basic_casing"), MMCR.id("block/second"))),
                RenderType.solid()).getFirst();
        BakedModel source = new WeightedBakedModel.Builder()
                .add(sourceModel(first, Direction.NORTH, RenderType.solid()), 1)
                .add(sourceModel(second, Direction.NORTH, RenderType.cutout()), 1).build();

        for (long seed = 0; seed < 32; seed++) {
            var sourceLayers = source.getRenderTypes(state, RandomSource.create(seed), ModelData.EMPTY);
            ModelData data = DynamicOverlayModelLoader.DynamicModel.withCtmSource(source, null, POS, state, ModelData.EMPTY, seed);
            BakedQuad chosen = source.getQuads(state, Direction.NORTH, RandomSource.create(seed), ModelData.EMPTY, null).getFirst();
            assertThat(model.getRenderTypes(state, RandomSource.create(seed), data).asList())
                    .containsExactlyElementsOf(ChunkRenderTypeSet.union(sourceLayers,
                            ChunkRenderTypeSet.of(RenderType.solid(), RenderType.cutout(),
                                    RenderType.translucent())).asList());
            for (RenderType layer : List.of(RenderType.solid(), RenderType.cutout(), RenderType.translucent())) {
                var actual = model.getQuads(state, Direction.NORTH, RandomSource.create(seed), data, layer);
                assertThat(actual.stream().filter(q -> q == first || q == second).toList())
                        .isEqualTo(sourceLayers.contains(layer) ? List.of(chosen) : List.of());
                assertThat(actual.stream().filter(q -> q != first && q != second).map(BakedQuad::getSprite).toList())
                        .isEqualTo(layer == RenderType.cutout() ? List.of(sprites.get(DynamicOverlayBakedModel.defaultPortOverlayTexture())) : List.of());
            }
            assertThat(model.getQuads(state, Direction.NORTH, RandomSource.create(seed), data, null))
                    .extracting(BakedQuad::getSprite).containsExactly(chosen.getSprite(), sprites.get(DynamicOverlayBakedModel.defaultPortOverlayTexture()));
            assertThat(model.getQuads(state, Direction.SOUTH, RandomSource.create(seed), data, null)).isEmpty();
        }
    }

    @Test
    void unculled_ctm_quad_keeps_its_light_and_normals_but_not_its_tint_on_the_overlay() {
        var state = Blocks.IRON_BLOCK.defaultBlockState();
        var model = bake(DynamicOverlayBakedModel.Kind.PORT, null);
        var random = RandomSource.create(42);
        var original = model.getQuads(state, Direction.WEST, random, ModelData.EMPTY, RenderType.solid()).getFirst();
        var vertices = original.getVertices().clone();
        for (int i = 0; i < vertices.length; i += IQuadTransformer.STRIDE) {
            vertices[i + IQuadTransformer.COLOR] = 0xff887766;
            vertices[i + IQuadTransformer.UV2] = 0x00b000c0;
        }
        var tinted = new BakedQuad(vertices, 7, Direction.WEST, original.getSprite(), false, false);
        var source = sourceModel(tinted, null, RenderType.translucent());
        var data = DynamicOverlayModelLoader.DynamicModel.withCtmSource(source, null, POS, state, ModelData.EMPTY, 42);
        assertThat(model.getQuads(state, Direction.WEST, random, data, null)).isEmpty();
        assertThat(model.getQuads(state, null, random, data, RenderType.cutout())).isEmpty();
        var quads = model.getQuads(state, null, random, data, RenderType.translucent());
        assertThat(quads).hasSize(2);
        assertThat(quads.getFirst()).isSameAs(tinted);
        var panel = quads.getLast();
        assertThat(panel.isShade()).isFalse();
        assertThat(panel.hasAmbientOcclusion()).isFalse();
        assertThat(panel.isTinted()).isFalse();
        for (int i = 0; i < vertices.length; i += IQuadTransformer.STRIDE) {
            assertThat(panel.getVertices()[i + IQuadTransformer.UV2]).isEqualTo(vertices[i + IQuadTransformer.UV2]);
            assertThat(panel.getVertices()[i + IQuadTransformer.NORMAL]).isEqualTo(vertices[i + IQuadTransformer.NORMAL]);
            assertThat(panel.getVertices()[i + IQuadTransformer.COLOR]).isEqualTo(-1);
            assertThat(vertices[i + IQuadTransformer.COLOR]).isEqualTo(0xff887766);
        }
    }

    @Test
    void stress_overlays_cover_recessed_panels_not_the_rim_or_casing_sides() {
        var blocks = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        var id = ResourceLocation.parse("mmcr_test:overlay_stress_fixture");
        if (!blocks.containsKey(id)) {
            blocks.unfreeze();
            try {
                Registry.register(blocks, id, StressInterfaceKind.INPUT.createBlock(Blocks.IRON_BLOCK.properties(), () -> BlockEntityType.CHEST));
            } finally {
                blocks.freeze();
            }
        }
        var state = blocks.get(id).defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X);
        var model = bake(DynamicOverlayBakedModel.Kind.PORT, null);
        var random = RandomSource.create(42);
        var all = model.getQuads(state, null, random, ModelData.EMPTY, null);
        var panels = model.getQuads(state, null, random, ModelData.EMPTY, RenderType.cutout());
        assertThat(panels).extracting(BakedQuad::getDirection).containsExactlyInAnyOrder(Direction.WEST, Direction.EAST);
        for (var panel : panels) {
            var base = all.stream().filter(q -> q.getSprite() != panel.getSprite() && q.getDirection() == panel.getDirection()
                    && position(q, 0).equals(position(panel, 0))).findFirst().orElseThrow();
            for (int i = 0; i < 4; i++) {
                var point = position(panel, i);
                assertThat(point).isEqualTo(position(base, i));
                assertThat(point.x).isStrictlyBetween(0F, 1F);
            }
        }
        for (var face : Direction.values()) assertThat(model.getQuads(state, face, random, ModelData.EMPTY, RenderType.cutout())).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"flux_point, 0.3125, false", "flux_plug, 0.5, false",
                "flux_point, 0.3125, true", "flux_plug, 0.5, true"})
    void world_flux_tiles_form_one_centered_core_with_colour_above_it(String core, float width, boolean translucentColour) {
        var model = bake(DynamicOverlayBakedModel.Kind.PORT, null);
        var on = ResourceLocation.parse("fluxnetworks:block/" + core + "_on");
        var colour = ResourceLocation.parse("fluxnetworks:block/" + core + "_colour");
        var colourSprite = sprites.computeIfAbsent(colour, id -> new TestSprite(id, translucentColour ? 128 : 0));
        var layers = new WorldFaceLayers(null, Direction.NORTH, List.of(
                new WorldFaceLayers.Layer(on, sprites.computeIfAbsent(on, TestSprite::new), RenderType.cutout()),
                new WorldFaceLayers.Layer(colour, colourSprite, translucentColour ? RenderType.translucent() : RenderType.cutout())));
        for (var face : Direction.values()) {
            var base = model.getQuads(Blocks.IRON_BLOCK.defaultBlockState(), face,
                    RandomSource.create(1), ModelData.EMPTY, RenderType.solid()).getFirst();
            int axis = face.getAxis().ordinal();
            int sign = face.getAxisDirection().getStep();
            // One CTM quarter must produce a quarter of the core, not a whole core.
            var vertices = base.getVertices().clone();
            for (int i = 0; i < vertices.length; i += IQuadTransformer.STRIDE) {
                for (int j = 0; j < 3; j++) {
                    int pos = i + IQuadTransformer.POSITION + j;
                    if (j != axis) vertices[pos] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[pos]) / 2F);
                }
            }
            var tile = new BakedQuad(vertices, 3, face, base.getSprite(), true, true);
            var overlays = new ArrayList<BakedQuad>();
            layers.emit(overlays, tile, RenderType.solid(), RenderType.cutout());
            layers.emit(overlays, tile, RenderType.solid(), RenderType.translucent());
            assertThat(overlays).extracting(BakedQuad::getSprite, BakedQuad::getTintIndex)
                    .containsExactly(tuple(sprites.get(on), -1), tuple(sprites.get(colour), FluxNetworkOverlay.NETWORK_TINT_INDEX));
            for (int i = 0; i < 4; i++) {
                var point = position(tile, i);
                var inner = position(overlays.getFirst(), i);
                var outer = position(overlays.getLast(), i);
                assertThat(inner.get(axis) * sign).isGreaterThan(point.get(axis) * sign);
                assertThat(outer.get(axis) * sign).isGreaterThan(inner.get(axis) * sign);
                for (int j = 0; j < 3; j++) {
                    if (j == axis) continue;
                    float expected = (1F - width) / 2 + point.get(j) * width;
                    assertThat(inner.get(j)).isEqualTo(expected);
                    assertThat(outer.get(j)).isEqualTo(expected);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(FluxNetworkInterfaceKind.class)
    void flux_item_core_keeps_its_existing_layer_spacing(FluxNetworkInterfaceKind kind) {
        var description = DynamicOverlayItemModel.Description.port(kind);
        var model = new DynamicOverlayModelLoader.DynamicModel(description.kind(), description,
                sprites.computeIfAbsent(description.overlayTextures().getFirst(), TestSprite::new),
                id -> sprites.computeIfAbsent(id, TestSprite::new), ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY, null);
        var item = model.getQuads(null, null, RandomSource.create(1), ModelData.EMPTY, null);
        for (var face : Direction.values()) {
            var quads = item.stream().filter(q -> q.getDirection() == face).toList();
            assertThat(quads).hasSize(3);
            int axis = face.getAxis().ordinal();
            float plane = position(quads.getFirst(), 0).get(axis);
            for (int layer = 0; layer < description.overlayTextures().size(); layer++) {
                var quad = quads.get(layer + 1);
                assertThat(quad.getSprite().contents().name()).isEqualTo(description.overlayTextures().get(layer));
                float depth = plane + face.getAxisDirection().getStep() * DynamicOverlayModelLoader.overlayGrow(layer);
                for (int i = 0; i < 4; i++) assertThat(position(quad, i).get(axis)).isEqualTo(depth);
            }
        }
    }

    private BakedModel sourceModel(BakedQuad quad, Direction bucket, RenderType layer) {
        return (BakedModel) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{BakedModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getModelData" -> args[3];
                    case "getRenderTypes" -> ChunkRenderTypeSet.of(layer);
                    case "getQuads" -> args[1] == bucket ? List.of(quad) : List.of();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    static Vector3f position(BakedQuad quad, int vertex) {
        int pos = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
        var data = quad.getVertices();
        return new Vector3f(Float.intBitsToFloat(data[pos]), Float.intBitsToFloat(data[pos + 1]), Float.intBitsToFloat(data[pos + 2]));
    }

    static void assertUv(BakedQuad quad, int vertex, float u, float v) {
        var sprite = quad.getSprite();
        int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
        assertThat(Float.intBitsToFloat(quad.getVertices()[offset])).isEqualTo(sprite.getU(Mth.lerp(sprite.uvShrinkRatio(), u, .5F)));
        assertThat(Float.intBitsToFloat(quad.getVertices()[offset + 1])).isEqualTo(sprite.getV(Mth.lerp(sprite.uvShrinkRatio(), v, .5F)));
    }

    private static void assertUvInsideSprite(BakedQuad quad) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        for (int index = 0; index < 4; index++) {
            assertThat(Float.intBitsToFloat(vertices[index * stride + 4]))
                    .isBetween(quad.getSprite().getU0(), quad.getSprite().getU1());
            assertThat(Float.intBitsToFloat(vertices[index * stride + 5]))
                    .isBetween(quad.getSprite().getV0(), quad.getSprite().getV1());
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class TestSprite extends TextureAtlasSprite {
        private TestSprite(ResourceLocation texture) {
            this(texture, 0);
        }

        private TestSprite(ResourceLocation texture, int alpha) {
            super(TextureAtlas.LOCATION_BLOCKS, new SpriteContents(texture, new FrameSize(16, 16),
                    image(alpha), ResourceMetadata.EMPTY), 256, 256, 16, 16);
        }

        private static NativeImage image(int alpha) {
            NativeImage image = new NativeImage(16, 16, true);
            image.fillRect(0, 0, 16, 16, 0xFFFFFFFF);
            if (alpha != 255) image.setPixelRGBA(0, 0, 0x00FFFFFF);
            image.setPixelRGBA(15, 15, alpha << 24 | 0x00FFFFFF);
            return image;
        }
    }

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void overlay_layers_keep_texture_order_and_append_state_last() {
        var basic = MMCR.id("block/overlay_basic");
        var type = MMCR.id("block/overlay_type");
        var tier = MMCR.id("block/overlay_tier");
        var state = MMCR.id("block/overlay_state");

        assertThat(DynamicOverlayModelLoader.overlayLayers(ImmutableList.of(basic, type, tier), state))
                .extracting(DynamicOverlayModelLoader.OverlayLayer::texture,
                        DynamicOverlayModelLoader.OverlayLayer::grow)
                .containsExactly(
                        tuple(basic, DynamicOverlayModelLoader.OVERLAY_GROW),
                        tuple(type, DynamicOverlayModelLoader.OVERLAY_GROW * 2.0f),
                        tuple(tier, DynamicOverlayModelLoader.OVERLAY_GROW * 3.0f),
                        tuple(state, DynamicOverlayModelLoader.OVERLAY_GROW * 4.0f));
    }

    @Test
    void controller_state_overlay_uses_egg_only_for_the_default_idle_texture() {
        var defaultMachine = MMCR.id("default_idle");
        var customMachine = MMCR.id("custom_idle");
        var customIdle = MMCR.id("block/custom_idle");
        var defaults = MachineAppearanceSpec.defaults();
        MachineAppearanceCache.replaceSnapshot(Map.of(
                defaultMachine, defaults,
                customMachine, new MachineAppearanceSpec(defaults.machineBasicBlock(), null, null, customIdle, null)));

        assertThat(DynamicOverlayBakedModel.controllerStateOverlay(defaultMachine, false, true))
                .isEqualTo(MMCR.id("block/overlay_egg"));
        assertThat(DynamicOverlayBakedModel.controllerStateOverlay(defaultMachine, true, true))
                .isEqualTo(MMCR.id("block/overlay_basic_active"));
        assertThat(DynamicOverlayBakedModel.controllerStateOverlay(customMachine, false, true))
                .isEqualTo(customIdle);
    }

    @Test
    void idle_easter_egg_requires_a_formed_inactive_controller_with_default_idle_texture() {
        var defaultMachine = MMCR.id("test_cube");
        var customMachine = MMCR.id("ineligible_custom_idle");
        var defaults = MachineAppearanceSpec.defaults();
        MachineAppearanceCache.replaceSnapshot(Map.of(
                defaultMachine, defaults,
                customMachine, new MachineAppearanceSpec(defaults.machineBasicBlock(), null, null,
                        MMCR.id("block/custom_idle"), null)));
        MachineControllerBlock block = (MachineControllerBlock) ModBlocks.controllerFor(defaultMachine).get();
        BlockState idle = block.defaultBlockState().setValue(MachineControllerBlock.FORMED, true);

        assertThat(ControllerIdleEasterEggManager.eligible(idle, defaultMachine)).isTrue();
        assertThat(ControllerIdleEasterEggManager.eligible(
                idle.setValue(MachineControllerBlock.ACTIVE, true), defaultMachine)).isFalse();
        assertThat(ControllerIdleEasterEggManager.eligible(
                idle.setValue(MachineControllerBlock.FORMED, false), defaultMachine)).isFalse();
        assertThat(ControllerIdleEasterEggManager.eligible(idle, customMachine)).isFalse();
    }

    @Test
    void idle_easter_egg_accepts_section_compilation_views_that_are_not_client_levels() {
        BlockAndTintGetter renderRegion = (BlockAndTintGetter) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{BlockAndTintGetter.class},
                (proxy, method, args) -> null);

        assertThat(ControllerIdleEasterEggManager.canTrackRenderView(renderRegion, true)).isTrue();
        assertThat(ControllerIdleEasterEggManager.canTrackRenderView(renderRegion, false)).isFalse();
    }

    @Test
    void controller_ctm_source_requires_formed_state_and_default_face_textures() {
        var machineId = MMCR.id("test_cube");
        var source = new MachineAppearanceSpec.TextureSource(BuiltInRegistries.BLOCK.getKey(Blocks.STONE), null);
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId,
                new MachineAppearanceSpec(source.blockId(), null, null)));
        ControllerSpecCache.replaceSnapshot(Map.of(machineId, MachineControllerSpec.defaultsFor(machineId)));
        MachineControllerBlock block = (MachineControllerBlock) ModBlocks.controllerFor(machineId).get();
        BlockState unformed = block.defaultBlockState();

        assertThat(DynamicOverlayModelLoader.ctmSource(DynamicOverlayBakedModel.Kind.CONTROLLER,
                unformed, ModelData.EMPTY)).isNull();
        assertThat(DynamicOverlayModelLoader.ctmSource(DynamicOverlayBakedModel.Kind.CONTROLLER,
                unformed.setValue(MachineControllerBlock.FORMED, true), ModelData.EMPTY)).isEqualTo(source);
    }

    @Test
    void port_ctm_source_requires_link_and_no_explicit_texture() {
        var source = new MachineAppearanceSpec.TextureSource(BuiltInRegistries.BLOCK.getKey(Blocks.STONE), null);
        var linked = ModelData.builder()
                .with(MachineModelDataKeys.PORT_LINKED, true)
                .with(MachineModelDataKeys.PORT_TEXTURE_SOURCE, source)
                .build();
        var overridden = ModelData.builder()
                .with(MachineModelDataKeys.PORT_LINKED, true)
                .with(MachineModelDataKeys.PORT_TEXTURE_SOURCE,
                        new MachineAppearanceSpec.TextureSource(source.blockId(), MMCR.id("block/custom")))
                .build();

        assertThat(DynamicOverlayModelLoader.ctmSource(DynamicOverlayBakedModel.Kind.PORT,
                Blocks.IRON_BLOCK.defaultBlockState(), linked)).isEqualTo(source);
        assertThat(DynamicOverlayModelLoader.ctmSource(DynamicOverlayBakedModel.Kind.PORT,
                Blocks.IRON_BLOCK.defaultBlockState(), linked.derive()
                        .with(MachineModelDataKeys.PORT_LINKED, false).build())).isNull();
        assertThat(DynamicOverlayModelLoader.ctmSource(DynamicOverlayBakedModel.Kind.PORT,
                Blocks.IRON_BLOCK.defaultBlockState(), overridden)).isNull();
    }

    @Test
    void source_state_requires_a_complete_cube() {
        var cube = new MachineAppearanceSpec.TextureSource(BuiltInRegistries.BLOCK.getKey(Blocks.STONE), null);
        var nonCube = new MachineAppearanceSpec.TextureSource(BuiltInRegistries.BLOCK.getKey(Blocks.TORCH), null);

        assertThat(DynamicOverlayBakedModel.sourceState(cube, EmptyBlockGetter.INSTANCE, POS))
                .contains(Blocks.STONE.defaultBlockState());
        assertThat(DynamicOverlayBakedModel.sourceState(nonCube, EmptyBlockGetter.INSTANCE, POS)).isEmpty();
    }
}
