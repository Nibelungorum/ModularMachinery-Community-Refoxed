package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.ExchangeFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.NetworkParticipantFacet;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies typed native capability facet contracts and snapshot preservation.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityFacetTest {
    @Test
    void lookup_returns_only_facets_declared_by_each_capability_view() {
        ItemBusCapability item = new ItemBusCapability(new LongItemStorage(1, 10L, () -> {}), IOType.INPUT);
        FluidHatchCapability fluid = new FluidHatchCapability(new LongFluidStorage(1, 1_000L, () -> {}), IOType.INPUT);
        EnergyHatchCapability energy = new EnergyHatchCapability(new LongEnergyStorage(100L, 100L, () -> {}), IOType.INPUT);

        assertThat(item.facet(ItemHandlerFacet.class)).contains(item);
        assertThat(item.facet(FluidHandlerFacet.class)).isEmpty();
        assertThat(fluid.facet(FluidHandlerFacet.class)).contains(fluid);
        assertThat(fluid.facet(EnergyStorageFacet.class)).isEmpty();
        assertThat(energy.facet(EnergyStorageFacet.class)).contains(energy);
        assertThat(energy.facet(ExchangeFacet.class)).isEmpty();
        assertThat(energy.facet(NetworkParticipantFacet.class)).isEmpty();
        assertThat(item.facet(CapabilityFacet.class)).isEmpty();
    }

    @Test
    void native_facets_keep_their_distinct_handler_contracts() {
        LongItemStorage itemHandler = new LongItemStorage(1, 10L, () -> {});
        LongFluidStorage fluidHandler = new LongFluidStorage(1, 1_000L, () -> {});
        LongEnergyStorage energyStorage = new LongEnergyStorage(100L, 100L, () -> {});
        ItemBusCapability item = new ItemBusCapability(itemHandler, IOType.INPUT);
        FluidHatchCapability fluid = new FluidHatchCapability(fluidHandler, IOType.INPUT);
        EnergyHatchCapability energy = new EnergyHatchCapability(energyStorage, IOType.INPUT);

        assertThat(item.facet(ItemHandlerFacet.class).orElseThrow().itemHandler()).isSameAs(itemHandler);
        assertThat(fluid.facet(FluidHandlerFacet.class).orElseThrow().fluidHandler()).isSameAs(fluidHandler);
        assertThat(energy.facet(EnergyStorageFacet.class).orElseThrow().energyStorage()).isSameAs(energyStorage);
    }

    @Test
    void snapshot_preserves_capability_identity_and_order() {
        ItemBusCapability first = new ItemBusCapability(new LongItemStorage(1, 10L, () -> {}), IOType.INPUT);
        FluidHatchCapability second = new FluidHatchCapability(new LongFluidStorage(1, 1_000L, () -> {}), IOType.INPUT);

        CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(first, second));

        assertThat(snapshot.capabilities()).containsExactly(first, second);
        assertThat(snapshot.capabilities().get(0)).isSameAs(first);
        assertThat(snapshot.capabilities().get(1)).isSameAs(second);
    }
}
