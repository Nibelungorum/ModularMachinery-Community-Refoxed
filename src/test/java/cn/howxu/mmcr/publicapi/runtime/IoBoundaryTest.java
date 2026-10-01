package cn.howxu.mmcr.publicapi.runtime;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailureReason;
import cn.howxu.mmcr.api.data.view.DataStorage;
import cn.howxu.mmcr.api.machine.definition.MachineIoPlan;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.internal.api.facade.data.StorageAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.RequirementAdapters;
import cn.howxu.mmcr.internal.api.facade.recipe.PlanningAdapters;
import cn.howxu.mmcr.api.capability.plan.PlanningContext;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import net.neoforged.neoforge.energy.EnergyStorage;
import cn.howxu.mmcr.internal.api.facade.runtime.IoAdapters;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.publicapi.data.DataKey;
import cn.howxu.mmcr.publicapi.data.DataStore;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Public plans preserve deferred data writes, native IO commits and one-shot semantics.
 * @author howxu <dev@howxu.cn>
 */
class IoBoundaryTest {
    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }
    @BeforeEach
    void capabilities() throws Exception { TestBootstrap.bootstrapCapabilities(); }

    @Test
    void callback_exception_rolls_back_io_and_data_then_a_fresh_plan_commits_both() {
        var energy = new LongEnergyStorage(100, 100, null);
        energy.setAmount(10);
        AtomicInteger notices = new AtomicInteger();
        var nativeStore = new cn.howxu.mmcr.api.data.DataStorage(ignored -> notices.incrementAndGet());
        DataStore store = StorageAdapters.wrap(DataStorage.view(nativeStore));
        IoTransaction failing = plan(new EnergyHatchCapability(energy, IOType.INPUT))
                .addInput(RequirementAdapters.wrap(new EnergyRequirement(4)));
        assertNull(failing.simulate().failure());
        assertEquals(10L, energy.getAmountAsLong());
        assertThrows(IllegalStateException.class, () -> failing.commitData(transaction -> {
            store.set("balance", DataKey.of(6L), transaction);
            throw new IllegalStateException("addon failure");
        }));
        assertEquals(10L, energy.getAmountAsLong());
        assertFalse(store.contains("balance"));
        assertEquals(0, notices.get());
        assertFalse(failing.commit().successful());
        assertThrows(IllegalStateException.class, failing::simulate);
        var successful = plan(new EnergyHatchCapability(energy, IOType.INPUT))
                .addInput(RequirementAdapters.wrap(new EnergyRequirement(4)));
        successful.simulate();
        assertTrue(successful.commit(transaction -> store.set("balance", DataKey.of(6L), transaction)).successful());
        assertEquals(6L, energy.getAmountAsLong());
        assertEquals(6L, store.get("balance").orElseThrow().longValue());
        assertEquals(1, notices.get());
    }

    @Test
    void changing_storage_after_simulation_preserves_native_commits_and_failure() {
        var first = new LongEnergyStorage(100, 100, null);
        var second = new LongEnergyStorage(100, 100, null);
        first.setAmount(3); second.setAmount(7);
        var transaction = plan(new EnergyHatchCapability(first, IOType.INPUT), new EnergyHatchCapability(second, IOType.INPUT))
                .addInput(RequirementAdapters.wrap(new EnergyRequirement(8)));
        assertNull(transaction.simulate().failure());
        second.setAmount(0);
        AtomicInteger callbacks = new AtomicInteger();
        var result = transaction.commit(ignored -> callbacks.incrementAndGet());
        assertFalse(result.successful());
        assertEquals(0L, first.getAmountAsLong());
        assertEquals(1, callbacks.get());
        assertNotNull(result.failure());
        assertNotNull(result.failure().id());
        assertNotNull(result.failure().source());
        assertFalse(result.failure().trace().isEmpty());
    }

    @Test
    void partial_outputs_expose_accepted_amount_and_data_deduction_shares_the_commit() {
        var output = new LongEnergyStorage(10, 100, null);
        output.setAmount(7);
        var nativeStore = new cn.howxu.mmcr.api.data.DataStorage();
        DataStore store = StorageAdapters.wrap(DataStorage.view(nativeStore));
        store.set("balance", DataKey.of(20L));
        var transaction = plan(new EnergyHatchCapability(output, IOType.OUTPUT)).addOutput(
                RequirementAdapters.wrap(new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 8)), OutputMode.ALLOW_PARTIAL);
        var simulation = transaction.simulate();
        assertNull(simulation.failure());
        var accepted = simulation.outputs().getFirst();
        assertEquals(OutputFit.PARTIAL, accepted.fit());
        assertEquals(8L, accepted.requested());
        assertEquals(3L, accepted.accepted());
        assertTrue(transaction.commitData(tx -> store.set("balance", DataKey.of(20L - accepted.accepted()), tx)).successful());
        assertEquals(10L, output.getAmountAsLong());
        assertEquals(17L, store.get("balance").orElseThrow().longValue());
        assertFalse(transaction.commit().successful());
    }

    @Test
    void commit_without_simulation_consumes_plan_and_failed_simulation_retains_diagnostics() {
        IoTransaction unprepared = plan();
        assertFalse(unprepared.commit().successful());
        assertThrows(IllegalStateException.class, unprepared::simulate);
        var empty = new LongEnergyStorage(10, 10, null);
        var insufficient = plan(new EnergyHatchCapability(empty, IOType.INPUT))
                .addInput(RequirementAdapters.wrap(new EnergyRequirement(4)));
        var simulation = insufficient.simulate();
        assertFalse(simulation.energySatisfied());
        assertNotNull(simulation.failure());
        assertEquals(simulation.failure().id(), insufficient.commit().failure().id());
    }

    private static IoTransaction plan(MachineCapability... capabilities) {
        return IoAdapters.wrap(new MachineIoPlan(new CapabilitySnapshot(List.of(capabilities))));
    }

    @Test
    void extension_reservations_support_an_ordinary_native_energy_handler() {
        var energy = new EnergyStorage(100);
        energy.receiveEnergy(10, false);
        var planning = PlanningAdapters.wrap(List.of(new EnergyHatchCapability(energy, IOType.INPUT)),
                new PlanningContext(1, 0));
        var capability = planning.capabilities().getFirst();
        var storage = capability.longValues().orElseThrow();
        var reservations = planning.reservations();
        assertEquals(10L, reservations.valueAvailable(storage, false));
        assertTrue(reservations.reserveValue(storage, 4, false));
        assertEquals(6L, reservations.valueAvailable(storage, false));
        assertFalse(reservations.reserveValueTotal(storage, 7, false));
        assertEquals(10, energy.getEnergyStored());
        assertTrue(capability.prepareValue(IoDirection.INPUT, 1, 4, false).commit().success());
        assertEquals(6, energy.getEnergyStored());
    }

    @Test
    void io_snapshot_retains_live_storage_reads_and_core_tag_filtering() {
        var energy = new LongEnergyStorage(100, 100, null);
        energy.setAmount(10);
        var core = new MachineIoView(new CapabilitySnapshot(List.of(new EnergyHatchCapability(energy, IOType.INPUT))));
        IoSnapshot snapshot = IoAdapters.wrap(core);
        energy.setAmount(20);
        assertEquals(core.energyInput(), snapshot.energyInput());
        assertEquals(20L, snapshot.energyInput());
        assertEquals(core.forTags(Set.of("missing")).energyInput(), snapshot.forTags(Set.of("missing")).energyInput());
        assertEquals(core.displays().stream().map(v -> v.value()).toList(), snapshot.displays().stream().map(IoDisplayValue -> IoDisplayValue.value()).toList());
    }

    @Test
    void failure_keeps_reason_translation_diagnostics_and_every_trace_frame() {
        ResourceLocation source = ResourceLocation.parse("test:source");
        ResourceLocation recipe = ResourceLocation.parse("test:recipe");
        var reason = new FailureReason(ResourceLocation.parse("test:insufficient"), "test.failure.insufficient", 5);
        var occurrence = FailureOccurrence.at(reason, source,
                cn.howxu.mmcr.api.capability.status.FailurePhase.REQUIREMENT_PLAN, recipe, 2, Map.of("missing", "energy"))
                .append(source, cn.howxu.mmcr.api.capability.status.FailurePhase.CAPABILITY_COMMIT, null, null);
        RuntimeFailure failure = IoAdapters.wrap(ExecutionStatus.blocked(ResourceLocation.parse("test:blocked"), source, occurrence));
        assertEquals(reason.id(), failure.reasonId());
        assertEquals(reason.translationKey(), failure.reasonTranslationKey());
        assertEquals(Map.of("missing", "energy"), failure.details());
        assertEquals(FailureSeverity.BLOCKED, failure.severity());
        assertEquals(2, failure.trace().size());
        assertEquals(recipe, failure.trace().getFirst().recipeId());
        assertEquals(FailurePhase.REQUIREMENT_PLAN, failure.trace().getFirst().phase());
        assertEquals(FailurePhase.CAPABILITY_COMMIT, failure.trace().getLast().phase());
        assertNull(failure.trace().getLast().requirementIndex());
        assertThrows(UnsupportedOperationException.class, () -> failure.trace().clear());
    }
}
