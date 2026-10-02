package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.plan.CapabilityResult;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.api.capability.plan.CraftingPlan;
import cn.howxu.mmcr.api.capability.plan.RequirementPlan;
import cn.howxu.mmcr.api.machine.definition.RecipeFinishContext;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.RequirementHandlerRegistry;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauRecipeTypes;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.ars_nouveau.SourceOutput;
import cn.howxu.mmcr.compat.ars_nouveau.SourceFailureReasons;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirementHandler;
import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortCapability;
import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortStorage;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies one-time source input bookkeeping and completion-only source output selection.
 *
 * @author howxu <dev@howxu.cn>
 */
class CraftingRuntimeSourceConsumptionTest {
    private RequirementHandlerRegistry.TestScope requirementScope;
    private OutputRegistry.TestScope outputScope;

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void registerSourceDeclarations() {
        requirementScope = RequirementHandlerRegistry.openTestScope();
        outputScope = OutputRegistry.openTestScope();
        ArsNouveauRecipeTypes.register();
        SourceRequirement.installHandler(new SourceRequirementHandler());
    }

    @AfterEach
    void closeSourceDeclarations() {
        SourceRequirement.installUnavailableHandler();
        outputScope.close();
        requirementScope.close();
    }

    @Test
    void savedSourceConsumptionValidationAcceptsOnlyPhysicalInputMarkersAtMatchingIndexes() {
        List<MachineRequirement> requirements = List.of(SourceRequirement.output(100L), SourceRequirement.input(300L),
                new EnergyRequirement(20L));
        ActiveMachineRecipe.InputConsumptionPlan saved = ActiveMachineRecipe.InputConsumptionPlan.deserialize(
                new ActiveMachineRecipe.InputConsumptionPlan(List.of(0, 1, 0)).serialize());

        assertThat(saved.isValidFor(requirements)).isTrue();
        assertThat(saved.isValidFor(activeRecipe("source_validation", requirements).getRecipe())).isTrue();
        assertThat(new ActiveMachineRecipe.InputConsumptionPlan(List.of(1, 1, 0)).isValidFor(requirements)).isFalse();
        assertThat(new ActiveMachineRecipe.InputConsumptionPlan(List.of(0, 1, 1)).isValidFor(requirements)).isFalse();
        assertThat(new ActiveMachineRecipe.InputConsumptionPlan(List.of(1)).isValidFor(requirements)).isFalse();
    }

    @Test
    void capturedSourceInputIsExcludedFromPerTickRequirementsAndStaysConsumedAfterRestore() throws Exception {
        List<MachineRequirement> requirements = List.of(SourceRequirement.input(300L));
        ActiveMachineRecipe active = activeRecipe("source_capture", requirements);
        active.setParallelism(2L);
        CraftingRuntime runtime = runtime(active, requirements);
        SourcePortStorage storage = new SourcePortStorage(() -> { });
        storage.setAmount(900L);
        SourcePortCapability input = new SourcePortCapability(null, storage, IOType.INPUT);
        CraftingPlan plan = new CraftingPlan(List.of(new RequirementPlan(0, 2L,
                List.of(input.prepare(new CapabilityRequests.ValueRequest(ArsSourceIds.TYPE, IOType.INPUT,
                        2L, 600L, false))), null)), 2L,
                Map.of(0, RecipeModifier.IOType.INPUT));

        assertThat(plan.commitInputs()).isTrue();
        capture(runtime, requirements, plan);

        assertThat(storage.amount()).isEqualTo(300L);
        assertThat(readField(runtime, "consumedAtStart")).isEqualTo(Set.of(0));
        assertThat(active.inputConsumptionPlan().consumedInputBatches()).containsExactly(1);
        assertThat(requirements(runtime, "perTickRequirements")).isEmpty();

        CraftingRuntime restored = runtime(null, List.of());
        active.setInputConsumptionPlan(ActiveMachineRecipe.InputConsumptionPlan.deserialize(
                active.inputConsumptionPlan().serialize()));
        restored.restore(active, null, 0L, 0L, 0L, 0L);

        assertThat(restored.active()).isTrue();
        assertThat(readField(restored, "consumedAtStart")).isEqualTo(Set.of(0));
        assertThat(requirements(restored, "perTickRequirements")).isEmpty();
        assertThat(storage.amount()).isEqualTo(300L);
    }

    @Test
    void captureKeepsOriginalSourceIndexesWhenEnergyIsPrefetched() throws Exception {
        List<MachineRequirement> requirements = List.of(new EnergyRequirement(20L), SourceRequirement.output(100L),
                SourceRequirement.input(300L), SourceRequirement.input(50L));
        ActiveMachineRecipe active = activeRecipe("source_prefetch_capture", requirements);
        CraftingRuntime runtime = runtime(active, requirements);
        CraftingPlan plan = new CraftingPlan(List.of(operation(2),
                new RequirementPlan(3, 1L, List.of(), null)), 1L,
                Map.of(2, RecipeModifier.IOType.INPUT, 3, RecipeModifier.IOType.INPUT));

        capture(runtime, requirements, plan);

        assertThat(readField(runtime, "consumedAtStart")).isEqualTo(Set.of(2));
        assertThat(readField(runtime, "retainedInputs")).isEqualTo(Set.of(3));
        assertThat(active.inputConsumptionPlan().consumedInputBatches()).containsExactly(0, 0, 1, 0);
    }

