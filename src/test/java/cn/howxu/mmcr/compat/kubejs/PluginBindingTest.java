package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.definition.RecipeBehavior;
import cn.howxu.mmcr.api.machine.definition.RecipeTickContext;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.machine.MachineRegistration;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.ModifierRegistry;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.internal.network.RuntimeContentServerBridge;
import cn.howxu.mmcr.internal.network.RuntimeContentSync;
import cn.howxu.mmcr.internal.registration.StartupContentRegistration;
import cn.howxu.mmcr.internal.sync.RuntimeContentVersion;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.RecipeTestSupport;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.internal.tile.MachineControllerBlockEntity;
import cn.howxu.mmcr.internal.tile.MachineControllerRuntime;
import com.google.gson.JsonParser;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.resources.ResourceLocation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author howxu <dev@howxu.cn>
 */
class PluginBindingTest {

    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @AfterEach
    void clearRecipes() {
        KubeJSContentReloadTransaction.clearPublishedForTesting();
        Plugin.clearCurrentServerForTesting();
        RuntimeContentServerBridge.clearForTesting();
        RuntimeContentSync.resetSenderForTesting();
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
        ModifierRegistry.installSnapshot(Map.of());
    }

    @Test
    void kubejs_content_transaction_replaces_dynamic_content_only_after_validation() {
        var machineId = MMCR.id("test_machine_name");
        var recipeId = MMCR.id("kubejs_transaction_success_recipe");

        var transaction = new KubeJSContentReloadTransaction();
        transaction.registerStructure(structure(machineId));
        transaction.registerRecipe(RecipeTestSupport.create(recipeId, machineId, 1, List.of(), List.of()));
        var committed = transaction.commit();

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsKey(machineId);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsKey(recipeId);
        assertThat(committed.snapshot().structures()).isEqualTo(MachineStructureRegistry.effectiveSnapshot());
        assertThat(committed.snapshot().recipes()).isEqualTo(RecipeRegistry.effectiveSnapshot());
    }

    @Test
    void kubejs_recipe_reload_keeps_builtin_machine_structure() {
        var machineId = MMCR.id("test_machine_name");
        var recipeId = MMCR.id("kubejs_recipe_with_builtin_structure");
        MachineStructureRegistry.replaceDynamic(Map.of(machineId, structure(machineId)));

        var transaction = new KubeJSContentReloadTransaction();
        transaction.registerRecipe(RecipeTestSupport.create(recipeId, machineId, 1, List.of(), List.of()));
        transaction.commit();

        assertThat(MachineRegistry.getMachine(machineId)).isNotNull();
        assertThat(RecipeRegistry.dynamicSnapshot()).containsKey(recipeId);
    }

    @Test
    void kubejs_transaction_discards_orphan_pool_recipe_and_keeps_valid_recipe() {
        var machineId = MMCR.id("test_machine_name");
        var validRecipeId = MMCR.id("kubejs_valid_pool_recipe");
        var orphanRecipeId = MMCR.id("kubejs_orphan_pool_recipe");

        var transaction = new KubeJSContentReloadTransaction();
        transaction.registerStructure(structure(machineId));
        transaction.registerRecipe(RecipeTestSupport.create(validRecipeId, machineId, 1, List.of(), List.of()));
        transaction.registerRecipe(RecipeTestSupport.create(orphanRecipeId,
                MMCR.id("missing_recipe_pool"), 1, List.of(), List.of()));

        var committed = transaction.commit();

        assertThat(RecipeRegistry.dynamicSnapshot()).containsKey(validRecipeId).doesNotContainKey(orphanRecipeId);
        assertThat(committed.result().errors()).singleElement().satisfies(error -> {
            assertThat(error.recipeId()).isEqualTo(orphanRecipeId);
            assertThat(error.path()).isEqualTo("recipe_pool");
        });
    }

    @Test
    void kubejs_transaction_keeps_cross_pool_dynamic_recipe_and_publishes_valid_recipes() {
        var firstPoolId = MMCR.id("test_machine_name");
        var secondPoolId = MMCR.id("cracker");
        var samePoolId = MMCR.id("kubejs_transaction_same_pool_recipe");
        var crossPoolId = MMCR.id("kubejs_transaction_cross_pool_recipe");
        var validId = MMCR.id("kubejs_transaction_valid_after_cross_pool_conflict");
        var dynamicSamePool = RecipeTestSupport.create(samePoolId, firstPoolId, 1, List.of(), List.of());
        var dynamicCrossPool = RecipeTestSupport.create(crossPoolId, firstPoolId, 2, List.of(), List.of());
        RecipeRegistry.replaceDynamic(Map.of(samePoolId, dynamicSamePool, crossPoolId, dynamicCrossPool));

        var transaction = new KubeJSContentReloadTransaction();
        transaction.registerStructure(structure(firstPoolId));
        transaction.registerStructure(structure(secondPoolId));
        var kubeJSSamePool = RecipeTestSupport.create(samePoolId, firstPoolId, 3, List.of(), List.of());
        var kubeJSCrossPool = RecipeTestSupport.create(crossPoolId, secondPoolId, 4, List.of(), List.of());
        var valid = RecipeTestSupport.create(validId, secondPoolId, 5, List.of(), List.of());
        transaction.registerRecipe(kubeJSSamePool);
        transaction.registerRecipe(kubeJSCrossPool);
        transaction.registerRecipe(valid);

        var committed = transaction.commit();

        assertThat(committed.result().errors()).singleElement().satisfies(error -> {
            assertThat(error.recipeId()).isEqualTo(crossPoolId);
            assertThat(error.path()).isEqualTo("recipe_pool");
            assertThat(error.getMessage()).contains(firstPoolId.toString());
        });
        assertThat(RecipeRegistry.dynamicSnapshot()).containsEntry(samePoolId, kubeJSSamePool)
                .containsEntry(crossPoolId, dynamicCrossPool)
                .containsEntry(validId, valid);
        assertThat(RecipeRegistry.getRecipe(samePoolId)).isSameAs(kubeJSSamePool);
        assertThat(RecipeRegistry.getRecipe(crossPoolId)).isSameAs(dynamicCrossPool);
    }

