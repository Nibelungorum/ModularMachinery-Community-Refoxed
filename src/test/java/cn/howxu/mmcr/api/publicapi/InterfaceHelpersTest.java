package cn.howxu.mmcr.api.publicapi;

import cn.howxu.mmcr.api.publicapi.machine.BlockPredicate;
import cn.howxu.mmcr.api.publicapi.machine.InterfacePredicates;
import cn.howxu.mmcr.api.publicapi.machine.InterfaceTiers;
import cn.howxu.mmcr.api.publicapi.machine.PortTiers;
import cn.howxu.mmcr.compat.kubejs.KubeJSInterfaceHelpers;
import cn.howxu.mmcr.compat.kubejs.MachineBuilderJS;
import cn.howxu.mmcr.compat.kubejs.MachineRecipeFactory;
import cn.howxu.mmcr.compat.kubejs.MachineStructureBuilderJS;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.api.recipe.requirement.SmartInterfaceRequirement;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import cn.howxu.mmcr.registry.ModBlocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterfaceHelpersTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void interface_predicates_keep_registered_port_alternatives() {
        assertThat(InterfacePredicates.anyOfItemInput().alternatives()).hasSize(7);
        assertThat(InterfacePredicates.anyOfFluidOutput().alternatives()).hasSize(8);
        assertThat(InterfacePredicates.anyOfEnergyInput().alternatives()).hasSize(8);
        assertThat(InterfacePredicates.anyOfPort("item_input_bus").alternatives()).hasSize(1);
        assertThat(InterfacePredicates.smartInterface().blockSupplier()).isPresent();
    }

    @Test
    void any_of_port_rejects_empty_alternatives() {
        assertThatThrownBy(() -> InterfacePredicates.anyOfPort())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one port is required");
        assertThatThrownBy(() -> InterfacePredicates.anyOfPort((String[]) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one port is required");
        assertThatThrownBy(() -> InterfacePredicates.anyOfPort(new String[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one port is required");
        assertThatThrownBy(() -> InterfacePredicates.anyOfPort((Identifier[]) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one port is required");
        assertThatThrownBy(() -> InterfacePredicates.anyOfPort(new Identifier[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one port is required");
        assertThatThrownBy(() -> InterfacePredicates.anyOfPort((BlockPredicate[]) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one port is required");
        assertThatThrownBy(() -> InterfacePredicates.anyOfPort(new BlockPredicate[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one port is required");
    }

    @Test
    void interface_tiers_keep_item_fluid_and_energy_enums_separate() {
        PortTiers tiers = InterfaceTiers.combine(InterfaceTiers.item("normal"),
                InterfaceTiers.fluid("vacuum"), InterfaceTiers.energy("ultimate"));

        assertThat(tiers.requirements()).extracting(PortTiers.Requirement::category)
                .containsExactly(PortTiers.PortCategory.ITEM, PortTiers.PortCategory.ITEM,
                        PortTiers.PortCategory.FLUID, PortTiers.PortCategory.FLUID,
                        PortTiers.PortCategory.ENERGY, PortTiers.PortCategory.ENERGY);
        assertThat(tiers.requirements()).extracting(PortTiers.Requirement::minTierId)
                .containsExactly("normal", "normal", "vacuum", "vacuum", "ultimate", "ultimate");
        assertThatThrownBy(() -> InterfaceTiers.item("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterfaceTiers.fluid("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterfaceTiers.energy("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortTiers.Requirement(PortTiers.PortCategory.ITEM,
                cn.howxu.mmcr.util.IOType.INPUT, PortTiers.ItemTier.NORMAL.ordinal(), "big"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void kubejs_helpers_delegate_all_interface_shapes_and_requirements() {
        assertThat(KubeJSInterfaceHelpers.anyOfItemInput().children()).hasSize(7);
        assertThat(KubeJSInterfaceHelpers.anyOfItemOutput().children()).hasSize(7);
        assertThat(KubeJSInterfaceHelpers.anyOfFluidInput().children()).hasSize(8);
        assertThat(KubeJSInterfaceHelpers.anyOfFluidOutput().children()).hasSize(8);
        assertThat(KubeJSInterfaceHelpers.anyOfEnergyInput().children()).hasSize(8);
        assertThat(KubeJSInterfaceHelpers.anyOfEnergyOutput().children()).hasSize(8);
        assertThat(KubeJSInterfaceHelpers.parallelControllers().children()).hasSize(8);
        assertThat(KubeJSInterfaceHelpers.smartInterface().matches(Blocks.STONE.defaultBlockState())).isFalse();
        assertThat(KubeJSInterfaceHelpers.itemOutputTier("big").requirements()).hasSize(1);
        assertThat(KubeJSInterfaceHelpers.fluidInputTier("vacuum").requirements()).hasSize(1);
        assertThat(KubeJSInterfaceHelpers.energyOutputTier("ultimate").requirements()).hasSize(1);
        assertThatThrownBy(() -> KubeJSInterfaceHelpers.anyOfPort())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(MachineRecipeFactory.smartInterfaceOutput("mode", 2F))
                .isEqualTo(SmartInterfaceRequirement.output("mode", 2F));
        assertThat(MachineRecipeFactory.smartInterfaceInput("mode", 1F, 3F))
                .isEqualTo(SmartInterfaceRequirement.input("mode", 1F, 3F));
    }

    @Test
    void kubejs_port_overloads_preserve_registered_block_identity() {
        var identifierPredicate = KubeJSInterfaceHelpers.anyOfPort(Identifier.parse("mmcr:item_input_bus"));
        var publicPredicate = KubeJSInterfaceHelpers.anyOfPort(InterfacePredicates.smartInterface());
        var registeredPort = ModBlocks.BLOCKS.get("item_input_bus").get();
        var smartInterface = ModBlocks.BLOCKS.get("smart_interface").get();

        assertThat(identifierPredicate.children()).singleElement()
                .matches(child -> child.matches(registeredPort.defaultBlockState()))
                .matches(child -> !child.matches(Blocks.STONE.defaultBlockState()));
        assertThat(publicPredicate.children()).singleElement()
                .matches(child -> child.matches(smartInterface.defaultBlockState()));
    }

    @Test
    void interface_tier_direction_requires_non_null_io_type() {
        assertThatThrownBy(() -> InterfaceTiers.item(PortTiers.ItemTier.NORMAL, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> InterfaceTiers.fluid(PortTiers.FluidTier.NORMAL, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> InterfaceTiers.energy(PortTiers.EnergyTier.NORMAL, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void kubejs_structure_builder_exposes_interface_helpers() {
        var builder = new MachineStructureBuilderJS("test:interfaces");
        assertThat(builder.anyOfItemInput().children()).hasSize(7);
        assertThat(builder.anyOfPort("mmcr:item_input_bus").children()).hasSize(1);
        assertThat(builder.parallelControllers().children()).hasSize(8);
        assertThat(builder.smartInterface()).isNotNull();
        assertThat(builder.energyOutputTier("ultimate").requirements()).hasSize(1);

        var machineBuilder = new MachineBuilderJS("test:interfaces");
        assertThat(machineBuilder.anyOfFluidOutput().children()).hasSize(8);
        assertThat(machineBuilder.smartInterfaceBlock()).isNotNull();
        assertThat(machineBuilder.itemInputTier("big").requirements()).hasSize(1);
    }

    @Test
    void identifier_lookup_preserves_namespace() {
        assertThat(cn.howxu.mmcr.api.publicapi.PublicBuiltinRegistration.block(
                Identifier.parse("minecraft:stone")).get()).isSameAs(Blocks.STONE);
    }
}
