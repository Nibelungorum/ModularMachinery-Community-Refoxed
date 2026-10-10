package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.internal.api.facade.structure.StructureAdapters;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.internal.api.facade.recipe.ModifierAdapters;
import cn.howxu.mmcr.api.machine.BlockPredicate;
import cn.howxu.mmcr.api.machine.definition.MachineStructureBuilder;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.definition.InterfaceTiers;
import cn.howxu.mmcr.api.machine.definition.PortTiers;
import cn.howxu.mmcr.api.machine.definition.ModifierDefinition;
import cn.howxu.mmcr.api.machine.level.MachineLevel;
import cn.howxu.mmcr.api.machine.level.LevelType;
import cn.howxu.mmcr.api.registration.StructureRegistration;
import cn.howxu.mmcr.publicapi.structure.BlockConditions;
import cn.howxu.mmcr.publicapi.structure.PortLimits;
import cn.howxu.mmcr.publicapi.structure.PortTierLimits;
import cn.howxu.mmcr.publicapi.structure.StructureStageSpec;
import cn.howxu.mmcr.publicapi.structure.StructureStageDraft;
import cn.howxu.mmcr.publicapi.structure.StructureDraft;
import cn.howxu.mmcr.publicapi.structure.StructureSpec;
import cn.howxu.mmcr.publicapi.structure.PatternDraft;
import cn.howxu.mmcr.publicapi.structure.PatternSpec;
import cn.howxu.mmcr.publicapi.structure.BlockCondition;
import cn.howxu.mmcr.publicapi.structure.StructureConstraints;
import cn.howxu.mmcr.publicapi.structure.level.Levels;
import cn.howxu.mmcr.publicapi.structure.level.LevelTypeSpec;
import cn.howxu.mmcr.publicapi.structure.level.MachineLevelSpec;
import cn.howxu.mmcr.publicapi.recipe.modifier.Modifiers;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierOperation;
import cn.howxu.mmcr.publicapi.recipe.modifier.ModifierScope;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies declaration behavior through the closed structure facade.
 * @author howxu <dev@howxu.cn>
 */
class PublicStructureFacadeTest {
    private static final Identifier MACHINE = Identifier.parse("mmcr:facade_structure");

