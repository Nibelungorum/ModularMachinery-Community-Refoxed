package cn.howxu.mmcr.internal.api.facade.structure;

import cn.howxu.mmcr.api.machine.definition.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.DisplayStack;
import cn.howxu.mmcr.api.machine.definition.MachineLevel;
import cn.howxu.mmcr.api.machine.definition.MachineStructureBuilder;
import cn.howxu.mmcr.api.machine.definition.MachineStructureDefinition;
import cn.howxu.mmcr.api.machine.definition.ModifierUse;
import cn.howxu.mmcr.api.machine.definition.PatternBuilder;
import cn.howxu.mmcr.api.machine.definition.PatternDefinition;
import cn.howxu.mmcr.api.machine.definition.PortRequirements;
import cn.howxu.mmcr.api.machine.definition.PortTiers;
import cn.howxu.mmcr.api.machine.definition.StructureRequirements;
import cn.howxu.mmcr.api.machine.definition.StructureStage;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.internal.api.facade.recipe.ModifierAdapters;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierBundle;
import cn.howxu.mmcr.publicapi.structure.BlockCondition;
import cn.howxu.mmcr.publicapi.structure.PatternDraft;
import cn.howxu.mmcr.publicapi.structure.PatternSpec;
import cn.howxu.mmcr.publicapi.structure.PortLimits;
import cn.howxu.mmcr.publicapi.structure.PortTierLimits;
import cn.howxu.mmcr.publicapi.structure.StructureConstraints;
import cn.howxu.mmcr.publicapi.structure.StructureDraft;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.structure.StructureStageDraft;
import cn.howxu.mmcr.publicapi.structure.StructureStageSpec;
import cn.howxu.mmcr.publicapi.structure.level.LevelTypeSpec;
import cn.howxu.mmcr.publicapi.structure.level.MachineLevelSpec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Typed internal boundary for structure declarations and event registration.
 * @author howxu <dev@howxu.cn>
 */
public final class StructureAdapters {
    private StructureAdapters() {}

    public static StructureDraft structure() { return new Draft(MachineStructureBuilder.structure()); }
    public static StructureDraft wrap(MachineStructureBuilder value) { return new Draft(Objects.requireNonNull(value)); }
    public static PatternDraft pattern() { return new PatternOptions(PatternBuilder.pattern()); }
    public static StructureStageDraft stage() { return new StageOptions(StructureStage.builder()); }
    public static PortLimits noPorts() { return wrap(PortRequirements.none()); }
    public static PortLimits.Builder ports() { return new PortOptions(PortRequirements.builder()); }
    public static StructureConstraints emptyConstraints() { return wrap(StructureRequirements.EMPTY); }
    public static StructureConstraints.Builder constraints() { return new ConstraintOptions(StructureRequirements.builder()); }
    public static LevelTypeSpec levelType(ResourceLocation id, Component name) { return wrap(new LevelType(id, name)); }
    public static MachineLevelSpec level(ResourceLocation id, ResourceLocation typeId, int priority, BlockCondition condition,
            ItemStack representative, ModifierBundle modifier) {
        return wrap(new MachineLevel(id, typeId, priority, unwrap(condition), DisplayStack.of(representative),
                ModifierAdapters.unwrap(modifier)));
    }

