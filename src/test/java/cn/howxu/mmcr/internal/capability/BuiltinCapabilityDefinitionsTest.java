package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.MMCR;
import cn.howxu.mmcr.api.capability.CapabilityType;
import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.OperationFacet;
import cn.howxu.mmcr.api.capability.facet.ResourceFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.type.CapabilityDefinition;
import cn.howxu.mmcr.api.capability.type.CapabilityRegistry;
import cn.howxu.mmcr.internal.api.PublicApiBootstrap;
import cn.howxu.mmcr.test.RuntimeTestFixtures;
import cn.howxu.mmcr.test.TestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies registration and registry-backed creation of built-in capabilities.
 *
 * @author howxu <dev@howxu.cn>
 */
class BuiltinCapabilityDefinitionsTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void beginCapabilityRegistration() {
        PublicApiBootstrap.clearForTesting();
        PublicApiBootstrap.begin();
    }

    @AfterEach
    void resetCapabilityRegistration() {
        PublicApiBootstrap.clearForTesting();
    }

    @Test
    void registers_built_in_ids_with_their_typed_facets() {
        assertThat(CapabilityRegistry.values())
                .extracting(definition -> definition.type().id())
                .containsExactly(MMCR.id("item"), MMCR.id("fluid"), MMCR.id("energy"));

        CapabilityDefinition item = definition("item");
        CapabilityDefinition fluid = definition("fluid");
        CapabilityDefinition energy = definition("energy");

        assertThat(item.facets()).contains(ResourceFacet.class, OperationFacet.class);
        assertThat(fluid.facets()).contains(ResourceFacet.class, OperationFacet.class);
        assertThat(energy.facets()).contains(ScalarFacet.class, OperationFacet.class);
    }

    @Test
    void creates_real_port_capabilities_from_registry_definitions() {
        MachineCapability item = RuntimeTestFixtures.itemInput(BlockPos.ZERO)
                .capabilitySnapshot().capabilities().getFirst();
        MachineCapability fluid = RuntimeTestFixtures.fluidInput(new BlockPos(1, 0, 0))
                .capabilitySnapshot().capabilities().getFirst();
        MachineCapability energy = RuntimeTestFixtures.energyInput(new BlockPos(2, 0, 0))
                .capabilitySnapshot().capabilities().getFirst();

        assertThat(item.type()).isEqualTo(definition("item").type());
        assertThat(fluid.type()).isEqualTo(definition("fluid").type());
        assertThat(energy.type()).isEqualTo(definition("energy").type());
        assertThat(item).isInstanceOf(ItemBusCapability.class);
        assertThat(fluid).isInstanceOf(FluidHatchCapability.class);
        assertThat(energy).isInstanceOf(EnergyHatchCapability.class);
        assertThat(item.facet(ResourceFacet.class)).isPresent();
        assertThat(fluid.facet(ResourceFacet.class)).isPresent();
        assertThat(energy.facet(ScalarFacet.class)).isPresent();
        assertThat(item.facet(OperationFacet.class)).isPresent();
        assertThat(fluid.facet(OperationFacet.class)).isPresent();
        assertThat(energy.facet(OperationFacet.class)).isPresent();
    }

    private static CapabilityDefinition definition(String path) {
        return CapabilityRegistry.get(new CapabilityType(MMCR.id(path)));
    }
}
