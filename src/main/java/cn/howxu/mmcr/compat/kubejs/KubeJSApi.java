package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.machine.modifier.MachineModifier;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalIngredient;
import cn.howxu.mmcr.api.compat.mekanism.ChemicalOutput;
import cn.howxu.mmcr.api.compat.mekanism.MekanismPortFamilies;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRecipeDeclarations;
import cn.howxu.mmcr.compat.ars_nouveau.SourceRequirement;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.compat.botania.ManaRecipeDeclarations;
import cn.howxu.mmcr.compat.botania.ManaRequirement;
import cn.howxu.mmcr.api.controller.ControllerScreenTextScope;
import cn.howxu.mmcr.api.recipe.MachineIngredient;
import cn.howxu.mmcr.api.recipe.MachineOutput;
import cn.howxu.mmcr.api.recipe.component.DataComponentPredicateSet;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier;
import cn.howxu.mmcr.api.recipe.requirement.MachineRequirement;
import cn.howxu.mmcr.api.recipe.requirement.EnergyRequirement;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import cn.howxu.mmcr.api.recipe.requirement.LevelRequirement;
import cn.howxu.mmcr.api.recipe.MachineRecipeBuilder;
import cn.howxu.mmcr.api.recipe.requirement.StageRequirement;
import cn.howxu.mmcr.api.capability.plan.OutputPolicy;
import cn.howxu.mmcr.api.recipe.modifier.RecipeModifier.IOType;
import cn.howxu.mmcr.api.recipe.CustomRecipeIo;
import cn.howxu.mmcr.api.recipe.RecipeIoValidation;
import cn.howxu.mmcr.compat.create.StressRequirement;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.definition.ModifierUse;
import cn.howxu.mmcr.api.machine.definition.MachineBehaviorContext;
import cn.howxu.mmcr.api.data.view.DataValue;
import cn.howxu.mmcr.api.network.view.MachineReference;
import cn.howxu.mmcr.api.network.view.NetworkApi;
import cn.howxu.mmcr.api.network.view.NetworkInterfaceReference;
import cn.howxu.mmcr.api.network.view.RequestBody;

import cn.howxu.mmcr.api.recipe.requirement.ItemRequirement;
import cn.howxu.mmcr.api.presentation.ReadableNumber;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Public declaration helpers exposed to KubeJS through {@code MMCR.getAPI()} and MMCR event objects.
 *
 * @author howxu <dev@howxu.cn>
 */
public final class KubeJSApi {
    private final ScreenScopeValues screenScope = new ScreenScopeValues();
    private final RecipeIoValues recipeIO = new RecipeIoValues();
    private final OutputPolicyValues outputPolicy = new OutputPolicyValues();
    private final ModifierOperationValues modifierOperation = new ModifierOperationValues();

    public ScreenScopeValues screenScope() {
        return screenScope;
    }

    public RecipeIoValues recipeIO() {
        return recipeIO;
    }

    public OutputPolicyValues outputPolicy() {
        return outputPolicy;
    }

    public ModifierOperationValues modifierOperation() {
        return modifierOperation;
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
        public final IOType INPUT = IOType.INPUT;
        public final IOType OUTPUT = IOType.OUTPUT;
    }

    /** KubeJS-visible machine output policy constants.
     * @author howxu <dev@howxu.cn>
     */
    public static final class OutputPolicyValues {
        public final OutputPolicy REQUIRE_FULL = OutputPolicy.REQUIRE_FULL;
        public final OutputPolicy ALLOW_PARTIAL = OutputPolicy.ALLOW_PARTIAL;
    }

    /** KubeJS-visible recipe modifier operation constants.
     * @author howxu <dev@howxu.cn>
     */
    public static final class ModifierOperationValues {
        public final RecipeModifier.Operation ADD = RecipeModifier.Operation.ADD;
        public final RecipeModifier.Operation MULTIPLY = RecipeModifier.Operation.MULTIPLY;
        public final RecipeModifier.Operation SUBTRACT = RecipeModifier.Operation.SUBTRACT;
        public final RecipeModifier.Operation DIVIDE = RecipeModifier.Operation.DIVIDE;
    }

    public String readableNumber(long value) {
        return ReadableNumber.formatCompact(value);
    }

    public String readableNumberBigInt(BigInteger value) {
        return ReadableNumber.formatCompact(value);
    }

