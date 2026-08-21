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
import cn.howxu.mmcr.api.recipe.modifier.SingleBlockModifierReplacement;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import cn.howxu.mmcr.api.machine.MachineStructureDefinition.Declaration;

/**
 * Server-script builder for reloadable machine structures.
 *
 * @author howxu <dev@howxu.cn>
 */
public class MachineStructureBuilderJS extends BuilderBase<MachineStructureDefinition> {
    public transient BlockArray pattern = new BlockArray(Map.of());
    public transient PortRequirementSpec portRequirements = PortRequirementSpec.none();
    public transient PortTierRequirementSpec portTierRequirements = PortTierRequirementSpec.none();
    public transient List<DynamicPatternSpec> dynamicPatterns = new ArrayList<>();
    public transient MachineStructureRequirements requirements = MachineStructureRequirements.EMPTY;
    private final List<Declaration> declarations = new ArrayList<>();
    private boolean patternDeclaration;
    private boolean classMetadataChanged;

    public MachineStructureBuilderJS(Identifier id) {
        super(id);
    }

    public MachineStructureBuilderJS(String id) {
        this(Identifier.parse(id));
    }

    public MachineStructureBuilderJS pattern(String grid, Map<String, Object> keys) {
        var blocks = new HashMap<BlockPos, BlockPredicate>();
        var symbolsByPosition = new HashMap<BlockPos, Character>();
        var requirementBuilder = MachineStructureRequirements.builder();
        var requirementKeys = new HashSet<Character>();
        var rows = grid.trim().split("\\s+");

        for (int y = 0; y < rows.length; y++) {
            var row = rows[y];

            for (int x = 0; x < row.length(); x++) {
                var key = row.charAt(x);

                if (key == '_' || key == '.') {
                    continue;
                }

                var value = keys.get(String.valueOf(key));

                if (value == null) {
                    continue;
                }

                BlockPos pos = new BlockPos(x, y, 0);
                PatternEntry entry = toPatternEntry(value);
                blocks.put(pos, entry.base());
                symbolsByPosition.put(pos, key);
                if (requirementKeys.add(key)) {
                    for (SingleBlockModifierReplacement modifier : entry.modifiers()) {
                        requirementBuilder.modifier(key, modifier);
                    }
                    if (value instanceof LevelSlot levelSlot) {
                        requirementBuilder.levelSlot(key, validateLevelType(levelSlot.typeId()));
                    }
                }
            }
        }

        pattern = new BlockArray(Map.copyOf(blocks), Map.of(), Map.copyOf(symbolsByPosition));
        requirements = requirementBuilder.build(pattern);
        declarations.clear();
        patternDeclaration = true;
        declarations.add(new Declaration(Declaration.Kind.FULL, pattern, portRequirements,
                portTierRequirements, dynamicPatterns, requirements));
        return this;
    }

    public MachineStructureBuilderJS fullStructure(BlockArray pattern) {
        return fullStructure(pattern, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(),
                MachineStructureRequirements.EMPTY);
    }

    public MachineStructureBuilderJS fullStructure(BlockArray pattern, PortRequirementSpec ports,
            PortTierRequirementSpec tiers, List<DynamicPatternSpec> dynamicPatterns,
            MachineStructureRequirements requirements) {
        applyPendingPatternMetadata();
        BlockArray fullPattern = Objects.requireNonNull(pattern);
        declarations.add(new Declaration(Declaration.Kind.FULL, fullPattern, ports, tiers,
                dynamicPatterns, requirements));
        patternDeclaration = false;
        classMetadataChanged = false;
        return this;
    }

    public MachineStructureBuilderJS extension(BlockArray pattern) {
        return extension(pattern, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(),
                MachineStructureRequirements.EMPTY);
    }

    public MachineStructureBuilderJS extension(BlockArray pattern, PortRequirementSpec ports,
            PortTierRequirementSpec tiers, List<DynamicPatternSpec> dynamicPatterns,
            MachineStructureRequirements requirements) {
        if (declarations.isEmpty()) throw new IllegalStateException("extension requires a full structure first");
        applyPendingPatternMetadata();
        BlockArray extensionPattern = Objects.requireNonNull(pattern);
        declarations.add(new Declaration(Declaration.Kind.EXTENSION, extensionPattern, ports, tiers,
                dynamicPatterns, requirements));
        return this;
    }

    public MachineStructureBuilderJS portRequirements(PortRequirementSpec requirements) {
        portRequirements = Objects.requireNonNull(requirements, "requirements");
        classMetadataChanged = true;
        return this;
    }

