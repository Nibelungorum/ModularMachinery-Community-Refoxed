package cn.howxu.mmcr.compat.jei;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.sync.RuntimeContentSnapshot;
import cn.howxu.mmcr.internal.sync.ClientRuntimeSnapshotBridge;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
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
        Identifier dynamicId = MMCR.id("jei_dynamic_machine");
        MachineDefinitions.register(MachineRegistration.builder(dynamicId).build());

        assertThat(JeiPlugin.machineIds()).contains(dynamicId);
    }

    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
        Items.IRON_NUGGET.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        JeiRuntimeReloader.setClientExecutorForTesting(Runnable::run);
    }

    @AfterEach
    void clearRuntime() {
        JeiRuntimeReloader.clearRuntimeForTesting();
        RecipeRegistry.clearForTesting();
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
    }

    @BeforeEach
    void useDirectClientExecutor() {
        MachineRegistry.clearForTesting();
        MachineStructureRegistry.clearForTesting();
        ClientRuntimeSnapshotBridge.resetForConnection();
        JeiRuntimeReloader.setClientExecutorForTesting(Runnable::run);
    }

    @Test
    void reloadWithoutJeiRuntimeDoesNothing() {
        JeiRuntimeReloader.clearRuntimeForTesting();

        assertThatCode(() -> JeiRuntimeReloader.reloadIfAvailable(RuntimeContentSnapshot.empty()))
                .doesNotThrowAnyException();
    }

    @Test
    void snapshotReceivedBeforeJeiStartsIsReplayedWhenRuntimeBecomesAvailable() {
        FakeRecipeManager manager = new FakeRecipeManager();
        Identifier machineId = MMCR.id("test_machine_name");
        RuntimeContentSnapshot early = snapshotWithRecipe(machineId, MMCR.id("early_synced_recipe"));
        RuntimeContentSnapshot latest = withVersion(snapshotWithRecipe(machineId, MMCR.id("latest_synced_recipe")), 2L);

        JeiRuntimeReloader.reloadIfAvailable(early);
        JeiRuntimeReloader.reloadIfAvailable(latest);
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        new JeiPlugin().onRuntimeAvailable(runtime(manager));

        assertThat(manager.addedRecipeIds()).containsExactly(MMCR.id("latest_synced_recipe"));
    }

    @Test
    void runtimeSyncAddsUpdatesAndRemovesStructuresWithoutClientServerScripts() {
        FakeRecipeManager manager = new FakeRecipeManager();
        Identifier machineId = MMCR.id("server_only_structure");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());
        JeiRuntimeReloader.captureInitialStructures(List.of());
        JeiRuntimeReloader.setRuntime(runtime(manager));
        RuntimeContentSnapshot first = snapshotWithRecipe(machineId, MMCR.id("server_only_recipe"));

        assertThat(first.applyClient()).isTrue();
        JeiRuntimeReloader.reloadIfAvailable(first);

        assertThat(manager.addedStructures).singleElement().satisfies(display -> {
            assertThat(display.machine().registryName()).isEqualTo(machineId);
            assertThat(display.machine().pattern()).isEqualTo(first.structures().get(machineId).pattern());
        });
        MachineStructureDisplay oldDisplay = manager.addedStructures.getFirst();
        MachineStructureDefinition replacement = new MachineStructureDefinition(machineId,
                new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))),
                PortRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);
        RuntimeContentSnapshot updated = new RuntimeContentSnapshot(Map.of(machineId, replacement),
                first.recipes(), Map.of(), Map.of(), first.machineRecipePools(), 2L);
        manager.clearRecordedCalls();

        assertThat(updated.applyClient()).isTrue();
        JeiRuntimeReloader.reloadIfAvailable(updated);

        assertThat(manager.hiddenStructures).containsExactly(oldDisplay);
        assertThat(manager.addedStructures).singleElement().satisfies(display ->
                assertThat(display.machine().pattern()).isEqualTo(replacement.pattern()));
        MachineStructureDisplay updatedDisplay = manager.addedStructures.getFirst();
        manager.clearRecordedCalls();
        RuntimeContentSnapshot removed = withVersion(RuntimeContentSnapshot.empty(), 3L);

        assertThat(removed.applyClient()).isTrue();
        JeiRuntimeReloader.reloadIfAvailable(removed);

        assertThat(manager.hiddenStructures).containsExactly(updatedDisplay);
        assertThat(manager.addedStructures).isEmpty();
    }

    @Test
    void firstStructureRefreshHidesTheDisplaysRegisteredDuringJeiStartup() {
        FakeRecipeManager manager = new FakeRecipeManager();
        Identifier machineId = MMCR.id("initial_structure");
        MachineDefinitions.register(MachineRegistration.builder(machineId).build());
        RuntimeContentSnapshot snapshot = snapshotWithRecipe(machineId, MMCR.id("initial_structure_recipe"));
        snapshot.applyClient();
        MachineStructureDisplay initial = MachineStructureDisplay.from(MachineRegistry.getMachine(machineId));
        JeiRuntimeReloader.captureInitialStructures(List.of(initial));
        JeiRuntimeReloader.setRuntime(runtime(manager));

        JeiRuntimeReloader.reloadIfAvailable(snapshot);

        assertThat(manager.hiddenStructures).containsExactly(initial);
        assertThat(manager.addedStructures).singleElement();
    }

    @Test
    void disconnectDiscardsQueuedRefreshAndAllowsLowerVersionOnNextServer() {
        Identifier machineId = MMCR.id("test_machine_name");
        FakeRecipeManager previous = new FakeRecipeManager();
        FakeRecipeManager next = new FakeRecipeManager();
        List<Runnable> queued = new ArrayList<>();
        JeiRuntimeReloader.setClientExecutorForTesting(queued::add);
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        JeiRuntimeReloader.setRuntime(runtime(previous));
        JeiRuntimeReloader.reloadIfAvailable(withVersion(
                snapshotWithRecipe(machineId, MMCR.id("old_server_recipe")), 90L));

        new JeiPlugin().onRuntimeUnavailable();
        JeiRuntimeReloader.reloadIfAvailable(snapshotWithRecipe(machineId, MMCR.id("new_server_recipe")));
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        JeiRuntimeReloader.setRuntime(runtime(next));
        queued.forEach(Runnable::run);

        assertThat(previous.addedRecipeIds()).isEmpty();
        assertThat(next.addedRecipeIds()).containsExactly(MMCR.id("new_server_recipe"));
    }

    private static RuntimeContentSnapshot withVersion(RuntimeContentSnapshot snapshot, long version) {
        return new RuntimeContentSnapshot(snapshot.structures(), snapshot.recipes(), snapshot.controllerSpecs(),
                snapshot.appearances(), snapshot.machineRecipePools(), version);
    }

    @Test
    void reloadUpdatesJeiRecipesForSyncedMachines() {
        FakeRecipeManager manager = new FakeRecipeManager();
        Identifier machineId = MMCR.id("test_machine_name");
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
        Identifier machineId = MMCR.id("shared_pool_machine");
        Identifier poolId = MMCR.id("shared_pool");
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(poolId));
        JeiRuntimeReloader.setRuntime(runtime(manager));

        JeiRuntimeReloader.reloadIfAvailable(snapshotWithRecipe(machineId, poolId, MMCR.id("shared_pool_recipe")));

        assertThat(manager.addedTypes()).containsExactly(JeiMachineRecipeTypes.forPool(poolId));
        assertThat(manager.addedRecipeIds()).containsExactly(MMCR.id("shared_pool_recipe"));
    }

    @Test
    void reloadMutatesJeiOnlyAfterTheInjectedClientExecutorRuns() {
        FakeRecipeManager manager = new FakeRecipeManager();
        Identifier machineId = MMCR.id("test_machine_name");
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
        Identifier machineId = MMCR.id("test_machine_name");
        Identifier recipeId = MMCR.id("initial_kubejs_recipe");
        MachineRecipe recipe = RecipeTestSupport.create(recipeId, machineId, 20, List.of(),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)));
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
        Identifier machineId = MMCR.id("test_machine_name");
        Identifier recipeId = MMCR.id("readded_runtime_recipe");
        MachineRecipe recipe = RecipeTestSupport.create(recipeId, machineId, 20, List.of(),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)));
        JeiRuntimeReloader.captureInitialDisplays(Map.of(machineId, List.of(MachineRecipeDisplay.from(recipe))));
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        JeiRuntimeReloader.setRuntime(runtime(manager));

        JeiRuntimeReloader.reloadIfAvailable(snapshotWithRecipe(machineId, recipeId));

        assertThat(manager.visibleRecipeIds()).containsExactly(recipeId);
    }

    @Test
    void reloadDoesNotRefreshTheSameCommittedVersionTwice() {
        FakeRecipeManager manager = new FakeRecipeManager();
        Identifier machineId = MMCR.id("test_machine_name");
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
        Identifier machineId = MMCR.id("test_machine_name");
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
        Identifier machineId = MMCR.id("runtime_only_machine");
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
        Identifier machineId = MMCR.id("test_machine_name");
        Identifier recipeId = MMCR.id("removed_runtime_recipe");
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
        Identifier machineId = MMCR.id("test_machine_name");
        Identifier staticRecipeId = MMCR.id("pre_existing_static_recipe");
        RecipeRegistry.registerStatic(RecipeTestSupport.create(staticRecipeId, machineId, 20, List.of(),
                List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1))));
        JeiRuntimeReloader.markRegisteredRecipePoolCategories(List.of(machineId));
        JeiRuntimeReloader.setRuntime(runtime(manager));

        JeiRuntimeReloader.reloadIfAvailable(RuntimeContentSnapshot.empty());

        assertThat(manager.hiddenRecipeIds()).doesNotContain(staticRecipeId);
        assertThat(manager.hiddenTypes()).isEmpty();
        assertThat(manager.addedTypes()).isEmpty();
    }

    private static RuntimeContentSnapshot snapshotWithRecipe(Identifier machineId, Identifier recipeId) {
        return snapshotWithRecipe(machineId, machineId, recipeId);
    }

    private static RuntimeContentSnapshot snapshotWithRecipe(Identifier machineId, Identifier recipePoolId, Identifier recipeId) {
        return new RuntimeContentSnapshot(
                Map.of(machineId, new MachineStructureDefinition(
                        machineId,
                        new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.BLAST_FURNACE))),
                        PortRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY)),
                Map.of(recipeId, RecipeTestSupport.create(recipeId, recipePoolId, 20, List.of(),
                        List.of(new ItemStack(Holder.direct(Items.IRON_NUGGET, DataComponentMap.EMPTY), 1)))),
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
        private final List<MachineStructureDisplay> addedStructures = new ArrayList<>();
        private final List<MachineStructureDisplay> hiddenStructures = new ArrayList<>();
        private final List<IRecipeType<?>> addedTypes = new ArrayList<>();
        private final List<Identifier> addedRecipeIds = new ArrayList<>();
        private final List<IRecipeType<?>> hiddenTypes = new ArrayList<>();
        private final List<Identifier> hiddenRecipeIds = new ArrayList<>();
        private final Set<Identifier> hiddenRecipes = new LinkedHashSet<>();
        private final List<Identifier> visibleRecipeIds = new ArrayList<>();
        private final AtomicBoolean failNextAdd = new AtomicBoolean();
        private boolean enforceHiddenRecipes;

        IRecipeManager proxy() {
            return (IRecipeManager) Proxy.newProxyInstance(
                    JeiRuntimeReloaderTest.class.getClassLoader(),
                    new Class<?>[]{IRecipeManager.class},
                    (proxy, method, args) -> {
                        if (args != null && args.length == 2 && args[0] == JeiMachineRecipeTypes.STRUCTURE) {
                            Collection<?> displays = (Collection<?>) args[1];
                            switch (method.getName()) {
                                case "addRecipes" -> displays.stream().map(MachineStructureDisplay.class::cast)
                                        .forEach(addedStructures::add);
                                case "hideRecipes" -> displays.stream().map(MachineStructureDisplay.class::cast)
                                        .forEach(hiddenStructures::add);
                                case "unhideRecipes" -> { }
                                default -> throw new UnsupportedOperationException(method.getName());
                            }
                            return null;
                        }
                        if (method.getName().equals("addRecipes")) {
                            if (failNextAdd.compareAndSet(true, false)) {
                                throw new IllegalStateException("synthetic JEI reload failure");
                            }
                            addedTypes.add((IRecipeType<?>) args[0]);
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
                            hiddenTypes.add((IRecipeType<?>) args[0]);
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

        List<IRecipeType<?>> addedTypes() {
            return addedTypes;
        }

        List<Identifier> addedRecipeIds() {
            return addedRecipeIds;
        }

        List<IRecipeType<?>> hiddenTypes() {
            return hiddenTypes;
        }

        List<Identifier> hiddenRecipeIds() {
            return hiddenRecipeIds;
        }

        List<Identifier> visibleRecipeIds() {
            return visibleRecipeIds;
        }

        void enforceHiddenRecipes() {
            enforceHiddenRecipes = true;
        }

        void clearRecordedCalls() {
            addedStructures.clear();
            hiddenStructures.clear();
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
