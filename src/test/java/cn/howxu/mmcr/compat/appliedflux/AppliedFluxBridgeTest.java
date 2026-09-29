package cn.howxu.mmcr.compat.appliedflux;

import appeng.api.config.Actionable;
import appeng.api.networking.ticking.TickRateModulation;
import cn.howxu.mmcr.compat.appliedflux.loaded.FluxEnergyNetwork;
import cn.howxu.mmcr.compat.appliedflux.loaded.FluxEnergyTicker;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyInputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.capability.FluxEnergyOutputCapability;
import cn.howxu.mmcr.compat.appliedflux.loaded.storage.FluxEnergyBuffer;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the optional AppFlux bridge and its native network adapter.
 *
 * @author howxu <dev@howxu.cn>
 */
class AppliedFluxBridgeTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrapCapabilities();
    }

    @Test
    void unavailableBridgeHasNoPortsOrLoadedReferences() {
        AppliedFluxBridge bridge = AppliedFluxBridgeBootstrap.selectForTesting(false);

        assertThat(bridge.available()).isFalse();
        assertThat(bridge.portKinds()).isEmpty();
        assertThat(bridge.isPort("appflux_energy_input")).isFalse();
        assertThat(UnavailableAppliedFluxBridge.class.getDeclaredFields())
                .allMatch(field -> !field.getType().getName().contains("loaded"));
    }

    @Test
    void exactExtractionSimulatesBeforeModulating() {
        RecordingStorage storage = new RecordingStorage(100L, 100L);
        FluxEnergyNetwork network = new FluxEnergyNetwork(storage);

        assertThat(network.planExactExtract(40L).orElseThrow().commit().success()).isTrue();

        assertThat(storage.calls).containsExactly("extract:40:SIMULATE", "extract:40:SIMULATE",
                "extract:40:MODULATE");
        assertThat(storage.amount).isEqualTo(60L);
    }

    @Test
    void unavailableEnergyDoesNotOfferAnExactExtractionPlan() {
        RecordingStorage storage = new RecordingStorage(0L, 100L);
        FluxEnergyNetwork network = new FluxEnergyNetwork(storage);

        assertThat(network.planExactExtract(40L)).isEmpty();
        assertThat(storage.calls).containsExactly("extract:40:SIMULATE");
    }

    @Test
    void shortModulationIsReportedAsFailureAfterImmediateExtraction() {
        RecordingStorage storage = new RecordingStorage(100L, 100L);
        storage.modulatedExtractionLimit = 25L;
        FluxEnergyNetwork network = new FluxEnergyNetwork(storage);

        assertThat(network.planExactExtract(40L).orElseThrow().commit().success()).isFalse();

        assertThat(storage.calls).containsExactly("extract:40:SIMULATE", "extract:40:SIMULATE",
                "extract:40:MODULATE");
        assertThat(storage.amount).isEqualTo(75L);
    }

    @Test
    void successfulPrefetchImmediatelyUpdatesNetworkAndLocalBuffer() {
        RecordingStorage storage = new RecordingStorage(100L, 100L);
        FluxEnergyNetwork network = new FluxEnergyNetwork(storage);
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        FluxEnergyInputCapability capability = new FluxEnergyInputCapability(buffer, "test-input",
                network::planExactExtract);
        var plan = capability.planPrefetch(40L).orElseThrow();

        assertThat(plan.operation().commit().success()).isTrue();

        assertThat(storage.amount).isEqualTo(60L);
        assertThat(buffer.amount()).isEqualTo(40L);
        assertThat(buffer.reserved()).isEqualTo(40L);
    }

    @Test
    void partialOutputDrainRetainsPendingEnergyAndRefreshesAdmissionCapacity() {
        RecordingStorage storage = new RecordingStorage(0L, 100L);
        storage.modulatedInsertionLimit = 30L;
        FluxEnergyNetwork network = new FluxEnergyNetwork(storage);
        FluxEnergyBuffer pending = new FluxEnergyBuffer();
        pending.insert(80L);

        assertThat(network.drainPending(pending)).isEqualTo(30L);
        assertThat(pending.amount()).isEqualTo(50L);
        assertThat(network.admissionCapacity()).isEqualTo(70L);
    }

    @Test
    void idleReturnPreservesReservedEnergy() {
        RecordingStorage storage = new RecordingStorage(0L, 3_000L);
        FluxEnergyNetwork network = new FluxEnergyNetwork(storage);
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        buffer.insert(20_000L);
        buffer.reserve(5_000L);
        for (int tick = 0; tick < FluxEnergyBuffer.IDLE_DELAY_TICKS; tick++) buffer.advanceIdle();

        assertThat(network.returnIdle(buffer)).isEqualTo(3_000L);
        assertThat(storage.amount).isEqualTo(3_000L);
        assertThat(buffer.amount()).isEqualTo(17_000L);
        assertThat(buffer.reserved()).isEqualTo(5_000L);
        assertThat(buffer.idleExcess()).isEqualTo(12_000L);
    }

    @Test
    void outputTickerKeepsPendingOnUnavailableOrFullNetworks() {
        FluxEnergyBuffer pending = committedBuffer(80L);
        FluxEnergyOutputCapability capability = new FluxEnergyOutputCapability(pending);

        assertThat(FluxEnergyTicker.output(false, pending, capability, null)).isEqualTo(TickRateModulation.SLOWER);
        assertThat(pending.amount()).isEqualTo(80L);

        FluxEnergyNetwork fullNetwork = new FluxEnergyNetwork(new RecordingStorage(0L, 0L));
        assertThat(FluxEnergyTicker.output(true, pending, capability, fullNetwork)).isEqualTo(TickRateModulation.SLOWER);
        assertThat(pending.amount()).isEqualTo(80L);
        assertThat(capability.admissionBudget()).isZero();

        FluxEnergyBuffer empty = new FluxEnergyBuffer();
        assertThat(FluxEnergyTicker.output(true, empty, new FluxEnergyOutputCapability(empty), fullNetwork))
                .isEqualTo(TickRateModulation.SLEEP);
    }

    @Test
    void outputTickerDeductsOnlyAcceptedPendingAndRefreshesBudget() {
        RecordingStorage storage = new RecordingStorage(0L, 100L);
        storage.modulatedInsertionLimit = 30L;
        FluxEnergyNetwork network = new FluxEnergyNetwork(storage);
        FluxEnergyBuffer pending = committedBuffer(80L);
        FluxEnergyOutputCapability capability = new FluxEnergyOutputCapability(pending);

        assertThat(FluxEnergyTicker.output(true, pending, capability, network)).isEqualTo(TickRateModulation.FASTER);
        assertThat(pending.amount()).isEqualTo(50L);
        assertThat(capability.admissionBudget()).isEqualTo(70L);
    }

    @Test
    void inputTickerWaitsForTheIdleDelayBeforeReturningExcess() {
        RecordingStorage storage = new RecordingStorage(0L, 20_000L);
        FluxEnergyNetwork network = new FluxEnergyNetwork(storage);
        FluxEnergyBuffer buffer = committedBuffer(15_000L);

        assertThat(FluxEnergyTicker.input(true, 1, buffer, network)).isEqualTo(TickRateModulation.SLOWER);
        assertThat(storage.amount).isZero();
        assertThat(FluxEnergyTicker.input(true, 198, buffer, network)).isEqualTo(TickRateModulation.SLOWER);
        assertThat(storage.amount).isZero();
        assertThat(FluxEnergyTicker.input(true, 1, buffer, network)).isEqualTo(TickRateModulation.SLEEP);
        assertThat(storage.amount).isEqualTo(15_000L);

        FluxEnergyBuffer atSoftLimit = committedBuffer(FluxEnergyBuffer.IDLE_SOFT_LIMIT);
        assertThat(FluxEnergyTicker.input(true, FluxEnergyBuffer.IDLE_DELAY_TICKS, atSoftLimit, network))
                .isEqualTo(TickRateModulation.SLEEP);
    }

    private static FluxEnergyBuffer committedBuffer(long amount) {
        FluxEnergyBuffer buffer = new FluxEnergyBuffer();
        buffer.insert(amount);
        return buffer;
    }

    private static final class RecordingStorage implements FluxEnergyNetwork.EnergyStorage {
        private final List<String> calls = new ArrayList<>();
        private long amount;
        private final long capacity;
        private long modulatedExtractionLimit = Long.MAX_VALUE;
        private long modulatedInsertionLimit = Long.MAX_VALUE;

        private RecordingStorage(long amount, long capacity) {
            this.amount = amount;
            this.capacity = capacity;
        }

        @Override
        public long extract(long requested, boolean simulate) {
            Actionable action = Actionable.ofSimulate(simulate);
            calls.add("extract:" + requested + ":" + action);
            long extracted = Math.min(requested, amount);
            if (action == Actionable.MODULATE) {
                extracted = Math.min(extracted, modulatedExtractionLimit);
                amount -= extracted;
            }
            return extracted;
        }

        @Override
        public long insert(long requested, boolean simulate) {
            Actionable action = Actionable.ofSimulate(simulate);
            calls.add("insert:" + requested + ":" + action);
            long inserted = Math.min(requested, capacity - amount);
            if (action == Actionable.MODULATE) {
                inserted = Math.min(inserted, modulatedInsertionLimit);
                amount += inserted;
            }
            return inserted;
        }
    }
}
