package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicOverlayModelTest {

    @AfterEach
    void clear() {
        MachineAppearanceCache.replaceSnapshot(Map.of());
        ControllerSpecCache.replaceSnapshot(Map.of());
    }

    @Test
    void controller_resolver_uses_appearance_base_and_controller_front_overlay() {
        Identifier machineId = MMCR.id("press");
        MachineAppearanceCache.replaceSnapshot(Map.of(
                machineId,
                MachineAppearanceSpec.fromBasicBlock(Identifier.parse("kubejs:steel_casing"))));
        ControllerSpecCache.replaceSnapshot(Map.of(machineId, new MachineControllerSpec(
                MMCR.id("press_controller"),
                MMCR.id("block/basic_controller"),
                MMCR.id("block/basic_casing"),
                MMCR.id("block/basic_casing"),
                MMCR.id("block/basic_casing"),
                false)));

        DynamicOverlayBakedModel.TextureSet textures = DynamicOverlayBakedModel.controllerTextures(machineId);

        assertThat(textures.base()).isEqualTo(Identifier.parse("kubejs:block/steel_casing"));
        assertThat(textures.overlay()).isEqualTo(MMCR.id("block/basic_controller"));
    }

    @Test
    void port_resolver_prefers_explicit_base_texture_over_machine_appearance() {
        Identifier machineId = MMCR.id("press");
        MachineAppearanceCache.replaceSnapshot(Map.of(
                machineId,
                new MachineAppearanceSpec(
                        Identifier.parse("kubejs:steel_casing"),
                        Identifier.parse("kubejs:block/controller_casing"),
                        Identifier.parse("kubejs:block/formed_casing"))));

        DynamicOverlayBakedModel.TextureSet textures = DynamicOverlayBakedModel.portTextures(
                machineId,
                Identifier.parse("kubejs:block/explicit_casing"),
                MMCR.id("block/overlay_inputbus_normal"));

        assertThat(textures.base()).isEqualTo(Identifier.parse("kubejs:block/explicit_casing"));
        assertThat(textures.overlay()).isEqualTo(MMCR.id("block/overlay_inputbus_normal"));
    }

    @Test
    void port_resolver_uses_formed_port_base_when_explicit_base_is_absent() {
        Identifier machineId = MMCR.id("press");
        MachineAppearanceCache.replaceSnapshot(Map.of(
                machineId,
                new MachineAppearanceSpec(
                        Identifier.parse("kubejs:steel_casing"),
                        Identifier.parse("kubejs:block/controller_casing"),
                        Identifier.parse("kubejs:block/formed_casing"))));

        DynamicOverlayBakedModel.TextureSet textures = DynamicOverlayBakedModel.portTextures(
                machineId,
                null,
                MMCR.id("block/overlay_inputbus_normal"));

        assertThat(textures.base()).isEqualTo(Identifier.parse("kubejs:block/formed_casing"));
        assertThat(textures.overlay()).isEqualTo(MMCR.id("block/overlay_inputbus_normal"));
    }

    @Test
    void port_style_resolver_uses_basic_casing_when_machine_id_is_absent() {
        DynamicOverlayBakedModel.TextureSet textures = DynamicOverlayBakedModel.portTextures(
                null,
                null,
                MMCR.id("block/overlay_factory_controller"));

        assertThat(textures.base()).isEqualTo(MMCR.id("block/basic_casing"));
        assertThat(textures.overlay()).isEqualTo(MMCR.id("block/overlay_factory_controller"));
    }

    @Test
    void cache_key_tracks_controller_and_appearance_revisions() {
        Identifier machineId = MMCR.id("press");
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId, MachineAppearanceSpec.defaults()));
        ControllerSpecCache.replaceSnapshot(Map.of(machineId, MachineControllerSpec.defaultsFor(machineId)));

        DynamicOverlayBakedModel.CacheKey first = DynamicOverlayBakedModel.controllerCacheKey(machineId);
        MachineAppearanceCache.replaceSnapshot(Map.of(
                machineId,
                MachineAppearanceSpec.fromBasicBlock(Identifier.parse("kubejs:steel_casing"))));
        DynamicOverlayBakedModel.CacheKey second = DynamicOverlayBakedModel.controllerCacheKey(machineId);

        assertThat(second.appearanceRevision()).isGreaterThan(first.appearanceRevision());
        assertThat(second.controllerRevision()).isEqualTo(first.controllerRevision());
        assertThat(second).isNotEqualTo(first);
    }
}