    public String readableNumberBigDecimal(BigDecimal value) {
        return ReadableNumber.formatCompact(value);
    }

    public String readableNumberExact(long value) {
        return ReadableNumber.formatExact(value);
    }

    public String readableNumberFull(long value) {
        return ReadableNumber.format(value);
    }

    public String readableNumberFullBigInt(BigInteger value) {
        return ReadableNumber.format(value);
    }

    public String readableNumberFullBigDecimal(BigDecimal value) {
        return ReadableNumber.format(value);
    }

    public String readableNumberForSlot(long value, int scale, String unit) {
        return ReadableNumber.formatForSlot(value, scale, unit);
    }

    public ResourceLocation id(String id) {
        return ResourceLocation.parse(id);
    }

    public List<NetworkInterfaceReference> networkInterfaces(MachineBehaviorContext context) {
        return NetworkApi.interfaces(context);
    }

    public void sendRequest(NetworkInterfaceReference source, MachineReference target, String requestId, Object body) {
        DataValue value = dataValue(body);
        Map<String, DataValue> values = value.asMap().orElseThrow(() ->
                new IllegalArgumentException("Network request body must be a map"));
        NetworkApi.sendRequest(source, target, ResourceLocation.parse(requestId), RequestBody.of(values));
    }

    public DataValue dataValue(Object value) {
        return toDataValue(value);
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
    public BlockPredicate anyOfSourceInput() { return KubeJSInterfaceHelpers.anyOfSourceInput(); }
    public BlockPredicate anyOfSourceOutput() { return KubeJSInterfaceHelpers.anyOfSourceOutput(); }
    public BlockPredicate anyOfSourcePorts() { return KubeJSInterfaceHelpers.anyOfSourcePorts(); }
    public BlockPredicate anySourceInput() { return anyOfSourceInput(); }
    public BlockPredicate anySourceOutput() { return anyOfSourceOutput(); }
    public BlockPredicate anySourcePorts() { return anyOfSourcePorts(); }
    public BlockPredicate anyOfManaInput() { return KubeJSInterfaceHelpers.anyOfManaInput(); }
    public BlockPredicate anyOfManaOutput() { return KubeJSInterfaceHelpers.anyOfManaOutput(); }
    public BlockPredicate anyOfManaPorts() { return KubeJSInterfaceHelpers.anyOfManaPorts(); }
    public BlockPredicate anyManaInput() { return anyOfManaInput(); }
    public BlockPredicate anyManaOutput() { return anyOfManaOutput(); }
    public BlockPredicate anyManaPorts() { return anyOfManaPorts(); }
    public BlockPredicate anyOfItemPorts() { return KubeJSInterfaceHelpers.anyOfItemPorts(); }
    public BlockPredicate anyOfFluidPorts() { return KubeJSInterfaceHelpers.anyOfFluidPorts(); }
    public BlockPredicate anyOfEnergyPorts() { return KubeJSInterfaceHelpers.anyOfEnergyPorts(); }
    public BlockPredicate anyOfChemicalPorts() { return KubeJSInterfaceHelpers.anyOfChemicalPorts(); }
    public BlockPredicate anyOfRadioactiveChemicalPorts() {
        return KubeJSInterfaceHelpers.anyOfRadioactiveChemicalPorts();
    }
    public BlockPredicate anyOfHeatPorts() { return KubeJSInterfaceHelpers.anyOfHeatPorts(); }
    public BlockPredicate anyOfStressInput() { return KubeJSInterfaceHelpers.anyOfStressInput(); }
    public BlockPredicate anyOfStressOutput() { return KubeJSInterfaceHelpers.anyOfStressOutput(); }
    public BlockPredicate anyOfStressPorts() { return KubeJSInterfaceHelpers.anyOfStressPorts(); }
    public BlockPredicate anyStressInput() { return anyOfStressInput(); }
    public BlockPredicate anyStressOutput() { return anyOfStressOutput(); }
    public BlockPredicate anyStressPorts() { return anyOfStressPorts(); }
    public BlockPredicate anyOfAirInput() { return KubeJSInterfaceHelpers.anyOfAirInput(); }
    public BlockPredicate anyOfAirOutput() { return KubeJSInterfaceHelpers.anyOfAirOutput(); }
    public BlockPredicate anyOfAirPorts() { return KubeJSInterfaceHelpers.anyOfAirPorts(); }
    public BlockPredicate anyAirInput() { return anyOfAirInput(); }
    public BlockPredicate anyAirOutput() { return anyOfAirOutput(); }
    public BlockPredicate anyAirPorts() { return anyOfAirPorts(); }
    public BlockPredicate anyOfUpgradeBus() { return KubeJSInterfaceHelpers.anyOfUpgradeBus(); }
    public BlockPredicate parallelControllers() { return KubeJSInterfaceHelpers.parallelControllers(); }
    public BlockPredicate smartInterface() { return KubeJSInterfaceHelpers.smartInterface(); }
    public BlockPredicate dataStorage() { return KubeJSInterfaceHelpers.dataStorage(); }
    public BlockPredicate factoryController() { return KubeJSInterfaceHelpers.factoryController(); }
    public BlockPredicate networkInterface() { return KubeJSInterfaceHelpers.networkInterface(); }

    public PortTierRequirementSpec sourceInputTier(String id) { return KubeJSInterfaceHelpers.sourceInputTier(id); }
    public PortTierRequirementSpec sourceOutputTier(String id) { return KubeJSInterfaceHelpers.sourceOutputTier(id); }
    public PortTierRequirementSpec manaInputTier(String id) { return KubeJSInterfaceHelpers.manaInputTier(id); }
    public PortTierRequirementSpec manaOutputTier(String id) { return KubeJSInterfaceHelpers.manaOutputTier(id); }

    public BlockPredicate ports() { return KubeJSInterfaceHelpers.ports(); }

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
        var tag = TagKey.create(Registries.BLOCK, ResourceLocation.parse(tagId));
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

    public MachineIngredient itemInput(String itemId, long count, float consumeChance) {
        return new MachineIngredient.ItemIngredient(Ingredient.of(requireItem(itemId)), MachineOutput.recipeStackAmount(count), null, consumeChance);
    }

    public MachineIngredient itemInput(String itemId, int count, float consumeChance) {
        return itemInput(itemId, (long) count, consumeChance);
    }

    public MachineIngredient tagInput(String tagId, long count, float consumeChance) {
        var tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(tagId));
        return new MachineIngredient.ItemIngredient(Ingredient.of(tag), MachineOutput.recipeStackAmount(count), null, consumeChance);
    }

