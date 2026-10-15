package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityHost;
import cn.howxu.mmcr.api.capability.CapabilitySnapshot;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.InputRoutingFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.tick.CapabilityTickPhase;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.api.machine.definition.MachineIoView;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.internal.port.PortFamilyIds;
import cn.howxu.mmcr.internal.runtime.ComponentRuntime;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises controller-local routing and persisted port isolation without changing storage identity.
 *
 * @author howxu <dev@howxu.cn>
 */
class InputIsolationStateTest {
    private static final HolderLookup.Provider LOOKUP = HolderLookup.Provider.create(Stream.empty());

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void controller_callback_contexts_keep_the_complete_current_routing_snapshot() throws Exception {
        DeclaredPort port = port(new BlockPos(1, 0, 0), IOType.INPUT, PortFamilyIds.ITEM, List.of(item(IOType.INPUT)));
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        attach(port, controller);
        controller.setFullyInputIsolated(true);
        Field runtimeField = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        MachineControllerRuntime runtime = (MachineControllerRuntime) runtimeField.get(controller);
        Field snapshotField = MachineIoView.class.getDeclaredField("snapshot");
        snapshotField.setAccessible(true);
        CapabilitySnapshot normal = (CapabilitySnapshot) snapshotField.get(runtime.behaviorContext().ioView());
        CapabilitySnapshot recipe = (CapabilitySnapshot) snapshotField.get(
                runtime.behaviorContext(controller.recipeScreenText("base")).ioView());
        CapabilitySnapshot tick = runtime.tickBehaviorContext().capabilityTickContext(CapabilityTickPhase.IDLE).capabilitySnapshot();

        for (CapabilitySnapshot snapshot : List.of(normal, recipe, tick)) {
            assertThat(snapshot.capabilities()).containsExactlyElementsOf(port.capabilities());
            assertThat(routing(snapshot)).isEqualTo(routing(controller.componentRuntime().capabilitySnapshot()));
            assertThat(routing(snapshot).inputPorts()).containsExactly(new InputRoutingFacet.Port(port.getBlockPos(), true));
        }
    }

