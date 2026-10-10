package cn.howxu.mmcr.internal.runtime.ui;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot;
import cn.howxu.mmcr.api.data.DataValue;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.machine.definition.TickBehavior;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.internal.runtime.ControllerRuntimeSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerScreenTextSnapshot;
import cn.howxu.mmcr.internal.runtime.ControllerSyncRuntime;
import cn.howxu.mmcr.internal.runtime.CraftingStateSnapshot;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.runtime.FactorySnapshot;
import cn.howxu.mmcr.internal.runtime.StructureSnapshot;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.CaptureCache;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.LaneProgress;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ownership, projection reuse and baseline-matched progress tests without stack runtime dependencies.
 * @author howxu <dev@howxu.cn>
 */
class ControllerUiSnapshotTest {
    private static final ResourceLocation MACHINE = id("ui_machine");
    private static final ResourceLocation RECIPE = id("ui_recipe");
    private static final ResourceLocation POOL = id("ui_pool");
    private static final UUID SESSION = UUID.randomUUID();
    private static final AtomicInteger ENCODINGS = new AtomicInteger();
    private static final OutputType<MutableOutput> OUTPUT_TYPE = new OutputType.Definition<>(id("ui_mutable_output"),
            Codec.LONG.xmap(MutableOutput::new, value -> value.amount).fieldOf("amount"),
            (value, chance) -> value, (value, modifiers) -> value, value -> value,
            RecipeSyncCodec.of(16, (buffer, value) -> {
                ENCODINGS.incrementAndGet();
                buffer.writeLong(value.amount);
            }, buffer -> new MutableOutput(buffer.readLong()), value -> {}));

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        OutputRegistry.register(OUTPUT_TYPE);
    }

    @Test
    void normal_capture_owns_server_pool_selection_storage_and_both_text_scopes() {
        MutableComponent global = Component.literal("global");
        MutableComponent operation = Component.literal("operation");
        List<ResourceLocation> pools = new ArrayList<>(List.of(POOL, id("other_pool")));
        Map<String, ControllerScreenTextSnapshot> text = Map.of("", text(4, global),
                "base", new ControllerScreenTextSnapshot(7, List.of(new ControllerScreenTextSnapshot.Line(
                        ControllerScreenTextScope.OPERATION, id("operation"), operation))));
        ControllerUiSnapshotData snapshot = ControllerUiSnapshotData.capture(SESSION, 1, Level.OVERWORLD,
                new BlockPos.MutableBlockPos(1, 2, 3), runtime(1, 0, 0, FactorySnapshot.empty(), presentation(),
                        Map.of("mode", DataValue.list(List.of(DataValue.of("work"))))), POOL, true, pools, text);
        global.append(" changed");
        operation.append(" changed");
        pools.clear();

        assertThat(snapshot.kind()).isEqualTo(ControllerUiSnapshot.Kind.NORMAL);
        assertThat(snapshot.role()).isEqualTo(ControllerUiSnapshot.Role.NORMAL);
        assertThat(snapshot.lanes()).hasSize(1);
        assertThat(snapshot.lanes().getFirst().id()).isEqualTo("base");
        assertThat(snapshot.lanes().getFirst().base()).isTrue();
        assertThat(snapshot.currentRecipePoolId()).contains(POOL);
        assertThat(snapshot.recipePoolIds()).containsExactly(POOL, id("other_pool"));
        assertThat(snapshot.dataStorageValues().get("mode").asList().orElseThrow().getFirst().stringValue()).isEqualTo("work");
        assertThat(snapshot.lines().getFirst().text().getString()).isEqualTo("global");
        assertThat(snapshot.lanes().getFirst().lines().getFirst().text().getString()).isEqualTo("operation");
        ((MutableComponent) snapshot.machineName()).append(" changed");
        ((MutableComponent) snapshot.lines().getFirst().text()).append(" changed");
        ((MutableComponent) snapshot.lanes().getFirst().lines().getFirst().text()).append(" changed");
        assertThat(snapshot.machineName().getString()).isEqualTo("machine.mmcr.ui_machine");
        assertThat(snapshot.lines().getFirst().text().getString()).isEqualTo("global");
        assertThat(snapshot.lanes().getFirst().lines().getFirst().text().getString()).isEqualTo("operation");
        assertThat(snapshot.dataStorageValues()).isUnmodifiable();
        assertThat(snapshot.lanes()).isUnmodifiable();
    }

    @Test
    void empty_storage_is_distinct_from_missing_storage_in_the_full_shape() {
        ControllerRuntimeSnapshot runtime = runtime(1, 0, 0, FactorySnapshot.empty(), presentation(), Map.of());
        ControllerUiSnapshotData missing = capture(new CaptureCache(), 1, runtime, false, Map.of());
        ControllerUiSnapshotData empty = capture(new CaptureCache(), 1, runtime, true, Map.of());
        assertThat(missing.dataStorageValues()).isEmpty();
        assertThat(empty.dataStorageValues()).isEmpty();
        assertThat(empty.hasDataStorage()).isTrue();
        assertThat(missing.hasDataStorage()).isFalse();
        assertThat(empty.sameShape(missing)).isFalse();
    }

    @Test
    void tick_only_runtime_has_no_fake_recipe_lane_and_uses_configured_identity_when_unformed() {
        DynamicMachine machine = new DynamicMachine(MACHINE, "Tick", new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(MACHINE), MachineAppearanceSpec.defaults(),
                PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1,
                List.of(), MachineRole.NORMAL, Set.of(), List.of(), RecipeFailureActions.getDefaultAction(),
                TickBehavior.builder().build());
        StructureSnapshot structure = new StructureSnapshot(machine, null, null, null, Direction.SOUTH,
                Direction.SOUTH, 0, false, 1L, null, null, null, false, true, Set.of());
        ControllerRuntimeSnapshot runtime = new ControllerRuntimeSnapshot(structure, 0, 0, 0, Map.of(), Map.of(),
                Set.of(), ModuleConnectionStatus.notRequired(), 0, CraftingStateSnapshot.empty(1, 0, 0),
                FactorySnapshot.empty(), List.of(), List.of(), List.of(), "", "", 0, false, false, 0, 0, 1);
        ControllerUiSnapshotData snapshot = capture(new CaptureCache(), 1, runtime, false, Map.of());
        assertThat(snapshot.machineId()).isEqualTo(MACHINE);
        assertThat(snapshot.kind()).isEqualTo(ControllerUiSnapshot.Kind.TICK);
        assertThat(snapshot.lanes()).isEmpty();
        assertThatThrownBy(() -> snapshot.withProgress(2, List.of(new LaneProgress("base", RECIPE, 1, 20, 3))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factory_projection_uses_same_runtime_role_modules_connections_and_all_lanes() {
        ControllerRecipePresentation presentation = presentation();
        FactorySnapshot factory = factory(List.of(thread("base", 0, true, false, 1, presentation),
                thread("core", 1, false, true, 4, presentation)));
        ControllerRuntimeSnapshot host = runtime(1, 1, 4, factory, presentation, Map.of());
        ControllerUiSnapshotData snapshot = capture(new CaptureCache(), 1, host, true,
                Map.of("core", text(1, Component.literal("core text"))));
        assertThat(snapshot.kind()).isEqualTo(ControllerUiSnapshot.Kind.FACTORY);
        assertThat(snapshot.role()).isEqualTo(ControllerUiSnapshot.Role.HOST);
        assertThat(snapshot.installedModuleCount()).isEqualTo(host.installedModuleCount());
        assertThat(snapshot.lanes()).extracting(ControllerUiSnapshot.Lane::id).containsExactly("base", "core");
        assertThat(snapshot.lanes().get(1).core()).isTrue();
        assertThat(snapshot.lanes().get(1).lines().getFirst().text().getString()).isEqualTo("core text");
        assertThat(new ControllerSyncRuntime().factoryState(host, POOL).installedModuleCount())
                .isEqualTo(host.installedModuleCount());
        assertThat(snapshot.sameShape(capture(new CaptureCache(), 2,
                runtime(1, 1, 5, factory, presentation, Map.of()), true,
                Map.of("core", text(1, Component.literal("core text")))))).isFalse();
        ControllerUiSnapshotData module = capture(new CaptureCache(), 1,
                runtime(1, 2, 0, factory, presentation, Map.of()), true, Map.of());
        assertThat(module.role()).isEqualTo(ControllerUiSnapshot.Role.MODULE);
        assertThat(module.connectedHostId()).contains(id("host"));
    }

    @Test
    void factory_header_rejects_negative_capacity_and_active_count_independently() {
        for (int[] counts : List.of(new int[]{-1, 0}, new int[]{1, -1})) {
            assertThatThrownBy(() -> new ControllerUiSnapshotData.HeaderData(MACHINE,
                    ControllerUiSnapshot.Kind.FACTORY, ControllerUiSnapshot.Role.NORMAL,
                    Component.translatable("machine.mmcr.ui_machine"), true, false, false, 0, null,
                    0, 1, List.of(), 0, 1, counts[0], counts[1], List.of(), null, null, false, Map.of(), List.of()))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid controller UI fields");
        }
    }

    @Test
    void repeated_tick_capture_reuses_owned_recipe_text_storage_and_header_without_encoding_outputs() {
        ControllerRecipePresentation presentation = presentation();
        CaptureCache cache = new CaptureCache();
        ControllerRuntimeSnapshot initial = runtime(1, 0, 0, FactorySnapshot.empty(), presentation,
                Map.of("mode", DataValue.of("work")));
        Map<String, ControllerScreenTextSnapshot> text = Map.of("", text(3, Component.literal("global")),
                "base", text(5, Component.literal("lane")));
        ControllerUiSnapshotData first = capture(cache, 1, initial, true, text);
        int encoded = ENCODINGS.get();
        ControllerRuntimeSnapshot progressed = initial.withRuntimeState(crafting(RECIPE, 2), initial.factory(),
                initial.capabilityPresentations(), initial.maxParallelism(), presentation);
        ControllerUiSnapshotData second = capture(cache, 2, progressed, true, Map.of("", text(3, Component.literal("global")),
                "base", text(5, Component.literal("lane"))));
        assertThat(second.sameShape(first)).isTrue();
        assertThat(second.header()).isSameAs(first.header());
        assertThat(second.dataStorageValues()).isSameAs(first.dataStorageValues());
        assertThat(second.laneData().getFirst().recipe()).isSameAs(first.laneData().getFirst().recipe());
        assertThat(second.laneData().getFirst().textLines()).isSameAs(first.laneData().getFirst().textLines());
        assertThat(second.header().textLines()).isSameAs(first.header().textLines());
        assertThat(ENCODINGS.get()).isEqualTo(encoded);
    }

    @Test
    void recipe_switch_and_lane_removal_replace_owned_values_and_drop_current_lane_cache() {
        ControllerRecipePresentation presentation = presentation();
        CaptureCache cache = new CaptureCache();
        ControllerRuntimeSnapshot normal = runtime(1, 0, 0, FactorySnapshot.empty(), presentation, Map.of());
        ControllerUiSnapshotData first = capture(cache, 1, normal, true,
                Map.of("base", text(1, Component.literal("lane"))));
        ControllerUiSnapshotData switched = capture(cache, 2, normal.withRuntimeState(crafting(id("next_recipe"), 1),
                normal.factory(), List.of(), 8, presentation), true, Map.of("base", text(1, Component.literal("lane"))));
        assertThat(switched.sameShape(first)).isFalse();
        assertThat(switched.laneData().getFirst().recipe()).isNotSameAs(first.laneData().getFirst().recipe());
        assertThat(switched.laneData().getFirst().textLines()).isNotSameAs(first.laneData().getFirst().textLines());

        FactorySnapshot two = factory(List.of(thread("base", 0, true, false, 1, presentation),
                thread("worker", 1, false, false, 1, presentation)));
        ControllerUiSnapshotData beforeRemoval = capture(cache, 3, runtime(1, 1, 0, two, presentation, Map.of()), true, Map.of());
        capture(cache, 4, runtime(1, 1, 0, factory(List.of(thread("base", 0, true, false, 1, presentation))),
                presentation, Map.of()), true, Map.of());
        int beforeReinstall = ENCODINGS.get();
        ControllerUiSnapshotData reinstalled = capture(cache, 5, runtime(1, 1, 0, two, presentation, Map.of()), true, Map.of());
        assertThat(reinstalled.laneData().get(1).recipe()).isNotSameAs(beforeRemoval.laneData().get(1).recipe());
        assertThat(ENCODINGS.get()).isEqualTo(beforeReinstall + 1);
        cache.clear();
        ControllerUiSnapshotData afterClear = capture(cache, 6, runtime(1, 1, 0, two, presentation, Map.of()), true, Map.of());
        assertThat(afterClear.laneData().getFirst().recipe()).isNotSameAs(reinstalled.laneData().getFirst().recipe());
    }

    @Test
    void progress_merges_only_matching_lanes_atomically_and_preserves_static_owned_identity() {
        ControllerUiSnapshotData snapshot = capture(new CaptureCache(), 10,
                runtime(1, 0, 0, FactorySnapshot.empty(), presentation(), Map.of()), true, Map.of());
        ControllerUiSnapshotData merged = snapshot.withProgress(11, List.of(new LaneProgress("base", RECIPE, 20, 20, 3)));
        assertThat(merged.lanes().getFirst().tick()).isEqualTo(20);
        assertThat(snapshot.lanes().getFirst().tick()).isEqualTo(1);
        assertThat(merged.header()).isSameAs(snapshot.header());
        assertThat(merged.laneData().getFirst().recipe()).isSameAs(snapshot.laneData().getFirst().recipe());
        assertThat(merged.sameShape(snapshot)).isTrue();
        for (LaneProgress invalid : List.of(new LaneProgress("missing", RECIPE, 2, 20, 3),
                new LaneProgress("base", id("wrong_recipe"), 2, 20, 3),
                new LaneProgress("base", RECIPE, 2, 21, 3), new LaneProgress("base", RECIPE, 2, 20, 4))) {
            assertThatThrownBy(() -> snapshot.withProgress(11, List.of(invalid))).isInstanceOf(IllegalArgumentException.class);
        }
        LaneProgress valid = new LaneProgress("base", RECIPE, 2, 20, 3);
        assertThatThrownBy(() -> snapshot.withProgress(11, List.of(valid, valid))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> snapshot.withProgress(10, List.of(valid))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LaneProgress("base", RECIPE, -1, 20, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LaneProgress("base", RECIPE, 21, 20, 3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nested_component_siblings_and_translatable_arguments_are_owned_on_capture_and_on_read() {
        MutableComponent sibling = Component.translatable("ui.test.sibling");
        MutableComponent argument = Component.translatable("ui.test.argument");
        MutableComponent source = Component.translatable("ui.test", argument).append(sibling);
        var line = new ControllerUiSnapshotData.TextLineData(id("nested"),
                ControllerUiSnapshot.TextLine.Scope.CONTROLLER, source);
        Component baseline = line.text();
        sibling.append(" live mutation");
        argument.append(" live mutation");
        assertThat(line.text()).isEqualTo(baseline);
        Component returned = line.text();
        ((MutableComponent) returned).append(" root reader mutation");
        ((MutableComponent) returned.getSiblings().getFirst()).append(" reader mutation");
        Object[] returnedArguments = ((TranslatableContents) returned.getContents()).getArgs();
        ((MutableComponent) returnedArguments[0]).append(" argument reader mutation");
        assertThat(line.text()).isEqualTo(baseline);
    }

    @Test
    void custom_output_is_frozen_even_when_registered_copy_returns_the_original_mutable_value() {
        MutableOutput live = new MutableOutput(2);
        var owned = new ControllerUiSnapshotData.OutputData(live, 6);
        int encoded = ENCODINGS.get();
        live.amount = 100;
        MutableOutput firstRead = (MutableOutput) owned.resource();
        MutableOutput secondRead = (MutableOutput) owned.resource();
        firstRead.amount = 200;
        assertThat(secondRead).isNotSameAs(firstRead);
        assertThat(secondRead.amount).isEqualTo(2);
        assertThat(((MutableOutput) owned.resource()).amount).isEqualTo(2);
        assertThat(ENCODINGS.get()).isEqualTo(encoded);
        assertThat(owned.amount()).isEqualTo(6);
        assertThat(owned).isEqualTo(new ControllerUiSnapshotData.OutputData(new MutableOutput(2), 6));
        assertThat(owned).isNotEqualTo(new ControllerUiSnapshotData.OutputData(new MutableOutput(3), 6));
    }

    @Test
    void text_revision_and_static_recipe_changes_require_full_shape_even_without_tick_changes() {
        CaptureCache cache = new CaptureCache();
        ControllerRuntimeSnapshot runtime = runtime(1, 0, 0, FactorySnapshot.empty(), presentation(), Map.of());
        ControllerUiSnapshotData first = capture(cache, 1, runtime, true, Map.of("base", text(1, Component.literal("old"))));
        ControllerUiSnapshotData textChanged = capture(cache, 2, runtime, true, Map.of("base", text(2, Component.literal("new"))));
        assertThat(textChanged.sameShape(first)).isFalse();
        ControllerRecipePresentation different = new ControllerRecipePresentation(List.of(
                new MachineOutputAmount(new MutableOutput(5), 15)), 9, 12, 0, 20, 3);
        ControllerUiSnapshotData outputChanged = capture(cache, 3,
                runtime.withRuntimeState(runtime.crafting(), runtime.factory(), List.of(), 8, different), true,
                Map.of("base", text(2, Component.literal("new"))));
        assertThat(outputChanged.sameShape(textChanged)).isFalse();
    }

    private static ControllerUiSnapshotData capture(CaptureCache cache, long revision, ControllerRuntimeSnapshot runtime,
                                                    boolean storage, Map<String, ControllerScreenTextSnapshot> text) {
        return cache.capture(SESSION, revision, Level.OVERWORLD, BlockPos.ZERO, runtime, POOL, storage, List.of(POOL), text);
    }

    private static ControllerRuntimeSnapshot runtime(int tick, int role, int modules, FactorySnapshot factory,
                                                     ControllerRecipePresentation presentation, Map<String, DataValue> storage) {
        StructureSnapshot structure = new StructureSnapshot(null, null, null, null, Direction.SOUTH, Direction.SOUTH,
                0, true, 1, null, null, null, false, true, Set.of());
        return new ControllerRuntimeSnapshot(structure, 1, 1, 1, Map.of(), Map.of(), Set.of(),
                role == 2 ? ModuleConnectionStatus.connected(id("host")) : ModuleConnectionStatus.notRequired(), modules,
                crafting(RECIPE, tick), factory, List.of(), List.of(), List.of("mmcr:steel"), MACHINE.toString(),
                "machine.mmcr.ui_machine", role, !factory.presentationLanes().isEmpty(),
                !factory.presentationLanes().isEmpty(), 2, 8, 8, storage, presentation);
    }

    private static CraftingStateSnapshot crafting(ResourceLocation recipe, int tick) {
        return new CraftingStateSnapshot(recipe, CraftingStatus.working(), null, 1, 1, 1, tick, 20, 3, 8);
    }

    private static ControllerRecipePresentation presentation() {
        return new ControllerRecipePresentation(List.of(new MachineOutputAmount(new MutableOutput(2), 6)), 9, 12, 0, 20, 3);
    }

    private static FactoryRuntime.ThreadSnapshot thread(String id, int index, boolean base, boolean core, int tick,
                                                         ControllerRecipePresentation presentation) {
        return new FactoryRuntime.ThreadSnapshot(index, id, base, core, true, RECIPE.toString(), tick, 20, 3,
                (ExecutionStatus) null, presentation);
    }

    private static FactorySnapshot factory(List<FactoryRuntime.ThreadSnapshot> threads) {
        ExecutionStatus failure = ExecutionStatus.blocked(id("failure"), MACHINE,
                FailureOccurrence.at(BuiltinFailureReasons.MISSING_ENERGY, MACHINE, FailurePhase.RUNTIME,
                        RECIPE, null, Map.of("required", "9")));
        return new FactorySnapshot(true, true, List.of(), threads.size(), threads.size(), 8, false,
                threads, "machine.mmcr.ui_machine", 2, failure, List.of("mmcr:steel"), 0, 1);
    }

    private static ControllerScreenTextSnapshot text(long revision, Component value) {
        return new ControllerScreenTextSnapshot(revision, List.of(new ControllerScreenTextSnapshot.Line(
                ControllerScreenTextScope.CONTROLLER, id("line"), value)));
    }

    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("mmcr", value); }

    /** Intentionally mutable custom resource with an unsafe copier, to exercise frozen-byte ownership.
     * @author howxu <dev@howxu.cn> */
    private static final class MutableOutput implements MachineOutput {
        private long amount;
        private MutableOutput(long amount) { this.amount = amount; }
        public OutputType<MutableOutput> outputType() { return OUTPUT_TYPE; }
        public float chance() { return 1; }
        public long amount() { return amount; }
    }
}
