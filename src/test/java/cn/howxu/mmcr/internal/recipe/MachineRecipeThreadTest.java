package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.LevelStub;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.runtime.CraftingRuntime;
import cn.howxu.mmcr.internal.runtime.PatternStartReservation;
import cn.howxu.mmcr.internal.block.MachineControllerBlock;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.registry.ModBlocks;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.ServerThreadTestScope;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Real remembered candidates retain externally paid provenance across save/load.
 * @author howxu <dev@howxu.cn>
 */
class MachineRecipeThreadTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        TestBootstrap.bootstrapCapabilities();
    }

    @AfterEach
    void cleanup() { RecipeRegistry.clearForTesting(); }

    @Test
    void restored_pattern_memory_is_a_real_candidate_but_never_an_automatic_restart() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            var input = RuntimeTestFixtures.itemInput(new BlockPos(1, 0, 0));
            var controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"), input);
            controller.setFormed(true);
            var runtime = new CraftingRuntime(controller, controller.componentRuntime());
            var thread = new MachineRecipeThread(controller, runtime);
            var recipe = recipe(1);
            RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
            input.itemHandler().setContents(0, new ItemStack(Items.IRON_INGOT), 2L);
            var prepared = runtime.prepareExactPatternStart(recipe, 1L, List.of(), null);
            assertThat(prepared).isNotNull();
            assertThat(runtime.reservePatternStart()).isTrue();
            assertThat(runtime.commitPatternStart(prepared)).isTrue();
            thread.rememberStartedRecipe();
            assertThat(thread.cachedPatternRecipe(recipe.id())).isSameAs(recipe);
            thread.markRecipeFinished();
            assertThat(thread.hasRestartPending()).isFalse();

            var lookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var memory = new CompoundTag();
            thread.savePatternMemory(memory, lookup);
            assertThat(memory.contains("recipe")).isTrue();
            var savedRuntime = new CompoundTag();
            runtime.save(savedRuntime, lookup);
            var restoredRuntime = new CraftingRuntime(controller, controller.componentRuntime());
            restoredRuntime.load(savedRuntime, controller.resourceDomain(), lookup);
            assertThat(restoredRuntime.requiresPatternRequest()).isTrue();
            var restored = new MachineRecipeThread(controller, restoredRuntime);
            restored.loadPatternMemory(memory, lookup);
            assertThat(restored.cachedPatternRecipe(recipe.id())).isSameAs(recipe);
            restored.markRecipeFinished();
            assertThat(restored.hasRestartPending()).isFalse();
            assertThat(restored.peekRestartRecipe(List.of(recipe), 1L,
                    controller.currentRuntimeSnapshot().structure().version())).isNull();

            // A native payment callback can save before onStarted updates the remembered lane.
            memory.putBoolean("requires_pattern_request", false);
            restored.loadPatternMemory(memory, lookup);
            restored.markRecipeFinished();
            assertThat(restored.hasRestartPending()).isFalse();

            var equivalent = recipe(1);
            RecipeRegistry.replaceDynamic(Map.of(equivalent.id(), equivalent));
            assertThat(restored.cachedPatternRecipe(recipe.id())).isSameAs(
                    RecipeRegistry.catalogForPool(recipe.recipePoolId()).recipes().stream()
                            .filter(candidate -> candidate.id().equals(recipe.id())).findFirst().orElseThrow());

            var changed = recipe(2);
            RecipeRegistry.replaceDynamic(Map.of(changed.id(), changed));
            assertThat(restored.cachedPatternRecipe(recipe.id())).isNull();
        }
    }

    private static MachineRecipe recipe(int duration) {
        return MachineRecipe.fromCanonical(MMCR.id("normal_pattern_cache"), MMCR.id("test_cube"), duration,
                List.of(new ItemRequirement(RecipeModifier.IOType.INPUT, Ingredient.of(Items.IRON_INGOT), 1, ItemStack.EMPTY)),
                List.of(), List.of(), 0, 1, false, false, false, Set.of());
    }

    @Test
    void cold_controller_preserves_pending_memory_until_real_topology_then_uses_last_recipe() throws Exception {
        try (var ignored = ServerThreadTestScope.open()) {
            var id = MMCR.id("test_cube");
            var source = RuntimeTestFixtures.itemInput(new BlockPos(16, 0, 0));
            var machine = new DynamicMachine(id, "machine.mmcr.test_cube", new BlockArray(Map.of(
                    new BlockPos(1, 0, 0), new BlockPredicate.OfBlock(source.getBlockState().getBlock()))));
            MachineRegistry.clearForTesting();
            MachineRegistry.register(machine);
            var original = RuntimeTestFixtures.controllerEntity(id, new BlockPos(15, 0, 0),
                    ModBlocks.controllerFor(id).get().defaultBlockState().setValue(MachineControllerBlock.FACING, Direction.SOUTH));
            MachineControllerBlockEntity cold = null;
            try {
                RuntimeTestFixtures.formStructureWithComponents(original, machine, source);
                var recipe = recipe(2);
                RecipeRegistry.replaceDynamic(Map.of(recipe.id(), recipe));
                var request = new LongItemStorage(1, 64L, null);
                request.setContents(0, new ItemStack(Items.IRON_INGOT), 1L);
                try (var start = original.reserveExactPatternStart(source.getBlockPos(), recipe.id(), List.of(), 1L,
                        List.of(new ItemBusCapability(request, IOType.INPUT)))) {
                    assertThat(start.recipeSelection()).isEqualTo(PatternStartReservation.RecipeSelection.CATALOG);
                    assertThat(start.commit()).isTrue();
                }
                var runtime = RuntimeTestFixtures.craftingRuntime(original);
                for (int tick = 0; tick < 2; tick++) {
                    RuntimeTestFixtures.advanceGameTime(original.getLevel());
                    runtime.tick();
                }
                assertThat(runtime.finishPending()).isTrue();
                assertThat(runtime.ledger().state().finalTickPaid()).isTrue();
                runtime.finish();
                assertThat(runtime.active()).isFalse();
                var lookup = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
                var saved = original.saveWithFullMetadata(lookup);
                var memory = saved.getCompound("normal_pattern_memory").copy();
                assertThat(memory.contains("recipe")).isTrue();
                original.invalidateFormedStructure();

                cold = RuntimeTestFixtures.controllerEntity(id, original.getBlockPos(), original.getBlockState());
                RuntimeTestFixtures.replaceBlockEntity(original, cold);
                // No setMachine: this is the real new-BE order, before block-identity discovery.
                cold.loadWithComponents(saved.copy(), lookup);
                cold.loadWithComponents(saved.copy(), lookup);
                assertThat(cold.currentStructureSnapshot().configuredMachine()).isNull();
                assertThat(cold.saveWithFullMetadata(lookup).getCompound("normal_pattern_memory")).isEqualTo(memory);
                RuntimeTestFixtures.setLoadedChunks(cold.getLevel(), Set.of(LevelStub.chunkKey(0, 0)));
                cold.tickStructure((ServerLevel) cold.getLevel(), cold.getBlockPos());
                assertThat(cold.currentStructureSnapshot().configuredMachine()).isNotNull();
                for (int query = 0; query < 3; query++) {
                    assertThat(cold.runtimeRestorationComplete()).isFalse();
                    assertThat(cold.runtimeSnapshot().structure().configuredMachine().registryName()).isEqualTo(id);
                    assertThat(cold.currentRuntimeSnapshot().structure().configuredMachine().registryName()).isEqualTo(id);
                    assertThat(cold.currentRecipePoolId()).isEqualTo(recipe.recipePoolId());
                    assertThat(RuntimeTestFixtures.craftingRuntime(cold).active()).isFalse();
                    assertThat(cold.saveWithFullMetadata(lookup).getCompound("normal_pattern_memory")).isEqualTo(memory);
                }
                RuntimeTestFixtures.setLoadedChunks(cold.getLevel(), Set.of(LevelStub.chunkKey(0, 0), LevelStub.chunkKey(1, 0)));
                cold.requestImmediateStructureCheck();
                for (int step = 0; step < 32 && !cold.runtimeRestorationComplete(); step++) {
                    cold.tickStructure((ServerLevel) cold.getLevel(), cold.getBlockPos());
                    RuntimeTestFixtures.advanceGameTime(cold.getLevel());
                }
                assertThat(cold.runtimeRestorationComplete()).isTrue();
                var field = MachineControllerBlockEntity.class.getDeclaredField("normalRecipeThread");
                field.setAccessible(true);
                var thread = (MachineRecipeThread) field.get(cold);
                assertThat(thread.cachedPatternRecipe(recipe.id())).isSameAs(RecipeRegistry.getRecipe(recipe.id()));
                runtime = RuntimeTestFixtures.craftingRuntime(cold);
                for (int step = 0; step < 4; step++) runtime.tick();
                thread.markRecipeFinished();
                assertThat(runtime.active()).isFalse();
                assertThat(thread.hasRestartPending()).isFalse();
                assertThat(thread.peekRestartRecipe(List.of(recipe), 1L, cold.currentStructureSnapshot().version())).isNull();
                request.setContents(0, new ItemStack(Items.IRON_INGOT), 1L);
                try (var start = cold.reserveExactPatternStart(source.getBlockPos(), recipe.id(), List.of(), 1L,
                        List.of(new ItemBusCapability(request, IOType.INPUT)))) {
                    assertThat(start.recipeSelection()).isEqualTo(PatternStartReservation.RecipeSelection.LAST_RECIPE);
                    assertThat(start.commit()).isTrue();
                }
                assertThat(runtime.active()).isTrue();
                assertThat(request.amount(0)).isZero();
                for (int step = 0; step < 2; step++) {
                    RuntimeTestFixtures.advanceGameTime(cold.getLevel());
                    runtime.tick();
                }
                assertThat(runtime.finishPending()).isTrue();
                assertThat(runtime.ledger().state().finalTickPaid()).isTrue();
                runtime.finish();
                assertThat(runtime.active()).isFalse();
                thread.markRecipeFinished();
                for (int step = 0; step < 4; step++) {
                    RuntimeTestFixtures.advanceGameTime(cold.getLevel());
                    runtime.tick();
                }
                assertThat(runtime.active()).isFalse();
                assertThat(source.itemHandler().amount(0)).isZero();
                assertThat(thread.hasRestartPending()).isFalse();
            } finally {
                if (cold != null) cold.invalidateFormedStructure();
                original.invalidateFormedStructure();
                MachineRegistry.clearForTesting();
                MachineRegistry.restoreStartupForTesting();
            }
        }
    }
}