    @Test
    void routing_changes_leave_old_snapshots_and_original_capability_occurrences_untouched() {
        MachineCapability input = item(IOType.INPUT);
        MachineCapability output = item(IOType.OUTPUT);
        DeclaredPort port = port(new BlockPos(9, 4, 2), IOType.INPUT, PortFamilyIds.ITEM, List.of(input, output, input));
        MachineControllerBlockEntity controller = controller(BlockPos.ZERO);
        attach(port, controller);
        ComponentRuntime runtime = controller.componentRuntime();
        CapabilitySnapshot before = runtime.capabilitySnapshot();
        InputRoutingFacet oldRouting = routing(before);
        Object reservationIdentity = input.facet(ItemHandlerFacet.class).orElseThrow().itemHandler();
        long capabilityVersion = runtime.capabilityVersion();
        long stateVersion = runtime.stateVersion();

        port.setInputIsolated(true);

        CapabilitySnapshot after = runtime.capabilitySnapshot();
        assertThat(after.capabilities()).containsExactly(input, output, input);
        for (int index = 0; index < before.capabilities().size(); index++) {
            assertThat(after.capabilities().get(index)).isSameAs(before.capabilities().get(index));
        }
        assertThat(input.facet(ItemHandlerFacet.class).orElseThrow().itemHandler()).isSameAs(reservationIdentity);
        assertThat(oldRouting.inputPorts()).containsExactly(new InputRoutingFacet.Port(port.getBlockPos(), false));
        assertThat(routing(after).inputPorts()).containsExactly(new InputRoutingFacet.Port(port.getBlockPos(), true));
        assertThat(routing(after).revision()).isGreaterThan(oldRouting.revision());
        assertThat(routing(after).sources()).containsExactly(
                new InputRoutingFacet.Source(0, port.getBlockPos()),
                new InputRoutingFacet.Source(1, port.getBlockPos()),
                new InputRoutingFacet.Source(2, port.getBlockPos()));
        assertThat(runtime.capabilityVersion()).isEqualTo(capabilityVersion);
        assertThat(runtime.stateVersion()).isGreaterThan(stateVersion);
        assertThatThrownBy(() -> oldRouting.inputPorts().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void effective_isolation_is_the_or_of_controller_and_local_policy_for_all_four_combinations() {
        DeclaredPort port = port(new BlockPos(1, 2, 3), IOType.INPUT, PortFamilyIds.FLUID, List.of());
        MachineControllerBlockEntity controller = controller(BlockPos.ZERO);
        attach(port, controller);

        for (boolean full : List.of(false, true)) {
            controller.setFullyInputIsolated(full);
            for (boolean local : List.of(false, true)) {
                port.setInputIsolated(local);
                assertThat(port.isInputIsolated()).isEqualTo(local);
                assertThat(routing(controller.componentRuntime().capabilitySnapshot()).inputPorts())
                        .containsExactly(new InputRoutingFacet.Port(port.getBlockPos(), full || local));
            }
        }
    }

    @Test
    void shared_port_notifies_both_owners_but_full_policy_only_changes_its_owner() {
        DeclaredPort port = port(new BlockPos(1, 0, 0), IOType.INPUT, PortFamilyIds.ITEM, List.of(item(IOType.INPUT)));
        MachineControllerBlockEntity first = controller(BlockPos.ZERO);
        MachineControllerBlockEntity second = controller(new BlockPos(3, 0, 0));
        attach(port, first, second);
        ComponentRuntime secondRuntime = second.componentRuntime();
        long secondVersion = secondRuntime.stateVersion();

        first.setFullyInputIsolated(true);

        assertThat(port.isInputIsolated()).isFalse();
        assertThat(secondRuntime.stateVersion()).isEqualTo(secondVersion);
        assertThat(routing(first.componentRuntime().capabilitySnapshot()).inputPorts().getFirst().isolated()).isTrue();
        assertThat(routing(secondRuntime.capabilitySnapshot()).inputPorts().getFirst().isolated()).isFalse();

        port.setInputIsolated(true);
        assertThat(secondRuntime.stateVersion()).isGreaterThan(secondVersion);
        assertThat(routing(secondRuntime.capabilitySnapshot()).inputPorts().getFirst().isolated()).isTrue();
        port.setInputIsolated(false);
        assertThat(routing(first.componentRuntime().capabilitySnapshot()).inputPorts().getFirst().isolated()).isTrue();
        assertThat(routing(secondRuntime.capabilitySnapshot()).inputPorts().getFirst().isolated()).isFalse();
    }

    @Test
    void masked_local_change_advances_search_state_and_syncs_once_without_changing_effective_routing() {
        DeclaredPort port = port(new BlockPos(1, 0, 0), IOType.INPUT, PortFamilyIds.ITEM, List.of());
        MachineControllerBlockEntity controller = controller(BlockPos.ZERO);
        Level level = attach(port, controller);
        controller.setFullyInputIsolated(true);
        ComponentRuntime runtime = controller.componentRuntime();
        long revision = runtime.inputRoutingRevision();
        long state = runtime.stateVersion();
        long capabilityVersion = runtime.capabilityVersion();
        long availability = controller.resourceAvailabilityEpoch();
        int updates = LevelStub.sentBlockUpdates(level);

        port.setInputIsolated(true);

        assertThat(runtime.inputRoutingRevision()).isEqualTo(revision);
        assertThat(runtime.stateVersion()).isGreaterThan(state);
        assertThat(controller.resourceAvailabilityEpoch()).isGreaterThan(availability);
        assertThat(LevelStub.sentBlockUpdates(level)).isGreaterThan(updates);
        long changedState = runtime.stateVersion();
        int changedUpdates = LevelStub.sentBlockUpdates(level);
        port.setInputIsolated(true);
        controller.setFullyInputIsolated(true);
        controller.onInputIsolationChanged();
        assertThat(runtime.stateVersion()).isEqualTo(changedState);
        assertThat(runtime.capabilityVersion()).isEqualTo(capabilityVersion);
        assertThat(LevelStub.sentBlockUpdates(level)).isEqualTo(changedUpdates);
    }

    @Test
    void closing_full_restores_local_settings_and_policy_survives_rebuilding_with_new_ports() {
        MachineControllerBlockEntity controller = controller(BlockPos.ZERO);
        DeclaredPort local = port(new BlockPos(2, 0, 0), IOType.INPUT, PortFamilyIds.ITEM, List.of());
        DeclaredPort ordinary = port(new BlockPos(1, 0, 0), IOType.INPUT, PortFamilyIds.FLUID, List.of());
        local.setInputIsolated(true);
        controller.componentRuntime().replaceComponents(List.of(component(local), component(ordinary)));
        controller.setFullyInputIsolated(true);
        controller.setFullyInputIsolated(false);
        assertThat(routing(controller.componentRuntime().capabilitySnapshot()).inputPorts()).containsExactly(
                new InputRoutingFacet.Port(ordinary.getBlockPos(), false),
                new InputRoutingFacet.Port(local.getBlockPos(), true));

        controller.setFullyInputIsolated(true);
        controller.componentRuntime().clear();
        DeclaredPort added = port(new BlockPos(5, 0, 0), IOType.INPUT, MekanismPortFamilies.CHEMICAL, List.of());
        controller.componentRuntime().replaceComponents(List.of(component(local), component(added)));
        assertThat(controller.isFullyInputIsolated()).isTrue();
        assertThat(added.isInputIsolated()).isFalse();
        assertThat(routing(controller.componentRuntime().capabilitySnapshot()).inputPorts()).containsExactly(
                new InputRoutingFacet.Port(local.getBlockPos(), true), new InputRoutingFacet.Port(added.getBlockPos(), true));
        controller.setFullyInputIsolated(false);
        assertThat(routing(controller.componentRuntime().capabilitySnapshot()).inputPorts()).containsExactly(
                new InputRoutingFacet.Port(local.getBlockPos(), true), new InputRoutingFacet.Port(added.getBlockPos(), false));
    }

    @Test
    void material_family_declarations_support_new_kinds_without_optional_loaded_types_or_size_metadata() {
        for (ResourceLocation family : List.of(PortFamilyIds.ITEM, PortFamilyIds.FLUID,
                MekanismPortFamilies.CHEMICAL, MekanismPortFamilies.RADIOACTIVE_CHEMICAL)) {
            DeclaredPort input = port(BlockPos.ZERO, IOType.INPUT, family, List.of());
            input.setInputIsolated(true);
            assertThat(input.supportsInputIsolation()).isTrue();
            assertThat(input.isInputIsolated()).isTrue();
            DeclaredPort output = port(BlockPos.ZERO, IOType.OUTPUT, family, List.of());
            output.setInputIsolated(true);
            assertThat(output.supportsInputIsolation()).isFalse();
            assertThat(output.isInputIsolated()).isFalse();
        }
        for (ResourceLocation family : List.of(PortFamilyIds.ENERGY, MekanismPortFamilies.HEAT, MMCR.id("smart_interface"))) {
            DeclaredPort unsupported = port(BlockPos.ZERO, IOType.INPUT, family, List.of());
            unsupported.setInputIsolated(true);
            assertThat(unsupported.supportsInputIsolation()).isFalse();
            assertThat(unsupported.isInputIsolated()).isFalse();
        }
        DeclaredPort outputFamily = new DeclaredPort(BlockPos.ZERO, new DeclaredKind(IOType.INPUT,
                List.of(new PortFamilyDescriptor(PortFamilyIds.ITEM, IOType.OUTPUT, 0, List.of()))), List.of());
        assertThat(outputFamily.supportsInputIsolation()).isFalse();
    }

    @Test
    void pattern_return_views_still_have_a_logical_input_port_and_legacy_material_hosts_stay_ordinary() {
        DeclaredPort pattern = port(new BlockPos(3, 2, 1), IOType.INPUT, PortFamilyIds.ITEM, List.of(item(IOType.OUTPUT)));
        MachineCapability legacyInput = item(IOType.INPUT);
        LegacyHost legacy = new LegacyHost(new BlockPos(4, 2, 1), List.of(legacyInput));
        ComponentRuntime runtime = new ComponentRuntime();
        BlockPos.MutableBlockPos componentPosition = new BlockPos.MutableBlockPos(-1, 7, 8);
        runtime.replaceComponents(List.of(component(pattern), new ProcessingComponent(null, legacy,
                componentPosition, BlockPos.ZERO, (String) null)));
        InputRoutingFacet before = routing(runtime.capabilitySnapshot());
        componentPosition.set(99, 99, 99);
        runtime.refreshInputRouting(true);

        assertThat(before.sources()).containsExactly(new InputRoutingFacet.Source(0, pattern.getBlockPos()),
                new InputRoutingFacet.Source(1, new BlockPos(-1, 7, 8)));
        assertThat(before.inputPorts()).containsExactly(new InputRoutingFacet.Port(new BlockPos(-1, 7, 8), false),
                new InputRoutingFacet.Port(pattern.getBlockPos(), false));
        assertThat(routing(runtime.capabilitySnapshot()).inputPorts()).containsExactly(
                new InputRoutingFacet.Port(new BlockPos(-1, 7, 8), false),
                new InputRoutingFacet.Port(pattern.getBlockPos(), true));
    }

    @Test
    void controller_and_port_persist_independent_policies_with_false_for_legacy_data() {
        MachineControllerBlockEntity controller = controller(BlockPos.ZERO);
        DeclaredPort port = port(new BlockPos(1, 0, 0), IOType.INPUT, PortFamilyIds.ITEM, List.of());
        controller.setFullyInputIsolated(true);
        port.setInputIsolated(true);
        CompoundTag controllerOutput = new CompoundTag();
        CompoundTag portOutput = new CompoundTag();
        controller.saveAdditional(controllerOutput, LOOKUP);
        port.saveAdditional(portOutput, LOOKUP);
        MachineControllerBlockEntity restored = controller(BlockPos.ZERO);
        DeclaredPort restoredPort = port(port.getBlockPos(), IOType.INPUT, PortFamilyIds.ITEM, List.of());
        restored.loadAdditional(controllerOutput, LOOKUP);
        restoredPort.loadAdditional(portOutput, LOOKUP);
        assertThat(restored.isFullyInputIsolated()).isTrue();
        assertThat(restoredPort.isInputIsolated()).isTrue();
        restored.componentRuntime().replaceComponents(List.of(component(restoredPort)));
        restored.setFullyInputIsolated(false);
        assertThat(restoredPort.isInputIsolated()).isTrue();
        assertThat(routing(restored.componentRuntime().capabilitySnapshot()).inputPorts().getFirst().isolated()).isTrue();

        restored.loadAdditional(new CompoundTag(), LOOKUP);
        restoredPort.loadAdditional(new CompoundTag(), LOOKUP);
        assertThat(restored.isFullyInputIsolated()).isFalse();
        assertThat(restoredPort.isInputIsolated()).isFalse();
    }

    private static MachineControllerBlockEntity controller(BlockPos pos) {
        return RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), pos);
    }