    public MachineIngredient tagInput(String tagId, int count, float consumeChance) {
        return tagInput(tagId, (long) count, consumeChance);
    }

    public MachineIngredient fluidInput(String fluidId, long amount) {
        ResourceLocation identifier = ResourceLocation.parse(fluidId);
        if (!BuiltInRegistries.FLUID.containsKey(identifier)) throw new IllegalArgumentException("Unknown fluid: " + fluidId);
        return new MachineIngredient.FluidIngredient(FluidIngredient.of(BuiltInRegistries.FLUID.get(identifier)), MachineOutput.recipeStackAmount(amount));
    }

    public MachineIngredient fluidInput(String fluidId, int amount) {
        return fluidInput(fluidId, (long) amount);
    }

    public FluidStack fluidStack(String fluidId, long amount) {
        ResourceLocation identifier = ResourceLocation.parse(fluidId);
        if (!BuiltInRegistries.FLUID.containsKey(identifier)) throw new IllegalArgumentException("Unknown fluid: " + fluidId);
        return new FluidStack(BuiltInRegistries.FLUID.get(identifier), MachineOutput.recipeStackAmount(amount));
    }

    public FluidStack fluidStack(String fluidId, int amount) {
        return fluidStack(fluidId, (long) amount);
    }

    public MachineIngredient energyInput(long fePerTick) { return new MachineIngredient.EnergyIngredient(fePerTick); }
    public MachineIngredient energyOutput(long fePerTick) {
        return new MachineIngredient.EnergyIngredient(RecipeModifier.IOType.OUTPUT, fePerTick);
    }

    public CustomRecipeIo chemicalInput(String chemicalId, long amount) {
        return customRecipeIo(MekanismPortFamilies.CHEMICAL.toString(), IOType.INPUT,
                MachineRecipeBuilder.chemicalInputPayload(
                        ChemicalIngredient.chemical(requireChemicalId(chemicalId, "chemicalId"), amount)));
    }

    public CustomRecipeIo chemicalInput(String chemicalId, long amount, double consumeChance) {
        return customRecipeIo(MekanismPortFamilies.CHEMICAL.toString(), IOType.INPUT,
                MachineRecipeBuilder.chemicalInputPayload(
                        ChemicalIngredient.chemical(requireChemicalId(chemicalId, "chemicalId"), amount),
                        (float) consumeChance));
    }

