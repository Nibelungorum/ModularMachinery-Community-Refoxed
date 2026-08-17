package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRegistry;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.script.KubeJSContextFactory;
import dev.latvian.mods.kubejs.util.RegistryOpsContainer;
import dev.latvian.mods.rhino.NativeObject;
import net.minecraft.core.BlockPos;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.ScopedValue;
import java.util.List;
import java.util.Map;

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
        MachineStructureRegistry.clearForTesting();
        RecipeRegistry.clearForTesting();
    }

    @Test
    void kubejs_content_transaction_replaces_dynamic_content_only_after_validation() {
        var machineId = MMCR.id("alloy_furnace");
        var recipeId = MMCR.id("kubejs_transaction_success_recipe");

        var transaction = new KubeJSContentReloadTransaction();
        transaction.registerStructure(structure(machineId));
        transaction.registerRecipe(new MachineRecipe(recipeId, machineId, 1, List.of(), List.of()));
        transaction.commit();

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsKey(machineId);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsKey(recipeId);
    }

    @Test
    void invalid_kubejs_content_transaction_preserves_previous_dynamic_snapshot() {
        var machineId = MMCR.id("alloy_furnace");
        var recipeId = MMCR.id("kubejs_transaction_previous_recipe");

        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(machineId));
        previous.registerRecipe(new MachineRecipe(recipeId, machineId, 1, List.of(), List.of()));
        previous.commit();
        var previousStructures = MachineStructureRegistry.dynamicSnapshot();
        var previousRecipes = RecipeRegistry.dynamicSnapshot();

        var invalid = new KubeJSContentReloadTransaction();
        invalid.registerRecipe(new MachineRecipe(MMCR.id("invalid_kubejs_transaction_recipe"), MMCR.id("missing_machine"), 1, List.of(), List.of()));

        assertThatThrownBy(invalid::commit).isInstanceOf(IllegalStateException.class);
        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousStructures);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousRecipes);
    }

    @Test
    void server_script_error_discards_collected_content_and_preserves_previous_snapshot() {
        var machineId = MMCR.id("alloy_furnace");
        var previousRecipeId = MMCR.id("kubejs_transaction_error_previous_recipe");
        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(machineId));
        previous.registerRecipe(new MachineRecipe(previousRecipeId, machineId, 1, List.of(), List.of()));
        previous.commit();
        var previousStructures = MachineStructureRegistry.dynamicSnapshot();
        var previousRecipes = RecipeRegistry.dynamicSnapshot();

        var reload = new Object();
        Plugin.beginServerReload(reload, 0);
        KubeJSContentReloadTransaction.active().registerStructure(structure(machineId));
        KubeJSContentReloadTransaction.active().registerRecipe(new MachineRecipe(
                MMCR.id("kubejs_transaction_error_recipe"), machineId, 1, List.of(), List.of()));
        Plugin.completeServerReload(reload, 1);

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousStructures);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousRecipes);
    }

    @Test
    void interrupted_server_reload_is_cleaned_before_after_hook_can_commit_it() {
        var machineId = MMCR.id("alloy_furnace");
        var previousRecipeId = MMCR.id("kubejs_transaction_interrupted_previous_recipe");
        var previous = new KubeJSContentReloadTransaction();
        previous.registerStructure(structure(machineId));
        previous.registerRecipe(new MachineRecipe(previousRecipeId, machineId, 1, List.of(), List.of()));
        previous.commit();
        var previousStructures = MachineStructureRegistry.dynamicSnapshot();
        var previousRecipes = RecipeRegistry.dynamicSnapshot();

        var reload = new Object();
        Plugin.beginServerReload(reload, 0);
        KubeJSContentReloadTransaction.active().registerStructure(structure(machineId));
        KubeJSContentReloadTransaction.active().registerRecipe(new MachineRecipe(
                MMCR.id("kubejs_transaction_interrupted_recipe"), machineId, 1, List.of(), List.of()));

        Plugin.abortServerReload(reload);
        Plugin.completeServerReload(reload, 0);

        assertThat(MachineStructureRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousStructures);
        assertThat(RecipeRegistry.dynamicSnapshot()).containsExactlyInAnyOrderEntriesOf(previousRecipes);
        new MachineRecipeBuilderJS("mmcr:kubejs_transaction_direct_recipe").machine("mmcr:alloy_furnace").build();
        assertThat(RecipeRegistry.containsStatic(MMCR.id("kubejs_transaction_direct_recipe"))).isTrue();
    }

    @Test
    void recipe_builder_has_a_stable_public_kubejs_binding() {
        assertThat(Plugin.RECIPE_BUILDER_BINDING).isEqualTo("MMCR_RECIPE_BUILDER");
        assertThat(Plugin.RECIPE_BUILDER_CLASS).isEqualTo(MachineRecipeBuilderJS.class);
    }

    @Test
    void plugin_exposes_stable_public_declaration_bindings() {
        var factory = new KubeJSContextFactory(null);
        var context = new KubeJSContext(factory);
        var scope = new NativeObject(factory);

        new Plugin().registerBindings(new BindingRegistry(context, scope));

        assertThat(scope.getIds(context)).contains(
                "MMCR_API", "MMCR_MACHINE_DEFINITIONS", "MMCR_MACHINE_STRUCTURES", "MMCR_RECIPE_REGISTRY",
                "MMCR_BLOCK_ARRAY", "MMCR_BLOCK_PREDICATE", "MMCR_MACHINE_REGISTRATION",
                "MMCR_STRUCTURE_DEFINITION", "MMCR_PORT_REQUIREMENTS", "MMCR_PORT_TIER_REQUIREMENTS",
                "MMCR_MACHINE_INGREDIENT", "MMCR_MACHINE_RECIPE", "MMCR_RECIPE_MODIFIER",
                "MMCR_SINGLE_BLOCK_MODIFIER", "MMCR_LEVEL_REQUIREMENT", "MMCR_SMART_INTERFACE_REQUIREMENT");
    }

    @Test
    void plugin_exposes_smart_interface_update_event() {
        assertThat(Plugin.events()).containsKey("mmcr.smart_interface.updated");
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
        Items.DIAMOND_SWORD.builtInRegistryHolder().bindComponents(DataComponentMap.EMPTY);
        var builder = new MachineRecipeBuilderJS("mmcr:sharp_sword")
                .machine("mmcr:alloy_furnace")
                .itemOutputWithComponents("minecraft:diamond_sword", 1, JsonParser.parseString("""
                        {
                          'minecraft:custom_name': { text: 'Better钻石剑' },
                          'minecraft:enchantments': { 'minecraft:sharpness': 4 }
                        }
                        """));

        var event = (RecipesKubeEvent) allocate(RecipesKubeEvent.class);
        var ops = RegistryOps.create(JsonOps.INSTANCE, VanillaRegistries.createLookup());
        setField(event, "ops", new RegistryOpsContainer(null, ops, null));
        ScopedValue.where(RecipesKubeEvent.INSTANCE, event).run(builder::build);

        assertThat(RecipeRegistry.getRecipe(MMCR.id("sharp_sword")).outputs()).singleElement().satisfies(output -> {
            assertThat(output.getItem()).isSameAs(Items.DIAMOND_SWORD);
            assertThat(output.getCount()).isEqualTo(1);
        });
    }

    @Test
    void public_recipe_builder_creates_chanced_item_output_requirement() {
        new MachineRecipeBuilderJS("mmcr:chanced_diamond")
                .machine("mmcr:alloy_furnace")
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

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static MachineStructureDefinition structure(net.minecraft.resources.Identifier id) {
        return new MachineStructureDefinition(id, new BlockArray(Map.of()), PortRequirementSpec.none(), List.of(), Map.of());
    }
}