    public MachineStructureBuilderJS portTierRequirements(PortTierRequirementSpec requirements) {
        portTierRequirements = Objects.requireNonNull(requirements, "requirements");
        classMetadataChanged = true;
        return this;
    }

    public MachineStructureBuilderJS dynamicPattern(DynamicPatternSpec pattern) {
        dynamicPatterns.add(Objects.requireNonNull(pattern, "pattern"));
        classMetadataChanged = true;
        return this;
    }

    public BlockPredicate anyOfItemInput() { return KubeJSInterfaceHelpers.anyOfItemInput(); }
    public BlockPredicate anyOfItemOutput() { return KubeJSInterfaceHelpers.anyOfItemOutput(); }
    public BlockPredicate anyOfFluidInput() { return KubeJSInterfaceHelpers.anyOfFluidInput(); }
    public BlockPredicate anyOfFluidOutput() { return KubeJSInterfaceHelpers.anyOfFluidOutput(); }
    public BlockPredicate anyOfEnergyInput() { return KubeJSInterfaceHelpers.anyOfEnergyInput(); }
    public BlockPredicate anyOfEnergyOutput() { return KubeJSInterfaceHelpers.anyOfEnergyOutput(); }
    public BlockPredicate anyOfPort(String... ids) { return KubeJSInterfaceHelpers.anyOfPort(ids); }
    public BlockPredicate parallelControllers() { return KubeJSInterfaceHelpers.parallelControllers(); }
    public BlockPredicate smartInterface() { return KubeJSInterfaceHelpers.smartInterface(); }

    @Override
    public MachineStructureDefinition createObject() {
        if (declarations.isEmpty()) {
            return new MachineStructureDefinition(id, List.of(new Declaration(Declaration.Kind.FULL, pattern,
                    portRequirements, portTierRequirements, dynamicPatterns, requirements)));
        }
        applyPendingPatternMetadata();
        if (!classMetadataChanged) return new MachineStructureDefinition(id, declarations);
        List<Declaration> result = new ArrayList<>(declarations);
        Declaration first = result.getFirst();
        result.set(0, new Declaration(first.kind(), first.pattern(), portRequirements, portTierRequirements,
                dynamicPatterns, requirements));
        return new MachineStructureDefinition(id, result);
    }

    public void build() {
        var transaction = KubeJSContentReloadTransaction.active();
        if (transaction == null) {
            throw new IllegalStateException("Machine structures must be built during KubeJS server script loading");
        }
        transaction.registerStructure(createObject());
    }

    private static PatternEntry toPatternEntry(Object value) {
        return switch (value) {
            case PatternEntry entry -> entry;
            case String blockId -> new PatternEntry(
                    new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId))), List.of());
            case Block block -> new PatternEntry(new BlockPredicate.OfBlock(block), List.of());
            case BlockState state -> new PatternEntry(new BlockPredicate.OfBlockState(state), List.of());
            case BlockPredicate predicate -> new PatternEntry(predicate, List.of());
            case LevelSlot levelSlot -> new PatternEntry(levelPredicate(levelSlot), List.of());
            default -> throw new IllegalArgumentException("Unknown pattern key value: " + value);
        };
    }

    private void applyPendingPatternMetadata() {
        if (!patternDeclaration || !classMetadataChanged || declarations.isEmpty()) return;
        Declaration first = declarations.getFirst();
        declarations.set(0, new Declaration(first.kind(), first.pattern(), portRequirements, portTierRequirements,
                dynamicPatterns, requirements));
        classMetadataChanged = false;
    }

    private static BlockPredicate levelPredicate(LevelSlot slot) {
        validateLevelType(slot.typeId());
        var levels = MachineLevelRegistry.levelsForType(slot.typeId());
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("Machine level type has no registered levels: " + slot.typeId());
        }
        return new BlockPredicate.AnyOf(levels.stream().map(level -> level.statePredicate()).toList());
    }

    private static Identifier validateLevelType(Identifier typeId) {
        Objects.requireNonNull(typeId, "typeId");
        if (MachineLevelRegistry.getType(typeId) == null) {
            throw new IllegalArgumentException("Unknown machine level type: " + typeId);
        }
        return typeId;
    }

    /**
     * Character-key structure declaration value carrying a base predicate plus modifier alternatives.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record PatternEntry(BlockPredicate base, List<SingleBlockModifierReplacement> modifiers) {
        public PatternEntry {
            Objects.requireNonNull(base, "base");
            modifiers = List.copyOf(modifiers == null ? List.of() : modifiers);
            modifiers.forEach(modifier -> Objects.requireNonNull(modifier, "modifier"));
        }
    }
}