    public CustomRecipeIo chemicalTagInput(String tagId, long amount) {
        return customRecipeIo(MekanismPortFamilies.CHEMICAL.toString(), IOType.INPUT,
                MachineRecipeBuilder.chemicalInputPayload(
                        ChemicalIngredient.tag(requireChemicalId(tagId, "tagId"), amount)));
    }

    public CustomRecipeIo chemicalTagInput(String tagId, long amount, double consumeChance) {
        return customRecipeIo(MekanismPortFamilies.CHEMICAL.toString(), IOType.INPUT,
                MachineRecipeBuilder.chemicalInputPayload(
                        ChemicalIngredient.tag(requireChemicalId(tagId, "tagId"), amount),
                        (float) consumeChance));
    }

    public CustomRecipeIo chemicalOutput(String chemicalId, long amount, double chance) {
        return customRecipeIo(MekanismPortFamilies.CHEMICAL.toString(), IOType.OUTPUT,
                MachineRecipeBuilder.chemicalOutputPayload(ChemicalOutput.of(
                        requireChemicalId(chemicalId, "chemicalId"), amount, (float) chance)));
    }

    public CustomRecipeIo heatTemperatureInput(double temperature) {
        return customRecipeIo(MekanismPortFamilies.HEAT_TEMPERATURE.toString(), IOType.INPUT,
                MachineRecipeBuilder.heatInputPayload(temperature));
    }

    public CustomRecipeIo heatOutput(double heat) {
        return customRecipeIo(MekanismPortFamilies.HEAT.toString(), IOType.OUTPUT,
                MachineRecipeBuilder.heatOutputPayload(heat));
    }

    public CustomRecipeIo sourceInput(long amount) {
        return customRecipeIo(ArsSourceIds.SOURCE.toString(), IOType.INPUT, SourceRecipeDeclarations.inputPayload(amount));
    }

    public CustomRecipeIo manaInput(long amount) {
        return customRecipeIo(BotaniaManaIds.MANA.toString(), IOType.INPUT, ManaRecipeDeclarations.inputPayload(amount));
    }

    public CustomRecipeIo manaOutput(long amount) {
        return customRecipeIo(BotaniaManaIds.MANA.toString(), IOType.OUTPUT, ManaRecipeDeclarations.outputPayload(amount));
    }

    public CustomRecipeIo sourceOutput(long amount) {
        return customRecipeIo(ArsSourceIds.SOURCE.toString(), IOType.OUTPUT, SourceRecipeDeclarations.outputPayload(amount));
    }

    public SourceRequirement sourceInputRequirement(long amount) { return sourceInputRequirement(amount, List.of()); }
    public SourceRequirement sourceInputRequirement(long amount, List<String> tags) { return new SourceRequirement(IOType.INPUT, amount, tags); }
    public SourceRequirement sourceOutputRequirement(long amount) { return sourceOutputRequirement(amount, List.of()); }
    public SourceRequirement sourceOutputRequirement(long amount, List<String> tags) { return new SourceRequirement(IOType.OUTPUT, amount, tags); }
    public ManaRequirement manaInputRequirement(long amount) { return manaInputRequirement(amount, List.of()); }
    public ManaRequirement manaInputRequirement(long amount, List<String> tags) { return new ManaRequirement(IOType.INPUT, amount, tags); }
    public ManaRequirement manaOutputRequirement(long amount) { return manaOutputRequirement(amount, List.of()); }
    public ManaRequirement manaOutputRequirement(long amount, List<String> tags) { return new ManaRequirement(IOType.OUTPUT, amount, tags); }

    public EnergyRequirement energyRequirement(IOType io, long fePerTick) {
        return new EnergyRequirement(io, fePerTick);
    }

    @HideFromJS
    public CustomRecipeIo airInput(long airPerTick, float minPressure) {
        return airInput(airPerTick, minPressure, List.of());
    }

    @HideFromJS
    public CustomRecipeIo airInput(long airPerTick, float minPressure, List<String> tags) {
        return customRecipeIo(PneumaticIds.AIR.toString(), IOType.INPUT,
                MachineRecipeBuilder.airInputPayload(airPerTick, minPressure, tags));
    }

    @HideFromJS
    public CustomRecipeIo airOutput(long airPerTick) { return airOutput(airPerTick, List.of()); }

