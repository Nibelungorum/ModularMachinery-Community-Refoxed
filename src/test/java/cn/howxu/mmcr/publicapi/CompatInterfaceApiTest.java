package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.definition.PortTiers;
import cn.howxu.mmcr.api.machine.definition.InterfaceTiers;
import cn.howxu.mmcr.api.port.PortDefinition;
import cn.howxu.mmcr.compat.ars_nouveau.ArsSourceIds;
import cn.howxu.mmcr.compat.botania.BotaniaManaIds;
import cn.howxu.mmcr.compat.create.CreateBridgeBootstrap;
import cn.howxu.mmcr.compat.create.CreateRecipeTypes;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticCraftBridgeBootstrap;
import cn.howxu.mmcr.compat.pneumaticcraft.PneumaticIds;
import cn.howxu.mmcr.compat.kubejs.KubeJSApi;
import cn.howxu.mmcr.compat.kubejs.MachineBuilderJS;
import cn.howxu.mmcr.compat.kubejs.MachineStructureBuilderJS;
import cn.howxu.mmcr.compat.kubejs.MachineStructureStageBuilderJS;
import cn.howxu.mmcr.internal.api.facade.structure.TierAdapters;
import cn.howxu.mmcr.internal.api.facade.structure.StructureAdapters;
import cn.howxu.mmcr.internal.registration.MachineDefinitionConverter;
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.publicapi.structure.PortTierLimits;
import cn.howxu.mmcr.publicapi.recipe.IoDirection;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import dev.latvian.mods.rhino.type.TypeInfo;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies compatibility structure helpers against registered family and direction bindings.
 * @author howxu <dev@howxu.cn>
 */
class CompatInterfaceApiTest {
    private static final ResourceLocation ID = ResourceLocation.parse("test:compat_structure_api");

    @BeforeAll
    static void bootstrap() throws Exception { TestBootstrap.bootstrap(); }