    private static DeclaredPort port(BlockPos pos, IOType direction, ResourceLocation family, List<MachineCapability> capabilities) {
        return new DeclaredPort(pos, new DeclaredKind(direction,
                List.of(new PortFamilyDescriptor(family, direction, 0, List.of()))), capabilities);
    }

    private static MachineCapability item(IOType direction) {
        return new ItemBusCapability(new LongItemStorage(1, 64L, () -> {}), direction);
    }

    private static ProcessingComponent component(BlockEntity host) {
        return new ProcessingComponent(null, host, host.getBlockPos(), BlockPos.ZERO, (String) null);
    }

    private static InputRoutingFacet routing(CapabilitySnapshot snapshot) {
        return snapshot.facets(InputRoutingFacet.class).getFirst();
    }

    private static Level attach(DeclaredPort port, MachineControllerBlockEntity... controllers) {
        List<BlockEntity> entities = Stream.concat(Stream.of(port), Stream.of(controllers)).toList();
        Level level = LevelStub.createWithBlockEntities(entities);
        port.setLevel(level);
        for (MachineControllerBlockEntity controller : controllers) {
            controller.setLevel(level);
            controller.componentRuntime().replaceComponents(List.of(component(port)));
            port.linkControllerAppearance(controller.getBlockPos(), null);
        }
        return level;
    }