    @HideFromJS
    public CustomRecipeIo airOutput(long airPerTick, List<String> tags) {
        return customRecipeIo(PneumaticIds.AIR.toString(), IOType.OUTPUT,
                MachineRecipeBuilder.airOutputPayload(airPerTick, tags));
    }

    public CustomRecipeIo airInput(Object airPerTick, Object minPressure) {
        return airInput(airPerTick, minPressure, List.of());
    }

    public CustomRecipeIo airInput(Object airPerTick, Object minPressure, List<String> tags) {
        return airInput(airRate(airPerTick), airPressure(minPressure), tags);
    }

    public CustomRecipeIo airOutput(Object airPerTick) { return airOutput(airPerTick, List.of()); }

    public CustomRecipeIo airOutput(Object airPerTick, List<String> tags) {
        return airOutput(airRate(airPerTick), tags);
    }

    private static long airRate(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Air rate must be a number");
        long rate;
        try {
            if (number instanceof BigInteger integer) rate = integer.longValueExact();
            else if (number instanceof BigDecimal decimal) rate = decimal.longValueExact();
            else if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
                rate = number.longValue();
            } else if (number instanceof Float || number instanceof Double) {
                double numeric = number.doubleValue();
                // 2^63 is the first out-of-range double; (double) Long.MAX_VALUE rounds up to it.
                if (!Double.isFinite(numeric) || numeric < 0D || numeric >= 0x1p63 || numeric != Math.rint(numeric)) {
                    throw new IllegalArgumentException("Air rate must be a non-negative long integer");
                }
                rate = (long) numeric;
            } else rate = new BigDecimal(number.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Air rate must be a non-negative long integer", exception);
        }
        if (rate < 0L) throw new IllegalArgumentException("Air rate must be a non-negative long integer");
        return rate;
    }

    private static float airPressure(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Air pressure must be a number");
        double pressure = number.doubleValue();
        float converted = (float) pressure;
        if (!Double.isFinite(pressure) || pressure < 0D || !Float.isFinite(converted)) {
            throw new IllegalArgumentException("Air pressure must be finite and non-negative");
        }
        return converted;
    }

    // Java keeps typed overloads; scripts validate original values before Rhino can coerce strings.
    @HideFromJS
    public CustomRecipeIo stressInput(double stress, double minRpm) {
        return stressInput(stress, minRpm, List.of());
    }

    @HideFromJS
    public CustomRecipeIo stressInput(double stress, double minRpm, List<String> tags) {
        return customRecipeIo(StressRequirement.TYPE.id().toString(), IOType.INPUT,
                MachineRecipeBuilder.stressInputPayload(stress, minRpm, tags));
    }

    @HideFromJS
    public CustomRecipeIo stressOutput(double stress, double rpm) {
        return stressOutput(stress, rpm, List.of());
    }

    @HideFromJS
    public CustomRecipeIo stressOutput(double stress, double rpm, List<String> tags) {
        return customRecipeIo(StressRequirement.TYPE.id().toString(), IOType.OUTPUT,
                MachineRecipeBuilder.stressOutputPayload(stress, rpm, tags));
    }

    public CustomRecipeIo stressInput(Object stress, Object minRpm) {
        return stressInput(stress, minRpm, List.of());
    }

    public CustomRecipeIo stressInput(Object stress, Object minRpm, List<String> tags) {
        return stressInput(stressNumber(stress), stressNumber(minRpm), tags);
    }

    public CustomRecipeIo stressOutput(Object stress, Object rpm) {
        return stressOutput(stress, rpm, List.of());
    }

    public CustomRecipeIo stressOutput(Object stress, Object rpm, List<String> tags) {
        return stressOutput(stressNumber(stress), stressNumber(rpm), tags);
    }

    private static double stressNumber(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Stress parameters must be numbers");
        return number.doubleValue();
    }

    /**
     * Creates a validated custom recipe IO declaration for KubeJS.
     *
     * @param typeId registered type identifier
     * @param io recipe IO direction
     * @param payload registered codec payload
     * @return validated custom recipe IO
     */
    public CustomRecipeIo customRecipeIo(String typeId, IOType io, JsonElement payload) {
        return RecipeIoValidation.custom(ResourceLocation.parse(typeId), io, payload);
    }

    public MachineModifier.Numeric modifier(String target, String scope, double value, String operation, boolean chance) {
        return MachineModifier.numeric(target, scope, value, operation, chance);
    }

