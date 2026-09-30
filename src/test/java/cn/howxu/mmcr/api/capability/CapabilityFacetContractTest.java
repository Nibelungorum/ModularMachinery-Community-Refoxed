package cn.howxu.mmcr.api.capability;

import cn.howxu.mmcr.api.capability.facet.EnergyStorageFacet;
import cn.howxu.mmcr.api.capability.facet.ExchangeFacet;
import cn.howxu.mmcr.api.capability.facet.FluidHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.ItemHandlerFacet;
import cn.howxu.mmcr.api.capability.facet.NetworkParticipantFacet;
import cn.howxu.mmcr.api.capability.facet.ScalarFacet;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.internal.capability.EnergyHatchCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.storage.LongEnergyStorage;
import cn.howxu.mmcr.internal.storage.LongFluidStorage;
import cn.howxu.mmcr.internal.storage.LongItemStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.test.capability.CapabilityContractAssertions;
import cn.howxu.mmcr.test.capability.TestExchangeFacet;
import cn.howxu.mmcr.test.capability.TestNetworkParticipantFacet;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reusable contracts for the public capability facet families.
 *
 * @author howxu <dev@howxu.cn>
 */
class CapabilityFacetContractTest {
    @BeforeAll
    static void bootstrapMinecraft() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void native_handler_facets_preserve_handler_identity_and_contents() {
        LongItemStorage items = new LongItemStorage(1, 10L, () -> {});
        LongFluidStorage fluids = new LongFluidStorage(1, 1_000L, () -> {});
        LongEnergyStorage energy = new LongEnergyStorage(100L, 100L, () -> {});
        items.setContents(0, new ItemStack(Items.IRON_INGOT), 3L);
        fluids.setContents(0, new FluidStack(Fluids.WATER, 1), 500L);
        energy.setAmount(40L);
        ItemBusCapability itemCapability = new ItemBusCapability(items, IOType.INPUT);
        FluidHatchCapability fluidCapability = new FluidHatchCapability(fluids, IOType.INPUT);
        EnergyHatchCapability energyCapability = new EnergyHatchCapability(energy, IOType.INPUT);
        CapabilitySnapshot snapshot = new CapabilitySnapshot(List.of(
                itemCapability, fluidCapability, energyCapability));

        assertThat(itemCapability.facet(ItemHandlerFacet.class)).get().extracting(ItemHandlerFacet::itemHandler)
                .isSameAs(items);
        assertThat(fluidCapability.facet(FluidHandlerFacet.class)).get().extracting(FluidHandlerFacet::fluidHandler)
                .isSameAs(fluids);
        assertThat(energyCapability.facet(EnergyStorageFacet.class)).get().extracting(EnergyStorageFacet::energyStorage)
                .isSameAs(energy);
        assertThat(snapshot.facets(ItemHandlerFacet.class)).containsExactly(itemCapability);
        assertThat(snapshot.facets(FluidHandlerFacet.class)).containsExactly(fluidCapability);
        assertThat(snapshot.facets(EnergyStorageFacet.class)).containsExactly(energyCapability);
        assertThat(items.resource(0).is(Items.IRON_INGOT)).isTrue();
        assertThat(fluids.resource(0).is(Fluids.WATER)).isTrue();
        assertThat(energy.getAmountAsLong()).isEqualTo(40L);
    }

    @Test
    void scalar_facet_commits_typed_requests_and_rejects_invalid_direction() {
        LongEnergyStorage storage = new LongEnergyStorage(10L, 10L, () -> {});
        EnergyHatchCapability facet = new EnergyHatchCapability(storage, IOType.OUTPUT);

        assertThat(facet.facet(ScalarFacet.class)).contains(facet);
        assertThat(facet.facet(EnergyStorageFacet.class)).contains(facet);
        assertThat(facet.prepareScalar(new CapabilityRequests.ValueRequest(
                facet.type(), IOType.OUTPUT, 1L, 2L, true)).commit().success()).isTrue();
        assertThat(storage.getAmountAsLong()).isEqualTo(2L);
        assertThatThrownBy(() -> facet.prepare(new CapabilityRequests.ValueRequest(
                facet.type(), IOType.INPUT, 1L, 1L, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exchange_facet_applies_signed_deltas_within_capacity() {
        TestExchangeFacet facet = new TestExchangeFacet();

        assertThat(facet.facet(ExchangeFacet.class)).contains(facet);
        assertThat(new CapabilitySnapshot(List.of(facet)).facets(ExchangeFacet.class)).containsExactly(facet);

        CapabilityContractAssertions.assertCommitted(facet.prepareExchange(3D).commit());
        CapabilityContractAssertions.assertCommitted(facet.prepareExchange(-1D).commit());
        assertThat(facet.potential()).isEqualTo(2D);
        assertThat(facet.capacity()).isGreaterThanOrEqualTo(facet.potential());
        assertThat(facet.conductance()).isPositive();
        assertThat(facet.prepareExchange(9D).commit().success()).isFalse();
        assertThat(facet.prepareExchange(-9D).commit().success()).isFalse();
        assertThat(facet.potential()).isEqualTo(2D);
    }

    @Test
    void network_participant_invalidates_topology_and_returns_read_only_snapshots() {
        TestNetworkParticipantFacet facet = new TestNetworkParticipantFacet();

        assertThat(facet.facet(NetworkParticipantFacet.class)).contains(facet);
        assertThat(facet.networkSnapshot().capabilities()).isEmpty();

        facet.attach();
        long attachedVersion = facet.topologyVersion();
        CapabilitySnapshot attachedSnapshot = facet.networkSnapshot();

        assertThat(attachedSnapshot.capabilities()).containsExactly(facet);
        assertThat(attachedSnapshot.facets(NetworkParticipantFacet.class)).containsExactly(facet);
        facet.detach();

        assertThat(facet.topologyVersion()).isGreaterThan(attachedVersion);
        CapabilitySnapshot detachedSnapshot = facet.networkSnapshot();
        assertThat(detachedSnapshot).isNotSameAs(attachedSnapshot);
        assertThat(detachedSnapshot.capabilities()).isEmpty();
        assertThat(attachedSnapshot.capabilities()).containsExactly(facet);
        assertThatThrownBy(() -> attachedSnapshot.capabilities().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
