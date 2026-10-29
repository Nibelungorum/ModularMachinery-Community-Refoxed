package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.facet.CapabilityFacet;
import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.ExchangeFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.NetworkParticipantFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityOperation;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.util.IOType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Verifies typed native capability facet contracts and snapshot preservation.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityFacetTest {
    @Test
    void incompatible_default_facet_does_not_query_the_view() {
        ProbeCapability capability = new ProbeCapability();
        assertThat(capability.facet(EnergyStorageFacet.class)).isEmpty();
        assertThat(capability.viewReads).isZero();
    }

    @Test
    void compatible_default_facet_keeps_declarations_live() {
        ProbeCapability capability = new ProbeCapability();
        assertThat(capability.facet(ProbeFacet.class)).isEmpty();
        capability.declared = true;
        assertThat(capability.facet(ProbeFacet.class)).contains(capability);
        capability.declared = false;
        assertThat(capability.facet(ProbeFacet.class)).isEmpty();
        assertThat(capability.viewReads).isEqualTo(3);
    }

    @Test
    void null_default_facet_is_rejected_before_querying_the_view() {
        ProbeCapability capability = new ProbeCapability();
        assertThatNullPointerException().isThrownBy(() -> capability.facet(null));
        assertThat(capability.viewReads).isZero();
    }

    @Test
    void a_custom_override_can_return_a_separate_facet_instance() {
        EnergyStorageFacet adapter = () -> new LongEnergyStorage(100L, 100L, () -> {});
        MachineCapability capability = new ProbeCapability() {
            @Override
            public <F extends CapabilityFacet> Optional<F> facet(Class<F> facetType) {
                if (facetType == EnergyStorageFacet.class) return Optional.of(facetType.cast(adapter));
                return super.facet(facetType);
            }
        };
        assertThat(capability).isNotInstanceOf(EnergyStorageFacet.class);
        assertThat(capability.facet(EnergyStorageFacet.class)).contains(adapter);
    }

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

    /** @author howxu <dev@howxu.cn> */
    private interface ProbeFacet extends CapabilityFacet { }

    /** @author howxu <dev@howxu.cn> */
    private static class ProbeCapability implements MachineCapability, ProbeFacet {
        private boolean declared;
        private int viewReads;

        @Override
        public CapabilityType type() {
            throw new AssertionError("Unexpected capability type query");
        }

        @Override
        public CapabilityView view() {
            viewReads++;
            return new CapabilityView() {
                @Override
                public CapabilityType type() {
                    return ProbeCapability.this.type();
                }

                @Override
                public Set<Class<? extends CapabilityFacet>> facets() {
                    return declared ? Set.of(ProbeFacet.class) : Set.of();
                }
            };
        }

        @Override
        public CapabilityOperation prepare(CapabilityRequest request) {
            throw new AssertionError("Unexpected resource planning");
        }
    }
}