    @Test
    void orphan_pool_transaction_replaces_previous_snapshot_with_valid_recipes_only() {
        var machineId = MMCR.id("test_machine_name");
        var recipeId = MMCR.id("kubejs_transaction_previous_recipe");
        var validRecipeId = MMCR.id("kubejs_transaction_valid_after_orphan");

        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(machineId));
        previous.registerRecipe(RecipeTestSupport.create(recipeId, machineId, 1, List.of(), List.of()));
        previous.commit();
        long previousVersion = RuntimeContentVersion.current();

        var invalid = new KubeJSContentReloadTransaction();
        invalid.registerStructure(structure(machineId));
        invalid.registerRecipe(RecipeTestSupport.create(validRecipeId, machineId, 1, List.of(), List.of()));
        invalid.registerRecipe(RecipeTestSupport.create(MMCR.id("invalid_kubejs_transaction_recipe"),
                MMCR.id("missing_machine"), 1, List.of(), List.of()));

        invalid.commit();

        assertThat(RecipeRegistry.dynamicSnapshot()).containsKey(validRecipeId)
                .doesNotContainKey(recipeId)
                .doesNotContainKey(MMCR.id("invalid_kubejs_transaction_recipe"));
        assertThat(RuntimeContentVersion.current()).isGreaterThan(previousVersion);
    }

    @Test
    void successful_kubejs_server_reload_replaces_previous_script_snapshot() {
        var removedMachineId = MMCR.id("test_machine_name");
        var keptMachineId = MMCR.id("cracker");
        var removedRecipeId = MMCR.id("kubejs_transaction_removed_recipe");
        var keptRecipeId = MMCR.id("kubejs_transaction_kept_recipe");

        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(removedMachineId));
        previous.registerStructure(structure(keptMachineId));
        previous.registerRecipe(RecipeTestSupport.create(removedRecipeId, removedMachineId, 1, List.of(), List.of()));
        previous.commit();

        var reload = new Object();
        Plugin.beginServerReload(reload, 0);
        KubeJSContentReloadTransaction.active().registerStructure(structure(keptMachineId));
        KubeJSContentReloadTransaction.active().registerRecipe(RecipeTestSupport.create(keptRecipeId, keptMachineId, 1, List.of(), List.of()));
        Plugin.completeServerReload(reload, 0);

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsOnlyKeys(keptMachineId);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsOnlyKeys(keptRecipeId);
    }

    @Test
    void successful_kubejs_server_reload_runs_completion_sync_after_commit() {
        var machineId = MMCR.id("test_machine_name");
        var recipeId = MMCR.id("kubejs_sync_after_commit_recipe");
        var reload = new Object();
        AtomicBoolean synced = new AtomicBoolean();

        Plugin.beginServerReload(reload, 0);
        KubeJSContentReloadTransaction.active().registerStructure(structure(machineId));
        KubeJSContentReloadTransaction.active().registerRecipe(RecipeTestSupport.create(recipeId, machineId, 1, List.of(), List.of()));
        Plugin.completeServerReloadForTesting(reload, 0, () -> synced.set(true));

        assertThat(RecipeRegistry.dynamicSnapshot()).containsKey(recipeId);
        assertThat(synced).isTrue();
    }

    @Test
    void ordinary_server_reload_does_not_rebuild_controller_text() throws Exception {
        MachineControllerBlockEntity controller = RuntimeTestFixtures.controller(MMCR.id("test_machine_name"));
        MachineControllerRuntime runtime = runtimeOf(controller);
        runtime.screenText().append(ControllerScreenTextScope.CONTROLLER,
                MMCR.id("stale_reload_text"), Component.literal("stale"));

        Object reload = new Object();
        Plugin.beginServerReload(reload, 0);
        Plugin.completeServerReload(reload, 0);

        assertThat(runtime.screenText().snapshot().lines()).singleElement()
                .satisfies(line -> assertThat(line.text()).isEqualTo(Component.literal("stale")));
    }

    @Test
    void after_scripts_loaded_server_path_sends_runtime_sync_when_current_server_is_available() {
        var machineId = MMCR.id("test_machine_name");
        var recipeId = MMCR.id("kubejs_after_scripts_sync_recipe");
        var reload = new Object();
        AtomicBoolean synced = new AtomicBoolean();
        Plugin.setCurrentServerSyncForTesting(() -> {
            synced.set(true);
            return true;
        });

        Plugin.beginServerReload(reload, 0);
        KubeJSContentReloadTransaction.active().registerStructure(structure(machineId));
        KubeJSContentReloadTransaction.active().registerRecipe(RecipeTestSupport.create(recipeId, machineId, 1, List.of(), List.of()));
        Plugin.completeServerReload(reload, 0);

        assertThat(RecipeRegistry.dynamicSnapshot()).containsKey(recipeId);
        assertThat(synced).isTrue();
    }

    @Test
    void runtime_content_server_bridge_clears_current_server_on_matching_stop_event() {
        MinecraftServer server = (MinecraftServer) allocate(DedicatedServer.class);
        AtomicInteger sends = new AtomicInteger();
        RuntimeContentSync.setSenderForTesting(target -> sends.incrementAndGet());

        RuntimeContentServerBridge.onServerAboutToStart(new ServerAboutToStartEvent(server));
        RuntimeContentServerBridge.onServerStopped(new ServerStoppedEvent(server));

        assertThat(RuntimeContentServerBridge.sendToCurrentServer()).isFalse();
        assertThat(sends).hasValue(1);
    }

    @Test
    void failed_kubejs_server_reload_does_not_run_completion_sync() {
        var reload = new Object();
        AtomicBoolean synced = new AtomicBoolean();

        Plugin.beginServerReload(reload, 0);
        Plugin.completeServerReloadForTesting(reload, 1, () -> synced.set(true));

        assertThat(synced).isFalse();
    }

    @Test
    void server_reload_removes_previous_script_ids_even_if_registry_republished_instances() {
        var removedMachineId = MMCR.id("test_machine_name");
        var keptMachineId = MMCR.id("cracker");
        var removedRecipeId = MMCR.id("kubejs_transaction_republished_removed_recipe");
        var keptRecipeId = MMCR.id("kubejs_transaction_republished_kept_recipe");

        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(removedMachineId));
        previous.registerRecipe(RecipeTestSupport.create(removedRecipeId, removedMachineId, 1, List.of(), List.of()));
        previous.commit();
        MachineStructureRegistry.replaceDynamic(Map.of(removedMachineId, structure(removedMachineId)));
        RecipeRegistry.replaceDynamic(Map.of(removedRecipeId,
                RecipeTestSupport.create(removedRecipeId, removedMachineId, 1, List.of(), List.of())));

        var reload = new Object();
        Plugin.beginServerReload(reload, 0);
        KubeJSContentReloadTransaction.active().registerStructure(structure(keptMachineId));
        KubeJSContentReloadTransaction.active().registerRecipe(RecipeTestSupport.create(keptRecipeId, keptMachineId, 1, List.of(), List.of()));
        Plugin.completeServerReload(reload, 0);

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsOnlyKeys(keptMachineId);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsOnlyKeys(keptRecipeId);
    }

    @Test
    void server_reload_removes_republished_script_structure_with_modifier_requirements() {
        var removedMachineId = MMCR.id("test_machine_name");
        var keptMachineId = MMCR.id("cracker");

        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(modifierStructure(removedMachineId));
        previous.commit();
        MachineStructureRegistry.replaceDynamic(Map.of(removedMachineId, modifierStructure(removedMachineId)));

        var reload = new Object();
        Plugin.beginServerReload(reload, 0);
        KubeJSContentReloadTransaction.active().registerStructure(structure(keptMachineId));
        Plugin.completeServerReload(reload, 0);

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsOnlyKeys(keptMachineId);
    }

    @Test
    void successful_empty_server_reload_removes_previous_script_snapshot() {
        var machineId = MMCR.id("test_machine_name");
        var recipeId = MMCR.id("kubejs_transaction_empty_reload_removed_recipe");
        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(machineId));
        previous.registerRecipe(RecipeTestSupport.create(recipeId, machineId, 1, List.of(), List.of()));
        previous.commit();

        var reload = new Object();
        Plugin.beginServerReload(reload, 0);
        Plugin.completeServerReload(reload, 0);

        assertThat(MachineStructureRegistry.dynamicSnapshot()).doesNotContainKey(machineId);
        assertThat(RecipeRegistry.dynamicSnapshot()).isEmpty();
    }

    @Test
    void empty_server_reload_preserves_same_id_content_replaced_outside_script_snapshot() {
        var machineId = MMCR.id("test_machine_name");
        var recipeId = MMCR.id("kubejs_transaction_external_takeover_recipe");
        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(machineId));
        previous.registerRecipe(RecipeTestSupport.create(recipeId, machineId, 1, List.of(), List.of()));
        previous.commit();
        var takeoverStructure = new MachineStructureDefinition(machineId,
                new BlockArray(Map.of(BlockPos.ZERO, new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK))),
                PortRequirementSpec.none(), List.of(), MachineStructureRequirements.EMPTY);
        MachineStructureRegistry.replaceDynamic(Map.of(machineId, takeoverStructure));
        RecipeRegistry.replaceDynamic(Map.of(recipeId, RecipeTestSupport.create(recipeId, machineId, 2, List.of(), List.of())));

        var reload = new Object();
        Plugin.beginServerReload(reload, 0);
        Plugin.completeServerReload(reload, 0);

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsEntry(machineId, takeoverStructure);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsOnlyKeys(recipeId);
        assertThat(RecipeRegistry.getRecipe(recipeId).tickTime()).isEqualTo(2);
    }

    @Test
    void datapack_conflict_rejects_entire_kubejs_transaction_and_preserves_old_snapshot() {
        var machineId = MMCR.id("test_machine_name");
        var previousId = MMCR.id("kubejs_previous_after_datapack_conflict");
        var conflictId = MMCR.id("kubejs_datapack_conflict");
        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(machineId));
        previous.registerRecipe(RecipeTestSupport.create(previousId, machineId, 1, List.of(), List.of()));
        previous.commit();
        var previousRecipes = RecipeRegistry.dynamicSnapshot();
        RecipeRegistry.replaceDataPack(Map.of(conflictId,
                RecipeTestSupport.create(conflictId, machineId, 2, List.of(), List.of())));

        var invalid = new KubeJSContentReloadTransaction();
        invalid.registerRecipe(RecipeTestSupport.create(conflictId, machineId, 3, List.of(), List.of()));

        assertThatThrownBy(invalid::commit).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("data-pack");
        assertThat(RecipeRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousRecipes);
        assertThat(RecipeRegistry.getRecipe(conflictId).tickTime()).isEqualTo(2);
    }

    @Test
    void datapack_conflict_does_not_publish_transaction_structure_or_recipe() {
        var machineId = MMCR.id("kubejs_datapack_conflict_structure");
        var recipeId = MMCR.id("kubejs_datapack_conflict_structure_recipe");
        RecipeRegistry.replaceDataPack(Map.of(recipeId,
                RecipeTestSupport.create(recipeId, MMCR.id("test_machine_name"), 2, List.of(), List.of())));
        var transaction = new KubeJSContentReloadTransaction();
        transaction.registerStructure(structure(machineId));
        transaction.registerRecipe(RecipeTestSupport.create(recipeId, machineId, 3, List.of(), List.of()));

        assertThatThrownBy(transaction::commit).isInstanceOf(IllegalStateException.class);
        assertThat(MachineStructureRegistry.dynamicSnapshot()).doesNotContainKey(machineId);
        assertThat(RecipeRegistry.dynamicSnapshot()).doesNotContainKey(recipeId);
    }

    @Test
    void duplicate_recipe_id_is_rejected_without_changing_transaction_snapshot() {
        var id = MMCR.id("kubejs_duplicate_recipe");
        var transaction = new KubeJSContentReloadTransaction();
        transaction.registerStructure(structure(MMCR.id("test_machine_name")));
        transaction.registerRecipe(RecipeTestSupport.create(id, MMCR.id("test_machine_name"), 1, List.of(), List.of()));

        assertThatThrownBy(() -> transaction.registerRecipe(
                RecipeTestSupport.create(id, MMCR.id("test_machine_name"), 2, List.of(), List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(id.toString());
        transaction.commit();
        assertThat(RecipeRegistry.getRecipe(id).tickTime()).isEqualTo(1);
    }

    @Test
    void server_script_error_without_collected_content_preserves_previous_snapshot() {
        var machineId = MMCR.id("test_machine_name");
        var previousRecipeId = MMCR.id("kubejs_transaction_error_previous_recipe");
        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(machineId));
        previous.registerRecipe(RecipeTestSupport.create(previousRecipeId, machineId, 1, List.of(), List.of()));
        previous.commit();
        var previousStructures = MachineStructureRegistry.dynamicSnapshot();
        var previousRecipes = RecipeRegistry.dynamicSnapshot();

        var reload = new Object();
        Plugin.beginServerReload(reload, 0);
        Plugin.completeServerReload(reload, 1);

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousStructures);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousRecipes);
    }

    @Test
    void recipe_script_error_publishes_valid_collected_content() {
        var machineId = MMCR.id("test_machine_name");
        var validRecipeId = MMCR.id("kubejs_valid_recipe_after_script_error");
        var invalidRecipeId = MMCR.id("kubejs_invalid_recipe_after_script_error");
        var reload = new Object();

        Plugin.beginServerReload(reload, 0);
        KubeJSContentReloadTransaction.active().registerStructure(structure(machineId));
        KubeJSContentReloadTransaction.active().registerRecipe(RecipeTestSupport.create(
                validRecipeId, machineId, 1, List.of(), List.of()));
        KubeJSContentReloadTransaction.active().registerRecipe(RecipeTestSupport.create(
                invalidRecipeId, MMCR.id("missing_recipe_pool"), 1, List.of(), List.of()));
        Plugin.completeServerReload(reload, 1);

        assertThat(RecipeRegistry.dynamicSnapshot()).containsKey(validRecipeId).doesNotContainKey(invalidRecipeId);
    }

    @Test
    void invalid_only_recipe_reload_preserves_previous_snapshot() {
        var machineId = MMCR.id("test_machine_name");
        var externalMachineId = MMCR.id("cracker");
        var previousRecipeId = MMCR.id("kubejs_previous_recipe_before_invalid_reload");
        var invalidRecipeId = MMCR.id("kubejs_invalid_only_recipe");
        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(machineId));
        previous.registerRecipe(RecipeTestSupport.create(previousRecipeId, machineId, 1, List.of(), List.of()));
        previous.commit();
        MachineStructureRegistry.replaceDynamic(Map.of(machineId, structure(machineId),
                externalMachineId, structure(externalMachineId)));
        var previousStructures = MachineStructureRegistry.dynamicSnapshot();
        var previousRecipes = RecipeRegistry.dynamicSnapshot();
        long previousVersion = RuntimeContentVersion.current();

        var reload = new Object();
        Plugin.beginServerReload(reload, 0);
        KubeJSContentReloadTransaction.active().registerRecipe(RecipeTestSupport.create(
                invalidRecipeId, MMCR.id("missing_recipe_pool"), 1, List.of(), List.of()));
        Plugin.completeServerReload(reload, 1);

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousStructures);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousRecipes);
        assertThat(RuntimeContentVersion.current()).isEqualTo(previousVersion);
    }

    @Test
    void interrupted_server_reload_is_cleaned_before_after_hook_can_commit_it() {
        var machineId = MMCR.id("test_machine_name");
        var previousRecipeId = MMCR.id("kubejs_transaction_interrupted_previous_recipe");
        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(machineId));
        previous.registerRecipe(RecipeTestSupport.create(previousRecipeId, machineId, 1, List.of(), List.of()));
        previous.commit();
        var previousStructures = MachineStructureRegistry.dynamicSnapshot();
        var previousRecipes = RecipeRegistry.dynamicSnapshot();

        var reload = new Object();
        Plugin.beginServerReload(reload, 0);
        KubeJSContentReloadTransaction.active().registerStructure(structure(machineId));
        KubeJSContentReloadTransaction.active().registerRecipe(RecipeTestSupport.create(
                MMCR.id("kubejs_transaction_interrupted_recipe"), machineId, 1, List.of(), List.of()));

        Plugin.abortServerReload(reload);
        Plugin.completeServerReload(reload, 0);

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousStructures);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousRecipes);
        new MachineRecipeBuilderJS("mmcr:kubejs_transaction_direct_recipe").recipePool("mmcr:test_machine_name").build();
        assertThat(RecipeRegistry.containsStatic(MMCR.id("kubejs_transaction_direct_recipe"))).isTrue();
    }

    @Test
    void recipe_builder_collects_recipe_while_server_reload_transaction_is_active() {
        var recipeId = MMCR.id("kubejs_transaction_collected_recipe");
        var reload = new Object();

        Plugin.beginServerReload(reload, 0);
        new MachineRecipeBuilderJS(recipeId)
                .recipePool("mmcr:test_machine_name")
                .build();

        assertThat(RecipeRegistry.containsStatic(recipeId)).isFalse();
        Plugin.abortServerReload(reload);
    }

    @Test
    void empty_server_reload_preserves_existing_dynamic_content() {
        var machineId = MMCR.id("test_machine_name");
        var recipeId = MMCR.id("non_script_dynamic_recipe");
        MachineStructureRegistry.replaceDynamic(Map.of(machineId, structure(machineId)));
        RecipeRegistry.replaceDynamic(Map.of(recipeId, RecipeTestSupport.create(recipeId, machineId, 1, List.of(), List.of())));
        var previousStructures = MachineStructureRegistry.dynamicSnapshot();
        var previousRecipes = RecipeRegistry.dynamicSnapshot();

        var reload = new Object();
        Plugin.beginServerReload(reload, 0);
        Plugin.completeServerReload(reload, 0);

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousStructures);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousRecipes);
    }

    @Test
    void kubejs_plugin_discovery_file_points_to_mmcr_plugin() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream("kubejs.plugins.txt")) {
            assertThat(stream).as("kubejs.plugins.txt must exist for KubeJS 26 plugin discovery").isNotNull();
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content.lines().map(String::trim).filter(line -> !line.isBlank() && !line.startsWith("#")))
                    .contains("cn.howxu.mmcr.compat.kubejs.Plugin kubejs");
        }
    }

    @Test
    void old_meta_inf_kubejs_plugin_discovery_file_is_not_present() {
        assertThat(getClass().getClassLoader().getResource("META-INF/kubejs.plugins.txt"))
                .as("KubeJS 26 discovers plugins from the resource root, not META-INF")
                .isNull();
    }

    @Test
    void plugin_exposes_strict_mmcr_lifecycle_events() {
        assertThat(Plugin.events()).containsEntry("mmcr.startup", "mmcr.startup");
        assertThat(Plugin.events()).containsEntry("mmcr.server", "mmcr.server");
        assertThat(Plugin.events()).containsEntry("mmcr.client", "mmcr.client");
        assertThat(Plugin.events()).containsEntry("mmcr.smart_interface.updated", "mmcr.smart_interface.updated");
    }

    @Test
    void startup_event_exposes_startup_declaration_api_only() {
        var event = new MMCRStartupEventJS();

        assertThat(event.getAPI()).isInstanceOf(KubeJSApi.class);
        assertThat(event.createMachine("mmcr:test_machine")).isInstanceOf(MachineBuilderJS.class);
        assertThat(event.createLevelType("mmcr:test_type")).isInstanceOf(LevelTypeBuilderJS.class);
        assertThat(event.createLevel("mmcr:test_level")).isInstanceOf(MachineLevelBuilderJS.class);
        assertThat(event.getClass().getMethods()).extracting(Method::getName)
                .contains("registerControllerScreenText")
                .doesNotContain("createStructure", "createRecipe");
    }

    @Test
    void startup_event_registers_modifier_definition_and_item_binding() {
        var modifierId = MMCR.id("kubejs_startup_modifier");
        StructureRegistration.resetCollector();
        try {
            var event = new MMCRStartupEventJS();
            var definition = new ModifierDefinition(List.of());

            event.registerModifier(modifierId.toString(), definition);
            event.registerModifierItem(new ItemStack(Items.DIAMOND, 16), modifierId.toString());

            var snapshot = StructureRegistration.current().freeze();
            assertThat(snapshot.modifiers()).containsEntry(modifierId, definition);
            assertThat(snapshot.modifierItems().get(modifierId)).singleElement()
                    .satisfies(stack -> assertThat(stack.getCount()).isEqualTo(1));
        } finally {
            StructureRegistration.resetCollector();
        }
    }

    @Test
    void unknown_kubejs_modifier_rejects_entire_server_reload() {
        var oldMachineId = MMCR.id("test_machine_name");
        var validMachineId = MMCR.id("cracker");
        var unknownModifierId = MMCR.id("kubejs_unknown_modifier");

        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(oldMachineId));
        previous.commit();
        var previousStructures = MachineStructureRegistry.dynamicSnapshot();

        var reload = new Object();
        Plugin.beginServerReload(reload, 0);
        KubeJSContentReloadTransaction.active().registerStructure(structure(validMachineId));
        KubeJSContentReloadTransaction.active().registerStructure(new MachineStructureBuilderJS(oldMachineId)
                .pattern("M")
                .set("M", Blocks.IRON_BLOCK)
                .modifier("M", new KubeJSApi().modifierUse(unknownModifierId.toString(),
                        new KubeJSApi().block("minecraft:diamond_block")))
                .createObject());

        assertThatThrownBy(() -> Plugin.completeServerReload(reload, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(oldMachineId.toString())
                .hasMessageContaining(unknownModifierId.toString());
        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousStructures);
        assertThat(MachineStructureRegistry.dynamicSnapshot()).doesNotContainKey(validMachineId);
    }

    @Test
    void startup_scripts_run_inside_machine_registry_phase() {
        MachineDefinitions.clearForTesting();
        PublicApiBootstrap.clearForTesting();

        try {
            Plugin.beginStartupRegistryPhaseForTesting();
            new MMCRStartupEventJS().createMachine("mmcr:kubejs_lifecycle_press").register();
            Plugin.freezeStartupRegistryPhaseForTesting();

            assertThat(MachineDefinitions.getRegistration(MMCR.id("kubejs_lifecycle_press"))).isNotNull();
            assertThat(MachineDefinitions.isRegistryPhaseOpen()).isFalse();
        } finally {
            TestBootstrap.restoreMachineDefinitions();
        }
    }

    @Test
    void startup_machine_declared_before_scripts_loaded_is_committed_after_startup_scripts() {
        MachineDefinitions.clearForTesting();
        PublicApiBootstrap.clearForTesting();
        try {
            Plugin.beginStartupRegistryPhaseForTesting();
            new MMCRStartupEventJS().createMachine("mmcr:kubejs_startup_window_press").register();

            assertThat(MachineDefinitions.getRegistration(MMCR.id("kubejs_startup_window_press"))).isNull();
            StartupContentRegistration.completeKubeJSStartup();

            assertThat(MachineDefinitions.getRegistration(MMCR.id("kubejs_startup_window_press"))).isNotNull();
            assertThat(MachineDefinitions.isRegistryPhaseOpen()).isFalse();
        } finally {
            PublicApiBootstrap.clearForTesting();
            TestBootstrap.restoreMachineDefinitions();
            MachineDefinitions.freezeRegistryPhase();
        }
    }

    @Test
    void registered_startup_machine_retains_recipe_behavior_after_commit() {
        MachineDefinitions.clearForTesting();
        PublicApiBootstrap.clearForTesting();
        var machineId = MMCR.id("kubejs_registered_behavior");
        AtomicInteger ticks = new AtomicInteger();

        try {
            Plugin.beginStartupRegistryPhaseForTesting();
            new MMCRStartupEventJS().createMachine(machineId.toString())
                    .recipeBehavior(behavior -> behavior.recipeTick(context -> ticks.incrementAndGet()))
                    .register();
            StartupContentRegistration.completeKubeJSStartup();

            MachineRegistration registration = MachineDefinitions.getRegistration(machineId);
            assertThat(registration).isNotNull();
            assertThat(registration.behavior()).isInstanceOf(RecipeBehavior.class);
            ((RecipeBehavior) registration.behavior()).recipeTick().accept(new RecipeTickContext(
                    RecipeTestSupport.create(MMCR.id("kubejs_registered_behavior_recipe"), machineId, 1, List.of(), List.of()),
                    0, 1, 1));
            assertThat(ticks).hasValue(1);
        } finally {
            PublicApiBootstrap.clearForTesting();
            TestBootstrap.restoreMachineDefinitions();
            MachineDefinitions.freezeRegistryPhase();
        }
    }

    @Test
    void repeated_startup_completion_preserves_declared_machine() {
        PublicApiBootstrap.clearForTesting();
        MachineDefinitions.clearForTesting();

        try {
            Plugin.beginStartupRegistryPhaseForTesting();
            new MMCRStartupEventJS().createMachine("mmcr:repeated_lifecycle_press").register();
            Plugin.beginStartupRegistryPhaseForTesting();
            Plugin.freezeStartupRegistryPhaseForTesting();
            Plugin.freezeStartupRegistryPhaseForTesting();

            assertThat(MachineDefinitions.getRegistration(MMCR.id("repeated_lifecycle_press"))).isNotNull();
            assertThat(MachineDefinitions.isRegistryPhaseOpen()).isFalse();
        } finally {
            PublicApiBootstrap.clearForTesting();
            TestBootstrap.restoreMachineDefinitions();
            MachineDefinitions.freezeRegistryPhase();
        }
    }

    @Test
    void startup_hot_reload_does_not_reopen_frozen_machine_registrations() {
        MachineDefinitions.clearForTesting();
        MachineDefinitions.register(new MMCRStartupEventJS().createMachine("mmcr:frozen_lifecycle_press").createObject());
        MachineDefinitions.freezeRegistryPhase();

        Plugin.beginStartupRegistryPhaseForTesting();

        assertThat(MachineDefinitions.getRegistration(MMCR.id("frozen_lifecycle_press"))).isNotNull();
        assertThat(MachineDefinitions.isRegistryPhaseOpen()).isFalse();

        TestBootstrap.restoreMachineDefinitions();
        MachineDefinitions.freezeRegistryPhase();
    }

    @Test
    void kubejs_startup_after_mmcr_freezes_empty_machine_registry_without_reopening_it() {
        MachineDefinitions.clearForTesting();
        PublicApiBootstrap.clearForTesting();
        try {
            PublicApiBootstrap.begin();
            MachineDefinitions.freezeRegistryPhase();
            Plugin.freezeStartupRegistryPhaseForTesting();

            assertThat(MachineDefinitions.isRegistryPhaseOpen()).isFalse();

            Plugin.beginStartupRegistryPhaseForTesting();

            assertThat(MachineDefinitions.isRegistryPhaseOpen()).isFalse();
            assertThat(Machines.isRegistrationOpen()).isFalse();
        } finally {
            PublicApiBootstrap.clearForTesting();
            TestBootstrap.restoreMachineDefinitions();
            MachineDefinitions.freezeRegistryPhase();
        }
    }

    @Test
    void server_event_exposes_server_declaration_api_only() {
        var event = new MMCRServerEventJS();

        assertThat(event.getAPI()).isInstanceOf(KubeJSApi.class);
        assertThat(event.createStructure("mmcr:test_structure")).isInstanceOf(MachineStructureBuilderJS.class);
        assertThat(event.getClass().getMethods()).extracting(Method::getName)
                .doesNotContain("registerControllerScreenText", "createMachine", "createLevelType", "createLevel",
                        "levelSlot", "createRecipe");
    }

    @Test
    void mmcr_exposes_api_and_integer_values() {
        var mmcr = new MMCRKubeJS();
        assertThat(mmcr.getAPI()).isInstanceOf(KubeJSApi.class);
        assertThat(mmcr.getValues().INT_MAX).isEqualTo(Integer.MAX_VALUE);
        assertThat(mmcr.getValues().INT_MIN).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void smart_interface_update_event_exposes_interface_owned_shape() {
        SmartInterfaceUpdateEventJS event = new SmartInterfaceUpdateEventJS(
                new BlockPos(1, 2, 3), MMCR.id("test_machine"), "temperature", 20F, 30F,
                List.of(new BlockPos(0, 0, 0), new BlockPos(9, 0, 0)));

        assertThat(event.interfacePos()).isEqualTo(new BlockPos(1, 2, 3));
        assertThat(event.machineId()).isEqualTo(MMCR.id("test_machine"));
        assertThat(event.type()).isEqualTo("temperature");
        assertThat(event.controllerCount()).isEqualTo(2);
        assertThat(event.controllerPositions()).containsExactly(new BlockPos(0, 0, 0), new BlockPos(9, 0, 0));
        assertThat(event.controllerPos()).isEqualTo(new BlockPos(0, 0, 0));
    }

    @Test
    void public_recipe_builder_creates_a_component_output_in_recipe_event_context() {
        var builder = new MachineRecipeBuilderJS("mmcr:sharp_sword")
                .recipePool("mmcr:test_machine_name")
                .itemOutputWithComponents("minecraft:diamond_sword", 1, JsonParser.parseString("""
                        {
                          'minecraft:custom_name': 'Better钻石剑'
                        }
                        """));

        withRegistryAccess(builder::build);

        assertThat(RecipeRegistry.getRecipe(MMCR.id("sharp_sword")).machineOutputs()).singleElement()
                .isInstanceOfSatisfying(MachineOutput.ItemOutput.class, output -> {
                    assertThat(output.stack().getItem()).isSameAs(Items.DIAMOND_SWORD);
                    assertThat(output.stack().getCount()).isEqualTo(1);
                });
    }

    @Test
    void outputs_replaces_previously_declared_component_outputs() {
        var builder = new MachineRecipeBuilderJS("mmcr:replaced_component_output")
                .recipePool("mmcr:test_machine_name")
                .itemOutput("minecraft:iron_ingot", 1)
                .itemOutputWithComponents("minecraft:diamond_sword", 1, JsonParser.parseString("""
                        { 'minecraft:custom_name': 'Discarded' }
                        """))
                .outputs(List.of(new ItemStack(Items.DIAMOND)));

        MachineRecipe recipe = createInRecipeEvent(builder);

        assertThat(recipe.machineOutputs()).singleElement().isInstanceOfSatisfying(MachineOutput.ItemOutput.class,
                output -> assertThat(output.stack().getItem()).isSameAs(Items.DIAMOND));
    }

    @Test
    void component_output_rejects_negative_count_before_codec_decoding() {
        assertThatThrownBy(() -> new MachineRecipeBuilderJS("mmcr:negative_component_output")
                .recipePool("mmcr:test_machine_name")
                .itemOutputWithComponents("minecraft:diamond_sword", -1, JsonParser.parseString("{}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Component item output count must be positive: -1");
    }

    @Test
    void item_output_caps_long_count_at_the_native_stack_limit() {
        MachineRecipe recipe = createInRecipeEvent(new MachineRecipeBuilderJS("mmcr:long_item_output")
                .recipePool("mmcr:test_machine_name")
                .itemOutput("minecraft:iron_ingot", Long.MAX_VALUE));

        assertThat(recipe.machineOutputs()).singleElement().isInstanceOfSatisfying(MachineOutput.ItemOutput.class,
                output -> assertThat(output.stack().getCount()).isEqualTo(Integer.MAX_VALUE));
    }

    @Test
    void component_output_added_after_outputs_list_is_merged_at_the_new_position() {
        var builder = new MachineRecipeBuilderJS("mmcr:component_after_outputs")
                .recipePool("mmcr:test_machine_name")
                .outputs(List.of(new ItemStack(Items.DIAMOND)))
                .itemOutputWithComponents("minecraft:diamond_sword", 1, JsonParser.parseString("""
                        { 'minecraft:custom_name': 'Kept' }
                        """));

        MachineRecipe recipe = createInRecipeEvent(builder);

        assertThat(recipe.machineOutputs()).hasSize(2);
        assertThat(((MachineOutput.ItemOutput) recipe.machineOutputs().get(0)).stack().getItem()).isSameAs(Items.DIAMOND);
        assertThat(((MachineOutput.ItemOutput) recipe.machineOutputs().get(1)).stack().getItem()).isSameAs(Items.DIAMOND_SWORD);
    }

    @Test
    void public_recipe_builder_creates_chanced_item_output_requirement() {
        new MachineRecipeBuilderJS("mmcr:chanced_diamond")
                .recipePool("mmcr:test_machine_name")
                .chancedItemOutput("minecraft:diamond", 1, 0.5F)
                .build();

        assertThat(RecipeRegistry.getRecipe(MMCR.id("chanced_diamond")).requirements())
                .singleElement()
                .isInstanceOfSatisfying(ItemRequirement.class, output -> {
                    assertThat(output.io()).isEqualTo(RecipeModifier.IOType.OUTPUT);
                    assertThat(output.stack().getItem()).isSameAs(Items.DIAMOND);
                    assertThat(output.stack().getCount()).isEqualTo(1);
                    assertThat(output.chance()).isEqualTo(0.5F);
                });
    }

    private static Object allocate(Class<?> type) {
        try {
            var unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            unsafe.setAccessible(true);
            return ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(type);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static MachineRecipe createInRecipeEvent(MachineRecipeBuilderJS builder) {
        final MachineRecipe[] recipe = new MachineRecipe[1];
        withRegistryAccess(() -> recipe[0] = builder.createObject());
        return recipe[0];
    }

    private static void withRegistryAccess(Runnable action) {
        RegistryAccessContainer previous = RegistryAccessContainer.current;
        RegistryAccessContainer.current = RegistryAccessContainer.BUILTIN;
        try {
            action.run();
        } finally {
            RegistryAccessContainer.current = previous;
        }
    }

    private static MachineControllerRuntime runtimeOf(MachineControllerBlockEntity controller) throws Exception {
        var field = MachineControllerBlockEntity.class.getDeclaredField("runtime");
        field.setAccessible(true);
        return (MachineControllerRuntime) field.get(controller);
    }

    private static MachineStructureDefinition structure(ResourceLocation id) {
        return new MachineStructureDefinition(id, new BlockArray(Map.of()), PortRequirementSpec.none(), List.of(),
                MachineStructureRequirements.EMPTY);
    }

    private static MachineStructureDefinition modifierStructure(ResourceLocation id) {
        ModifierRegistry.installSnapshot(Map.of(MMCR.id("speed"), new ModifierDefinition(List.of())));
        BlockArray pattern = BlockArray.builder()
                .pattern("M")
                .set('M', new BlockPredicate.OfBlock(Blocks.IRON_BLOCK))
                .build();
        MachineStructureRequirements requirements = MachineStructureRequirements.builder()
                .modifier('M', new SingleBlockModifierReplacement(MMCR.id("speed").toString(), new BlockPredicate.OfBlock(Blocks.GOLD_BLOCK),
                        List.of(MachineModifier.numeric("output", "output", 2D, "multiply", false)),
                        new ItemStack(Blocks.GOLD_BLOCK)))
                .build(pattern);
        return new MachineStructureDefinition(id, pattern, PortRequirementSpec.none(), List.of(), requirements);
    }

}
