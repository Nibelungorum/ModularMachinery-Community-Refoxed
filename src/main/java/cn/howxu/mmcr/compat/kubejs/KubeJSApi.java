package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.recipe.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Public declaration helpers exposed to KubeJS as {@code MMCR_API}.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class KubeJSApi {
    public Identifier id(String id) {
        return Identifier.parse(id);
    }

    public BlockPos pos(int x, int y, int z) { return new BlockPos(x, y, z); }
    public BlockPredicate air() { return new BlockPredicate.Air(); }
    public BlockPredicate any() { return new BlockPredicate.Any(); }
    public BlockPredicate coupler() { return BlockPredicate.machineCoupler(); }

    public BlockPredicate block(String blockId) {
        return new BlockPredicate.OfBlock(requireBlock(blockId));
    }

    public BlockPredicate state(String blockStateId) {
        return new BlockPredicate.OfBlockState(requireBlock(blockStateId).defaultBlockState());
    }

    public BlockPredicate tag(String tagId) {
        return new BlockPredicate.OfTag(TagKey.create(Registries.BLOCK, Identifier.parse(tagId)));
    }

    public BlockPredicate anyOf(BlockPredicate... children) {
        if (children == null || children.length == 0) throw new IllegalArgumentException("anyOf requires children");
        return new BlockPredicate.AnyOf(List.of(children));
    }

    public BlockArray blockArray(Map<BlockPos, BlockPredicate> blocks) { return new BlockArray(blocks); }

    public PortRequirementSpec portRequirements(Map<String, Object> ranges) {
        var builder = PortRequirementSpec.builder();
        for (var entry : ranges.entrySet()) {
            Object range = entry.getValue();
            if (range instanceof Number min) builder.min(entry.getKey(), min.intValue());
            else if (range instanceof List<?> values && values.size() == 2
                    && values.getFirst() instanceof Number min && values.get(1) instanceof Number max) {
                builder.range(entry.getKey(), min.intValue(), max.intValue());
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
        return new MachineIngredient.ItemIngredient(Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(tag)), count, null, consumeChance);
    }

    public MachineIngredient fluidInput(String fluidId, int amount) {
        var fluid = BuiltInRegistries.FLUID.getValue(Identifier.parse(fluidId));
        if (fluid == null) throw new IllegalArgumentException("Unknown fluid: " + fluidId);
        return new MachineIngredient.FluidIngredient(FluidIngredient.of(fluid), amount);
    }

    public MachineIngredient energyInput(int fePerTick) { return new MachineIngredient.EnergyIngredient(fePerTick); }
    public MachineIngredient energyOutput(int fePerTick) {
        return new MachineIngredient.EnergyIngredient(RecipeModifier.IOType.OUTPUT, fePerTick);
    }

    public RecipeModifier modifier(String target, String io, float value, String operation, boolean chance) {
        return new RecipeModifier(target, ioType(io), value, operation(operation), chance);
    }

    public SingleBlockModifierReplacement singleBlockModifier(String name, BlockPos pos, BlockPredicate predicate,
            List<RecipeModifier> modifiers, String description, ItemStack display) {
        return new SingleBlockModifierReplacement(name, pos, predicate, modifiers, description, display);
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

    public SmartInterfaceRequirement smartInterfaceInput(String type, float min, float max) {
        return SmartInterfaceRequirement.input(type, min, max);
    }

    public SmartInterfaceRequirement smartInterfaceOutput(String type, float value) {
        return SmartInterfaceRequirement.output(type, value);
    }

    private static net.minecraft.world.level.block.Block requireBlock(String id) {
        var block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        if (block == null) throw new IllegalArgumentException("Unknown block: " + id);
        return block;
    }

    private static net.minecraft.world.item.Item requireItem(String id) {
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
        if (item == null) throw new IllegalArgumentException("Unknown item: " + id);
        return item;
    }

    private static RecipeModifier.IOType ioType(String io) {
        return switch (io) {
            case "input" -> RecipeModifier.IOType.INPUT;
            case "output" -> RecipeModifier.IOType.OUTPUT;
            default -> throw new IllegalArgumentException("Unknown modifier IO: " + io);
        };
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
        int separator = parts[0].lastIndexOf('_');
        if (separator < 1) throw new IllegalArgumentException("Invalid port tier requirement: " + minimum);
        String categoryName = parts[0].substring(0, separator);
        String ioName = parts[0].substring(separator + 1);
        var category = switch (categoryName) {
            case "item" -> PortTierRequirementSpec.PortCategory.ITEM;
            case "fluid" -> PortTierRequirementSpec.PortCategory.FLUID;
            case "energy" -> PortTierRequirementSpec.PortCategory.ENERGY;
            default -> throw new IllegalArgumentException("Unknown port category: " + categoryName);
        };
        var io = switch (ioName) {
            case "input" -> cn.howxu.mmcr.util.IOType.INPUT;
            case "output" -> cn.howxu.mmcr.util.IOType.OUTPUT;
            default -> throw new IllegalArgumentException("Unknown port IO: " + ioName);
        };
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
