package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.List;

public class MachineRecipeBuilderJS {
    public Identifier machineId;
    public int tickTime = 40;
    public final List<MachineIngredient> inputs = new ArrayList<>();
    public final List<ItemStack> outputs = new ArrayList<>();
    public int energyPerTick = 0;
    public boolean cancelIfPerTickFails = false;

    private Identifier id;

    public MachineRecipeBuilderJS(String id) {
        this.id = Identifier.parse(id);
    }

    public MachineRecipeBuilderJS(Identifier id) {
        this.id = id;
    }

    public MachineRecipeBuilderJS id(String id) {
        this.id = Identifier.parse(id);
        return this;
    }

    public MachineRecipeBuilderJS machine(String id) {
        var parsed = Identifier.parse(id);

        if (MachineRegistry.getMachine(parsed) == null && MachineDefinitions.getRegistration(parsed) == null) {
            throw new IllegalArgumentException("Machine not found: " + id);
        }

        this.machineId = parsed;

        return this;
    }

    public MachineRecipeBuilderJS tickTime(int tickTime) {
        this.tickTime = tickTime;
        return this;
    }

    public MachineRecipeBuilderJS itemInput(String itemId, int count) {
        return addItemInput(Ingredient.of(item(itemId)), count, DataComponentPredicateSet.EMPTY, 1F);
    }

    public MachineRecipeBuilderJS tagInput(String tagId, int count) {
        return addItemInput(Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(TagKey.create(Registries.ITEM, Identifier.parse(tagId)))), count,
                DataComponentPredicateSet.EMPTY, 1F);
    }

    public MachineRecipeBuilderJS itemInputWithComponents(String itemId, int count, JsonElement components) {
        return addItemInput(Ingredient.of(item(itemId)), count,
                DataComponentPredicateSet.CODEC.parse(JsonOps.INSTANCE, components).getOrThrow(), 1F);
    }

    public MachineRecipeBuilderJS notConsumableItemInput(String itemId, int count) {
        return addItemInput(Ingredient.of(item(itemId)), count, DataComponentPredicateSet.EMPTY, 0F);
    }

    public MachineRecipeBuilderJS chancedItemInput(String itemId, int count, float consumeChance) {
        return addItemInput(Ingredient.of(item(itemId)), count, DataComponentPredicateSet.EMPTY, consumeChance);
    }

    public MachineRecipeBuilderJS itemOutput(String itemId, int count) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
        outputs.add(new ItemStack(item, count));
        return this;
    }

    public MachineRecipeBuilderJS energyPerTick(int energyPerTick) {
        this.energyPerTick = energyPerTick;
        return this;
    }

    public MachineRecipeBuilderJS cancelIfPerTickFails(boolean cancelIfPerTickFails) {
        this.cancelIfPerTickFails = cancelIfPerTickFails;
        return this;
    }

    private MachineRecipeBuilderJS addItemInput(Ingredient item, int count, DataComponentPredicateSet components, float consumeChance) {
        inputs.add(new MachineIngredient.ItemIngredient(item, count, components, consumeChance));
        return this;
    }

    private net.minecraft.world.item.Item item(String itemId) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
    }

    public void build() {
        if (machineId == null) {
            throw new IllegalStateException("machine() not called");
        }

        var recipeInputs = new ArrayList<>(inputs);

        if (energyPerTick > 0) {
            recipeInputs.add(new MachineIngredient.EnergyIngredient(energyPerTick));
        }

        RecipeRegistry.register(new MachineRecipe(id, machineId, tickTime, List.copyOf(recipeInputs), List.copyOf(outputs), List.of(), 0, 1, cancelIfPerTickFails));
    }
}