    public static StructureSpec wrap(MachineStructureDefinition value) { return new StructureView(Objects.requireNonNull(value)); }
    public static MachineStructureDefinition unwrap(StructureSpec value) { return ((StructureView) value).value; }
    public static PatternSpec wrap(PatternDefinition value) { return new PatternView(Objects.requireNonNull(value)); }
    public static PatternDefinition unwrap(PatternSpec value) { return ((PatternView) value).value; }
    public static StructureStageSpec wrap(StructureStage value) { return new StageView(Objects.requireNonNull(value)); }
    public static StructureStage unwrap(StructureStageSpec value) { return ((StageView) value).value; }
    public static BlockCondition wrap(BlockPredicate value) { return new ConditionView(Objects.requireNonNull(value)); }
    public static BlockCondition wrap(cn.howxu.mmcr.api.machine.BlockPredicate value) { return RuntimeBlockAdapters.wrap(value); }
    public static BlockPredicate unwrap(BlockCondition value) {
        if (value instanceof ConditionView view) return view.value;
        return RuntimeBlockAdapters.toDeclaration(RuntimeBlockAdapters.unwrap(value));
    }
    /** Preserves the original matcher; only runtime-backed conditions have a runtime handle. */
    public static cn.howxu.mmcr.api.machine.BlockPredicate unwrapRuntime(BlockCondition value) {
        return RuntimeBlockAdapters.unwrap(value);
    }
    public static PortLimits wrap(PortRequirements value) { return new PortView(Objects.requireNonNull(value)); }
    public static PortRequirements unwrap(PortLimits value) { return ((PortView) value).value; }
    public static PortTierLimits wrap(PortTiers value) { return TierAdapters.wrap(value); }
    public static PortTiers unwrap(PortTierLimits value) { return TierAdapters.unwrap(value); }
    public static StructureConstraints wrap(StructureRequirements value) { return new ConstraintView(Objects.requireNonNull(value)); }
    public static StructureRequirements unwrap(StructureConstraints value) { return ((ConstraintView) value).value; }
    public static LevelTypeSpec wrap(LevelType value) { return new TypeView(Objects.requireNonNull(value)); }
    public static LevelType unwrap(LevelTypeSpec value) { return ((TypeView) value).value; }
    public static MachineLevelSpec wrap(MachineLevel value) { return new LevelView(Objects.requireNonNull(value)); }
    public static MachineLevelSpec wrap(cn.howxu.mmcr.api.machine.level.MachineLevel value) {
        return new RuntimeLevelView(Objects.requireNonNull(value));
    }
    /** Converts runtime predicates only when they are representable without losing semantics. */
    public static MachineLevel unwrap(MachineLevelSpec value) {
        if (value instanceof LevelView view) return view.value;
        var runtime = ((RuntimeLevelView) value).value;
        return new MachineLevel(runtime.id(), runtime.typeId(), runtime.priority(),
                RuntimeBlockAdapters.toDeclaration(runtime.statePredicate()), DisplayStack.of(runtime.representative()),
                runtime.modifier());
    }
    /** Typed registration boundary: preserve runtime handles or compile real declarations. */
    public static cn.howxu.mmcr.api.machine.level.MachineLevel unwrapRuntime(MachineLevelSpec value) {
        if (value instanceof RuntimeLevelView view) return view.value;
        return MachineDefinitionConverter.toMachineLevel(unwrap(value));
    }

    /** Core-backed structure builder.
     * @author howxu <dev@howxu.cn>
     */
    private static final class Draft implements StructureDraft {
        private final MachineStructureBuilder value;
        private Draft(MachineStructureBuilder value) { this.value = value; }
        public StructureDraft stateSensitive() { value.stateSensitive(); return this; }
        public StructureDraft stateInsensitive() { value.stateInsensitive(); return this; }
        public StructureDraft fullStructure(Consumer<StructureStageDraft> configure) {
            Objects.requireNonNull(configure, "configure");
            value.fullStructure(builder -> { configure.accept(new StageOptions(builder)); return builder; });
            return this;
        }
        public StructureDraft expandStructure(Consumer<StructureStageDraft> configure) {
            Objects.requireNonNull(configure, "configure");
            value.expandStructure(builder -> { configure.accept(new StageOptions(builder)); return builder; });
            return this;
        }
        public StructureDraft extension(Consumer<StructureStageDraft> configure) {
            Objects.requireNonNull(configure, "configure");
            value.extension(builder -> { configure.accept(new StageOptions(builder)); return builder; });
            return this;
        }
        public StructureDraft singlePattern(Consumer<PatternDraft> configure) {
            return fullStructure(stage -> stage.pattern(configure));
        }
        public StructureSpec build(ResourceLocation machineId) { return wrap(value.build(machineId)); }
    }

    /** Core-backed pattern builder.
     * @author howxu <dev@howxu.cn>
     */
    private static final class PatternOptions implements PatternDraft {
        private final PatternBuilder value;
        private PatternOptions(PatternBuilder value) { this.value = value; }
        public PatternDraft layer(String... rows) { value.layer(rows); return this; }
        public PatternDraft pattern(String... rows) { value.pattern(rows); return this; }
        public PatternDraft where(char symbol, BlockCondition condition) { value.where(symbol, unwrap(condition)); return this; }
        public PatternDraft controller(char symbol) { value.controller(symbol); return this; }
        public PatternSpec build() { return wrap(value.build()); }
    }

