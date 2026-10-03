package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.InterfacePredicates;
import cn.howxu.mmcr.api.machine.definition.MachineIoPlan;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.compat.fluxnetworks.FluxNetworksTestBootstrap;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkInputCapability;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkOutputCapability;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkPlugHandler;
import cn.howxu.mmcr.compat.fluxnetworks.loaded.FluxNetworkPointHandler;
import cn.howxu.mmcr.compat.kubejs.KubeJSApi;
import cn.howxu.mmcr.compat.kubejs.KubeJSInterfaceHelpers;
import cn.howxu.mmcr.internal.api.facade.runtime.IoAdapters;
import cn.howxu.mmcr.internal.api.facade.structure.StructureAdapters;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.publicapi.recipe.requirement.Requirements;
import cn.howxu.mmcr.publicapi.runtime.OutputFit;
import cn.howxu.mmcr.publicapi.runtime.OutputMode;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.energy.EnergyStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises existing public and KubeJS energy entry points against real Flux inventory and admission.
 *
 * @author howxu <dev@howxu.cn>
 */
class FluxNetworksEnergyApiTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        FluxNetworksTestBootstrap.bootstrap();
    }

    @Test
    void publicInputViewAndTransactionIncludeArrivedFluxEnergy() {
        EnergyStorage nativeStorage = new EnergyStorage(10);
        nativeStorage.receiveEnergy(10, false);
        FluxNetworkPointHandler point = chargedPoint(20L);
        CapabilitySnapshot snapshot = snapshot(new EnergyHatchCapability(nativeStorage, IOType.INPUT),
                new FluxNetworkInputCapability(point, () -> "public-input"));
        var view = IoAdapters.wrap(new MachineIoView(snapshot));
        var transaction = IoAdapters.wrap(new MachineIoPlan(snapshot));

        assertThat(view.energyInput()).isEqualTo(30L);
        transaction.addInput(Requirements.energy(25L));
        var simulation = transaction.simulate();
        assertThat(simulation.energySatisfied()).isTrue();
        assertThat(simulation.failure()).isNull();
        assertThat(view.energyInput()).isEqualTo(30L);
        assertThat(transaction.commit().successful()).isTrue();
        assertThat(view.energyInput()).isEqualTo(5L);
        assertThat(nativeStorage.getEnergyStored()).isZero();
        assertThat(point.getBuffer()).isEqualTo(5L);
        assertThat(transaction.commit().successful()).isFalse();
        assertThat(view.energyInput()).isEqualTo(5L);
    }

    @Test
    void outputSnapshotMatchesActualNonLinearAdmissionAndExactCommit() {
        EnergyStorage nativeStorage = new EnergyStorage(10);
        FluxNetworkPlugHandler plug = plug(100L);
        assertThat(plug.acceptRecipeEnergy(40L)).isTrue();
        CapabilitySnapshot snapshot = snapshot(new EnergyHatchCapability(nativeStorage, IOType.OUTPUT),
                new FluxNetworkOutputCapability(plug));
        var view = IoAdapters.wrap(new MachineIoView(snapshot));
        long available = view.energyOutputCapacity();
        assertThat(available).isEqualTo(30L);

        var oversized = IoAdapters.wrap(new MachineIoPlan(snapshot));
        oversized.addOutput(Requirements.energy(IoDirection.OUTPUT, available + 1L), OutputMode.REQUIRE_FULL);
        assertThat(oversized.simulate().failure()).isNotNull();
        assertThat(oversized.commit().successful()).isFalse();
        assertThat(nativeStorage.getEnergyStored()).isZero();
        assertThat(plug.getBuffer()).isEqualTo(40L);

        var exact = IoAdapters.wrap(new MachineIoPlan(snapshot));
        exact.addOutput(Requirements.energy(IoDirection.OUTPUT, available), OutputMode.REQUIRE_FULL);
        assertThat(exact.simulate().failure()).isNull();
        assertThat(exact.outputSimulations()).singleElement().satisfies(output -> {
            assertThat(output.requested()).isEqualTo(available);
            assertThat(output.accepted()).isEqualTo(available);
            assertThat(output.fit()).isEqualTo(OutputFit.FULL);
        });
        assertThat(plug.getBuffer()).isEqualTo(40L);
        assertThat(view.energyOutputCapacity()).isEqualTo(available);
        assertThat(exact.commit().successful()).isTrue();
        assertThat(nativeStorage.getEnergyStored()).isEqualTo(10);
        assertThat(plug.getBuffer()).isEqualTo(60L);
        assertThat(nativeStorage.getEnergyStored() + plug.getBuffer()).isEqualTo(40L + available);
        assertThat(view.energyOutputCapacity()).isZero();
    }

    @Test
    void independentPlugQueriesAccumulateAndValueOnlyPublicPlanSplitsOutput() {
        FluxNetworkPlugHandler first = plug(20L);
        FluxNetworkPlugHandler second = plug(20L);
        CapabilitySnapshot snapshot = snapshot(new FluxNetworkOutputCapability(first),
                new FluxNetworkOutputCapability(second));
        var view = IoAdapters.wrap(new MachineIoView(snapshot));
        var transaction = IoAdapters.wrap(new MachineIoPlan(snapshot));

        assertThat(view.energyOutputCapacity()).isEqualTo(40L);
        transaction.addOutput(Requirements.energy(IoDirection.OUTPUT, 25L), OutputMode.REQUIRE_FULL);
        assertThat(transaction.simulate().failure()).isNull();
        assertThat(view.energyOutputCapacity()).isEqualTo(40L);
        assertThat(first.getBuffer()).isZero();
        assertThat(second.getBuffer()).isZero();
        assertThat(transaction.commit().successful()).isTrue();
        assertThat(first.getBuffer()).isEqualTo(20L);
        assertThat(second.getBuffer()).isEqualTo(5L);
        assertThat(view.energyOutputCapacity()).isEqualTo(10L);
    }

    @Test
    void sameHandlerAliasesHaveTheSamePublicCapacityAndPlanAsOnePlug() {
        for (boolean mixed : List.of(false, true)) {
            FluxNetworkPlugHandler plug = plug(100L);
            assertThat(plug.acceptRecipeEnergy(40L)).isTrue();
            EnergyStorage fullNative = new EnergyStorage(1);
            fullNative.receiveEnergy(1, false);
            FluxNetworkOutputCapability first = new FluxNetworkOutputCapability(plug);
            FluxNetworkOutputCapability alias = new FluxNetworkOutputCapability(plug);
            CapabilitySnapshot snapshot = mixed ? snapshot(new EnergyHatchCapability(fullNative, IOType.OUTPUT),
                    first, alias) : snapshot(first, alias);
            var view = IoAdapters.wrap(new MachineIoView(snapshot));

            assertThat(view.energyOutputCapacity()).isEqualTo(20L);
            assertThat(view.energyOutputCapacity()).isEqualTo(20L);
            var oversized = IoAdapters.wrap(new MachineIoPlan(snapshot));
            oversized.addOutput(Requirements.energy(IoDirection.OUTPUT, 30L), OutputMode.REQUIRE_FULL);
            assertThat(oversized.simulate().failure()).isNotNull();
            assertThat(oversized.commit().successful()).isFalse();
            assertThat(plug.getBuffer()).isEqualTo(40L);
            assertThat(view.energyOutputCapacity()).isEqualTo(20L);

            var exact = IoAdapters.wrap(new MachineIoPlan(snapshot));
            exact.addOutput(Requirements.energy(IoDirection.OUTPUT, view.energyOutputCapacity()),
                    OutputMode.REQUIRE_FULL);
            assertThat(exact.simulate().failure()).isNull();
            assertThat(exact.outputSimulations()).singleElement().satisfies(output -> {
                assertThat(output.accepted()).isEqualTo(20L);
                assertThat(output.fit()).isEqualTo(OutputFit.FULL);
            });
            assertThat(plug.getBuffer()).isEqualTo(40L);
            assertThat(exact.commit().successful()).isTrue();
            assertThat(plug.getBuffer()).isEqualTo(60L);
            assertThat(plug.storage().amount()).isEqualTo(60L);
            assertThat(fullNative.getEnergyStored()).isEqualTo(1);
            assertThat(view.energyOutputCapacity()).isZero();
        }
    }

    @Test
    void nativeOutputAliasesAreCountedOnceByQueryAndPublicPlan() {
        EnergyStorage storage = new EnergyStorage(10);
        CapabilitySnapshot snapshot = snapshot(new EnergyHatchCapability(storage, IOType.OUTPUT),
                new EnergyHatchCapability(storage, IOType.OUTPUT));
        var view = IoAdapters.wrap(new MachineIoView(snapshot));
        var transaction = IoAdapters.wrap(new MachineIoPlan(snapshot));

        assertThat(view.energyOutputCapacity()).isEqualTo(10L);
        transaction.addOutput(Requirements.energy(IoDirection.OUTPUT, view.energyOutputCapacity()),
                OutputMode.REQUIRE_FULL);
        assertThat(transaction.simulate().failure()).isNull();
        assertThat(storage.getEnergyStored()).isZero();
        assertThat(transaction.commit().successful()).isTrue();
        assertThat(storage.getEnergyStored()).isEqualTo(10);
        assertThat(view.energyOutputCapacity()).isZero();
    }

    @Test
    void inactiveFluxOutputDoesNotInflatePublicCapacityOrPartialAcceptance() {
        EnergyStorage nativeStorage = new EnergyStorage(10);
        AtomicBoolean active = new AtomicBoolean(false);
        FluxNetworkPlugHandler plug = new FluxNetworkPlugHandler(active::get, () -> 100L, () -> {});
        plug.setLimit(100L);
        CapabilitySnapshot snapshot = snapshot(new EnergyHatchCapability(nativeStorage, IOType.OUTPUT),
                new FluxNetworkOutputCapability(plug));
        var view = IoAdapters.wrap(new MachineIoView(snapshot));
        var transaction = IoAdapters.wrap(new MachineIoPlan(snapshot));

        assertThat(view.energyOutputCapacity()).isEqualTo(10L);
        transaction.addOutput(Requirements.energy(IoDirection.OUTPUT, 25L), OutputMode.ALLOW_PARTIAL);
        assertThat(transaction.simulate().failure()).isNull();
        assertThat(transaction.outputSimulations()).singleElement().satisfies(output -> {
            assertThat(output.requested()).isEqualTo(25L);
            assertThat(output.accepted()).isEqualTo(10L);
            assertThat(output.fit()).isEqualTo(OutputFit.PARTIAL);
        });
        assertThat(transaction.commit().successful()).isTrue();
        assertThat(nativeStorage.getEnergyStored()).isEqualTo(10);
        assertThat(plug.getBuffer()).isZero();
        assertThat(view.energyOutputCapacity()).isZero();
        active.set(true);
        assertThat(view.energyOutputCapacity()).isEqualTo(100L);
    }

    @Test
    void kubejsEnergyInputKeepsExistingRequirementAndConsumesMixedInventory() {
        EnergyStorage nativeStorage = new EnergyStorage(10);
        nativeStorage.receiveEnergy(10, false);
        FluxNetworkPointHandler point = chargedPoint(20L);
        CapabilitySnapshot snapshot = snapshot(new EnergyHatchCapability(nativeStorage, IOType.INPUT),
                new FluxNetworkInputCapability(point, () -> "kubejs-input"));
        MachineRequirement requirement = MachineRequirement.fromInput(new KubeJSApi().energyInput(25L));
        MachineIoPlan plan = new MachineIoPlan(snapshot).addInput(requirement);

        assertThat(requirement).isInstanceOf(EnergyRequirement.class);
        assertThat(plan.simulate().failure()).isNull();
        assertThat(plan.energySatisfied()).isTrue();
        assertThat(point.getBuffer()).isEqualTo(20L);
        assertThat(plan.commit().successful()).isTrue();
        assertThat(nativeStorage.getEnergyStored() + point.getBuffer()).isEqualTo(5L);
    }

    @Test
    void kubejsEnergyOutputKeepsExistingRequirementAndCommitsToBothEndpoints() {
        EnergyStorage nativeStorage = new EnergyStorage(10);
        FluxNetworkPlugHandler plug = plug(20L);
        CapabilitySnapshot snapshot = snapshot(new EnergyHatchCapability(nativeStorage, IOType.OUTPUT),
                new FluxNetworkOutputCapability(plug));
        MachineRequirement requirement = MachineRequirement.fromInput(new KubeJSApi().energyOutput(25L));
        MachineIoPlan plan = new MachineIoPlan(snapshot).addOutput(requirement);

        assertThat(requirement).isInstanceOf(EnergyRequirement.class);
        assertThat(plan.simulate().failure()).isNull();
        assertThat(plug.getBuffer()).isZero();
        assertThat(plan.commit().successful()).isTrue();
        assertThat(nativeStorage.getEnergyStored()).isEqualTo(5);
        assertThat(plug.getBuffer()).isEqualTo(20L);
    }

    @Test
    void existingStructureHelpersPreserveNativeEnergyDirectionsAcrossFacades() {
        Block input = ModBlocks.BLOCKS.get("energy_input_hatch").get();
        Block output = ModBlocks.BLOCKS.get("energy_output_hatch").get();

        assertThat(blocks(InterfacePredicates.anyOfEnergyInput())).contains(input).doesNotContain(output);
        assertThat(blocks(InterfacePredicates.anyOfEnergyOutput())).contains(output).doesNotContain(input);
        assertThat(blocks(StructureAdapters.unwrap(BlockConditions.energyInput()))).contains(input).doesNotContain(output);
        assertThat(blocks(StructureAdapters.unwrap(BlockConditions.energyOutput()))).contains(output).doesNotContain(input);
        assertThat(KubeJSInterfaceHelpers.anyOfEnergyInput().matches(input.defaultBlockState())).isTrue();
        assertThat(KubeJSInterfaceHelpers.anyOfEnergyInput().matches(output.defaultBlockState())).isFalse();
        assertThat(KubeJSInterfaceHelpers.anyOfEnergyOutput().matches(output.defaultBlockState())).isTrue();
        assertThat(KubeJSInterfaceHelpers.anyOfEnergyOutput().matches(input.defaultBlockState())).isFalse();
    }

    private static List<Block> blocks(BlockPredicate predicate) {
        if (predicate.blockSupplier().isPresent()) return List.of(predicate.blockSupplier().orElseThrow().get());
        return predicate.alternatives().stream().flatMap(child -> blocks(child).stream()).toList();
    }

    private static CapabilitySnapshot snapshot(MachineCapability... capabilities) {
        return new CapabilitySnapshot(List.of(capabilities));
    }

    private static FluxNetworkPointHandler chargedPoint(long amount) {
        FluxNetworkPointHandler point = new FluxNetworkPointHandler(() -> true, () -> 1L, () -> {});
        point.setLimit(amount);
        point.requestWarmup(amount);
        point.onCycleStart();
        point.addToBuffer(point.getRequest());
        return point;
    }

    private static FluxNetworkPlugHandler plug(long capacity) {
        FluxNetworkPlugHandler plug = new FluxNetworkPlugHandler(() -> true, () -> capacity, () -> {});
        plug.setLimit(capacity);
        return plug;
    }
}
