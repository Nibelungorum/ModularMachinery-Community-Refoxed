package cn.howxu.mmcr.compat.ars_nouveau;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.helper.CraftingStatus;
import cn.howxu.mmcr.compat.ars_nouveau.loaded.SourcePortBlockEntity;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.recipe.FactoryRecipeThread;
import cn.howxu.mmcr.internal.recipe.RecipeSearchContextKey;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.ParallelControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.List;

/** Real source consumption, restoration, completion, and targeted wakeups in one world. @author howxu <dev@howxu.cn> */
public final class ArsSourceRecipeGameTest {
    public static final ResourceLocation MACHINE_ID = MMCR.id("ars_source_task9");
    public static final ResourceLocation RECIPE_ID = MMCR.id("ars_source_task9_recipe");
    public static final ResourceLocation OUTPUT_WAIT_ID = MMCR.id("ars_source_task9_output_wait");

    public void recipeLifecycleConsumesOnceAndNativeTransfersWakeSearches(GameTestHelper helper) {
        BlockPos controllerPos = new BlockPos(2, 1, 1);
        SourcePortBlockEntity input = ArsSourceGameTestFixtures.port(helper, controllerPos.west(), IOType.INPUT);
        SourcePortBlockEntity output = ArsSourceGameTestFixtures.port(helper, controllerPos.east(), IOType.OUTPUT);
        helper.setBlock(controllerPos.above(), ModBlocks.BLOCKS.get("parallel_controller_normal").get().defaultBlockState());
        ParallelControllerBlockEntity parallel = helper.getBlockEntity(controllerPos.above());
        parallel.setCurrentParallelism(2);
        helper.setBlock(controllerPos, ModBlocks.controllerFor(MACHINE_ID).get().defaultBlockState()
                .setValue(MachineControllerBlock.FACING, Direction.SOUTH));
        MachineControllerBlockEntity controller = helper.getBlockEntity(controllerPos);
        controller.setMachine(MachineRegistry.getMachine(MACHINE_ID));
        controller.requestImmediateStructureCheck();
        controller.tickStructure(helper.getLevel(), controller.getBlockPos());
        helper.assertTrue(controller.currentStructureSnapshot().formed()
                        && input.linkedControllerPositions().contains(controller.getBlockPos())
                        && output.linkedControllerPositions().contains(controller.getBlockPos()),
                "Startup-registered test structure forms and links actual source interfaces");

        MachineRecipe recipe = RecipeRegistry.getRecipe(RECIPE_ID);
        MachineRecipe outputOnly = RecipeRegistry.getRecipe(OUTPUT_WAIT_ID);
        helper.assertTrue(recipe != null && outputOnly != null,
                "Reusable recipe fixtures are registered once by the startup recipe lifecycle");
        var ownerRuntime = ArsSourceGameTestFixtures.runtime(controller);
        FactoryRuntime factory = ownerRuntime.factoryRuntime();
        factory.ensureBaseLane(controller);
        // Explicit prior-time search context keeps this a main-thread lifecycle test even in ASYNC work mode.
        long gameTime = helper.getLevel().getGameTime() - 1;
        factory.tick(List.of(recipe), 2, gameTime);
        FactoryRecipeThread inputWait = baseLane(factory);
        RecipeSearchContextKey inputKey = inputWait.searchFailureKey();
        helper.assertTrue(inputKey != null && !inputWait.canSearch(gameTime, inputKey),
                "Insufficient real source puts the factory lane into search backoff");
        var jar = ArsSourceGameTestFixtures.jar(helper, new BlockPos(1, 1, 3), 900);
        var relay = ArsSourceGameTestFixtures.relay(helper, new BlockPos(2, 1, 3));
        helper.assertTrue(input.externalHandler().receiveSource(900, true) == 900
                        && input.storage().amount() == 0 && !inputWait.canSearch(gameTime, inputKey),
                "Native simulation changes neither storage nor source-input search backoff");
        helper.assertTrue(relay.transferSource(jar.getSourceStorage(), input.externalHandler()) == 900
                        && inputWait.canSearch(gameTime, inputKey),
                "Native relay insertion emits matching INPUT_AVAILABLE and wakes the waiting source lane");
        factory.clear();

        CraftingRuntime runtime = ownerRuntime.craftingRuntime();
        runtime.start(recipe, 2);
        helper.assertTrue(runtime.active() && runtime.parallelism() == 2 && input.storage().amount() == 300
                        && output.storage().amount() == 0,
                "Parallel start consumes 600 source once and produces nothing before completion");
        advanceToCompletion(helper, runtime, input, output, 0);
        output.storage().setAmount(output.storage().capacity() - 100);
        helper.assertTrue(runtime.prepareAsyncFinish(), "Shared-IO finish preparation uses the active batch");
        runtime.finish();
        helper.assertTrue(runtime.active() && runtime.finishPending() && runtime.parallelism() == 2
                        && runtime.failure() != null && runtime.failure().reason().equals(SourceFailureReasons.OUTPUT_BLOCKED)
                        && input.storage().amount() == 300 && output.storage().amount() == output.storage().capacity() - 100,
                "Prepared async finish cannot lower a committed batch to fit one of two outputs or move any source");
        output.storage().setAmount(0);
        runtime.activeRecipe().markFinishBlocked((int) gameTime - 10);
        runtime.finish();
        helper.assertTrue(!runtime.active() && input.storage().amount() == 300 && output.storage().amount() == 200,
                "Normal completion produces exactly the parallel total without consuming input again");

        jar.setSource(600);
        relay.transferSource(jar.getSourceStorage(), input.externalHandler());
        runtime.start(recipe, 2);
        helper.assertTrue(runtime.active() && input.storage().amount() == 300, "Second start consumes only its own batch");
        runtime.pause();
        helper.assertTrue(runtime.active() && runtime.snapshot().status().getStatus() == CraftingStatus.Status.PAUSED
                        && input.storage().amount() == 300 && output.storage().amount() == 200,
                "Pausing preserves the active execution and both real stores");
        runtime.resume();
        runtime.tick();
        helper.assertTrue(input.storage().amount() == 300 && output.storage().amount() == 200,
                "Resume and pre-save progress do not repeat source input or produce output early");
        CompoundTag saved = new CompoundTag();
        runtime.save(saved, helper.getLevel().registryAccess());
        runtime.invalidate();
        CraftingRuntime restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(saved, controller.resourceDomain(), helper.getLevel().registryAccess());
        helper.assertTrue(restored.active() && restored.parallelism() == 2 && restored.tickCount() > 0
                        && input.storage().amount() == 300 && output.storage().amount() == 200,
                "A newly constructed runtime restores the real serialized execution without another start debit");
        output.storage().setAmount(output.storage().capacity() - 100);
        advanceToCompletion(helper, restored, input, output, output.storage().capacity() - 100);
        restored.finish();
        helper.assertTrue(restored.active() && restored.finishPending() && restored.failure() != null
                        && restored.parallelism() == 2 && restored.failure().reason().equals(SourceFailureReasons.OUTPUT_BLOCKED)
                        && input.storage().amount() == 300 && output.storage().amount() == output.storage().capacity() - 100,
                "One batch of output space blocks the whole restored parallel batch without any insertion");
        CompoundTag finishSaved = new CompoundTag();
        restored.save(finishSaved, helper.getLevel().registryAccess());
        restored.invalidate();
        restored = new CraftingRuntime(controller, controller.componentRuntime());
        restored.load(finishSaved, controller.resourceDomain(), helper.getLevel().registryAccess());
        helper.assertTrue(restored.active() && restored.finishPending() && restored.parallelism() == 2,
                "The blocked finish boundary itself survives serialization without losing a batch");
        restored.activeRecipe().markFinishBlocked((int) gameTime - 10);
        restored.finish();
        helper.assertTrue(restored.active() && output.storage().amount() == output.storage().capacity() - 100,
                "Reloaded finish still refuses a smaller positive-parallelism plan");

        factory.ensureBaseLane(controller);
        factory.tick(List.of(outputOnly), 1, gameTime);
        FactoryRecipeThread outputWait = baseLane(factory);
        RecipeSearchContextKey outputKey = outputWait.searchFailureKey();
        helper.assertTrue(outputKey != null && !outputWait.canSearch(gameTime, outputKey),
                "Source-output capacity failure enters targeted search backoff");
        helper.assertTrue(output.externalHandler().extractSource(100, true) == 100
                        && !outputWait.canSearch(gameTime, outputKey),
                "Simulated extraction does not wake the source-output lane");
        jar.setSource(jar.getMaxSource() - 100);
        helper.assertTrue(relay.transferSource(output.externalHandler(), jar.getSourceStorage()) == 100
                        && outputWait.canSearch(gameTime, outputKey),
                "Native relay extraction emits matching OUTPUT_CAPACITY and wakes a blocked source lane");
        // Make the native retry entry eligible directly; do not wait ten world ticks or mutate level time.
        restored.activeRecipe().markFinishBlocked((int) gameTime - 10);
        restored.finish();
        helper.assertTrue(!restored.active() && input.storage().amount() == 300
                        && output.storage().amount() == output.storage().capacity() && jar.getSource() == jar.getMaxSource(),
                "Freed space accepts the restored batch's output exactly once and conserves extracted source");
        restored.tick();
        restored.finish();
        helper.assertTrue(output.storage().amount() == output.storage().capacity() && jar.getSource() == jar.getMaxSource()
                        && input.storage().amount() == 300,
                "Repeated idle lifecycle entrypoints cannot duplicate finished output or reconsume input");
        factory.clear();

        CompoundTag portSaved = output.saveWithoutMetadata(helper.getLevel().registryAccess());
        factory.ensureBaseLane(controller);
        factory.tick(List.of(outputOnly), 1, gameTime);
        FactoryRecipeThread reloadWait = baseLane(factory);
        RecipeSearchContextKey reloadKey = reloadWait.searchFailureKey();
        helper.assertTrue(reloadKey != null && !reloadWait.canSearch(gameTime, reloadKey),
                "Full output establishes a blocked lane before BE reload");
        SourcePortBlockEntity reloaded = new SourcePortBlockEntity(output.getBlockPos(), output.getBlockState(), output.kind());
        reloaded.setLevel(helper.getLevel());
        reloaded.loadWithComponents(portSaved, helper.getLevel().registryAccess());
        helper.assertTrue(!reloadWait.canSearch(gameTime, reloadKey) && reloaded.storage().amount() == reloaded.storage().capacity(),
                "Loading the real full store with saved links silently establishes availability");
        output.setRemoved();
        helper.getLevel().setBlockEntity(reloaded);
        reloaded.onLoad();
        helper.assertTrue(!reloadWait.canSearch(gameTime, reloadKey), "onLoad baseline capture does not wake a blocked lane");
        helper.assertTrue(reloaded.externalHandler().extractSource(200, true) == 200
                        && reloaded.storage().amount() == reloaded.storage().capacity() && !reloadWait.canSearch(gameTime, reloadKey),
                "First simulated extraction after reload neither warms the baseline nor wakes the lane");
        jar.setSource(jar.getMaxSource() - 200);
        helper.assertTrue(relay.transferSource(reloaded.externalHandler(), jar.getSourceStorage()) == 200
                        && reloaded.storage().amount() == reloaded.storage().capacity() - 200
                        && reloadWait.canSearch(gameTime, reloadKey),
                "First real native extraction after reload immediately emits OUTPUT_CAPACITY without an inventory prewarm");
        factory.clear();
        helper.succeed();
    }

    private static void advanceToCompletion(GameTestHelper helper, CraftingRuntime runtime,
                                            SourcePortBlockEntity input, SourcePortBlockEntity output, int beforeOutput) {
        int remaining = runtime.totalTick() - runtime.tickCount();
        for (int step = 0; step < remaining && !runtime.finishPending(); step++) {
            runtime.tick();
            helper.assertTrue(input.storage().amount() == 300 && output.storage().amount() == beforeOutput,
                    "Every direct recipe lifecycle step preserves start-only consumption and deferred output");
        }
        helper.assertTrue(runtime.active() && runtime.finishPending(), "Recipe lifecycle reaches its completion boundary");
    }

    private static FactoryRecipeThread baseLane(FactoryRuntime factory) {
        try {
            Field field = FactoryRuntime.class.getDeclaredField("lanes");
            field.setAccessible(true);
            List<?> lanes = (List<?>) field.get(factory);
            return lanes.stream().map(FactoryRecipeThread.class::cast).filter(FactoryRecipeThread::isBaseThread)
                    .findFirst().orElseThrow();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect native search backoff", exception);
        }
    }
}
