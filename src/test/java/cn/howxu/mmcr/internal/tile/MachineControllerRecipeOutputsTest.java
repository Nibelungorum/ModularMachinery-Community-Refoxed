package cn.howxu.mmcr.internal.tile;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.definition.RecipeStartContext;
import cn.howxu.mmcr.api.recipe.ActiveMachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.MachineOutputAmount;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.OutputRegistry;
import cn.howxu.mmcr.api.recipe.OutputType;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.internal.network.PktMachineStatePayload;
import cn.howxu.mmcr.internal.runtime.ControllerRecipePresentation;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.FactoryRuntime;
import cn.howxu.mmcr.compat.mekanism.loaded.LoadedHeatOutput;
import cn.howxu.mmcr.test.TestBootstrap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link MachineControllerBlockEntity#recipeOutputs()} falls back to the
 * controller's {@code CraftingRuntime.activeOutputs()} when no factory lane is active and
 * aggregates per-lane outputs when factory lanes are present.
 *
 * @author howxu <dev@howxu.cn>
 */
class MachineControllerRecipeOutputsTest {

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void emptyControllerReturnsEmptyOutputs() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        assertThat(controller.recipeOutputs()).isEmpty();
    }

    @Test
    void factoryAggregationMergesIdenticalItems() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        TestBootstrap.bindFactoryWithOutputs(controller, List.of(
                new MachineOutput.ItemOutput(new ItemStack(Items.STONE, 4), 1F),
                new MachineOutput.ItemOutput(new ItemStack(Items.STONE, 6), 1F)));
        List<MachineOutputAmount> merged = controller.recipeOutputs();
        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).amount()).isEqualTo(10L);
        assertThat(((MachineOutput.ItemOutput) merged.get(0).output()).stack().getCount()).isEqualTo(4);
    }

    @Test
    void factoryAggregationReusesOneOwnedCopyPerInput() {
        try (var scope = OutputRegistry.openTestScope()) {
            OutputRegistry.register(CountingOutput.TYPE);
            MachineControllerBlockEntity controller = TestBootstrap.newController();
            TestBootstrap.bindFactoryWithOutputs(controller, List.of(
                    new CountingOutput(4L, 0.8F), new CountingOutput(6L, 0.5F)), 2L);
            TestBootstrap.bindFactoryWithOutputs(controller, List.of(new CountingOutput(3L, 0.7F)), 3L);
            CountingOutput.COPIES.set(0);

            List<MachineOutputAmount> merged = controller.recipeOutputs();

            // Three amount owners, three consumer reads, one materialized owner. No key/chance rereads.
            assertThat(CountingOutput.COPIES).hasValue(7);
            assertThat(merged).singleElement().satisfies(output -> {
                assertThat(output.amount()).isEqualTo(29L);
                CountingOutput owned = (CountingOutput) output.output();
                assertThat(owned.amount()).isEqualTo(4L);
                assertThat(owned.chance()).isEqualTo(0.5F);
            });
        }
    }

    @Test
    void factoryAggregationRetainsChanceAndOwnedItemStacks() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        MachineOutput.ItemOutput first = new MachineOutput.ItemOutput(new ItemStack(Items.STONE, 4), 0.8F);
        MachineOutput.ItemOutput second = new MachineOutput.ItemOutput(new ItemStack(Items.STONE, 6), 0.5F);
        TestBootstrap.bindFactoryWithOutputs(controller, List.of(first, second));
        MachineOutputAmount merged = controller.recipeOutputs().getFirst();
        MachineOutput.ItemOutput owned = (MachineOutput.ItemOutput) merged.output();
        assertThat(merged.amount()).isEqualTo(10L);
        assertThat(owned.chance()).isEqualTo(0.5F);
        owned.stack().setCount(64);
        assertThat(((MachineOutput.ItemOutput) merged.output()).stack().getCount()).isEqualTo(4);
        assertThat(first.stack().getCount()).isEqualTo(4);
        assertThat(second.stack().getCount()).isEqualTo(6);
        assertThat(controller.recipeOutputs().getFirst().amount()).isEqualTo(10L);
    }

    @Test
    void ordinaryMachineParallelismKeepsLongOutputAmount() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        TestBootstrap.bindCraftingWithOutputs(controller,
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.STONE, Integer.MAX_VALUE), 1F)), 2L);

        assertThat(controller.recipeOutputs()).singleElement()
                .satisfies(output -> assertThat(output.amount()).isEqualTo((long) Integer.MAX_VALUE * 2L));
    }

    @Test
    void ordinaryMachineWithoutParallelismKeepsTheStackAmount() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        TestBootstrap.bindCraftingWithOutputs(controller,
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.STONE, 4), 1F)), 1L);

        assertThat(controller.recipeOutputs()).singleElement()
                .satisfies(output -> assertThat(output.amount()).isEqualTo(4L));
    }

    @Test
    void factoryThreadsAggregateLongOutputAmounts() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        MachineOutput output = new MachineOutput.ItemOutput(new ItemStack(Items.STONE, Integer.MAX_VALUE), 1F);
        TestBootstrap.bindFactoryWithOutputs(controller, List.of(output), 2L);
        TestBootstrap.bindFactoryWithOutputs(controller, List.of(output), 2L);

        assertThat(controller.recipeOutputs()).singleElement()
                .satisfies(value -> assertThat(value.amount()).isEqualTo((long) Integer.MAX_VALUE * 4L));
    }

    @Test
    void outputAmountSaturatesAtLongMaximumAfterParallelScaling() {
        MachineControllerBlockEntity controller = TestBootstrap.newController();
        TestBootstrap.bindCraftingWithOutputs(controller,
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.STONE, Integer.MAX_VALUE), 1F)),
                Long.MAX_VALUE);

        assertThat(controller.recipeOutputs()).singleElement()
                .satisfies(output -> assertThat(output.amount()).isEqualTo(Long.MAX_VALUE));
    }

    @Test
    void runtimePresentationScalesOutputsEnergyAndHeatByParallelism() {
        CraftingRuntime runtime = TestBootstrap.newCraftingRuntime();
        TestBootstrap.configureCraftingWithPresentation(runtime,
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND, 3), 1F),
                        new LoadedHeatOutput(2.5D)),
                List.of(new EnergyRequirement(RecipeModifier.IOType.INPUT, 100L),
                        new EnergyRequirement(RecipeModifier.IOType.OUTPUT, 50L)), 4L);

        ControllerRecipePresentation presentation = runtime.recipePresentation();

        assertThat(presentation.outputs()).extracting(MachineOutputAmount::amount).containsExactly(12L);
        assertThat(presentation.energyInputPerTick()).isEqualTo(400L);
        assertThat(presentation.energyOutputPerTick()).isEqualTo(200L);
        assertThat(presentation.heatOutputPerTick()).isEqualTo(10D);
        assertThat(presentation.parallelism()).isEqualTo(4L);
        assertThat(runtime.recipePresentation()).isSameAs(presentation);

        runtime.activeRecipe().setParallelism(2L);
        ControllerRecipePresentation scaled = runtime.recipePresentation();
        assertThat(scaled).isNotSameAs(presentation);
        assertThat(scaled.outputs()).extracting(MachineOutputAmount::amount).containsExactly(6L);
        assertThat(scaled.energyInputPerTick()).isEqualTo(200L);
        assertThat(scaled.energyOutputPerTick()).isEqualTo(100L);
        assertThat(scaled.heatOutputPerTick()).isEqualTo(5D);
    }

    @Test
    void ordinaryPresentationSurvivesProgressOnlyUpdates() {
        CraftingRuntime runtime = TestBootstrap.newCraftingRuntime();
        TestBootstrap.configureCraftingWithPresentation(runtime,
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND, 3), 1F)),
                List.of(new EnergyRequirement(IOType.INPUT, 100L)), 4L);
        runtime.activeRecipe().setTotalTick(20);
        ControllerRecipePresentation first = runtime.recipePresentation();

        runtime.activeRecipe().setTick(1);
        assertThat(runtime.recipePresentation()).isSameAs(first);
        runtime.pause();
        assertThat(runtime.recipePresentation()).isSameAs(first);
        runtime.resume();
        assertThat(runtime.recipePresentation()).isSameAs(first);

        runtime.activeRecipe().setParallelism(2L);
        ControllerRecipePresentation parallel = runtime.recipePresentation();
        assertThat(parallel).isNotSameAs(first);
        assertThat(parallel.outputs()).extracting(MachineOutputAmount::amount).containsExactly(6L);
        assertThat(parallel.energyInputPerTick()).isEqualTo(200L);
        runtime.activeRecipe().setTotalTick(40);
        ControllerRecipePresentation duration = runtime.recipePresentation();
        assertThat(duration).isNotSameAs(parallel);
        assertThat(duration.durationTicks()).isEqualTo(40);
        assertThat(runtime.recipePresentation()).isSameAs(duration);
    }

    @Test
    void ordinaryPresentationTracksExecutionRevisionWithoutRecipeReplacement() {
        CraftingRuntime runtime = TestBootstrap.newCraftingRuntime();
        TestBootstrap.configureCraftingWithPresentation(runtime,
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND, 3), 1F)),
                List.of(new EnergyRequirement(IOType.INPUT, 100L)), 2L);
        ActiveMachineRecipe active = runtime.activeRecipe();
        active.setTotalTick(20);
        ControllerRecipePresentation first = runtime.recipePresentation();
        long revision = active.effectiveExecutionRevision();

        active.setEffectiveExecutionSnapshot(new RecipeStartContext.ExecutionSnapshot(20,
                List.of(new EnergyRequirement(IOType.INPUT, 100L)),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, 5), 1F))));
        ControllerRecipePresentation outputs = runtime.recipePresentation();
        assertThat(runtime.activeRecipe()).isSameAs(active);
        assertThat(active.effectiveExecutionRevision()).isGreaterThan(revision);
        assertThat(outputs).isNotSameAs(first);
        assertThat(outputs.outputs()).extracting(MachineOutputAmount::amount).containsExactly(10L);
        assertThat(((MachineOutput.ItemOutput) outputs.outputs().getFirst().output()).stack().getItem())
                .isEqualTo(Items.GOLD_INGOT);

        active.setEffectiveExecutionSnapshot(new RecipeStartContext.ExecutionSnapshot(20,
                List.of(new EnergyRequirement(IOType.INPUT, 7L), new EnergyRequirement(IOType.OUTPUT, 11L)),
                active.effectiveOutputs()));
        ControllerRecipePresentation requirements = runtime.recipePresentation();
        assertThat(requirements).isNotSameAs(outputs);
        assertThat(requirements.energyInputPerTick()).isEqualTo(14L);
        assertThat(requirements.energyOutputPerTick()).isEqualTo(22L);
        long currentRevision = active.effectiveExecutionRevision();
        active.setTick(4);
        assertThat(active.effectiveExecutionRevision()).isEqualTo(currentRevision);
        assertThat(runtime.recipePresentation()).isSameAs(requirements);
        active.reset();
        assertThat(active.effectiveExecutionRevision()).isEqualTo(currentRevision);
        assertThat(runtime.recipePresentation()).isNotSameAs(requirements);
        assertThat(runtime.recipePresentation().energyInputPerTick()).isEqualTo(7L);
    }

    @Test
    void ordinaryPresentationTracksRecipeIdentityAndStop() {
        CraftingRuntime runtime = TestBootstrap.newCraftingRuntime();
        List<MachineOutput> outputs = List.of(new MachineOutput.ItemOutput(new ItemStack(Items.STONE, 4), 1F));
        TestBootstrap.configureCraftingWithPresentation(runtime, outputs, List.of(), 1L);
        ControllerRecipePresentation first = runtime.recipePresentation();
        ActiveMachineRecipe active = runtime.activeRecipe();

        TestBootstrap.configureCraftingWithPresentation(runtime, outputs, List.of(), 1L);
        assertThat(runtime.activeRecipe()).isNotSameAs(active);
        assertThat(runtime.recipePresentation()).isNotSameAs(first);
        runtime.invalidate();
        ControllerRecipePresentation empty = runtime.recipePresentation();
        assertThat(empty.outputs()).isEmpty();
        assertThat(empty.durationTicks()).isZero();
        assertThat(empty.parallelism()).isZero();
        assertThat(empty.energyInputPerTick()).isZero();
        assertThat(empty.energyOutputPerTick()).isZero();
        assertThat(empty.heatOutputPerTick()).isZero();
        assertThat(runtime.recipePresentation()).isSameAs(empty);
    }

    @Test
    void restoringSameRecipeInvalidatesRuntimePresentationEpoch() {
        CraftingRuntime runtime = TestBootstrap.newCraftingRuntime();
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("presentation_restore"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 1, false, false, false, Set.of());
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        runtime.restore(active, null, 0L, 0L, 0L, 0L);
        assertThat(runtime.activeRecipe()).isSameAs(active);
        assertThat(active.hasEffectiveExecutionSnapshot()).isTrue();
        ControllerRecipePresentation first = runtime.recipePresentation();
        long revision = active.effectiveExecutionRevision();

        runtime.restore(active, null, 0L, 0L, 0L, 0L);
        assertThat(active.effectiveExecutionRevision()).isEqualTo(revision);
        assertThat(runtime.recipePresentation()).isNotSameAs(first);
        assertThat(runtime.recipePresentation().durationTicks()).isEqualTo(20);
    }

    @Test
    void outputCopiesCannotMutateCachedPresentationOrEffectiveExecution() {
        CraftingRuntime runtime = TestBootstrap.newCraftingRuntime();
        TestBootstrap.configureCraftingWithPresentation(runtime, List.of(), List.of(), 2L);
        MachineOutput.ItemOutput output = new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND, 3), 1F);
        RecipeStartContext.ExecutionSnapshot execution = new RecipeStartContext.ExecutionSnapshot(20, List.of(),
                List.of(output));
        runtime.activeRecipe().setEffectiveExecutionSnapshot(execution);
        ControllerRecipePresentation presentation = runtime.recipePresentation();

        output.stack().setCount(64);
        ((MachineOutput.ItemOutput) execution.outputs().getFirst()).stack().setCount(64);
        ((MachineOutput.ItemOutput) runtime.activeRecipe().effectiveOutputs().getFirst()).stack().setCount(64);
        ((MachineOutput.ItemOutput) runtime.activeOutputs().getFirst()).stack().setCount(64);
        ((MachineOutput.ItemOutput) presentation.outputs().getFirst().output()).stack().setCount(64);

        assertThat(runtime.recipePresentation()).isSameAs(presentation);
        assertThat(presentation.outputs()).extracting(MachineOutputAmount::amount).containsExactly(6L);
        assertThat(((MachineOutput.ItemOutput) presentation.outputs().getFirst().output()).stack().getCount())
                .isEqualTo(3);
        assertThat(((MachineOutput.ItemOutput) runtime.activeOutputs().getFirst()).stack().getCount()).isEqualTo(3);
    }

    @Test
    void cachedPresentationPreservesLongAmountsAndSaturatingParallelism() {
        CraftingRuntime runtime = TestBootstrap.newCraftingRuntime();
        TestBootstrap.configureCraftingWithPresentation(runtime,
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.STONE, Integer.MAX_VALUE), 1F)),
                List.of(), Long.MAX_VALUE);
        runtime.activeRecipe().setParallelism(2L);
        ControllerRecipePresentation first = runtime.recipePresentation();
        assertThat(first.outputs()).extracting(MachineOutputAmount::amount)
                .containsExactly((long) Integer.MAX_VALUE * 2L);

        runtime.activeRecipe().setParallelism(Long.MAX_VALUE);
        ControllerRecipePresentation saturated = runtime.recipePresentation();
        assertThat(saturated).isNotSameAs(first);
        assertThat(saturated.outputs()).extracting(MachineOutputAmount::amount).containsExactly(Long.MAX_VALUE);
        assertThat(runtime.recipePresentation()).isSameAs(saturated);
    }

    @Test
    void controllerSnapshotsReusePresentationWhileProgressChanges() {
        MachineControllerRuntime controllerRuntime = new MachineControllerRuntime(TestBootstrap.newController());
        CraftingRuntime runtime = controllerRuntime.craftingRuntime();
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("presentation_snapshot"), MMCR.id("test_cube"),
                20, List.of(), List.of(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND, 3), 1F)),
                List.of(), 0, 1, false, false, false, Set.of());
        assertThat(runtime.start(recipe, 1L).isCrafting()).isTrue();
        controllerRuntime.publishCraftingState(recipe.id(), runtime.snapshot().status(), null, 0, 20, 1L, 1L);
        var first = controllerRuntime.currentSnapshot();
        runtime.activeRecipe().setTick(1);
        controllerRuntime.publishCraftingState(recipe.id(), runtime.snapshot().status(), null, 1, 20, 1L, 1L);
        var next = controllerRuntime.currentSnapshot();

        assertThat(next).isNotSameAs(first);
        assertThat(next.recipePresentation()).isSameAs(first.recipePresentation()).isSameAs(runtime.recipePresentation());
        assertThat(next.crafting().tick()).isEqualTo(1);
    }

    @Test
    void controllerSnapshotsPublishExecutionChangesWithoutProgressChanges() {
        MachineControllerRuntime controllerRuntime = new MachineControllerRuntime(TestBootstrap.newController());
        CraftingRuntime runtime = controllerRuntime.craftingRuntime();
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("presentation_execution_snapshot"), MMCR.id("test_cube"),
                20, List.of(), List.of(new MachineOutput.ItemOutput(new ItemStack(Items.DIAMOND, 3), 1F)),
                List.of(), 0, 1, false, false, false, Set.of());
        assertThat(runtime.start(recipe, 1L).isCrafting()).isTrue();
        controllerRuntime.publishCraftingState(recipe.id(), runtime.snapshot().status(), null, 0, 20, 1L, 1L);
        ActiveMachineRecipe active = runtime.activeRecipe();
        var first = controllerRuntime.snapshot();
        var firstPayload = PktMachineStatePayload.from(BlockPos.ZERO, first);
        assertThat(controllerRuntime.currentSnapshot()).isSameAs(first);

        active.setEffectiveExecutionSnapshot(new RecipeStartContext.ExecutionSnapshot(20, List.of(),
                List.of(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, 5), 1F))));
        controllerRuntime.publishCraftingState(recipe.id(), runtime.snapshot().status(), null, 0, 20, 1L, 1L);
        var outputs = controllerRuntime.snapshot();
        var outputsPayload = PktMachineStatePayload.from(BlockPos.ZERO, outputs);

        assertThat(runtime.activeRecipe()).isSameAs(active);
        assertThat(outputs).isNotSameAs(first).isSameAs(controllerRuntime.currentSnapshot());
        assertThat(outputs.crafting()).isEqualTo(first.crafting());
        assertThat(outputs.recipePresentation()).isNotSameAs(first.recipePresentation());
        assertThat(outputs.recipePresentation().outputs()).extracting(MachineOutputAmount::amount).containsExactly(5L);
        assertThat(((MachineOutput.ItemOutput) outputs.recipePresentation().outputs().getFirst().output()).stack().getItem())
                .isEqualTo(Items.GOLD_INGOT);
        assertThat(outputsPayload.recipeName()).isEqualTo(firstPayload.recipeName());
        assertThat(PktMachineStatePayload.nextUpdate(outputsPayload, firstPayload)).isSameAs(outputsPayload);

        active.setEffectiveExecutionSnapshot(new RecipeStartContext.ExecutionSnapshot(20,
                List.of(new EnergyRequirement(IOType.INPUT, 7L), new EnergyRequirement(IOType.OUTPUT, 11L)),
                active.effectiveOutputs()));
        controllerRuntime.publishCraftingState(recipe.id(), runtime.snapshot().status(), null, 0, 20, 1L, 1L);
        var requirements = controllerRuntime.snapshot();
        var requirementsPayload = PktMachineStatePayload.from(BlockPos.ZERO, requirements);

        assertThat(runtime.activeRecipe()).isSameAs(active);
        assertThat(requirements).isNotSameAs(outputs).isSameAs(controllerRuntime.currentSnapshot());
        assertThat(requirements.crafting()).isEqualTo(first.crafting());
        assertThat(requirements.recipePresentation().energyInputPerTick()).isEqualTo(7L);
        assertThat(requirements.recipePresentation().energyOutputPerTick()).isEqualTo(11L);
        assertThat(requirementsPayload.recipeName()).isEqualTo(firstPayload.recipeName());
        assertThat(PktMachineStatePayload.nextUpdate(requirementsPayload, outputsPayload)).isSameAs(requirementsPayload);
        controllerRuntime.publishSnapshot();
        assertThat(controllerRuntime.snapshot()).isSameAs(requirements);
    }

    @Test
    void controllerSnapshotsPublishSameRecipeRestoreEpochWithoutProgressChanges() {
        MachineControllerRuntime controllerRuntime = new MachineControllerRuntime(TestBootstrap.newController());
        CraftingRuntime runtime = controllerRuntime.craftingRuntime();
        MachineRecipe recipe = MachineRecipe.fromCanonical(MMCR.id("presentation_restore_snapshot"), MMCR.id("test_cube"),
                20, List.of(), List.of(), List.of(), 0, 1, false, false, false, Set.of());
        ActiveMachineRecipe active = new ActiveMachineRecipe(recipe);
        runtime.restore(active, null, 0L, 0L, 0L, 0L);
        controllerRuntime.publishSnapshot();
        var first = controllerRuntime.snapshot();
        long revision = active.effectiveExecutionRevision();

        runtime.restore(active, null, 0L, 0L, 0L, 0L);
        var working = controllerRuntime.currentSnapshot();
        assertThat(working).isNotSameAs(first);
        assertThat(working.recipePresentation()).isNotSameAs(first.recipePresentation());
        assertThat(controllerRuntime.snapshot()).isSameAs(first);
        controllerRuntime.publishSnapshot();

        assertThat(runtime.activeRecipe()).isSameAs(active);
        assertThat(active.effectiveExecutionRevision()).isEqualTo(revision);
        assertThat(controllerRuntime.snapshot()).isSameAs(working);
        assertThat(working.crafting()).isEqualTo(first.crafting());
    }

    @Test
    void factoryThreadPresentationContainsOnlyItsOwnOutputs() {
        ControllerRecipePresentation first = new ControllerRecipePresentation(List.of(
                new MachineOutputAmount(new MachineOutput.ItemOutput(new ItemStack(Items.IRON_INGOT, 1), 1F), 3L)),
                0L, 0L, 0D);
        ControllerRecipePresentation second = new ControllerRecipePresentation(List.of(
                new MachineOutputAmount(new MachineOutput.ItemOutput(new ItemStack(Items.GOLD_INGOT, 1), 1F), 5L)),
                0L, 0L, 0D);

        FactoryRuntime.ThreadSnapshot firstThread = new FactoryRuntime.ThreadSnapshot(0, "factory-0", true, false,
                true, "mmcr:first", 1, 20, 3L, null, first);
        FactoryRuntime.ThreadSnapshot secondThread = new FactoryRuntime.ThreadSnapshot(1, "factory-1", false, false,
                true, "mmcr:second", 1, 20, 5L, null, second);

        assertThat(firstThread.presentation().outputs()).containsExactly(first.outputs().getFirst());
        assertThat(firstThread.presentation().outputs()).doesNotContainAnyElementsOf(second.outputs());
        assertThat(secondThread.presentation().outputs()).containsExactly(second.outputs().getFirst());
    }
    /** @author howxu <dev@howxu.cn> */
    private record CountingOutput(long amount, float chance) implements MachineOutput {
        private static final ResourceLocation ID = MMCR.id("factory_counting_output_test");
        private static final AtomicInteger COPIES = new AtomicInteger();
        private static final OutputType<CountingOutput> TYPE = new OutputType.Definition<>(ID,
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.LONG.fieldOf("amount").forGetter(CountingOutput::amount),
                        Codec.FLOAT.fieldOf("chance").forGetter(CountingOutput::chance)
                ).apply(instance, CountingOutput::new)),
                (output, chance) -> new CountingOutput(output.amount(), chance),
                (output, modifiers) -> output,
                output -> {
                    COPIES.incrementAndGet();
                    return new CountingOutput(output.amount(), output.chance());
                }, OutputType.Presentation.defaults(ID), ID.toString(),
                (output, tags) -> new EnergyRequirement(IOType.OUTPUT, output.amount(), tags),
                requirement -> false);

        @Override
        public OutputType<CountingOutput> outputType() {
            return TYPE;
        }
    }
}
