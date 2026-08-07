package cn.howxu.mmcr.client.controller;

import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import net.minecraft.resources.Identifier;
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
        Identifier id = Identifier.parse("mmcr:dynamic");
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
        Identifier id = Identifier.parse("mmcr:listener");
        AtomicInteger invocations = new AtomicInteger();
        ControllerSpecCache.addInvalidationListener(invocations::incrementAndGet);

        assertThat(ControllerSpecCache.replaceSnapshot(Map.of(id, testSpec(id)))).isTrue();
        assertThat(ControllerSpecCache.replaceSnapshot(Collections.singletonMap(id, null))).isFalse();

        assertThat(invocations).hasValue(1);
    }

    @Test
    void modelKeyChangesWhenAcceptedSnapshotChanges() {
        Identifier id = Identifier.parse("mmcr:model");
        ControllerModelCache.clear();
        ControllerSpecCache.replaceSnapshot(Map.of(id, testSpec(id)));
        var first = ControllerModelCache.modelFor(id);

        ControllerSpecCache.replaceSnapshot(Map.of(id, new MachineControllerSpec(
                testSpec(id).id(), Identifier.parse("mmcr:block/changed"), testSpec(id).sideTexture(),
                testSpec(id).topTexture(), testSpec(id).bottomTexture(), false)));

        assertThat(ControllerModelCache.modelFor(id)).isNotSameAs(first);
        assertThat(ControllerModelCache.size()).isEqualTo(1);
    }

    private static MachineControllerSpec testSpec(Identifier machineId) {
        return new MachineControllerSpec(
                Identifier.fromNamespaceAndPath(machineId.getNamespace(), machineId.getPath() + "_controller"),
                Identifier.parse("mmcr:block/front"),
                Identifier.parse("mmcr:block/side"),
                Identifier.parse("mmcr:block/top"),
                Identifier.parse("mmcr:block/bottom"),
                false);
    }
}
