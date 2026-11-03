package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.MEStorage;
import cn.howxu.mmcr.api.capability.storage.ResourceStorage;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.api.capability.plan.PlanningReservations;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2KeyAdapter;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2ResourceFamilies;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.network.FluidNetworkResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.network.ItemNetworkResourceStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.storage.network.NetworkResourceStorage;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the event-backed AE2 network resource view extracts from live ME storage.
 *
 * @author howxu <dev@howxu.cn>
 */
class AE2NetworkResourceStorageTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2_147_483_648L, 2_147_483_647_000_000_000L})
    void nativeCapabilitiesExtractLongInputsWithoutPerBatchNetworkCalls(long amount) {
        AEKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        AEKey waterKey = AEFluidKey.of(Fluids.WATER);
        FakeMEStorage network = new FakeMEStorage(ironKey, amount);
        network.setAmount(waterKey, amount);
        var items = new ItemBusCapability(new ItemNetworkResourceStorage(network, List.of(ironKey)), IOType.INPUT);
        var fluids = new FluidHatchCapability(new FluidNetworkResourceStorage(network, List.of(waterKey)), IOType.INPUT);
        var itemRequest = new CapabilityRequests.ResourceRequest<>(items.type(), IOType.INPUT, Integer.MAX_VALUE,
                List.of(new CapabilityRequests.ResourceAction<>(0, ItemResource.of(Items.IRON_INGOT), amount, false)));
        var fluidRequest = new CapabilityRequests.ResourceRequest<>(fluids.type(), IOType.INPUT, Integer.MAX_VALUE,
                List.of(new CapabilityRequests.ResourceAction<>(0, FluidResource.of(Fluids.WATER), amount, false)));

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(items.prepareOperation(itemRequest).commit(transaction).success()).isTrue();
            assertThat(fluids.prepareOperation(fluidRequest).commit(transaction).success()).isTrue();
        }
        assertThat(network.amount(ironKey)).isEqualTo(amount);
        assertThat(network.amount(waterKey)).isEqualTo(amount);
        assertThat(network.modulateExtractCalls()).isZero();
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(items.prepareOperation(itemRequest).commit(transaction).success()).isTrue();
            assertThat(fluids.prepareOperation(fluidRequest).commit(transaction).success()).isTrue();
            transaction.commit();
        }
        assertThat(network.amount(ironKey)).isZero();
        assertThat(network.amount(waterKey)).isZero();
        assertThat(network.modulateExtractCalls()).isEqualTo(2);
        assertThat(network.simulateExtractCalls()).isEqualTo(4);
    }

    @Test
    void fixedSlotsConvertOnceAndWatcherChangesOnlyAmounts() {
        AEKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        AEKey waterKey = AEFluidKey.of(Fluids.WATER);
        AEKey goldKey = AEItemKey.of(Items.GOLD_INGOT);
        List<AEKey> keys = List.of(ironKey, waterKey, goldKey, ironKey);
        CountingKeyAdapter<ItemResource> adapter = new CountingKeyAdapter<>(AE2ResourceFamilies.ITEM.adapter());
        NetworkResourceStorage<ItemResource> storage = new NetworkResourceStorage<>(
                new FakeMEStorage(ironKey, 5L), keys, adapter) {};

        assertThat(adapter.convertedKeys).containsExactlyElementsOf(keys);
        ItemResource iron = storage.resource(0);
        ItemResource gold = storage.resource(2);
        for (int access = 0; access < 3; access++) {
            assertThat(storage.resource(0)).isSameAs(iron);
            assertThat(storage.resource(1)).isNull();
            assertThat(storage.resource(2)).isSameAs(gold);
            assertThat(storage.resource(3)).isEqualTo(iron);
            storage.contentFingerprint();
        }
        storage.onStackChange(ironKey, 11L);
        storage.onStackChange(waterKey, 20L);
        storage.onStackChange(goldKey, 7L);

        assertThat(storage.size()).isEqualTo(keys.size());
        assertThat(storage.amount(0)).isEqualTo(11L);
        assertThat(storage.amount(1)).isEqualTo(20L);
        assertThat(storage.amount(2)).isEqualTo(7L);
        assertThat(storage.amount(3)).isEqualTo(11L);
        assertThat(storage.resource(0)).isSameAs(iron);
        assertThat(storage.resource(1)).isNull();
        assertThat(storage.resource(2)).isSameAs(gold);
        assertThat(adapter.convertedKeys).containsExactlyElementsOf(keys);
    }

    @Test
    void newDelegatesConvertTheirOwnConfigurationWithoutSharingWatcherAmounts() {
        AEKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        AEKey goldKey = AEItemKey.of(Items.GOLD_INGOT);
        FakeMEStorage network = new FakeMEStorage(ironKey, 8L);
        FakeMEStorage reboundNetwork = new FakeMEStorage(goldKey, 3L);
        CountingKeyAdapter<ItemResource> adapter = new CountingKeyAdapter<>(AE2ResourceFamilies.ITEM.adapter());
        List<AEKey> keys = new ArrayList<>(List.of(ironKey, goldKey));
        NetworkResourceStorage<ItemResource> original = new NetworkResourceStorage<>(network, keys, adapter) {};
        original.onStackChange(ironKey, 8L);
        keys.set(0, goldKey);
        keys.set(1, ironKey);
        NetworkResourceStorage<ItemResource> reconfigured = new NetworkResourceStorage<>(network, keys, adapter) {};
        NetworkResourceStorage<ItemResource> rebound = new NetworkResourceStorage<>(reboundNetwork, keys, adapter) {};

        assertThat(adapter.convertedKeys).containsExactly(ironKey, goldKey, goldKey, ironKey, goldKey, ironKey);
        assertThat(original.resource(0)).isEqualTo(ItemResource.of(Items.IRON_INGOT));
        assertThat(reconfigured.resource(0)).isEqualTo(ItemResource.of(Items.GOLD_INGOT));
        assertThat(reconfigured.resource(1)).isEqualTo(ItemResource.of(Items.IRON_INGOT));
        assertThat(rebound.resource(0)).isEqualTo(ItemResource.of(Items.GOLD_INGOT));
        assertThat(original.reservationIdentity()).isSameAs(network);
        assertThat(reconfigured.reservationIdentity()).isSameAs(network);
        assertThat(rebound.reservationIdentity()).isSameAs(reboundNetwork);
        assertThat(reconfigured.amount(1)).isZero();
        assertThat(rebound.amount(0)).isZero();
        reconfigured.onStackChange(ironKey, 4L);
        rebound.onStackChange(goldKey, 3L);
        assertThat(original.amount(0)).isEqualTo(8L);
        assertThat(reconfigured.amount(1)).isEqualTo(4L);
        assertThat(reconfigured.amount(0)).isZero();
        assertThat(rebound.amount(0)).isEqualTo(3L);
        assertThat(rebound.amount(1)).isZero();
        assertThat(adapter.convertedKeys).hasSize(6);
    }

    @Test
    void watcherAmountIsDisplayOnlyAndExtractionUsesLiveStorage() {
        AEKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        FakeMEStorage network = new FakeMEStorage(ironKey, 8L);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        ItemNetworkResourceStorage storage = new ItemNetworkResourceStorage(
                network, List.of(ironKey));
        ItemResource cachedIron = storage.resource(0);
        storage.onStackChange(ironKey, 9L);
        network.setAmount(ironKey, 5L);

        assertThat(storage.amount(0)).isEqualTo(9L);
        assertThat(storage.resource(0)).isEqualTo(iron);
        assertThat(storage.resource(0)).isSameAs(cachedIron);
        assertThat(storage.reservationIdentity()).isSameAs(network);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, storage.resource(0), 1L, transaction)).isZero();
            transaction.commit();
        }
        assertThat(network.insertCalls()).isZero();

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.extract(0, storage.resource(0), 6L, transaction)).isEqualTo(5L);
            transaction.commit();
        }
        assertThat(network.amount(ironKey)).isZero();
        assertThat(network.simulateExtractCalls()).isEqualTo(1);
        assertThat(network.modulateExtractCalls()).isEqualTo(1);
        assertThat(storage.amount(0)).isEqualTo(9L);
        storage.onStackChange(ironKey, -1L);
        assertThat(storage.amount(0)).isZero();
    }

    @Test
    void fluidViewUsesFluidAdapterAndLiveExtraction() {
        AEKey waterKey = AEFluidKey.of(Fluids.WATER);
        FluidResource water = FluidResource.of(Fluids.WATER);
        FakeMEStorage network = new FakeMEStorage(waterKey, 8L);
        FluidNetworkResourceStorage storage = new FluidNetworkResourceStorage(
                network, List.of(waterKey));

        assertThat(storage.resource(0)).isEqualTo(water);
        assertThat(storage.resourceType()).isEqualTo(FluidResource.class);
        assertThat(storage.capacity(0, water)).isEqualTo(Long.MAX_VALUE);
        assertThat(storage.reservationIdentity()).isSameAs(network);
        storage.onStackChange(waterKey, 12L);

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.extract(0, water, 20L, transaction)).isEqualTo(8L);
            transaction.commit();
        }

        assertThat(network.amount(waterKey)).isZero();
        assertThat(storage.amount(0)).isEqualTo(12L);
    }

    @Test
    void configuredKeysStayInFixedSlots() {
        AEKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        AEKey goldKey = AEItemKey.of(Items.GOLD_INGOT);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        ItemResource gold = ItemResource.of(Items.GOLD_INGOT);
        FakeMEStorage network = new FakeMEStorage(ironKey, 5L);
        network.setAmount(goldKey, 3L);
        ItemNetworkResourceStorage storage = new ItemNetworkResourceStorage(
                network, List.of(ironKey, goldKey));

        assertThat(storage.size()).isEqualTo(2);
        assertThat(storage.resource(0)).isEqualTo(iron);
        assertThat(storage.resource(1)).isEqualTo(gold);
        assertThat(storage.capacity(0, null)).isEqualTo(Long.MAX_VALUE);
        assertThat(storage.capacity(1, gold)).isEqualTo(Long.MAX_VALUE);
        assertThat(storage.isValid(0, iron)).isTrue();
        assertThat(storage.isValid(0, gold)).isFalse();

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.insert(0, gold, 1L, transaction)).isZero();
            assertThat(storage.extract(0, gold, 1L, transaction)).isZero();
            transaction.commit();
        }
        assertThat(network.insertCalls()).isZero();
        assertThat(network.extractCalls()).isZero();
    }

    @Test
    void rejectsInvalidSlotsAndResources() {
        AEKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        FluidResource water = FluidResource.of(Fluids.WATER);
        ItemNetworkResourceStorage storage = new ItemNetworkResourceStorage(
                new FakeMEStorage(ironKey, 1L), List.of(ironKey));

        assertThatThrownBy(() -> storage.resource(-1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> storage.amount(1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> storage.capacity(1, null)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> storage.isValid(1, iron)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> storage.insert(-1, iron, 1L, null))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> storage.extract(1, iron, 1L, null))
                .isInstanceOf(IndexOutOfBoundsException.class);

        assertThatThrownBy(() -> storage.insert(0, null, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.extract(0, null, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.insert(0, iron, -1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.extract(0, iron, -1L, null))
                .isInstanceOf(IllegalArgumentException.class);

        @SuppressWarnings("rawtypes")
        ResourceStorage rawStorage = storage;
        assertThatThrownBy(() -> rawStorage.isValid(0, water))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rawStorage.insert(0, water, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rawStorage.extract(0, water, 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fakeStorageReturnsZeroForUnknownKeys() {
        AEKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        AEKey goldKey = AEItemKey.of(Items.GOLD_INGOT);
        FakeMEStorage network = new FakeMEStorage(ironKey, 1L);

        assertThat(network.extract(goldKey, 1L, Actionable.MODULATE, IActionSource.empty())).isZero();
        assertThat(network.amount(goldKey)).isZero();
    }

    @Test
    void extractionWaitsForCommitAndAccountsForPendingAmount() {
        AEKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        FakeMEStorage network = new FakeMEStorage(ironKey, 8L);
        ItemNetworkResourceStorage storage = new ItemNetworkResourceStorage(
                network, List.of(ironKey));

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.extract(0, iron, 5L, transaction)).isEqualTo(5L);
            assertThat(storage.extract(0, iron, 5L, transaction)).isEqualTo(3L);
            assertThat(network.amount(ironKey)).isEqualTo(8L);
            assertThat(network.simulateExtractCalls()).isEqualTo(2);
            assertThat(network.modulateExtractCalls()).isZero();
            transaction.commit();
        }

        assertThat(network.amount(ironKey)).isZero();
        assertThat(network.modulateExtractCalls()).isEqualTo(1);
        assertThat(network.modulatedAmount()).isEqualTo(8L);
    }

    @Test
    void rollbackDiscardsPendingExtractionWithoutTouchingLiveStorage() {
        AEKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        ItemResource iron = ItemResource.of(Items.IRON_INGOT);
        FakeMEStorage network = new FakeMEStorage(ironKey, 8L);
        ItemNetworkResourceStorage storage = new ItemNetworkResourceStorage(
                network, List.of(ironKey));

        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.extract(0, iron, 5L, transaction)).isEqualTo(5L);
            assertThat(network.amount(ironKey)).isEqualTo(8L);
        }

        assertThat(network.amount(ironKey)).isEqualTo(8L);
        assertThat(network.modulateExtractCalls()).isZero();
    }

    @Test
    void partialCommittedExtractionFailsAndRestoresTheNetwork() {
        AEKey ironKey = AEItemKey.of(Items.IRON_INGOT);
        FakeMEStorage network = new FakeMEStorage(ironKey, 8L);
        network.setModulationLimit(3L);
        ItemNetworkResourceStorage storage = new ItemNetworkResourceStorage(
                network, List.of(ironKey));
        ItemResource iron = storage.resource(0);

        assertThatThrownBy(() -> {
            try (Transaction transaction = Transaction.openRoot()) {
                assertThat(storage.extract(0, iron, 5L, transaction)).isEqualTo(5L);
                transaction.commit();
            }
        }).hasRootCauseMessage("Unable to complete AE2 network extraction");

        assertThat(network.amount(ironKey)).isEqualTo(8L);
        assertThat(network.modulateExtractCalls()).isEqualTo(1);
        assertThat(network.insertCalls()).isEqualTo(1);
        assertThat(storage.resource(0)).isSameAs(iron);
        network.setModulationLimit(Long.MAX_VALUE);
        try (Transaction transaction = Transaction.openRoot()) {
            assertThat(storage.extract(0, storage.resource(0), 8L, transaction)).isEqualTo(8L);
            transaction.commit();
        }
        assertThat(network.amount(ironKey)).isZero();
        assertThat(network.modulateExtractCalls()).isEqualTo(2);
    }

    @Test
    void itemAndFluidViewsAtTheSameLocalSlotReserveIndependentNetworkKeys() {
        var iron = ItemResource.of(Items.IRON_INGOT);
        var water = FluidResource.of(Fluids.WATER);
        var ironKey = AEItemKey.of(iron);
        var waterKey = AEFluidKey.of(water);
        var network = new FakeMEStorage(ironKey, 5L);
        network.setAmount(waterKey, 8L);
        var items = new ItemNetworkResourceStorage(network, List.of(ironKey));
        var fluids = new FluidNetworkResourceStorage(network, List.of(waterKey));
        items.onStackChange(ironKey, 5L);
        fluids.onStackChange(waterKey, 8L);
        var reservations = new PlanningReservations();
        assertThat(reservations.reserveExtract(items, 0, iron, 5L)).isTrue();
        assertThat(reservations.reserveExtract(fluids, 0, water, 8L)).isTrue();
        try (var transaction = Transaction.openRoot()) {
            assertThat(items.extract(0, iron, 5L, transaction)).isEqualTo(5L);
            assertThat(fluids.extract(0, water, 8L, transaction)).isEqualTo(8L);
            assertThat(network.amount(ironKey)).isEqualTo(5L);
            assertThat(network.amount(waterKey)).isEqualTo(8L);
            transaction.commit();
        }
        assertThat(network.amount(ironKey)).isZero();
        assertThat(network.amount(waterKey)).isZero();
    }

    @Test
    void reorderedAndDuplicateItemSlotsShareOneReservationBudget() {
        var iron = ItemResource.of(Items.IRON_INGOT);
        var ironKey = AEItemKey.of(iron);
        var network = new FakeMEStorage(ironKey, 60L);
        var first = new ItemNetworkResourceStorage(network, List.of(ironKey, ironKey));
        var second = new ItemNetworkResourceStorage(network, List.of(AEItemKey.of(Items.GOLD_INGOT), ironKey));
        first.onStackChange(ironKey, 60L);
        second.onStackChange(ironKey, 60L);
        var reservations = new PlanningReservations();
        assertThat(reservations.reserveExtract(first, 0, iron, 40L)).isTrue();
        assertThat(reservations.amount(first, 1)).isEqualTo(20L);
        assertThat(reservations.amount(second, 1)).isEqualTo(20L);
        assertThat(reservations.reserveExtract(second, 1, iron, 40L)).isFalse();
        assertThat(reservations.reserveExtract(first, 1, iron, 20L)).isTrue();
        assertThat(reservations.amount(second, 1)).isZero();
        assertThat(network.amount(ironKey)).isEqualTo(60L);
    }

    /**
     * Counts key conversion while retaining the production resource-family adapter.
     *
     * @author howxu <dev@howxu.cn>
     */
    private static final class CountingKeyAdapter<R> implements AE2KeyAdapter<R> {
        private final AE2KeyAdapter<R> delegate;
        private final List<AEKey> convertedKeys = new ArrayList<>();

        private CountingKeyAdapter(AE2KeyAdapter<R> delegate) {
            this.delegate = delegate;
        }

        @Override
        public AEKeyType keyType() {
            return delegate.keyType();
        }

        @Override
        public Class<R> resourceType() {
            return delegate.resourceType();
        }

        @Override
        public AEKey toKey(R resource) {
            return delegate.toKey(resource);
        }

        @Override
        public Optional<R> toResource(AEKey key) {
            convertedKeys.add(key);
            return delegate.toResource(key);
        }
    }

    private static final class FakeMEStorage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();
        private int insertCalls;
        private int extractCalls;
        private int simulateExtractCalls;
        private int modulateExtractCalls;
        private long modulatedAmount;
        private long modulationLimit = Long.MAX_VALUE;

        private FakeMEStorage(AEKey key, long amount) {
            amounts.put(key, amount);
        }

        private void setAmount(AEKey key, long amount) {
            amounts.put(key, amount);
        }

        private void setModulationLimit(long limit) {
            modulationLimit = limit;
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            insertCalls++;
            if (mode != Actionable.MODULATE || amount <= 0L) return 0L;
            amounts.merge(key, amount, Long::sum);
            return amount;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            extractCalls++;
            long available = amounts.getOrDefault(key, 0L);
            long extracted = Math.min(amount, available);
            if (mode == Actionable.SIMULATE) {
                simulateExtractCalls++;
            } else if (mode == Actionable.MODULATE && extracted > 0L) {
                extracted = Math.min(extracted, modulationLimit);
                modulateExtractCalls++;
                modulatedAmount += extracted;
                amounts.put(key, available - extracted);
            }
            return extracted;
        }

        @Override
        public Component getDescription() {
            return Component.literal("test");
        }

        private long amount(AEKey key) {
            return amounts.getOrDefault(key, 0L);
        }

        private int insertCalls() {
            return insertCalls;
        }

        private int extractCalls() {
            return extractCalls;
        }

        private int simulateExtractCalls() {
            return simulateExtractCalls;
        }

        private int modulateExtractCalls() {
            return modulateExtractCalls;
        }

        private long modulatedAmount() {
            return modulatedAmount;
        }
    }
}