    @Test
    void restoreUsesSavedConsumedAndRetainedSourceIndexes() throws Exception {
        List<MachineRequirement> requirements = List.of(SourceRequirement.output(100L), SourceRequirement.input(300L),
                SourceRequirement.input(50L));
        ActiveMachineRecipe active = activeRecipe("source_restore_indexes", requirements);
        active.setInputConsumptionPlan(new ActiveMachineRecipe.InputConsumptionPlan(List.of(0, 1, 0)));
        CraftingRuntime restored = runtime(null, List.of());

        restored.restore(active, null, 0L, 0L, 0L, 0L);

        assertThat(restored.active()).isTrue();
        assertThat(readField(restored, "consumedAtStart")).isEqualTo(Set.of(1));
        assertThat(readField(restored, "retainedInputs")).isEqualTo(Set.of(2));
        assertThat(requirements(restored, "perTickRequirements")).containsExactly(requirements.get(2));
        assertThat(active.inputConsumptionPlan().consumedInputBatches()).containsExactly(0, 1, 0);
    }

    @Test
    void restoredCompletionAndPreparedAsyncFinishWaitForTheWholeCommittedSourceBatch() throws Exception {
        for (boolean asyncPrepared : List.of(false, true)) {
            SourcePortStorage storage = new SourcePortStorage(() -> {});
            storage.setAmount(9_900L);
            SourcePortCapability output = new SourcePortCapability(null, storage, IOType.OUTPUT);
            MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
            Field capabilities = ComponentRuntime.class.getDeclaredField("capabilities");
            capabilities.setAccessible(true);
            capabilities.set(controller.componentRuntime(), List.of(output));
            MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("source_exact_finish_" + asyncPrepared),
                    MMCR.id("test_cube"), 3, List.of(), List.of(new SourceOutput(100L)), List.of(),
                    0, 1, false, true, false, Set.of());
            ActiveMachineRecipe active = new ActiveMachineRecipe(recipe, 2L);
            active.setParallelism(2L);
            active.setInputConsumptionPlan(new ActiveMachineRecipe.InputConsumptionPlan(List.of(0)));
            CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
            var snapshot = controller.currentRuntimeSnapshot();
            runtime.restore(active, controller.resourceDomain(), snapshot.structure().version(),
                    snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion());
            assertThat(runtime.active()).isTrue();
            active.setTick(active.getTotalTick() - 1);
            active.beginFinishCommit();
            if (asyncPrepared) assertThat(runtime.prepareAsyncFinish()).isTrue();

            runtime.finish();

            assertThat(runtime.active()).isTrue();
            assertThat(runtime.finishPending()).isTrue();
            assertThat(runtime.parallelism()).isEqualTo(2L);
            assertThat(runtime.failure().reason()).isEqualTo(SourceFailureReasons.OUTPUT_BLOCKED);
            assertThat(storage.amount()).isEqualTo(9_900L);
            storage.move(100L, false, false);
            active.markFinishBlocked(-10);
            if (asyncPrepared) assertThat(runtime.prepareAsyncFinish()).isTrue();
            runtime.finish();
            assertThat(runtime.active()).isFalse();
            assertThat(storage.amount()).isEqualTo(storage.capacity());
            runtime.finish();
            assertThat(storage.amount()).isEqualTo(storage.capacity());
        }
    }

    @Test
    void sourceOutputIsKeptForCompletionInsteadOfPerTickExecution() throws Exception {
        List<MachineRequirement> requirements = List.of(SourceRequirement.input(300L), SourceRequirement.output(100L));
        ActiveMachineRecipe active = activeRecipe("source_finish_selection", requirements);
        CraftingRuntime runtime = runtime(active, requirements);
        capture(runtime, requirements, new CraftingPlan(List.of(operation(0), operation(1)), 1L,
                Map.of(0, RecipeModifier.IOType.INPUT, 1, RecipeModifier.IOType.OUTPUT)));

        assertThat(requirements(runtime, "perTickRequirements")).isEmpty();
        assertThat(requirements(runtime, "finishRequirements")).containsExactlyElementsOf(requirements);
        Method finishOutputs = CraftingRuntime.class.getDeclaredMethod("finishOutputs", RecipeFinishContext.class);
        finishOutputs.setAccessible(true);
        assertThat((List<?>) finishOutputs.invoke(runtime, new RecipeFinishContext(active.getRecipe(), 2L, 2L,
                List.of(new SourceOutput(100L))))).isEqualTo(List.of(new SourceOutput(100L)));
    }

    private static ActiveMachineRecipe activeRecipe(String id, List<MachineRequirement> requirements) {
        MachineRecipe recipe = new MachineRecipe(MMCR.id(id), MMCR.id("test_cube"), 3,
                requirements, List.of(), List.of(), 0, 1, false, false, false, Set.of());
        return new ActiveMachineRecipe(recipe, 1L);
    }

    private static RequirementPlan operation(int index) {
        return new RequirementPlan(index, 1L, List.of(CapabilityResult::successful), null);
    }

    private static CraftingRuntime runtime(ActiveMachineRecipe active, List<MachineRequirement> requirements)
            throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        setField(runtime, "activeRecipe", active);
        setField(runtime, "effectiveRequirements", requirements);
        return runtime;
    }

    private static void capture(CraftingRuntime runtime, List<MachineRequirement> requirements, CraftingPlan plan)
            throws Exception {
        Method method = CraftingRuntime.class.getDeclaredMethod("captureInputState", List.class, CraftingPlan.class);
        method.setAccessible(true);
        method.invoke(runtime, requirements, plan);
    }

    @SuppressWarnings("unchecked")
    private static List<MachineRequirement> requirements(CraftingRuntime runtime, String name) throws Exception {
        Method method = CraftingRuntime.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return (List<MachineRequirement>) method.invoke(runtime);
    }

    private static Object readField(CraftingRuntime runtime, String name) throws Exception {
        Field field = CraftingRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(runtime);
    }

    private static void setField(CraftingRuntime runtime, String name, Object value) throws Exception {
        Field field = CraftingRuntime.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(runtime, value);
    }
}