    /** Core-backed stage builder; core owns replacement versus accumulation semantics.
     * @author howxu <dev@howxu.cn>
     */
    private static final class StageOptions implements StructureStageDraft {
        private final StructureStage.Builder value;
        private StageOptions(StructureStage.Builder value) { this.value = value; }
        public StructureStageDraft full() { value.full(); return this; }
        public StructureStageDraft expansion() { value.expansion(); return this; }
        public StructureStageDraft extension() { value.extension(); return this; }
        public StructureStageDraft pattern(Consumer<PatternDraft> configure) {
            Objects.requireNonNull(configure, "configure");
            value.pattern(builder -> { configure.accept(new PatternOptions(builder)); return builder; });
            return this;
        }
        public StructureStageDraft ports(Consumer<PortLimits.Builder> configure) {
            Objects.requireNonNull(configure, "configure");
            value.ports(builder -> { configure.accept(new PortOptions(builder)); return builder; });
            return this;
        }
        public StructureStageDraft portTiers(Consumer<PortTierLimits.Builder> configure) {
            Objects.requireNonNull(configure, "configure");
            value.portTiers(builder -> { configure.accept(TierAdapters.builder(builder)); return builder; });
            return this;
        }
        public StructureStageDraft requirements(Consumer<StructureConstraints.Builder> configure) {
            Objects.requireNonNull(configure, "configure");
            value.requirements(builder -> { configure.accept(new ConstraintOptions(builder)); return builder; });
            return this;
        }
        public StructureStageDraft modifier(char symbol, ResourceLocation modifierId, BlockCondition replacement) {
            value.modifier(symbol, ModifierUse.of(modifierId, unwrap(replacement))); return this;
        }
        public StructureStageSpec build() { return wrap(value.build()); }
    }

    /** Core-backed count builder.
     * @author howxu <dev@howxu.cn>
     */
    private static final class PortOptions implements PortLimits.Builder {
        private final PortRequirements.Builder value;
        private PortOptions(PortRequirements.Builder value) { this.value = value; }
        public PortLimits.Builder min(String id, int min) { value.min(id, min); return this; }
        public PortLimits.Builder range(String id, int min, int max) { value.range(id, min, max); return this; }
        public PortLimits build() { return wrap(value.build()); }
    }

    /** Core-backed constraint builder.
     * @author howxu <dev@howxu.cn>
     */
    private static final class ConstraintOptions implements StructureConstraints.Builder {
        private final StructureRequirements.Builder value;
        private ConstraintOptions(StructureRequirements.Builder value) { this.value = value; }
        public StructureConstraints.Builder modifier(char symbol, ResourceLocation id) { value.modifier(symbol, id); return this; }
        public StructureConstraints.Builder modifier(char symbol, ResourceLocation id, BlockCondition replacement) {
            value.modifier(symbol, ModifierUse.of(id, unwrap(replacement))); return this;
        }
        public StructureConstraints.Builder levelSlot(char symbol, ResourceLocation typeId) { value.levelSlot(symbol, typeId); return this; }
        public StructureConstraints build() { return wrap(value.build()); }
    }

    /** Structure declaration view.
     * @author howxu <dev@howxu.cn>
     */
    private static final class StructureView implements StructureSpec {
        private final MachineStructureDefinition value;
        private StructureView(MachineStructureDefinition value) { this.value = value; }
        public ResourceLocation machineId() { return value.machineId(); }
        public List<StructureStageSpec> stages() { return value.stages().stream().map(StructureAdapters::wrap).toList(); }
        public boolean stateSensitive() { return value.stateSensitive(); }
    }

    /** Pattern declaration view.
     * @author howxu <dev@howxu.cn>
     */
    private static final class PatternView implements PatternSpec {
        private final PatternDefinition value;
        private PatternView(PatternDefinition value) { this.value = value; }
        public List<List<String>> layers() { return value.layers(); }
        public Map<Character, BlockCondition> predicates() {
            Map<Character, BlockCondition> result = new LinkedHashMap<>();
            value.predicates().forEach((symbol, predicate) -> result.put(symbol, wrap(predicate)));
            return Collections.unmodifiableMap(result);
        }
        public char controllerSymbol() { return value.controllerSymbol(); }
        public int width() { return value.width(); }
        public int height() { return value.height(); }
        public int depth() { return value.depth(); }
    }

    /** Stage declaration view.
     * @author howxu <dev@howxu.cn>
     */
    private static final class StageView implements StructureStageSpec {
        private final StructureStage value;
        private StageView(StructureStage value) { this.value = value; }
        public Kind kind() {
            return switch (value.kind()) {
                case FULL -> Kind.FULL;
                case EXPANSION -> Kind.EXPANSION;
                case EXTENSION -> Kind.EXTENSION;
            };
        }
        public PatternSpec pattern() { return wrap(value.pattern()); }
        public PortLimits portRequirements() { return wrap(value.portRequirements()); }
        public PortTierLimits portTiers() { return wrap(value.portTiers()); }
        public StructureConstraints requirements() { return wrap(value.requirements()); }
    }