    /** @author howxu <dev@howxu.cn> */
    private record DeclaredKind(IOType ioType, List<PortFamilyDescriptor> families) implements IOPortKind {
        @Override public String id() { return "isolation_state_test"; }
        @Override public PortDefinition definition() { return PortDefinition.of(MMCR.id(id()), List.of()); }
        @Override public BlockEntityType.BlockEntitySupplier<? extends IOPortBlockEntity> entityFactory() {
            return (pos, state) -> new DeclaredPort(pos, this, List.of());
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class DeclaredPort extends IOPortBlockEntity {
        private final IOPortKind kind;
        private final CapabilitySnapshot snapshot;

        private DeclaredPort(BlockPos pos, IOPortKind kind, List<MachineCapability> capabilities) {
            super(ModBlockEntities.BES.get(PortKinds.ITEM_INPUT.id()).get(), pos,
                    ModBlocks.BLOCKS.get(PortKinds.ITEM_INPUT.id()).get().defaultBlockState());
            this.kind = kind;
            snapshot = new CapabilitySnapshot(capabilities);
        }

        @Override public IOType ioType() { return kind.ioType(); }
        @Override public IOPortKind kind() { return kind; }
        @Override public CapabilitySnapshot capabilitySnapshot() { return snapshot; }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class LegacyHost extends BlockEntity implements CapabilityHost {
        private final CapabilitySnapshot snapshot;

        private LegacyHost(BlockPos pos, List<MachineCapability> capabilities) {
            super(ModBlockEntities.BES.get(PortKinds.ITEM_INPUT.id()).get(), pos,
                    ModBlocks.BLOCKS.get(PortKinds.ITEM_INPUT.id()).get().defaultBlockState());
            snapshot = new CapabilitySnapshot(capabilities);
        }

        @Override public CapabilitySnapshot capabilitySnapshot() { return snapshot; }
    }
}
