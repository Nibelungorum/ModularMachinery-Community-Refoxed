package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DynamicOverlayModelLoaderTest {
    private static final BlockPos POS = BlockPos.ZERO;

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
