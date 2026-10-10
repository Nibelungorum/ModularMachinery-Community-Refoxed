package cn.howxu.mmcr.compat.patchouli;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.registry.ModItems;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * Verifies guide acquisition against the actual optional runtime environment.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class PatchouliGuideGameTest {
    private PatchouliGuideGameTest() {
    }

    public static void acquisitionMatchesOptionalInstallation(GameTestHelper helper) {
        boolean installed = ModList.get().isLoaded("patchouli");
        var key = ResourceKey.<Recipe<?>>create(Registries.RECIPE, MMCR.id("modular_guide"));
        var holder = helper.getLevel().recipeAccess().byKey(key);
        helper.assertTrue(holder.isPresent() == installed,
                "Guide recipe availability must match Patchouli installation");
        if (!installed) {
            helper.succeed();
            return;
        }

        helper.assertTrue(holder.orElseThrow().value() instanceof ShapelessRecipe,
                "Guide acquisition uses a vanilla shapeless recipe");
        ShapelessRecipe recipe = (ShapelessRecipe) holder.orElseThrow().value();
        ItemStack book = new ItemStack(Items.BOOK);
        ItemStack alloy = new ItemStack(ModItems.MODULARIUM.get());
        CraftingInput input = CraftingInput.of(2, 1, List.of(book, alloy));
        CraftingInput reversed = CraftingInput.of(2, 1, List.of(alloy, book));
        CraftingInput wrong = CraftingInput.of(2, 1,
                List.of(new ItemStack(Items.BOOK), new ItemStack(Items.IRON_INGOT)));
        helper.assertTrue(recipe.matches(input, helper.getLevel())
                        && recipe.matches(reversed, helper.getLevel()),
                "Book and modularium craft the guide in either order");
        helper.assertTrue(!recipe.matches(wrong, helper.getLevel()),
                "An unrelated ingot must not craft the guide");

        ItemStack result = recipe.assemble(input);
        helper.assertTrue(BuiltInRegistries.ITEM.getKey(result.getItem())
                        .equals(Identifier.parse("patchouli:guide_book")),
                "Crafting must return the functional Patchouli guide item");
        JsonObject encoded = ItemStack.CODEC.encodeStart(
                helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),
                result).getOrThrow().getAsJsonObject();
        JsonObject components = encoded.getAsJsonObject("components");
        helper.assertTrue(components != null && components.has("patchouli:book"),
                "Crafted guide must retain its Patchouli book component");
        helper.assertTrue(components.get("patchouli:book")
                        .getAsString().equals("mmcr:modular_guide"),
                "Crafted guide must open this book, not an undefined or different book");
        helper.succeed();
    }
}
