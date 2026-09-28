package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MachineAppearanceCacheTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clear() {
        MachineAppearanceCache.replaceSnapshot(Map.of());
    }

    @Test
    void replacement_is_atomic_and_missing_ids_use_defaults() {
        ResourceLocation id = MMCR.id("press");
        MachineAppearanceSpec spec = MachineAppearanceSpec.fromBasicBlock(ResourceLocation.parse("kubejs:steel_casing"));

        assertThat(MachineAppearanceCache.replaceSnapshot(Map.of(id, spec))).isTrue();
        assertThat(MachineAppearanceCache.specFor(id)).isEqualTo(spec);

        assertThat(MachineAppearanceCache.replaceSnapshot(Collections.singletonMap(id, null))).isFalse();
        assertThat(MachineAppearanceCache.specFor(id)).isEqualTo(spec);

        assertThat(MachineAppearanceCache.replaceSnapshot(Map.of())).isTrue();
        assertThat(MachineAppearanceCache.specFor(id)).isEqualTo(MachineAppearanceSpec.defaults());
    }

    @Test
    void listeners_run_only_after_successful_replacement() {
        AtomicInteger calls = new AtomicInteger();
        MachineAppearanceCache.addInvalidationListener(calls::incrementAndGet);

        assertThat(MachineAppearanceCache.replaceSnapshot(Map.of(MMCR.id("press"), MachineAppearanceSpec.defaults()))).isTrue();
        assertThat(MachineAppearanceCache.replaceSnapshot(Collections.singletonMap(MMCR.id("bad"), null))).isFalse();

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void synchronized_replacement_uses_content_version() {
        ResourceLocation id = MMCR.id("versioned_press");
        MachineAppearanceSpec spec = MachineAppearanceSpec.defaults();

        assertThat(MachineAppearanceCache.replaceSnapshot(Map.of(id, spec), 42L)).isTrue();
        assertThat(MachineAppearanceCache.revision()).isEqualTo(42L);
    }

    @Test
    void persisted_snapshot_round_trips_complete_appearance_specs() {
        ResourceLocation id = MMCR.id("press");
        MachineAppearanceSpec spec = new MachineAppearanceSpec(
                ResourceLocation.parse("kubejs:steel_casing"),
                ResourceLocation.parse("kubejs:block/controller_casing"),
                ResourceLocation.parse("kubejs:block/formed_casing"),
                ResourceLocation.parse("kubejs:block/idle_controller"),
                ResourceLocation.parse("kubejs:block/active_controller"));
        Path file = tempDir.resolve("machine-appearance.properties");

        MachineAppearanceCache.replaceSnapshot(Map.of(id, spec));
        MachineAppearanceCache.savePersistedSnapshot(file);
        MachineAppearanceCache.replaceSnapshot(Map.of());

        MachineAppearanceCache.loadPersistedSnapshot(file);

        assertThat(MachineAppearanceCache.specFor(id)).isEqualTo(spec);
    }

    @Test
    void legacy_persisted_snapshot_uses_default_controller_state_overlays() throws Exception {
        ResourceLocation id = MMCR.id("press");
        Path file = tempDir.resolve("machine-appearance.properties");
        Files.writeString(file, "mmcr\\:press=kubejs:steel_casing,kubejs:block/controller_casing,kubejs:block/formed_casing\n");

        MachineAppearanceCache.loadPersistedSnapshot(file);

        assertThat(MachineAppearanceCache.specFor(id).controllerIdleOverlayTexture())
                .isEqualTo(MMCR.id("block/overlay_basic_idle"));
        assertThat(MachineAppearanceCache.specFor(id).controllerActiveOverlayTexture())
                .isEqualTo(MMCR.id("block/overlay_basic_active"));
    }
}
