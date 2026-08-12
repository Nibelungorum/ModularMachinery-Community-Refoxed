package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.test.TestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import dev.latvian.mods.kubejs.util.RegistryOpsContainer;
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

import static org.assertj.core.api.Assertions.assertThat;

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
        RecipeRegistry.clearForTesting();
    }

    @Test
    void recipe_builder_has_a_stable_public_kubejs_binding() {
        assertThat(Plugin.RECIPE_BUILDER_BINDING).isEqualTo("MMCR_RECIPE_BUILDER");
        assertThat(Plugin.RECIPE_BUILDER_CLASS).isEqualTo(MachineRecipeBuilderJS.class);
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
            assertThat(output.getHoverName().getString()).isEqualTo("Better钻石剑");
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
}
