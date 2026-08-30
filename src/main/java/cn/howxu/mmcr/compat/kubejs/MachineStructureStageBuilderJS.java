package cn.howxu.mmcr.compat.kubejs;

import cn.howxu.mmcr.api.machine.BlockArray;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.DynamicPatternSpec;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.MachineStructureRequirements;
import cn.howxu.mmcr.api.machine.PortRequirementSpec;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.level.LevelSlot;
import cn.howxu.mmcr.api.machine.level.MachineLevelRegistry;
import cn.howxu.mmcr.api.publicapi.machine.ModifierUse;
import cn.howxu.mmcr.registry.ModBlocks;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import dev.latvian.mods.rhino.util.HideFromJS;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * KubeJS-facing builder for one machine structure stage.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MachineStructureStageBuilderJS extends BuilderBase<MachineStructureDefinition.Declaration> {
    private final BlockArray.Builder sliceBuilder = new BlockArray.Builder();
    private final MachineStructureRequirements.Builder stageRequirements = MachineStructureRequirements.builder();
    private final List<DynamicPatternSpec> dynamicPatterns = new ArrayList<>();
    private PortRequirementSpec portRequirements = PortRequirementSpec.none();
    private PortTierRequirementSpec portTierRequirements = PortTierRequirementSpec.none();

    public MachineStructureStageBuilderJS(Identifier id) {
        super(Objects.requireNonNull(id, "id"));
        sliceBuilder.noController();
    }

    public MachineStructureStageBuilderJS(String id) {
        this(Identifier.parse(id));
    }

    public MachineStructureStageBuilderJS pattern(String... rows) {
        sliceBuilder.pattern(rows);
        return this;
    }

    @HideFromJS
    public MachineStructureStageBuilderJS pattern(List<String> rows) {
        return pattern(rows.toArray(String[]::new));
    }

    public MachineStructureStageBuilderJS patternAll(List<List<String>> slices) {
        if (slices.isEmpty()) {
            throw new IllegalArgumentException("patternAll(...) must contain at least one slice");
        }
        for (List<String> slice : slices) {
            pattern(slice);
        }
        return this;
    }

    public MachineStructureStageBuilderJS set(String symbol, Object value) {
        if (symbol == null || symbol.length() != 1 || symbol.charAt(0) == ' ') {
            throw new IllegalArgumentException("A pattern symbol must be exactly one non-space character");
        }
        MachineStructureBuilderJS.PatternEntry entry = toPatternEntry(value);
        sliceBuilder.set(symbol.charAt(0), entry.base());
        if (value instanceof LevelSlot levelSlot) {
            stageRequirements.levelSlot(symbol.charAt(0), levelSlot.typeId());
        }
        return this;
    }

    public MachineStructureStageBuilderJS modifier(String symbol, ModifierUse use) {
        if (symbol == null || symbol.length() != 1 || symbol.charAt(0) == ' ') {
            throw new IllegalArgumentException("A pattern symbol must be exactly one non-space character");
        }
        Objects.requireNonNull(use, "use");
        stageRequirements.modifier(symbol.charAt(0), use.modifierId(),
                MachineStructureBuilderJS.toInternalBlockPredicate(use.replacement()));
        return this;
    }

    public MachineStructureStageBuilderJS controller(String symbol) {
        if (symbol == null || symbol.length() != 1 || symbol.charAt(0) == ' ') {
            throw new IllegalArgumentException("A controller symbol must be exactly one non-space character");
        }
        sliceBuilder.set(symbol.charAt(0), new BlockPredicate.OfBlock(ModBlocks.controllerFor(id).get()));
        sliceBuilder.controller(symbol.charAt(0));
        return this;
    }

    public MachineStructureStageBuilderJS portRequirements(PortRequirementSpec requirements) {
        portRequirements = Objects.requireNonNull(requirements, "requirements");
        return this;
    }

    public MachineStructureStageBuilderJS portTierRequirements(PortTierRequirementSpec requirements) {
        portTierRequirements = Objects.requireNonNull(requirements, "requirements");
        return this;
    }

    public MachineStructureStageBuilderJS dynamicPattern(DynamicPatternSpec pattern) {
        dynamicPatterns.add(Objects.requireNonNull(pattern, "pattern"));
        return this;
    }

    public PortTierRequirementSpec itemInputTier(String id) { return KubeJSInterfaceHelpers.itemInputTier(id); }
    public PortTierRequirementSpec itemOutputTier(String id) { return KubeJSInterfaceHelpers.itemOutputTier(id); }
    public PortTierRequirementSpec fluidInputTier(String id) { return KubeJSInterfaceHelpers.fluidInputTier(id); }
    public PortTierRequirementSpec fluidOutputTier(String id) { return KubeJSInterfaceHelpers.fluidOutputTier(id); }
    public PortTierRequirementSpec energyInputTier(String id) { return KubeJSInterfaceHelpers.energyInputTier(id); }
    public PortTierRequirementSpec energyOutputTier(String id) { return KubeJSInterfaceHelpers.energyOutputTier(id); }

    public BlockPredicate anyOfItemInput() { return KubeJSInterfaceHelpers.anyOfItemInput(); }
    public BlockPredicate anyOfItemOutput() { return KubeJSInterfaceHelpers.anyOfItemOutput(); }
    public BlockPredicate anyOfFluidInput() { return KubeJSInterfaceHelpers.anyOfFluidInput(); }
    public BlockPredicate anyOfFluidOutput() { return KubeJSInterfaceHelpers.anyOfFluidOutput(); }
    public BlockPredicate anyOfEnergyInput() { return KubeJSInterfaceHelpers.anyOfEnergyInput(); }
    public BlockPredicate anyOfEnergyOutput() { return KubeJSInterfaceHelpers.anyOfEnergyOutput(); }
    public BlockPredicate anyOfUpgradeBus() { return KubeJSInterfaceHelpers.anyOfUpgradeBus(); }
    public BlockPredicate anyOfPort(String... ids) { return KubeJSInterfaceHelpers.anyOfPort(ids); }
    public BlockPredicate anyOfPort(Identifier... ids) { return KubeJSInterfaceHelpers.anyOfPort(ids); }
    public BlockPredicate anyOfPort(cn.howxu.mmcr.api.publicapi.machine.BlockPredicate... predicates) {
        return KubeJSInterfaceHelpers.anyOfPort(predicates);
    }
    public BlockPredicate factoryController() { return anyOfPort("factory_controller"); }
    public BlockPredicate parallelControllers() { return KubeJSInterfaceHelpers.parallelControllers(); }
    public BlockPredicate smartInterface() { return KubeJSInterfaceHelpers.smartInterface(); }

    public MachineStructureDefinition.Declaration build() {
        BlockArray pattern = sliceBuilder.build();
        MachineStructureRequirements requirements = MachineStructureRequirements.merge(
                sliceBuilder.requirements(), stageRequirements.build(pattern), 0);
        return new MachineStructureDefinition.Declaration(MachineStructureDefinition.Declaration.Kind.FULL,
                pattern, portRequirements, portTierRequirements, dynamicPatterns, requirements);
    }

    @Override
    @HideFromJS
    public MachineStructureDefinition.Declaration createObject() {
        return build();
    }

    private static MachineStructureBuilderJS.PatternEntry toPatternEntry(Object value) {
        return switch (value) {
            case MachineStructureBuilderJS.PatternEntry entry -> entry;
            case String blockId -> new MachineStructureBuilderJS.PatternEntry(new BlockPredicate.OfBlock(
                    BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId))));
            case Block block -> new MachineStructureBuilderJS.PatternEntry(new BlockPredicate.OfBlock(block));
            case BlockState state -> new MachineStructureBuilderJS.PatternEntry(new BlockPredicate.OfBlockState(state));
            case BlockPredicate predicate -> new MachineStructureBuilderJS.PatternEntry(predicate);
            case LevelSlot levelSlot -> new MachineStructureBuilderJS.PatternEntry(levelPredicate(levelSlot));
            default -> throw new IllegalArgumentException("Unknown pattern key value: " + value);
        };
    }

    private static BlockPredicate levelPredicate(LevelSlot slot) {
        Objects.requireNonNull(slot, "slot");
        if (MachineLevelRegistry.getType(slot.typeId()) == null) {
            throw new IllegalArgumentException("Unknown machine level type: " + slot.typeId());
        }
        var levels = MachineLevelRegistry.levelsForType(slot.typeId());
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("Machine level type has no registered levels: " + slot.typeId());
        }
        return new BlockPredicate.AnyOf(levels.stream().map(level -> level.statePredicate()).toList());
    }

}
