package cn.howxu.mmcr.compat.appliedenergistics2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import cn.howxu.mmcr.compat.appliedenergistics2.loaded.adapter.AE2NativeAdapters;
import cn.howxu.mmcr.api.capability.plan.CapabilityRequests;
import cn.howxu.mmcr.internal.capability.FluidHatchCapability;
import cn.howxu.mmcr.internal.capability.ItemBusCapability;
import cn.howxu.mmcr.internal.capability.NativeReservationAccess;
import cn.howxu.mmcr.internal.capability.NativeStackSync;
import cn.howxu.mmcr.util.IOType;
import cn.howxu.mmcr.test.TestBootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AE2NetworkAdapterTest {
    @BeforeAll
    static void setup() throws Exception {
        TestBootstrap.bootstrap();
    }

    @Test
    void itemViewUsesAeKeyIdentityAndActionableModes() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        FakeMEStorage network = new FakeMEStorage(Map.of(iron, 12L));
        IItemHandler handler = AE2NativeAdapters.networkItems(() -> network, () -> List.of(iron),
                IActionSource.empty());

        ItemStack simulated = handler.extractItem(0, 5, true);
        assertThat(AEItemKey.of(simulated)).isEqualTo(iron);
        assertThat(simulated.getCount()).isEqualTo(5);
        assertThat(network.amount(iron)).isEqualTo(12L);

        ItemStack extracted = handler.extractItem(0, 5, false);
        assertThat(AEItemKey.of(extracted)).isEqualTo(iron);
        assertThat(network.amount(iron)).isEqualTo(7L);
        assertThat(network.modes()).containsExactly(Actionable.SIMULATE, Actionable.MODULATE);
    }

    @Test
    void fluidViewSimulatesWithoutMutatingAndModulatesOnce() {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        FakeMEStorage network = new FakeMEStorage(Map.of(water, 2_000L));
        IFluidHandler handler = AE2NativeAdapters.networkFluids(() -> network, () -> List.of(water),
                IActionSource.empty());
        FluidStack request = water.toStack(750);

        assertThat(handler.drain(request, IFluidHandler.FluidAction.SIMULATE).getAmount()).isEqualTo(750);
        assertThat(network.amount(water)).isEqualTo(2_000L);
        assertThat(handler.drain(request, IFluidHandler.FluidAction.EXECUTE).getAmount()).isEqualTo(750);
        assertThat(network.amount(water)).isEqualTo(1_250L);
    }

    @Test
    void nativeSyncRetainsLongAmountsBeyondVanillaStackSize() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        IItemHandler handler = AE2NativeAdapters.networkItems(() -> new FakeMEStorage(Map.of()),
                () -> List.of(iron), IActionSource.empty());
        long amount = (long) Integer.MAX_VALUE + 42L;

        ((NativeStackSync.Item) handler).setContents(0, iron.toStack(1), amount);

        assertThat(((NativeStackSync.Item) handler).amount(0)).isEqualTo(amount);
        assertThat(AEItemKey.of(handler.getStackInSlot(0))).isEqualTo(iron);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2_147_483_648L, 2_147_483_647_000_000_000L})
    void itemCapabilityCommitsLongInputWithoutChunking(long amount) {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        FakeMEStorage network = new FakeMEStorage(Map.of(iron, amount));
        network.extractionLimit = 2;
        ItemBusCapability capability = new ItemBusCapability(AE2NativeAdapters.networkItems(() -> network,
                () -> List.of(iron), IActionSource.empty()), IOType.INPUT);
        var request = new CapabilityRequests.ItemRequest(capability.type(), IOType.INPUT, Integer.MAX_VALUE,
                List.of(new CapabilityRequests.ItemAction(0, iron.toStack(1), amount, false)));

        assertThat(capability.prepareOperation(request).commit().success()).isTrue();
        assertThat(network.amount(iron)).isZero();
        assertThat(network.modes()).containsExactly(Actionable.SIMULATE, Actionable.MODULATE);
        assertThat(network.requests).containsExactly(amount, amount);
    }

    @ParameterizedTest
    @ValueSource(longs = {1L, 2_147_483_648L, 2_147_483_647_000_000_000L})
    void fluidCapabilityCommitsLongInputWithoutChunking(long amount) {
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        FakeMEStorage network = new FakeMEStorage(Map.of(water, amount));
        network.extractionLimit = 2;
        FluidHatchCapability capability = new FluidHatchCapability(AE2NativeAdapters.networkFluids(() -> network,
                () -> List.of(water), IActionSource.empty()), IOType.INPUT);
        var request = new CapabilityRequests.FluidRequest(capability.type(), IOType.INPUT, Integer.MAX_VALUE,
                List.of(new CapabilityRequests.FluidAction(0, water.toStack(1), amount, false)));

        assertThat(capability.prepareOperation(request).commit().success()).isTrue();
        assertThat(network.amount(water)).isZero();
        assertThat(network.modes()).containsExactly(Actionable.SIMULATE, Actionable.MODULATE);
        assertThat(network.requests).containsExactly(amount, amount);
    }

    @Test
    void longInputSimulationDoesNotMutateEitherNetworkView() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        long amount = (long) Integer.MAX_VALUE + 42L;
        FakeMEStorage network = new FakeMEStorage(Map.of(iron, amount, water, amount));
        var items = (NativeReservationAccess) AE2NativeAdapters.networkItems(() -> network,
                () -> List.of(iron), IActionSource.empty());
        var fluids = (NativeReservationAccess) AE2NativeAdapters.networkFluids(() -> network,
                () -> List.of(water), IActionSource.empty());

        assertThat(items.transfer(0, iron.toStack(1), amount, false, true)).isEqualTo(amount);
        assertThat(fluids.transfer(0, water.toStack(1), amount, false, true)).isEqualTo(amount);
        assertThat(network.amount(iron)).isEqualTo(amount);
        assertThat(network.amount(water)).isEqualTo(amount);
        assertThat(network.modes()).containsExactly(Actionable.SIMULATE, Actionable.SIMULATE);
    }

    @Test
    void longInputHonorsChangedConfigurationAndRejectsInsertion() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        AEFluidKey lava = AEFluidKey.of(Fluids.LAVA);
        FakeMEStorage network = new FakeMEStorage(Map.of(iron, 10L, gold, 10L, water, 10L, lava, 10L));
        AtomicReference<List<AEKey>> configured = new AtomicReference<>(List.of(iron, water, gold, lava));
        var items = (NativeReservationAccess) AE2NativeAdapters.networkItems(() -> network,
                configured::get, IActionSource.empty());
        var fluids = (NativeReservationAccess) AE2NativeAdapters.networkFluids(() -> network,
                configured::get, IActionSource.empty());
        configured.set(List.of(gold, lava, iron, water));

        assertThat(items.transfer(0, iron.toStack(1), 1L, false, false)).isZero();
        assertThat(fluids.transfer(0, water.toStack(1), 1L, false, false)).isZero();
        assertThat(items.transfer(1, iron.toStack(1), 1L, true, false)).isZero();
        assertThat(fluids.transfer(1, water.toStack(1), 1L, true, false)).isZero();
        assertThat(items.transfer(1, iron.toStack(1), 1L, false, false)).isEqualTo(1L);
        assertThat(fluids.transfer(1, water.toStack(1), 1L, false, false)).isEqualTo(1L);
        configured.set(List.of());
        assertThat(items.transfer(1, iron.toStack(1), 1L, false, false)).isZero();
        assertThat(fluids.transfer(1, water.toStack(1), 1L, false, false)).isZero();
        assertThat(network.amount(iron)).isEqualTo(9L);
        assertThat(network.amount(water)).isEqualTo(9L);
        assertThat(network.modes()).containsExactly(Actionable.MODULATE, Actionable.MODULATE);
    }

    @Test
    void longInputRejectsDifferentResourceComponentsBeforeAccessingNetwork() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        FakeMEStorage network = new FakeMEStorage(Map.of(iron, 10L, water, 10L));
        var items = (NativeReservationAccess) AE2NativeAdapters.networkItems(() -> network,
                () -> List.of(iron), IActionSource.empty());
        var fluids = (NativeReservationAccess) AE2NativeAdapters.networkFluids(() -> network,
                () -> List.of(water), IActionSource.empty());
        ItemStack namedIron = iron.toStack(1);
        namedIron.set(DataComponents.CUSTOM_NAME, Component.literal("different identity"));
        FluidStack namedWater = water.toStack(1);
        namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("different identity"));

        assertThat(items.transfer(0, namedIron, 1L, false, false)).isZero();
        assertThat(fluids.transfer(0, namedWater, 1L, false, false)).isZero();
        assertThat(network.modes()).isEmpty();
        assertThat(network.amount(iron)).isEqualTo(10L);
        assertThat(network.amount(water)).isEqualTo(10L);
    }

    @Test
    void insufficientLongInputFailsBeforeAnyActualExtraction() {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        FakeMEStorage network = new FakeMEStorage(Map.of(iron, 4L, water, 4L));
        ItemBusCapability items = new ItemBusCapability(AE2NativeAdapters.networkItems(() -> network,
                () -> List.of(iron), IActionSource.empty()), IOType.INPUT);
        FluidHatchCapability fluids = new FluidHatchCapability(AE2NativeAdapters.networkFluids(() -> network,
                () -> List.of(water), IActionSource.empty()), IOType.INPUT);
        long amount = (long) Integer.MAX_VALUE + 1L;
        var itemRequest = new CapabilityRequests.ItemRequest(items.type(), IOType.INPUT, 1L,
                List.of(new CapabilityRequests.ItemAction(0, iron.toStack(1), amount, false)));
        var fluidRequest = new CapabilityRequests.FluidRequest(fluids.type(), IOType.INPUT, 1L,
                List.of(new CapabilityRequests.FluidAction(0, water.toStack(1), amount, false)));

        assertThat(items.prepareOperation(itemRequest).commit().success()).isFalse();
        assertThat(fluids.prepareOperation(fluidRequest).commit().success()).isFalse();
        assertThat(network.amount(iron)).isEqualTo(4L);
        assertThat(network.amount(water)).isEqualTo(4L);
        assertThat(network.modes()).containsExactly(Actionable.SIMULATE, Actionable.SIMULATE);
    }

    @Test
    void typedConfigurationViewsAreReusedAndObserveInPlaceChanges() throws ReflectiveOperationException {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEItemKey gold = AEItemKey.of(Items.GOLD_INGOT);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        AEFluidKey lava = AEFluidKey.of(Fluids.LAVA);
        List<AEKey> configured = new ArrayList<>(List.of(iron, water, iron, lava));
        FakeMEStorage network = new FakeMEStorage(Map.of(iron, 10L, gold, 10L, water, 10L, lava, 10L));
        var items = AE2NativeAdapters.networkItems(() -> network, () -> configured, IActionSource.empty());
        var fluids = AE2NativeAdapters.networkFluids(() -> network, () -> configured, IActionSource.empty());
        var itemAccess = (NativeReservationAccess) items;
        var fluidAccess = (NativeReservationAccess) fluids;
        var itemKeys = typedKeys(items);
        var fluidKeys = typedKeys(fluids);
        assertThat(itemKeys).containsExactly(iron);
        assertThat(fluidKeys).containsExactly(water, lava);
        for (int index = 0; index < 100; index++) {
            assertThat(items.getSlots()).isEqualTo(1);
            assertThat(fluids.getTanks()).isEqualTo(2);
            assertThat(itemAccess.storedKey(0)).isEqualTo(iron);
            assertThat(fluidAccess.storedKey(0)).isEqualTo(water);
            assertThat(typedKeys(items)).isSameAs(itemKeys);
            assertThat(typedKeys(fluids)).isSameAs(fluidKeys);
        }

        configured.set(0, gold);
        configured.set(2, gold);
        configured.set(1, lava);
        configured.set(3, water);
        assertThat(itemAccess.storedKey(0)).isEqualTo(gold);
        assertThat(fluidAccess.storedKey(0)).isEqualTo(lava);
        assertThat(fluidAccess.storedKey(1)).isEqualTo(water);
        assertThat(itemAccess.transfer(0, iron.toStack(1), 1L, false, false)).isZero();
        assertThat(fluidAccess.transfer(0, water.toStack(1), 1L, false, false)).isZero();
        assertThat(network.modes()).isEmpty();
        assertThat(itemKeys).containsExactly(iron);
        assertThat(fluidKeys).containsExactly(water, lava);

        configured.clear();
        assertThat(items.getSlots()).isZero();
        assertThat(fluids.getTanks()).isZero();
        assertThat(itemAccess.transfer(0, gold.toStack(1), 1L, false, false)).isZero();
        assertThat(fluidAccess.transfer(0, lava.toStack(1), 1L, false, false)).isZero();
    }

    @Test
    void replacementConfigurationsReuseEqualViewsButPreserveResourceComponents() throws ReflectiveOperationException {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        ItemStack namedIron = iron.toStack(1);
        namedIron.set(DataComponents.CUSTOM_NAME, Component.literal("configured identity"));
        FluidStack namedWater = water.toStack(1);
        namedWater.set(DataComponents.CUSTOM_NAME, Component.literal("configured identity"));
        AEItemKey configuredIron = AEItemKey.of(namedIron);
        AEFluidKey configuredWater = AEFluidKey.of(namedWater);
        AtomicReference<List<AEKey>> configured = new AtomicReference<>(List.of(iron, water));
        FakeMEStorage network = new FakeMEStorage(Map.of(iron, 10L, water, 10L));
        var items = AE2NativeAdapters.networkItems(() -> network, configured::get, IActionSource.empty());
        var fluids = AE2NativeAdapters.networkFluids(() -> network, configured::get, IActionSource.empty());
        var itemKeys = typedKeys(items);
        var fluidKeys = typedKeys(fluids);

        configured.set(new ArrayList<>(List.of(iron, water)));
        assertThat(typedKeys(items)).isSameAs(itemKeys);
        assertThat(typedKeys(fluids)).isSameAs(fluidKeys);
        configured.set(List.of(configuredIron, configuredWater));
        var itemAccess = (NativeReservationAccess) items;
        var fluidAccess = (NativeReservationAccess) fluids;
        assertThat(itemAccess.storedKey(0)).isEqualTo(configuredIron).isNotEqualTo(iron);
        assertThat(fluidAccess.storedKey(0)).isEqualTo(configuredWater).isNotEqualTo(water);
        assertThat(itemAccess.transfer(0, iron.toStack(1), 1L, false, false)).isZero();
        assertThat(fluidAccess.transfer(0, water.toStack(1), 1L, false, false)).isZero();
        assertThat(network.modes()).isEmpty();
    }

    @Test
    void reusedConfigurationDoesNotCacheNetworkIdentityOrStoredAmounts() throws ReflectiveOperationException {
        AEItemKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEFluidKey water = AEFluidKey.of(Fluids.WATER);
        List<AEKey> configured = List.of(iron, water);
        FakeMEStorage first = new FakeMEStorage(Map.of(iron, 10L, water, 20L));
        FakeMEStorage second = new FakeMEStorage(Map.of(iron, 30L, water, 40L));
        AtomicReference<FakeMEStorage> network = new AtomicReference<>(first);
        var items = AE2NativeAdapters.networkItems(network::get, () -> configured, IActionSource.empty());
        var fluids = AE2NativeAdapters.networkFluids(network::get, () -> configured, IActionSource.empty());
        var itemAccess = (NativeReservationAccess) items;
        var fluidAccess = (NativeReservationAccess) fluids;
        var itemKeys = typedKeys(items);
        var fluidKeys = typedKeys(fluids);
        assertThat(itemAccess.storedAmount(0)).isEqualTo(10L);
        assertThat(fluidAccess.storedAmount(0)).isEqualTo(20L);
        first.amounts.put(iron, 7L);
        first.amounts.put(water, 8L);
        assertThat(itemAccess.storedAmount(0)).isEqualTo(7L);
        assertThat(fluidAccess.storedAmount(0)).isEqualTo(8L);

        network.set(second);
        assertThat(itemAccess.reservationIdentity()).isSameAs(second);
        assertThat(fluidAccess.reservationIdentity()).isSameAs(second);
        assertThat(itemAccess.storedAmount(0)).isEqualTo(30L);
        assertThat(fluidAccess.storedAmount(0)).isEqualTo(40L);
        assertThat(itemAccess.transfer(0, iron.toStack(1), 3L, false, false)).isEqualTo(3L);
        assertThat(fluidAccess.transfer(0, water.toStack(1), 4L, false, false)).isEqualTo(4L);
        assertThat(first.amount(iron)).isEqualTo(7L);
        assertThat(first.amount(water)).isEqualTo(8L);
        assertThat(second.amount(iron)).isEqualTo(27L);
        assertThat(second.amount(water)).isEqualTo(36L);
        assertThat(typedKeys(items)).isSameAs(itemKeys);
        assertThat(typedKeys(fluids)).isSameAs(fluidKeys);
    }

    @SuppressWarnings("unchecked")
    private static List<AEKey> typedKeys(Object adapter) throws ReflectiveOperationException {
        var method = adapter.getClass().getDeclaredMethod("keys");
        method.setAccessible(true);
        return (List<AEKey>) method.invoke(adapter);
    }

    private static final class FakeMEStorage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();
        private final java.util.ArrayList<Actionable> modes = new java.util.ArrayList<>();
        private final java.util.ArrayList<Long> requests = new java.util.ArrayList<>();
        private int extractionLimit = Integer.MAX_VALUE;

        private FakeMEStorage(Map<AEKey, Long> amounts) {
            this.amounts.putAll(amounts);
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            return 0L;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (modes.size() >= extractionLimit) throw new AssertionError("Bulk input was split into repeated extractions");
            modes.add(mode);
            requests.add(amount);
            long extracted = Math.min(amount, amounts.getOrDefault(key, 0L));
            if (mode == Actionable.MODULATE) amounts.merge(key, -extracted, Long::sum);
            return extracted;
        }

        @Override
        public Component getDescription() {
            return Component.literal("test");
        }

        private long amount(AEKey key) {
            return amounts.getOrDefault(key, 0L);
        }

        private List<Actionable> modes() {
            return List.copyOf(modes);
        }
    }
}
