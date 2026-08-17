package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.MachineDefinitions;
import cn.howxu.mmcr.api.machine.MachineRegistry;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.MachineRecipe;
import cn.howxu.mmcr.api.recipe.RecipeRegistry;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MachineRecipeBuilderJS {
    public Identifier machineId;
    public int tickTime = 40;
    public final List<MachineIngredient> inputs = new ArrayList<>();
    public final List<ItemStack> outputs = new ArrayList<>();
    private final List<Float> outputChances = new ArrayList<>();
    private final List<FluidStack> fluidOutputs = new ArrayList<>();
    private final List<RecipeModifier> conditions = new ArrayList<>();
    private int priority = 0;
    private int maxThreads = 1;
    public int energyPerTick = 0;
    public boolean cancelIfPerTickFails = false;
    public final List<LevelRequirement> levelRequirements = new ArrayList<>();
    public final Set<Identifier> requiredHostIds = new LinkedHashSet<>();
    final List<MachineRequirement> requirements = new ArrayList<>();
    private boolean allowPartialOutputs = false;

    private Identifier id;
    private final List<ComponentOutput> componentOutputs = new ArrayList<>();

    public MachineRecipeBuilderJS(String id) {
        this(Identifier.parse(id));
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

    public MachineRecipeBuilderJS inputs(List<MachineIngredient> inputs) {
        this.inputs.clear();
        this.inputs.addAll(inputs);
        return this;
    }

    public MachineRecipeBuilderJS addInput(MachineIngredient input) {
        inputs.add(input);
        return this;
    }

    public MachineRecipeBuilderJS outputs(List<ItemStack> outputs) {
        this.outputs.clear();
        outputChances.clear();
        for (ItemStack output : outputs) addOutput(output, 1F);
        return this;
    }

    public MachineRecipeBuilderJS addOutput(ItemStack output, float chance) {
        outputs.add(output);
        outputChances.add(chance);
        return this;
    }

    public MachineRecipeBuilderJS fluidOutputs(List<FluidStack> fluidOutputs) {
        this.fluidOutputs.clear();
        this.fluidOutputs.addAll(fluidOutputs);
        return this;
    }

    public MachineRecipeBuilderJS requirements(List<MachineRequirement> requirements) {
        this.requirements.clear();
        this.requirements.addAll(requirements);
        return this;
    }

    public MachineRecipeBuilderJS addRequirement(MachineRequirement requirement) {
        requirements.add(requirement);
        return this;
    }

    public MachineRecipeBuilderJS priority(int priority) {
        this.priority = priority;
        return this;
    }

    public MachineRecipeBuilderJS maxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        return this;
    }

    public MachineRecipeBuilderJS conditions(List<RecipeModifier> conditions) {
        this.conditions.clear();
        this.conditions.addAll(conditions);
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
        outputs.add(new ItemStack(item(itemId), count));
        outputChances.add(1F);
        return this;
    }

    public MachineRecipeBuilderJS chancedItemOutput(String itemId, int count, float chance) {
        outputs.add(new ItemStack(item(itemId), count));
        outputChances.add(chance);
        return this;
    }

    public MachineRecipeBuilderJS itemOutputWithComponents(String itemId, int count, JsonElement components) {
        JsonObject stack = new JsonObject();
        stack.addProperty("id", itemId);
        stack.addProperty("count", count);
        stack.add("components", components.deepCopy());
        componentOutputs.add(new ComponentOutput(outputs.size(), stack));
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

    public MachineRecipeBuilderJS allowPartialOutputs() {
        this.allowPartialOutputs = true;
        return this;
    }

    public MachineRecipeBuilderJS allowPartialOutputs(boolean allowPartialOutputs) {
        this.allowPartialOutputs = allowPartialOutputs;
        return this;
    }

    public MachineRecipeBuilderJS smartInterfaceInput(String type, float value) {
        requirements.add(SmartInterfaceRequirement.input(type, value));
        return this;
    }

    public MachineRecipeBuilderJS smartInterfaceInput(String type, float minValue, float maxValue) {
        requirements.add(SmartInterfaceRequirement.input(type, minValue, maxValue));
        return this;
    }

    public MachineRecipeBuilderJS smartInterfaceOutput(String type, float value) {
        requirements.add(SmartInterfaceRequirement.output(type, value));
        return this;
    }

    public MachineRecipeBuilderJS requiresLevel(String typeId, String levelId) {
        var type = Identifier.parse(typeId);
        var level = MachineLevelRegistry.getLevel(Identifier.parse(levelId));
        if (level == null) {
            throw new IllegalArgumentException("Machine level not found: " + levelId);
        }
        if (!level.typeId().equals(type)) {
            throw new IllegalArgumentException("Machine level " + levelId + " does not belong to type " + typeId);
        }
        levelRequirements.add(new LevelRequirement(type, level.id()));
        return this;
    }

    public MachineRecipeBuilderJS requiredHost(String hostId) {
        requiredHostIds.add(Identifier.parse(hostId));
        return this;
    }

    public MachineRecipeBuilderJS requiredHosts(String... hostIds) {
        if (hostIds == null) return this;
        for (String hostId : hostIds) {
            if (hostId != null) requiredHost(hostId);
        }
        return this;
    }

    private MachineRecipeBuilderJS addItemInput(Ingredient item, int count, DataComponentPredicateSet components, float consumeChance) {
        inputs.add(new MachineIngredient.ItemIngredient(item, count, components, consumeChance));
        return this;
    }

    private net.minecraft.world.item.Item item(String itemId) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
    }

    public MachineRecipe createObject() {
        if (machineId == null) {
            throw new IllegalStateException("machine() not called");
        }
        if (tickTime < 0 || energyPerTick < 0 || maxThreads < 0) {
            throw new IllegalArgumentException("Recipe counts must not be negative");
        }
        for (MachineIngredient input : inputs) {
            if ((input instanceof MachineIngredient.ItemIngredient item && item.count() < 0)
                    || (input instanceof MachineIngredient.FluidIngredient fluid && fluid.amount() < 0)
                    || (input instanceof MachineIngredient.EnergyIngredient energy && energy.fePerTick() < 0)) {
                throw new IllegalArgumentException("Recipe counts must not be negative");
            }
        }

        var recipeInputs = new ArrayList<>(inputs);

        if (energyPerTick > 0) {
            recipeInputs.add(new MachineIngredient.EnergyIngredient(energyPerTick));
        }

        var recipeOutputs = new ArrayList<>(outputs);
        var recipeOutputChances = new ArrayList<>(outputChances);

        if (!componentOutputs.isEmpty()) {
            if (!RecipesKubeEvent.INSTANCE.isBound()) {
                throw new IllegalStateException("Component item outputs must be built during the KubeJS recipe event");
            }

            var ops = RecipesKubeEvent.INSTANCE.get().ops.json();
            for (int index = 0; index < componentOutputs.size(); index++) {
                var output = componentOutputs.get(index);
                recipeOutputs.add(output.index() + index, ItemStack.CODEC.parse(ops, output.stack()).getOrThrow());
                recipeOutputChances.add(output.index() + index, 1F);
            }
        }

        var recipeRequirements = new ArrayList<MachineRequirement>();
        for (MachineIngredient input : recipeInputs) recipeRequirements.add(MachineRequirement.fromInput(input));
        for (int index = 0; index < recipeOutputs.size(); index++) {
            recipeRequirements.add(MachineRequirement.itemOutput(recipeOutputs.get(index), recipeOutputChances.get(index)));
        }
        for (FluidStack fluidOutput : fluidOutputs) recipeRequirements.add(MachineRequirement.fluidOutput(fluidOutput));
        recipeRequirements.addAll(requirements);

        return new MachineRecipe(id, machineId, tickTime, List.copyOf(recipeInputs), List.copyOf(recipeOutputs), List.copyOf(conditions), priority, maxThreads,
                cancelIfPerTickFails, List.copyOf(fluidOutputs), List.copyOf(recipeRequirements), false, List.copyOf(levelRequirements), allowPartialOutputs, new LinkedHashSet<>(requiredHostIds));
    }

    public void build() {
        var recipe = createObject();
        var transaction = KubeJSContentReloadTransaction.active();
        if (transaction != null) {
            transaction.registerRecipe(recipe);
        } else {
            RecipeRegistry.register(recipe);
        }
    }

    private record ComponentOutput(int index, JsonObject stack) {
    }
}
