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
import dev.latvian.mods.rhino.util.HideFromJS;
import dev.latvian.mods.kubejs.registry.BuilderBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
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
    private final BlockArray.Builder sliceBuilder = new BlockArray.Builder();
    private final MachineStructureRequirements.Builder sliceRequirements = MachineStructureRequirements.builder();
    private boolean patternMetadataPending;
    private boolean classMetadataChanged;
    private boolean slicePatternPending;
    private StructureApiMode structureApiMode;
    private boolean stateSensitive;

    public MachineStructureBuilderJS(Identifier id) {
        super(id);
        sliceBuilder.noController();
    }

    public MachineStructureBuilderJS(String id) {
        this(Identifier.parse(id));
    }

    public MachineStructureBuilderJS stateSensitive() {
        stateSensitive = true;
        return this;
    }

    public MachineStructureBuilderJS stateInsensitive() {
        stateSensitive = false;
        return this;
    }

    public MachineStructureBuilderJS pattern(String... rows) {
        selectTopLevelStructureApi();
        sliceBuilder.pattern(rows);
        slicePatternPending = true;
        return this;
    }

    @HideFromJS
    public MachineStructureBuilderJS pattern(List<String> rows) {
        return pattern(rows.toArray(String[]::new));
    }

    public MachineStructureBuilderJS patternAll(List<List<String>> slices) {
        if (slices.isEmpty()) {
            throw new IllegalArgumentException("patternAll(...) must contain at least one slice");
        }
        for (List<String> slice : slices) {
            pattern(slice);
        }
        return this;
    }

    public MachineStructureBuilderJS set(String symbol, Object value) {
        selectTopLevelStructureApi();
        if (symbol == null || symbol.length() != 1 || symbol.charAt(0) == ' ') {
            throw new IllegalArgumentException("A pattern symbol must be exactly one non-space character");
        }
        char key = symbol.charAt(0);
        if (!sliceBuilder.containsSymbol(key)) {
            throw new IllegalStateException("Pattern symbol is absent from the current pattern: " + key);
        }
        PatternEntry entry = toPatternEntry(value);
        sliceBuilder.set(key, entry.base());
        if (value instanceof LevelSlot levelSlot) {
            sliceRequirements.levelSlot(key, validateLevelType(levelSlot.typeId()));
        }
        return this;
    }

    public MachineStructureBuilderJS modifier(String symbol, ModifierUse use) {
        selectTopLevelStructureApi();
        if (symbol == null || symbol.length() != 1 || symbol.charAt(0) == ' ') {
            throw new IllegalArgumentException("A pattern symbol must be exactly one non-space character");
        }
        Objects.requireNonNull(use, "use");
        sliceRequirements.modifier(symbol.charAt(0), use.modifierId(), toInternalBlockPredicate(use.replacement()));
        return this;
    }

    public MachineStructureBuilderJS controller(String symbol) {
        selectTopLevelStructureApi();
        if (symbol == null || symbol.length() != 1 || symbol.charAt(0) == ' ') {
            throw new IllegalArgumentException("A controller symbol must be exactly one non-space character");
        }
        char key = symbol.charAt(0);
        if (!sliceBuilder.containsSymbol(key)) {
            throw new IllegalStateException("Pattern symbol is absent from the current pattern: " + key);
        }
        sliceBuilder.set(key, new BlockPredicate.OfBlock(ModBlocks.controllerFor(id).get()));
        sliceBuilder.controller(key);
        return this;
    }

    private void selectTopLevelStructureApi() {
        if (structureApiMode == StructureApiMode.CALLBACK) {
            throw new IllegalStateException("Callback and top-level structure APIs cannot be mixed");
        }
        structureApiMode = StructureApiMode.TOP_LEVEL;
    }

    private void selectCallbackStructureApi() {
        if (structureApiMode == StructureApiMode.TOP_LEVEL) {
            throw new IllegalStateException("Callback and top-level structure APIs cannot be mixed");
        }
        structureApiMode = StructureApiMode.CALLBACK;
    }

    public MachineStructureBuilderJS fullStructure(PortRequirementSpec ports,
            PortTierRequirementSpec tiers, List<DynamicPatternSpec> dynamicPatterns,
            MachineStructureRequirements requirements) {
        selectTopLevelStructureApi();
        syncSlicePattern();
        MachineStructureRequirements explicitRequirements = requirements == null
                ? MachineStructureRequirements.EMPTY : requirements;
        MachineStructureRequirements combinedRequirements = MachineStructureRequirements.merge(
                this.requirements, explicitRequirements, 0);
        this.requirements = combinedRequirements;
        declarations.add(new Declaration(Declaration.Kind.FULL, pattern, ports, tiers, dynamicPatterns,
                combinedRequirements, stateSensitive));
        patternMetadataPending = false;
        classMetadataChanged = false;
        return this;
    }

    public MachineStructureBuilderJS fullStructure(BlockArray pattern) {
        return fullStructure(pattern, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(),
                MachineStructureRequirements.EMPTY);
    }

    public MachineStructureBuilderJS fullStructure(BlockArray pattern, PortRequirementSpec ports,
            PortTierRequirementSpec tiers, List<DynamicPatternSpec> dynamicPatterns,
            MachineStructureRequirements requirements) {
        selectTopLevelStructureApi();
        syncSlicePattern();
        appendPendingPatternDeclaration();
        applyPendingPatternMetadata();
        BlockArray fullPattern = Objects.requireNonNull(pattern);
        declarations.add(new Declaration(Declaration.Kind.FULL, fullPattern, ports, tiers,
                dynamicPatterns, requirements));
        patternMetadataPending = false;
        classMetadataChanged = false;
        return this;
    }

    public MachineStructureBuilderJS mainStructure(Consumer<MachineStructureStageBuilderJS> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (!declarations.isEmpty()) {
            if (structureApiMode == StructureApiMode.TOP_LEVEL) {
                throw new IllegalStateException("Callback and top-level structure APIs cannot be mixed");
            }
            throw new IllegalStateException("mainStructure can only be declared once");
        }
        selectCallbackStructureApi();
        declarations.add(stageDeclaration(consumer, Declaration.Kind.FULL));
        return this;
    }

    public MachineStructureBuilderJS expandStructure(Consumer<MachineStructureStageBuilderJS> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (declarations.isEmpty() || declarations.getFirst().kind() != Declaration.Kind.FULL) {
            throw new IllegalStateException("expandStructure requires a full structure first");
        }
        selectCallbackStructureApi();
        declarations.add(stageDeclaration(consumer, Declaration.Kind.FULL));
        return this;
    }

    @HideFromJS
    public MachineStructureBuilderJS extension(BlockArray pattern) {
        return extension(pattern, PortRequirementSpec.none(), PortTierRequirementSpec.none(), List.of(),
                MachineStructureRequirements.EMPTY);
    }

    @HideFromJS
    public MachineStructureBuilderJS extension(BlockArray pattern, PortRequirementSpec ports,
            PortTierRequirementSpec tiers, List<DynamicPatternSpec> dynamicPatterns,
            MachineStructureRequirements requirements) {
        selectTopLevelStructureApi();
        syncSlicePattern();
        appendPendingPatternDeclaration();
        if (declarations.isEmpty()) throw new IllegalStateException("extension requires a full structure first");
        applyPendingPatternMetadata();
        BlockArray extensionPattern = Objects.requireNonNull(pattern);
        declarations.add(new Declaration(Declaration.Kind.EXTENSION, extensionPattern, ports, tiers,
                dynamicPatterns, requirements));
        return this;
    }

    public MachineStructureBuilderJS extension(Consumer<MachineStructureStageBuilderJS> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        if (declarations.isEmpty()) throw new IllegalStateException("extension requires a full structure first");
        selectCallbackStructureApi();
        declarations.add(stageDeclaration(consumer, Declaration.Kind.EXTENSION));
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

    public PortTierRequirementSpec itemInputTier(String id) { return KubeJSInterfaceHelpers.itemInputTier(id); }
    public PortTierRequirementSpec itemOutputTier(String id) { return KubeJSInterfaceHelpers.itemOutputTier(id); }
    public PortTierRequirementSpec fluidInputTier(String id) { return KubeJSInterfaceHelpers.fluidInputTier(id); }
    public PortTierRequirementSpec fluidOutputTier(String id) { return KubeJSInterfaceHelpers.fluidOutputTier(id); }
    public PortTierRequirementSpec energyInputTier(String id) { return KubeJSInterfaceHelpers.energyInputTier(id); }
    public PortTierRequirementSpec energyOutputTier(String id) { return KubeJSInterfaceHelpers.energyOutputTier(id); }

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
    public BlockPredicate anyOfUpgradeBus() { return KubeJSInterfaceHelpers.anyOfUpgradeBus(); }
    public BlockPredicate anyOfPort(String... ids) { return KubeJSInterfaceHelpers.anyOfPort(ids); }
    public BlockPredicate anyOfPort(Identifier... ids) { return KubeJSInterfaceHelpers.anyOfPort(ids); }
    public BlockPredicate anyOfPort(cn.howxu.mmcr.api.publicapi.machine.BlockPredicate... predicates) {
        return KubeJSInterfaceHelpers.anyOfPort(predicates);
    }
    public BlockPredicate factoryController() { return anyOfPort("factory_controller"); }
    public BlockPredicate parallelControllers() { return KubeJSInterfaceHelpers.parallelControllers(); }
    public BlockPredicate smartInterface() { return KubeJSInterfaceHelpers.smartInterface(); }
    public BlockPredicate dataStorage() { return KubeJSInterfaceHelpers.dataStorage(); }

    @Override
    public MachineStructureDefinition createObject() {
        syncSlicePattern();
        if (declarations.isEmpty()) {
            return new MachineStructureDefinition(id, List.of(new Declaration(Declaration.Kind.FULL, pattern,
                    portRequirements, portTierRequirements, dynamicPatterns, requirements, stateSensitive)));
        }
        applyPendingPatternMetadata();
        List<Declaration> result = declarations.stream()
                .map(declaration -> new Declaration(declaration.kind(), declaration.pattern(),
                        declaration.portRequirements(), declaration.portTierRequirements(), declaration.dynamicPatterns(),
                        declaration.requirements(), stateSensitive))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (!classMetadataChanged) return new MachineStructureDefinition(id, result);
        Declaration first = result.getFirst();
        result.set(0, new Declaration(first.kind(), first.pattern(), portRequirements, portTierRequirements,
                dynamicPatterns, requirements, stateSensitive));
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
                    new BlockPredicate.OfBlock(BuiltInRegistries.BLOCK.getValue(Identifier.parse(blockId))));
            case Block block -> new PatternEntry(new BlockPredicate.OfBlock(block));
            case BlockState state -> new PatternEntry(new BlockPredicate.OfBlockState(state));
            case BlockPredicate predicate -> new PatternEntry(predicate);
            case LevelSlot levelSlot -> new PatternEntry(levelPredicate(levelSlot));
            default -> throw new IllegalArgumentException("Unknown pattern key value: " + value);
        };
    }

    static BlockPredicate toInternalBlockPredicate(
            cn.howxu.mmcr.api.publicapi.machine.BlockPredicate predicate) {
        Objects.requireNonNull(predicate, "replacement");
        if (predicate.isMachineCoupler()) return BlockPredicate.machineCoupler();
        if (predicate.blockState().isPresent()) {
            return new BlockPredicate.OfBlockState(predicate.blockState().get());
        }
        if (predicate.block().isPresent()) {
            return new BlockPredicate.OfBlock(predicate.block().get());
        }
        if (predicate.blockSupplier().isPresent()) {
            return new BlockPredicate.DeferredBlock(predicate.blockSupplier().get());
        }
        if (predicate.tag().isPresent()) {
            return new BlockPredicate.OfTag(predicate.tag().get());
        }
        return new BlockPredicate.AnyOf(predicate.alternatives().stream()
                .map(MachineStructureBuilderJS::toInternalBlockPredicate).toList());
    }

    private void applyPendingPatternMetadata() {
        if (!patternMetadataPending || !classMetadataChanged || declarations.isEmpty()) return;
        Declaration first = declarations.getFirst();
        declarations.set(0, new Declaration(first.kind(), first.pattern(), portRequirements, portTierRequirements,
                dynamicPatterns, requirements));
        classMetadataChanged = false;
    }

    private void appendPendingPatternDeclaration() {
        if (!patternMetadataPending || !declarations.isEmpty()) return;
        declarations.add(new Declaration(Declaration.Kind.FULL, pattern, portRequirements,
                portTierRequirements, dynamicPatterns, requirements, stateSensitive));
        patternMetadataPending = false;
        classMetadataChanged = false;
    }

    private void syncSlicePattern() {
        if (!slicePatternPending) return;
        pattern = sliceBuilder.build();
        requirements = sliceRequirements.build(pattern);
        slicePatternPending = false;
        declarations.clear();
        patternMetadataPending = true;
    }

    private Declaration stageDeclaration(Consumer<MachineStructureStageBuilderJS> consumer,
            Declaration.Kind kind) {
        Objects.requireNonNull(consumer, "consumer");
        MachineStructureStageBuilderJS stage = new MachineStructureStageBuilderJS(id);
        consumer.accept(stage);
        Declaration declaration = stage.build();
        return new Declaration(kind, declaration.pattern(), declaration.portRequirements(),
                declaration.portTierRequirements(), declaration.dynamicPatterns(), declaration.requirements());
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

    private enum StructureApiMode {
        TOP_LEVEL,
        CALLBACK
    }

    /**
     * Character-key structure declaration value carrying a base predicate.
     *
     * @author howxu <dev@howxu.cn>
     */
    public record PatternEntry(BlockPredicate base) {
        public PatternEntry {
            Objects.requireNonNull(base, "base");
        }
    }
}
