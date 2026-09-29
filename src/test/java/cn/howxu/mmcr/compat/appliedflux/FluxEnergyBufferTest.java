package cn.howxu.mmcr.compat.appliedflux;

import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies persistence and native mutation invariants of the local Flux cache.
 *
 * @author howxu <dev@howxu.cn>
 */
class FluxEnergyBufferTest {
    @Test
    void inputReservationSurvivesIdleCacheChecks() {
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        assertThat(buffer.insert(15_000L)).isEqualTo(15_000L);
        buffer.reserve(5_000L);
        for (int tick = 0; tick < FluxEnergyBuffer.IDLE_DELAY_TICKS; tick++) buffer.advanceIdle();

        assertThat(buffer.amount()).isEqualTo(15_000L);
        assertThat(buffer.reserved()).isEqualTo(5_000L);
        assertThat(buffer.idleExcess()).isEqualTo(10_000L);
        assertThat(buffer.isIdleReady()).isFalse();
    }

    @Test
    void inputReleaseMovesUnusedReservationToIdleState() {
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        buffer.insert(100L);
        buffer.reserve(100L);
        assertThat(buffer.releaseReservation(40L)).isEqualTo(40L);

        assertThat(buffer.amount()).isEqualTo(100L);
        assertThat(buffer.reserved()).isEqualTo(60L);
        assertThat(buffer.idleExcess()).isEqualTo(40L);
    }

    @Test
    void nativeMutationsImmediatelyUpdateAmountReservationAndIdleMetadata() {
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        buffer.insert(100L);
        buffer.reserve(70L);
        buffer.extract(30L);
        buffer.releaseReservation(20L);
        buffer.insert(50L);
        buffer.advanceIdle();

        assertThat(buffer.amount()).isEqualTo(120L);
        assertThat(buffer.reserved()).isEqualTo(20L);
        assertThat(buffer.idleExcess()).isEqualTo(100L);
        assertThat(buffer.idleTicks()).isZero();
    }

    @Test
    void outputPendingEnergySurvivesCompoundTagSaveLoad() {
        FluxEnergyBuffer pending = new FluxEnergyBuffer();
        assertThat(pending.insert(90L)).isEqualTo(90L);
        CompoundTag output = new CompoundTag();
        pending.save(output);
        FluxEnergyBuffer restored = new FluxEnergyBuffer();
        restored.load(output);

        assertThat(restored.amount()).isEqualTo(90L);
        assertThat(restored.idleExcess()).isEqualTo(90L);
    }
}