    public MachineModifier.Parallelized modifier(String target, String scope, boolean value) {
        if (!"parallelized".equalsIgnoreCase(target) || !"recipe".equalsIgnoreCase(scope)) {
            throw new IllegalArgumentException("Boolean machine modifiers require target 'parallelized' and scope 'recipe'");
        }
        return MachineModifier.parallelized(value);
    }

    public ModifierDefinition modifierDefinition(List<MachineModifier> modifiers) {
        return new ModifierDefinition(modifiers);
    }

    public ModifierUse modifierUse(String modifierId, BlockPredicate replacement) {
        ResourceLocation id = ControllerScreenTextEventJS.parseResourceLocation(modifierId, "modifierId");
        return new ModifierUse(id, toPublicBlockPredicate(replacement));
    }

    public LevelRequirement levelRequirement(String typeId, String levelId) {
        ResourceLocation type = ResourceLocation.parse(typeId);
        ResourceLocation level = ResourceLocation.parse(levelId);
        var registered = MachineLevelRegistry.getLevel(level);
        if (MachineLevelRegistry.getType(type) == null || registered == null
                || !registered.typeId().equals(type)) {
            throw new IllegalArgumentException("Unknown or mismatched machine level: " + typeId + "/" + levelId);
        }
        return new LevelRequirement(type, level);
    }

    public StageRequirement stageRequirement(int minStage) {
        return new StageRequirement(minStage);
    }

    public LevelSlot levelSlot(String typeId) {
        ResourceLocation type = ResourceLocation.parse(typeId);
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

    public MachineRequirement itemOutputRequirement(String itemId, long count, float chance) {
        return MachineRequirement.itemOutput(new ItemStack(requireItem(itemId), MachineOutput.recipeStackAmount(count)), chance);
    }

    public MachineRequirement itemOutputRequirement(String itemId, int count, float chance) {
        return itemOutputRequirement(itemId, (long) count, chance);
    }

    public MachineRequirement itemOutputRequirementWithComponents(String itemId, long count, JsonElement components, float chance) {
        return new ItemRequirement(RecipeModifier.IOType.OUTPUT, null, 0,
                new ItemStack(requireItem(itemId), MachineOutput.recipeStackAmount(count)), chance, List.of(),
                DataComponentPredicateSet.CODEC.parse(JsonOps.INSTANCE, components).getOrThrow(), 1F);
    }

    public MachineRequirement itemOutputRequirementWithComponents(String itemId, int count, JsonElement components,
                                                                    float chance) {
        return itemOutputRequirementWithComponents(itemId, (long) count, components, chance);
    }

    public MachineRequirement itemInputRequirement(String itemId, long count) {
        return MachineRequirement.fromInput(new MachineIngredient.ItemIngredient(Ingredient.of(requireItem(itemId)), MachineOutput.recipeStackAmount(count)));
    }

    public MachineRequirement itemInputRequirement(String itemId, int count) {
        return itemInputRequirement(itemId, (long) count);
    }

    public MachineRequirement fluidInputRequirement(String fluidId, long amount) {
        return MachineRequirement.fromInput(fluidInput(fluidId, amount));
    }

    public MachineRequirement fluidInputRequirement(String fluidId, int amount) {
        return fluidInputRequirement(fluidId, (long) amount);
    }

    public MachineRequirement fluidOutputRequirement(String fluidId, long amount, float chance) {
        return MachineRequirement.fluidOutput(fluidStack(fluidId, amount), chance);
    }

    public MachineRequirement fluidOutputRequirement(String fluidId, int amount, float chance) {
        return fluidOutputRequirement(fluidId, (long) amount, chance);
    }

    private static Block requireBlock(String id) {
        ResourceLocation identifier = ResourceLocation.parse(id);
        if (!BuiltInRegistries.BLOCK.containsKey(identifier)) throw new IllegalArgumentException("Unknown block: " + id);
        return BuiltInRegistries.BLOCK.get(identifier);
    }

    private static Item requireItem(String id) {
        ResourceLocation identifier = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(identifier)) throw new IllegalArgumentException("Unknown item: " + id);
        return BuiltInRegistries.ITEM.get(identifier);
    }

