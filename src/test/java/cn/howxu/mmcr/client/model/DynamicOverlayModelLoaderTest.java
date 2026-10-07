package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
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
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.WeightedBakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.lang.reflect.Proxy;
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
        IGeometryBakingContext context = (IGeometryBakingContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{IGeometryBakingContext.class},
                (proxy, method, args) -> method.getName().equals("getTransforms") ? ItemTransforms.NO_TRANSFORMS : null);
        return new DynamicOverlayModelLoader.Unbaked(kind, itemBlockId).bake(context, null,
                material -> sprites.computeIfAbsent(material.texture(), TestSprite::new), null, ItemOverrides.EMPTY);
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
    void port_base_is_solid_and_only_overlays_are_translucent() {
        BakedModel model = bake(DynamicOverlayBakedModel.Kind.PORT, null);
        var state = Blocks.IRON_BLOCK.defaultBlockState();
        var base = MMCR.id("block/formed_base");
        ModelData data = ModelData.of(MachineModelDataKeys.PORT_TEXTURE_SOURCE,
                new MachineAppearanceSpec.TextureSource(MMCR.id("basic_casing"), base));
        RandomSource random = RandomSource.create(0L);

        assertThat(model.getRenderTypes(state, random, data).asList())
                .containsExactly(RenderType.solid(), RenderType.translucent());
        for (var direction : Direction.values()) {
            assertThat(model.getQuads(state, direction, random, data, RenderType.solid()))
                    .extracting(quad -> quad.getSprite().contents().name()).containsExactly(base);
            assertThat(model.getQuads(state, direction, random, data, RenderType.translucent())).isEmpty();
        }
        assertThat(model.getQuads(state, null, random, data, RenderType.solid())).isEmpty();
        assertThat(model.getQuads(state, null, random, data, RenderType.translucent()))
                .hasSize(6).allSatisfy(quad -> assertThat(quad.getSprite().contents().name())
                        .isEqualTo(DynamicOverlayBakedModel.defaultPortOverlayTexture()));
        assertThat(model.getQuads(state, null, random, data, RenderType.cutout())).isEmpty();
        assertThat(model.getQuads(state, Direction.NORTH, random, data, null)).hasSize(1);
    }

    @Test
    void controller_base_and_front_state_overlays_use_separate_layers() {
        var machineId = MMCR.id("test_cube");
        var base = MMCR.id("block/controller_base");
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId,
                new MachineAppearanceSpec(MMCR.id("basic_casing"), base, null)));
        MachineControllerBlock block = (MachineControllerBlock) ModBlocks.controllerFor(machineId).get();
        var state = block.defaultBlockState().setValue(MachineControllerBlock.FACING, Direction.EAST)
                .setValue(MachineControllerBlock.FORMED, true).setValue(MachineControllerBlock.ACTIVE, true);
        BakedModel model = bake(DynamicOverlayBakedModel.Kind.CONTROLLER, null);
        var random = RandomSource.create(0L);

        assertThat(model.getQuads(state, Direction.NORTH, random, ModelData.EMPTY, RenderType.solid()))
                .extracting(quad -> quad.getSprite().contents().name()).containsExactly(base);
        assertThat(model.getQuads(state, null, random, ModelData.EMPTY, RenderType.translucent()))
                .allSatisfy(quad -> assertThat(quad.getDirection()).isEqualTo(Direction.EAST))
                .extracting(quad -> quad.getSprite().contents().name()).containsExactly(
                        ControllerSpecCache.specFor(machineId).frontTexture(), MMCR.id("block/overlay_basic_active"));
    }

    @Test
    void item_passes_separate_cube_base_from_overlays_and_keep_uv_inside_sprites() {
        var machineId = MMCR.id("test_cube");
        var blockId = BuiltInRegistries.BLOCK.getKey(ModBlocks.controllerFor(machineId).get());
        var model = bake(DynamicOverlayBakedModel.Kind.CONTROLLER, blockId);
        var passes = model.getRenderPasses(ItemStack.EMPTY, false);
        var random = RandomSource.create(0L);

        assertThat(passes).hasSize(2);
        assertThat(passes.get(0).getRenderTypes(ItemStack.EMPTY, false))
                .containsExactly(NeoForgeRenderTypes.ITEM_LAYERED_SOLID.get());
        assertThat(passes.get(1).getRenderTypes(ItemStack.EMPTY, false))
                .containsExactly(NeoForgeRenderTypes.ITEM_UNSORTED_TRANSLUCENT.get());
        assertThat(passes.get(0).getQuads(null, null, random)).hasSize(6)
                .allSatisfy(quad -> assertThat(quad.getSprite().contents().name()).isEqualTo(MMCR.id("block/ctm/basic_casing/particle")));
        assertThat(passes.get(1).getQuads(null, null, random)).hasSize(2)
                .allSatisfy(DynamicOverlayModelLoaderTest::assertUvInsideSprite);
    }

    @Test
    void ctm_proxy_keeps_weighted_base_selection_and_filters_other_layers() throws Exception {
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
                .add(sourceModel(first, RenderType.solid()), 1)
                .add(sourceModel(second, RenderType.cutout()), 1).build();

        for (long seed = 0; seed < 32; seed++) {
            var sourceLayers = source.getRenderTypes(state, RandomSource.create(seed), ModelData.EMPTY);
            ModelData data = ctmData(source, state, sourceLayers);
            assertThat(model.getRenderTypes(state, RandomSource.create(seed), data).asList())
                    .containsExactlyElementsOf(ChunkRenderTypeSet.union(sourceLayers,
                            ChunkRenderTypeSet.of(RenderType.translucent())).asList());
            for (RenderType layer : List.of(RenderType.solid(), RenderType.cutout(), RenderType.translucent())) {
                var actual = model.getQuads(state, Direction.NORTH, RandomSource.create(seed), data, layer);
                if (sourceLayers.contains(layer)) {
                    assertThat(actual).containsExactlyElementsOf(source.getQuads(state, Direction.NORTH,
                            RandomSource.create(seed), ModelData.EMPTY, layer));
                } else {
                    assertThat(actual).isEmpty();
                }
            }
            assertThat(model.getQuads(state, Direction.NORTH, RandomSource.create(seed), data, null))
                    .containsExactlyElementsOf(source.getQuads(state, Direction.NORTH,
                            RandomSource.create(seed), ModelData.EMPTY, null));
        }
    }

    private BakedModel sourceModel(BakedQuad quad, RenderType layer) {
        return (BakedModel) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{BakedModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRenderTypes" -> ChunkRenderTypeSet.of(layer);
                    case "getQuads" -> args[1] == Direction.NORTH ? List.of(quad) : List.of();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static ModelData ctmData(BakedModel source, BlockState state, ChunkRenderTypeSet layers) throws Exception {
        var property = DynamicOverlayModelLoader.class.getDeclaredField("CTM_CONTEXT");
        property.setAccessible(true);
        var type = Class.forName(DynamicOverlayModelLoader.class.getName() + "$CtmContext");
        var constructor = type.getDeclaredConstructor(BakedModel.class, BlockState.class, ModelData.class,
                ChunkRenderTypeSet.class);
        constructor.setAccessible(true);
        return ModelData.of((ModelProperty<Object>) property.get(null),
                constructor.newInstance(source, state, ModelData.EMPTY, layers));
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
            super(TextureAtlas.LOCATION_BLOCKS, new SpriteContents(texture, new FrameSize(16, 16),
                    new NativeImage(16, 16, false), ResourceMetadata.EMPTY), 256, 256, 16, 16);
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
