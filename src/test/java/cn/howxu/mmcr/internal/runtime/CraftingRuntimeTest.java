package cn.howxu.mmcr.internal.runtime;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.Machine;
import cn.howxu.mmcr.api.machine.MachineAppearanceSpec;
import cn.howxu.mmcr.api.machine.MachineControllerSpec;
import cn.howxu.mmcr.api.machine.MachineRole;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.RecipeFailureActions;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.publicapi.machine.MachineBehavior;
import cn.howxu.mmcr.api.publicapi.machine.RecipeBehavior;
import cn.howxu.mmcr.api.machine.SmartInterfaceType;
import cn.howxu.mmcr.api.recipe.helper.ProcessingComponent;
import cn.howxu.mmcr.internal.multiblock.ModuleConnectionStatus;
import cn.howxu.mmcr.internal.tile.EnergyInputHatchBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemInputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.ItemOutputBusBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.internal.tile.SmartInterfaceBlockEntity;
import cn.howxu.mmcr.internal.recipe.MachineRecipeThread;
import cn.howxu.mmcr.registry.ModBlockEntities;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.LevelStub;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Final crafting runtime behavior tests.
 *
 * @author howxu <dev@howxu.cn>
 */
class CraftingRuntimeTest {
    private static final HolderLookup.Provider EMPTY_LOOKUP = HolderLookup.Provider.create(Stream.empty());

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void cleanup() {
        RecipeRegistry.clearForTesting();
    }

