package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.StatusSeverity;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.network.PktFactoryControllerStatePayload;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Immutable controller sync and final payload tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class ControllerSyncRuntimeTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void machineProjectionContainsFactoryProgressModuleStateLevelsAndFailure() {
        ControllerRuntimeSnapshot runtime = runtimeSnapshot();

        MachineStateSnapshot state = new ControllerSyncRuntime().machineState(runtime);

        assertThat(state.formed()).isTrue();
        assertThat(state.active()).isTrue();
        assertThat(state.activeRecipe()).isEqualTo("mmcr:factory_recipe");
        assertThat(state.tick()).isEqualTo(4);
        assertThat(state.totalTick()).isEqualTo(20);
        assertThat(state.parallelism()).isEqualTo(6);
        assertThat(state.maxParallelism()).isEqualTo(8);
        assertThat(state.factoryThreadCount()).isEqualTo(2);
        assertThat(state.activeFactoryThreadCount()).isEqualTo(1);
        assertThat(state.moduleConnected()).isTrue();
        assertThat(state.installedModuleCount()).isEqualTo(2);
        assertThat(state.foundLevelIds()).containsExactly("mmcr:steel");
        assertThat(state.failure().details()).containsEntry("reason", "insufficient_energy");
    }

    @Test
    void factoryProjectionUsesImmutableLaneSnapshotsAndAggregatedParallelism() {
        FactorySnapshot factory = new ControllerSyncRuntime().factoryState(runtimeSnapshot());

        assertThat(factory.active()).isTrue();
        assertThat(factory.activeParallelism()).isEqualTo(6);
        assertThat(factory.laneLimit()).isEqualTo(2);
        assertThat(factory.presentationLanes()).hasSize(2).isUnmodifiable();
        assertThat(factory.foundLevelIds()).containsExactly("mmcr:steel");
        assertThat(factory.lanes()).isUnmodifiable();
    }

    @Test
    void sync_runtime_consumes_a_published_controller_snapshot() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        var machine = controller.runtimeSnapshot().structure().configuredMachine();
        RuntimeTestFixtures.publishStructure(controller, machine, true);

        MachineStateSnapshot state = new ControllerSyncRuntime().machineState(controller.runtimeSnapshot());

        assertThat(state.formed()).isTrue();
        assertThat(state.machineId()).isEqualTo("mmcr:test_cube");
        assertThat(state.moduleConnected()).isFalse();
    }

    @Test
    void clientPayloadSnapshotsDoNotRetainMutableFluidValuesOrRequireAnOwner() {
        ControllerRuntimeSnapshot runtime = runtimeSnapshot();
        PktMachineStatePayload payload = PktMachineStatePayload.from(new BlockPos(3, 4, 5), runtime);
        FluidStack fluid = payload.primaryFluid();
        fluid.setAmount(1);

        assertThat(payload.primaryFluid().getAmount()).isEqualTo(250);
        assertThat(payload.pos()).isEqualTo(new BlockPos(3, 4, 5));
        assertThat(payload.factoryThreadCount()).isEqualTo(2);
    }

    @Test
    void finalFactoryPayloadRoundTripsAllLaneAndLevelStateWithoutAnOwnerBlockEntity() {
        PktFactoryControllerStatePayload payload = new PktFactoryControllerStatePayload(BlockPos.ZERO,
                new ControllerSyncRuntime().factoryState(runtimeSnapshot()));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE);

        PktFactoryControllerStatePayload.STREAM_CODEC.encode(buffer, payload);
        PktFactoryControllerStatePayload decoded = PktFactoryControllerStatePayload.STREAM_CODEC.decode(buffer);

        assertThat(decoded.controllerPos()).isEqualTo(BlockPos.ZERO);
        assertThat(decoded.snapshot()).isEqualTo(payload.snapshot());
        assertThat(decoded.snapshot().presentationLanes()).hasSize(2);
    }

    private static ControllerRuntimeSnapshot runtimeSnapshot() {
        ExecutionStatus failure = new ExecutionStatus(MMCR.id("runtime_failure"), StatusSeverity.BLOCKED,
                MMCR.id("crafting_runtime"), Map.of("reason", "insufficient_energy"));
        FactoryRuntime.ThreadSnapshot activeLane = new FactoryRuntime.ThreadSnapshot(0, true, false, true,
                "mmcr:factory_recipe", 4, 20, 6, "", true, "mmcr:factory_recipe");
        FactoryRuntime.ThreadSnapshot idleLane = new FactoryRuntime.ThreadSnapshot(1, false, false, false,
                "", 0, 0, 1, "", false, "");
        CraftingStateSnapshot crafting = new CraftingStateSnapshot(MMCR.id("crafting_recipe"),
                CraftingStatus.working(), null, 7L, 8L, 9L, 3, 20, 2, 8, true, "mmcr:crafting_recipe");
        FactorySnapshot factory = new FactorySnapshot(true, true, List.of(crafting), 6, 2, 1, 8,
                false, List.of(activeLane, idleLane), "factory", 3, failure, List.of("mmcr:steel"));
        StructureSnapshot structure = new StructureSnapshot(null, null, null, null, null,
                net.minecraft.core.Direction.SOUTH, 1, true, 7L, null, null, null, false, true, Set.of());
        return new ControllerRuntimeSnapshot(structure, 8L, 9L, 10L, Map.of(), Map.of(), Set.of(),
                ModuleConnectionStatus.connected(MMCR.id("host")), 2,
                new ComponentRuntime.CapabilityAggregate(250L, 1000L,
                        new FluidStack(Fluids.WATER, 250), new FluidStack(Fluids.LAVA, 100)),
                crafting, factory, List.of(), List.of(), List.of("mmcr:steel"), "mmcr:machine", "machine", 1,
                true, true, 1, 2, 8);
    }
}
