package cn.howxu.mmcr.publicapi.ui;

import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.capability.status.ExecutionStatus;
import cn.howxu.mmcr.api.capability.status.FailureOccurrence;
import cn.howxu.mmcr.api.capability.status.FailurePhase;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.RecipeSyncCodec;
import cn.howxu.mmcr.internal.api.facade.recipe.OutputAdapters;
import cn.howxu.mmcr.internal.api.facade.ui.UiSnapshotAdapters;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.HeaderData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.LaneData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.OutputData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.RecipeData;
import cn.howxu.mmcr.internal.runtime.ui.ControllerUiSnapshotData.TextLineData;
import cn.howxu.mmcr.publicapi.runtime.OutputView;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Public facade preserves core values, pre-scaled amounts, and mutable-value isolation.
 * @author howxu <dev@howxu.cn>
 */
class UiSnapshotAdapterTest {
    private static final ResourceLocation MACHINE = id("facade_machine");
    private static final ResourceLocation RECIPE = id("facade_recipe");
    private static final ResourceLocation POOL = id("facade_pool");
    private static final OutputType<MutableOutput> OUTPUT_TYPE = new OutputType.Definition<>(id("facade_output"),
            Codec.LONG.xmap(MutableOutput::new, value -> value.amount).fieldOf("amount"),
            (value, chance) -> value, (value, modifiers) -> value, value -> value,
            RecipeSyncCodec.of(16, (buffer, value) -> buffer.writeLong(value.amount),
                    buffer -> new MutableOutput(buffer.readLong()), value -> {}));

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        OutputRegistry.register(OUTPUT_TYPE);
    }

    @Test
    void facade_maps_semantic_fields_nested_views_storage_and_typed_failures() {
        ControllerUiSnapshotData core = snapshot();
        ControllerUiSnapshot facade = UiSnapshotAdapters.wrap(core);
        assertThat(UiSnapshotAdapters.unwrap(facade)).isSameAs(core);
        assertThat(facade.sessionId()).isEqualTo(core.sessionId());
        assertThat(facade.revision()).isEqualTo(core.revision());
        assertThat(facade.ready()).isTrue();
        assertThat(facade.dimension()).isEqualTo(Level.OVERWORLD);
        assertThat(facade.controllerPos()).isEqualTo(core.controllerPos());
        assertThat(facade.machineId()).isEqualTo(MACHINE);
        assertThat(facade.kind()).isEqualTo(ControllerUiSnapshot.Kind.FACTORY);
        assertThat(facade.role()).isEqualTo(ControllerUiSnapshot.Role.MODULE);
        assertThat(facade.connectedHostId()).contains(id("facade_host"));
        assertThat(facade.installedModuleCount()).isEqualTo(core.installedModuleCount());
        assertThat(facade.currentRecipePoolId()).contains(POOL);
        assertThat(facade.recipePoolIds()).containsExactly(POOL);
        assertThat(facade.hasDataStorage()).isTrue();
        assertThat(facade.dataStorageValues().get("nested").asMap().orElseThrow().get("mode").stringValue()).isEqualTo("work");
        assertThat(facade.failure().orElseThrow().reasonId()).isEqualTo(BuiltinFailureReasons.MISSING_ENERGY.id());
        assertThat(facade.failure().orElseThrow().trace()).hasSize(1);
        assertThat(facade.lanes().getFirst().failure().orElseThrow().details()).containsEntry("required", "9");
        assertThat(facade.lanes().getFirst().id()).isEqualTo("core");
        assertThat(facade.lanes().getFirst().core()).isTrue();
        assertThat(facade.lanes().getFirst().recipeId()).contains(RECIPE);
        assertThat(facade.lanes().getFirst().recipe().energyInputPerTick()).isEqualTo(core.lanes().getFirst().recipe().energyInputPerTick());
        assertThat(facade.lines().getFirst().scope()).isEqualTo(ControllerUiSnapshot.TextLine.Scope.CONTROLLER);
        assertThat(facade.lanes().getFirst().lines().getFirst().scope()).isEqualTo(ControllerUiSnapshot.TextLine.Scope.OPERATION);
        assertThat(facade.lanes()).isUnmodifiable();
        assertThat(facade.dataStorageValues()).isUnmodifiable();
        assertThat(facade.failure().orElseThrow().details()).isUnmodifiable();
    }

    @Test
    void two_facades_cannot_mutate_each_others_components_or_custom_resources() {
        ControllerUiSnapshotData core = snapshot();
        ControllerUiSnapshot first = UiSnapshotAdapters.wrap(core);
        ControllerUiSnapshot second = UiSnapshotAdapters.wrap(core);
        ((MutableComponent) first.machineName()).append(" changed");
        ((MutableComponent) first.lines().getFirst().text()).append(" changed");
        ((MutableComponent) first.lanes().getFirst().lines().getFirst().text()).append(" changed");
        ControllerUiSnapshot.Output output = first.lanes().getFirst().recipe().outputs().getFirst();
        MutableOutput mutable = (MutableOutput) OutputAdapters.unwrap(output.resource());
        mutable.amount = 100;
        assertThat(second.machineName().getString()).isEqualTo("name");
        assertThat(second.lines().getFirst().text().getString()).isEqualTo("global");
        assertThat(second.lanes().getFirst().lines().getFirst().text().getString()).isEqualTo("operation");
        assertThat(output.resource().amount()).isEqualTo(2);
        assertThat(output.amount()).isEqualTo(6);
        assertThat(second.lanes().getFirst().recipe().outputs().getFirst().amount()).isEqualTo(6);
        assertThat(second.lanes().getFirst().recipe().outputs().getFirst().resource().amount()).isEqualTo(2);
        assertThat(core.laneData().getFirst().recipe().outputData().getFirst().amount()).isEqualTo(6);
    }

    @Test
    void isolated_resource_supplier_survives_derived_views_and_preserves_ordinary_runtime_identity() {
        MutableOutput live = new MutableOutput(2);
        OutputView runtime = OutputAdapters.wrap(live);
        assertThat(OutputAdapters.unwrap(runtime)).isSameAs(live);

        OutputData owned = new OutputData(live, 6);
        OutputView isolated = OutputAdapters.wrap(owned.resource(), owned::resource);
        live.amount = 100;
        for (OutputView view : List.of(isolated, isolated.copy(), isolated.withChance(0.5F),
                isolated.applyModifiers(List.of()))) {
            MutableOutput first = (MutableOutput) OutputAdapters.unwrap(view);
            MutableOutput second = (MutableOutput) OutputAdapters.unwrap(view);
            first.amount = 200;
            assertThat(second).isNotSameAs(first);
            assertThat(second.amount).isEqualTo(2);
            assertThat(view.amount()).isEqualTo(2);
            assertThat(OutputAdapters.unwrap(view).amount()).isEqualTo(2);
        }
        assertThat(runtime.amount()).isEqualTo(100);
        assertThat(owned.amount()).isEqualTo(6);
    }

    @Test
    void reconstructible_nested_values_own_constructor_inputs_and_round_trip_full_shape() {
        ControllerUiSnapshotData core = snapshot();
        HeaderData header = core.header();
        MutableComponent name = Component.literal("name");
        Map<String, DataValue> storage = new LinkedHashMap<>(header.dataStorageValues());
        HeaderData reconstructedHeader = new HeaderData(header.machineId(), header.kind(), header.role(), name,
                header.formed(), header.active(), header.redstonePaused(), header.installedModuleCount(), header.connectedHost(),
                header.matchedStage(), header.stageCount(), header.foundLevelIds(), header.parallelSlots(), header.maxParallelism(),
                header.threadLimit(), header.activeThreadCount(), header.recipePoolIds(), header.currentRecipePool(),
                header.runtimeFailure(), header.hasDataStorage(), storage, header.textLines());
        LaneData lane = core.laneData().getFirst();
        RecipeData recipe = lane.recipe();
        RecipeData reconstructedRecipe = new RecipeData(recipe.outputData().stream()
                .map(value -> new OutputData(value.resource(), value.amount())).toList(), recipe.energyInputPerTick(),
                recipe.energyOutputPerTick(), recipe.heatOutputPerTick(), recipe.durationTicks(), recipe.parallelism());
        LaneData reconstructedLane = new LaneData(lane.id(), lane.index(), lane.base(), lane.core(), lane.active(),
                lane.currentRecipe(), lane.tick(), lane.totalTick(), lane.parallelism(), lane.runtimeFailure(),
                lane.textLines(), reconstructedRecipe);
        ControllerUiSnapshotData reconstructed = new ControllerUiSnapshotData(core.sessionId(), core.revision(), core.ready(),
                core.dimension(), core.controllerPos(), reconstructedHeader, List.of(reconstructedLane));
        name.append(" changed");
        storage.clear();
        assertThat(reconstructed.sameShape(core)).isTrue();
        assertThat(reconstructed).isEqualTo(core);
        assertThatThrownBy(() -> UiSnapshotAdapters.unwrap(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ControllerUiSnapshotData snapshot() {
        var failure = ExecutionStatus.blocked(id("facade_failure"), MACHINE,
                FailureOccurrence.at(BuiltinFailureReasons.MISSING_ENERGY, MACHINE, FailurePhase.RUNTIME,
                        RECIPE, null, Map.of("required", "9")));
        var global = new TextLineData(id("global"), cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.TextLine.Scope.CONTROLLER,
                Component.literal("global"));
        var operation = new TextLineData(id("operation"), cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.TextLine.Scope.OPERATION,
                Component.literal("operation"));
        HeaderData header = new HeaderData(MACHINE, cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Kind.FACTORY,
                cn.howxu.mmcr.api.controller.ui.ControllerUiSnapshot.Role.MODULE, Component.literal("name"),
                true, true, false, 2, id("facade_host"), 1, 2, List.of(id("steel")), 2, 8, 1, 1,
                List.of(POOL), POOL, failure, true, Map.of("nested", DataValue.map(Map.of("mode", DataValue.of("work")))),
                List.of(global));
        RecipeData recipe = new RecipeData(List.of(new OutputData(new MutableOutput(2), 6)), 9, 12, 0, 20, 3);
        LaneData lane = new LaneData("core", 0, false, true, true, RECIPE, 4, 20, 3, failure, List.of(operation), recipe);
        return new ControllerUiSnapshotData(UUID.randomUUID(), 1, true, Level.OVERWORLD, new BlockPos(1, 2, 3), header, List.of(lane));
    }

    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("mmcr", value); }

    /** Mutable custom output validates that the facade never exposes the retained core value.
     * @author howxu <dev@howxu.cn> */
    private static final class MutableOutput implements MachineOutput {
        private long amount;
        private MutableOutput(long amount) { this.amount = amount; }
        public OutputType<MutableOutput> outputType() { return OUTPUT_TYPE; }
        public float chance() { return 1; }
        public long amount() { return amount; }
    }
}