    @Test
    void startTickAndFinishCommitInputsAndOutputs() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 2));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_complete", 1, List.of(
                input(Items.IRON_INGOT, 2), output(Items.IRON_NUGGET, 1)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        assertThat(runtime.tick().isCrafting()).isTrue();
        assertThat(runtime.finish().getStatus()).isEqualTo(cn.howxu.mmcr.api.recipe.helper.CraftingStatus.Status.IDLE);
        ItemStack result = output.getItemStackHandler(null).getStackInSlot(0);
        assertThat(result.getItem()).isEqualTo(Items.IRON_NUGGET);
        assertThat(result.getCount()).isEqualTo(1);
    }

    @Test
    void cancelled_start_stays_idle_without_failure_or_input_consumption() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        controller.setMachine(machine(controller.machineId(), RecipeBehavior.builder()
                .beforeStart(context -> context.cancel()).build()));
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("runtime_cancelled_start", 20,
                List.of(input(Items.IRON_INGOT, 1))), 1).getStatus())
                .isEqualTo(cn.howxu.mmcr.api.recipe.helper.CraftingStatus.Status.IDLE);
        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure()).isNull();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void finish_veto_uses_the_retry_gate() {
        AtomicInteger callbacks = new AtomicInteger();
        assertFinishRetryGate(RecipeBehavior.builder().beforeFinish(context -> {
            callbacks.incrementAndGet();
            context.cancel();
        }).build(), callbacks);
    }

    @Test
    void finish_callback_exception_uses_the_retry_gate() {
        AtomicInteger callbacks = new AtomicInteger();
        assertFinishRetryGate(RecipeBehavior.builder().beforeFinish(context -> {
            callbacks.incrementAndGet();
            throw new IllegalStateException("expected test callback failure");
        }).build(), callbacks);
    }

    @Test
    void invalid_finish_output_uses_the_retry_gate() {
        AtomicInteger callbacks = new AtomicInteger();
        assertFinishRetryGate(RecipeBehavior.builder().beforeFinish(context -> {
            callbacks.incrementAndGet();
            context.setOutputs(List.of(new MachineOutput.ItemOutput(ItemStack.EMPTY, 1F)));
        }).build(), callbacks);
    }

    @Test
    void failedStartReportsStructuredMissingResourceAndRollsBackRootTransaction() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_missing_input", 20, List.of(
                input(Items.IRON_INGOT, 2)));

        runtime.start(recipe, 1);

        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure()).isNotNull();
        assertThat(runtime.failure().details()).containsEntry("reason", "insufficient_resource");
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void duplicateInputRequirementsRemainAtomicWhenCombinedStorageIsInsufficient() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_duplicate_input", 20, List.of(
                input(Items.IRON_INGOT, 1), input(Items.IRON_INGOT, 1)));

        runtime.start(recipe, 1);

        assertThat(runtime.active()).isFalse();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void duplicateOutputRequirementsCommit_each_output_to_the_real_storage() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_duplicate_output", 1, List.of(
                output(Items.IRON_NUGGET, 1), output(Items.IRON_NUGGET, 1)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();
        assertThat(runtime.finish().getStatus()).isEqualTo(cn.howxu.mmcr.api.recipe.helper.CraftingStatus.Status.IDLE);
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(2);
    }

    @Test
    void redstonePauseAndResumeKeepTheActiveRuntime() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_pause", 20, List.of());

        runtime.start(recipe, 1);
        runtime.pause();
        assertThat(runtime.snapshot().status().isPaused()).isTrue();
        assertThat(runtime.active()).isTrue();

        runtime.resume();
        assertThat(runtime.snapshot().status().isCrafting()).isTrue();
    }

    @Test
    void capabilityVersionInvalidationCancelsTheActiveRuntime() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_invalidation", 20, List.of(input(Items.IRON_INGOT, 1)));

        runtime.start(recipe, 1);
        controller.componentRuntime().replaceComponents(List.of());
        controller.setMachine(controller.runtimeSnapshot().structure().configuredMachine());

        runtime.tick();

        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure().details()).containsEntry("reason", "version_invalidated");
    }

    @Test
    void perTickEnergyIsCommittedThroughTheRealEnergyCapability() {
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        energy.energyStorage().setAmount(10);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = new MachineRecipe(MMCR.id("runtime_energy"), MMCR.id("test_cube"), 3,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(new EnergyRequirement(2)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(8);
        runtime.tick();

        assertThat(energy.energyStorage().getAmountAsLong()).isEqualTo(6);
        assertThat(runtime.active()).isTrue();
    }

    @Test
    void missingPerTickEnergyReportsMissingEnergy() {
        EnergyInputHatchBlockEntity energy = RuntimeTestFixtures.energyInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), energy);
        energy.energyStorage().setAmount(2);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = new MachineRecipe(MMCR.id("runtime_missing_energy"), MMCR.id("test_cube"), 3,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(new EnergyRequirement(2)));

        runtime.start(recipe, 1);
        runtime.tick();

        assertThat(runtime.failureUnloc()).isEqualTo("gui.mmcr.controller.failure.missing_energy");
    }

    @Test
    void modifier_and_component_state_changes_invalidate_an_active_runtime() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_version_changes", 20, List.of());

        runtime.start(recipe, 1);
        controller.componentRuntime().replaceModifiers(Map.of("changed", List.of()));
        RuntimeTestFixtures.republish(controller);
        runtime.tick();
        assertThat(runtime.failure().details()).containsEntry("reason", "version_invalidated");

        runtime.start(recipe, 1);
        controller.componentRuntime().replaceModuleConnectionState(ModuleConnectionStatus.connected(MMCR.id("host")), 1);
        RuntimeTestFixtures.republish(controller);
        runtime.tick();
        assertThat(runtime.failure().details()).containsEntry("reason", "version_invalidated");
    }

    @Test
    void smart_interface_change_invalidates_an_active_runtime_with_a_dedicated_failure() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_smart_interface_change", 20, List.of());

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();

        runtime.invalidateForSmartInterfaceChange();

        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure()).isNotNull();
        assertThat(runtime.failure().details()).containsEntry("reason", "smart_interface_changed");
        assertThat(runtime.failureUnloc()).isEqualTo("gui.mmcr.controller.failure.smart_interface_changed");
    }

    @Test
    void recipe_thread_forwards_smart_interface_change_to_its_runtime() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipeThread thread = new MachineRecipeThread(controller);

        assertThat(thread.runtime().start(recipe("thread_smart_interface_change", 20, List.of()), 1)
                .isCrafting()).isTrue();

        thread.invalidateForSmartInterfaceChange();

        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.runtime().failure().details()).containsEntry("reason", "smart_interface_changed");
    }

    @Test
    void smart_interface_output_change_during_finish_is_applied_after_the_commit() {
        SmartInterfaceBlockEntity smartInterface = (SmartInterfaceBlockEntity) ModBlockEntities.SMART_INTERFACE.get()
                .create(new BlockPos(1, 0, 0), ModBlocks.SMART_INTERFACE.get().defaultBlockState());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        smartInterface.setLevel(controller.getLevel());
        assertThat(smartInterface.claimController(controller.getBlockPos(), MMCR.id("test_cube"), Map.of(
                "mode", new SmartInterfaceType("mode", 1F, 0)), false)).isTrue();
        controller.componentRuntime().replaceComponents(List.of(new ProcessingComponent(
                null, smartInterface, smartInterface.getBlockPos(), smartInterface.getBlockPos(), (String) null)));
        CraftingRuntime runtime = controllerRuntime(controller);
        MachineRecipe recipe = new MachineRecipe(MMCR.id("runtime_smart_interface_output"), MMCR.id("test_cube"), 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(SmartInterfaceRequirement.output("mode", 9F)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        runtime.tick();

        assertThat(runtime.finish()).isEqualTo(cn.howxu.mmcr.api.recipe.helper.CraftingStatus.failure(
                "gui.mmcr.controller.failure.smart_interface_changed"));
        assertThat(runtime.active()).isFalse();
        assertThat(runtime.failure()).isNotNull();
        assertThat(runtime.failure().details()).containsEntry("reason", "smart_interface_changed");
        assertThat(smartInterface.value("mode")).contains(9F);
    }

    @Test
    void zero_consume_chance_retains_the_input_across_start_and_tick() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = new MachineRecipe(MMCR.id("runtime_retain_input"), MMCR.id("test_cube"), 2,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), List.of(
                new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                        ItemStack.EMPTY, 1F, List.of(),
                        DataComponentPredicateSet.EMPTY, 0F)));

        runtime.start(recipe, 1);
        runtime.tick();

        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void zeroChanceOutputDoesNotMutateStorageAtFinish() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_zero_chance", 1, List.of(
                new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                        stack(Items.IRON_NUGGET, 2), 0F, List.of())));

        runtime.start(recipe, 1);
        runtime.tick();
        runtime.finish();

        assertThat(output.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
    }

    @Test
    void positive_output_and_consume_chance_commit_through_real_item_storage() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        ItemRequirement consumed = new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1,
                ItemStack.EMPTY, 1F, List.of(), DataComponentPredicateSet.EMPTY, 1F);
        ItemRequirement produced = new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                stack(Items.IRON_NUGGET, 1), 1F, List.of(), DataComponentPredicateSet.EMPTY, 1F);
        MachineRecipe recipe = recipe("runtime_positive_chance", 1, List.of(consumed, produced));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).isEmpty()).isTrue();
        runtime.tick();
        runtime.finish();

        assertThat(output.getItemStackHandler(null).getStackInSlot(0).is(Items.IRON_NUGGET)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void partialOutputCommitsAvailableStorageWithoutLeakingTheRemainder() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        for (int slot = 1; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, stack(Items.COBBLESTONE, 64));
        }
        output.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 44));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = new MachineRecipe(MMCR.id("runtime_partial_output"), MMCR.id("test_cube"), 1,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(),
                List.of(output(Items.IRON_INGOT, 64)), false, List.of(), true);

        runtime.start(recipe, 1);
        runtime.tick();
        runtime.finish();

        assertThat(output.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(64);
    }

    @Test
    void blocked_finish_stays_pending_until_the_output_retry_window_opens() {
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(1, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), output);
        var level = LevelStub.createWithBlockEntities(List.of(controller, output));
        controller.setLevel(level);
        output.setLevel(level);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());
        MachineRecipe recipe = recipe("runtime_finish_retry", 1, List.of(output(Items.IRON_NUGGET, 1)));

        assertThat(runtime.start(recipe, 1).isCrafting()).isTrue();
        for (int slot = 0; slot < output.getItemStackHandler(null).getSlots(); slot++) {
            output.getItemStackHandler(null).setStackInSlot(slot, stack(Items.COBBLESTONE, 64));
        }
        runtime.tick();

        assertThat(runtime.finishPending()).isTrue();
        assertThat(runtime.finish().getStatus()).isEqualTo(cn.howxu.mmcr.api.recipe.helper.CraftingStatus.Status.NO_RECIPE);
        assertThat(runtime.active()).isTrue();
        assertThat(runtime.shouldRetryFinish()).isFalse();

        output.getItemStackHandler(null).setStackInSlot(0, ItemStack.EMPTY);
        LevelStub.setGameTime(level, 10);

        assertThat(runtime.shouldRetryFinish()).isTrue();
        assertThat(runtime.finish().getStatus()).isEqualTo(cn.howxu.mmcr.api.recipe.helper.CraftingStatus.Status.IDLE);
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).is(Items.IRON_NUGGET)).isTrue();
    }

    @Test
    void active_runtime_persists_finish_state_and_input_plan() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_persisted", 1, List.of());
        RecipeRegistry.register(recipe);
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        runtime.start(recipe, 1);
        runtime.tick();
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        runtime.save(output);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), null);

        assertThat(restored.active()).isTrue();
        assertThat(restored.finishPending()).isTrue();
        assertThat(restored.recipe()).isEqualTo(recipe);
        assertThat(restored.activeRecipe().inputConsumptionPlan().consumedBatches(0)).isZero();
    }

    @Test
    void active_runtime_finishes_with_its_original_recipe_after_registry_replacement() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        MachineRecipe oldRecipe = recipe("runtime_reload_active", 1, List.of(
                input(Items.IRON_INGOT, 1), output(Items.IRON_NUGGET, 1)));
        MachineRecipe replacement = recipe("runtime_reload_active", 1, List.of(
                input(Items.GOLD_INGOT, 1), output(Items.DIAMOND, 1)));
        RecipeRegistry.replaceDynamic(Map.of(oldRecipe.id(), oldRecipe));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(oldRecipe, 1).isCrafting()).isTrue();
        RecipeRegistry.replaceDynamic(Map.of(replacement.id(), replacement));

        runtime.tick();
        runtime.finish();

        assertThat(runtime.active()).isFalse();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).is(Items.IRON_NUGGET)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).is(Items.DIAMOND)).isFalse();
    }

    @Test
    void active_runtime_load_uses_embedded_old_definition_and_does_not_extract_inputs_again() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        MachineRecipe oldRecipe = recipe("runtime_reload_persisted", 1, List.of(
                input(Items.IRON_INGOT, 1), output(Items.IRON_NUGGET, 1)));
        MachineRecipe replacement = recipe("runtime_reload_persisted", 1, List.of(
                input(Items.IRON_INGOT, 1), output(Items.DIAMOND, 1)));
        RecipeRegistry.replaceDynamic(Map.of(oldRecipe.id(), oldRecipe));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(oldRecipe, 1).isCrafting()).isTrue();

        TagValueOutput outputTag = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        saved.save(outputTag);
        var savedRecipeTag = outputTag.buildResult().getCompound("recipe").orElseThrow();
        assertThat(savedRecipeTag.getBooleanOr("has_recipe_definition", false)).isTrue();
        assertThat(savedRecipeTag.getCompound("recipe_definition").orElseThrow().isEmpty()).isFalse();
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        RecipeRegistry.replaceDynamic(Map.of(replacement.id(), replacement));

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), outputTag.buildResult()), null);

        assertThat(restored.recipe()).isNotNull();
        assertThat(restored.recipe().id()).isEqualTo(oldRecipe.id());
        assertThat(restored.recipe().outputs()).singleElement()
                .satisfies(recipeOutput -> assertThat(recipeOutput.is(Items.IRON_NUGGET)).isTrue());
        restored.tick();
        restored.finish();

        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).is(Items.IRON_NUGGET)).isTrue();
        assertThat(output.getItemStackHandler(null).getStackInSlot(0).is(Items.DIAMOND)).isFalse();
    }

    @Test
    void active_runtime_loads_old_nbt_without_an_embedded_definition_from_the_registry() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_old_nbt", 20, List.of());
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        saved.save(output);
        var savedRecipeTag = output.buildResult().getCompound("recipe").orElseThrow();
        savedRecipeTag.remove("has_recipe_definition");
        savedRecipeTag.remove("recipe_definition");
        savedRecipeTag.remove("recipe_definition_version");
        savedRecipeTag.remove("recipe_definition_fingerprint");
        savedRecipeTag.remove("has_effective_definition");
        savedRecipeTag.remove("effective_definition_version");
        savedRecipeTag.remove("effective_duration");
        savedRecipeTag.remove("effective_requirements");
        savedRecipeTag.remove("effective_outputs");

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), null);

        assertThat(restored.active()).isTrue();
        assertThat(restored.recipe()).isEqualTo(recipe);
    }

    @Test
    void invalid_embedded_definition_loads_as_a_failed_idle_runtime_without_resource_operations() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_invalid_embedded", 20, List.of());
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        saved.save(output);
        var savedRecipeTag = output.buildResult().getCompound("recipe").orElseThrow();
        savedRecipeTag.remove("recipe_definition");

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), null);

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().details()).containsEntry("reason", "recipe_load");
    }

    @Test
    void corrupted_embedded_definition_fingerprint_loads_as_a_failed_idle_runtime() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_corrupt_fingerprint", 20, List.of());
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        saved.save(output);
        var savedRecipeTag = output.buildResult().getCompound("recipe").orElseThrow();
        savedRecipeTag.putString("recipe_definition_fingerprint", "0".repeat(64));

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), null);

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().details()).containsEntry("reason", "recipe_load");
    }

    @Test
    void malformed_input_consumption_plan_fails_before_restore_and_resource_operations() {
        ItemInputBusBlockEntity input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
        ItemOutputBusBlockEntity output = RuntimeTestFixtures.itemOutput(new BlockPos(2, 0, 0));
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input, output);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));
        MachineRecipe recipe = recipe("runtime_malformed_plan", 20, List.of(
                input(Items.IRON_INGOT, 1), output(Items.IRON_NUGGET, 1)));
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();

        TagValueOutput outputTag = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        saved.save(outputTag);
        var savedRecipeTag = outputTag.buildResult().getCompound("recipe").orElseThrow();
        CompoundTag malformedPlan = new CompoundTag();
        malformedPlan.putIntArray("consumedInputBatches", new int[]{1});
        savedRecipeTag.put("inputConsumptionPlan", malformedPlan);
        input.getItemStackHandler(null).setStackInSlot(0, stack(Items.IRON_INGOT, 1));

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), outputTag.buildResult()), null);

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().details()).containsEntry("reason", "recipe_load");
        assertThat(input.getItemStackHandler(null).getStackInSlot(0).getCount()).isEqualTo(1);
    }

    @Test
    void modified_embedded_recipe_definition_is_rejected_by_its_fingerprint() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe recipe = recipe("runtime_modified_definition", 20, List.of());
        RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
        CraftingRuntime saved = new CraftingRuntime(controller, controller.componentRuntime());
        assertThat(saved.start(recipe, 1).isCrafting()).isTrue();

        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, EMPTY_LOOKUP);
        saved.save(output);
        var recipeTag = output.buildResult().getCompound("recipe").orElseThrow();
        assertThat(recipeTag.getStringOr("recipe_definition_fingerprint", ""))
                .matches("[0-9a-f]{64}");
        recipeTag.getCompound("recipe_definition").orElseThrow().putInt("tick_time", 99);

        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, EMPTY_LOOKUP, output.buildResult()), null);

        assertThat(restored.active()).isFalse();
        assertThat(restored.failure()).isNotNull();
        assertThat(restored.failure().details()).containsEntry("reason", "recipe_load");
    }

    private static MachineRecipe recipe(String path, int duration, List<ItemRequirement> requirements) {
        return new MachineRecipe(MMCR.id(path), MMCR.id("test_cube"), duration,
                List.of(), List.of(), List.of(), 0, 1, false, List.of(), new java.util.ArrayList<>(requirements));
    }

    private static void assertFinishRetryGate(MachineBehavior behavior, AtomicInteger callbacks) {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        controller.setMachine(machine(controller.machineId(), behavior));
        CraftingRuntime runtime = new CraftingRuntime(controller, controller.componentRuntime());

        assertThat(runtime.start(recipe("runtime_finish_callback_retry", 1, List.of()), 1).isCrafting()).isTrue();
        runtime.tick();
        runtime.finish();
        assertThat(callbacks).hasValue(1);
        assertThat(runtime.shouldRetryFinish()).isFalse();

        runtime.finish();
        assertThat(callbacks).hasValue(1);

        LevelStub.setGameTime(controller.getLevel(), 10);
        runtime.finish();
        assertThat(callbacks).hasValue(2);
        assertThat(runtime.shouldRetryFinish()).isFalse();
    }

    private static Machine machine(net.minecraft.resources.Identifier id, MachineBehavior behavior) {
        return new DynamicMachine(id, id.toString(), new BlockArray(Map.of()),
                MachineControllerSpec.defaultsFor(id), MachineAppearanceSpec.defaults(), PortRequirementSpec.none(),
                PortTierRequirementSpec.none(), List.of(), Map.of(), 1, false, false, 1, List.of(), MachineRole.NORMAL,
                Set.of(), List.of(), RecipeFailureActions.getDefaultAction(), behavior);
    }

    private static ItemRequirement input(net.minecraft.world.item.Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(item), count, ItemStack.EMPTY);
    }

    private static ItemRequirement output(net.minecraft.world.item.Item item, int count) {
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0, stack(item, count));
    }

    private static ItemStack stack(net.minecraft.world.item.Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.MAX_STACK_SIZE, 64);
        return stack;
    }

    private static CraftingRuntime controllerRuntime(MachineControllerBlockEntity controller) {
        try {
            Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return ((MachineControllerRuntime) field.get(controller)).craftingRuntime();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to access controller runtime", exception);
        }
    }
}
