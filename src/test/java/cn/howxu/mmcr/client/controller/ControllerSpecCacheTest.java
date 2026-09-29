package cn.howxu.mmcr.client.controller;

import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerSpecCacheTest {

    @BeforeEach
    void clearSnapshot() {
        ControllerSpecCache.replaceSnapshot(Map.of());
    }

    @Test
    void replacementIsAtomicAndMissingIdsUseDefaults() {
        ResourceLocation id = ResourceLocation.parse("mmcr:dynamic");
        MachineControllerSpec spec = testSpec(id);

        assertThat(ControllerSpecCache.replaceSnapshot(Map.of(id, spec))).isTrue();
        assertThat(ControllerSpecCache.specFor(id)).isEqualTo(spec);
        assertThat(ControllerSpecCache.replaceSnapshot(Collections.singletonMap(id, null))).isFalse();
        assertThat(ControllerSpecCache.specFor(id)).isEqualTo(spec);
        assertThat(ControllerSpecCache.replaceSnapshot(Map.of())).isTrue();
        assertThat(ControllerSpecCache.specFor(id)).isEqualTo(MachineControllerSpec.defaultsFor(id));
    }

    @Test
    void listenersRunOnlyAfterSuccessfulReplacement() {
        ResourceLocation id = ResourceLocation.parse("mmcr:listener");
        AtomicInteger invocations = new AtomicInteger();
        ControllerSpecCache.addInvalidationListener(invocations::incrementAndGet);

        assertThat(ControllerSpecCache.replaceSnapshot(Map.of(id, testSpec(id)))).isTrue();
        assertThat(ControllerSpecCache.replaceSnapshot(Collections.singletonMap(id, null))).isFalse();

        assertThat(invocations).hasValue(1);
    }

    @Test
    void synchronized_replacement_uses_content_version() {
        ResourceLocation id = ResourceLocation.parse("mmcr:versioned_controller");

        assertThat(ControllerSpecCache.replaceSnapshot(Map.of(id, testSpec(id)), 42L)).isTrue();
        assertThat(ControllerSpecCache.revision()).isEqualTo(42L);
    }

    @Test
    void modelKeyChangesWhenAcceptedSnapshotChanges() {
        ResourceLocation id = ResourceLocation.parse("mmcr:model");
        ControllerModelCache.clear();
        ControllerSpecCache.replaceSnapshot(Map.of(id, testSpec(id)));
        var first = ControllerModelCache.modelFor(id);

        ControllerSpecCache.replaceSnapshot(Map.of(id, new MachineControllerSpec(
                testSpec(id).id(), ResourceLocation.parse("mmcr:block/changed"), testSpec(id).sideTexture(),
                testSpec(id).topTexture(), testSpec(id).bottomTexture(), false)));

        assertThat(ControllerModelCache.modelFor(id)).isNotSameAs(first);
        assertThat(ControllerModelCache.size()).isEqualTo(1);
    }

    private static MachineControllerSpec testSpec(ResourceLocation machineId) {
        return new MachineControllerSpec(
                ResourceLocation.fromNamespaceAndPath(machineId.getNamespace(), machineId.getPath() + "_controller"),
                ResourceLocation.parse("mmcr:block/front"),
                ResourceLocation.parse("mmcr:block/side"),
                ResourceLocation.parse("mmcr:block/top"),
                ResourceLocation.parse("mmcr:block/bottom"),
                false);
    }
}