    private static ResourceLocation requireChemicalId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
        try {
            return ResourceLocation.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value, exception);
        }
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

    private static DataValue toDataValue(Object value) {
        if (value == null) throw new IllegalArgumentException("Network value must not be null");
        if (value instanceof DataValue dataValue) return dataValue;
        if (value instanceof Map<?, ?> map) {
            Map<String, DataValue> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                    throw new IllegalArgumentException("Network map keys must be non-blank strings");
                }
                converted.put(key, toDataValue(entry.getValue()));
            }
            return DataValue.map(converted);
        }
        if (value instanceof Collection<?> collection) {
            List<DataValue> converted = new ArrayList<>(collection.size());
            for (Object element : collection) converted.add(toDataValue(element));
            return DataValue.list(converted);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<DataValue> converted = new ArrayList<>(length);
            for (int index = 0; index < length; index++) converted.add(toDataValue(Array.get(value, index)));
            return DataValue.list(converted);
        }
        if (value instanceof Boolean booleanValue) return DataValue.of(booleanValue);
        if (value instanceof String stringValue) return DataValue.of(stringValue);
        if (value instanceof BigInteger bigInteger) return DataValue.of(bigInteger);
        if (value instanceof BigDecimal bigDecimal) return DataValue.of(bigDecimal);
        if (value instanceof Byte byteValue) return DataValue.of(byteValue);
        if (value instanceof Short shortValue) return DataValue.of(shortValue);
        if (value instanceof Integer integerValue) return DataValue.of(integerValue);
        if (value instanceof Long longValue) return DataValue.of(longValue);
        if (value instanceof Float floatValue) return DataValue.of(floatValue);
        if (value instanceof Double doubleValue) return DataValue.of(doubleValue);
        throw new IllegalArgumentException("Unsupported network value: " + value.getClass().getName());
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

    private static cn.howxu.mmcr.api.machine.definition.BlockPredicate toPublicBlockPredicate(
            BlockPredicate predicate) {
        Objects.requireNonNull(predicate, "replacement");
        return switch (predicate) {
            case BlockPredicate.Air ignored -> cn.howxu.mmcr.api.machine.definition.BlockPredicate.block(Blocks.AIR);
            case BlockPredicate.Any ignored -> throw new IllegalArgumentException(
                    "Any is not supported as a modifier replacement predicate");
            case BlockPredicate.MachineCoupler ignored ->
                    cn.howxu.mmcr.api.machine.definition.BlockPredicate.machineCoupler();
            case BlockPredicate.OfBlock ofBlock ->
                    cn.howxu.mmcr.api.machine.definition.BlockPredicate.block(ofBlock.block());
            case BlockPredicate.DeferredBlock deferredBlock ->
                    cn.howxu.mmcr.api.machine.definition.BlockPredicate.deferredBlock(deferredBlock.supplier());
            case BlockPredicate.OfBlockState ofBlockState ->
                    cn.howxu.mmcr.api.machine.definition.BlockPredicate.blockState(ofBlockState.state());
            case BlockPredicate.OfTag ofTag -> cn.howxu.mmcr.api.machine.definition.BlockPredicate.tag(ofTag.tag());
            case BlockPredicate.AnyOf anyOf -> cn.howxu.mmcr.api.machine.definition.BlockPredicate.anyOf(
                    anyOf.children().stream().map(KubeJSApi::toPublicBlockPredicate).toList());
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
            case "source" -> PortTierRequirementSpec.PortCategory.SOURCE;
            case "mana" -> PortTierRequirementSpec.PortCategory.MANA;
            default -> throw new IllegalArgumentException("Unknown port category: " + categoryName);
        };
        var io = switch (ioName) {
            case "input" -> cn.howxu.mmcr.util.IOType.INPUT;
            case "output" -> cn.howxu.mmcr.util.IOType.OUTPUT;
            default -> throw new IllegalArgumentException("Unknown port IO: " + ioName);
        };
        String expectedFamily = category == PortTierRequirementSpec.PortCategory.MANA ? "pool"
                : category == PortTierRequirementSpec.PortCategory.SOURCE ? "interface"
                : category == PortTierRequirementSpec.PortCategory.ITEM ? "bus" : "hatch";
        if (!port[2].equals(expectedFamily)) throw new IllegalArgumentException("Invalid port family: " + parts[0]);
        String[] tiers = category == PortTierRequirementSpec.PortCategory.SOURCE
                || category == PortTierRequirementSpec.PortCategory.MANA
                ? new String[] {"normal"}
                : category == PortTierRequirementSpec.PortCategory.FLUID
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
