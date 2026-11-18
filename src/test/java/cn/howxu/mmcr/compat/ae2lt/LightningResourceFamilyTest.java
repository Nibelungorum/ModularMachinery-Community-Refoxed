package cn.howxu.mmcr.compat.ae2lt;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import cn.howxu.mmcr.api.capability.facet.TransferFacet;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.api.compat.ae2lt.LightningTier;
import cn.howxu.mmcr.compat.ae2lt.loaded.LoadedLightningBridge;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AsyncOutputService;
import cn.howxu.mmcr.test.TestBootstrap;
import cn.howxu.mmcr.util.IOType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.EXTREME_HIGH_VOLTAGE;
import static cn.howxu.mmcr.api.compat.ae2lt.LightningTier.HIGH_VOLTAGE;
import static org.assertj.core.api.Assertions.assertThat;

/** @author howxu <dev@howxu.cn> */
class LightningResourceFamilyTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        TestBootstrap.bootstrap();
    }

    @BeforeEach
    void loadBridge() {
        LightningBridgeBootstrap.installForTesting(new LoadedLightningBridge());
    }

    @AfterEach
    void resetBridge() {
        LightningBridgeBootstrap.resetForTesting();
    }

    @Test
    void roundTripsBothTiersThroughOneFamily() {
        var family = LoadedLightningBridge.FAMILY;
        assertThat(AE2ResourceFamilies.all()).containsExactly(
                AE2ResourceFamilies.ITEM, AE2ResourceFamilies.FLUID, family);
        for (LightningTier tier : LightningTier.values()) {
            AEKey key = family.adapter().toKey(tier);
            assertThat(key.getType()).isSameAs(family.adapter().keyType());
            assertThat(family.adapter().toResource(key)).contains(tier);
            assertThat(AE2ResourceFamilies.supportsKey(key)).isTrue();
        }
        assertThat(family.adapter().toResource(AEItemKey.of(Items.IRON_INGOT))).isEmpty();
        assertThat(family.adapter().toResource(AEFluidKey.of(Fluids.WATER))).isEmpty();
        LightningBridgeBootstrap.installForTesting(LightningBridgeBootstrap.selectForTesting(true, false));
        assertThat(AE2ResourceFamilies.all()).containsExactly(AE2ResourceFamilies.ITEM, AE2ResourceFamilies.FLUID);
        assertThat(AE2ResourceFamilies.supportsKey(family.adapter().toKey(HIGH_VOLTAGE))).isFalse();
    }

    @Test
    void mixedSlotsDoNotExposeOtherResourceTypesAndRollbackPreservesBothTiers() {
        var family = LoadedLightningBridge.FAMILY;
        var inventory = new GenericStackInv(Set.of(AEKeyType.items(), AEKeyType.fluids(), family.adapter().keyType()),
                null, GenericStackInv.Mode.STORAGE, 4);
        inventory.setStack(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 8L));
        inventory.setStack(1, new GenericStack(AEFluidKey.of(Fluids.WATER), 1_000L));
        inventory.setStack(2, new GenericStack(family.adapter().toKey(HIGH_VOLTAGE), 4_294_967_294L));
        inventory.setStack(3, new GenericStack(family.adapter().toKey(EXTREME_HIGH_VOLTAGE), 19L));
        var view = family.standardInputView(inventory);
        assertThat(view.resource(0)).isNull();
        assertThat(view.resource(1)).isNull();
        assertThat(view.resource(2)).isEqualTo(HIGH_VOLTAGE);
        assertThat(view.resource(3)).isEqualTo(EXTREME_HIGH_VOLTAGE);
        assertThat(view.reservationIdentity()).isSameAs(AE2ResourceFamilies.ITEM.standardInputView(inventory).reservationIdentity());
        try (var transaction = Transaction.openRoot()) {
            assertThat(view.extract(2, EXTREME_HIGH_VOLTAGE, 10L, transaction)).isZero();
            assertThat(view.extract(2, HIGH_VOLTAGE, 4_294_967_294L, transaction)).isEqualTo(4_294_967_294L);
        }
        assertThat(view.amount(2)).isEqualTo(4_294_967_294L);
        assertThat(view.amount(3)).isEqualTo(19L);
    }

    @Test
    void networkRebindAndWatcherUpdatesKeepBothTiersIndependent() {
        var family = LoadedLightningBridge.FAMILY;
        List<AEKey> keys = List.of(AEItemKey.of(Items.IRON_INGOT),
                family.adapter().toKey(HIGH_VOLTAGE), family.adapter().toKey(EXTREME_HIGH_VOLTAGE));
        var first = new TestNetwork();
        var second = new TestNetwork();
        var oldView = family.stockingInputView(first, keys);
        oldView.onStackChange(keys.get(1), 15L);
        oldView.onStackChange(keys.get(2), 27L);
        var rebound = family.stockingInputView(second, keys);
        rebound.onStackChange(keys.get(1), 5L);
        rebound.onStackChange(keys.get(2), 9L);
        assertThat(oldView.reservationIdentity()).isSameAs(first);
        assertThat(rebound.reservationIdentity()).isSameAs(second);
        assertThat(rebound.resource(0)).isNull();
        assertThat(rebound.amount(1)).isEqualTo(5L);
        assertThat(rebound.amount(2)).isEqualTo(9L);
        assertThat(oldView.amount(1)).isEqualTo(15L);
    }

    @Test
    void asyncViewsShareOneServiceAndOnlyAcceptedAmountsLeaveTheQueue() {
        var family = LoadedLightningBridge.FAMILY;
        var network = new TestNetwork();
        var service = new AsyncOutputService();
        var output = family.asyncOutputView(() -> network, service, IActionSource.empty(), () -> {});
        var items = AE2ResourceFamilies.ITEM.asyncOutputView(() -> network, service, IActionSource.empty(), () -> {});
        assertThat(output.reservationIdentity()).isSameAs(items.reservationIdentity());
        var plan = output.planOutput(HIGH_VOLTAGE, 20L, new PlanningReservations(), true);
        try (var transaction = Transaction.openRoot()) {
            assertThat(plan.operation().commit(transaction).success()).isTrue();
        }
        assertThat(service.isEmpty()).isTrue();
        plan = output.planOutput(HIGH_VOLTAGE, 20L, new PlanningReservations(), true);
        try (var transaction = Transaction.openRoot()) {
            assertThat(plan.operation().commit(transaction).success()).isTrue();
            transaction.commit();
        }
        network.perInsert = 3L;
        service.drainTo(network, IActionSource.empty(), 8);
        assertThat(network.amounts.get(family.adapter().toKey(HIGH_VOLTAGE))).isEqualTo(3L);
        assertThat(service.isEmpty()).isFalse();
        network.perInsert = Long.MAX_VALUE;
        service.drainTo(network, IActionSource.empty(), 8);
        assertThat(network.amounts.get(family.adapter().toKey(HIGH_VOLTAGE))).isEqualTo(20L);
        assertThat(service.isEmpty()).isTrue();
    }

    @Test
    void noExternalTransferExposure() {
        var family = LoadedLightningBridge.FAMILY;
        var inventory = new GenericStackInv(Set.of(family.adapter().keyType()), null, GenericStackInv.Mode.STORAGE, 1);
        var capability = family.capabilityView(null, family.standardInputView(inventory), IOType.INPUT, false);
        assertThat(capability.facet(TransferFacet.class)).isEmpty();
        assertThat(capability.view().facets()).doesNotContain(TransferFacet.class);
        assertThat(family.inputBinding(_ -> family.standardInputView(inventory), false).externalExposure()).isEmpty();
        for (var bindings : List.of(AE2ResourceFamilies.inputBindings(), AE2ResourceFamilies.stockingInputBindings(),
                AE2ResourceFamilies.outputBindings(), AE2ResourceFamilies.asyncOutputBindings(), AE2ResourceFamilies.patternBindings())) {
            assertThat(bindings).filteredOn(binding -> binding.type().equals(family.capabilityType())).singleElement()
                    .satisfies(binding -> {
                        assertThat(binding.nativeTransferExposure()).isFalse();
                        assertThat(binding.externalExposure()).isEmpty();
                    });
        }
    }

    /** @author howxu <dev@howxu.cn> */
    private static final class TestNetwork implements MEStorage {
        private final KeyCounter amounts = new KeyCounter();
        private long perInsert = Long.MAX_VALUE;

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long accepted = Math.min(amount, perInsert);
            if (mode == Actionable.MODULATE) amounts.add(key, accepted);
            return accepted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            out.addAll(amounts);
        }

        @Override
        public Component getDescription() {
            return Component.empty();
        }
    }
}
