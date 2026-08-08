package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicOverlayTexturesTest {

    @AfterEach
    void clearControllerSpecs() {
        ControllerSpecCache.replaceSnapshot(Map.of());
    }

    @Test
    void port_overlay_uses_kind_id_and_tier() {
        assertThat(DynamicOverlayTextures.portOverlayTextureForName("item_input_bus_big"))
                .isEqualTo(MMCR.id("block/overlay_inputbus_big"));
    }

    @Test
    void unknown_port_kind_uses_default_overlay() {
        assertThat(DynamicOverlayTextures.portOverlayTextureForName("external_port"))
                .isEqualTo(DynamicOverlayBakedModel.defaultPortOverlayTexture());
    }

    @Test
    void parallel_and_factory_controllers_use_port_style_overlays() {
        assertThat(DynamicOverlayTextures.portOverlayTextureForName("parallel_controller_16"))
                .isEqualTo(MMCR.id("block/overlay_parallel_controller_reinforced"));
        assertThat(DynamicOverlayTextures.portOverlayTextureForName("factory_controller"))
                .isEqualTo(MMCR.id("block/overlay_factory_controller"));
    }

    @Test
    void parallel_and_factory_overlay_texture_resources_exist() {
        assertThat(textureResource("overlay_parallel_controller_normal.png")).isNotNull();
        assertThat(textureResource("overlay_parallel_controller_reinforced.png")).isNotNull();
        assertThat(textureResource("overlay_parallel_controller_super.png")).isNotNull();
        assertThat(textureResource("overlay_parallel_controller_elite.png")).isNotNull();
        assertThat(textureResource("overlay_parallel_controller_ultimate.png")).isNotNull();
        assertThat(textureResource("overlay_factory_controller.png")).isNotNull();
    }

    @Test
    void controller_overlay_uses_cached_front_texture() {
        Identifier machineId = MMCR.id("press");
        Identifier frontTexture = MMCR.id("block/press_controller");
        ControllerSpecCache.replaceSnapshot(Map.of(machineId, new MachineControllerSpec(
                machineId,
                frontTexture,
                MMCR.id("block/basic_casing"),
                MMCR.id("block/basic_casing"),
                MMCR.id("block/basic_casing"),
                false)));

        assertThat(DynamicOverlayTextures.controllerOverlayTexture(machineId))
                .isEqualTo(frontTexture);
    }

    private static java.net.URL textureResource(String name) {
        return DynamicOverlayTexturesTest.class.getClassLoader()
                .getResource("assets/mmcr/textures/block/" + name);
    }
}
