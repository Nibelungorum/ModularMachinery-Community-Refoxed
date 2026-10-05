package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.MachineCapability;
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
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.ars_nouveau.ArsNouveauRecipeTypes;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.compat.botania.BotaniaRecipeTypes;
import cn.howxu.mmcr.compat.botania.ManaFailureReasons;
import cn.howxu.mmcr.compat.botania.ManaOutput;
import cn.howxu.mmcr.compat.botania.ManaPortCapability;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.compat.botania.ManaRequirementHandler;
import cn.howxu.mmcr.compat.botania.ManaStorage;
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

/** Runtime one-time mana consumption, restored markers and full completion output contracts.
 * @author howxu <dev@howxu.cn>
 */
class CraftingRuntimeManaConsumptionTest {
    private RequirementHandlerRegistry.TestScope requirementScope;
    private OutputRegistry.TestScope outputScope;

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @BeforeEach
    void registerManaDeclarations() {
        requirementScope = RequirementHandlerRegistry.openTestScope();
        outputScope = OutputRegistry.openTestScope();
        BotaniaRecipeTypes.register();
        ManaRequirement.installHandler(new ManaRequirementHandler());
    }

    @AfterEach
    void closeManaDeclarations() {
        ManaRequirement.installUnavailableHandler();
        outputScope.close();
        requirementScope.close();
    }

    @Test
    void savedConsumptionMarkersAcceptOnlyPhysicalManaInputsAtOriginalIndexes() {
        List<MachineRequirement> requirements = List.of(ManaRequirement.output(100L), ManaRequirement.input(300L), new EnergyRequirement(20L));
        var saved = ActiveMachineRecipe.InputConsumptionPlan.deserialize(new ActiveMachineRecipe.InputConsumptionPlan(List.of(0, 1, 0)).serialize());
        assertThat(saved.isValidFor(requirements)).isTrue();
        assertThat(saved.isValidFor(activeRecipe("mana_validation", requirements).getRecipe())).isTrue();
        assertThat(new ActiveMachineRecipe.InputConsumptionPlan(List.of(1, 1, 0)).isValidFor(requirements)).isFalse();
        assertThat(new ActiveMachineRecipe.InputConsumptionPlan(List.of(0, 1, 1)).isValidFor(requirements)).isFalse();
        assertThat(new ActiveMachineRecipe.InputConsumptionPlan(List.of(1)).isValidFor(requirements)).isFalse();
    }

    @Test
    void actualStartTickAndRestoredTickConsumeManaOnceAndOutputOnlyAtCompletion() throws Exception {
        ManaStorage inputStorage = new ManaStorage(() -> {});
        inputStorage.setAmount(900L);
        ManaStorage outputStorage = new ManaStorage(() -> {});
        outputStorage.setAmount(10L);
        var controller = controller(List.of(new ManaPortCapability(null, inputStorage, IOType.INPUT),
                new ManaPortCapability(null, outputStorage, IOType.OUTPUT)));
        var recipe = MachineRecipe.fromCanonical(MMCR.id("mana_real_start"), MMCR.id("test_cube"), 5,
                List.of(ManaRequirement.input(300L)), List.of(new ManaOutput(100L)), List.of(), 0, 1, false, false, false, Set.of());
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());

        runtime.start(recipe, 1L, null);

