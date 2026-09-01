package cn.howxu.mmcr;

import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;
import java.util.Set;

/**
 * @author howxu <dev@howxu.cn>
 */
public class TagComponentIngredientGameTest {

    public void tagIngredientMatchesComponentPredicate(GameTestHelper helper) {
        var registryAccess = helper.getLevel().registryAccess();
        var items = registryAccess.lookupOrThrow(Registries.ITEM);
        var ingredient = new MachineIngredient.ItemIngredient(Ingredient.of(items.getOrThrow(ItemTags.LOGS)), 2,
                DataComponentPredicateSet.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                        { 'minecraft:custom_name': { text: 'Validated' } }
                        """)).getOrThrow(), 1F);
        var recipe = MachineRecipe.fromCanonical(Identifier.parse("mmcr:tag_component_input"),
                MMCR.id("iron_compressor"), 20, List.of(MachineRequirement.fromInput(ingredient)), List.of(),
                List.of(), 0, 1, false, false, List.of(), false, Set.of());
        var ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess);
        var decoded = MachineRecipe.CODEC.codec().parse(ops,
                MachineRecipe.CODEC.codec().encodeStart(ops, recipe).getOrThrow()).getOrThrow();
        var input = (ItemRequirement) decoded.requirements().getFirst();
        var matchingLog = new ItemStack(Items.OAK_LOG);
        matchingLog.set(DataComponents.CUSTOM_NAME, Component.literal("Validated"));
        var unnamedLog = new ItemStack(Items.OAK_LOG);
        var namedIngot = new ItemStack(Items.IRON_INGOT);
        namedIngot.set(DataComponents.CUSTOM_NAME, Component.literal("Validated"));

        helper.assertTrue(input.item().test(matchingLog), "Named log matches the tag ingredient");
        helper.assertTrue(input.components().matches(matchingLog), "Named log matches the component predicate");
        helper.assertTrue(input.item().test(unnamedLog), "Unnamed log matches the tag ingredient");
        helper.assertTrue(!input.components().matches(unnamedLog), "Unnamed log fails the component predicate");
        helper.assertTrue(!input.item().test(namedIngot), "Named ingot fails the tag ingredient");
        helper.succeed();
    }
}
