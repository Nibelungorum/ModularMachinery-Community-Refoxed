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
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.model.data.ModelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.joml.Vector3f;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DynamicOverlayModelLoaderTest {
    private static final BlockPos POS = BlockPos.ZERO;

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
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
    void destruction_particle_material_loads_a_bundled_texture(DynamicOverlayBakedModel.Kind kind) {
        List<TextureAtlasSprite> sprites = new ArrayList<>();
        try {
            MaterialBaker materials = (MaterialBaker) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{MaterialBaker.class}, (proxy, method, args) -> {
                        Material material = (Material) args[0];
                        var id = material.sprite();
                        try (var resource = getClass().getResourceAsStream(
                                "/assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png")) {
                            assertThat(resource).as("Bundled particle texture %s", id).isNotNull();
                            NativeImage image = NativeImage.read(resource);
                            var contents = new SpriteContents(id, new FrameSize(image.getWidth(), image.getHeight()), image);
                            var sprite = new TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, contents,
                                    image.getWidth(), image.getHeight(), 0, 0, 0) {};
                            sprites.add(sprite);
                            return new Material.Baked(sprite, material.forceTranslucent());
                        }
                    });
            ModelBaker baker = (ModelBaker) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{ModelBaker.class}, (proxy, method, args) -> {
                        if (method.getName().equals("materials")) return materials;
                        throw new UnsupportedOperationException(method.getName());
                    });

            var model = new DynamicOverlayModelLoader.Unbaked(kind).bake(baker);

            assertThat(sprites).singleElement().isSameAs(model.particleMaterial().sprite());
        } finally {
            sprites.forEach(TextureAtlasSprite::close);
        }
    }

    @Test
    void runtime_block_quads_follow_the_reloaded_atlas() {
        var base = MMCR.id("block/test_runtime_base");
        var panel = MMCR.id("block/overlay/base/item");
        var arrows = MMCR.id("block/overlay/direction/input");
        var icon = MMCR.id("block/overlay/type/item");
        var tier = MMCR.id("block/overlay/tier/ludicrous");
        var state = ModBlocks.BLOCKS.get("item_input_bus_ludicrous").get().defaultBlockState();
        var data = ModelData.builder().with(MachineModelDataKeys.PORT_TEXTURE_SOURCE,
                new MachineAppearanceSpec.TextureSource(MMCR.id("basic_casing"), base)).build();
        var level = (BlockAndTintGetter) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{BlockAndTintGetter.class}, (proxy, method, args) -> data);

        try (var casing = sprite(base, 0xff0000ff); var newCasing = sprite(base, 0xffcccccc); var frame = sprite(panel, 0);
             var first = animated(arrows, 2, true, 0x80ff0000, 0xffff0000);
             var replacement = animated(arrows, 5, false, 0, 0xff00ff00);
             var type = sprite(icon, 0xffaaaaaa); var lights = animated(tier, 3, false, 0, 0xff888888)) {
            var sprites = new HashMap<>(Map.of(panel, frame, icon, type, tier, lights));
            var model = new DynamicOverlayModelLoader(DynamicOverlayBakedModel.Kind.PORT,
                    new Material.Baked(casing, false), texture -> new Material.Baked(sprites.get(texture), false));
            for (var arrow : List.of(first, replacement)) {
                var currentCasing = arrow == first ? casing : newCasing;
                sprites.put(base, currentCasing);
                sprites.put(arrows, arrow);
                var parts = new ArrayList<BlockStateModelPart>();
                model.collectParts(level, POS, state, RandomSource.create(0L), parts);
                var part = parts.getFirst();
                assertThat(part.getQuads(null)).isEmpty();
                var upperPass = arrow == first ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.CUTOUT;
                for (var face : Direction.values()) {
                    var quads = part.getQuads(face);
                    assertThat(quads).extracting(q -> q.materialInfo().sprite())
                            .containsExactly(currentCasing, frame, arrow, type, lights);
                    assertThat(quads).extracting(q -> q.materialInfo().layer())
                            .containsExactly(ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT, upperPass, upperPass, upperPass);
                    assertThat(quads.get(2).materialInfo().flags() & BakedQuad.FLAG_ANIMATED).isNotZero();
                    assertThat(quads.getLast().materialInfo().flags() & BakedQuad.FLAG_ANIMATED).isNotZero();
                    for (var quad : quads) {
                        assertThat(quad.direction()).isSameAs(face);
                        var sprite = quad.materialInfo().sprite();
                        for (int i = 0; i < 4; i++) {
                            var point = quad.position(i);
                            assertThat(point).isEqualTo(quads.getFirst().position(i));
                            var uv = DynamicOverlayModelLoader.uv(face, Direction.NORTH, point);
                            assertThat(quad.packedUV(i)).isEqualTo(UVPair.pack(sprite.getU(uv[0]), sprite.getV(uv[1])));
                        }
                    }
                }
            }
            assertThat(casing.getPixelRGBA(0, 0, 0)).isEqualTo(0xff0000ff);
        }
    }

    @Test
    void only_controller_front_changes_material_when_active_and_vertical_roll_is_retained() {
        var id = MMCR.id("test_cube");
        var base = MMCR.id("block/test_casing");
        var front = ControllerSpecCache.specFor(id).frontTexture();
        var idle = MMCR.id("block/test_idle");
        var active = MMCR.id("block/test_active");
        var previous = MachineAppearanceCache.snapshot();
        var revision = MachineAppearanceCache.revision();
        MachineAppearanceCache.replaceSnapshot(Map.of(id, new MachineAppearanceSpec(
                MachineAppearanceSpec.defaults().machineBasicBlock(), base, null, idle, active)));
        try (var casing = sprite(base, 0xff0000ff); var panel = sprite(front, 0x80ff0000);
             var off = sprite(idle, 0); var on = sprite(active, 0xff00ff00)) {
            var materials = Map.of(base, casing, front, panel, idle, off, active, on);
            var state = ModBlocks.controllerFor(id).get().defaultBlockState()
                    .setValue(MachineControllerBlock.FACING, Direction.UP)
                    .setValue(MachineControllerBlock.ROLL_FACING, Direction.EAST);
            var model = new DynamicOverlayModelLoader(DynamicOverlayBakedModel.Kind.CONTROLLER,
                    new Material.Baked(casing, false), tex -> new Material.Baked(materials.get(tex), false));
            for (boolean running : List.of(false, true)) {
                var parts = new ArrayList<BlockStateModelPart>();
                model.collectParts(BlockAndTintGetter.EMPTY, POS, state.setValue(MachineControllerBlock.ACTIVE, running),
                        RandomSource.create(1L), parts);
                var part = parts.getFirst();
                assertThat(part.getQuads(null)).isEmpty();
                for (var face : Direction.values()) {
                    var quads = part.getQuads(face);
                    if (face != Direction.UP) {
                        assertThat(quads).extracting(q -> q.materialInfo().sprite()).containsExactly(casing);
                    } else {
                        assertThat(quads).extracting(q -> q.materialInfo().sprite())
                                .containsExactly(casing, panel, running ? on : off);
                        for (var q : quads) {
                            var sprite = q.materialInfo().sprite();
                            for (int i = 0; i < 4; i++) {
                                var p = q.position(i);
                                float u = q == quads.getFirst() ? p.x() : p.z();
                                float v = q == quads.getFirst() ? p.z() : 1 - p.x();
                                assertThat(q.packedUV(i)).isEqualTo(UVPair.pack(sprite.getU(u), sprite.getV(v)));
                            }
                        }
                    }
                }
            }
        } finally {
            MachineAppearanceCache.replaceSnapshot(previous, revision);
        }
    }

    private static TextureAtlasSprite animated(Identifier id, int time, boolean interpolate, int first, int next) {
        var image = new NativeImage(1, 2, false);
        image.setPixel(0, 0, first);
        image.setPixel(0, 1, next);
        var metadata = new AnimationMetadataSection(Optional.empty(), Optional.empty(), Optional.empty(), time, interpolate);
        var contents = new SpriteContents(id, new FrameSize(1, 1), image, Optional.of(metadata), List.of(), Optional.empty());
        return new TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, contents, 32, 32, time, time, 0) {};
    }

    private static TextureAtlasSprite sprite(Identifier texture, int color) {
        var image = new NativeImage(1, 1, false);
        image.setPixel(0, 0, color);
        var contents = new SpriteContents(texture, new FrameSize(1, 1), image);
        return new TextureAtlasSprite(TextureAtlas.LOCATION_BLOCKS, contents, 1, 1, 0, 0, 0) {};
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
