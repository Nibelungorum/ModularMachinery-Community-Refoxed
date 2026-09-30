package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies runtime JEI refresh after MMCR content sync.
 *
 * @author howxu <dev@howxu.cn>
 */
class JeiRuntimeReloaderTest {

    @AfterEach
    void clearDefinitions() {
        MachineDefinitions.clearForTesting();
    }

    @Test
    void machineIds_include_dynamic_kubejs_machine_definitions() {
        ResourceLocation dynamicId = MMCR.id("jei_dynamic_machine");
        MachineDefinitions.register(MachineRegistration.builder(dynamicId).build());

        assertThat(JeiPlugin.machineIds()).contains(dynamicId);
    }

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        JeiRuntimeReloader.setClientExecutorForTesting(Runnable::run);
    }

    @AfterEach
    void clearRuntime() {
        JeiRuntimeReloader.clearRuntimeForTesting();
        RecipeRegistry.clearForTesting();
    }

    @BeforeEach
    void useDirectClientExecutor() {
        JeiRuntimeReloader.setClientExecutorForTesting(Runnable::run);
    }

    @Test
    void reloadWithoutJeiRuntimeDoesNothing() {
        JeiRuntimeReloader.clearRuntimeForTesting();

        assertThatCode(() -> JeiRuntimeReloader.reloadIfAvailable(RuntimeContentSnapshot.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    void reloadUpdatesJeiRecipesForSyncedMachines() {
        FakeRecipeManager manager = new FakeRecipeManager();
        ResourceLocation machineId = MMCR.id("test_machine_name");
        RecipeRegistry.clearForTesting();
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        JeiRuntimeReloader.setRuntime(runtime(manager));
        RuntimeContentSnapshot snapshot = snapshotWithRecipe(machineId, MMCR.id("jei_synced_recipe"));

        JeiRuntimeReloader.reloadIfAvailable(snapshot);

        assertThat(manager.addedTypes()).contains(JeiMachineRecipeTypes.forPool(machineId));
        assertThat(manager.addedRecipeIds()).contains(MMCR.id("jei_synced_recipe"));
        assertThat(manager.hiddenTypes()).contains(JeiMachineRecipeTypes.forPool(machineId));
        assertThat(manager.addedRecipeIds()).hasSize(1);
    }

    @Test
    void reloadUsesPoolCategoryForRecipesBoundToDifferentMachineIds() {
        FakeRecipeManager manager = new FakeRecipeManager();
        ResourceLocation machineId = MMCR.id("shared_pool_machine");
        ResourceLocation poolId = MMCR.id("shared_pool");
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(poolId));
        JeiRuntimeReloader.setRuntime(runtime(manager));

        JeiRuntimeReloader.reloadIfAvailable(snapshotWithRecipe(machineId, poolId, MMCR.id("shared_pool_recipe")));

        assertThat(manager.addedTypes()).containsExactly(JeiMachineRecipeTypes.forPool(poolId));
        assertThat(manager.addedRecipeIds()).containsExactly(MMCR.id("shared_pool_recipe"));
    }

    @Test
    void reloadMutatesJeiOnlyAfterTheInjectedClientExecutorRuns() {
        FakeRecipeManager manager = new FakeRecipeManager();
        ResourceLocation machineId = MMCR.id("test_machine_name");
        List<Runnable> queued = new ArrayList<>();
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        JeiRuntimeReloader.setRuntime(runtime(manager));
        JeiRuntimeReloader.setClientExecutorForTesting(queued::add);

        JeiRuntimeReloader.reloadIfAvailable(snapshotWithRecipe(machineId, MMCR.id("jei_executor_recipe")));

        assertThat(queued).singleElement();
        assertThat(manager.addedTypes()).isEmpty();
        queued.getFirst().run();
        assertThat(manager.addedRecipeIds()).containsExactly(MMCR.id("jei_executor_recipe"));
    }

    @Test
    void firstRuntimeReloadHidesDisplaysRegisteredBeforeRuntimeWasAvailable() {
        FakeRecipeManager manager = new FakeRecipeManager();
        ResourceLocation machineId = MMCR.id("test_machine_name");
        ResourceLocation recipeId = MMCR.id("initial_kubejs_recipe");
        MachineRecipe recipe = RecipeTestSupport.create(recipeId, machineId, 20, List.of(),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET), 1)));
        JeiRuntimeReloader.captureInitialDisplays(Map.of(machineId, List.of(MachineRecipeDisplay.from(recipe))));
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        JeiRuntimeReloader.setRuntime(runtime(manager));

        JeiRuntimeReloader.reloadIfAvailable(snapshotWithRecipe(machineId, recipeId));

        assertThat(manager.hiddenRecipeIds()).containsExactly(recipeId);
        assertThat(manager.addedRecipeIds()).containsExactly(recipeId);
    }

    @Test
    void runtimeReloadMakesReaddedDisplaysVisibleAfterHidingPreviousDisplays() {
        FakeRecipeManager manager = new FakeRecipeManager();
        manager.enforceHiddenRecipes();
        ResourceLocation machineId = MMCR.id("test_machine_name");
        ResourceLocation recipeId = MMCR.id("readded_runtime_recipe");
        MachineRecipe recipe = RecipeTestSupport.create(recipeId, machineId, 20, List.of(),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET), 1)));
        JeiRuntimeReloader.captureInitialDisplays(Map.of(machineId, List.of(MachineRecipeDisplay.from(recipe))));
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        JeiRuntimeReloader.setRuntime(runtime(manager));

        JeiRuntimeReloader.reloadIfAvailable(snapshotWithRecipe(machineId, recipeId));

        assertThat(manager.visibleRecipeIds()).containsExactly(recipeId);
    }

    @Test
    void reloadDoesNotRefreshTheSameCommittedVersionTwice() {
        FakeRecipeManager manager = new FakeRecipeManager();
        ResourceLocation machineId = MMCR.id("test_machine_name");
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        JeiRuntimeReloader.setRuntime(runtime(manager));
        RuntimeContentSnapshot snapshot = snapshotWithRecipe(machineId, MMCR.id("jei_same_version_recipe"));

        JeiRuntimeReloader.reloadIfAvailable(snapshot);
        manager.clearRecordedCalls();
        JeiRuntimeReloader.reloadIfAvailable(snapshot);

        assertThat(manager.addedTypes()).isEmpty();
        assertThat(manager.hiddenTypes()).isEmpty();
    }

    @Test
    void failedAsyncReloadDoesNotClaimVersionBeforeRetrySucceeds() {
        FakeRecipeManager manager = new FakeRecipeManager();
        ResourceLocation machineId = MMCR.id("test_machine_name");
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        JeiRuntimeReloader.setRuntime(runtime(manager));
        RuntimeContentSnapshot snapshot = snapshotWithRecipe(machineId, MMCR.id("jei_retry_recipe"));
        manager.failNextAdd();

        assertThatCode(() -> JeiRuntimeReloader.reloadIfAvailable(snapshot)).isInstanceOf(RuntimeException.class);
        JeiRuntimeReloader.reloadIfAvailable(snapshot);

        assertThat(manager.addedRecipeIds()).containsExactly(MMCR.id("jei_retry_recipe"));
    }

    @Test
    void reloadSkipsPoolsWithoutRegisteredJeiCategory() {
        FakeRecipeManager manager = new FakeRecipeManager();
        ResourceLocation machineId = MMCR.id("runtime_only_machine");
        RecipeRegistry.clearForTesting();
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(MMCR.id("test_machine_name")));
        JeiRuntimeReloader.setRuntime(runtime(manager));

        JeiRuntimeReloader.reloadIfAvailable(snapshotWithRecipe(machineId, MMCR.id("runtime_only_recipe")));

        assertThat(manager.addedTypes()).isEmpty();
        assertThat(manager.hiddenTypes()).isEmpty();
    }

    @Test
    void reloadHidesVisibleDisplaysForRemovedMachine() {
        FakeRecipeManager manager = new FakeRecipeManager();
        ResourceLocation machineId = MMCR.id("test_machine_name");
        ResourceLocation recipeId = MMCR.id("removed_runtime_recipe");
        RecipeRegistry.clearForTesting();
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        JeiRuntimeReloader.setRuntime(runtime(manager));

        JeiRuntimeReloader.reloadIfAvailable(snapshotWithRecipe(machineId, recipeId));
        manager.clearRecordedCalls();

        JeiRuntimeReloader.reloadIfAvailable(RuntimeContentSnapshot.empty());

        assertThat(manager.hiddenTypes()).containsExactly(JeiMachineRecipeTypes.forPool(machineId));
        assertThat(manager.hiddenRecipeIds()).containsExactly(recipeId);
        assertThat(manager.addedTypes()).doesNotContain(JeiMachineRecipeTypes.forPool(machineId));
        assertThat(manager.addedRecipeIds()).doesNotContain(recipeId);
    }

    @Test
    void reloadDoesNotHidePreExistingStaticDisplayForUnsyncedMachine() {
        FakeRecipeManager manager = new FakeRecipeManager();
        ResourceLocation machineId = MMCR.id("test_machine_name");
        ResourceLocation staticRecipeId = MMCR.id("pre_existing_static_recipe");
        RecipeRegistry.registerStatic(RecipeTestSupport.create(staticRecipeId, machineId, 20, List.of(),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET), 1))));
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        JeiRuntimeReloader.setRuntime(runtime(manager));

        JeiRuntimeReloader.reloadIfAvailable(RuntimeContentSnapshot.empty());

        assertThat(manager.hiddenRecipeIds()).doesNotContain(staticRecipeId);
        assertThat(manager.hiddenTypes()).isEmpty();
        assertThat(manager.addedTypes()).isEmpty();
    }

    private static RuntimeContentSnapshot snapshotWithRecipe(ResourceLocation machineId, ResourceLocation recipeId) {
        return snapshotWithRecipe(machineId, machineId, recipeId);
    }

    private static RuntimeContentSnapshot snapshotWithRecipe(ResourceLocation machineId, ResourceLocation recipePoolId, ResourceLocation recipeId) {
        return new RuntimeContentSnapshot(
                Map.of(machineId, new MachineStructureDefinition(
                        machineId,
                        new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE))),
                        PortRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY)),
                Map.of(recipeId, RecipeTestSupport.create(recipeId, recipePoolId, 20, List.of(),
                        List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET), 1)))),
                Map.of(), Map.of(), Map.of(machineId, List.of(recipePoolId)), 1L);
    }

    private static IJeiRuntime runtime(FakeRecipeManager manager) {
        return (IJeiRuntime) Proxy.newProxyInstance(
                JeiRuntimeReloaderTest.class.getClassLoader(),
                new Class<?>[]{IJeiRuntime.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRecipeManager" -> manager.proxy();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "FakeJeiRuntime";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class FakeRecipeManager {
        private final List<RecipeType<?>> addedTypes = new ArrayList<>();
        private final List<ResourceLocation> addedRecipeIds = new ArrayList<>();
        private final List<RecipeType<?>> hiddenTypes = new ArrayList<>();
        private final List<ResourceLocation> hiddenRecipeIds = new ArrayList<>();
        private final Set<ResourceLocation> hiddenRecipes = new LinkedHashSet<>();
        private final List<ResourceLocation> visibleRecipeIds = new ArrayList<>();
        private final AtomicBoolean failNextAdd = new AtomicBoolean();
        private boolean enforceHiddenRecipes;

        IRecipeManager proxy() {
            return (IRecipeManager) Proxy.newProxyInstance(
                    JeiRuntimeReloaderTest.class.getClassLoader(),
                    new Class<?>[]{IRecipeManager.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("addRecipes")) {
                            if (failNextAdd.compareAndSet(true, false)) {
                                throw new IllegalStateException("synthetic JEI reload failure");
                            }
                            addedTypes.add((RecipeType<?>) args[0]);
                            ((List<?>) args[1]).stream()
                                    .map(MachineRecipeDisplay.class::cast)
                                    .map(MachineRecipeDisplay::recipeId)
                                    .forEach(recipeId -> {
                                        addedRecipeIds.add(recipeId);
                                        if (!enforceHiddenRecipes || !hiddenRecipes.contains(recipeId)) {
                                            visibleRecipeIds.add(recipeId);
                                        }
                                    });
                            return null;
                        }
                        if (method.getName().equals("hideRecipes")) {
                            hiddenTypes.add((RecipeType<?>) args[0]);
                            Collection<?> displays = (Collection<?>) args[1];
                            assertThat(displays).allMatch(MachineRecipeDisplay.class::isInstance);
                            displays.stream()
                                    .map(MachineRecipeDisplay.class::cast)
                                    .map(MachineRecipeDisplay::recipeId)
                                    .forEach(hiddenRecipeIds::add);
                            displays.stream()
                                    .map(MachineRecipeDisplay.class::cast)
                                    .map(MachineRecipeDisplay::recipeId)
                                    .forEach(hiddenRecipes::add);
                            return null;
                        }
                        if (method.getName().equals("unhideRecipes")) {
                            Collection<?> displays = (Collection<?>) args[1];
                            displays.stream()
                                    .map(MachineRecipeDisplay.class::cast)
                                    .map(MachineRecipeDisplay::recipeId)
                                    .forEach(hiddenRecipes::remove);
                            return null;
                        }
                        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                        if (method.getName().equals("equals")) return proxy == args[0];
                        if (method.getName().equals("toString")) return "FakeRecipeManager";
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        List<RecipeType<?>> addedTypes() {
            return addedTypes;
        }

        List<ResourceLocation> addedRecipeIds() {
            return addedRecipeIds;
        }

        List<RecipeType<?>> hiddenTypes() {
            return hiddenTypes;
        }

        List<ResourceLocation> hiddenRecipeIds() {
            return hiddenRecipeIds;
        }

        List<ResourceLocation> visibleRecipeIds() {
            return visibleRecipeIds;
        }

        void enforceHiddenRecipes() {
            enforceHiddenRecipes = true;
        }

        void clearRecordedCalls() {
            addedTypes.clear();
            addedRecipeIds.clear();
            hiddenTypes.clear();
            hiddenRecipeIds.clear();
            visibleRecipeIds.clear();
        }

        void failNextAdd() {
            failNextAdd.set(true);
        }
    }
}