    /** Condition declaration view with the core's value semantics.
     * @author howxu <dev@howxu.cn>
     */
    private static final class ConditionView implements BlockCondition {
        private final BlockPredicate value;
        private ConditionView(BlockPredicate value) { this.value = value; }
        public boolean isMachineCoupler() { return value.isMachineCoupler(); }
        public boolean isAny() { return false; }
        public boolean isAir() { return false; }
        public boolean isNetworkInterface() { return false; }
        public Optional<Block> block() { return value.block(); }
        public Optional<BlockState> blockState() { return value.blockState(); }
        public Optional<Supplier<? extends Block>> blockSupplier() { return value.blockSupplier(); }
        public Optional<TagKey<Block>> tag() { return value.tag(); }
        public List<BlockCondition> alternatives() { return value.alternatives().stream().map(StructureAdapters::wrap).toList(); }
        @Override public boolean equals(Object other) { return other instanceof ConditionView view && value.equals(view.value); }
        @Override public int hashCode() { return value.hashCode(); }
    }

    /** Count declaration view.
     * @author howxu <dev@howxu.cn>
     */
    private static final class PortView implements PortLimits {
        private final PortRequirements value;
        private PortView(PortRequirements value) { this.value = value; }
        public Map<String, CountRangeView> requirements() {
            Map<String, CountRangeView> result = new LinkedHashMap<>();
            value.requirements().forEach((id, range) -> result.put(id, new CountView(range)));
            return Collections.unmodifiableMap(result);
        }
    }

    /** Count range view.
     * @author howxu <dev@howxu.cn>
     */
    private static final class CountView implements PortLimits.CountRangeView {
        private final PortRequirements.CountRange value;
        private CountView(PortRequirements.CountRange value) { this.value = value; }
        public int min() { return value.min(); }
        public OptionalInt max() { return value.max(); }
    }

    /** Constraint declaration view.
     * @author howxu <dev@howxu.cn>
     */
    private static final class ConstraintView implements StructureConstraints {
        private final StructureRequirements value;
        private ConstraintView(StructureRequirements value) { this.value = value; }
        public Map<Character, List<ModifierPlacementView>> modifierReplacements() {
            Map<Character, List<ModifierPlacementView>> result = new LinkedHashMap<>();
            value.modifierReplacements().forEach((symbol, uses) -> result.put(symbol,
                    uses.stream().<ModifierPlacementView>map(PlacementView::new).toList()));
            return Collections.unmodifiableMap(result);
        }
        public Map<Character, ResourceLocation> levelSlots() { return value.levelSlots(); }
    }

    /** Modifier placement view.
     * @author howxu <dev@howxu.cn>
     */
    private static final class PlacementView implements StructureConstraints.ModifierPlacementView {
        private final ModifierUse value;
        private PlacementView(ModifierUse value) { this.value = value; }
        public ResourceLocation modifierId() { return value.modifierId(); }
        public BlockCondition replacement() { return wrap(value.replacement()); }
    }

    /** Canonical level category view.
     * @author howxu <dev@howxu.cn>
     */
    private static final class TypeView implements LevelTypeSpec {
        private final LevelType value;
        private TypeView(LevelType value) { this.value = value; }
        public ResourceLocation id() { return value.id(); }
        public Component displayName() { return value.displayName(); }
    }

    /** Level declaration view; real runtime compilation remains in the converter.
     * @author howxu <dev@howxu.cn>
     */
    private static final class LevelView implements MachineLevelSpec {
        private final MachineLevel value;
        private LevelView(MachineLevel value) { this.value = value; }
        public ResourceLocation id() { return value.id(); }
        public ResourceLocation typeId() { return value.typeId(); }
        public int priority() { return value.priority(); }
        public BlockCondition statePredicate() { return wrap(value.statePredicate()); }
        public ItemStack representative() { return value.representative().stack(); }
        public ModifierBundle modifier() { return ModifierAdapters.wrap(value.modifier()); }
    }

    /** Canonical runtime level view, including levels registered directly by KJS.
     * @author howxu <dev@howxu.cn>
     */
    private static final class RuntimeLevelView implements MachineLevelSpec {
        private final cn.howxu.mmcr.api.machine.level.MachineLevel value;
        private RuntimeLevelView(cn.howxu.mmcr.api.machine.level.MachineLevel value) { this.value = value; }
        public ResourceLocation id() { return value.id(); }
        public ResourceLocation typeId() { return value.typeId(); }
        public int priority() { return value.priority(); }
        public BlockCondition statePredicate() { return wrap(value.statePredicate()); }
        public ItemStack representative() { return value.representative(); }
        public ModifierBundle modifier() { return ModifierAdapters.wrap(value.modifier()); }
    }
}