    @BeforeAll
    static void bootstrapMinecraft() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void callbacks_run_immediately_and_requirements_accumulate_on_the_same_core_builder() {
        AtomicInteger callbacks = new AtomicInteger();
        Identifier first = Identifier.parse("mmcr:first");
        Identifier second = Identifier.parse("mmcr:second");
        var draft = Structures.structure().fullStructure(stage -> {
            callbacks.incrementAndGet();
            stage.pattern(pattern -> pattern.layer("CM").controller('C')
                    .where('M', BlockConditions.block(Blocks.STONE)));
            stage.requirements(r -> r.modifier('M', first, BlockConditions.block(Blocks.IRON_BLOCK)));
            stage.requirements(r -> r.modifier('M', second, BlockConditions.block(Blocks.GOLD_BLOCK)));
            stage.requirements(r -> r.levelSlot('M', first));
        });
        assertThat(callbacks).hasValue(1);
        var result = draft.build(MACHINE);
        var constraints = result.stages().getFirst().requirements();
        assertThat(constraints.modifierReplacements().get('M')).extracting(p -> p.modifierId())
                .containsExactly(first, second);
        assertThat(constraints.levelSlots()).containsEntry('M', first);
        assertThatThrownBy(() -> constraints.modifierReplacements().get('M').clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(StructureAdapters.wrap(StructureAdapters.unwrap(result)).machineId()).isEqualTo(MACHINE);
    }

    @Test
    void automatic_controller_binding_and_read_views_do_not_resolve_suppliers() {
        AtomicInteger calls = new AtomicInteger();
        var deferred = BlockConditions.deferredBlock(() -> { calls.incrementAndGet(); return Blocks.STONE; });
        var structure = Structures.structure().singlePattern(p -> p.layer("CB").controller('C').where('B', deferred))
                .build(MACHINE);
        var pattern = structure.stages().getFirst().pattern();
        assertThat(pattern.predicates().get('C').blockSupplier()).isPresent();
        assertThat(pattern.predicates().get('B').blockSupplier()).isPresent();
        assertThat(calls).hasValue(0);
        assertThatThrownBy(() -> pattern.layers().getFirst().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> pattern.predicates().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(pattern.predicates().get('B').blockSupplier().orElseThrow().get()).isEqualTo(Blocks.STONE);
        assertThat(calls).hasValue(1);
    }

    @Test
    void manual_controller_is_preserved_and_stage_kinds_are_distinct() {
        var structure = Structures.structure().stateSensitive()
                .fullStructure(s -> s.pattern(p -> p.layer("C").where('C', BlockConditions.block(Blocks.STONE)).controller('C')))
                .expandStructure(s -> s.pattern(p -> p.layer("C").controller('C')))
                .extension(s -> s.pattern(p -> p.layer("C").controller('C'))).build(MACHINE);
        assertThat(structure.stateSensitive()).isTrue();
        assertThat(structure.stages()).extracting(StructureStageSpec::kind).containsExactly(
                StructureStageSpec.Kind.FULL, StructureStageSpec.Kind.EXPANSION, StructureStageSpec.Kind.EXTENSION);
        assertThat(structure.stages().stream().map(stage -> MachineDefinitionConverter.toDeclaration(
                StructureAdapters.unwrap(stage)).kind().name()).toList()).containsExactly("FULL", "FULL", "EXTENSION");
        assertThat(structure.stages().getFirst().pattern().predicates().get('C').block()).contains(Blocks.STONE);
        assertThatThrownBy(() -> Structures.structure().expandStructure(s -> {})).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Structures.structure().extension(s -> s.pattern(p -> p.layer("C").controller('C')))
                .build(MACHINE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void counts_overwrite_while_tiers_append_and_combination_keeps_direction() {
        var counts = PortLimits.builder().range("item_input", 1, 3).min("item_input", 2).build();
        assertThat(counts.requirements().get("item_input").max()).isEmpty();
        var limits = PortTierLimits.combine(PortTierLimits.itemInput("SMALL"), null,
                PortTierLimits.energy(PortTierLimits.EnergyTier.ULTIMATE, IoDirection.OUTPUT));
        assertThat(limits.requirements()).extracting(PortTierLimits.RequirementView::ioType)
                .containsExactly(IoDirection.INPUT, IoDirection.OUTPUT);
        assertThat(limits.requirements()).extracting(PortTierLimits.RequirementView::minTierId)
                .containsExactly("small", "ultimate");
        assertThatThrownBy(() -> PortLimits.builder().range("item_input", 3, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chemical_facade_factories_preserve_tiers_and_directions_in_runtime_conversion() {
        for (var tier : PortTierLimits.ChemicalTier.values()) {
            var limits = PortTierLimits.combine(PortTierLimits.chemicalInput(tier),
                    PortTierLimits.chemicalOutput(tier.id()));
            assertThat(StructureAdapters.unwrap(limits)).isEqualTo(InterfaceTiers.chemical(tier.id()));
            assertThat(StructureAdapters.unwrap(PortTierLimits.chemical(tier.id())))
                    .isEqualTo(StructureAdapters.unwrap(limits));
            assertThat(StructureAdapters.unwrap(PortTierLimits.chemical(tier)))
                    .isEqualTo(StructureAdapters.unwrap(limits));
            assertThat(StructureAdapters.unwrap(PortTierLimits.chemical(tier, IoDirection.INPUT)))
                    .isEqualTo(InterfaceTiers.chemicalInput(tier.id()));
            assertThat(StructureAdapters.unwrap(PortTierLimits.chemicalOutput(tier)))
                    .isEqualTo(InterfaceTiers.chemicalOutput(tier.id()));
            assertThat(StructureAdapters.unwrap(PortTierLimits.chemicalInput(tier.id())))
                    .isEqualTo(InterfaceTiers.chemicalInput(tier.id()));
        }
        assertThatThrownBy(() -> PortTierLimits.chemical("normal")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortTierLimits.chemical(PortTierLimits.ChemicalTier.BASIC, null))
                .isInstanceOf(NullPointerException.class);

        var stage = Structures.stage().pattern(p -> p.layer("C").controller('C'))
                .portTiers(t -> t.minChemicalInput(PortTierLimits.ChemicalTier.ADVANCED)
                        .minChemicalOutput(PortTierLimits.ChemicalTier.ELITE)
                        .anyChemicalInput().anyChemicalOutput()
                        .anyRadioactiveChemicalInput().anyRadioactiveChemicalOutput()
                        .anyHeatInput().anyHeatOutput()).build();
        var runtime = MachineDefinitionConverter.toDeclaration(StructureAdapters.unwrap(stage));
        assertThat(runtime.portTierRequirements()).isEqualTo(PortTierRequirementSpec.from(PortTiers.builder()
                .minChemicalInput(PortTiers.ChemicalTier.ADVANCED).minChemicalOutput(PortTiers.ChemicalTier.ELITE)
                .anyChemicalInput().anyChemicalOutput().anyRadioactiveChemicalInput().anyRadioactiveChemicalOutput()
                .anyHeatInput().anyHeatOutput().build()));
        assertThat(stage.portTiers().requirements()).extracting(PortTierLimits.RequirementView::category)
                .containsExactly(PortTierLimits.PortCategory.CHEMICAL, PortTierLimits.PortCategory.CHEMICAL,
                        PortTierLimits.PortCategory.CHEMICAL, PortTierLimits.PortCategory.CHEMICAL,
                        PortTierLimits.PortCategory.RADIOACTIVE_CHEMICAL, PortTierLimits.PortCategory.RADIOACTIVE_CHEMICAL,
                        PortTierLimits.PortCategory.HEAT, PortTierLimits.PortCategory.HEAT);
    }

    @Test
    void single_tier_facade_factories_require_the_requested_directions() {
        assertThat(StructureAdapters.unwrap(PortTierLimits.radioactiveChemical()))
                .isEqualTo(InterfaceTiers.radioactiveChemical());
        assertThat(StructureAdapters.unwrap(PortTierLimits.heat())).isEqualTo(InterfaceTiers.heat());
        assertThat(StructureAdapters.unwrap(PortTierLimits.combine(PortTierLimits.radioactiveChemicalInput(),
                PortTierLimits.radioactiveChemicalOutput(), PortTierLimits.heatInput(), PortTierLimits.heatOutput())))
                .isEqualTo(PortTiers.combine(InterfaceTiers.radioactiveChemical(), InterfaceTiers.heat()));
        assertThat(StructureAdapters.unwrap(PortTierLimits.radioactiveChemical(IoDirection.OUTPUT)))
                .isEqualTo(InterfaceTiers.radioactiveChemicalOutput());
        assertThat(StructureAdapters.unwrap(PortTierLimits.heat(IoDirection.INPUT)))
                .isEqualTo(InterfaceTiers.heatInput());
        assertThatThrownBy(() -> PortTierLimits.heat(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PortTiers.Requirement(PortTiers.PortCategory.HEAT,
                IOType.INPUT, 1, "advanced")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void alternatives_are_recursive_readonly_views_and_keep_core_equality() {
        var condition = BlockConditions.any(BlockConditions.coupler(), BlockConditions.block(Blocks.STONE));
        assertThat(condition.alternatives().getFirst().isMachineCoupler()).isTrue();
        assertThat(condition.alternatives().get(1)).isEqualTo(BlockConditions.block(Blocks.STONE));
        assertThatThrownBy(() -> condition.alternatives().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BlockConditions.any()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void level_retains_exact_state_runtime_conversion_modifiers_and_defensive_stacks() {
        var state = Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, true);
        var bundle = Modifiers.bundle(Modifiers.numeric("parallelism", ModifierScope.MACHINE, 2,
                ModifierOperation.ADD, false));
        ItemStack original = new ItemStack(Items.FURNACE, 4);
        Identifier typeId = Identifier.parse("mmcr:facade_level_type");
        var type = Levels.type(typeId, Component.translatable("level.mmcr.facade"));
        assertThat(StructureAdapters.unwrap(type).id()).isEqualTo(typeId);
        var level = Levels.level(Identifier.parse("mmcr:facade_level"), typeId, 1,
                BlockConditions.blockState(state), original, bundle);
        original.setCount(1);
        level.representative().setCount(2);
        assertThat(level.representative().getCount()).isEqualTo(4);
        assertThat(level.statePredicate().blockState()).contains(state);
        assertThat(level.modifier().modifiers()).hasSize(1);
        var runtime = MachineDefinitionConverter.toMachineLevel(StructureAdapters.unwrap(level));
        assertThat(runtime.statePredicate().matches(state)).isTrue();
        assertThat(runtime.statePredicate().matches(state.setValue(BlockStateProperties.LIT, false))).isFalse();
        assertThat(runtime.modifier()).isSameAs(StructureAdapters.unwrap(level).modifier());
    }

    @Test
    void wrapped_structure_builder_is_the_original_callback_builder() {
        var core = MachineStructureBuilder.structure();
        var draft = StructureAdapters.wrap(core);
        draft.singlePattern(p -> p.layer("C").controller('C'));
        core.stateSensitive();
        assertThat(draft.build(MACHINE).stateSensitive()).isTrue();
        assertThat(core.build(MACHINE).stages()).hasSize(1);
        draft.stateInsensitive();
        assertThat(core.build(MACHINE).stateSensitive()).isFalse();
    }

    @Test
    void canonical_collector_levels_are_readable_without_a_public_source_including_kjs_registration() {
        Identifier typeId = Identifier.parse("mmcr:kjs_level_type");
        Identifier levelId = Identifier.parse("mmcr:kjs_level");
        var state = Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, true);
        var predicate = new BlockPredicate.OfBlockState(state);
        var modifiers = ModifierAdapters.unwrap(Modifiers.bundle(Modifiers.parallelized(true)));
        var input = new ItemStack(Items.FURNACE, 4);
        var runtime = new MachineLevel(levelId, typeId, 1, predicate, input, modifiers);
        var collector = new StructureRegistration(List.of());
        collector.registerLevelType(new LevelType(typeId, Component.translatable("level.mmcr.kjs")));
        collector.registerLevel(runtime);
        var view = StructureAdapters.wrap(collector.levels().get(levelId));
        input.setCount(1);
        runtime.representative().setCount(2);
        view.representative().setCount(3);
        assertThat(view.representative().getCount()).isEqualTo(4);
        assertThat(view.id()).isEqualTo(levelId);
        assertThat(view.typeId()).isEqualTo(typeId);
        assertThat(view.statePredicate().blockState()).contains(state);
        assertThat(StructureAdapters.unwrapRuntime(view)).isSameAs(runtime);
        assertThat(StructureAdapters.unwrapRuntime(view.statePredicate())).isSameAs(predicate);
        assertThat(ModifierAdapters.unwrap(view.modifier())).isSameAs(modifiers);
        assertThat(StructureAdapters.unwrap(view).modifier()).isSameAs(modifiers);
        var compiledAgain = MachineDefinitionConverter.toMachineLevel(StructureAdapters.unwrap(view));
        assertThat(compiledAgain.statePredicate().matches(state)).isTrue();
        assertThat(compiledAgain.statePredicate().matches(state.setValue(BlockStateProperties.LIT, false))).isFalse();
        var anotherCollector = new StructureRegistration(List.of());
        anotherCollector.registerLevelType(collector.levelTypes().get(typeId));
        anotherCollector.registerLevel(StructureAdapters.unwrapRuntime(view));
        assertThat(anotherCollector.levels().get(levelId)).isSameAs(runtime);
    }

    @Test
    void runtime_predicate_views_expose_special_variants_and_preserve_lazy_suppliers() {
        var any = StructureAdapters.wrap(new BlockPredicate.Any());
        var air = StructureAdapters.wrap(new BlockPredicate.Air());
        AtomicInteger calls = new AtomicInteger();
        var network = StructureAdapters.wrap(new BlockPredicate.DeferredBlock(
                () -> { calls.incrementAndGet(); return Blocks.STONE; }, true));
        var alternatives = StructureAdapters.wrap(new BlockPredicate.AnyOf(List.of(
                new BlockPredicate.OfBlock(Blocks.STONE), new BlockPredicate.Any(),
                new BlockPredicate.Air(), StructureAdapters.unwrapRuntime(network))));
        assertThat(any.isAny()).isTrue();
        assertThat(air.isAir()).isTrue();
        assertThat(network.isNetworkInterface()).isTrue();
        assertThat(network.blockSupplier()).isPresent();
        assertThat(alternatives.alternatives().getFirst().block()).contains(Blocks.STONE);
        assertThat(alternatives.alternatives().get(1).isAny()).isTrue();
        assertThat(alternatives.alternatives().get(2).isAir()).isTrue();
        assertThat(alternatives.alternatives().get(3).isNetworkInterface()).isTrue();
        assertThat(calls).hasValue(0);
        assertThatThrownBy(() -> alternatives.alternatives().clear()).isInstanceOf(UnsupportedOperationException.class);
        for (var condition : List.of(any, air, network, alternatives)) {
            assertThatThrownBy(() -> StructureAdapters.unwrap(condition)).isInstanceOf(UnsupportedOperationException.class);
        }
        var runtime = new MachineLevel(Identifier.parse("mmcr:unregistered_any"),
                Identifier.parse("mmcr:unregistered_type"), 1, StructureAdapters.unwrapRuntime(any),
                new ItemStack(Items.STONE), ModifierDefinition.EMPTY);
        var view = StructureAdapters.wrap(runtime);
        assertThat(view.statePredicate().isAny()).isTrue();
        assertThat(StructureAdapters.unwrapRuntime(view)).isSameAs(runtime);
        assertThatThrownBy(() -> StructureAdapters.unwrap(view)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void representable_runtime_conditions_convert_losslessly_without_resolving_deferred_blocks() {
        AtomicInteger calls = new AtomicInteger();
        var deferred = new BlockPredicate.DeferredBlock(() -> { calls.incrementAndGet(); return Blocks.STONE; });
        var tag = new BlockPredicate.OfTag(BlockTags.LOGS);
        var predicate = new BlockPredicate.AnyOf(List.of(new BlockPredicate.OfBlock(Blocks.STONE),
                new BlockPredicate.OfBlockState(Blocks.FURNACE.defaultBlockState()), deferred, tag,
                BlockPredicate.machineCoupler()));
        var view = StructureAdapters.wrap(predicate);
        var declaration = StructureAdapters.unwrap(view);
        assertThat(declaration.alternatives().get(2).blockSupplier()).contains(deferred.supplier());
        assertThat(view.alternatives().get(3).tag()).contains(BlockTags.LOGS);
        assertThat(view.alternatives().get(4).isMachineCoupler()).isTrue();
        assertThat(calls).hasValue(0);
        var declaredLevel = Levels.level(Identifier.parse("mmcr:converted_runtime"),
                Identifier.parse("mmcr:converted_type"), 1, view, new ItemStack(Items.STONE), Modifiers.bundle());
        var runtime = StructureAdapters.unwrapRuntime(declaredLevel);
        assertThat(runtime.statePredicate().children().get(2)).isEqualTo(deferred);
        assertThat(runtime.statePredicate().children().get(3)).isEqualTo(tag);
        assertThat(calls).hasValue(0);
        assertThatThrownBy(() -> StructureAdapters.unwrap(StructureAdapters.wrap(new BlockPredicate.AnyOf(List.of()))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void replacement_callbacks_use_fresh_builders_but_tier_entries_append() {
        var stage = Structures.stage()
                .pattern(p -> p.layer("CX").controller('C').where('X', BlockConditions.block(Blocks.STONE)))
                .pattern(p -> p.layer("C").controller('C'))
                .ports(p -> p.min("old", 1))
                .ports(p -> p.min("new", 1))
                .portTiers(t -> t.minItemInput(PortTierLimits.ItemTier.HUGE))
                .portTiers(t -> t.anyFluidInput().anyFluidOutput()).build();
        assertThat(stage.pattern().predicates()).doesNotContainKey('X');
        assertThat(stage.portRequirements().requirements()).containsOnlyKeys("new");
        assertThat(stage.portTiers().requirements()).extracting(PortTierLimits.RequirementView::category)
                .containsExactly(PortTierLimits.PortCategory.FLUID, PortTierLimits.PortCategory.FLUID);
        assertThatThrownBy(() -> Structures.pattern().layer("CC").controller('C').build())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> Structures.structure().fullStructure(s -> s.extension()
                .pattern(p -> p.layer("C").controller('C'))).build(MACHINE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void public_signatures_are_closed_including_nested_views_callbacks_and_generics() {
        List<Class<?>> contracts = List.of(Structures.class, StructureDraft.class, StructureSpec.class,
                PatternDraft.class, PatternSpec.class, StructureStageDraft.class, StructureStageSpec.class,
                BlockCondition.class, BlockConditions.class, PortLimits.class, PortTierLimits.class,
                StructureConstraints.class, Levels.class, LevelTypeSpec.class, MachineLevelSpec.class);
        contracts.forEach(PublicStructureFacadeTest::checkContract);
    }

    private static void checkContract(Class<?> contract) {
        for (Type parent : contract.getGenericInterfaces()) checkType(parent);
        for (var method : contract.getDeclaredMethods()) {
            checkType(method.getGenericReturnType());
            for (Type parameter : method.getGenericParameterTypes()) checkType(parameter);
        }
        for (Class<?> nested : contract.getDeclaredClasses()) checkContract(nested);
    }

    private static void checkType(Type type) {
        if (type instanceof Class<?> raw) {
            if (raw.isArray()) { checkType(raw.getComponentType()); return; }
            assertThat(raw.getName()).doesNotStartWith("cn.howxu.mmcr.api.")
                    .doesNotStartWith("cn.howxu.mmcr.internal.").doesNotStartWith("cn.howxu.mmcr.compat.");
            assertThat(raw).isNotEqualTo(Object.class);
        } else if (type instanceof ParameterizedType parameterized) {
            checkType(parameterized.getRawType());
            for (Type argument : parameterized.getActualTypeArguments()) checkType(argument);
        } else if (type instanceof GenericArrayType array) {
            checkType(array.getGenericComponentType());
        } else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) checkType(bound);
            for (Type bound : wildcard.getLowerBounds()) checkType(bound);
        }
    }
}
