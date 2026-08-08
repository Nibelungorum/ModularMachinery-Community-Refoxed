package cn.howxu.mmcr.client.model;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MachineAppearanceCacheTest {

    @AfterEach
    void clear() {
        MachineAppearanceCache.replaceSnapshot(Map.of());
    }

    @Test
    void replacement_is_atomic_and_missing_ids_use_defaults() {
        Identifier id = MMCR.id("press");
        MachineAppearanceSpec spec = MachineAppearanceSpec.fromBasicBlock(Identifier.parse("kubejs:steel_casing"));

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
}
