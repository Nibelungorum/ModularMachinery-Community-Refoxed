package cn.howxu.mmcr.compat.pneumaticcraft;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.MachineBuilder;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.registration.MachineDefinitionRegistration;
import cn.howxu.mmcr.api.registration.MachineRecipeRegistration;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirInterfaceKind;
import cn.howxu.mmcr.compat.pneumaticcraft.loaded.AirPortBlockEntity;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.recipe.RecipeSearchContextKey;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import cn.howxu.mmcr.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Assembled native ports exercise production recipe progression, persisted stores and targeted wakeups.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PneumaticRecipeGameTest {
    public static final ResourceLocation MACHINE_ID = MMCR.id("pneumatic_air_task3");
    public static final ResourceLocation RECIPE_ID = MMCR.id("pneumatic_air_task3_recipe");
    public static final ResourceLocation SINGLE_TICK_ID = MMCR.id("pneumatic_air_task3_single_tick");
    public static final ResourceLocation CONDITION_ID = MMCR.id("pneumatic_air_task3_condition");
    public static final ResourceLocation OUTPUT_WAIT_ID = MMCR.id("pneumatic_air_task3_output_wait");

    /** Startup hooks are called by the main agent's GameTestRegistry wiring. */
    public static void registerMachineDefinitions(MachineDefinitionRegistration event) {
        event.registerMachine(MachineBuilder.machine(MACHINE_ID)
                .displayNameKey("block.mmcr.pneumaticcraft_air_input_interface").build());
    }

    public static void registerMachineStructures(StructureRegistration event) {
        event.registerStructure(MACHINE_ID, structure -> {
            structure.fullStructure(stage -> stage.pattern(pattern -> pattern.layer("ICO")
                    .where('I', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get(PneumaticIds.INPUT).get()))
                    .where('C', BlockPredicate.deferredBlock(() -> ModBlocks.controllerFor(MACHINE_ID).get()))
                    .where('O', BlockPredicate.deferredBlock(() -> ModBlocks.BLOCKS.get(PneumaticIds.OUTPUT).get()))
                    .controller('C')));
            return structure;
        });
    }

    public static void registerRecipes(MachineRecipeRegistration event) {
        event.registerRecipe(MachineRecipeBuilder.recipe(RECIPE_ID).recipePool(MACHINE_ID).duration(3)
                .requirement(AirRequirement.input(40, 4F)).requirement(AirRequirement.output(80)).build());
        event.registerRecipe(MachineRecipeBuilder.recipe(SINGLE_TICK_ID).recipePool(MACHINE_ID).duration(1)
                .requirement(AirRequirement.input(40, 4F)).requirement(AirRequirement.output(80)).build());
        event.registerRecipe(MachineRecipeBuilder.recipe(CONDITION_ID).recipePool(MACHINE_ID).duration(3)
                .requirement(AirRequirement.input(0, 4F)).requirement(AirRequirement.output(80)).build());
        event.registerRecipe(MachineRecipeBuilder.recipe(OUTPUT_WAIT_ID).recipePool(MACHINE_ID).duration(1)
                .requirement(AirRequirement.output(80)).build());
    }

    public void recipeLifecycleRestoreAndNativeRefillWakeSearch(GameTestHelper helper) {
        Fixture fixture = assemble(helper);
        MachineRecipe recipe = recipe(helper, RECIPE_ID);
        var owner = controllerRuntime(fixture.controller());
        FactoryRuntime factory = owner.factoryRuntime();
        long searchTime = helper.getLevel().getGameTime() - 1;
        factory.ensureBaseLane(fixture.controller());
        factory.tick(List.of(recipe), 1, searchTime);
        FactoryRecipeThread wait = baseLane(factory);
        RecipeSearchContextKey waitKey = wait.searchFailureKey();
        helper.assertTrue(waitKey != null && !wait.canSearch(searchTime, waitKey),
                "Insufficient native pressure enters recipe search backoff");
        setAir(fixture.input(), 40_080);
        helper.assertTrue(wait.canSearch(searchTime, waitKey),
                "Native refill observation wakes the matching input search without waiting world ticks");
        factory.clear();

        CraftingRuntime runtime = owner.craftingRuntime();
        helper.assertTrue(runtime.start(recipe, 1).isCrafting(), "Native recipe starts after pressure recovery");
        assertAir(helper, fixture, 40_080, 0, "Startup validates air without debit or output");
        runtime.tick();
        assertAir(helper, fixture, 40_040, 80, "First successful tick debits and produces once");
        runtime.pause();
        runtime.tick();
        runtime.finish();
        assertAir(helper, fixture, 40_040, 80, "Pause neither advances nor replays air IO");
        runtime.resume();

        CompoundTag saved = new CompoundTag();
        runtime.save(saved, helper.getLevel().registryAccess());
        CompoundTag inputSaved = fixture.input().saveWithoutMetadata(helper.getLevel().registryAccess());
        CompoundTag outputSaved = fixture.output().saveWithoutMetadata(helper.getLevel().registryAccess());
        runtime.invalidate();
        // Disturb both stores before loading their actual native-handler NBT.
        setAir(fixture.input(), 0);
        setAir(fixture.output(), 0);
        fixture.input().loadWithComponents(inputSaved, helper.getLevel().registryAccess());
        fixture.output().loadWithComponents(outputSaved, helper.getLevel().registryAccess());
        CraftingRuntime restored = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
        restored.load(saved, fixture.controller().resourceDomain(), helper.getLevel().registryAccess());
        helper.assertTrue(restored.active() && restored.tickCount() == 1
                        && restored.activeRecipe().inputConsumptionPlan().consumedInputBatches().equals(List.of(0, 0)),
                "Serialized execution restores progress and zero start-consumption markers for continuous air");
        assertAir(helper, fixture, 40_040, 80, "Loading real native stores does not replay the completed tick");
        restored.tick();
        restored.tick();
        helper.assertTrue(restored.finishPending() && fixture.input().airHandler().getPressure() < 4F,
                "Final successful debit may cross below the required pressure");
        assertAir(helper, fixture, 39_960, 240, "Exactly three progress grants settle exactly three air batches");
        restored.tick();
        restored.finish();
        restored.finish();
        restored.tick();
        helper.assertTrue(!restored.active(), "Finish excludes air and succeeds below the prior pressure condition");
        assertAir(helper, fixture, 39_960, 240, "Repeated completion and idle entrypoints cannot duplicate air");

        setAir(fixture.input(), 40_000);
        CraftingRuntime single = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
        helper.assertTrue(single.start(recipe(helper, SINGLE_TICK_ID), 1).isCrafting(), "Duration-one recipe starts");
        single.tick();
        helper.assertTrue(single.finishPending(), "One explicit grant reaches the duration-one finish boundary");
        single.finish();
        helper.assertTrue(!single.active(), "Duration-one finish does not require another pressure check");
        assertAir(helper, fixture, 39_960, 320, "Duration-one start/tick/finish settles only one batch");
        helper.succeed();
    }

    public void pressureOnlyFallbackAndFullOutputRecovery(GameTestHelper helper) {
        Fixture fixture = assemble(helper);
        setAir(fixture.input(), 40_000);
        CraftingRuntime runtime = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
        helper.assertTrue(runtime.start(recipe(helper, CONDITION_ID), 1).isCrafting(), "Pressure-only recipe starts");
        helper.assertTrue(asyncTick(runtime, fixture.controller()), "Unsupported air descriptors use the live main-thread fallback");
        assertAir(helper, fixture, 40_000, 80, "Zero rate leaves native air unchanged while producing one tick of output");
        var prepared = runtime.prepareAsyncTickPlan(fixture.controller().currentRuntimeSnapshot());
        helper.assertTrue(prepared != null && prepared.initialMainThreadRequirements().equals(List.of(0, 1)),
                "Both air requirement indexes are explicitly unsupported by worker planning");
        var planned = prepared.plan();
        setAir(fixture.input(), 39_999);
        helper.assertTrue(!runtime.commitAsyncTick(planned), "Fallback reads native pressure changed after worker preparation");
        runtime.discardAsyncTickPreparation();
        helper.assertTrue(runtime.tickCount() == 1 && runtime.failure() != null
                        && runtime.failure().reason().equals(AirFailureReasons.INSUFFICIENT_PRESSURE),
                "Low-pressure zero-rate condition pauses progress and identifies the real pressure failure");
        assertAir(helper, fixture, 39_999, 80, "Blocked condition creates no additional air");
        setAir(fixture.input(), 40_000);
        helper.assertTrue(asyncTick(runtime, fixture.controller()) && asyncTick(runtime, fixture.controller()),
                "Native refill resumes both remaining grants through the same fallback");
        runtime.finish();
        helper.assertTrue(!runtime.active(), "Pressure-only recipe completes after three successful grants");
        assertAir(helper, fixture, 40_000, 240, "Async pressure-only completion never consumes or duplicates air");

        int capacity = (int) Math.floor((double) fixture.output().airHandler().getDangerPressure()
                * fixture.output().airHandler().getVolume());
        setAir(fixture.output(), capacity);
        MachineRecipe outputOnly = recipe(helper, OUTPUT_WAIT_ID);
        FactoryRuntime factory = controllerRuntime(fixture.controller()).factoryRuntime();
        factory.ensureBaseLane(fixture.controller());
        long searchTime = helper.getLevel().getGameTime() - 1;
        factory.tick(List.of(outputOnly), 1, searchTime);
        FactoryRecipeThread wait = baseLane(factory);
        RecipeSearchContextKey waitKey = wait.searchFailureKey();
        helper.assertTrue(waitKey != null && !wait.canSearch(searchTime, waitKey),
                "Full native output establishes targeted output-capacity search backoff");
        setAir(fixture.output(), capacity - 80);
        helper.assertTrue(wait.canSearch(searchTime, waitKey),
                "Observed native extraction wakes the matching output-capacity search immediately");
        factory.clear();
        setAir(fixture.output(), capacity);
        CraftingRuntime outputRuntime = new CraftingRuntime(fixture.controller(), fixture.controller().componentRuntime());
        helper.assertTrue(outputRuntime.start(outputOnly, 1).isCrafting(), "Startup retains normal deferred output validation");
        outputRuntime.tick();
        helper.assertTrue(outputRuntime.active() && !outputRuntime.finishPending() && outputRuntime.tickCount() == 0
                        && outputRuntime.failure() != null
                        && outputRuntime.failure().reason().equals(AirFailureReasons.OUTPUT_BLOCKED),
                "Full output blocks the tick without progress or partial insertion");
        setAir(fixture.output(), capacity - 80);
        outputRuntime.tick();
        outputRuntime.finish();
        outputRuntime.finish();
        helper.assertTrue(!outputRuntime.active() && fixture.output().airHandler().getAir() == capacity,
                "Freed space receives exactly one resumed tick's output, never a finish duplicate");
        helper.assertTrue(fixture.input().airHandler().getAir() == 40_000,
                "Output-only recipe does not touch input air");
        helper.succeed();
    }

    private static boolean asyncTick(CraftingRuntime runtime, MachineControllerBlockEntity controller) {
        var prepared = runtime.prepareAsyncTickPlan(controller.currentRuntimeSnapshot());
        if (prepared == null || prepared.initialMainThreadRequirements().isEmpty()) return false;
        if (!runtime.commitAsyncTick(prepared.plan())) {
            runtime.discardAsyncTickPreparation();
            return false;
        }
        return runtime.completeAsyncTickAfterInputs() && runtime.completeAsyncTickAfterRecipe().isCrafting();
    }

    private static MachineRecipe recipe(GameTestHelper helper, ResourceLocation id) {
        MachineRecipe recipe = RecipeRegistry.getRecipe(id);
        helper.assertTrue(recipe != null, "Test recipe is installed by startup registration: " + id);
        return recipe;
    }

    private static Fixture assemble(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 1);
        AirPortBlockEntity input = port(helper, pos.west(), AirInterfaceKind.INPUT);
        AirPortBlockEntity output = port(helper, pos.east(), AirInterfaceKind.OUTPUT);
        helper.setBlock(pos, ModBlocks.controllerFor(MACHINE_ID).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        MachineControllerBlockEntity controller = helper.getBlockEntity(pos);
        controller.setMachine(MachineRegistry.getMachine(MACHINE_ID));
        controller.requestImmediateStructureCheck();
        controller.tickStructure(helper.getLevel(), controller.getBlockPos());
        helper.assertTrue(controller.currentStructureSnapshot().formed()
                        && input.linkedControllerPositions().contains(controller.getBlockPos())
                        && output.linkedControllerPositions().contains(controller.getBlockPos()),
                "Actual assembled structure discovers and links both native air ports");
        return new Fixture(controller, input, output);
    }

    private static AirPortBlockEntity port(GameTestHelper helper, BlockPos pos, AirInterfaceKind kind) {
        helper.setBlock(pos, ModBlocks.BLOCKS.get(kind == AirInterfaceKind.INPUT ? PneumaticIds.INPUT : PneumaticIds.OUTPUT)
                .get().defaultBlockState());
        AirPortBlockEntity port = helper.getBlockEntity(pos);
        port.onLoad();
        return port;
    }

    private static void setAir(AirPortBlockEntity port, int amount) {
        port.airHandler().addAir(amount - port.airHandler().getAir());
        port.observeAirChanges();
    }

    private static void assertAir(GameTestHelper helper, Fixture fixture, int input, int output, String message) {
        helper.assertTrue(fixture.input().airHandler().getAir() == input && fixture.output().airHandler().getAir() == output,
                message);
    }

    private static MachineControllerRuntime controllerRuntime(MachineControllerBlockEntity controller) {
        try {
            Field field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
            field.setAccessible(true);
            return (MachineControllerRuntime) field.get(controller);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to access production controller runtime", exception);
        }
    }

    private static FactoryRecipeThread baseLane(FactoryRuntime factory) {
        try {
            Field field = FactoryRuntime.class.getDeclaredField("lanes");
            field.setAccessible(true);
            List<?> lanes = (List<?>) field.get(factory);
            return lanes.stream().map(FactoryRecipeThread.class::cast).filter(FactoryRecipeThread::isBaseThread)
                    .findFirst().orElseThrow();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect production search wakeups", exception);
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private record Fixture(MachineControllerBlockEntity controller, AirPortBlockEntity input, AirPortBlockEntity output) { }
}
