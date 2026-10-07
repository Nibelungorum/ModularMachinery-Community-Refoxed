package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicOverlayBakedModelTest {
    @Test
    void texture_set_preserves_overlay_insertion_order() {
        var first = MMCR.id("block/overlay_basic");
        var second = MMCR.id("block/overlay_type");
        var third = MMCR.id("block/overlay_tier");

        var textures = new DynamicOverlayBakedModel.TextureSet(
                DynamicOverlayBakedModel.FaceTextures.uniform(MMCR.id("block/base")),
                ImmutableList.of(first, second, third));

        assertThat(textures.overlays()).containsExactly(first, second, third);
    }

    @Test
    void incomplete_appearance_faces_use_uniform_fallback() {
        var faces = DynamicOverlayBakedModel.completeOrFallback(Map.of(Direction.NORTH, MMCR.id("block/north")));

        assertThat(faces).isEqualTo(DynamicOverlayBakedModel.FaceTextures.uniform(MMCR.id("block/ctm/basic_casing/particle")));
    }

    @Test
    void complete_appearance_faces_are_preserved() {
        var faces = DynamicOverlayBakedModel.completeOrFallback(Map.of(
                Direction.DOWN, MMCR.id("block/down"), Direction.UP, MMCR.id("block/up"),
                Direction.NORTH, MMCR.id("block/north"), Direction.SOUTH, MMCR.id("block/south"),
                Direction.WEST, MMCR.id("block/west"), Direction.EAST, MMCR.id("block/east")));

        assertThat(faces.forFace(Direction.SOUTH)).isEqualTo(MMCR.id("block/south"));
    }

    @Test
    void controller_state_overlay_uses_the_machine_active_texture() {
        var machineId = MMCR.id("stateful_controller");
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId, new MachineAppearanceSpec(
                MMCR.id("basic_casing"), null, null,
                MMCR.id("block/custom_idle"), MMCR.id("block/custom_active"))));

        assertThat(DynamicOverlayBakedModel.controllerStateOverlay(machineId, true))
                .isEqualTo(MMCR.id("block/custom_active"));
    }

    @Test
    void controller_state_overlay_uses_the_machine_idle_texture() {
        var machineId = MMCR.id("stateful_controller");
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId, new MachineAppearanceSpec(
                MMCR.id("basic_casing"), null, null,
                MMCR.id("block/custom_idle"), MMCR.id("block/custom_active"))));

        assertThat(DynamicOverlayBakedModel.controllerStateOverlay(machineId, false))
                .isEqualTo(MMCR.id("block/custom_idle"));
    }

    @Test
    void controller_ctm_requires_appearance_only_base_and_default_face_textures() {
        var machineId = MMCR.id("athena_controller");
        MachineControllerSpec defaults = MachineControllerSpec.defaultsFor(machineId);
        MachineAppearanceSpec appearance = new MachineAppearanceSpec(
                MMCR.id("athena_casing"), null, null,
                MMCR.id("block/custom_idle"), MMCR.id("block/custom_active"));

        assertThat(DynamicOverlayBakedModel.controllerCtmEligible(machineId, appearance, defaults)).isTrue();
        assertThat(DynamicOverlayBakedModel.controllerCtmEligible(machineId,
                new MachineAppearanceSpec(appearance.machineBasicBlock(), MMCR.id("block/base"), null), defaults)).isFalse();

        for (int face = 0; face < 4; face++) {
            List<ResourceLocation> textures = new ArrayList<>(List.of(
                    defaults.frontTexture(), defaults.sideTexture(), defaults.topTexture(), defaults.bottomTexture()));
            textures.set(face, MMCR.id("block/custom_" + face));
            var customized = new MachineControllerSpec(defaults.id(), textures.get(0), textures.get(1),
                    textures.get(2), textures.get(3), false);
            assertThat(DynamicOverlayBakedModel.controllerCtmEligible(machineId, appearance, customized)).isFalse();
        }
    }
}
