package cn.howxu.mmcr.publicapi;

import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.machine.PortTierRequirementSpec;
import cn.howxu.mmcr.api.machine.definition.PortTiers;
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
import cn.howxu.mmcr.internal.port.IOPortKind;
import cn.howxu.mmcr.internal.port.PortFamilyDescriptor;
import cn.howxu.mmcr.publicapi.structure.PortTierLimits;
import cn.howxu.mmcr.registry.PortKinds;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import dev.latvian.mods.rhino.ContextFactory;
import dev.latvian.mods.rhino.ScriptableObject;
import java.util.List;
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
