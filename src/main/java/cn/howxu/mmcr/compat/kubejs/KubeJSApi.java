package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.publicapi.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.publicapi.machine.OutputPolicy;
import cn.howxu.mmcr.api.publicapi.recipe.RecipeIo;

import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.publicapi.ReadableNumber;
import cn.howxu.mmcr.util.IOType;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Public declaration helpers exposed to KubeJS through {@code MMCR.getAPI()} and MMCR event objects.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class KubeJSApi {
    private final ScreenScopeValues screenScope = new ScreenScopeValues();
    private final RecipeIoValues recipeIO = new RecipeIoValues();
    private final OutputPolicyValues outputPolicy = new OutputPolicyValues();

    public ScreenScopeValues screenScope() {
        return screenScope;
    }

    public RecipeIoValues recipeIO() {
        return recipeIO;
    }

    public OutputPolicyValues outputPolicy() {
        return outputPolicy;
    }

    /**
     * KubeJS-visible controller screen text scope constants.
     *
     * @author howxu <dev@howxu.cn>
     */
    public static final class ScreenScopeValues {
        public final ControllerScreenTextScope CONTROLLER = ControllerScreenTextScope.CONTROLLER;
        public final ControllerScreenTextScope OPERATION = ControllerScreenTextScope.OPERATION;
    }

    /** KubeJS-visible recipe input/output direction constants.
     * @author howxu <dev@howxu.cn>
     */
    public static final class RecipeIoValues {
        public final RecipeIo INPUT = RecipeIo.INPUT;
        public final RecipeIo OUTPUT = RecipeIo.OUTPUT;
    }

    /** KubeJS-visible machine output policy constants.
     * @author howxu <dev@howxu.cn>
     */
    public static final class OutputPolicyValues {
        public final OutputPolicy REQUIRE_FULL = OutputPolicy.REQUIRE_FULL;
        public final OutputPolicy ALLOW_PARTIAL = OutputPolicy.ALLOW_PARTIAL;
    }

    public String readableNumber(long value) {
        return ReadableNumber.formatCompact(value);
    }

    public String readableNumberExact(long value) {
        return ReadableNumber.formatExact(value);
    }

    public Identifier id(String id) {
        return Identifier.parse(id);
    }

    public BlockPredicate air() { return new BlockPredicate.Air(); }
    public BlockPredicate any() { return new BlockPredicate.Any(); }
    public BlockPredicate coupler() { return BlockPredicate.machineCoupler(); }
    public BlockPredicate anyOfItemInput() { return KubeJSInterfaceHelpers.anyOfItemInput(); }
    public BlockPredicate anyOfItemOutput() { return KubeJSInterfaceHelpers.anyOfItemOutput(); }
    public BlockPredicate anyOfFluidInput() { return KubeJSInterfaceHelpers.anyOfFluidInput(); }
    public BlockPredicate anyOfFluidOutput() { return KubeJSInterfaceHelpers.anyOfFluidOutput(); }
    public BlockPredicate anyOfEnergyInput() { return KubeJSInterfaceHelpers.anyOfEnergyInput(); }
    public BlockPredicate anyOfEnergyOutput() { return KubeJSInterfaceHelpers.anyOfEnergyOutput(); }
    public BlockPredicate parallelControllers() { return KubeJSInterfaceHelpers.parallelControllers(); }
    public BlockPredicate smartInterface() { return KubeJSInterfaceHelpers.smartInterface(); }
    public BlockPredicate dataStorage() { return KubeJSInterfaceHelpers.dataStorage(); }
    public BlockPredicate factoryController() { return KubeJSInterfaceHelpers.factoryController(); }

    public BlockPredicate block(String blockId) {
        return new BlockPredicate.OfBlock(requireBlock(blockId));
    }

    public BlockPredicate state(String blockStateId) {
        int propertiesStart = blockStateId.indexOf('[');
        if (propertiesStart < 0) return new BlockPredicate.OfBlockState(requireBlock(blockStateId).defaultBlockState());
        if (!blockStateId.endsWith("]")) throw new IllegalArgumentException("Invalid block state: " + blockStateId);
        BlockState state = requireBlock(blockStateId.substring(0, propertiesStart)).defaultBlockState();
        String properties = blockStateId.substring(propertiesStart + 1, blockStateId.length() - 1);
        if (properties.isEmpty()) throw new IllegalArgumentException("Invalid block state: " + blockStateId);
        for (String assignment : properties.split(",", -1)) {
            String[] pair = assignment.split("=", -1);
            if (pair.length != 2 || pair[0].isEmpty() || pair[1].isEmpty()) {
                throw new IllegalArgumentException("Invalid block state property: " + assignment);
            }
            Property<?> property = state.getBlock().getStateDefinition().getProperty(pair[0]);
            if (property == null) throw new IllegalArgumentException("Unknown block state property: " + pair[0]);
            state = setProperty(state, property, pair[1]);
        }
        return new BlockPredicate.OfBlockState(state);
    }

    public BlockPredicate tag(String tagId) {
        var tag = TagKey.create(Registries.BLOCK, Identifier.parse(tagId));
        return new BlockPredicate.OfTag(tag);
    }

    public BlockPredicate anyOf(BlockPredicate... children) {
        if (children == null || children.length == 0) throw new IllegalArgumentException("anyOf requires children");
        return new BlockPredicate.AnyOf(List.of(children));
    }

    public PortRequirementSpec portRequirements(Map<String, Object> ranges) {
        var builder = PortRequirementSpec.builder();
        for (var entry : ranges.entrySet()) {
            Object range = entry.getValue();
            if (range instanceof Number min) builder.min(entry.getKey(), wholeNumber(min));
            else if (range instanceof List<?> values && values.size() == 2
                    && values.getFirst() instanceof Number min && values.get(1) instanceof Number max) {
                builder.range(entry.getKey(), wholeNumber(min), wholeNumber(max));
            } else throw new IllegalArgumentException("Invalid port range for " + entry.getKey());
        }
        return builder.build();
    }

    public PortTierRequirementSpec portTierRequirements(List<String> minimums) {
        List<PortTierRequirementSpec.Requirement> requirements = new ArrayList<>();
        for (String minimum : minimums) requirements.add(parseTierRequirement(minimum));
        return requirements.isEmpty() ? PortTierRequirementSpec.none() : new PortTierRequirementSpec(requirements);
    }

    public MachineIngredient itemInput(String itemId, int count, float consumeChance) {
        return new MachineIngredient.ItemIngredient(Ingredient.of(requireItem(itemId)), count, null, consumeChance);
    }

    public MachineIngredient tagInput(String tagId, int count, float consumeChance) {
        var tag = TagKey.create(Registries.ITEM, Identifier.parse(tagId));
        var items = RegistryAccessContainer.current.lookup(Registries.ITEM)
                .orElseThrow(() -> new IllegalStateException("Item registry unavailable"));
        return new MachineIngredient.ItemIngredient(Ingredient.of(items.getOrThrow(tag)), count, null, consumeChance);
    }

    public MachineIngredient fluidInput(String fluidId, int amount) {
        Identifier identifier = Identifier.parse(fluidId);
        if (!BuiltInRegistries.FLUID.containsKey(identifier)) throw new IllegalArgumentException("Unknown fluid: " + fluidId);
        return new MachineIngredient.FluidIngredient(FluidIngredient.of(BuiltInRegistries.FLUID.getValue(identifier)), amount);
    }

    public FluidStack fluidStack(String fluidId, int amount) {
        Identifier identifier = Identifier.parse(fluidId);
        if (!BuiltInRegistries.FLUID.containsKey(identifier)) throw new IllegalArgumentException("Unknown fluid: " + fluidId);
        return new FluidStack(BuiltInRegistries.FLUID.getValue(identifier), amount);
    }

    public MachineIngredient energyInput(int fePerTick) { return new MachineIngredient.EnergyIngredient(fePerTick); }
    public MachineIngredient energyOutput(int fePerTick) {
        return new MachineIngredient.EnergyIngredient(RecipeModifier.IOType.OUTPUT, fePerTick);
    }

    public MachineRequirement energyRequirement(RecipeIo io, int fePerTick) {
        return new EnergyRequirement(io == RecipeIo.OUTPUT ? RecipeModifier.IOType.OUTPUT : RecipeModifier.IOType.INPUT,
                fePerTick);
    }

    public RecipeModifier modifier(String target, String io, float value, String operation, boolean chance) {
        return new RecipeModifier(target, ioType(io), value, operation(operation), chance);
    }

    public SingleBlockModifierReplacement singleBlockModifier(String name, BlockPredicate predicate,
            List<RecipeModifier> modifiers, ItemStack display) {
        return new SingleBlockModifierReplacement(name, predicate, modifiers, display);
    }

    public MachineStructureBuilderJS.PatternEntry patternEntry(BlockPredicate base,
            List<SingleBlockModifierReplacement> modifiers) {
        return new MachineStructureBuilderJS.PatternEntry(base, modifiers);
    }

    public LevelRequirement levelRequirement(String typeId, String levelId) {
        Identifier type = Identifier.parse(typeId);
        Identifier level = Identifier.parse(levelId);
        if (MachineLevelRegistry.getType(type) == null || MachineLevelRegistry.getLevel(level) == null
                || !MachineLevelRegistry.getLevel(level).typeId().equals(type)) {
            throw new IllegalArgumentException("Unknown or mismatched machine level: " + typeId + "/" + levelId);
        }
        return new LevelRequirement(type, level);
    }

    public LevelSlot levelSlot(String typeId) {
        Identifier type = Identifier.parse(typeId);
        if (MachineLevelRegistry.getType(type) == null) {
            throw new IllegalArgumentException("Unknown machine level type: " + typeId);
        }
        return new LevelSlot(type);
    }

    public SmartInterfaceRequirement smartInterfaceInput(String type, float min, float max) {
        return SmartInterfaceRequirement.input(type, min, max);
    }

    public SmartInterfaceRequirement smartInterfaceOutput(String type, float value) {
        return SmartInterfaceRequirement.output(type, value);
    }

    public MachineRequirement itemOutputRequirement(String itemId, int count, float chance) {
        return MachineRequirement.itemOutput(new ItemStack(requireItem(itemId), count), chance);
    }

    public MachineRequirement itemOutputRequirementWithComponents(String itemId, int count, JsonElement components, float chance) {
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                new ItemStack(requireItem(itemId), count), chance, List.of(),
                DataComponentPredicateSet.CODEC.parse(JsonOps.INSTANCE, components).getOrThrow(), 1F);
    }

    public MachineRequirement itemInputRequirement(String itemId, int count) {
        return MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(requireItem(itemId)), count));
    }

    public MachineRequirement fluidOutputRequirement(String fluidId, int amount, float chance) {
        return MachineRequirement.fluidOutput(fluidStack(fluidId, amount), chance);
    }

    private static Block requireBlock(String id) {
        Identifier identifier = Identifier.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(identifier)) throw new IllegalArgumentException("Unknown block: " + id);
        return BuiltInRegistries.BLOCK.getValue(identifier);
    }

    private static Item requireItem(String id) {
        Identifier identifier = Identifier.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(identifier)) throw new IllegalArgumentException("Unknown item: " + id);
        return BuiltInRegistries.ITEM.getValue(identifier);
    }

    private static RecipeModifier.IOType ioType(String io) {
        return switch (io) {
            case "input" -> RecipeModifier.IOType.INPUT;
            case "output" -> RecipeModifier.IOType.OUTPUT;
            default -> throw new IllegalArgumentException("Unknown modifier IO: " + io);
        };
    }

    private static int wholeNumber(Number value) {
        double number = value.doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Port count must be an integer: " + value);
        }
        return (int) number;
    }

    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, String value) {
        T parsed = property.getValue(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid value " + value + " for block state property " + property.getName()));
        return state.setValue(property, parsed);
    }

    private static RecipeModifier.Operation operation(String operation) {
        return switch (operation) {
            case "add" -> RecipeModifier.Operation.ADD;
            case "multiply" -> RecipeModifier.Operation.MULTIPLY;
            case "subtract" -> RecipeModifier.Operation.SUBTRACT;
            case "divide" -> RecipeModifier.Operation.DIVIDE;
            default -> throw new IllegalArgumentException("Unknown modifier operation: " + operation);
        };
    }

    private static PortTierRequirementSpec.Requirement parseTierRequirement(String minimum) {
        String[] parts = minimum.split(">=", -1);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid port tier requirement: " + minimum);
        String[] port = parts[0].split("_", -1);
        if (port.length != 3) throw new IllegalArgumentException("Invalid port tier requirement: " + minimum);
        String categoryName = port[0];
        String ioName = port[1];
        var category = switch (categoryName) {
            case "item" -> PortTierRequirementSpec.PortCategory.ITEM;
            case "fluid" -> PortTierRequirementSpec.PortCategory.FLUID;
            case "energy" -> PortTierRequirementSpec.PortCategory.ENERGY;
            default -> throw new IllegalArgumentException("Unknown port category: " + categoryName);
        };
        var io = switch (ioName) {
            case "input" -> IOType.INPUT;
            case "output" -> IOType.OUTPUT;
            default -> throw new IllegalArgumentException("Unknown port IO: " + ioName);
        };
        String expectedFamily = category == PortTierRequirementSpec.PortCategory.ITEM ? "bus" : "hatch";
        if (!port[2].equals(expectedFamily)) throw new IllegalArgumentException("Invalid port family: " + parts[0]);
        String[] tiers = category == PortTierRequirementSpec.PortCategory.FLUID
                ? new String[] {"tiny", "small", "normal", "reinforced", "big", "huge", "ludicrous", "vacuum"}
                : category == PortTierRequirementSpec.PortCategory.ENERGY
                ? new String[] {"tiny", "small", "normal", "reinforced", "big", "huge", "ludicrous", "ultimate"}
                : new String[] {"tiny", "small", "normal", "reinforced", "big", "huge", "ludicrous"};
        for (int tier = 0; tier < tiers.length; tier++) {
            if (tiers[tier].equals(parts[1])) return new PortTierRequirementSpec.Requirement(category, io, tier, parts[1]);
        }
        throw new IllegalArgumentException("Unknown port tier: " + parts[1]);
    }
}
