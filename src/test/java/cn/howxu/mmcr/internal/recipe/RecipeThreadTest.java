package cn.howxu.mmcr.internal.recipe;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.status.BuiltinFailureReasons;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.DynamicMachine;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.config.ServerConfig;
import cn.howxu.mmcr.internal.async.MachineAsyncCoordinator;
import cn.howxu.mmcr.internal.multiblock.SharedIoCoordinator;
import cn.howxu.mmcr.internal.multiblock.StructureClaimRegistry;
import cn.howxu.mmcr.internal.runtime.MachineWorkMode;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.test.ConfigTestSupport;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import com.electronwill.nightconfig.core.CommentedConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.config.IConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies machine-pool boundaries for ordinary recipe threads.
 *
 * @author howxu <dev@howxu.cn>
 */
class RecipeThreadTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
        CommentedConfig config = CommentedConfig.inMemory();
        ServerConfig.SPEC.correct(config);
        var constructor = Class.forName("net.neoforged.fml.config.LoadedConfig").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        ServerConfig.SPEC.acceptConfig((IConfigSpec.ILoadedConfig) constructor.newInstance(config, null, null));
    }

    @AfterEach
    void cleanup() {
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        RecipeRegistry.clearForTesting();
    }

    @Test
    void ordinary_search_ignores_a_recipe_from_a_different_pool() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        MachineRecipe foreign = RecipeTestSupport.create(MMCR.id("ordinary_foreign_pool_recipe"),
                MMCR.id("ordinary_foreign_pool"), 20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);

        assertThat(thread.searchAndStartRecipe(List.of(foreign), 1,
                controller.runtimeSnapshot().structure().version())).isFalse();
        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.runtime().failure()).isNotNull();
        assertThat(thread.runtime().failure().reason()).isEqualTo(BuiltinFailureReasons.RECIPE_SEARCH);
    }

    @Test
    void ordinary_search_uses_the_controller_selected_recipe_pool() {
        Identifier machineId = MMCR.id("ordinary_selected_pool_machine");
        Identifier firstPool = MMCR.id("ordinary_selected_pool_first");
        Identifier secondPool = MMCR.id("ordinary_selected_pool_second");
        MachineControllerBlockEntity controller = multiPoolController(machineId, firstPool, secondPool);
        assertThat(controller.selectRecipePool(secondPool)).isTrue();
        MachineRecipe first = RecipeTestSupport.create(MMCR.id("ordinary_selected_pool_first_recipe"), firstPool,
                20, List.of(), List.of());
        MachineRecipe second = RecipeTestSupport.create(MMCR.id("ordinary_selected_pool_second_recipe"), secondPool,
                20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);

        assertThat(thread.searchAndStartRecipe(List.of(first, second), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        assertThat(thread.runtime().recipe()).isEqualTo(second);
    }

    @Test
    void async_ordinary_search_ignores_a_recipe_from_a_different_pool() {
        MachineControllerBlockEntity controller = normalController();
        MachineRecipe foreign = RecipeTestSupport.create(MMCR.id("async_ordinary_foreign_pool_recipe"),
                MMCR.id("async_ordinary_foreign_pool"), 20, List.of(), List.of());
        MachineRecipe valid = RecipeTestSupport.create(MMCR.id("async_ordinary_valid_pool_recipe"),
                MMCR.id("test_cube"), 20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        assertThat(thread.searchAndStartAsyncRecipe(List.of(foreign, valid), 1,
                controller.runtimeSnapshot().structure().version(), null)).isTrue();

        completeAsyncLevelTick((ServerLevel) controller.getLevel());

        assertThat(thread.runtime().active()).isTrue();
        assertThat(thread.runtime().recipe()).isEqualTo(valid);
    }

    @Test
    void switching_pools_discards_an_outstanding_async_search_result() {
        Identifier machineId = MMCR.id("test_cube");
        Identifier firstPool = MMCR.id("async_selected_pool_first");
        Identifier secondPool = MMCR.id("async_selected_pool_second");
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId)
                .recipePoolIds(List.of(firstPool, secondPool)).build());
        MachineControllerBlockEntity controller = normalController();
        assertThat(controller.selectRecipePool(secondPool)).isTrue();
        MachineRecipe second = RecipeTestSupport.create(MMCR.id("async_selected_pool_recipe"), secondPool,
                20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);

        assertThat(thread.searchAndStartAsyncRecipe(List.of(second), 1,
                controller.runtimeSnapshot().structure().version(), null)).isTrue();
        assertThat(controller.selectRecipePool(firstPool)).isTrue();
        completeAsyncLevelTick((ServerLevel) controller.getLevel());

        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.hasPendingAsyncSearch()).isFalse();
    }

    @Test
    void async_normal_controller_waits_for_worker_search_before_starting() {
        MachineControllerBlockEntity controller = normalController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_normal_search"), MMCR.id("test_cube"), 20,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        controller.serverTick();

        assertThat(controller.isRuntimeActive()).isFalse();

        completeAsyncLevelTick((ServerLevel) controller.getLevel());

        assertThat(controller.isRuntimeActive()).isTrue();
    }

    @Test
    void async_normal_controller_discards_a_structure_changed_search_and_retries() {
        MachineControllerBlockEntity controller = normalController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_normal_stale_search"), MMCR.id("test_cube"), 20,
                List.of(), List.of());
        RecipeRegistry.registerStatic(recipe);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);
        ServerLevel level = (ServerLevel) controller.getLevel();

        controller.serverTick();
        controller.setFormed(false);
        completeAsyncLevelTick(level);

        assertThat(controller.isRuntimeActive()).isFalse();

        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        RuntimeTestFixtures.advanceGameTime(level);
        controller.serverTick();
        completeAsyncLevelTick(level);

        assertThat(controller.isRuntimeActive()).isTrue();
    }

    @Test
    void valid_last_recipe_restarts_without_a_normal_candidate_search() {
        MachineControllerBlockEntity controller = normalController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("async_normal_last_recipe"), MMCR.id("test_cube"), 20,
                List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);
        ConfigTestSupport.setMachineWorkMode(MachineWorkMode.ASYNC);

        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        thread.markRecipeFinished();
        thread.runtime().invalidate();

        assertThat(thread.tryRestartLastRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        assertThat(thread.hasPendingAsyncSearch()).isFalse();
        assertThat(thread.runtime().active()).isTrue();
    }

    @Test
    void recipe_pool_change_discards_an_active_recipe_and_its_restart_memory() {
        MachineControllerBlockEntity controller = normalController();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("recipe_pool_active_discard"), MMCR.id("test_cube"),
                20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);
        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        thread.markRecipeFinished();

        thread.discardForRecipePoolChange();

        assertThat(thread.runtime().active()).isFalse();
        assertThat(thread.tryRestartLastRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isFalse();
    }

    @Test
    void recipe_pool_change_discards_a_pending_recipe_start() {
        MachineControllerBlockEntity controller = normalController();
        ServerLevel level = (ServerLevel) controller.getLevel();
        assertThat(StructureClaimRegistry.get(level).claim(controller.getBlockPos(), List.of()).accepted()).isTrue();
        MachineRecipe recipe = RecipeTestSupport.create(MMCR.id("recipe_pool_pending_discard"), MMCR.id("test_cube"),
                20, List.of(), List.of());
        MachineRecipeThread thread = new MachineRecipeThread(controller);
        assertThat(thread.searchAndStartRecipe(List.of(recipe), 1,
                controller.runtimeSnapshot().structure().version())).isTrue();
        assertThat(thread.isStartPending()).isTrue();

        thread.discardForRecipePoolChange();
        completeAsyncLevelTick(level);

        assertThat(thread.isStartPending()).isFalse();
        assertThat(thread.runtime().active()).isFalse();
    }

    private static MachineControllerBlockEntity normalController() {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controllerEntity(MMCR.id("test_cube"), BlockPos.ZERO);
        RuntimeTestFixtures.formStructure(controller,
                new DynamicMachine(MMCR.id("test_cube"), "async normal", new BlockArray(Map.of())));
        controller.setFormed(true);
        RuntimeTestFixtures.republish(controller);
        StructureClaimRegistry.get((ServerLevel) controller.getLevel()).release(controller.getBlockPos());
        return controller;
    }

    private static MachineControllerBlockEntity multiPoolController(Identifier machineId, Identifier... pools) {
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(MachineRegistration.builder(machineId).recipePoolIds(List.of(pools)).build());
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_cube"));
        controller.setMachine(new DynamicMachine(machineId, "multi pool", new BlockArray(Map.of())));
        return controller;
    }

    private static void completeAsyncLevelTick(ServerLevel level) {
        SharedIoCoordinator sharedIo = SharedIoCoordinator.get(level);
        MachineAsyncCoordinator async = MachineAsyncCoordinator.get(level);
        long gameTime = level.getGameTime();
        sharedIo.beginLevelTick(gameTime);
        async.beginLevelTick(gameTime);
        sharedIo.resolve(level);
        async.completeUntilIdleForTesting(() -> sharedIo.resolve(level));
        MachineControllerBlockEntity.flushQueuedAsyncRuntimeState(level);
    }
}
