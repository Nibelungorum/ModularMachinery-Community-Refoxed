package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.client.controller.ControllerModelCache;
import cn.howxu.mmcr.client.controller.ControllerSpecCache;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MachineAppearanceInvalidationTest {

    @AfterEach
    void clear() {
        MachineAppearanceCache.replaceSnapshot(Map.of());
        ControllerSpecCache.replaceSnapshot(Map.of());
        ControllerModelCache.clear();
    }

    @Test
    void accepted_appearance_snapshots_increment_revision_and_notify_listeners() {
        AtomicInteger calls = new AtomicInteger();
        long before = MachineAppearanceCache.revision();
        MachineAppearanceCache.addInvalidationListener(calls::incrementAndGet);

        assertThat(MachineAppearanceCache.replaceSnapshot(Map.of(MMCR.id("press"), MachineAppearanceSpec.defaults()))).isTrue();
        long accepted = MachineAppearanceCache.revision();
        assertThat(MachineAppearanceCache.replaceSnapshot(Collections.singletonMap(MMCR.id("bad"), null))).isFalse();

        assertThat(accepted).isGreaterThan(before);
        assertThat(MachineAppearanceCache.revision()).isEqualTo(accepted);
        assertThat(calls).hasValue(1);
    }

    @Test
    void controller_model_key_tracks_controller_and_appearance_revisions() {
        Identifier machineId = MMCR.id("press");
        ControllerSpecCache.replaceSnapshot(Map.of(machineId, MachineControllerSpec.defaultsFor(machineId)));
        MachineAppearanceCache.replaceSnapshot(Map.of(machineId, MachineAppearanceSpec.defaults()));
        ControllerModelCache.clear();
        var first = ControllerModelCache.modelFor(machineId);

        MachineAppearanceCache.replaceSnapshot(Map.of(
                machineId,
                MachineAppearanceSpec.fromBasicBlock(Identifier.parse("kubejs:steel_casing"))));
        var second = ControllerModelCache.modelFor(machineId);

        assertThat(second).isNotSameAs(first);
        assertThat(second.appearanceRevision()).isGreaterThan(first.appearanceRevision());
        assertThat(second.controllerRevision()).isEqualTo(first.controllerRevision());
        assertThat(second.appearance()).isEqualTo(MachineAppearanceSpec.fromBasicBlock(Identifier.parse("kubejs:steel_casing")));
        assertThat(ControllerModelCache.size()).isEqualTo(1);
    }
}