        assertThat(runtime.active()).isTrue();
        assertThat(runtime.failure()).isNull();
        assertThat(inputStorage.amount()).isEqualTo(600);
        assertThat(outputStorage.amount()).isEqualTo(10);
        var active = runtime.activeRecipe();
        assertThat(active.inputConsumptionPlan().consumedInputBatches()).containsExactly(1, 0);
        runtime.tick();
        assertThat(runtime.failure()).isNull();
        assertThat(inputStorage.amount()).isEqualTo(600);
        assertThat(outputStorage.amount()).isEqualTo(10);
        active.setInputConsumptionPlan(ActiveMachineRecipe.InputConsumptionPlan.deserialize(active.inputConsumptionPlan().serialize()));
        var restored = new CraftingRuntime(controller, controller.componentRuntime());
        restoreCurrent(restored, active, controller);
        assertThat(readField(restored, "consumedAtStart")).isEqualTo(Set.of(0));
        restored.tick();
        assertThat(restored.failure()).isNull();
        assertThat(inputStorage.amount()).isEqualTo(600);
        assertThat(outputStorage.amount()).isEqualTo(10);
        active.setTick(active.getTotalTick() - 1);
        active.beginFinishCommit();
        restored.finish();
        assertThat(restored.active()).isFalse();
        assertThat(inputStorage.amount()).isEqualTo(600);
        assertThat(outputStorage.amount()).isEqualTo(110);
        restored.finish();
        assertThat(outputStorage.amount()).isEqualTo(110);
    }

    @Test
    void capturedParallelManaBatchStaysConsumedAfterMarkerRoundTripAndRestore() throws Exception {
        List<MachineRequirement> requirements = List.of(ManaRequirement.input(300L));
        var active = activeRecipe("mana_capture", requirements);
        active.setMaxParallelism(2L);
        active.setParallelism(2L);
        var runtime = runtime(active, requirements);
        ManaStorage storage = new ManaStorage(() -> {});
        storage.setAmount(900L);
        var input = new ManaPortCapability(null, storage, IOType.INPUT);
        var plan = new CraftingPlan(List.of(new RequirementPlan(0, 2L,
                List.of(input.prepare(new CapabilityRequests.ValueRequest(BotaniaManaIds.TYPE, IOType.INPUT, 2L, 600L, false))), null)),
                2L, Map.of(0, RecipeModifier.IOType.INPUT));
        assertThat(plan.commitInputs()).isTrue();
        capture(runtime, requirements, plan);
        assertThat(storage.amount()).isEqualTo(300);
        assertThat(active.inputConsumptionPlan().consumedInputBatches()).containsExactly(1);
        assertThat(requirements(runtime, "perTickRequirements")).isEmpty();
        active.setInputConsumptionPlan(ActiveMachineRecipe.InputConsumptionPlan.deserialize(active.inputConsumptionPlan().serialize()));
        var restored = runtime(null, List.of());
        restored.restore(active, null, 0L, 0L, 0L, 0L);
        assertThat(restored.active()).isTrue();
        assertThat(restored.parallelism()).isEqualTo(2L);
        assertThat(readField(restored, "consumedAtStart")).isEqualTo(Set.of(0));
        assertThat(requirements(restored, "perTickRequirements")).isEmpty();
        assertThat(storage.amount()).isEqualTo(300);
    }

    @Test
    void mixedEnergyAndSourceIoPreserveOriginalManaConsumptionIndexes() throws Exception {
        ArsNouveauRecipeTypes.register();
        List<MachineRequirement> requirements = List.of(new EnergyRequirement(20L), SourceRequirement.input(40L),
                ManaRequirement.output(100L), ManaRequirement.input(300L), ManaRequirement.input(50L));
        var active = activeRecipe("mana_prefetch_capture", requirements);
        var runtime = runtime(active, requirements);
        capture(runtime, requirements, new CraftingPlan(List.of(operation(1), operation(3), new RequirementPlan(4, 1L, List.of(), null)),
                1L, Map.of(1, RecipeModifier.IOType.INPUT, 3, RecipeModifier.IOType.INPUT, 4, RecipeModifier.IOType.INPUT)));
        assertThat(readField(runtime, "consumedAtStart")).isEqualTo(Set.of(1, 3));
        assertThat(readField(runtime, "retainedInputs")).isEqualTo(Set.of(4));
        assertThat(active.inputConsumptionPlan().consumedInputBatches()).containsExactly(0, 1, 0, 1, 0);
        var restored = runtime(null, List.of());
        restored.restore(active, null, 0L, 0L, 0L, 0L);
        assertThat(restored.active()).isTrue();
        assertThat(readField(restored, "consumedAtStart")).isEqualTo(Set.of(1, 3));
        assertThat(readField(restored, "retainedInputs")).isEqualTo(Set.of(4));
        assertThat(requirements(restored, "perTickRequirements")).containsExactly(requirements.get(0), requirements.get(4));
    }

    @Test
    void restoredAndAsyncPreparedCompletionBlockThenRetryWholeParallelManaOutputOnce() throws Exception {
        for (boolean asyncPrepared : List.of(false, true)) {
            ManaStorage storage = new ManaStorage(() -> {});
            storage.setAmount(storage.capacity() - 100L);
            var controller = controller(List.of(new ManaPortCapability(null, storage, IOType.OUTPUT)));
            var recipe = MachineRecipe.fromCanonical(MMCR.id("mana_exact_finish_" + asyncPrepared), MMCR.id("test_cube"), 3,
                    List.of(), List.of(new ManaOutput(100L)), List.of(), 0, 1, false, true, false, Set.of());
            var active = new ActiveMachineRecipe(recipe, 2L);
            active.setParallelism(2L);
            active.setInputConsumptionPlan(new ActiveMachineRecipe.InputConsumptionPlan(List.of(0)));
            var runtime = new CraftingRuntime(controller, controller.componentRuntime());
            restoreCurrent(runtime, active, controller);
            assertThat(runtime.active()).isTrue();
            active.setTick(active.getTotalTick() - 1);
            active.beginFinishCommit();
            if (asyncPrepared) assertThat(runtime.prepareAsyncFinish()).isTrue();
            runtime.finish();
            assertThat(runtime.active()).isTrue();
            assertThat(runtime.finishPending()).isTrue();
            assertThat(runtime.parallelism()).isEqualTo(2L);
            assertThat(runtime.failure().reason()).isEqualTo(ManaFailureReasons.OUTPUT_BLOCKED);
            assertThat(storage.amount()).isEqualTo(storage.capacity() - 100);
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
    void manaOutputIsSelectedOnlyForCompletion() throws Exception {
        List<MachineRequirement> requirements = List.of(ManaRequirement.input(300L), ManaRequirement.output(100L));
        var active = activeRecipe("mana_finish_selection", requirements);
        var runtime = runtime(active, requirements);
        capture(runtime, requirements, new CraftingPlan(List.of(operation(0), operation(1)), 1L,
                Map.of(0, RecipeModifier.IOType.INPUT, 1, RecipeModifier.IOType.OUTPUT)));
        assertThat(requirements(runtime, "perTickRequirements")).isEmpty();
        assertThat(requirements(runtime, "finishRequirements")).containsExactlyElementsOf(requirements);
        Method method = CraftingRuntime.class.getDeclaredMethod("finishOutputs", RecipeFinishContext.class);
        method.setAccessible(true);
        assertThat((List<?>) method.invoke(runtime, new RecipeFinishContext(active.getRecipe(), 2L, 2L, List.of(new ManaOutput(100L)))))
                .isEqualTo(List.of(new ManaOutput(100L)));
    }

    private static ActiveMachineRecipe activeRecipe(String id, List<MachineRequirement> requirements) {
        var recipe = new MachineRecipe(MMCR.id(id), MMCR.id("test_cube"), 3, requirements, List.of(), List.of(), 0, 1, false, false, false, Set.of());
        return new ActiveMachineRecipe(recipe, 1L);
    }

    private static RequirementPlan operation(int index) {
        return new RequirementPlan(index, 1L, List.of(CapabilityResult::successful), null);
    }

    private static MachineControllerBlockEntity controller(List<MachineCapability> capabilities) throws Exception {
        var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        setField(controller.componentRuntime(), "capabilities", capabilities);
        return controller;
    }

    private static void restoreCurrent(CraftingRuntime runtime, ActiveMachineRecipe active, MachineControllerBlockEntity controller) {
        var snapshot = controller.currentRuntimeSnapshot();
        runtime.restore(active, controller.resourceDomain(), snapshot.structure().version(), snapshot.capabilityVersion(), snapshot.modifierVersion(), snapshot.stateVersion());
    }

    private static CraftingRuntime runtime(ActiveMachineRecipe active, List<MachineRequirement> requirements) throws Exception {
        var controller = controller(List.of());
        var runtime = new CraftingRuntime(controller, controller.componentRuntime());
        setField(runtime, "activeRecipe", active);
        setField(runtime, "effectiveRequirements", requirements);
        return runtime;
    }

    private static void capture(CraftingRuntime runtime, List<MachineRequirement> requirements, CraftingPlan plan) throws Exception {
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

    private static Object readField(Object value, String name) throws Exception {
        Field field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(value);
    }

    private static void setField(Object value, String name, Object contents) throws Exception {
        Field field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(value, contents);
    }
}
