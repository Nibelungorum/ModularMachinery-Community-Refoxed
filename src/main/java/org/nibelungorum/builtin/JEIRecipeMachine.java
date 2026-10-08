package org.nibelungorum.builtin;

import cn.howxu.mmcr.publicapi.Machines;
import cn.howxu.mmcr.publicapi.Recipes;
import cn.howxu.mmcr.publicapi.Structures;
import cn.howxu.mmcr.publicapi.event.RegisterMachineDefinitionsEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineRecipesEvent;
import cn.howxu.mmcr.publicapi.event.RegisterMachineStructuresEvent;
import cn.howxu.mmcr.publicapi.recipe.RecipeDraft;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;

import static cn.howxu.mmcr.publicapi.ApiIds.id;

/** Built-in mixed-resource recipe matrix for inspecting JEI layouts in game.
 * @author howxu <dev@howxu.cn>
 */
@EventBusSubscriber
public final class JEIRecipeMachine {
    private static final ResourceLocation ID = id("jei_recipe_machine");

    private JEIRecipeMachine() {
    }

    public static void registerDefinitions(RegisterMachineDefinitionsEvent event) {
        if (!event.definitions().containsKey(ID)) {
            event.registerMachine(Machines.machine(ID)
                    .recipePool(ID)
                    .displayNameKey("machine.mmcr.jei_recipe_machine")
                    .controller(controller -> controller.id(ID.withSuffix("_controller")))
                    .build());
        }
    }

    @SubscribeEvent
    public static void registerStructures(RegisterMachineStructuresEvent event) {
        if (!event.structures().containsKey(ID)) {
            event.registerStructure(Structures.structure()
                    .fullStructure(stage -> stage.pattern(pattern -> pattern.layer("C").controller('C')))
                    .build(ID));
        }
    }

    @SubscribeEvent
    public static void registerRecipes(RegisterMachineRecipesEvent event) {
        if (!ModList.get().isLoaded("mekanism")) return;
        Item[] items = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.DIAMOND,
                Items.EMERALD, Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL, Items.QUARTZ,
                Items.AMETHYST_SHARD};
        Fluid[] fluids = {Fluids.WATER, Fluids.LAVA,
                BuiltInRegistries.FLUID.get(ResourceLocation.parse("mekanism:heavy_water"))};
        List<ResourceLocation> chemicals = List.of("hydrogen", "oxygen", "chlorine", "sulfur_dioxide",
                "sulfur_trioxide", "sulfuric_acid", "hydrogen_chloride").stream()
                .map(name -> ResourceLocation.fromNamespaceAndPath("mekanism", name)).toList();
        for (int inputs = 1; inputs <= 20; inputs++) {
            for (int outputs = 1; outputs <= 20; outputs++) {
                ResourceLocation recipeId = id(String.format(Locale.ROOT, "jei_recipe_machine/%02d_%02d", inputs, outputs));
                RecipeDraft recipe = Recipes.recipe(recipeId).recipePool(ID).duration(100);
                addPrefix(recipe, inputs, true, items, fluids, chemicals);
                addPrefix(recipe, outputs, false, items, fluids, chemicals);
                event.registerRecipe(recipe.build());
            }
        }
    }

    private static void addPrefix(RecipeDraft recipe, int count, boolean input,
                                  Item[] items, Fluid[] fluids, List<ResourceLocation> chemicals) {
        for (int index = 0; index < count; index++) {
            if (index < items.length) {
                if (input) recipe.inputItem(items[index], 1);
                else recipe.outputItem(items[index], 1);
            } else if (index < items.length + fluids.length) {
                Fluid fluid = fluids[index - items.length];
                if (input) recipe.inputFluid(fluid, 1_000);
                else recipe.outputFluid(fluid, 1_000);
            } else {
                ResourceLocation chemical = chemicals.get(index - items.length - fluids.length);
                if (input) recipe.inputChemical(chemical, 1_000);
                else recipe.outputChemical(chemical, 1_000, 1F);
            }
        }
    }
}
