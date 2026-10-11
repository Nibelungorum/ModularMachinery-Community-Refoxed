package cn.howxu.mmcr.internal.capability;

import cn.howxu.mmcr.api.capability.MachineCapability;
import cn.howxu.mmcr.api.capability.facet.AsyncPlanningFacet;
import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies capabilities can publish their non-transfer facets without exposing transfer access.
 *
 * @author howxu <dev@howxu.cn>
 */
class NonTransferCapabilityTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void itemCapabilityCanHideTransferFacet() {
        ItemBusCapability capability = new ItemBusCapability(
                null, new LongItemStorage(1, 64L, () -> {}), IOType.INPUT, false);

        assertThat(capability.view().facets()).doesNotContain(TransferFacet.class);
        assertThat(capability.facet(TransferFacet.class)).isEmpty();
        assertThat(capability.facet(ItemHandlerFacet.class)).contains(capability);
        assertThat(capability.facet(AsyncPlanningFacet.class)).isPresent();
    }

    @Test
    void fluidCapabilityCanHideTransferFacet() {
        FluidHatchCapability capability = new FluidHatchCapability(
                null, new LongFluidStorage(1, 1_000L, () -> {}), IOType.INPUT, false);

        assertThat(capability.view().facets()).doesNotContain(TransferFacet.class);
        assertThat(capability.facet(TransferFacet.class)).isEmpty();
        assertThat(capability.facet(FluidHandlerFacet.class)).contains(capability);
        assertThat(capability.facet(AsyncPlanningFacet.class)).isPresent();
    }

    @Test
    void builtin_transfer_facets_return_the_declared_instance_and_keep_other_lookup_contracts() {
        List<MachineCapability> capabilities = List.of(
                new ItemBusCapability(new LongItemStorage(1, 64L, () -> {}), IOType.INPUT),
                new FluidHatchCapability(new LongFluidStorage(1, 1_000L, () -> {}), IOType.INPUT),
                new EnergyHatchCapability(new LongEnergyStorage(1_000L, 100L, () -> {}), IOType.INPUT));
        for (MachineCapability capability : capabilities) {
            assertThat(capability.view().facets()).contains(TransferFacet.class);
            assertThat(capability.facet(TransferFacet.class).orElseThrow()).isSameAs(capability);
            assertThat(capability.facet(AsyncPlanningFacet.class)).isPresent();
            assertThat(capability.facet(CapabilityFacet.class)).isEmpty();
        }
    }
}
