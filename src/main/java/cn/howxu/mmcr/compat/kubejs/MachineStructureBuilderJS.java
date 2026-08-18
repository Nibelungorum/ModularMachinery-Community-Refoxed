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
import java.util.LinkedHashMap;
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
    public transient Map<BlockPos, List<SingleBlockModifierReplacement>> modifierReplacements = new LinkedHashMap<>();
    public transient Map<BlockPos, Identifier> levelSlots = new LinkedHashMap<>();
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
                blocks.put(pos, toPredicate(value));
                symbolsByPosition.put(pos, key);
                if (value instanceof LevelSlot levelSlot) {
                    levelSlots.put(pos, levelSlot.typeId());
                }
            }
        }

        pattern = new BlockArray(Map.copyOf(blocks), Map.of(), Map.copyOf(symbolsByPosition));
        declarations.clear();
        patternDeclaration = true;
        declarations.add(new Declaration(Declaration.Kind.FULL, pattern, portRequirements,
                portTierRequirements, dynamicPatterns, requirements, modifierReplacements, levelSlots));
        return this;
    }

    public MachineStructureBuilderJS fullStructure(BlockArray pattern) {
        return fullStructure(pattern, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), Map.of());
    }

    public MachineStructureBuilderJS fullStructure(BlockArray pattern, PortRequirementSpec ports,
            PortTierRequirementSpec tiers, List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifiers, Map<BlockPos, Identifier> levels) {
        applyPendingPatternMetadata();
        BlockArray fullPattern = Objects.requireNonNull(pattern);
        declarations.add(new Declaration(Declaration.Kind.FULL, fullPattern, ports, tiers,
                dynamicPatterns, modifiers, validateLevelSlots(levels, fullPattern)));
        patternDeclaration = false;
        classMetadataChanged = false;
        return this;
    }

    public MachineStructureBuilderJS extension(BlockArray pattern) {
        return extension(pattern, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(), Map.of(), Map.of());
    }

    public MachineStructureBuilderJS extension(BlockArray pattern, PortRequirementSpec ports,
            PortTierRequirementSpec tiers, List<DynamicPatternSpec> dynamicPatterns,
            Map<BlockPos, List<SingleBlockModifierReplacement>> modifiers, Map<BlockPos, Identifier> levels) {
        if (declarations.isEmpty()) throw new IllegalStateException("extension requires a full structure first");
        applyPendingPatternMetadata();
        BlockArray extensionPattern = Objects.requireNonNull(pattern);
        declarations.add(new Declaration(Declaration.Kind.EXTENSION, extensionPattern, ports, tiers,
                dynamicPatterns, modifiers, validateLevelSlots(levels, extensionPattern)));
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

    public MachineStructureBuilderJS levelSlot(BlockPos pos, String typeId) {
        levelSlots.put(Objects.requireNonNull(pos, "pos"), validateLevelType(Identifier.parse(typeId)));
        classMetadataChanged = true;
        return this;
    }

    public MachineStructureBuilderJS dynamicPattern(DynamicPatternSpec pattern) {
        dynamicPatterns.add(Objects.requireNonNull(pattern, "pattern"));
        classMetadataChanged = true;
        return this;
    }

    @Override
    public MachineStructureDefinition createObject() {
        if (declarations.isEmpty()) {
            return new MachineStructureDefinition(id, List.of(new Declaration(Declaration.Kind.FULL, pattern,
                    portRequirements, portTierRequirements, dynamicPatterns, requirements,
                    modifierReplacements, validateLevelSlots(levelSlots, pattern))));
        }
        applyPendingPatternMetadata();
        if (!classMetadataChanged) return new MachineStructureDefinition(id, declarations);
        List<Declaration> result = new ArrayList<>(declarations);
        Declaration first = result.getFirst();
        result.set(0, new Declaration(first.kind(), first.pattern(), portRequirements, portTierRequirements,
                dynamicPatterns, requirements, modifierReplacements, validateLevelSlots(levelSlots, first.pattern())));
        return new MachineStructureDefinition(id, result);
    }

    public void build() {
        var transaction = KubeJSContentReloadTransaction.active();
        if (transaction == null) {
            throw new IllegalStateException("Machine structures must be built during KubeJS server script loading");
        }
        transaction.registerStructure(createObject());
    }

    private static BlockPredicate toPredicate(Object value) {
        return switch (value) {
            case String blockId -> new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId)));
            case Block block -> new BlockPredicate.OfBlock(block);
            case BlockState state -> new BlockPredicate.OfBlockState(state);
            case BlockPredicate predicate -> predicate;
            case LevelSlot levelSlot -> levelPredicate(levelSlot);
            default -> throw new IllegalArgumentException("Unknown pattern key value: " + value);
        };
    }

    private void applyPendingPatternMetadata() {
        if (!patternDeclaration || !classMetadataChanged || declarations.isEmpty()) return;
        Declaration first = declarations.getFirst();
        declarations.set(0, new Declaration(first.kind(), first.pattern(), portRequirements, portTierRequirements,
                dynamicPatterns, requirements, modifierReplacements, validateLevelSlots(levelSlots, first.pattern())));
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

    private static Map<BlockPos, Identifier> validateLevelSlots(Map<BlockPos, Identifier> levels) {
        Map<BlockPos, Identifier> result = new LinkedHashMap<>();
        Objects.requireNonNull(levels, "levels").forEach((pos, typeId) ->
                result.put(Objects.requireNonNull(pos, "level pos"), validateLevelType(typeId)));
        return result;
    }

    private static Map<BlockPos, Identifier> validateLevelSlots(Map<BlockPos, Identifier> levels, BlockArray pattern) {
        Map<BlockPos, Identifier> result = validateLevelSlots(levels);
        for (BlockPos pos : result.keySet()) {
            if (!pattern.pattern().containsKey(pos)) {
                throw new IllegalArgumentException("Level slot outside structure pattern: " + pos);
            }
        }
        return result;
    }

    private static Identifier validateLevelType(Identifier typeId) {
        Objects.requireNonNull(typeId, "typeId");
        if (MachineLevelRegistry.getType(typeId) == null) {
            throw new IllegalArgumentException("Unknown machine level type: " + typeId);
        }
        return typeId;
    }
}