    @Test
    void public_normal_tier_shortcuts_match_builder_constraints_and_validate_direction() {
        var expected = PortTiers.builder().anySourceInput().anySourceOutput().anyManaInput().anyManaOutput().build();
        for (var tiers : List.of(
                PortTierLimits.combine(PortTierLimits.sourceInput(), PortTierLimits.sourceOutput(),
                        PortTierLimits.manaInput(), PortTierLimits.manaOutput()),
                PortTierLimits.combine(PortTierLimits.sourceInput("NORMAL"), PortTierLimits.sourceOutput("normal"),
                        PortTierLimits.manaInput("normal"), PortTierLimits.manaOutput("NORMAL")))) {
            assertThat(TierAdapters.unwrap(tiers)).isEqualTo(expected);
            var requirement = PortTierRequirementSpec.from(TierAdapters.unwrap(tiers));
            List<IOPortKind> ports = List.of(new FamilyPort("minecraft:stone", ArsSourceIds.SOURCE, IOType.INPUT, IOType.INPUT),
                    new FamilyPort("minecraft:dirt", ArsSourceIds.SOURCE, IOType.OUTPUT, IOType.OUTPUT),
                    new FamilyPort("minecraft:sand", BotaniaManaIds.MANA, IOType.INPUT, IOType.INPUT),
                    new FamilyPort("minecraft:gravel", BotaniaManaIds.MANA, IOType.OUTPUT, IOType.OUTPUT));
            assertThat(requirement.validate(ports)).isEmpty();
            assertThat(requirement.validate(ports.subList(1, ports.size()))).isPresent();
        }
        assertThatThrownBy(() -> PortTierLimits.sourceInput("tiny")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortTierLimits.sourceOutput("ultimate")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortTierLimits.manaInput(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortTierLimits.manaOutput("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rhino_predicates_on_all_builders_match_family_and_direction_including_aliases() {
        CreateBridgeBootstrap.installForTesting(() -> true);
        PneumaticCraftBridgeBootstrap.installForTesting(() -> true);
        try {
            var context = new ContextFactory().enter();
            var scope = context.initStandardObjects();
            ScriptableObject.putProperty(scope, "builders", builders(), context);
            ScriptableObject.putProperty(scope, "inputState", Blocks.STONE.defaultBlockState(), context);
            ScriptableObject.putProperty(scope, "outputState", Blocks.DIRT.defaultBlockState(), context);
            ScriptableObject.putProperty(scope, "mismatchedState", Blocks.COBBLESTONE.defaultBlockState(), context);
            for (var family : List.of(ArsSourceIds.SOURCE, BotaniaManaIds.MANA, CreateRecipeTypes.STRESS, PneumaticIds.AIR)) {
                PortKinds.clearForTesting();
                PortKinds.register(new FamilyPort("minecraft:stone", family, IOType.INPUT, IOType.INPUT));
                PortKinds.register(new FamilyPort("minecraft:dirt", family, IOType.OUTPUT, IOType.OUTPUT));
                PortKinds.register(new FamilyPort("minecraft:cobblestone", family, IOType.INPUT, IOType.OUTPUT));
                String name = family.equals(ArsSourceIds.SOURCE) ? "Source"
                        : family.equals(BotaniaManaIds.MANA) ? "Mana" : family.equals(CreateRecipeTypes.STRESS) ? "Stress" : "Air";
                ScriptableObject.putProperty(scope, "family", name, context);
                assertThat(context.evaluateString(scope, """
                        var matched = true;
                        for (var i = 0; i < builders.size(); i++) {
                            var builder = builders.get(i);
                            for (var prefix of ['anyOf', 'any']) {
                                var input = builder[prefix + family + 'Input']();
                                var output = builder[prefix + family + 'Output']();
                                var ports = builder[prefix + family + 'Ports']();
                                matched = matched && input.matches(inputState) && !input.matches(outputState)
                                    && output.matches(outputState) && !output.matches(inputState)
                                    && ports.matches(inputState) && ports.matches(outputState)
                                    && !ports.matches(mismatchedState);
                            }
                        }
                        matched;
                        """, "compat-predicate-shortcuts", 1, null)).as(name).isEqualTo(true);
            }
        } finally {
            PortKinds.clearForTesting();
            CreateBridgeBootstrap.resetForTesting();
            PneumaticCraftBridgeBootstrap.resetForTesting();
        }
    }

    @Test
    void rhino_tier_shortcuts_on_all_builders_preserve_source_and_mana_categories() {
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "builders", builders(), context);
        assertThat(context.evaluateString(scope, """
                var matched = true;
                for (var i = 0; i < builders.size(); i++) {
                    var builder = builders.get(i);
                    matched = matched && builder.sourceInputTier('normal').requirements().get(0).id() == 'source_input_interface>=normal'
                        && builder.sourceOutputTier('NORMAL').requirements().get(0).id() == 'source_output_interface>=normal'
                        && builder.manaInputTier('NORMAL').requirements().get(0).id() == 'mana_input_pool>=normal'
                        && builder.manaOutputTier('normal').requirements().get(0).id() == 'mana_output_pool>=normal';
                }
                matched;
                """, "compat-tier-shortcuts", 1, null)).isEqualTo(true);
    }

    @Test
    void chemical_facade_factories_and_builders_preserve_runtime_tiers_and_directions() {
        for (var tier : PortTierLimits.ChemicalTier.values()) {
            var expected = InterfaceTiers.chemical(tier.id());
            assertThat(TierAdapters.unwrap(PortTierLimits.chemical(tier))).isEqualTo(expected);
            assertThat(TierAdapters.unwrap(PortTierLimits.chemical(tier.id()))).isEqualTo(expected);
            assertThat(TierAdapters.unwrap(PortTierLimits.combine(PortTierLimits.chemicalInput(tier),
                    PortTierLimits.chemicalOutput(tier.id())))).isEqualTo(expected);
            assertThat(TierAdapters.unwrap(PortTierLimits.chemical(tier, IoDirection.INPUT)))
                    .isEqualTo(InterfaceTiers.chemicalInput(tier.id()));
            assertThat(TierAdapters.unwrap(PortTierLimits.chemical(tier, IoDirection.OUTPUT)))
                    .isEqualTo(InterfaceTiers.chemicalOutput(tier.id()));
            assertThat(TierAdapters.unwrap(PortTierLimits.chemicalInput(tier.id())))
                    .isEqualTo(InterfaceTiers.chemicalInput(tier.id()));
            assertThat(TierAdapters.unwrap(PortTierLimits.chemicalOutput(tier)))
                    .isEqualTo(InterfaceTiers.chemicalOutput(tier.id()));
        }
        var stage = Structures.stage().pattern(p -> p.layer("C").controller('C'))
                .portTiers(t -> t.minChemicalInput(PortTierLimits.ChemicalTier.ADVANCED)
                        .minChemicalOutput(PortTierLimits.ChemicalTier.ELITE)
                        .anyChemicalInput().anyChemicalOutput()
                        .anyRadioactiveChemicalInput().anyRadioactiveChemicalOutput()
                        .anyHeatInput().anyHeatOutput().anyStressInput().anyStressOutput()
                        .anyAirInput().anyAirOutput()).build();
        var expected = PortTierRequirementSpec.from(PortTiers.builder()
                .minChemicalInput(PortTiers.ChemicalTier.ADVANCED).minChemicalOutput(PortTiers.ChemicalTier.ELITE)
                .anyChemicalInput().anyChemicalOutput().anyRadioactiveChemicalInput().anyRadioactiveChemicalOutput()
                .anyHeatInput().anyHeatOutput().anyStressInput().anyStressOutput().anyAirInput().anyAirOutput().build());
        assertThat(MachineDefinitionConverter.toDeclaration(StructureAdapters.unwrap(stage)).portTierRequirements())
                .isEqualTo(expected);
        assertThat(stage.portTiers().requirements()).extracting(PortTierLimits.RequirementView::category)
                .containsExactly(PortTierLimits.PortCategory.CHEMICAL, PortTierLimits.PortCategory.CHEMICAL,
                        PortTierLimits.PortCategory.CHEMICAL, PortTierLimits.PortCategory.CHEMICAL,
                        PortTierLimits.PortCategory.RADIOACTIVE_CHEMICAL, PortTierLimits.PortCategory.RADIOACTIVE_CHEMICAL,
                        PortTierLimits.PortCategory.HEAT, PortTierLimits.PortCategory.HEAT,
                        PortTierLimits.PortCategory.STRESS, PortTierLimits.PortCategory.STRESS,
                        PortTierLimits.PortCategory.AIR, PortTierLimits.PortCategory.AIR);
        assertThatThrownBy(() -> PortTierLimits.chemical("normal")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortTierLimits.chemical(PortTierLimits.ChemicalTier.BASIC, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void single_tier_facade_factories_preserve_presence_and_requested_directions() {
        assertThat(TierAdapters.unwrap(PortTierLimits.radioactiveChemical())).isEqualTo(InterfaceTiers.radioactiveChemical());
        assertThat(TierAdapters.unwrap(PortTierLimits.heat())).isEqualTo(InterfaceTiers.heat());
        assertThat(TierAdapters.unwrap(PortTierLimits.stress())).isEqualTo(InterfaceTiers.stress());
        assertThat(TierAdapters.unwrap(PortTierLimits.air())).isEqualTo(InterfaceTiers.air());
        for (var io : IoDirection.values()) {
            IOType coreIo = io == IoDirection.INPUT ? IOType.INPUT : IOType.OUTPUT;
            assertThat(TierAdapters.unwrap(PortTierLimits.radioactiveChemical(io))).isEqualTo(InterfaceTiers.radioactiveChemical(coreIo));
            assertThat(TierAdapters.unwrap(PortTierLimits.heat(io))).isEqualTo(InterfaceTiers.heat(coreIo));
            assertThat(TierAdapters.unwrap(PortTierLimits.stress(io))).isEqualTo(InterfaceTiers.stress(coreIo));
            assertThat(TierAdapters.unwrap(PortTierLimits.air(io))).isEqualTo(InterfaceTiers.air(coreIo));
        }
        assertThat(TierAdapters.unwrap(PortTierLimits.combine(PortTierLimits.radioactiveChemicalInput(),
                PortTierLimits.radioactiveChemicalOutput(), PortTierLimits.heatInput(), PortTierLimits.heatOutput(),
                PortTierLimits.stressInput(), PortTierLimits.stressOutput(), PortTierLimits.airInput(), PortTierLimits.airOutput())))
                .isEqualTo(PortTiers.combine(InterfaceTiers.radioactiveChemical(), InterfaceTiers.heat(),
                        InterfaceTiers.stress(), InterfaceTiers.air()));
        for (var family : List.of(CreateRecipeTypes.STRESS, PneumaticIds.AIR)) {
            var spec = PortTierRequirementSpec.from(family.equals(CreateRecipeTypes.STRESS)
                    ? InterfaceTiers.stressInput() : InterfaceTiers.airInput());
            assertThat(spec.validate(List.of(new FamilyPort("test:input", family, IOType.INPUT, IOType.INPUT)))).isEmpty();
            assertThat(spec.validate(List.of())).isPresent();
            assertThat(spec.validate(List.of(new FamilyPort("test:output", family, IOType.OUTPUT, IOType.OUTPUT)))).isPresent();
            assertThat(spec.validate(List.of(new FamilyPort("test:mismatch", family, IOType.INPUT, IOType.OUTPUT)))).isPresent();
            assertThat(spec.validate(List.of(new FamilyPort("test:other", ArsSourceIds.SOURCE, IOType.INPUT, IOType.INPUT)))).isPresent();
        }
    }

    @Test
    void rhino_new_tier_factories_on_api_and_all_builders_work_without_native_mods() {
        var expected = List.of(
                PortTierRequirementSpec.from(InterfaceTiers.chemicalInput("advanced")),
                PortTierRequirementSpec.from(InterfaceTiers.chemicalOutput("elite")),
                PortTierRequirementSpec.from(InterfaceTiers.radioactiveChemicalInput()),
                PortTierRequirementSpec.from(InterfaceTiers.radioactiveChemicalOutput()),
                PortTierRequirementSpec.from(InterfaceTiers.heatInput()), PortTierRequirementSpec.from(InterfaceTiers.heatOutput()),
                PortTierRequirementSpec.from(InterfaceTiers.stressInput()), PortTierRequirementSpec.from(InterfaceTiers.stressOutput()),
                PortTierRequirementSpec.from(InterfaceTiers.airInput()), PortTierRequirementSpec.from(InterfaceTiers.airOutput()));
        for (var factory : builders()) {
            var context = new ContextFactory().enter();
            var scope = context.initStandardObjects();
            ScriptableObject.putProperty(scope, "factory", factory, context);
            context.evaluateString(scope, """
                    var results = [factory.chemicalInputTier('advanced'), factory.chemicalOutputTier('elite'),
                        factory.radioactiveChemicalInputTier(), factory.radioactiveChemicalOutputTier(),
                        factory.heatInputTier(), factory.heatOutputTier(), factory.stressInputTier(),
                        factory.stressOutputTier(), factory.airInputTier(), factory.airOutputTier()];
                    """, "compat-port-tier-factories", 1, null);
            for (int i = 0; i < expected.size(); i++) {
                var result = context.evaluateString(scope, "results[" + i + "]", "compat-tier-result", 1, null);
                assertThat(context.jsToJava(result, TypeInfo.of(PortTierRequirementSpec.class)))
                        .as("%s factory %s", factory.getClass().getSimpleName(), i).isEqualTo(expected.get(i));
            }
        }
    }

    @Test
    void rhino_mixed_tier_strings_reach_structure_stages_and_reject_fabricated_grades() {
        var api = new KubeJSApi();
        var structure = new MachineStructureBuilderJS(ID);
        var context = new ContextFactory().enter();
        var scope = context.initStandardObjects();
        ScriptableObject.putProperty(scope, "api", api, context);
        ScriptableObject.putProperty(scope, "structure", structure, context);
        context.evaluateString(scope, """
                structure.mainStructure(function(stage) {
                    stage.pattern('X').set('X', 'minecraft:iron_block');
                    stage.portRequirements(api.portRequirements({chemical_input_hatch: [1, 2], heat_output_hatch: 1}));
                    stage.portTierRequirements(api.portTierRequirements([
                        'item_input_bus>=small', 'fluid_output_hatch>=vacuum', 'energy_input_hatch>=ultimate',
                        'source_input_interface>=normal', 'mana_output_pool>=normal',
                        'chemical_input_hatch>=advanced', 'chemical_output_hatch>=ultimate',
                        'radioactive_chemical_input_hatch', 'radioactive_chemical_output_hatch',
                        'heat_input_hatch', 'heat_output_hatch', 'create_stress_input_interface',
                        'create_stress_output_interface', 'pneumaticcraft_air_input_interface',
                        'pneumaticcraft_air_output_interface'
                    ]));
                });
                """, "compat-port-tier-strings", 1, null);
        var declaration = structure.createObject().declarations().getFirst();
        assertThat(declaration.portTierRequirements().requirements()).extracting(PortTierRequirementSpec.Requirement::id)
                .containsExactly("item_input_bus>=small", "fluid_output_hatch>=vacuum", "energy_input_hatch>=ultimate",
                        "source_input_interface>=normal", "mana_output_pool>=normal",
                        "chemical_input_hatch>=advanced", "chemical_output_hatch>=ultimate",
                        "radioactive_chemical_input_hatch", "radioactive_chemical_output_hatch", "heat_input_hatch", "heat_output_hatch",
                        "create_stress_input_interface", "create_stress_output_interface",
                        "pneumaticcraft_air_input_interface", "pneumaticcraft_air_output_interface");
        assertThat(declaration.portRequirements()).isEqualTo(api.portRequirements(Map.of("chemical_input_hatch", List.of(1, 2), "heat_output_hatch", 1)));
        for (String invalid : List.of("chemical_input_hatch>=normal", "chemical_input_hatch>=", "chemical_input_hatch",
                "chemical_input_bus>=basic", "chemical_input_hatch>=basic>=elite", "heat_input_hatch>=ultimate",
                "radioactive_chemical_input_hatch>=elite", "create_stress_input_interface>=normal",
                "pneumaticcraft_air_output_interface>=any", "mana_input_pool>=NORMAL")) {
            assertThatThrownBy(() -> api.portTierRequirements(List.of(invalid))).as(invalid).isInstanceOf(IllegalArgumentException.class);
        }
    }

    private static List<Object> builders() {
        return List.of(new KubeJSApi(), new MachineBuilderJS(ID), new MachineStructureBuilderJS(ID),
                new MachineStructureStageBuilderJS(ID));
    }

    /** Registered family fixture with an independently declared binding direction.
     * @author howxu <dev@howxu.cn>
     */
    private record FamilyPort(String id, ResourceLocation family, IOType ioType, IOType bindingDirection) implements IOPortKind {
        public BlockEntityType.BlockEntitySupplier<? extends BlockEntity> entityFactory() { return (position, state) -> null; }
        public List<PortFamilyDescriptor> families() { return List.of(new PortFamilyDescriptor(family, ioType, 0, List.of())); }
        public PortDefinition definition() {
            return PortDefinition.of(ResourceLocation.parse(id), IOPortKind.binding(new CapabilityType(family), bindingDirection, families()));
        }
    }
}
